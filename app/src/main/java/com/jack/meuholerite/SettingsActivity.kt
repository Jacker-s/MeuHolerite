package com.jack.meuholerite

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.CookieManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.Canvas as ComposeCanvas
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import androidx.core.os.LocaleListCompat
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import com.jack.meuholerite.database.AppDatabase
import com.jack.meuholerite.ui.SectionHeader
import com.jack.meuholerite.ui.theme.AppThemePalettes
import com.jack.meuholerite.ui.theme.MeuHoleriteTheme
import com.jack.meuholerite.parser.PontoParser
import com.jack.meuholerite.parser.ReciboParser
import com.jack.meuholerite.utils.extractStartDate
import com.jack.meuholerite.utils.BackupManager
import com.jack.meuholerite.utils.GoogleDriveBackupManager
import com.jack.meuholerite.utils.StorageManager
import com.jack.meuholerite.ads.AdsDataStore
import com.jack.meuholerite.ads.RewardedInterstitialAdManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val storageManager = StorageManager(this)
        val driveManager = GoogleDriveBackupManager(this)
        val firestoreManager = BackupManager(this)
        
        val gso = com.google.android.gms.auth.api.signin.GoogleSignInOptions.Builder(com.google.android.gms.auth.api.signin.GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(com.google.android.gms.common.api.Scope(com.google.api.services.drive.DriveScopes.DRIVE_FILE))
            .build()
        val googleSignInClient = com.google.android.gms.auth.api.signin.GoogleSignIn.getClient(this, gso)

        val currentVersion = try {
            packageManager.getPackageInfo(packageName, 0).versionName ?: "1.0"
        } catch (_: Exception) {
            "1.0"
        }

        setContent {
            val systemInDarkTheme = isSystemInDarkTheme()
            var useDarkTheme by remember {
                val hasSet = storageManager.hasDarkModeSet()
                mutableStateOf(if (hasSet) storageManager.isDarkMode() else systemInDarkTheme)
            }
            var themeAccent by remember { mutableStateOf(storageManager.getThemeAccent()) }

            MeuHoleriteTheme(darkTheme = useDarkTheme, themeAccent = themeAccent) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val prefs = remember { getSharedPreferences("user_prefs", MODE_PRIVATE) }
                    val userName by remember { mutableStateOf(prefs.getString("user_name", "") ?: "") }
                    val userPhoto by remember { mutableStateOf(prefs.getString("user_photo", "") ?: "") }
                    var showAccountDetails by remember { mutableStateOf(false) }
                    val scope = rememberCoroutineScope()
                    val googleSignInLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.StartActivityForResult()
                    ) { _ -> }
                    val activity = this@SettingsActivity

                    val billingManager = remember { com.jack.meuholerite.utils.BillingManager(this@SettingsActivity) }
                    var showRemoveAdsDialog by remember { mutableStateOf(false) }

                    LaunchedEffect(Unit) {
                        billingManager.startConnection()
                    }

                    val isAdsRemoved by com.jack.meuholerite.ads.AdsDataStore.isAdsRemovedFlow(this@SettingsActivity).collectAsState(initial = false)

                    fun exitScreen() {
                        finish()
                    }

                    SettingsScreen(
                        storage = storageManager,
                        driveManager = driveManager,
                        firestoreManager = firestoreManager,
                        currentVersion = currentVersion,
                        userName = userName,
                        userPhoto = userPhoto,
                        onAccountClick = { showAccountDetails = true },
                        onConnectGoogle = {
                            googleSignInLauncher.launch(googleSignInClient.signInIntent)
                        },
                        onRecoverableAuth = { intent ->
                            googleSignInLauncher.launch(intent)
                        },
                        isAdsRemoved = isAdsRemoved,
                        onRemoveAdsClick = { showRemoveAdsDialog = true },
                        isDarkTheme = useDarkTheme,
                        selectedThemeAccent = themeAccent,
                        onToggleDarkMode = { enabled ->
                            storageManager.setDarkMode(enabled)
                            useDarkTheme = enabled
                            scope.launch { 
                                val account = com.google.android.gms.auth.api.signin.GoogleSignIn.getLastSignedInAccount(this@SettingsActivity)
                                if (account != null) {
                                    driveManager.backupNow(account) { /* quiet backup */ }
                                    firestoreManager.backupData { /* quiet backup */ }
                                }
                            }
                        },
                        onThemeAccentSelected = { accent ->
                            storageManager.setThemeAccent(accent)
                            themeAccent = accent
                            scope.launch {
                                val account = com.google.android.gms.auth.api.signin.GoogleSignIn.getLastSignedInAccount(this@SettingsActivity)
                                if (account != null) {
                                    driveManager.backupNow(account) { }
                                    firestoreManager.backupData { }
                                }
                            }
                        },
                        onBack = ::exitScreen
                    )

                    if (showAccountDetails) {
                        AccountDetailsDialog(
                            backupManager = driveManager,
                            onDismiss = { showAccountDetails = false },
                            onConnectGoogle = {
                                googleSignInLauncher.launch(googleSignInClient.signInIntent)
                            }
                        )
                    }

                    if (showRemoveAdsDialog) {
                        RemoveAdsDialog(
                            onDismiss = { showRemoveAdsDialog = false },
                            onPurchase = {
                                billingManager.launchPurchaseFlow(this@SettingsActivity)
                            }
                        )
                    }
                }
            }
        }
    }
}

/* =========================
   DIALOG: PROGRESSO BACKUP/RESTAURA
   ========================= */

