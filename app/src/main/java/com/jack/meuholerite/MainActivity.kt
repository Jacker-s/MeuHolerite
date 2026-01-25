package com.jack.meuholerite

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.Cursor
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.util.Base64
import android.view.MotionEvent
import android.webkit.*
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.os.LocaleListCompat
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.google.gson.Gson
import com.jack.meuholerite.R
import com.jack.meuholerite.database.AppDatabase
import com.jack.meuholerite.database.toEntity
import com.jack.meuholerite.database.toModel
import com.jack.meuholerite.model.EspelhoPonto
import com.jack.meuholerite.model.ReciboItem
import com.jack.meuholerite.model.ReciboPagamento
import com.jack.meuholerite.parser.PontoParser
import com.jack.meuholerite.parser.ReciboParser
import com.jack.meuholerite.ui.theme.MeuHoleriteTheme
import com.jack.meuholerite.utils.PdfReader
import com.jack.meuholerite.utils.UpdateManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs
import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.pm.PackageManager
import android.location.Location
import android.location.Geocoder
import android.location.LocationManager
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Inicialização Global do AdMob
        val requestConfiguration = RequestConfiguration.Builder()
            .setTestDeviceIds(listOf("0DE9BC5C143E849581BCCEF706786AFA"))
            .build()
        MobileAds.setRequestConfiguration(requestConfiguration)
        MobileAds.initialize(this) {}

        createNotificationChannel()
        enableEdgeToEdge()
        setContent {
            MeuHoleriteTheme {
                MainScreen(intent)
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Avisos de Falta"
            val descriptionText = "Notificações de faltas detectadas no espelho de ponto"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel("ABSENCE_ALERTS", name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}

fun showAbsenceNotification(context: Context, periodo: String, numFaltas: Int) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return
        }
    }

    val intent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
    }
    val pendingIntent: PendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)

    val builder = NotificationCompat.Builder(context, "ABSENCE_ALERTS")
        .setSmallIcon(R.drawable.ic_launcher_foreground) 
        .setContentTitle("Faltas Detectadas")
        .setContentText("Foram detectadas $numFaltas faltas no período $periodo.")
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .setContentIntent(pendingIntent)
        .setAutoCancel(true)

    with(NotificationManagerCompat.from(context)) {
        notify(1001, builder.build())
    }
}

