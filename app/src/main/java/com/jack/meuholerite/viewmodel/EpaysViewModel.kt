package com.jack.meuholerite.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.jack.meuholerite.database.AppDatabase
import com.jack.meuholerite.database.toEntity
import com.jack.meuholerite.model.ReciboPagamento
import com.jack.meuholerite.model.InformeRendimento
import com.jack.meuholerite.parser.AiParser
import com.jack.meuholerite.parser.PontoParser
import com.jack.meuholerite.parser.ReciboParser
import com.jack.meuholerite.parser.InformeParser
import com.jack.meuholerite.utils.BackupManager
import com.jack.meuholerite.utils.PdfReader
import com.jack.meuholerite.utils.extractStartDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

sealed class ImportState {
    object Idle : ImportState()
    data class Loading(val message: String) : ImportState()
    data class Success(val type: String, val id: String, val path: String) : ImportState()
    data class Error(val message: String) : ImportState()
}

class EpaysViewModel(application: Application) : AndroidViewModel(application) {

    private val _importState = MutableStateFlow<ImportState>(ImportState.Idle)
    val importState = _importState.asStateFlow()

    private val db = AppDatabase.getDatabase(application)
    private val gson = Gson()
    private val backupManager = BackupManager(application)
    private val pdfReader = PdfReader(application)

    private val googleDriveBackupManager = com.jack.meuholerite.utils.GoogleDriveBackupManager(application)

    fun handleImport(uri: Uri) {
        viewModelScope.launch {
            try {
                _importState.value = ImportState.Loading("Lendo PDF...")
                
                val text = withContext(Dispatchers.IO) { pdfReader.extractTextFromUri(uri) }
                if (text == null) {
                    _importState.value = ImportState.Error("Falha ao extrair texto do PDF")
                    return@launch
                }

                val textToAnalyze = text.uppercase()
                val isInforme = textToAnalyze.contains("INFORME DE RENDIMENTOS") || (textToAnalyze.contains("FONTE PAGADORA") && textToAnalyze.contains("RENDIMENTOS TRIBUTÁVEIS"))
                val isEspelho = !isInforme && (textToAnalyze.contains("ESPELHO DE PONTO") || textToAnalyze.contains("CARTÃO DE PONTO") || textToAnalyze.contains("HORAS"))
                val isRecibo = !isInforme && (textToAnalyze.contains("PAGAMENTO") || textToAnalyze.contains("DEMONSTRATIVO") || textToAnalyze.contains("HOLERITE") || 
                               textToAnalyze.contains("CONTRACHEQUE") || textToAnalyze.contains("PROVENTOS") || textToAnalyze.contains("RECIBO") ||
                               textToAnalyze.contains("LÍQUIDO") || textToAnalyze.contains("FGTS"))

                when {
                    isInforme -> processInforme(uri, text)
                    isRecibo -> processRecibo(uri, text)
                    isEspelho -> processPonto(uri, text)
                    else -> {
                        Log.w("EpaysViewModel", "Documento não reconhecido")
                        _importState.value = ImportState.Error("Documento não reconhecido como Holerite, Ponto ou Informe")
                    }
                }
            } catch (e: Exception) {
                Log.e("EpaysViewModel", "Erro no processamento", e)
                _importState.value = ImportState.Error("Erro inesperado: ${e.message}")
            }
        }
    }

    private suspend fun processRecibo(uri: Uri, text: String) {
        _importState.value = ImportState.Loading("Extraindo dados do Holerite...")
        
        var novo = ReciboParser().parse(text)
        
        if (novo.periodo == "Não identificado" || novo.valorLiquido == "0,00") {
            _importState.value = ImportState.Loading("Usando IA para melhorar extração...")
            val aiNovo = AiParser().parseRecibo(text)
            if (aiNovo != null) novo = aiNovo
        }

        val prefs = getApplication<Application>().getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
        val userName = prefs.getString("user_name", "") ?: ""
        val userMatricula = prefs.getString("user_matricula", "") ?: ""

        if (novo.funcionario == "Não identificado" && userName.isNotEmpty()) {
            novo = novo.copy(funcionario = userName, matricula = userMatricula)
        }
        
        _importState.value = ImportState.Loading("Verificando se já existe...")
        val existe = withContext(Dispatchers.IO) { db.reciboDao().exists(novo.periodo) }
        if (existe) {
            _importState.value = ImportState.Error("Este Holerite (${novo.periodo}) já foi importado anteriormente.")
            return
        }

        _importState.value = ImportState.Loading("Salvando...")
        val fileName = "recibo_${novo.periodo.replace("/", "_").ifEmpty { "unkn" }}.pdf"
        val path = savePdfPermanently(uri, fileName, "RECIBO", novo.periodo)
        
        val updatedNovo = novo.copy(pdfFilePath = path)
        withContext(Dispatchers.IO) {
            db.reciboDao().insert(updatedNovo.toEntity(gson, path))
            
            // Stats & Backup
            val baseSalaryToReport = updatedNovo.salarioBase.replace(".", "").replace(",", ".").toDoubleOrNull() ?: 0.0
            if (updatedNovo.cargo.isNotEmpty() && baseSalaryToReport > 0) {
                backupManager.saveAnonymousSalaryStat(updatedNovo)
            }
            backupManager.backupData()
            
            val account = com.google.android.gms.auth.api.signin.GoogleSignIn.getLastSignedInAccount(getApplication())
            if (account != null) googleDriveBackupManager.backupNow(account)
        }

        // Notificação (Pode ser mantida na Activity ou disparada via evento, mas aqui é seguro)
        com.jack.meuholerite.showPaymentNotification(getApplication(), updatedNovo.periodo, updatedNovo.dataPagamento)

        _importState.value = ImportState.Success("RECIBO", novo.periodo, path ?: "")
    }