@Composable
fun BackupRestoreProgressDialog(isBackup: Boolean, progress: Int, message: String? = null) {
    val title = if (isBackup) "Fazendo Backup" else "Restaurando Dados"
    val description = message ?: (if (isBackup) "Enviando seus dados para a nuvem de forma segura..." else "Baixando seus dados da nuvem...")
    val icon = if (isBackup) Icons.Outlined.CloudUpload else Icons.Outlined.CloudDownload
    val color = if (isBackup) Color(0xFF007AFF) else Color(0xFF5856D6)

    Dialog(
        onDismissRequest = { },
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
    ) {
        Surface(
            modifier = Modifier.width(300.dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Animação de envio/download
                val infiniteTransition = rememberInfiniteTransition(label = "infinite")
                val dy by infiniteTransition.animateFloat(
                    initialValue = 0f,
                    targetValue = if (isBackup) -15f else 15f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1000, easing = LinearEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "dy"
                )

                Box(
                    modifier = Modifier.size(100.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        progress = { progress / 100f },
                        modifier = Modifier.fillMaxSize(),
                        color = color,
                        strokeWidth = 6.dp,
                        trackColor = color.copy(alpha = 0.15f),
                        strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                    )
                    
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            modifier = Modifier
                                .size(32.dp)
                                .offset(y = dy.dp),
                            tint = color
                        )
                        Text(
                            text = "$progress%",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = color
                        )
                    }
                }

                Text(title, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    description,
                    textAlign = TextAlign.Center,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 20.sp
                )
            }
        }
    }
}

/* =========================
   DIALOG: DETALHES DA CONTA
   ========================= */

