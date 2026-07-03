package com.jack.meuholerite.utils

import android.accounts.Account
import android.content.Context
import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.FileContent
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File
import com.google.api.services.drive.model.FileList
import com.google.gson.Gson
import com.jack.meuholerite.database.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileOutputStream
import java.io.InputStream
import java.util.Collections

class GoogleDriveBackupManager(private val context: Context) {

    private val gson = Gson()
    private val db = AppDatabase.getDatabase(context)

    private fun getDriveService(account: GoogleSignInAccount): Drive {
        val credential = GoogleAccountCredential.usingOAuth2(
            context, Collections.singleton(DriveScopes.DRIVE_FILE)
        )
        // Se account.account for nulo, usamos o email como fallback
        credential.selectedAccount = account.account ?: account.email?.let { android.accounts.Account(it, "com.google") }

        return Drive.Builder(
            NetHttpTransport(),
            GsonFactory.getDefaultInstance(),
            credential
        ).setApplicationName("Meu Holerite").build()
    }

    suspend fun backupNow(account: GoogleSignInAccount, onProgress: (String) -> Unit = {}): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val service = getDriveService(account)
            onProgress("Localizando pasta de backup...")

            // 1. Encontrar ou criar a pasta Root "Meu Holerite Backup"
            val folderId = getOrCreateBackupFolder(service)

            // 2. Limpeza Padrão (Excluir PDFs soltos da raiz legada do Drive)
            onProgress("Limpando PDFs antigos da raiz...")
            try {
                val rootPdfResult = service.files().list()
                    .setQ("'$folderId' in parents and mimeType = 'application/pdf' and trashed = false")
                    .setFields("files(id)")
                    .execute()
                rootPdfResult.files?.forEach { fileToTrash ->
                    service.files().delete(fileToTrash.id).execute()
                }
            } catch (e: Exception) {}

            // 3. Obter ou Criar pastas sincronizadas do Cloud
            val folderPontoId = getOrCreateSubFolder(service, folderId, "PONTO")
            val folderReciboId = getOrCreateSubFolder(service, folderId, "RECIBO")

            // 4. Obter mapas de arquivos existentes em subpastas para evitar reuploads
            onProgress("Verificando estrutura na nuvem...")
            val mapPonto = listFilesMapInFolder(service, folderPontoId)
            val mapRecibo = listFilesMapInFolder(service, folderReciboId)
            
            // 5. Deep Scan e Backup
            onProgress("Sincronizando PDFs (Varredura Profunda)...")
            val pdfDir = java.io.File(context.filesDir, "pdfs")
            if (pdfDir.exists()) {
                val files = pdfDir.walkTopDown().filter { it.isFile && it.extension.lowercase() == "pdf" }.toList()
                files.forEachIndexed { index, pdfFile ->
                    onProgress("Enviando PDF ${index + 1}/${files.size}: ${pdfFile.name}")
                    val isPonto = pdfFile.absolutePath.contains("/PONTO", ignoreCase = true) || pdfFile.name.contains("espelho", ignoreCase = true)
                    
                    val targetFolderId = if (isPonto) folderPontoId else folderReciboId
                    val map = if (isPonto) mapPonto else mapRecibo
                    
                    uploadOrUpdateFileOptimized(service, targetFolderId, pdfFile.name, "application/pdf", pdfFile, map[pdfFile.name])
                }
            }

