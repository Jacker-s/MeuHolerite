package com.jack.meuholerite.utils

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class UpdateManager(private val context: Context) {
    private val githubApiUrl = "https://api.github.com/repos/Jacker-s/MeuHolerite/releases/latest"

    data class GitHubRelease(
        val tag_name: String?,
        val assets: List<Asset>?
    )

    data class Asset(
        val browser_download_url: String?,
        val name: String?
    )

    suspend fun checkForUpdates(
        currentVersion: String,
        onUpdateAvailable: (String, String) -> Unit,
        onNoUpdate: () -> Unit,
        onError: () -> Unit
    ) {
        withContext(Dispatchers.IO) {
            var connection: HttpURLConnection? = null
            try {
                val url = URL(githubApiUrl)
                connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 15000
                connection.readTimeout = 15000
                connection.requestMethod = "GET"
                connection.setRequestProperty("Accept", "application/vnd.github.v3+json")
                connection.setRequestProperty("User-Agent", "MeuHolerite-App")
                
                if (connection.responseCode == 200) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    val release = Gson().fromJson(response, GitHubRelease::class.java)
                    val tagName = release?.tag_name
                    
                    if (tagName != null) {
                        val latestVersion = tagName.replace("v", "").trim()
                        if (isNewerVersion(currentVersion, latestVersion)) {
                            val apkAsset = release.assets?.find { it.name?.endsWith(".apk") == true }
                            val downloadUrl = apkAsset?.browser_download_url
                            if (downloadUrl != null) {
                                withContext(Dispatchers.Main) { onUpdateAvailable(latestVersion, downloadUrl) }
                                return@withContext
                            }
                        }
                    }
                    withContext(Dispatchers.Main) { onNoUpdate() }
                } else {
                    withContext(Dispatchers.Main) { onNoUpdate() }
                }
            } catch (e: Exception) {
                Log.e("UpdateManager", "Erro ao verificar: ${e.message}")
                withContext(Dispatchers.Main) { onError() }
            } finally {
                connection?.disconnect()
            }
        }
    }

    private fun isNewerVersion(current: String, latest: String): Boolean {
        return try {
            val curParts = current.split(".").map { it.toInt() }
            val latParts = latest.split(".").map { it.toInt() }
            for (i in 0 until maxOf(curParts.size, latParts.size)) {
                val cur = curParts.getOrElse(i) { 0 }
                val lat = latParts.getOrElse(i) { 0 }
                if (lat > cur) return true
                if (lat < cur) return false
            }
            false
        } catch (e: Exception) { latest != current }
    }

    fun downloadAndInstall(url: String, version: String) {
        val fileName = "meu_holerite_$version.apk"
        // Salva na pasta pública de Downloads para garantir que o instalador do sistema tenha acesso
        val destination = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName)
        
        if (destination.exists()) {
            installApk(destination)
            return
        }

        Toast.makeText(context, "Iniciando download da v$version...", Toast.LENGTH_LONG).show()

        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle("Meu Holerite v$version")
            .setDescription("Baixando nova versão...")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, fileName)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)

        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val downloadId = try {
            downloadManager.enqueue(request)
        } catch (e: Exception) {
            Log.e("UpdateManager", "Erro ao enfileirar download: ${e.message}")
            Toast.makeText(context, "Erro ao iniciar download", Toast.LENGTH_SHORT).show()
            return
        }

        val onComplete = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (id == downloadId) {
                    installApk(destination)
                    context.applicationContext.unregisterReceiver(this)
                }
            }
        }
        
        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.applicationContext.registerReceiver(onComplete, filter, Context.RECEIVER_EXPORTED)
        } else {
            context.applicationContext.registerReceiver(onComplete, filter)
        }
    }

    private fun installApk(file: File) {
        try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
                Toast.makeText(context, "Autorize a instalação de fontes desconhecidas", Toast.LENGTH_LONG).show()
                context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = Uri.parse("package:${context.packageName}")
                })
                return
            }

            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e("UpdateManager", "Erro ao instalar: ${e.message}")
            Toast.makeText(context, "Erro ao abrir instalador", Toast.LENGTH_SHORT).show()
        }
    }
}