@Composable
fun AccountDetailsDialog(backupManager: GoogleDriveBackupManager, onDismiss: () -> Unit, onConnectGoogle: () -> Unit) {
    val context = LocalContext.current
    var account by remember { mutableStateOf(com.google.android.gms.auth.api.signin.GoogleSignIn.getLastSignedInAccount(context)) }
    val scope = rememberCoroutineScope()

    var backupInfo by remember { mutableStateOf<Pair<Int, String>?>(null) }
    var isLoadingInfo by remember { mutableStateOf(false) }

    // Tentar recuperar conta e informações do backup
    LaunchedEffect(account) {
        if (account == null) {
            val gso = com.google.android.gms.auth.api.signin.GoogleSignInOptions.Builder(com.google.android.gms.auth.api.signin.GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestScopes(com.google.android.gms.common.api.Scope(com.google.api.services.drive.DriveScopes.DRIVE_FILE))
                .build()
            val client = com.google.android.gms.auth.api.signin.GoogleSignIn.getClient(context, gso)
            client.silentSignIn().addOnSuccessListener { restored ->
                account = restored
            }
        } else {
            isLoadingInfo = true
            backupInfo = backupManager.getBackupInfo(account!!)
            isLoadingInfo = false
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Conta de Backup", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                val currentAccount = account
                if (currentAccount != null) {
                    AccountDetailRow(
                        Icons.Outlined.Person,
                        "Conta Google",
                        currentAccount.displayName ?: "Usuário Google"
                    )
                    AccountDetailRow(Icons.Outlined.Email, "E-mail", currentAccount.email ?: "")

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))

                    if (isLoadingInfo) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.height(8.dp))
                            Text("Buscando informações do backup...", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else if (backupInfo != null) {
                        AccountDetailRow(Icons.Outlined.Folder, "Arquivos Salvos", "${backupInfo!!.first} PDFs")
                        AccountDetailRow(Icons.Outlined.Schedule, "Último Backup", backupInfo!!.second)
                    } else {
                        Text("Nenhum backup encontrado na nuvem.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))

                    Text(
                        "Seu backup está sendo realizado na pasta 'Meu Holerite Backup' do seu Google Drive.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.CloudOff, null, modifier = Modifier.size(48.dp), tint = Color.Gray)
                        Spacer(Modifier.height(8.dp))
                        Text("Acesso ao Google Drive pendente", fontWeight = FontWeight.Bold)
                        Text("Para realizar o backup na nuvem, precisamos de permissão para criar arquivos no seu Google Drive.", textAlign = TextAlign.Center, fontSize = 14.sp)
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = onConnectGoogle,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Vincular Google Drive")
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
        shape = RoundedCornerShape(28.dp)
    )
}

@Composable
fun AccountDetailRow(icon: ImageVector, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(16.dp))
        Column {
            Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, letterSpacing = 0.5.sp)
            Text(value, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

/* =========================
   TELA SETTINGS (PRO)
   + PDFs: ver / exportar tudo (ZIP)
   ========================= */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    storage: StorageManager,
    driveManager: GoogleDriveBackupManager,
    firestoreManager: BackupManager,
    currentVersion: String,
    userName: String,
    userPhoto: String,
    onAccountClick: () -> Unit,
    onConnectGoogle: () -> Unit,
    onRecoverableAuth: (Intent) -> Unit,
    isAdsRemoved: Boolean,
    onRemoveAdsClick: () -> Unit,
    isDarkTheme: Boolean,
    selectedThemeAccent: String,
    onToggleDarkMode: (Boolean) -> Unit,
    onThemeAccentSelected: (String) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    BackHandler(onBack = onBack)
    val scope = rememberCoroutineScope()

    var backingUp by remember { mutableStateOf(false) }
    var restoring by remember { mutableStateOf(false) }
    var operationProgress by remember { mutableIntStateOf(0) }
    var operationMessage by remember { mutableStateOf<String?>(null) }
    
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showLogoutConfirm by remember { mutableStateOf(false) }

    val auth = remember { FirebaseAuth.getInstance() }
    val currentUser = auth.currentUser

    var appLockEnabled by remember { mutableStateOf(storage.isAppLockEnabled()) }
    var privacyModeEnabled by remember { mutableStateOf(storage.isHideValuesEnabled()) }
    var versionTapCount by remember { mutableIntStateOf(0) }
    var showEasterEgg by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }

    // PDFs
    var showPdfBrowser by remember { mutableStateOf(false) }
    var pdfItems by remember { mutableStateOf<List<PdfItem>>(emptyList()) }
    var loadingPdfs by remember { mutableStateOf(false) }
    var exportingAll by remember { mutableStateOf(false) }
    var reimportingAll by remember { mutableStateOf(false) }
    var pdfToDelete by remember { mutableStateOf<PdfItem?>(null) }
    
    var showBackupSelection by remember { mutableStateOf(false) }
    var showRestoreSelection by remember { mutableStateOf(false) }

    // Idioma atual

    fun openPdfPath(filePath: String) {
        try {
            val file = File(filePath)
            if (!file.exists()) {
                Toast.makeText(context, "PDF não encontrado no armazenamento.", Toast.LENGTH_SHORT).show()
                return
            }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intentView = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/pdf")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
            }
            context.startActivity(Intent.createChooser(intentView, context.getString(R.string.open_pdf_with)))
        } catch (e: Exception) {
            Toast.makeText(context, "Erro ao abrir PDF: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    suspend fun loadSavedAndCloudRestoredPdfs() {
        loadingPdfs = true
        pdfItems = emptyList()

        val items = withContext(Dispatchers.IO) {
            val list = mutableListOf<PdfItem>()

            // 1) PDFs salvos localmente
            val localDir = File(context.filesDir, "pdfs")
            if (localDir.exists()) {
                localDir.walkTopDown()
                    .filter { it.isFile && it.extension.equals("pdf", true) }
                    .forEach { f ->
                        val isPonto = f.absolutePath.contains("/PONTO", ignoreCase = true) || f.name.contains("espelho", ignoreCase = true)
                        val cat = if (isPonto) "Ponto" else "Recibo"
                        list.add(
                            PdfItem(
                                title = f.nameWithoutExtension,
                                subtitle = "Local • $cat • ${formatBytes(f.length())}",
                                path = f.absolutePath,
                                source = PdfSource.LOCAL,
                                lastModified = f.lastModified(),
                                category = cat
                            )
                        )
                    }
            }

            // 2) Pastas candidatas de PDFs restaurados
            val candidates = listOf(
                File(context.filesDir, "pdfs_cloud"),
                File(context.filesDir, "restored_pdfs"),
                File(context.cacheDir, "pdfs_cloud")
            )
            candidates.forEach { dir ->
                if (dir.exists()) {
                    dir.walkTopDown()
                        .filter { it.isFile && it.extension.equals("pdf", true) }
                        .forEach { f ->
                            val isPonto = f.absolutePath.contains("/PONTO", ignoreCase = true) || f.name.contains("espelho", ignoreCase = true)
                            val cat = if (isPonto) "Ponto" else "Recibo"
                            list.add(
                                PdfItem(
                                    title = f.nameWithoutExtension,
                                    subtitle = "Nuvem (restaurado) • $cat • ${formatBytes(f.length())}",
                                    path = f.absolutePath,
                                    source = PdfSource.CLOUD_RESTORED,
                                    lastModified = f.lastModified(),
                                    category = cat
                                )
                            )
                        }
                }
            }

            list.distinctBy { it.path }.sortedByDescending { it.lastModified }
        }

        pdfItems = items
        loadingPdfs = false
    }

    suspend fun exportAllPdfsZip() {
        if (exportingAll) return
        exportingAll = true

        try {
            val items = if (pdfItems.isNotEmpty()) pdfItems else withContext(Dispatchers.IO) {
                // se ainda não carregou a lista, busca direto no /files/pdfs
                val dir = File(context.filesDir, "pdfs")
                val list = mutableListOf<PdfItem>()
                if (dir.exists()) {
                    dir.walkTopDown()
                        .filter { it.isFile && it.extension.equals("pdf", true) }
                        .forEach { f ->
                            val cat = if (f.absolutePath.contains("/PONTO", true) || f.name.contains("espelho", ignoreCase = true)) "Ponto" else "Recibo"
                            list.add(
                                PdfItem(
                                    title = f.nameWithoutExtension,
                                    subtitle = "Local • $cat • ${formatBytes(f.length())}",
                                    path = f.absolutePath,
                                    source = PdfSource.LOCAL,
                                    lastModified = f.lastModified(),
                                    category = cat
                                )
                            )
                        }
                }
                list.sortedByDescending { it.lastModified }
            }

            if (items.isEmpty()) {
                Toast.makeText(context, "Nenhum PDF para exportar.", Toast.LENGTH_SHORT).show()
                exportingAll = false
                return
            }

            val sdf = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault())
            val zipName = "meu_holerite_pdfs_${sdf.format(Date())}.zip"
            val outDir = File(context.cacheDir, "exports").apply { if (!exists()) mkdirs() }
            val zipFile = File(outDir, zipName)
            if (zipFile.exists()) zipFile.delete()

            withContext(Dispatchers.IO) {
                ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
                    val usedNames = HashSet<String>()
                    items.forEach { item ->
                        val src = File(item.path)
                        if (!src.exists() || !src.isFile) return@forEach

                        // Garante nome único dentro do ZIP
                        val base = src.name
                        val entryName = generateUniqueName(base, usedNames)
                        usedNames.add(entryName)

                        zos.putNextEntry(ZipEntry(entryName))
                        FileInputStream(src).use { fis ->
                            fis.copyTo(zos, bufferSize = 8 * 1024)
                        }
                        zos.closeEntry()
                    }
                }
            }

            val zipUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", zipFile)
            val share = Intent(Intent.ACTION_SEND).apply {
                type = "application/zip"
                putExtra(Intent.EXTRA_STREAM, zipUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                putExtra(Intent.EXTRA_SUBJECT, "Exportar PDFs - Meu Holerite")
            }

            context.startActivity(Intent.createChooser(share, "Exportar PDFs"))
        } catch (e: Exception) {
            Toast.makeText(context, "Erro ao exportar: ${e.message}", Toast.LENGTH_LONG).show()
        } finally {
            exportingAll = false
        }
    }

    suspend fun organizeExistingPdfs() {
        if (reimportingAll) return
        reimportingAll = true
        
        try {
            withContext(Dispatchers.IO) {
                val db = AppDatabase.getDatabase(context)
                val pdfReader = com.jack.meuholerite.utils.PdfReader(context)
                val pdfDir = File(context.filesDir, "pdfs")
                if (!pdfDir.exists()) return@withContext

                // Pegamos apenas os arquivos na RAIZ da pasta pdfs (os antigos)
                val files = pdfDir.listFiles()?.filter { it.isFile && it.extension.equals("pdf", true) } ?: return@withContext
                val puntoParser = PontoParser()
                val reciboParser = ReciboParser()

                files.forEach { file ->
                    try {
                        val uri = android.net.Uri.fromFile(file)
                        val pdfText = pdfReader.extractTextFromUri(uri)
                        if (pdfText.isNullOrBlank()) return@forEach
                        
                        val textToAnalyze = pdfText.uppercase()
                        val isEspelho = textToAnalyze.contains("ESPELHO DE PONTO") || textToAnalyze.contains("CARTÃO DE PONTO") || textToAnalyze.contains("HORAS")
                        
                        val tipo = if (isEspelho) "PONTO" else "RECIBO"
                        val periodo = if (isEspelho) puntoParser.parse(pdfText).periodo else reciboParser.parse(pdfText).periodo
                        
                        if (periodo != "Não identificado") {
                            // Extrair Ano e Mês para nova rota
                            val date = periodo.extractStartDate()
                            val cal = java.util.Calendar.getInstance()
                            cal.time = date
                            val year = cal.get(java.util.Calendar.YEAR).toString()
                            val month = (cal.get(java.util.Calendar.MONTH) + 1).toString().padStart(2, '0')

                            val targetDir = File(File(File(pdfDir, tipo), year), month)
                            if (!targetDir.exists()) targetDir.mkdirs()
                            
                            val destFile = File(targetDir, file.name)
                            if (file.renameTo(destFile)) {
                                // ATUALIZA O BANCO DE DADOS COM O NOVO CAMINHO
                                if (isEspelho) {
                                    val entity = db.espelhoDao().getAll().find { it.periodo == periodo }
                                    if (entity != null) {
                                        db.espelhoDao().insert(entity.copy(pdfFilePath = destFile.absolutePath))
                                    }
                                } else {
                                    val entity = db.reciboDao().getAll().find { it.periodo == periodo }
                                    if (entity != null) {
                                        db.reciboDao().insert(entity.copy(pdfFilePath = destFile.absolutePath))
                                    }
                                }
                            }
                        }
                    } catch (_: Exception) { }
                }
            }
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Arquivos organizados com sucesso!", Toast.LENGTH_LONG).show()
                loadSavedAndCloudRestoredPdfs() // Atualiza a lista visual
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Erro na organização: ${e.message}", Toast.LENGTH_LONG).show()
            }
        } finally {
            reimportingAll = false
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Configurações", fontWeight = FontWeight.ExtraBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Voltar")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { innerPadding ->
        if (showBackupSelection) {
            BackupSelectionDialog(
                title = "O que você deseja salvar na nuvem?",
                onDismiss = { showBackupSelection = false },
                onOptionSelected = { syncData, syncPdf ->
                    showBackupSelection = false
                    val account = com.google.android.gms.auth.api.signin.GoogleSignIn.getLastSignedInAccount(context)
                    if (account != null && !backingUp) {
                        scope.launch {
                            backingUp = true
                            operationProgress = 0
                            val stepFactor = if (syncData && syncPdf) 2 else 1
                            val baseDataProg = if (syncPdf) 50 else 0

                            if (syncPdf) {
                                driveManager.backupNow(account) { msg ->
                                    operationMessage = msg
                                    if (operationProgress < (100 / stepFactor)) operationProgress += 5
                                }.onSuccess {
                                    operationProgress = 100 / stepFactor
                                }.onFailure { e ->
                                    if (e is UserRecoverableAuthIOException) onRecoverableAuth(e.intent)
                                    else Toast.makeText(context, "Erro Drive: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            }

                            if (syncData) {
                                firestoreManager.backupData { progress ->
                                    operationMessage = "Sincronizando dados..."
                                    operationProgress = baseDataProg + (progress / stepFactor)
                                }.onSuccess {
                                    Toast.makeText(context, "Dados salvos com sucesso!", Toast.LENGTH_SHORT).show()
                                }.onFailure {
                                    Toast.makeText(context, "Erro Firestore: ${it.message}", Toast.LENGTH_SHORT).show()
                                }
                            }

                            if (syncData && syncPdf) {
                                Toast.makeText(context, "Backup completo concluído!", Toast.LENGTH_SHORT).show()
                            }
                            operationMessage = null
                            backingUp = false
                        }
                    }
                }
            )
        }

        if (showRestoreSelection) {
            BackupSelectionDialog(
                title = "O que você deseja restaurar?",
                onDismiss = { showRestoreSelection = false },
                onOptionSelected = { syncData, syncPdf ->
                    showRestoreSelection = false
                    val account = com.google.android.gms.auth.api.signin.GoogleSignIn.getLastSignedInAccount(context)
                    if (account != null && !restoring) {
                        scope.launch {
                            restoring = true
                            operationProgress = 0
                            val stepFactor = if (syncData && syncPdf) 2 else 1
                            val baseDataProg = if (syncPdf) 50 else 0

                            if (syncPdf) {
                                driveManager.restoreNow(account) { msg ->
                                    operationMessage = msg
                                    if (operationProgress < (100 / stepFactor)) operationProgress += 5
                                }.onSuccess {
                                    operationProgress = 100 / stepFactor
                                }.onFailure { e ->
                                    if (e is UserRecoverableAuthIOException) onRecoverableAuth(e.intent)
                                    else Toast.makeText(context, "Erro Drive: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            }

                            if (syncData) {
                                firestoreManager.restoreData { progress ->
                                    operationMessage = "Recuperando dados..."
                                    operationProgress = baseDataProg + (progress / stepFactor)
                                }.onSuccess {
                                    Toast.makeText(context, "Dados recuperados!", Toast.LENGTH_SHORT).show()
                                }.onFailure {
                                    Toast.makeText(context, "Erro Firestore: ${it.message}", Toast.LENGTH_SHORT).show()
                                }
                            }

                            if (syncPdf) loadSavedAndCloudRestoredPdfs()
                            if (syncData && syncPdf) Toast.makeText(context, "Restauração completa!", Toast.LENGTH_SHORT).show()
                            
                            operationMessage = null
                            restoring = false
                        }
                    }
                }
            )
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(bottom = 32.dp, start = 16.dp, end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item { Spacer(Modifier.height(8.dp)) }

            // 👤 PERFIL REFORMULADO
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                    onClick = onAccountClick
                ) {
                    Row(
                        modifier = Modifier.padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (userPhoto.isNotEmpty()) {
                            AsyncImage(
                                model = userPhoto,
                                contentDescription = "Foto de perfil",
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .background(MaterialTheme.colorScheme.primary, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Outlined.Person, null, tint = Color.White, modifier = Modifier.size(32.dp))
                            }
                        }
                        Spacer(Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                userName.ifEmpty { "Usuário" },
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                currentUser?.email ?: "",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(Icons.Outlined.ChevronRight, null, tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                    }
                }
            }

            // 🛠️ PREFERÊNCIAS E SEGURANÇA
            item {
                SectionHeader("Preferências e Segurança", modifier = Modifier.padding(top = 16.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        ThemeAccentRow(
                            selectedThemeAccent = selectedThemeAccent,
                            onThemeAccentSelected = onThemeAccentSelected
                        )
                        HorizontalDivider(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                        SettingsToggleRow(
                            icon = Icons.Outlined.Lock,
                            label = "Senha / Biometria",
                            color = Color(0xFF007AFF),
                            checked = appLockEnabled,
                            onCheckedChange = { enabled ->
                                appLockEnabled = enabled
                                storage.setAppLockEnabled(enabled)
                                if (enabled && !storage.hasPin()) {
                                    Toast.makeText(context, context.getString(R.string.pin_toast), Toast.LENGTH_SHORT).show()
                                }
                                val account = com.google.android.gms.auth.api.signin.GoogleSignIn.getLastSignedInAccount(context)
                                if (account != null) {
                                    scope.launch { 
                                        driveManager.backupNow(account)
                                        firestoreManager.backupData()
                                    }
                                }
                            }
                        )
                    }
                }
            }

            // ☁️ BACKUP + PDFs
            item {
                SectionHeader("Backup e Sincronização", modifier = Modifier.padding(top = 16.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        SettingsActionRow(
                            icon = Icons.Outlined.FolderOpen,
                            label = "PDFs salvos",
                            color = Color(0xFFFF9500)
                        ) {
                            scope.launch {
                                showPdfBrowser = true
                                loadSavedAndCloudRestoredPdfs()
                            }
                        }

                        HorizontalDivider(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))

                        SettingsActionRow(
                            icon = Icons.Outlined.CloudUpload,
                            label = if (backingUp) "Salvando..." else "Salvar na Nuvem",
                            color = Color(0xFF007AFF)
                        ) {
                            val account = com.google.android.gms.auth.api.signin.GoogleSignIn.getLastSignedInAccount(context)
                            val user = FirebaseAuth.getInstance().currentUser
                            if (account == null || user == null) {
                                onConnectGoogle()
                                return@SettingsActionRow
                            }
                            if (!backingUp && !restoring) {
                                showBackupSelection = true
                            }
                        }

                        HorizontalDivider(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))

                        SettingsActionRow(
                            icon = Icons.Outlined.CloudDownload,
                            label = if (restoring) "Restaurando..." else "Restaurar da Nuvem",
                            color = Color(0xFF5856D6)
                        ) {
                            val account = com.google.android.gms.auth.api.signin.GoogleSignIn.getLastSignedInAccount(context)
                            val user = FirebaseAuth.getInstance().currentUser
                            if (account == null || user == null) {
                                onConnectGoogle()
                                return@SettingsActionRow
                            }
                            if (!backingUp && !restoring) {
                                showRestoreSelection = true
                            }
                        }
                    }
                }
            }

            // ⭐ PREMIUM
            if (!isAdsRemoved) {
                item {
                    SectionHeader(stringResource(R.string.premium_section), modifier = Modifier.padding(top = 16.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.1f),
                        border = BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            SettingsActionRow(
                                icon = Icons.Outlined.Stars,
                                label = stringResource(R.string.remover_anuncios),
                                color = Color(0xFF6200EE)
                            ) {
                                onRemoveAdsClick()
                            }
                        }
                    }
                }
            }

            // ⚠️ CONTA
            item {
                SectionHeader(stringResource(R.string.account_section), modifier = Modifier.padding(top = 16.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        SettingsActionRow(Icons.AutoMirrored.Outlined.Logout, stringResource(R.string.logout), MaterialTheme.colorScheme.primary) {
                            showLogoutConfirm = true
                        }
                        HorizontalDivider(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                        SettingsActionRow(Icons.Outlined.DeleteForever, stringResource(R.string.delete_data), Color(0xFFFF3B30)) {
                            showDeleteConfirm = true
                        }
                    }
                }
            }

            // ℹ️ SOBRE
            item {
                SectionHeader(stringResource(R.string.about_section), modifier = Modifier.padding(top = 16.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    versionTapCount++
                                    if (versionTapCount >= 7) {
                                        versionTapCount = 0
                                        scope.launch {
                                            AdsDataStore.setAdsRemoved(context, true)
                                            Toast.makeText(context, "Acesso PRO liberado!", Toast.LENGTH_SHORT).show()
                                        }
                                        showEasterEgg = true
                                    }
                                }
                                .padding(vertical = 12.dp, horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier.size(36.dp).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Outlined.Info, null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f), modifier = Modifier.size(20.dp))
                            }
                            Spacer(Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.version_label), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                                Text(currentVersion, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                            }
                        }

                        HorizontalDivider(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))

                        SettingsActionRow(Icons.Outlined.Info, stringResource(R.string.about_app), MaterialTheme.colorScheme.primary) {
                            showAboutDialog = true
                        }

                        HorizontalDivider(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))

                        SettingsActionRow(Icons.Outlined.Shield, stringResource(R.string.privacy_policy), Color(0xFF34C759)) {
                            context.startActivity(Intent(context, PrivacyPolicyActivity::class.java))
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(32.dp)) }
        }
    }

    /* =========================
       DIALOGS DE PROGRESSO
       ========================= */
    if (backingUp) {
        BackupRestoreProgressDialog(isBackup = true, progress = operationProgress, message = operationMessage)
    }
    if (restoring) {
        BackupRestoreProgressDialog(isBackup = false, progress = operationProgress, message = operationMessage)
    }

    /* =========================
       DIALOG: PDF BROWSER
       ========================= */
    if (showPdfBrowser) {
        PdfBrowserDialog(
            items = pdfItems,
            isLoading = loadingPdfs,
            exporting = exportingAll,
            isOrganizing = reimportingAll,
            onRefresh = { scope.launch { loadSavedAndCloudRestoredPdfs() } },
            onOpen = { openPdfPath(it.path) },
            onDelete = { pdfToDelete = it },
            onExportAll = {
                scope.launch {
                    if (pdfItems.isEmpty()) loadSavedAndCloudRestoredPdfs()
                    exportAllPdfsZip()
                }
            },
            onOrganize = {
                if (!reimportingAll) {
                    scope.launch {
                        organizeExistingPdfs()
                    }
                }
            },
            onDismiss = { showPdfBrowser = false }
        )
    }

    if (pdfToDelete != null) {
        AlertDialog(
            onDismissRequest = { pdfToDelete = null },
            title = { Text("Excluir PDF", fontWeight = FontWeight.Bold) },
            text = { Text("Tem certeza que deseja excluir o arquivo '${pdfToDelete?.title}'?\n\nIsso removerá o PDF do dispositivo e apagará os dados extraídos correspondentes do seu histórico.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        val item = pdfToDelete
                        pdfToDelete = null
                        if (item != null) {
                            scope.launch(Dispatchers.IO) {
                                val file = File(item.path)
                                if (file.exists()) file.delete()
                                
                                val db = AppDatabase.getDatabase(context)
                                val espelhos = db.espelhoDao().getAll().filter { it.pdfFilePath == item.path }
                                espelhos.forEach { db.espelhoDao().delete(it) }
                                
                                val recibos = db.reciboDao().getAll().filter { it.pdfFilePath == item.path }
                                recibos.forEach { db.reciboDao().delete(it) }
                                
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(context, "PDF e dados excluídos.", Toast.LENGTH_SHORT).show()
                                }
                                loadSavedAndCloudRestoredPdfs()
                            }
                        }
                    }
                ) { Text("Excluir", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { pdfToDelete = null }) { Text(stringResource(R.string.cancel)) }
            },
            shape = RoundedCornerShape(22.dp)
        )
    }

    /* =========================
       CONFIRMS (logout/delete/easter)
       ========================= */
    if (showLogoutConfirm) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirm = false },
            title = { Text(stringResource(R.string.logout_confirm_title), fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(stringResource(R.string.logout_confirm))

                    // BOTÃO 1: APENAS SAIR
                    Button(
                        onClick = {
                            scope.launch {
                                context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE).edit().clear().apply()
                                FirebaseAuth.getInstance().signOut()
                                showLogoutConfirm = false
                                val intent = Intent(context, LoginActivity::class.java)
                                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                context.startActivity(intent)
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(stringResource(R.string.exit_only))
                    }

                    // BOTÃO 2: SAIR E LIMPAR
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    val db = AppDatabase.getDatabase(context)
                                    db.clearAllTables()
                                    context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE).edit().clear().apply()
                                    val pdfDir = File(context.filesDir, "pdfs")
                                    if (pdfDir.exists()) pdfDir.deleteRecursively()
                                    withContext(Dispatchers.Main) {
                                        CookieManager.getInstance().removeAllCookies(null)
                                        CookieManager.getInstance().flush()
                                    }
                                    FirebaseAuth.getInstance().signOut()
                                }
                                showLogoutConfirm = false
                                val intent = Intent(context, LoginActivity::class.java)
                                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                context.startActivity(intent)
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
                    ) {
                        Text(stringResource(R.string.exit_and_clear))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLogoutConfirm = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
            shape = RoundedCornerShape(22.dp)
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.delete_confirm_title), fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.delete_confirm)) },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            val account = com.google.android.gms.auth.api.signin.GoogleSignIn.getLastSignedInAccount(context)
                            if (account != null) {
                                driveManager.deleteBackup(account)
                            }
                            firestoreManager.deleteBackup()
                            withContext(Dispatchers.IO) {
                                val db = AppDatabase.getDatabase(context)
                                db.clearAllTables()
                                context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE).edit().clear().apply()
                                context.getSharedPreferences("meu_holerite_prefs", Context.MODE_PRIVATE).edit().clear().apply()
                                val pdfDir = File(context.filesDir, "pdfs")
                                if (pdfDir.exists()) pdfDir.deleteRecursively()
                                withContext(Dispatchers.Main) {
                                    CookieManager.getInstance().removeAllCookies(null)
                                    CookieManager.getInstance().flush()
                                }
                                FirebaseAuth.getInstance().signOut()
                            }
                            showDeleteConfirm = false
                            val intent = Intent(context, LoginActivity::class.java)
                            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            context.startActivity(intent)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF3B30))
                ) { Text(stringResource(R.string.delete_all), color = Color.White) }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text(stringResource(R.string.cancel)) } },
            shape = RoundedCornerShape(22.dp)
        )
    }

    if (showEasterEgg) {
        CyberDeveloperEasterEgg(onDismiss = { showEasterEgg = false })
    }

    if (showAboutDialog) {
        AboutDialog(onDismiss = { showAboutDialog = false })
    }
}