@Composable
fun IosTopBar(
    userName: String,
    userMatricula: String,
    onAboutClick: () -> Unit,
    onEditClick: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Surface(
        color = Color(0xFFF2F2F7).copy(alpha = 0.95f),
        modifier = Modifier.fillMaxWidth().statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                val calendar = Calendar.getInstance()
                val hour = calendar.get(Calendar.HOUR_OF_DAY)
                val greeting = when (hour) {
                    in 5..12 -> "Bom dia,"
                    in 13..18 -> "Boa tarde,"
                    else -> "Boa noite,"
                }
                Text(
                    text = greeting,
                    fontSize = 13.sp,
                    color = Color.Gray,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = userName.ifEmpty { "Bem-vindo" },
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.5).sp
                )
            }

            Box {
                Surface(
                    onClick = { showMenu = true },
                    shape = CircleShape,
                    color = Color.White,
                    shadowElevation = 1.dp,
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (userName.isNotEmpty()) {
                            Text(
                                userName.take(1).uppercase(),
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF007AFF),
                                fontSize = 16.sp
                            )
                        } else {
                            Icon(Icons.Outlined.Person, null, tint = Color.Gray, modifier = Modifier.size(20.dp))
                        }
                    }
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    modifier = Modifier.background(Color.White).width(220.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(userName.ifEmpty { "Usuário" }, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (userMatricula.isNotEmpty()) {
                            Text("Matrícula: $userMatricula", fontSize = 12.sp, color = Color.Gray)
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 8.dp))
                    DropdownMenuItem(
                        text = { Text("Editar Meus Dados") },
                        onClick = {
                            showMenu = false
                            onEditClick()
                        },
                        leadingIcon = { Icon(Icons.Outlined.Edit, null, modifier = Modifier.size(20.dp)) }
                    )
                    DropdownMenuItem(
                        text = { Text("Sobre o App") },
                        onClick = {
                            showMenu = false
                            onAboutClick()
                        },
                        leadingIcon = { Icon(Icons.Outlined.Info, null, modifier = Modifier.size(20.dp)) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun MainScreen(intent: Intent? = null) {
    val pagerState = rememberPagerState(pageCount = { 4 })
    val scope = rememberCoroutineScope()

    val context = LocalContext.current
    val db = remember { AppDatabase.getDatabase(context) }
    val gson = remember { Gson() }
    val prefs = remember { context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE) }

    var selectedEspelho by remember { mutableStateOf<EspelhoPonto?>(null) }
    var selectedRecibo by remember { mutableStateOf<ReciboPagamento?>(null) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showAbsenceWarning by remember { mutableStateOf(false) }

    var userName by remember { mutableStateOf(prefs.getString("user_name", "") ?: "") }
    var userMatricula by remember { mutableStateOf(prefs.getString("user_matricula", "") ?: "") }
    var showOnboarding by remember { mutableStateOf(userName.isEmpty() || userMatricula.isEmpty()) }
    var selectedDeduction by remember { mutableStateOf<ReciboItem?>(null) }
    
    val updateManager = remember { UpdateManager(context) }
    var autoUpdateInfo by remember { mutableStateOf<Pair<String, String>?>(null) }
    val currentVersion = remember { context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0" }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Toast.makeText(context, "Permissão de notificação negada. Você não receberá avisos de falta.", Toast.LENGTH_LONG).show()
        }
    }

    fun openPdf(filePath: String?) {
        if (filePath == null) {
            Toast.makeText(context, "Arquivo PDF não disponível", Toast.LENGTH_SHORT).show()
            return
        }
        val file = File(filePath)
        if (!file.exists()) {
            Toast.makeText(context, "Arquivo não encontrado", Toast.LENGTH_SHORT).show()
            return
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
        }
        try {
            context.startActivity(Intent.createChooser(intent, "Abrir PDF com..."))
        } catch (e: Exception) {
            Toast.makeText(context, "Nenhum aplicativo encontrado para abrir PDF", Toast.LENGTH_SHORT).show()
        }
    }

    suspend fun savePdfPermanently(uri: Uri, fileName: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                val directory = File(context.filesDir, "pdfs")
                if (!directory.exists()) directory.mkdirs()
                val destFile = File(directory, fileName)
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(destFile).use { output ->
                        input.copyTo(output)
                    }
                }
                destFile.absolutePath
            } catch (e: Exception) {
                null
            }
        }
    }

    suspend fun refreshData() {
        withContext(Dispatchers.IO) {
            val list = db.espelhoDao().getAll().map { it.toModel(gson) }
            if (list.isNotEmpty()) {
                selectedEspelho = list.sortedByDescending { it.periodo.extractStartDate() }.first()
            }
            val listRecibos = db.reciboDao().getAll().map { it.toModel(gson) }
            if (listRecibos.isNotEmpty()) {
                selectedRecibo = listRecibos.sortedByDescending { it.periodo.extractStartDateForRecibo() }.first()
            }
        }
    }

    LaunchedEffect(Unit) {
        refreshData()
        updateManager.checkForUpdates(
            currentVersion = currentVersion,
            onUpdateAvailable = { version, url, _ ->
                autoUpdateInfo = version to url
            },
            onNoUpdate = {},
            onError = {}
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    LaunchedEffect(selectedEspelho) {
        val hasAbsences = selectedEspelho?.hasAbsences ?: false
        if (hasAbsences) {
            showAbsenceWarning = true
            showAbsenceNotification(context, selectedEspelho?.periodo ?: "", selectedEspelho?.diasFaltas?.size ?: 0)
        }
    }

    if (showOnboarding) {
        OnboardingDialog(
            initialName = userName,
            initialMatricula = userMatricula,
            onSave = { name, matricula ->
                userName = name
                userMatricula = matricula
                prefs.edit().putString("user_name", name).putString("user_matricula", matricula).apply()
                showOnboarding = false
            }
        )
    }

    LaunchedEffect(intent) {
        if (intent?.action == Intent.ACTION_VIEW && intent.type == "application/pdf") {
            intent.data?.let { uri ->
                val pdfReader = PdfReader(context)
                val text = pdfReader.extractTextFromUri(uri)
                if (text != null) {
                    val isPonto = text.contains("PONTO", true) || text.contains("ESPELHO", true) || text.contains("BATIDA", true)
                    val isRecibo = text.contains("PAGAMENTO", true) || text.contains("DEMONSTRATIVO", true) || text.contains("HOLERITE", true)

                    if (isPonto && !text.contains("DEMONSTRATIVO", true)) {
                        val novo = PontoParser().parse(text)
                        val path = savePdfPermanently(uri, "ponto_${novo.periodo.replace("/", "_")}.pdf")
                        val updatedNovo = novo.copy(pdfFilePath = path)
                        selectedEspelho = updatedNovo
                        db.espelhoDao().insert(updatedNovo.toEntity(gson, path))
                        pagerState.animateScrollToPage(3)
                    } else if (isRecibo) {
                        var novo = ReciboParser().parse(text)
                        if (novo.funcionario == "Não identificado" && userName.isNotEmpty()) {
                            novo = novo.copy(funcionario = userName, matricula = userMatricula)
                        }
                        val path = savePdfPermanently(uri, "recibo_${novo.periodo.replace("/", "_")}.pdf")
                        val updatedNovo = novo.copy(pdfFilePath = path)
                        selectedRecibo = updatedNovo
                        db.reciboDao().insert(updatedNovo.toEntity(gson, path))
                        pagerState.animateScrollToPage(2)
                    }
                }
            }
        }
    }

    if (showAboutDialog) {
        AboutDialog(onDismiss = { showAboutDialog = false })
    }

    if (showAbsenceWarning) {
        AbsenceWarningDialog(onDismiss = { showAbsenceWarning = false })
    }

    if (selectedDeduction != null) {
        DeductionDetailDialog(selectedDeduction!!) {
            selectedDeduction = null
        }
    }
    
    if (autoUpdateInfo != null) {
        AlertDialog(
            onDismissRequest = { autoUpdateInfo = null },
            title = { Text("Nova Atualização Disponível", fontWeight = FontWeight.Bold) },
            text = { Text("Uma nova versão (v${autoUpdateInfo!!.first}) está disponível para download. Deseja atualizar agora?") },
            confirmButton = {
                Button(
                    onClick = {
                        updateManager.downloadAndInstall(autoUpdateInfo!!.second, autoUpdateInfo!!.first)
                        autoUpdateInfo = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF34C759))
                ) {
                    Text("Atualizar")
                }
            },
            dismissButton = {
                TextButton(onClick = { autoUpdateInfo = null }) {
                    Text("Depois")
                }
            },
            shape = RoundedCornerShape(22.dp),
            containerColor = Color.White
        )
    }

    Scaffold(
        containerColor = Color(0xFFF2F2F7),
        topBar = {
            IosTopBar(
                userName = userName,
                userMatricula = userMatricula,
                onAboutClick = { showAboutDialog = true },
                onEditClick = { showOnboarding = true }
            )
        },
        bottomBar = {
            NavigationBar(containerColor = Color.White.copy(alpha = 0.95f)) {
                NavigationBarItem(
                    selected = pagerState.currentPage == 0,
                    onClick = { scope.launch { pagerState.animateScrollToPage(0) } },
                    icon = { Icon(if (pagerState.currentPage == 0) Icons.Filled.Home else Icons.Outlined.Home, stringResource(R.string.home)) },
                    label = { Text(stringResource(R.string.home)) }
                )
                NavigationBarItem(
                    selected = pagerState.currentPage == 1,
                    onClick = { scope.launch { pagerState.animateScrollToPage(1) } },
                    icon = { Icon(if (pagerState.currentPage == 1) Icons.Filled.Public else Icons.Outlined.Public, "ePays") },
                    label = { Text("ePays") }
                )
                NavigationBarItem(
                    selected = pagerState.currentPage == 2,
                    onClick = { scope.launch { pagerState.animateScrollToPage(2) } },
                    icon = { Icon(if (pagerState.currentPage == 2) Icons.Filled.Description else Icons.Outlined.Description, stringResource(R.string.receipts)) },
                    label = { Text(stringResource(R.string.receipts)) }
                )
                NavigationBarItem(
                    selected = pagerState.currentPage == 3,
                    onClick = { scope.launch { pagerState.animateScrollToPage(3) } },
                    icon = { Icon(if (pagerState.currentPage == 3) Icons.Filled.Schedule else Icons.Outlined.Schedule, stringResource(R.string.timesheet)) },
                    label = { Text(stringResource(R.string.timesheet)) }
                )
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                beyondViewportPageCount = 1,
                userScrollEnabled = true
            ) { page ->
                val animatedModifier = Modifier.graphicsLayer {
                    val pageOffset = (
                            (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
                            ).coerceIn(-1f, 1f)
                    alpha = 1f - abs(pageOffset)
                    scaleX = 1f - (abs(pageOffset) * 0.1f)
                    scaleY = 1f - (abs(pageOffset) * 0.1f)
                }

                Box(animatedModifier) {
                    when (page) {
                        0 -> HomeScreen(
                            selectedRecibo = selectedRecibo,
                            selectedEspelho = selectedEspelho,
                            onGoToRecibo = { scope.launch { pagerState.animateScrollToPage(2) } },
                            onGoToPonto = { scope.launch { pagerState.animateScrollToPage(3) } },
                            onOpenPdf = { openPdf(it) },
                            onDeductionClick = { selectedDeduction = it },
                            onRefresh = { refreshData() }
                        )
                        1 -> EpaysWebViewPage { uri ->
                            val pdfReader = PdfReader(context)
                            val text = pdfReader.extractTextFromUri(uri)
                            if (text != null) {
                                val isPonto = text.contains("PONTO", true) || text.contains("ESPELHO", true) || text.contains("BATIDA", true)
                                val isRecibo = text.contains("PAGAMENTO", true) || text.contains("DEMONSTRATIVO", true) || text.contains("HOLERITE", true)

                                if (isPonto && !text.contains("DEMONSTRATIVO", true)) {
                                    val novo = PontoParser().parse(text)
                                    scope.launch {
                                        val path = savePdfPermanently(uri, "ponto_${novo.periodo.replace("/", "_")}.pdf")
                                        val updatedNovo = novo.copy(pdfFilePath = path)
                                        selectedEspelho = updatedNovo
                                        db.espelhoDao().insert(updatedNovo.toEntity(gson, path))
                                        pagerState.animateScrollToPage(3)
                                    }
                                    Toast.makeText(context, "Espelho de ponto importado!", Toast.LENGTH_SHORT).show()
                                } else if (isRecibo) {
                                    var novo = ReciboParser().parse(text)
                                    if (novo.funcionario == "Não identificado" && userName.isNotEmpty()) {
                                        novo = novo.copy(funcionario = userName, matricula = userMatricula)
                                    }
                                    scope.launch {
                                        val path = savePdfPermanently(uri, "recibo_${novo.periodo.replace("/", "_")}.pdf")
                                        val updatedNovo = novo.copy(pdfFilePath = path)
                                        selectedRecibo = updatedNovo
                                        db.reciboDao().insert(updatedNovo.toEntity(gson, path))
                                        pagerState.animateScrollToPage(2)
                                    }
                                    Toast.makeText(context, "Recibo de pagamento importado!", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "PDF não identificado. Verifique se é um espelho ou recibo.", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                        2 -> ReceiptsScreen(selectedRecibo, db, gson, onOpen = { openPdf(it) }) { selected ->
                            selectedRecibo = selected
                        }
                        3 -> TimesheetScreen(
                            espelho = selectedEspelho,
                            db = db,
                            gson = gson,
                            userName = userName,
                            userMatricula = userMatricula,
                            onEditProfile = { showOnboarding = true },
                            onSelect = { selected ->
                                selectedEspelho = selected
                            },
                            onOpen = { openPdf(it) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AbsenceWarningDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Entendido") } },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Warning, contentDescription = null, tint = Color(0xFFFF3B30))
                Spacer(Modifier.width(8.dp))
                Text("Faltas Detectadas", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Text(stringResource(R.string.warning_absences), lineHeight = 20.sp)
        },
        shape = RoundedCornerShape(22.dp),
        containerColor = Color.White
    )
}

@Composable
fun DeductionDetailDialog(item: ReciboItem, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.MonetizationOn, contentDescription = null, tint = Color(0xFFFF3B30))
                Spacer(Modifier.width(8.dp))
                Text(item.descricao, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Valor: R$ ${item.valor}", fontWeight = FontWeight.SemiBold, color = Color(0xFFFF3B30))
                Text("Referência: ${item.referencia}", color = Color.Gray)
                if (item.detalhe.isNotEmpty()) {
                    HorizontalDivider()
                    Text("Detalhes:", fontWeight = FontWeight.SemiBold)
                    Text(item.detalhe, color = Color.DarkGray, lineHeight = 18.sp)
                }
            }
        },
        shape = RoundedCornerShape(22.dp),
        containerColor = Color.White
    )
}

data class OnboardingStep(
    val titleRes: Int,
    val descRes: Int,
    val icon: ImageVector,
    val color: Color
)

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun OnboardingDialog(initialName: String, initialMatricula: String, onSave: (String, String) -> Unit) {
    var name by remember { mutableStateOf(initialName) }
    var matricula by remember { mutableStateOf(initialMatricula) }
    
    val steps = listOf(
        OnboardingStep(R.string.onboarding_welcome_title, R.string.onboarding_welcome_desc, Icons.Outlined.WavingHand, Color(0xFF007AFF)),
        OnboardingStep(R.string.onboarding_import_title, R.string.onboarding_import_desc, Icons.Outlined.CloudDownload, Color(0xFF5856D6)),
        OnboardingStep(R.string.onboarding_receipts_title, R.string.onboarding_receipts_desc, Icons.Outlined.AccountBalanceWallet, Color(0xFF34C759)),
        OnboardingStep(R.string.onboarding_ponto_title, R.string.onboarding_ponto_desc, Icons.Outlined.Schedule, Color(0xFFFF9500)),
        OnboardingStep(R.string.onboarding_privacy_title, R.string.onboarding_privacy_desc, Icons.Outlined.Shield, Color(0xFFFF3B30)),
        OnboardingStep(R.string.onboarding_finish_title, R.string.onboarding_finish_desc, Icons.Outlined.Person, Color(0xFF8E8E93))
    )
    
    val pagerState = rememberPagerState(pageCount = { steps.size })
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = { },
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false, usePlatformDefaultWidth = false),
        modifier = Modifier.fillMaxSize().padding(12.dp),
        content = {
            Surface(
                modifier = Modifier.fillMaxWidth().wrapContentHeight(),
                shape = RoundedCornerShape(32.dp),
                color = Color.White,
                shadowElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxWidth().height(600.dp),
                        userScrollEnabled = true
                    ) { page ->
                        val step = steps[page]
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Box(
                                modifier = Modifier.size(160.dp).background(step.color.copy(alpha = 0.1f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(step.icon, null, tint = step.color, modifier = Modifier.size(90.dp))
                            }
                            Spacer(Modifier.height(40.dp))
                            Text(stringResource(step.titleRes), fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center, lineHeight = 38.sp)
                            Spacer(Modifier.height(24.dp))
                            Text(stringResource(step.descRes), fontSize = 19.sp, color = Color.Gray, textAlign = TextAlign.Center, lineHeight = 28.sp)
                            
                            if (page == steps.size - 1) {
                                Spacer(Modifier.height(32.dp))
                                OutlinedTextField(
                                    value = name,
                                    onValueChange = { name = it },
                                    label = { Text("Nome Completo") },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    singleLine = true
                                )
                                Spacer(Modifier.height(16.dp))
                                OutlinedTextField(
                                    value = matricula,
                                    onValueChange = { matricula = it },
                                    label = { Text("Matrícula") },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    singleLine = true
                                )
                            }
                        }
                    }
                    
                    Spacer(Modifier.height(24.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            repeat(steps.size) { iteration ->
                                val color = if (pagerState.currentPage == iteration) steps[iteration].color else Color.LightGray
                                Box(modifier = Modifier.padding(2.dp).clip(CircleShape).background(color).size(12.dp))
                            }
                        }
                        
                        Button(
                            onClick = {
                                if (pagerState.currentPage < steps.size - 1) {
                                    scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                                } else {
                                    if (name.isNotBlank() && matricula.isNotBlank()) onSave(name, matricula)
                                }
                            },
                            enabled = if (pagerState.currentPage == steps.size - 1) name.isNotBlank() && matricula.isNotBlank() else true,
                            shape = RoundedCornerShape(16.dp),
                            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (pagerState.currentPage == steps.size - 1) Color(0xFF007AFF) else Color(0xFFF2F2F7),
                                contentColor = if (pagerState.currentPage == steps.size - 1) Color.White else Color.Black
                            )
                        ) {
                            Text(
                                text = if (pagerState.currentPage == steps.size - 1) stringResource(R.string.start) else stringResource(R.string.next),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    )
}

@Composable
fun TimesheetScreen(
    espelho: EspelhoPonto?,
    db: AppDatabase,
    gson: Gson,
    userName: String,
    userMatricula: String,
    onEditProfile: () -> Unit,
    onSelect: (EspelhoPonto) -> Unit,
    onOpen: (String?) -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val historicoEntities by db.espelhoDao().getAllFlow().collectAsState(initial = emptyList())
    val historico = remember(historicoEntities) {
        historicoEntities.map { it.toModel(gson) }.sortedByDescending { it.periodo.extractStartDate() }
    }

    var showHistory by remember { mutableStateOf(false) }

    if (showHistory) {
        TimesheetHistoryDialog(historico, onDismiss = { showHistory = false }, onSelect = {
            onSelect(it)
            showHistory = false
        }, onDelete = {
            scope.launch { db.espelhoDao().deleteByPeriodo(it.periodo) }
        })
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(modifier = Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.timesheet), fontSize = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1).sp)
                IconButton(onClick = { showHistory = true }) {
                    Icon(Icons.Outlined.History, null, tint = Color(0xFF007AFF))
                }
            }
            Text(espelho?.periodo ?: stringResource(R.string.select_pdf), color = Color.Gray, fontSize = 17.sp)
        }

        if (espelho != null) {
            item { IosWidgetSummaryLargeCard(espelho, userName, userMatricula, onEditProfile, onOpen = { onOpen(espelho.pdfFilePath) }) }

            if (espelho.hasAbsences) {
                item {
                    AbsenceDetailCard(espelho)
                }
            }

            item {
                Text("DETALHES", fontSize = 13.sp, color = Color.Gray, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 4.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    espelho.resumoItens.forEach { item ->
                        val resId = context.resources.getIdentifier(item.label, "string", context.packageName)
                        val label = if (resId != 0) stringResource(resId) else item.label
                        val descId = context.resources.getIdentifier(item.label.replace("label_", "desc_"), "string", context.packageName)
                        val description = if (descId != 0) stringResource(descId) else ""

                        IosWidgetCard(
                            title = label,
                            value = item.value,
                            color = if (item.isNegative) Color(0xFFFF3B30) else Color(0xFF007AFF),
                            description = description,
                            icon = getIconForLabel(item.label, item.isNegative)
                        )
                    }
                }
            }
            item { Spacer(modifier = Modifier.height(32.dp)) }
        } else {
            item {
                Box(Modifier.fillMaxWidth().padding(top = 100.dp), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.no_periods_saved), color = Color.Gray, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AbsenceDetailCard(espelho: EspelhoPonto) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = Color(0xFFFF3B30).copy(alpha = 0.1f),
        border = BorderStroke(1.dp, Color(0xFFFF3B30).copy(alpha = 0.2f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Warning, null, tint = Color(0xFFFF3B30), modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("FALTAS NO PERÍODO", color = Color(0xFFFF3B30), fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Foram detectadas faltas nos seguintes dias:",
                fontSize = 13.sp,
                color = Color.DarkGray
            )
            Spacer(Modifier.height(4.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                espelho.diasFaltas.forEach { data ->
                    Surface(
                        color = Color(0xFFFF3B30),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = data,
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TimesheetHistoryDialog(historico: List<EspelhoPonto>, onDismiss: () -> Unit, onSelect: (EspelhoPonto) -> Unit, onDelete: (EspelhoPonto) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
        title = { Text("Histórico de Pontos", fontWeight = FontWeight.Bold) },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                items(historico) { item ->
                    Row(modifier = Modifier.fillMaxWidth().clickable { onSelect(item) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(item.periodo, fontWeight = FontWeight.Bold)
                            Text("Saldo: ${item.saldoFinalBH}", color = if (item.saldoFinalBH.startsWith("-")) Color(0xFFFF3B30) else Color(0xFF34C759))
                        }
                        IconButton(onClick = { onDelete(item) }) { Icon(Icons.Outlined.Delete, null, tint = Color.Gray) }
                    }
                    HorizontalDivider(color = Color.LightGray.copy(alpha = 0.5f))
                }
            }
        },
        shape = RoundedCornerShape(22.dp),
        containerColor = Color.White
    )
}

@Composable
fun ReceiptsScreen(recibo: ReciboPagamento?, db: AppDatabase, gson: Gson, onOpen: (String?) -> Unit, onSelect: (ReciboPagamento) -> Unit) {
    val scope = rememberCoroutineScope()
    val recibosEntities by db.reciboDao().getAllFlow().collectAsState(initial = emptyList())
    val recibos = remember(recibosEntities) {
        recibosEntities.map { it.toModel(gson) }.sortedByDescending { it.periodo.extractStartDateForRecibo() }
    }

    var showHistory by remember { mutableStateOf(false) }

    if (showHistory) {
        ReceiptHistoryDialog(recibos, onDismiss = { showHistory = false }, onSelect = {
            onSelect(it)
            showHistory = false
        }, onDelete = {
            scope.launch { db.reciboDao().deleteByPeriodo(it.periodo) }
        })
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(modifier = Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.receipts), fontSize = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1).sp)
                IconButton(onClick = { showHistory = true }) {
                    Icon(Icons.Outlined.History, null, tint = Color(0xFF007AFF))
                }
            }
            Text(recibo?.periodo ?: stringResource(R.string.select_pdf), color = Color.Gray, fontSize = 17.sp)
        }

        if (recibo != null) {
            item { ReceiptSummaryCard(recibo, onOpen = { onOpen(recibo.pdfFilePath) }) }
            item {
                Text(stringResource(R.string.earnings).uppercase(), fontSize = 13.sp, color = Color.Gray, fontWeight = FontWeight.SemiBold)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    recibo.proventos.forEach { item -> ReceiptItemCard(item, Color(0xFF34C759)) }
                }
            }
            item {
                Text(stringResource(R.string.deductions).uppercase(), fontSize = 13.sp, color = Color.Gray, fontWeight = FontWeight.SemiBold)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    recibo.descontos.forEach { item -> ReceiptItemCard(item, Color(0xFFFF3B30)) }
                }
            }
            item { Spacer(modifier = Modifier.height(32.dp)) }
        } else {
            item {
                Box(Modifier.fillMaxWidth().padding(top = 100.dp), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.no_receipts_saved), color = Color.Gray, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

@Composable
fun ReceiptSummaryCard(recibo: ReciboPagamento, onOpen: () -> Unit = {}) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = Color.White,
        shadowElevation = 4.dp
    ) {
        Column(
            modifier = Modifier
                .background(Brush.verticalGradient(listOf(Color(0xFF34C759), Color(0xFF248A3D))))
                .padding(24.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    if (recibo.funcionario.isNotEmpty() && recibo.funcionario != "Não identificado") {
                        Text(recibo.funcionario, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black)
                        if (recibo.matricula.isNotEmpty()) {
                            Text("Matrícula: ${recibo.matricula}", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
                        }
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    IconButton(onClick = onOpen) {
                        Icon(Icons.Outlined.PictureAsPdf, null, tint = Color.White)
                    }
                    Text("VER PDF", color = Color.White.copy(alpha = 0.7f), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            }

            if (recibo.funcionario.isNotEmpty() && recibo.funcionario != "Não identificado") {
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = Color.White.copy(alpha = 0.2f))
                Spacer(modifier = Modifier.height(16.dp))
            }

            Text(stringResource(R.string.net_pay), color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text("R$ ${recibo.valorLiquido}", color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(modifier = Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.earnings), color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
                    Text("R$ ${recibo.totalProventos}", color = Color.White, fontWeight = FontWeight.Bold)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.deductions), color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
                    Text("R$ ${recibo.totalDescontos}", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }

            if (recibo.dataPagamento.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text("Pagamento em: ${recibo.dataPagamento}", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun IosWidgetReceiptFullCard(recibo: ReciboPagamento, modifier: Modifier = Modifier, onClick: () -> Unit, onOpen: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed) 0.98f else 1f, label = "scale")

    Surface(
        modifier = modifier
            .scale(scale)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        shape = RoundedCornerShape(32.dp),
        color = Color.White,
        shadowElevation = 8.dp
    ) {
        Column(
            modifier = Modifier
                .background(Brush.linearGradient(listOf(Color(0xFF34C759), Color(0xFF248A3D))))
                .padding(24.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("HOLERITE", color = Color.White.copy(alpha = 0.8f), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                    Text(recibo.periodo, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black)
                }
                IconButton(onClick = onOpen) { Icon(Icons.Outlined.PictureAsPdf, null, tint = Color.White) }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text("VALOR LÍQUIDO", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text(
                    text = "R$ ${recibo.valorLiquido}",
                    color = Color.White,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = (-1).sp
                )
            }

            Spacer(modifier = Modifier.height(20.dp))
            HorizontalDivider(color = Color.White.copy(alpha = 0.2f), thickness = 1.dp)
            Spacer(modifier = Modifier.height(16.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                SummaryItem(label = "PROVENTOS", value = "R$ ${recibo.totalProventos}", modifier = Modifier.weight(1f), small = true)
                SummaryItem(label = "DESCONTOS", value = "R$ ${recibo.totalDescontos}", modifier = Modifier.weight(1f), small = true)
            }
        }
    }
}

@Composable
fun ReceiptItemCard(item: ReciboItem, color: Color) {
    var expanded by remember { mutableStateOf(false) }
    Surface(
        color = Color.White,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }
    ) {
        Column(modifier = Modifier.padding(16.dp).animateContentSize()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(item.descricao, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    Text("Ref: ${item.referencia}", fontSize = 12.sp, color = Color.Gray)
                }
                Text("R$ ${item.valor}", fontWeight = FontWeight.Bold, color = color, fontSize = 16.sp)
            }
            if (expanded && item.detalhe.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFF9F9F9), RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    Text(item.detalhe, fontSize = 13.sp, color = Color.DarkGray, lineHeight = 18.sp)
                }
            }
        }
    }
}

@Composable
fun ReceiptHistoryDialog(recibos: List<ReciboPagamento>, onDismiss: () -> Unit, onSelect: (ReciboPagamento) -> Unit, onDelete: (ReciboPagamento) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
        title = { Text(stringResource(R.string.history), fontWeight = FontWeight.Bold) },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                items(recibos) { recibo ->
                    Row(modifier = Modifier.fillMaxWidth().clickable { onSelect(recibo) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(recibo.periodo, fontWeight = FontWeight.Bold)
                            Text("Líquido: R$ ${recibo.valorLiquido}", color = Color(0xFF34C759), fontSize = 14.sp)
                            if (recibo.dataPagamento.isNotEmpty()) {
                                Text("Pago em: ${recibo.dataPagamento}", fontSize = 12.sp, color = Color.Gray)
                            }
                        }
                        IconButton(onClick = { onDelete(recibo) }) { Icon(Icons.Outlined.Delete, null, tint = Color.Gray) }
                    }
                    HorizontalDivider(color = Color.LightGray.copy(alpha = 0.5f))
                }
            }
        },
        shape = RoundedCornerShape(22.dp),
        containerColor = Color.White
    )
}

class WebAppInterface(private val onBlobReceived: (String, String) -> Unit) {
    @JavascriptInterface
    fun processBlob(base64Data: String, fileName: String) {
        onBlobReceived(base64Data, fileName)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
@Composable
fun EpaysWebViewPage(onPdfDownloaded: (Uri) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var webView: WebView? by remember { mutableStateOf(null) }
    var hasError by remember { mutableStateOf(false) }

    fun saveAndImportPdf(bytes: ByteArray, fileName: String) {
        scope.launch(Dispatchers.IO) {
            try {
                val file = File(context.cacheDir, fileName)
                FileOutputStream(file).use { it.write(bytes) }
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                withContext(Dispatchers.Main) { onPdfDownloaded(uri) }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { Toast.makeText(context, "Erro ao processar arquivo", Toast.LENGTH_SHORT).show() }
            }
        }
    }

    fun handleBlob(url: String) {
        webView?.evaluateJavascript("""
            (function() {
                var xhr = new XMLHttpRequest();
                xhr.open('GET', '$url', true);
                xhr.responseType = 'blob';
                xhr.onload = function(e) {
                    if (this.status == 200) {
                        var reader = new FileReader();
                        reader.readAsDataURL(this.response);
                        reader.onloadend = function() {
                            AndroidDownloadInterface.processBlob(reader.result, 'documento_' + new Date().getTime() + '.pdf');
                        }
                    }
                };
                xhr.send();
            })();
        """.trimIndent(), null)
    }

    fun downloadDirectly(url: String, userAgent: String) {
        scope.launch(Dispatchers.IO) {
            try {
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.setRequestProperty("User-Agent", userAgent)
                connection.setRequestProperty("Cookie", CookieManager.getInstance().getCookie(url))
                connection.connect()
                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    val bytes = connection.inputStream.readBytes()
                    saveAndImportPdf(bytes, "download_${System.currentTimeMillis()}.pdf")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { Toast.makeText(context, "Falha no download direto", Toast.LENGTH_SHORT).show() }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (hasError) {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Outlined.WifiOff, null, modifier = Modifier.size(64.dp), tint = Color.Gray)
                Spacer(Modifier.height(16.dp))
                Text("Sem Conexão", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text("Não foi possível carregar o portal ePays. Verifique sua internet.", textAlign = TextAlign.Center, color = Color.Gray)
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = {
                        hasError = false
                        webView?.reload()
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Tentar Novamente")
                }
            }
        }

        AndroidView(
            modifier = Modifier.fillMaxSize().alpha(if (hasError) 0f else 1f),
            factory = { ctx ->
                var startX = 0f
                var startY = 0f
                WebView(ctx).apply {
                    webView = this
                    setOnTouchListener { v, event ->
                        when (event.action) {
                            MotionEvent.ACTION_DOWN -> {
                                startX = event.x
                                startY = event.y
                                v.parent.requestDisallowInterceptTouchEvent(true)
                            }
                            MotionEvent.ACTION_MOVE -> {
                                val deltaX = abs(event.x - startX)
                                val deltaY = event.y - startY
                                if (deltaX > abs(deltaY) && deltaX > 30) {
                                    v.parent.requestDisallowInterceptTouchEvent(false)
                                } else {
                                    v.parent.requestDisallowInterceptTouchEvent(true)
                                }
                            }
                        }
                        false
                    }
                    addJavascriptInterface(WebAppInterface { base64, name ->
                        val pureBase64 = if (base64.contains(",")) base64.split(",")[1] else base64
                        saveAndImportPdf(Base64.decode(pureBase64, Base64.DEFAULT), name)
                    }, "AndroidDownloadInterface")
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        databaseEnabled = true
                        allowFileAccess = true
                        allowContentAccess = true
                        loadWithOverviewMode = true
                        useWideViewPort = true
                        mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                        userAgentString = "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/116.0.0.0 Mobile Safari/537.36"
                    }
                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(v: WebView?, u: String?, f: Bitmap?) {
                            hasError = false
                        }
                        override fun onReceivedError(v: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                            if (request?.isForMainFrame == true) {
                                hasError = true
                            }
                        }
                        override fun shouldOverrideUrlLoading(v: WebView?, request: WebResourceRequest?): Boolean {
                            val url = request?.url?.toString() ?: ""
                            if (url.lowercase().endsWith(".pdf") || url.contains("download", true) || url.contains("relatorio", true) || url.contains("export", true)) {
                                if (url.startsWith("blob:")) handleBlob(url)
                                else downloadDirectly(url, v?.settings?.userAgentString ?: "")
                                return true
                            }
                            return false
                        }
                    }
                    setDownloadListener { url, userAgent, _, _, _ ->
                        if (url.startsWith("blob:")) handleBlob(url)
                        else downloadDirectly(url, userAgent)
                    }
                    loadUrl("https://app.epays.com.br/")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    selectedRecibo: ReciboPagamento?,
    selectedEspelho: EspelhoPonto?,
    onGoToRecibo: () -> Unit,
    onGoToPonto: () -> Unit,
    onOpenPdf: (String?) -> Unit,
    onDeductionClick: (ReciboItem) -> Unit,
    onRefresh: suspend () -> Unit
) {
    val scope = rememberCoroutineScope()
    var isRefreshing by remember { mutableStateOf(false) }

    val cardsPagerState = rememberPagerState(pageCount = {
        if (selectedRecibo != null && selectedEspelho != null) 2
        else 1
    })

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = {
            scope.launch {
                isRefreshing = true
                onRefresh()
                delay(1000)
                isRefreshing = false
            }
        },
        modifier = Modifier.fillMaxSize()
    ) {
        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            SectionHeader("DESTAQUES")

            HorizontalPager(
                state = cardsPagerState,
                modifier = Modifier.fillMaxWidth(),
                pageSpacing = 16.dp
            ) { page ->
                val fullCardModifier = Modifier.fillMaxWidth().height(320.dp)
                when {
                    selectedRecibo != null && selectedEspelho != null -> {
                        if (page == 0) IosWidgetReceiptFullCard(selectedRecibo, modifier = fullCardModifier, onClick = onGoToRecibo, onOpen = { onOpenPdf(selectedRecibo.pdfFilePath) })
                        else IosWidgetTimesheetFullCard(selectedEspelho, modifier = fullCardModifier, onClick = onGoToPonto, onOpen = { onOpenPdf(selectedEspelho.pdfFilePath) })
                    }
                    selectedRecibo != null -> IosWidgetReceiptFullCard(selectedRecibo, modifier = fullCardModifier, onClick = onGoToRecibo, onOpen = { onOpenPdf(selectedRecibo.pdfFilePath) })
                    selectedEspelho != null -> IosWidgetTimesheetFullCard(selectedEspelho, modifier = fullCardModifier, onClick = onGoToPonto, onOpen = { onOpenPdf(selectedEspelho.pdfFilePath) })
                    else -> IosWidgetFinanceWideCard(
                        title = "Importe seus dados",
                        value = "---",
                        subtitle = "Use a aba ePays para Começar",
                        color = Color.Gray,
                        icon = Icons.Outlined.CloudDownload,
                        onClick = {}
                    )
                }
            }

            if (cardsPagerState.pageCount > 1) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    repeat(cardsPagerState.pageCount) { iteration ->
                        val color = if (cardsPagerState.currentPage == iteration) Color(0xFF007AFF) else Color.LightGray
                        Box(modifier = Modifier.padding(2.dp).clip(CircleShape).background(color).size(6.dp))
                    }
                }
            }

            SectionHeader("Último Holerite")
            if (selectedRecibo != null) {
                IosWidgetFinanceWideCard(
                    title = "Líquido a Receber",
                    value = "R$ ${selectedRecibo.valorLiquido}",
                    subtitle = selectedRecibo.periodo,
                    color = Color(0xFF34C759),
                    icon = Icons.Outlined.AccountBalanceWallet,
                    onClick = onGoToRecibo
                )
            } else {
                IosWidgetFinanceWideCard(title = "Líquido a Receber", value = "---", subtitle = "Importe um recibo", color = Color(0xFF34C759), icon = Icons.Outlined.AccountBalanceWallet, onClick = onGoToRecibo)
            }

            SectionHeader("Banco de Horas")
            IosWidgetFinanceWideCard(
                title = "Saldo Atual",
                value = selectedEspelho?.saldoFinalBH ?: "0:00",
                subtitle = selectedEspelho?.periodo ?: "Importe um espelho de ponto",
                color = if (selectedEspelho?.saldoFinalBH?.startsWith("-") == true) Color(0xFFFF3B30) else Color(0xFF007AFF),
                icon = Icons.Outlined.Schedule,
                onClick = onGoToPonto
            )

            SectionHeader("Outros Descontos")

            if (selectedRecibo != null) {
                val deductions = selectedRecibo.descontos
                val emprestimo = deductions.find { it.descricao.uppercase().contains("EMPREST") }
                val inss = deductions.find { it.descricao.uppercase().contains("INSS") }
                val others = deductions.filter { it != emprestimo && it != inss }
                    .sortedByDescending { it.valor.replace(".", "").replace(",", ".").toDoubleOrNull() ?: 0.0 }

                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (emprestimo != null || inss != null) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            if (emprestimo != null) {
                                val value = "R$ ${emprestimo.valor.replace("salário", "", ignoreCase = true).trim()}"
                                IosWidgetSmallInfoCard(
                                    label = "EMPRÉSTIMO",
                                    value = value,
                                    icon = Icons.Outlined.AccountBalance,
                                    modifier = Modifier.weight(1.8f).height(115.dp).clickable { onDeductionClick(emprestimo) },
                                    cardColor = Color(0xFFC62828),
                                    iconColor = Color.White,
                                    textColor = Color.White
                                )
                            }
                            if (inss != null) {
                                val value = "R$ ${inss.valor.replace("salário", "", ignoreCase = true).trim()}"
                                IosWidgetSmallInfoCard(
                                    label = "INSS",
                                    value = value,
                                    icon = Icons.Outlined.Security,
                                    modifier = Modifier.weight(1f).height(115.dp).clickable { onDeductionClick(inss) },
                                    cardColor = Color(0xFFE53935),
                                    iconColor = Color.White,
                                    textColor = Color.White
                                )
                            } else if (emprestimo != null) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }

                    if (others.isNotEmpty()) {
                        val firstOther = others.first()
                        val value = "R$ ${firstOther.valor.replace("salário", "", ignoreCase = true).trim()}"
                        IosWidgetFinanceWideCard(
                            title = firstOther.descricao,
                            value = value,
                            subtitle = "Dedução Adicional",
                            color = Color(0xFFB71C1C),
                            icon = Icons.Outlined.MonetizationOn,
                            onClick = { onDeductionClick(firstOther) }
                        )
                    }

                    if (emprestimo == null && inss == null && others.isEmpty()) {
                        IosWidgetFinanceWideCard(title = "Sem Descontos", value = "R$ 0,00", subtitle = "Este recibo não possui descontos detalhados.", color = Color.Gray, icon = Icons.Outlined.MonetizationOn, onClick = onGoToRecibo)
                    }
                }
            } else {
                IosWidgetFinanceWideCard(title = "Descontos", value = "---", subtitle = "Importe um recibo para visualizar os detalhes", color = Color.Gray, icon = Icons.Outlined.MonetizationOn, onClick = onGoToRecibo)
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun IosWidgetTimesheetFullCard(espelho: EspelhoPonto, modifier: Modifier = Modifier, onClick: () -> Unit, onOpen: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed) 0.98f else 1f, label = "scale")

    Surface(
        modifier = modifier
            .scale(scale)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        shape = RoundedCornerShape(32.dp),
        color = Color.White,
        shadowElevation = 8.dp
    ) {
        Column(
            modifier = Modifier
                .background(Brush.linearGradient(listOf(Color(0xFF007AFF), Color(0xFF00C6FF))))
                .padding(24.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("BANCO DE HORAS", color = Color.White.copy(alpha = 0.8f), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                    Text(espelho.periodo, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black)
                }
                IconButton(onClick = { onOpen() }) { Icon(Icons.Outlined.PictureAsPdf, null, tint = Color.White) }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text("SALDO FINAL", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text(
                    text = espelho.saldoFinalBH,
                    color = Color.White,
                    fontSize = 44.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = (-1).sp
                )

                if (espelho.hasAbsences) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Surface(
                        color = Color.White.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Warning, null, tint = Color(0xFFFF3B30), modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("FALTAS DETECTADAS: ${espelho.diasFaltas.size}", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
            HorizontalDivider(color = Color.White.copy(alpha = 0.2f), thickness = 1.dp)
            Spacer(modifier = Modifier.height(16.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                SummaryItem(label = "TRABALHADAS", value = espelho.resumoItens.find { it.label == "label_worked_hours" }?.value ?: "0:00", modifier = Modifier.weight(1f), small = true)
                SummaryItem(label = "SALDO PERÍODO", value = espelho.saldoPeriodoBH, modifier = Modifier.weight(1f), small = true)
            }
        }
    }
}

private fun getDeductionColor(description: String): Color {
    val upperDesc = description.uppercase()
    return when {
        upperDesc.contains("IRRF") -> Color(0xFFFF3B30)
        upperDesc.contains("INSS") -> Color(0xFFE53935)
        upperDesc.contains("VALE") || upperDesc.contains("TRANSPORTE") -> Color(0xFFB71C1C)
        else -> Color(0xFFC62828)
    }
}

@Composable
fun InfoBadge(icon: ImageVector, text: String) {
    Surface(
        color = Color.White.copy(alpha = 0.15f),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(12.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(text, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
fun IosWidgetSmallInfoCard(
    label: String,
    value: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    cardColor: Color = Color.White,
    iconColor: Color = Color.Gray,
    textColor: Color = Color.Black
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        color = cardColor,
        shadowElevation = 1.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Icon(icon, null, tint = iconColor, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.height(8.dp))
            Text(label, fontSize = 12.sp, color = textColor.copy(alpha = 0.7f), fontWeight = FontWeight.Medium)
            Text(value, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = textColor)
        }
    }
}

@Composable
fun IosWidgetFinanceWideCard(
    title: String,
    value: String,
    subtitle: String,
    color: Color,
    icon: ImageVector,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed) 0.97f else 1f, label = "scale")

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        shape = RoundedCornerShape(22.dp),
        color = Color.White,
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .background(color.copy(alpha = 0.1f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = color, modifier = Modifier.size(28.dp))
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.Gray)
                Text(value, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = color)
                Text(subtitle, fontSize = 12.sp, color = Color.Gray)
            }
            Icon(Icons.Outlined.ChevronRight, null, tint = Color.LightGray)
        }
    }
}

@Composable
fun SectionHeader(title: String) {
    Text(
        title.uppercase(),
        fontSize = 13.sp,
        color = Color.Gray,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp, top = 8.dp)
    )
}

@Composable
fun IosWidgetSummaryLargeCard(espelho: EspelhoPonto, userName: String = "", matricula: String = "", onEdit: () -> Unit = {}, onOpen: () -> Unit = {}) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = Color.White,
        shadowElevation = 4.dp
    ) {
        Column(
            modifier = Modifier
                .background(Brush.verticalGradient(listOf(Color(0xFF007AFF), Color(0xFF005BBF))))
                .padding(24.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    if (userName.isNotEmpty()) {
                        Row(modifier = Modifier.clickable { onEdit() }, verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(userName, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black)
                                if (matricula.isNotEmpty()) Text("Matrícula: $matricula", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                            }
                            Icon(Icons.Outlined.Edit, null, tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(16.dp))
                        }
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    IconButton(onClick = { onOpen() }) {
                        Icon(Icons.Outlined.PictureAsPdf, null, tint = Color.White)
                    }
                    Text("VER PDF", color = Color.White.copy(alpha = 0.7f), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            }

            if (userName.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = Color.White.copy(alpha = 0.2f))
                Spacer(modifier = Modifier.height(16.dp))
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text(stringResource(R.string.summary), color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text(espelho.periodo, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                }
                Icon(Icons.Outlined.Analytics, null, tint = Color.White.copy(alpha = 0.3f), modifier = Modifier.size(40.dp))
            }
            Spacer(modifier = Modifier.height(24.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                SummaryItem(label = stringResource(R.string.bank_balance), value = espelho.saldoFinalBH, modifier = Modifier.weight(1f))
                SummaryItem(label = stringResource(R.string.label_worked_hours), value = espelho.resumoItens.find { it.label == "label_worked_hours" }?.value ?: "0:00", modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun SummaryItem(label: String, value: String, modifier: Modifier = Modifier, small: Boolean = false) {
    Column(modifier = modifier) {
        Text(label, color = Color.White.copy(alpha = 0.8f), fontSize = if (small) 11.sp else 12.sp, maxLines = 1)
        Text(value, color = Color.White, fontSize = if (small) 20.sp else 28.sp, fontWeight = FontWeight.Bold)
    }
}

private fun String.extractStartDate(): Date {
    val dateRegex = """\d{2}/\d{2}/\d{4}""".toRegex()
    return try { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).parse(dateRegex.find(this)?.value ?: "") ?: Date(0) } catch (e: Exception) { Date(0) }
}

private fun String.extractStartDateForRecibo(): Date {
    val monthsMap = mapOf(
        "JAN" to "01", "FEV" to "02", "MAR" to "03", "ABR" to "04", "MAI" to "05", "JUN" to "06",
        "JUL" to "07", "AGO" to "08", "SET" to "09", "OUT" to "10", "NOV" to "11", "DEZ" to "12"
    )
    val text = this.uppercase()
    val nameMatch = """([A-Z]{3})\s+(\d{4})""".toRegex().find(text)
    if (nameMatch != null) {
        val monthName = nameMatch.groupValues[1]
        val year = nameMatch.groupValues[2]
        val monthNum = monthsMap[monthName]
        if (monthNum != null) {
            return try { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).parse("01/$monthNum/$year") ?: Date(0) } catch (e: Exception) { Date(0) }
        }
    }
    val dateRegex = """(\d{2})/(\d{4})""".toRegex()
    val match = dateRegex.find(this)
    if (match != null) {
        val month = match.groupValues[1]
        val year = match.groupValues[2]
        return try { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).parse("01/$month/$year") ?: Date(0) } catch (e: Exception) { Date(0) }
    }
    return try { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).parse(this) ?: Date(0) } catch (e: Exception) { Date(0) }
}

@Composable
fun IosWidgetCard(title: String, value: String, color: Color, description: String = "", icon: ImageVector) {
    var expanded by remember { mutableStateOf(false) }
    Surface(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).clickable { expanded = !expanded }, color = Color.White, shape = RoundedCornerShape(22.dp)) {
        Column(modifier = Modifier.padding(20.dp).animateContentSize()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(32.dp).background(color.copy(alpha = 0.1f), CircleShape), contentAlignment = Alignment.Center) { Icon(icon, null, tint = color, modifier = Modifier.size(18.dp)) }
                Spacer(modifier = Modifier.width(12.dp))
                Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.Black, modifier = Modifier.weight(1f))
                Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = color)
            }
            if (expanded && description.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Box(modifier = Modifier.fillMaxWidth().background(Color(0xFFF9F9F9), RoundedCornerShape(12.dp)).padding(12.dp)) { Text(description, fontSize = 13.sp, color = Color.DarkGray, lineHeight = 18.sp) }
            }
        }
    }
}

@Composable
fun IosWidgetButton(onClick: () -> Unit, text: String, icon: ImageVector? = null) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed) 0.96f else 1f, animationSpec = spring(stiffness = Spring.StiffnessLow), label = "buttonScale")
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(56.dp).scale(scale),
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF007AFF),
        interactionSource = interactionSource
    ) {
        Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) { Icon(icon, null, tint = Color.White); Spacer(Modifier.width(8.dp)) }
            Text(text, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
    }
}

private fun getIconForLabel(label: String, isNegative: Boolean): ImageVector {
    return when {
        label.contains("worked") -> Icons.Outlined.Schedule
        label.contains("night") -> Icons.Outlined.NightsStay
        label.contains("extra") -> Icons.Outlined.TrendingUp
        label.contains("absence") || label.contains("excused") -> Icons.Outlined.CheckCircle
        isNegative -> Icons.Outlined.TrendingDown
        else -> Icons.Outlined.Info
    }
}