            onProgress("Backup de PDFs concluído (Estruturado)!")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("DriveBackup", "Erro no backup de PDFs", e)
            Result.failure(e)
        }
    }

    suspend fun restoreNow(account: GoogleSignInAccount, onProgress: (String) -> Unit = {}): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val service = getDriveService(account)
            onProgress("Localizando pasta de backup...")
            
            val folderId = findBackupFolder(service) ?: return@withContext Result.failure(Exception("Pasta de backup não encontrada no Google Drive."))

            // 1. Obter IDs das subpastas (se existirem)
            onProgress("Acessando estrutura na nuvem...")
            val folderPontoId = findSubFolder(service, folderId, "PONTO")
            val folderReciboId = findSubFolder(service, folderId, "RECIBO")

            val allDriveFiles = mutableListOf<Pair<File, String>>() // Pair(DriveFile, LocalCategory)

            // 2. Listar arquivos de todas as fontes possíveis (PONTO, RECIBO e RAIZ do backup)
            onProgress("Escaneando arquivos no Drive...")
            
            // Subpasta PONTO
            if (folderPontoId != null) {
                allDriveFiles.addAll(listAllPdfFilesPageable(service, folderPontoId).map { it to "PONTO" })
            }
            
            // Subpasta RECIBO
            if (folderReciboId != null) {
                allDriveFiles.addAll(listAllPdfFilesPageable(service, folderReciboId).map { it to "RECIBO" })
            }
            
            // Raiz da pasta de backup (legado)
            allDriveFiles.addAll(listAllPdfFilesPageable(service, folderId).map { it to "ROOT" })

            val pdfDir = java.io.File(context.filesDir, "pdfs")
            if (!pdfDir.exists()) pdfDir.mkdirs()
            
            val pontoDir = java.io.File(pdfDir, "PONTO")
            val reciboDir = java.io.File(pdfDir, "RECIBO")
            if (!pontoDir.exists()) pontoDir.mkdirs()
            if (!reciboDir.exists()) reciboDir.mkdirs()

            var count = 0
            val total = allDriveFiles.size
            
            if (total == 0) {
                onProgress("Nenhum PDF encontrado para restaurar.")
                return@withContext Result.success(Unit)
            }

            allDriveFiles.distinctBy { it.first.id }.forEach { (driveFile, category) ->
                count++
                onProgress("Baixando PDF $count/$total: ${driveFile.name}")
                
                // Determinar destino local
                val targetDir = when (category) {
                    "PONTO" -> pontoDir
                    "RECIBO" -> reciboDir
                    else -> {
                        // Se for da ROOT, tenta adivinhar pelo nome
                        if (driveFile.name.contains("espelho", ignoreCase = true) || driveFile.name.contains("ponto", ignoreCase = true)) {
                            pontoDir
                        } else {
                            reciboDir
                        }
                    }
                }
                
                val localFile = java.io.File(targetDir, driveFile.name)
                if (!localFile.exists()) {
                    try {
                        service.files().get(driveFile.id).executeMediaAndDownloadTo(FileOutputStream(localFile))
                    } catch (e: Exception) {
                        Log.e("DriveBackup", "Falha ao baixar ${driveFile.name}", e)
                    }
                }
            }

            onProgress("Restauração de PDFs concluída!")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("DriveBackup", "Erro na restauração de PDFs", e)
            Result.failure(e)
        }
    }

    private fun findSubFolder(service: Drive, parentFolderId: String, folderName: String): String? {
        val result = service.files().list()
            .setQ("name = '$folderName' and '$parentFolderId' in parents and mimeType = 'application/vnd.google-apps.folder' and trashed = false")
            .setSpaces("drive")
            .setFields("files(id)")
            .execute()
        return result.files?.firstOrNull()?.id
    }

    private fun listAllPdfFilesPageable(service: Drive, folderId: String): List<File> {
        val allFiles = mutableListOf<File>()
        var pageToken: String? = null
        val query = "'$folderId' in parents and mimeType = 'application/pdf' and trashed = false"
        
        do {
            val result = service.files().list()
                .setQ(query)
                .setSpaces("drive")
                .setFields("nextPageToken, files(id, name)")
                .setPageToken(pageToken)
                .execute()
            
            result.files?.let { allFiles.addAll(it) }
            pageToken = result.nextPageToken
        } while (pageToken != null)
        
        return allFiles
    }

    private fun getOrCreateBackupFolder(service: Drive): String {
        val existingFolder = findBackupFolder(service)
        if (existingFolder != null) return existingFolder

        val folderMetadata = File().apply {
            name = "Meu Holerite Backup"
            mimeType = "application/vnd.google-apps.folder"
        }
        val folder = service.files().create(folderMetadata).setFields("id").execute()
        return folder.id
    }

    private fun getOrCreateSubFolder(service: Drive, parentFolderId: String, folderName: String): String {
        val result = service.files().list()
            .setQ("name = '$folderName' and '$parentFolderId' in parents and mimeType = 'application/vnd.google-apps.folder' and trashed = false")
            .setSpaces("drive")
            .setFields("files(id)")
            .execute()
        val existing = result.files?.firstOrNull()?.id
        if (existing != null) return existing
        
        val folderMetadata = File().apply {
            name = folderName
            mimeType = "application/vnd.google-apps.folder"
            parents = Collections.singletonList(parentFolderId)
        }
        val folder = service.files().create(folderMetadata).setFields("id").execute()
        return folder.id
    }

    private fun findBackupFolder(service: Drive): String? {
        val result: FileList = service.files().list()
            .setQ("name = 'Meu Holerite Backup' and mimeType = 'application/vnd.google-apps.folder' and trashed = false")
            .setSpaces("drive")
            .setFields("files(id, name)")
            .execute()
        return result.files?.firstOrNull()?.id
    }

    private fun listFilesMapInFolder(service: Drive, folderId: String): Map<String, String> {
        val files = mutableMapOf<String, String>()
        var pageToken: String? = null
        do {
            val result = service.files().list()
                .setQ("'$folderId' in parents and trashed = false")
                .setSpaces("drive")
                .setFields("nextPageToken, files(id, name)")
                .setPageToken(pageToken)
                .execute()
            
            result.files?.forEach { file ->
                // Guardar apenas o primeiro encontrado (se houver duplicatas no drive, vamos tratar depois)
                if (!files.containsKey(file.name)) {
                    files[file.name] = file.id
                } else {
                    // SE JÁ EXISTE UM COM ESSE NOME, DELETAR A DUPLICATA EXTRA PARA LIMPAR O DRIVE
                    try { service.files().delete(file.id).execute() } catch (e: Exception) {}
                }
            }
            pageToken = result.nextPageToken
        } while (pageToken != null)
        return files
    }

    private fun uploadOrUpdateFileOptimized(service: Drive, folderId: String, fileName: String, mime: String, localFile: java.io.File, existingFileId: String?) {
        val fileMetadata = File().apply {
            name = fileName
            if (existingFileId == null) {
                parents = Collections.singletonList(folderId)
            }
        }
        val mediaContent = FileContent(mime, localFile)

        try {
            if (existingFileId != null) {
                service.files().update(existingFileId, fileMetadata, mediaContent).execute()
            } else {
                service.files().create(fileMetadata, mediaContent).setFields("id").execute()
            }
        } catch (e: Exception) {
            // Se falhar o update (talvez arquivo deletado manualmente no meio tempo), tenta criar
            if (existingFileId != null) {
                fileMetadata.parents = Collections.singletonList(folderId)
                service.files().create(fileMetadata, mediaContent).setFields("id").execute()
            }
        }
    }

    private suspend fun getBackupDataMap(): Map<String, Any> {
        val espelhos = db.espelhoDao().getAll()
        val recibos = db.reciboDao().getAll()
        val expenses = db.financeExpenseDao().getAll()
        
        val userPrefs = context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
        val settingsPrefs = context.getSharedPreferences("meu_holerite_prefs", Context.MODE_PRIVATE)

        return mapOf(
            "version" to 1,
            "userName" to (userPrefs.getString("user_name", "") ?: ""),
            "userMatricula" to (userPrefs.getString("user_matricula", "") ?: ""),
            "settings" to mapOf(
                "dark_mode" to settingsPrefs.getBoolean("dark_mode", false),
                "app_lock_enabled" to settingsPrefs.getBoolean("app_lock_enabled", false),
                "app_lock_pin" to (settingsPrefs.getString("app_lock_pin", "") ?: "")
            ),
            "espelhos" to espelhos,
            "recibos" to recibos,
            "expenses" to expenses
        )
    }

    private suspend fun restoreFromMap(map: Map<*, *>) {
        val version = (map["version"] as? Number)?.toInt() ?: 1
        val userPrefs = context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE).edit()
        userPrefs.putString("user_name", map["userName"] as? String ?: "")
        userPrefs.putString("user_matricula", map["userMatricula"] as? String ?: "")
        userPrefs.apply()

        (map["settings"] as? Map<*, *>)?.let { settings ->
            context.getSharedPreferences("meu_holerite_prefs", Context.MODE_PRIVATE).edit().apply {
                putBoolean("dark_mode", settings["dark_mode"] as? Boolean ?: false)
                putBoolean("app_lock_enabled", settings["app_lock_enabled"] as? Boolean ?: false)
                putString("app_lock_pin", settings["app_lock_pin"] as? String ?: "")
                apply()
            }
        }

        // Limpar Tabelas Atuais antes de restaurar (Evita Duplicatas)
        db.espelhoDao().deleteAll()
        db.reciboDao().deleteAll()
        db.financeExpenseDao().deleteAll()

        (map["espelhos"] as? List<*>)?.let { list ->
            list.filterIsInstance<Map<String, Any>>().forEach { m ->
                val json = gson.toJson(m)
                val entity = gson.fromJson(json, com.jack.meuholerite.database.EspelhoEntity::class.java)
                db.espelhoDao().insert(entity)
            }
        }

        (map["recibos"] as? List<*>)?.let { list ->
            list.filterIsInstance<Map<String, Any>>().forEach { m ->
                val json = gson.toJson(m)
                val entity = gson.fromJson(json, com.jack.meuholerite.database.ReciboEntity::class.java)
                db.reciboDao().insert(entity)
            }
        }

        (map["expenses"] as? List<*>)?.let { list ->
            list.filterIsInstance<Map<String, Any>>().forEach { m ->
                val json = gson.toJson(m)
                val entity = gson.fromJson(json, com.jack.meuholerite.database.FinanceExpenseEntity::class.java)
                db.financeExpenseDao().insert(entity)
            }
        }
    }

    suspend fun getBackupInfo(account: GoogleSignInAccount): Pair<Int, String>? = withContext(Dispatchers.IO) {
        try {
            val service = getDriveService(account)
            val result = service.files().list()
                .setQ("name = 'Meu Holerite Backup' and mimeType = 'application/vnd.google-apps.folder' and trashed = false")
                .setSpaces("drive")
                .setFields("files(id, modifiedTime)")
                .execute()
            
            val folder = result.files?.firstOrNull()
            if (folder != null) {
                val pontoId = findSubFolder(service, folder.id, "PONTO")
                val reciboId = findSubFolder(service, folder.id, "RECIBO")
                
                var total = 0
                if (pontoId != null) total += listAllPdfFilesPageable(service, pontoId).size
                if (reciboId != null) total += listAllPdfFilesPageable(service, reciboId).size
                total += listAllPdfFilesPageable(service, folder.id).size
                
                val modifiedStr = folder.modifiedTime?.value?.let {
                    java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault()).format(java.util.Date(it))
                } ?: "Desconhecido"
                
                return@withContext Pair(total, modifiedStr)
            }
        } catch (e: Exception) {
            Log.e("DriveBackup", "Error getting backup info", e)
        }
        return@withContext null
    }

    suspend fun deleteBackup(account: GoogleSignInAccount): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val service = getDriveService(account)
            val folderId = findBackupFolder(service) ?: return@withContext Result.success(Unit)
            service.files().delete(folderId).execute()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