@Composable
fun CyberDeveloperEasterEgg(onDismiss: () -> Unit) {
    val codeSnippet = remember {
        listOf(
            "// INITIALIZING JACKSON TERMINAL...",
            "// BYPASSING SECURITY PROTOCOLS...",
            "// ACCESS GRANTED: STATUS_PRO_UNLOCKED",
            "",
            "class JacksonDeveloper {",
            "    val identity = \"Jackson\"",
            "    val rank     = \"Legendary Developer\"",
            "    val passion  = \"Crafting perfect code\"",
            "    ",
            "    fun execute() {",
            "        while(alive) {",
            "            code(); optimize(); innovate();",
            "        }",
            "    }",
            "}",
            "",
            "// STACK: Kotlin + Compose + AI",
            "// MISSION: Build the best app."
        )
    }

    var visibleText by remember { mutableStateOf("") }
    val scrollState = rememberScrollState()

    LaunchedEffect(Unit) {
        val fullText = codeSnippet.joinToString("\n")
        fullText.forEachIndexed { index, _ ->
            delay(15L)
            visibleText = fullText.substring(0, index + 1)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.75f)
                .clip(RoundedCornerShape(24.dp))
                .background(Color.Black)
                .border(2.dp, Brush.linearGradient(listOf(Color(0xFF00F2FF), Color(0xFF006BFF))), RoundedCornerShape(24.dp))
        ) {
            // Matrix Rain Effect (Background)
            MatrixRainBackground()

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.85f))
            ) {
                // Cyber Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White.copy(alpha = 0.05f))
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(Color(0xFF00F2FF), CircleShape)
                            .shadow(4.dp, CircleShape, spotColor = Color(0xFF00F2FF))
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "TERMINAL://JACKSON_PRO",
                        color = Color(0xFF00F2FF),
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp
                    )
                    Spacer(Modifier.weight(1f))
                    Icon(
                        Icons.Outlined.Close,
                        null,
                        tint = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.size(20.dp).clickable { onDismiss() }
                    )
                }

                // Terminal Content
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(20.dp)
                        .verticalScroll(scrollState)
                ) {
                    Text(
                        text = buildAnnotatedCyberCode(visibleText + (if (System.currentTimeMillis() % 1000 < 500) "_" else "")),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp,
                        lineHeight = 22.sp
                    )
                }

                // Footer Status
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("SECURE_AUTH: OK", color = Color(0xFF34C759), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Text("PRO_MODE: ACTIVE", color = Color(0xFF00F2FF), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun MatrixRainBackground() {
    val infiniteTransition = rememberInfiniteTransition(label = "matrix")
    val progress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(tween(20000, easing = LinearEasing)),
        label = "progress"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val cw = 20.dp.toPx()
        val columns = (size.width / cw).toInt()
        val charSize = 14.sp.toPx()
        val h = size.height
        
        for (i in 0 until columns) {
            val speed = (i % 5 + 2) * 20f
            val yOffset = (progress * speed + i * 100f) % (h + 200f)
            
            drawContext.canvas.nativeCanvas.apply {
                val paint = android.graphics.Paint().apply {
                    val alpha = (25 * (1 - (yOffset / (h + 200f)))).toInt().coerceIn(0, 50)
                    color = android.graphics.Color.argb(alpha, 0, 242, 255)
                    textSize = charSize
                    typeface = android.graphics.Typeface.MONOSPACE
                }
                
                for (j in 0 until 10) {
                    val chars = ('0'..'9') + ('A'..'Z')
                    val char = chars.random().toString()
                    drawText(char, i * cw, yOffset - j * charSize, paint)
                }
            }
        }
    }
}

@Composable
fun buildAnnotatedCyberCode(text: String) = buildAnnotatedString {
    val keywords = listOf("class", "val", "fun", "while", "return", "if", "else")
    val comments = Regex("//.*")
    val strings = Regex("\".*?\"")
    val accessGranted = "ACCESS GRANTED"

    append(text)

    // Keywords (Cyan/Neon Blue)
    keywords.forEach { keyword ->
        var index = text.indexOf(keyword)
        while (index >= 0) {
            addStyle(SpanStyle(color = Color(0xFF00F2FF), fontWeight = FontWeight.Bold), index, index + keyword.length)
            index = text.indexOf(keyword, index + 1)
        }
    }

    // Comments (Dimmed Gray)
    comments.findAll(text).forEach { match ->
        addStyle(SpanStyle(color = Color.White.copy(alpha = 0.4f)), match.range.first, match.range.last + 1)
    }

    // Strings (Warm Cyan)
    strings.findAll(text).forEach { match ->
        addStyle(SpanStyle(color = Color(0xFF80FAFF)), match.range.first, match.range.last + 1)
    }
    
    // Highlight "ACCESS GRANTED"
    if (text.contains(accessGranted)) {
        val start = text.indexOf(accessGranted)
        addStyle(SpanStyle(color = Color(0xFF34C759), fontWeight = FontWeight.Black), start, start + accessGranted.length)
    }
}

/* =========================
   DIALOG: LISTA DE PDFs
   ========================= */

private enum class PdfSource { LOCAL, CLOUD_RESTORED }

private data class PdfItem(
    val title: String,
    val subtitle: String,
    val path: String,
    val source: PdfSource,
    val lastModified: Long,
    val category: String = "Outros"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PdfBrowserDialog(
    items: List<PdfItem>,
    isLoading: Boolean,
    exporting: Boolean,
    isOrganizing: Boolean,
    onRefresh: () -> Unit,
    onOpen: (PdfItem) -> Unit,
    onDelete: (PdfItem) -> Unit,
    onExportAll: () -> Unit,
    onOrganize: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.padding(18.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.FolderOpen, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.backups_saved), fontWeight = FontWeight.ExtraBold)
                    Text(
                        stringResource(R.string.local_cloud_restored),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
                IconButton(onClick = onRefresh) { Icon(Icons.Outlined.Refresh, null) }
            }
        },
        text = {
            Column {
                // Ações do topo (Exportar tudo e Organizar)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalButton(
                        onClick = onExportAll,
                        enabled = !exporting && !isLoading,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Outlined.Archive, null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (exporting) "Exportando..." else "Exportar Tudo")
                    }
                    OutlinedButton(
                        onClick = onOrganize,
                        enabled = !isOrganizing && !isLoading,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Outlined.FolderCopy, null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (isOrganizing) "Organizando..." else "Organizar")
                    }
                }

                Spacer(Modifier.height(4.dp))

                var selectedTabIndex by remember { mutableIntStateOf(0) }
                val tabs = listOf("Todos", "Pontos", "Recibos")

                TabRow(
                    selectedTabIndex = selectedTabIndex,
                    containerColor = Color.Transparent,
                    divider = { HorizontalDivider() }
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTabIndex == index,
                            onClick = { selectedTabIndex = index },
                            text = { 
                                Text(
                                    title, 
                                    fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Medium,
                                    color = if (selectedTabIndex == index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                ) 
                            }
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    val filteredItems = items.filter { 
                        when (selectedTabIndex) {
                            1 -> it.category == "Ponto"
                            2 -> it.category == "Recibo"
                            else -> true
                        }
                    }

                    if (isLoading) {
                        Box(Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    } else if (filteredItems.isEmpty()) {
                        Box(Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) {
                            Text("Nenhum PDF encontrado.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp),
                            contentPadding = PaddingValues(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(filteredItems) { it ->
                                PdfRow(item = it, onOpen = { onOpen(it) }, onDelete = { onDelete(it) })
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close))
            }
        },
        shape = RoundedCornerShape(22.dp)
    )
}

@Composable
private fun PdfRow(item: PdfItem, onOpen: () -> Unit, onDelete: () -> Unit) {
    val badgeColor = if (item.source == PdfSource.LOCAL) Color(0xFF007AFF) else Color(0xFF34C759)
    val badgeText = if (item.source == PdfSource.LOCAL) stringResource(R.string.local_badge) else stringResource(R.string.cloud_badge)

    Surface(
        onClick = onOpen,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(38.dp).background(badgeColor.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Outlined.PictureAsPdf, null, tint = badgeColor)
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(item.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                Text(item.subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            }
            Spacer(Modifier.width(10.dp))
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = badgeColor.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.35f))
            ) {
                Text(
                    badgeText,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = badgeColor
                )
            }
            Spacer(Modifier.width(4.dp))
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Outlined.Delete, contentDescription = "Excluir", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
            }
        }
    }
}

