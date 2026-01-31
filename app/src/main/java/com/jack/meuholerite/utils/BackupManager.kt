package com.jack.meuholerite.utils

import android.content.Context
import android.util.Base64
import android.util.Log
import android.webkit.CookieManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.gson.Gson
import com.jack.meuholerite.database.AppDatabase
import com.jack.meuholerite.database.EspelhoEntity
import com.jack.meuholerite.database.ReciboEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream

class BackupManager(private val context: Context) {

    private val db = AppDatabase.getDatabase(context)
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val gson = Gson()
    private val epaysUrl = "https://app.epays.com.br/"

    suspend fun backupData(): Result<Unit> = withContext(Dispatchers.IO) {
        val user = auth.currentUser ?: return@withContext Result.failure(Exception("Usuário não autenticado"))
        val userId = user.uid

        try {
            val espelhos = db.espelhoDao().getAll()
            val recibos = db.reciboDao().getAll()

            val userPrefs = context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
            val settingsPrefs = context.getSharedPreferences("meu_holerite_prefs", Context.MODE_PRIVATE)

            val userData = hashMapOf(
                "user_name" to (userPrefs.getString("user_name", "") ?: ""),
                "user_matricula" to (userPrefs.getString("user_matricula", "") ?: "")
            )

            val settingsData = hashMapOf(
                "dark_mode" to settingsPrefs.getBoolean("dark_mode", false),
                "hide_values_enabled" to settingsPrefs.getBoolean("hide_values_enabled", false),
                "app_lock_enabled" to settingsPrefs.getBoolean("app_lock_enabled", false),
                "app_lock_pin" to (settingsPrefs.getString("app_lock_pin", "") ?: ""),
                "has_dark_mode_set" to settingsPrefs.contains("dark_mode")
            )

            val cookies = withContext(Dispatchers.Main) {
                CookieManager.getInstance().getCookie(epaysUrl) ?: ""
            }

            // --- START PDF BACKUP (With size limit for Firestore 1MB limit) ---
            val pdfDir = File(context.filesDir, "pdfs")
            val pdfFilesData = mutableListOf<Map<String, String>>()
            var currentBackupSize = 0L 
            
            if (pdfDir.exists() && pdfDir.isDirectory) {
                // Ordenar por data para priorizar os mais recentes se houver muitos
                val files = pdfDir.listFiles()?.sortedByDescending { it.lastModified() }
                files?.forEach { file ->
                    if (file.isFile && file.name.endsWith(".pdf")) {
                        // Limite de segurança de ~750KB total para PDFs (Base64 aumenta o tamanho em 33%)
                        // 750KB * 1.33 = ~1MB. Usamos 600KB para garantir espaço para o resto dos dados.
                        if (currentBackupSize + file.length() < 600000) {
                            val base64Content = fileToBase64(file)
                            pdfFilesData.add(
                                hashMapOf(
                                    "fileName" to file.name,
                                    "content" to base64Content
                                )
                            )
                            currentBackupSize += file.length()
                        } else {
                            Log.w("BackupManager", "PDF ignorado no backup (limite de tamanho): ${file.name}")
                        }
                    }
                }
            }
            // --- END PDF BACKUP ---

            val backupMap = hashMapOf(
                "userData" to userData,
                "settings" to settingsData,
                "epays_cookies" to cookies,
                "pdf_files" to pdfFilesData,
                "lastBackup" to System.currentTimeMillis(),
                "espelhos" to espelhos.map { entity ->
                    hashMapOf(
                        "funcionario" to entity.funcionario,
                        "empresa" to entity.empresa,
                        "periodo" to entity.periodo,
                        "jornada" to entity.jornada,
                        "jornadaRealizada" to entity.jornadaRealizada,
                        "resumoItensJson" to entity.resumoItensJson,
                        "saldoFinalBH" to entity.saldoFinalBH,
                        "saldoPeriodoBH" to entity.saldoPeriodoBH,
                        "detalhesSaldoBH" to entity.detalhesSaldoBH,
                        "hasAbsences" to entity.hasAbsences,
                        "diasFaltasJson" to entity.diasFaltasJson,
                        "timestamp" to entity.timestamp,
                        "pdfFilePath" to (entity.pdfFilePath ?: "")
                    )
                },
                "recibos" to recibos.map { entity ->
                    hashMapOf(
                        "funcionario" to entity.funcionario,
                        "matricula" to entity.matricula,
                        "periodo" to entity.periodo,
                        "dataPagamento" to entity.dataPagamento,
                        "empresa" to entity.empresa,
                        "proventosJson" to entity.proventosJson,
                        "descontosJson" to entity.descontosJson,
                        "totalProventos" to entity.totalProventos,
                        "totalDescontos" to entity.totalDescontos,
                        "valorLiquido" to entity.valorLiquido,
                        "baseInss" to entity.baseInss,
                        "fgtsMes" to entity.fgtsMes,
                        "baseIrpf" to entity.baseIrpf,
                        "timestamp" to entity.timestamp,
                        "pdfFilePath" to (entity.pdfFilePath ?: "")
                    )
                }
            )

            firestore.collection("backups").document(userId)
                .set(backupMap) // Removido merge para garantir que o documento seja substituído e mantido limpo
                .await()

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("BackupManager", "Erro ao fazer backup", e)
            Result.failure(e)
        }
    }