    private suspend fun processPonto(uri: Uri, text: String) {
        val novo = PontoParser().parse(text)
        
        _importState.value = ImportState.Loading("Verificando se já existe...")
        val existe = withContext(Dispatchers.IO) { db.espelhoDao().exists(novo.periodo) }
        if (existe) {
            _importState.value = ImportState.Error("Este Espelho de Ponto (${novo.periodo}) já foi importado anteriormente.")
            return
        }

        _importState.value = ImportState.Loading("Processando Espelho de Ponto...")
        val fileName = "ponto_${novo.periodo.replace("/", "_").ifEmpty { "unkn" }}.pdf"
        val path = savePdfPermanently(uri, fileName, "PONTO", novo.periodo)
        
        val updatedNovo = novo.copy(pdfFilePath = path)
        withContext(Dispatchers.IO) {
            db.espelhoDao().insert(updatedNovo.toEntity(gson, path))
            backupManager.backupData()
            
            val account = com.google.android.gms.auth.api.signin.GoogleSignIn.getLastSignedInAccount(getApplication())
            if (account != null) googleDriveBackupManager.backupNow(account)
        }
        
        _importState.value = ImportState.Success("PONTO", novo.periodo, path ?: "")
    }

    private suspend fun processInforme(uri: Uri, text: String) {
        _importState.value = ImportState.Loading("Extraindo dados do Informe de Rendimentos...")
        val novo = InformeParser().parse(text)
        
        _importState.value = ImportState.Loading("Verificando se já existe...")
        val existe = withContext(Dispatchers.IO) { db.informeDao().exists(novo.anoCalendario) }
        if (existe) {
            _importState.value = ImportState.Error("Este Informe de Rendimentos (${novo.anoCalendario}) já foi importado anteriormente.")
            return
        }

        _importState.value = ImportState.Loading("Salvando...")
        val fileName = "informe_${novo.anoCalendario}.pdf"
        val path = savePdfPermanently(uri, fileName, "INFORME", novo.anoCalendario)
        
        val updatedNovo = novo.copy(pdfFilePath = path)
        withContext(Dispatchers.IO) {
            db.informeDao().insert(updatedNovo.toEntity(path))
            backupManager.backupData()
            
            val account = com.google.android.gms.auth.api.signin.GoogleSignIn.getLastSignedInAccount(getApplication())
            if (account != null) googleDriveBackupManager.backupNow(account)
        }
        
        _importState.value = ImportState.Success("INFORME", novo.anoCalendario, path ?: "")
    }

    private suspend fun savePdfPermanently(uri: Uri, fileName: String, tipo: String, periodo: String): String? = withContext(Dispatchers.IO) {
        val sanitizedFileName = fileName.replace("[^a-zA-Z0-9._-]".toRegex(), "_")
        try {
            val year: String
            val month: String
            if (tipo == "INFORME") {
                year = periodo
                month = "00"
            } else {
                val date = periodo.extractStartDate()
                val cal = java.util.Calendar.getInstance()
                cal.time = date
                year = cal.get(java.util.Calendar.YEAR).toString()
                month = (cal.get(java.util.Calendar.MONTH) + 1).toString().padStart(2, '0')
            }

            val baseDir = File(getApplication<Application>().filesDir, "pdfs")
            val targetDir = File(File(File(baseDir, tipo), year), month)
            
            if (!targetDir.exists()) targetDir.mkdirs()
            
            val destFile = File(targetDir, sanitizedFileName)
            getApplication<Application>().contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
            destFile.absolutePath
        } catch (e: Exception) {
            Log.e("EpaysViewModel", "Erro salvando PDF organizado", e)
            null
        }
    }

    fun resetState() {
        _importState.value = ImportState.Idle
    }
}
