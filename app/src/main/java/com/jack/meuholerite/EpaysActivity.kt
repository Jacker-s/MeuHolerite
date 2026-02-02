package com.jack.meuholerite

import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.google.gson.Gson
import com.jack.meuholerite.ads.AdsDataStore
import com.jack.meuholerite.ads.RewardedInterstitialAdManager
import com.jack.meuholerite.database.AppDatabase
import com.jack.meuholerite.database.toEntity
import com.jack.meuholerite.parser.PontoParser
import com.jack.meuholerite.parser.ReciboParser
import com.jack.meuholerite.ui.EpaysWebViewPage
import com.jack.meuholerite.ui.theme.MeuHoleriteTheme
import com.jack.meuholerite.utils.BackupManager
import com.jack.meuholerite.utils.PdfReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class EpaysActivity : ComponentActivity() {

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            MeuHoleriteTheme {
                Scaffold(
                    topBar = {
                        CenterAlignedTopAppBar(
                            title = { Text("ePays", fontWeight = FontWeight.Bold) },
                            navigationIcon = {
                                IconButton(onClick = { finish() }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                                }
                            }
                        )
                    }
                ) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding)) {
                        EpaysWebViewPage { uri ->
                            handleImport(uri)
                        }
                    }
                }
            }
        }
    }

    private fun handleImport(uri: Uri) {
        val scope = lifecycleScope
        val context = this
        val db = AppDatabase.getDatabase(context)
        val gson = Gson()
        val prefs = context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
        val backupManager = BackupManager(context)
        val userName = prefs.getString("user_name", "") ?: ""
        val userMatricula = prefs.getString("user_matricula", "") ?: ""

        scope.launch {
            val pdfReader = PdfReader(context)
            val text = withContext(Dispatchers.IO) { pdfReader.extractTextFromUri(uri) }
            if (text != null) {
                val isPonto = text.contains("PONTO", true) || text.contains("ESPELHO", true) || text.contains("BATIDA", true)
                val isRecibo = text.contains("PAGAMENTO", true) || text.contains("DEMONSTRATIVO", true) || text.contains("HOLERITE", true)

                if (isPonto && !text.contains("DEMONSTRATIVO", true)) {
                    val novo = PontoParser().parse(text)
                    val path = savePdfPermanently(uri, "ponto_${novo.periodo.replace("/", "_")}.pdf")
                    val updatedNovo = novo.copy(pdfFilePath = path)
                    withContext(Dispatchers.IO) {
                        db.espelhoDao().insert(updatedNovo.toEntity(gson, path))
                    }
                    backupManager.backupData()
                    finish()
                } else if (isRecibo) {
                    var novo = ReciboParser().parse(text)
                    if (novo.funcionario == "Não identificado" && userName.isNotEmpty()) {
                        novo = novo.copy(funcionario = userName, matricula = userMatricula)
                    }
                    val path = savePdfPermanently(uri, "recibo_${novo.periodo.replace("/", "_")}.pdf")
                    val updatedNovo = novo.copy(pdfFilePath = path)
                    withContext(Dispatchers.IO) {
                        db.reciboDao().insert(updatedNovo.toEntity(gson, path))
                    }
                    backupManager.backupData()
                    if (updatedNovo.dataPagamento.isNotEmpty()) {
                        showPaymentNotification(context, updatedNovo.periodo, updatedNovo.dataPagamento)
                    }

                    val shown = AdsDataStore.wasShownAfterImport(context)
                    if (!shown) {
                        RewardedInterstitialAdManager.showAd(this@EpaysActivity) {
                            scope.launch {
                                AdsDataStore.markShownAfterImport(context)
                                finish()
                            }
                        }
                    } else {
                        finish()
                    }
                }
            }
        }
    }

    private suspend fun savePdfPermanently(uri: Uri, fileName: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                val directory = File(filesDir, "pdfs")
                if (!directory.exists()) directory.mkdirs()
                val destFile = File(directory, fileName)
                contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(destFile).use { output ->
                        input.copyTo(output)
                    }
                }
                destFile.absolutePath
            } catch (_: Exception) {
                null
            }
        }
    }
}