    suspend fun restoreData(): Result<Unit> = withContext(Dispatchers.IO) {
        val user = auth.currentUser ?: return@withContext Result.failure(Exception("Usuário não autenticado"))
        val userId = user.uid

        try {
            val document = firestore.collection("backups").document(userId).get().await()
            if (!document.exists()) {
                return@withContext Result.failure(Exception("Nenhum backup encontrado"))
            }

            // 1. Restaurar Cookies
            try {
                val cookies = document.getString("epays_cookies")
                if (!cookies.isNullOrEmpty()) {
                    withContext(Dispatchers.Main) {
                        val cookieManager = CookieManager.getInstance()
                        cookieManager.setAcceptCookie(true)
                        cookieManager.removeAllCookies(null)
                        cookies.split(";").forEach { cookie ->
                            val cleanCookie = cookie.trim()
                            if (cleanCookie.isNotEmpty()) {
                                cookieManager.setCookie(epaysUrl, cleanCookie)
                            }
                        }
                        cookieManager.flush()
                    }
                }
            } catch (e: Exception) { Log.e("BackupManager", "Erro cookies", e) }

            // 2. Restaurar Espelhos
            try {
                val espelhosList = document.get("espelhos") as? List<Map<String, Any>> ?: emptyList()
                espelhosList.forEach { map ->
                    try {
                        val entity = EspelhoEntity(
                            funcionario = map["funcionario"] as? String ?: "",
                            empresa = map["empresa"] as? String ?: "",
                            periodo = map["periodo"] as? String ?: "",
                            jornada = map["jornada"] as? String ?: "",
                            jornadaRealizada = map["jornadaRealizada"] as? String ?: "",
                            resumoItensJson = map["resumoItensJson"] as? String ?: "[]",
                            saldoFinalBH = map["saldoFinalBH"] as? String ?: "0:00",
                            saldoPeriodoBH = map["saldoPeriodoBH"] as? String ?: "0:00",
                            detalhesSaldoBH = map["detalhesSaldoBH"] as? String ?: "",
                            hasAbsences = map["hasAbsences"] as? Boolean ?: false,
                            diasFaltasJson = map["diasFaltasJson"] as? String ?: "[]",
                            timestamp = (map["timestamp"] as? Long) ?: System.currentTimeMillis(),
                            pdfFilePath = map["pdfFilePath"] as? String
                        )
                        db.espelhoDao().insert(entity)
                    } catch (e: Exception) { Log.e("BackupManager", "Erro item espelho", e) }
                }
            } catch (e: Exception) { Log.e("BackupManager", "Erro lista espelhos", e) }

            // 3. Restaurar Recibos
            try {
                val recibosList = document.get("recibos") as? List<Map<String, Any>> ?: emptyList()
                recibosList.forEach { map ->
                    try {
                        val entity = ReciboEntity(
                            funcionario = map["funcionario"] as? String ?: "",
                            matricula = map["matricula"] as? String ?: "",
                            periodo = map["periodo"] as? String ?: "",
                            dataPagamento = map["dataPagamento"] as? String ?: "",
                            empresa = map["empresa"] as? String ?: "",
                            proventosJson = map["proventosJson"] as? String ?: "[]",
                            descontosJson = map["descontosJson"] as? String ?: "[]",
                            totalProventos = map["totalProventos"] as? String ?: "0,00",
                            totalDescontos = map["totalDescontos"] as? String ?: "0,00",
                            valorLiquido = map["valorLiquido"] as? String ?: "0,00",
                            baseInss = map["baseInss"] as? String ?: "0,00",
                            fgtsMes = map["fgtsMes"] as? String ?: "0,00",
                            baseIrpf = map["baseIrpf"] as? String ?: "0,00",
                            timestamp = (map["timestamp"] as? Long) ?: System.currentTimeMillis(),
                            pdfFilePath = map["pdfFilePath"] as? String
                        )
                        db.reciboDao().insert(entity)
                    } catch (e: Exception) { Log.e("BackupManager", "Erro item recibo", e) }
                }
            } catch (e: Exception) { Log.e("BackupManager", "Erro lista recibos", e) }

            // 4. Restaurar User Data
            try {
                val userData = document.get("userData") as? Map<*, *>
                if (userData != null) {
                    val userPrefs = context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
                    userPrefs.edit().apply {
                        putString("user_name", userData["user_name"]?.toString())
                        putString("user_matricula", userData["user_matricula"]?.toString())
                        apply()
                    }
                }
            } catch (e: Exception) { Log.e("BackupManager", "Erro userData", e) }

            // 5. Restaurar Configurações
            try {
                val settings = document.get("settings") as? Map<*, *>
                if (settings != null) {
                    val settingsPrefs = context.getSharedPreferences("meu_holerite_prefs", Context.MODE_PRIVATE)
                    settingsPrefs.edit().apply {
                        putBoolean("dark_mode", settings["dark_mode"] as? Boolean ?: false)
                        putBoolean("hide_values_enabled", settings["hide_values_enabled"] as? Boolean ?: false)
                        putBoolean("app_lock_enabled", settings["app_lock_enabled"] as? Boolean ?: false)
                        putString("app_lock_pin", settings["app_lock_pin"]?.toString())
                        apply()
                    }
                }
            } catch (e: Exception) { Log.e("BackupManager", "Erro settings", e) }
            
            // 6. Restaurar PDFs
            try {
                val pdfFilesList = document.get("pdf_files") as? List<Map<String, Any>> ?: emptyList()
                val pdfDir = File(context.filesDir, "pdfs")
                if (!pdfDir.exists()) pdfDir.mkdirs()
                pdfFilesList.forEach { map ->
                    val fileName = map["fileName"] as? String
                    val base64Content = map["content"] as? String
                    if (fileName != null && base64Content != null) {
                        try {
                            val file = File(pdfDir, fileName)
                            file.writeBytes(Base64.decode(base64Content, Base64.DEFAULT))
                        } catch (e: Exception) { Log.e("BackupManager", "Erro PDF: $fileName", e) }
                    }
                }
            } catch (e: Exception) { Log.e("BackupManager", "Erro lista PDFs", e) }

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("BackupManager", "Erro crítico na restauração", e)
            Result.failure(e)
        }
    }

    suspend fun deleteBackup(): Result<Unit> = withContext(Dispatchers.IO) {
        val user = auth.currentUser ?: return@withContext Result.failure(Exception("Usuário não autenticado"))
        val userId = user.uid
        try {
            firestore.collection("backups").document(userId).delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("BackupManager", "Erro ao apagar backup", e)
            Result.failure(e)
        }
    }

    private suspend fun fileToBase64(file: File): String {
        return withContext(Dispatchers.IO) {
            FileInputStream(file).use { inputStream ->
                val bytes = inputStream.readBytes()
                Base64.encodeToString(bytes, Base64.NO_WRAP)
            }
        }
    }
}