/* =========================
   COMPONENTES EXISTENTES REFORMULADOS
   ========================= */


@Composable
fun SettingsActionRow(icon: ImageVector, label: String, color: Color, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(vertical = 12.dp, horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(36.dp).background(color.copy(alpha = 0.1f), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(16.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface)
        Icon(Icons.Outlined.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f))
    }
}

@Composable
fun SettingsToggleRow(icon: ImageVector, label: String, color: Color, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 6.dp, horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(36.dp).background(color.copy(alpha = 0.1f), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(16.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface)
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedTrackColor = color)
        )
    }
}

@Composable
fun ThemeAccentRow(
    selectedThemeAccent: String,
    onThemeAccentSelected: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Outlined.Palette, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(16.dp))
            Column {
                Text("Tema de cores", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    "Escolha a cor principal do app.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AppThemePalettes.all.forEach { palette ->
                val selected = palette.key == selectedThemeAccent
                Surface(
                    onClick = { onThemeAccentSelected(palette.key) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    color = palette.primary.copy(alpha = 0.10f),
                    border = BorderStroke(
                        if (selected) 2.dp else 1.dp,
                        if (selected) palette.primary else palette.primary.copy(alpha = 0.22f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .background(palette.primary, CircleShape)
                            )
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .background(palette.tertiary, CircleShape)
                            )
                        }
                        Text(
                            palette.label,
                            fontSize = 11.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

/* =========================
   HELPERS
   ========================= */

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return "${String.format("%.1f", kb)} KB".replace(".", ",")
    val mb = kb / 1024.0
    if (mb < 1024) return "${String.format("%.1f", mb)} MB".replace(".", ",")
    val gb = mb / 1024.0
    return "${String.format("%.2f", gb)} GB".replace(".", ",")
}

private fun generateUniqueName(name: String, used: Set<String>): String {
    if (!used.contains(name)) return name
    val dot = name.lastIndexOf('.')
    val base = if (dot > 0) name.substring(0, dot) else name
    val ext = if (dot > 0) name.substring(dot) else ""
    var i = 2
    while (true) {
        val candidate = "${base}_$i$ext"
        if (!used.contains(candidate)) return candidate
        i++
    }
}

@Composable
fun BackupSelectionDialog(
    title: String,
    onDismiss: () -> Unit,
    onOptionSelected: (Boolean, Boolean) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = { onOptionSelected(true, true) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) { Text("Ambos (Dados e PDFs)") }
                
                OutlinedButton(
                    onClick = { onOptionSelected(true, false) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) { Text("Apenas Dados") }
                
                OutlinedButton(
                    onClick = { onOptionSelected(false, true) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) { Text("Apenas PDFs") }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
        shape = RoundedCornerShape(24.dp)
    )
}
