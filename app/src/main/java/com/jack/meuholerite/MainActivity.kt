package com.jack.meuholerite

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.CookieManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.DialogProperties
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.google.gson.Gson
import com.jack.meuholerite.ads.AdsDataStore
import com.jack.meuholerite.ads.RewardedInterstitialAdManager
import com.jack.meuholerite.database.AppDatabase
import com.jack.meuholerite.database.toEntity
import com.jack.meuholerite.database.toModel
import com.jack.meuholerite.model.EspelhoPonto
import com.jack.meuholerite.model.ReciboItem
import com.jack.meuholerite.model.ReciboPagamento
import com.jack.meuholerite.parser.PontoParser
import com.jack.meuholerite.parser.ReciboParser
import com.jack.meuholerite.ui.EditProfileDialog
import com.jack.meuholerite.ui.SectionHeader
import com.jack.meuholerite.ui.theme.MeuHoleriteTheme
import com.jack.meuholerite.utils.BackupManager
import com.jack.meuholerite.utils.PdfReader
import com.jack.meuholerite.utils.StorageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs

class MainActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        val requestConfiguration = RequestConfiguration.Builder().build()
        MobileAds.setRequestConfiguration(requestConfiguration)

        MobileAds.initialize(this) {
            RewardedInterstitialAdManager.loadAd(this)
        }

        val storageManager = StorageManager(this)

        createNotificationChannel()

        setContentView(
            ComposeView(this).apply {
                setContent {

                    val hideValuesDefault = storageManager.isHideValuesEnabled()
                    var hideValues by remember {
                        mutableStateOf(hideValuesDefault)
                    }

                    val systemInDarkTheme = isSystemInDarkTheme()

                    var useDarkTheme by remember {
                        val hasSet = storageManager.hasDarkModeSet()
                        mutableStateOf(
                            if (hasSet) storageManager.isDarkMode()
                            else systemInDarkTheme
                        )
                    }

                    val lifecycleOwner = LocalLifecycleOwner.current
                    DisposableEffect(lifecycleOwner) {
                        val observer = LifecycleEventObserver { _, event ->
                            if (event == Lifecycle.Event.ON_RESUME) {
                                val hasSet = storageManager.hasDarkModeSet()
                                useDarkTheme = if (hasSet) storageManager.isDarkMode() else systemInDarkTheme
                            }
                        }
                        lifecycleOwner.lifecycle.addObserver(observer)
                        onDispose {
                            lifecycleOwner.lifecycle.removeObserver(observer)
                        }
                    }

                    MeuHoleriteTheme(darkTheme = useDarkTheme) {

                        AppLockGate(storage = storageManager) {

                            MainScreen(
                                isDarkTheme = useDarkTheme,
                                onToggleDarkMode = { enabled ->
                                    storageManager.setDarkMode(enabled)
                                    useDarkTheme = enabled
                                },
                                hideValues = hideValues,
                                onToggleHideValues = { enabled ->
                                    storageManager.setHideValues(enabled)
                                    hideValues = enabled
                                }
                            )
                        }
                    }
                }
            }
        )
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            val manager =
                getSystemService(Context.NOTIFICATION_SERVICE)
                        as NotificationManager

            val absenceChannel = NotificationChannel(
                "ABSENCE_ALERTS",
                "Avisos de Falta",
                NotificationManager.IMPORTANCE_DEFAULT
            )

            val paymentChannel = NotificationChannel(
                "PAYMENT_ALERTS",
                "Avisos de Pagamento",
                NotificationManager.IMPORTANCE_DEFAULT
            )

            manager.createNotificationChannel(absenceChannel)
            manager.createNotificationChannel(paymentChannel)
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
    val pendingIntent: PendingIntent =
        PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)

    val builder = NotificationCompat.Builder(context, "ABSENCE_ALERTS")
        .setSmallIcon(R.drawable.ic_launcher_foreground)
        .setContentTitle(context.getString(R.string.absences_detected))
        .setContentText(context.getString(R.string.absences_detected_msg, numFaltas, periodo))
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .setContentIntent(pendingIntent)
        .setAutoCancel(true)

    try {
        NotificationManagerCompat.from(context).notify(1001, builder.build())
    } catch (_: SecurityException) {
    }
}

fun showPaymentNotification(context: Context, periodo: String, dataPagamento: String) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return
        }
    }

    val intent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
    }
    val pendingIntent: PendingIntent =
        PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)

    val builder = NotificationCompat.Builder(context, "PAYMENT_ALERTS")
        .setSmallIcon(R.drawable.ic_launcher_foreground)
        .setContentTitle(context.getString(R.string.import_recibo_success))
        .setContentText("Pagamento de $periodo: $dataPagamento")
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .setContentIntent(pendingIntent)
        .setAutoCancel(true)

    try {
        NotificationManagerCompat.from(context).notify(1002, builder.build())
    } catch (_: SecurityException) {
    }
}

@Composable
fun IosTopBar(
    userName: String,
    onSettingsClick: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
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
                    in 5..12 -> stringResource(R.string.greeting_morning)
                    in 13..18 -> stringResource(R.string.greeting_afternoon)
                    else -> stringResource(R.string.greeting_evening)
                }
                Text(
                    text = greeting,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = userName.ifEmpty { stringResource(R.string.welcome_user) },
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.5).sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            IconButton(onClick = onSettingsClick) {
                Icon(Icons.Default.Settings, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MainScreen(
    intent: Intent? = null,
    isDarkTheme: Boolean,
    onToggleDarkMode: (Boolean) -> Unit,
    hideValues: Boolean,
    onToggleHideValues: (Boolean) -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { 4 })
    val scope = rememberCoroutineScope()

    val context = LocalContext.current
    val storage = remember { StorageManager(context) }
    val db = remember { AppDatabase.getDatabase(context) }
    val gson = remember { Gson() }
    val prefs = remember { context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE) }
    val backupManager = remember { BackupManager(context) }

    var selectedEspelho by remember { mutableStateOf<EspelhoPonto?>(null) }
    var selectedRecibo by remember { mutableStateOf<ReciboPagamento?>(null) }
    var showAbsenceWarning by remember { mutableStateOf(false) }

    var userName by remember { mutableStateOf(prefs.getString("user_name", "") ?: "") }
    var userMatricula by remember { mutableStateOf(prefs.getString("user_matricula", "") ?: "") }
    var showOnboarding by remember { mutableStateOf(userName.isEmpty() || userMatricula.isEmpty()) }
    var showEditProfile by remember { mutableStateOf(false) }
    
    var selectedReciboItemForPopup by remember { mutableStateOf<Pair<ReciboItem, Boolean>?>(null) }
    var selectedPontoItemForPopup by remember { mutableStateOf<Triple<String, String, Boolean>?>(null) } // (LabelKey, Value, isNegative)


    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Toast.makeText(context, context.getString(R.string.notification_denied), Toast.LENGTH_LONG).show()
        }
    }

    suspend fun refreshData() {
        withContext(Dispatchers.IO) {
            val newName = prefs.getString("user_name", "") ?: ""
            val newMatricula = prefs.getString("user_matricula", "") ?: ""
            withContext(Dispatchers.Main) {
                userName = newName
                userMatricula = newMatricula
            }
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

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                scope.launch { refreshData() }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    fun triggerAutoBackup() {
        scope.launch {
            backupManager.backupData()
        }
    }

    fun openPdf(filePath: String?) {
        if (filePath == null) {
            Toast.makeText(context, context.getString(R.string.pdf_not_available), Toast.LENGTH_SHORT).show()
            return
        }
        val file = File(filePath)
        if (!file.exists()) {
            Toast.makeText(context, context.getString(R.string.file_not_found), Toast.LENGTH_SHORT).show()
            return
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intentView = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
        }
        try {
            context.startActivity(Intent.createChooser(intentView, context.getString(R.string.open_pdf_with)))
        } catch (_: Exception) {
            Toast.makeText(context, context.getString(R.string.no_pdf_app), Toast.LENGTH_SHORT).show()
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
            } catch (_: Exception) {
                null
            }
        }
    }

    val showAdIfNeeded = { afterImport: Boolean ->
        scope.launch {
            val shown = if (afterImport) AdsDataStore.wasShownAfterImport(context) else AdsDataStore.wasShownHomeTimed(context)
            if (!shown) {
                (context as? Activity)?.let { activity ->
                    RewardedInterstitialAdManager.showAd(activity) {
                        scope.launch {
                            if (afterImport) AdsDataStore.markShownAfterImport(context)
                            else AdsDataStore.markShownHomeTimed(context)
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        refreshData()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        delay(120000)
        showAdIfNeeded(false)
    }

    LaunchedEffect(selectedEspelho) {
        if (selectedEspelho?.hasAbsences == true) {
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
                triggerAutoBackup()
            }
        )
    }

    if (showEditProfile) {
        EditProfileDialog(
            initialName = userName,
            initialMatricula = userMatricula,
            onDismiss = { showEditProfile = false },
            onSave = { name: String, matricula: String ->
                userName = name
                userMatricula = matricula
                prefs.edit().putString("user_name", name).putString("user_matricula", matricula).apply()
                showEditProfile = false
                triggerAutoBackup()
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
                        triggerAutoBackup()
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
                        triggerAutoBackup()
                        pagerState.animateScrollToPage(2)
                        if (updatedNovo.dataPagamento.isNotEmpty()) {
                            showPaymentNotification(context, updatedNovo.periodo, updatedNovo.dataPagamento)
                        }
                    }
                }
            }
        }
    }

    if (showAbsenceWarning) {
        AbsenceWarningDialog(onDismiss = { showAbsenceWarning = false })
    }

    if (selectedReciboItemForPopup != null) {
        DeductionDetailDialog(selectedReciboItemForPopup!!.first, selectedReciboItemForPopup!!.second) { selectedReciboItemForPopup = null }
    }

    if (selectedPontoItemForPopup != null) {
        PontoDetailDialog(
            labelKey = selectedPontoItemForPopup!!.first,
            value = selectedPontoItemForPopup!!.second,
            isNegative = selectedPontoItemForPopup!!.third,
            onDismiss = { selectedPontoItemForPopup = null }
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            IosTopBar(
                userName = userName,
                onSettingsClick = { context.startActivity(Intent(context, SettingsActivity::class.java)) }
            )
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)) {
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
                    val pageOffset = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction).coerceIn(-1f, 1f)
                    alpha = 1f - abs(pageOffset)
                    scaleX = 1f - (abs(pageOffset) * 0.1f)
                    scaleY = 1f - (abs(pageOffset) * 0.1f)
                }

                Box(animatedModifier) {
                    when (page) {
                        0 -> HomeScreen(
                            userName = userName,
                            userMatricula = userMatricula,
                            selectedRecibo = selectedRecibo,
                            selectedEspelho = selectedEspelho,
                            onGoToRecibo = { scope.launch { pagerState.animateScrollToPage(2) } },
                            onGoToPonto = { scope.launch { pagerState.animateScrollToPage(3) } },
                            onOpenPdf = { openPdf(it) },
                            onRefresh = {
                                scope.launch {
                                    refreshData()
                                    (context as? Activity)?.let { activity ->
                                        RewardedInterstitialAdManager.showAd(activity) {}
                                    }
                                }
                            },
                            db = db,
                            gson = gson,
                            onSelectItem = { item, isProvento -> selectedReciboItemForPopup = item to isProvento }
                        )
                        1 -> EpaysWebViewPage { uri: Uri ->
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
                                        triggerAutoBackup()
                                        pagerState.animateScrollToPage(3)
                                    }
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
                                        triggerAutoBackup()
                                        pagerState.animateScrollToPage(2)
                                        if (updatedNovo.dataPagamento.isNotEmpty()) {
                                            showPaymentNotification(context, updatedNovo.periodo, updatedNovo.dataPagamento)
                                        }
                                    }
                                }
                                showAdIfNeeded(true)
                            }
                        }
                        2 -> ReceiptsScreen(
                            recibo = selectedRecibo,
                            db = db,
                            gson = gson,
                            userName = userName,
                            userMatricula = userMatricula,
                            onEditProfile = { showEditProfile = true },
                            onOpen = { openPdf(it) },
                            onSelect = { selected -> selectedRecibo = selected },
                            onRefresh = { scope.launch { refreshData() } },
                            onSelectItem = { item, isProvento -> selectedReciboItemForPopup = item to isProvento }
                        )
                        3 -> TimesheetScreen(
                            espelho = selectedEspelho,
                            db = db,
                            gson = gson,
                            userName = userName,
                            userMatricula = userMatricula,
                            onEditProfile = { showEditProfile = true },
                            onSelect = { selected -> selectedEspelho = selected },
                            onOpen = { openPdf(it) },
                            onRefresh = { scope.launch { refreshData() } },
                            onSelectItem = { labelKey, value, isNegative ->
                                selectedPontoItemForPopup = Triple(labelKey, value, isNegative)
                            }
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
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Warning, contentDescription = null, tint = Color(0xFFFF3B30))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.absences_detected), fontWeight = FontWeight.Bold)
            }
        },
        text = { Text(stringResource(R.string.warning_absences), lineHeight = 20.sp) },
        shape = RoundedCornerShape(22.dp)
    )
}

@Composable
fun DeductionDetailDialog(item: ReciboItem, isProvento: Boolean, onDismiss: () -> Unit) {
    val color = if (isProvento) Color(0xFF34C759) else Color(0xFFFF3B30)
    val title = if (isProvento) "Detalhe" else "Detalhe" // Contexto dinâmico abaixo
    val question = if (isProvento) stringResource(R.string.earnings) else stringResource(R.string.deductions)

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(if (isProvento) Icons.Outlined.AddCircle else Icons.Outlined.RemoveCircle, contentDescription = null, tint = color)
                Spacer(Modifier.width(8.dp))
                Text(item.descricao, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            }
        },
        text = {
            val detalheContexto = getDetalheParaItem(item.descricao, isProvento)
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("R$ ${item.valor}", fontWeight = FontWeight.SemiBold, color = color)
                Text("Ref: ${item.referencia}", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                
                HorizontalDivider()
                
                Text(question, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Text(detalheContexto, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f), lineHeight = 20.sp)
                
                if (item.detalhe.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text("Observação:", fontWeight = FontWeight.SemiBold)
                    Text(item.detalhe, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f), fontSize = 13.sp)
                }
            }
        },
        shape = RoundedCornerShape(22.dp)
    )
}

@Composable
fun PontoDetailDialog(labelKey: String, value: String, isNegative: Boolean, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val resId = context.resources.getIdentifier(labelKey, "string", context.packageName)
    val displayLabel = if (resId != 0) stringResource(resId) else labelKey
    
    val color = if (isNegative) Color(0xFFFF3B30) else Color(0xFF007AFF)
    val icon = if (isNegative) Icons.Outlined.TrendingDown else Icons.Outlined.TrendingUp
    
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = color)
                Spacer(Modifier.width(8.dp))
                Text(displayLabel, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            }
        },
        text = {
            val detalheContexto = getDetalheParaResumoItem(labelKey)
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(value, fontWeight = FontWeight.SemiBold, color = color, fontSize = 18.sp)
                HorizontalDivider()
                Text(stringResource(R.string.summary), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Text(detalheContexto, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f), lineHeight = 20.sp)
            }
        },
        shape = RoundedCornerShape(22.dp)
    )
}


fun getDetalheParaItem(descricao: String, isProvento: Boolean): String {
    val d = descricao.uppercase()
    return if (isProvento) {
        when {
            d.contains("SALARIO") || d.contains("VENCIMENTO") -> "Seu salário base mensal registrado em contrato."
            d.contains("HORA EXTRA") || d.contains("H.E") -> "Pagamento pelas horas trabalhadas além da sua jornada normal."
            d.contains("ADICIONAL NOTURNO") -> "Compensação financeira para quem trabalha entre as 22h e 5h."
            d.contains("FERIAS") -> "Pagamento referente ao seu período de descanso anual."
            d.contains("13O") || d.contains("GRATIFICACAO") -> "Décimo Terceiro Salário (13º)."
            d.contains("PERICULOSIDADE") -> "Adicional de 30% pago a profissionais expostos a riscos."
            d.contains("INSALUBRIDADE") -> "Adicional pago por exposição a agentes nocivos à saúde."
            d.contains("DSR") || d.contains("REPOUSO") -> "Descanso Semanal Remunerado."
            d.contains("PREMIO") || d.contains("BONUS") -> "Valor extra pago como reconhecimento por desempenho."
            d.contains("AUXILIO") || d.contains("ABONO") -> "Benefício ou ajuda de custo paga pela empresa."
            else -> "Provento que compõe seu salário bruto."
        }
    } else {
        when {
            d.contains("INSS") -> "Contribuição obrigatória para a Previdência Social."
            d.contains("IRRF") || d.contains("RENDA") -> "Imposto de Renda Retido na Fonte."
            d.contains("VALE TRANSPORTE") || d.contains("V.T") -> "Coparticipação no benefício de transporte."
            d.contains("VALE REFEIÇÃO") || d.contains("V.R") || d.contains("ALIMENTACAO") -> "Desconto referente ao custo de refeição ou alimentação."
            d.contains("MEDICO") || d.contains("SAUDE") || d.contains("ODONTO") -> "Coparticipação em plano de saúde ou odontológico."
            d.contains("SINDICATO") || d.contains("ASSISTENCIAL") -> "Contribuição voltada ao sindicato da categoria."
            d.contains("FALTA") -> "Desconto por ausência não justificada."
            d.contains("ATRASO") -> "Desconto por atraso no horário de entrada."
            d.contains("CONSIGNADO") || d.contains("EMPRESTIMO") -> "Parcela de empréstimo descontada em folha."
            d.contains("ADIANTAMENTO") -> "Valor pago antecipadamente no mês."
            else -> "Desconto específico da sua folha de pagamento."
        }
    }
}

@Composable
fun getDetalheParaResumoItem(labelKey: String): String {
    return when (labelKey) {
        "label_worked_hours" -> stringResource(R.string.desc_ponto_total_trabalhadas)
        "label_absences" -> stringResource(R.string.desc_ponto_faltas)
        "label_excused_absence" -> stringResource(R.string.desc_ponto_horas_abonadas)
        "label_night_allowance" -> stringResource(R.string.desc_ponto_horas_noturnas)
        "label_extra_hours_50", "label_extra_hours_100" -> stringResource(R.string.desc_ponto_credito_he)
        "label_previous_balance" -> stringResource(R.string.desc_ponto_saldo_anterior)
        "label_period_balance" -> stringResource(R.string.desc_ponto_saldo_periodo)
        "label_early_departure" -> stringResource(R.string.desc_ponto_saida_antecipada)
        "label_interval_delay" -> stringResource(R.string.desc_ponto_atraso_intervalo)
        // Casos genéricos baseados no texto se a chave não bater
        else -> {
            val key = labelKey.uppercase()
            when {
                key.contains("FINAL") || key.contains("SALDO ATUAL") -> stringResource(R.string.desc_ponto_saldo_final)
                else -> stringResource(R.string.desc_ponto_default)
            }
        }
    }
}

data class OnboardingStep(val titleRes: Int, val descRes: Int, val icon: ImageVector, val color: Color)

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
            Surface(modifier = Modifier.fillMaxWidth().wrapContentHeight(), shape = RoundedCornerShape(32.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 8.dp) {
                Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    HorizontalPager(state = pagerState, modifier = Modifier.fillMaxWidth().height(600.dp)) { page ->
                        val step = steps[page]
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center, modifier = Modifier.fillMaxSize()) {
                            Box(modifier = Modifier.size(160.dp).background(step.color.copy(alpha = 0.1f), CircleShape), contentAlignment = Alignment.Center) {
                                Icon(step.icon, null, tint = step.color, modifier = Modifier.size(90.dp))
                            }
                            Spacer(Modifier.height(40.dp))
                            Text(stringResource(step.titleRes), fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center, lineHeight = 38.sp, color = MaterialTheme.colorScheme.onSurface)
                            Spacer(Modifier.height(24.dp))
                            Text(stringResource(step.descRes), fontSize = 19.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), textAlign = TextAlign.Center, lineHeight = 28.sp)
                            if (page == steps.size - 1) {
                                Spacer(Modifier.height(32.dp))
                                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(stringResource(R.string.full_name)) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                                Spacer(Modifier.height(16.dp))
                                OutlinedTextField(value = matricula, onValueChange = { matricula = it }, label = { Text(stringResource(R.string.matricula_label)) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                            }
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            repeat(steps.size) { iteration ->
                                val color = if (pagerState.currentPage == iteration) steps[iteration].color else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                                Box(modifier = Modifier.padding(2.dp).clip(CircleShape).background(color).size(12.dp))
                            }
                        }
                        Button(
                            onClick = {
                                if (pagerState.currentPage < steps.size - 1) scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                                else if (name.isNotBlank() && matricula.isNotBlank()) onSave(name, matricula)
                            },
                            enabled = if (pagerState.currentPage == steps.size - 1) name.isNotBlank() && matricula.isNotBlank() else true,
                            shape = RoundedCornerShape(16.dp)
                        ) { Text(if (pagerState.currentPage == steps.size - 1) stringResource(R.string.start) else stringResource(R.string.next)) }
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
    onOpen: (String?) -> Unit, 
    onRefresh: () -> Unit,
    onSelectItem: (labelKey: String, value: String, isNegative: Boolean) -> Unit
) {
    val scope = rememberCoroutineScope()
    val historicoEntities by db.espelhoDao().getAllFlow().collectAsState(initial = emptyList())
    val historico = remember(historicoEntities) { historicoEntities.map { it.toModel(gson) }.sortedByDescending { it.periodo.extractStartDate() } }
    var showHistory by remember { mutableStateOf(false) }
    if (showHistory) {
        TimesheetHistoryDialog(historico, onDismiss = { showHistory = false }, onSelect = { onSelect(it); showHistory = false }, onDelete = { scope.launch { db.espelhoDao().deleteByPeriodo(it.periodo); onRefresh() } })
    }
    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Row(modifier = Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.timesheet), fontSize = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1).sp, color = MaterialTheme.colorScheme.onSurface)
                IconButton(onClick = { showHistory = true }) { Icon(Icons.Outlined.History, null, tint = Color(0xFF007AFF)) }
            }
            Text(espelho?.periodo ?: stringResource(R.string.select_pdf), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), fontSize = 17.sp)
        }
        if (espelho != null) {
            item { IosWidgetSummaryLargeCard(espelho, userName, userMatricula, onEditProfile, onOpen = { onOpen(espelho.pdfFilePath) }) }
            if (espelho.hasAbsences) item { AbsenceDetailCard(espelho) }

            val proventos = espelho.resumoItens.filter { !it.isNegative }
            val descontos = espelho.resumoItens.filter { it.isNegative }

            if (proventos.isNotEmpty()) {
                item {
                    Text(stringResource(R.string.earnings).uppercase(), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), fontWeight = FontWeight.SemiBold)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        proventos.forEach { item ->
                            val resId = LocalContext.current.resources.getIdentifier(item.label, "string", LocalContext.current.packageName)
                            val label = if (resId != 0) stringResource(resId) else item.label
                            val color = Color(0xFF007AFF)
                            val icon = getIconForLabel(item.label, item.isNegative)

                            IosWidgetCardClickable(
                                title = label, 
                                value = item.value, 
                                color = color, 
                                icon = icon,
                                onClick = { onSelectItem(item.label, item.value, item.isNegative) }
                            )
                        }
                    }
                }
            }

            if (descontos.isNotEmpty()) {
                item {
                    Text(stringResource(R.string.deductions).uppercase(), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), fontWeight = FontWeight.SemiBold)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        descontos.forEach { item ->
                            val resId = LocalContext.current.resources.getIdentifier(item.label, "string", LocalContext.current.packageName)
                            val label = if (resId != 0) stringResource(resId) else item.label
                            val color = Color(0xFFFF3B30)
                            val icon = getIconForLabel(item.label, item.isNegative)

                            IosWidgetCardClickable(
                                title = label,
                                value = item.value,
                                color = color,
                                icon = icon,
                                onClick = { onSelectItem(item.label, item.value, item.isNegative) }
                            )
                        }
                    }
                }
            }
            item { Spacer(modifier = Modifier.height(32.dp)) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AbsenceDetailCard(espelho: EspelhoPonto) {
    Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), color = Color(0xFFFF3B30).copy(alpha = 0.1f), border = BorderStroke(1.dp, Color(0xFFFF3B30).copy(alpha = 0.2f))) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Warning, null, tint = Color(0xFFFF3B30), modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.absences_detected).uppercase(), color = Color(0xFFFF3B30), fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
            Spacer(Modifier.height(8.dp))
            FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                espelho.diasFaltas.forEach { data ->
                    Surface(color = Color(0xFFFF3B30), shape = RoundedCornerShape(8.dp)) { Text(text = data, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) }
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
        title = { Text(stringResource(R.string.history), fontWeight = FontWeight.Bold) },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                items(historico) { item ->
                    Row(modifier = Modifier.fillMaxWidth().clickable { onSelect(item) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(item.periodo, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                            Text("${stringResource(R.string.current_balance)}: ${item.saldoFinalBH}", color = if (item.saldoFinalBH.startsWith("-")) Color(0xFFFF3B30) else Color(0xFF34C759))
                        }
                        IconButton(onClick = { onDelete(item) }) { Icon(Icons.Outlined.Delete, null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)) }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                }
            }
        },
        shape = RoundedCornerShape(22.dp)
    )
}

@Composable
fun ReceiptsScreen(
    recibo: ReciboPagamento?, 
    db: AppDatabase, 
    gson: Gson, 
    userName: String, 
    userMatricula: String, 
    onEditProfile: () -> Unit, 
    onOpen: (String?) -> Unit, 
    onSelect: (ReciboPagamento) -> Unit, 
    onRefresh: () -> Unit,
    onSelectItem: (ReciboItem, Boolean) -> Unit
) {
    val scope = rememberCoroutineScope()
    val recibosEntities by db.reciboDao().getAllFlow().collectAsState(initial = emptyList())
    val recibos = remember(recibosEntities) { recibosEntities.map { it.toModel(gson) }.sortedByDescending { it.periodo.extractStartDateForRecibo() } }
    var showHistory by remember { mutableStateOf(false) }

    if (showHistory) {
        ReceiptHistoryDialog(recibos, onDismiss = { showHistory = false }, onSelect = { onSelect(it); showHistory = false }, onDelete = { scope.launch { db.reciboDao().deleteByPeriodo(it.periodo); onRefresh() } })
    }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Row(modifier = Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.receipts), fontSize = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1).sp, color = MaterialTheme.colorScheme.onSurface)
                IconButton(onClick = { showHistory = true }) { Icon(Icons.Outlined.History, null, tint = Color(0xFF007AFF)) }
            }
            Text(recibo?.periodo ?: stringResource(R.string.select_pdf), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), fontSize = 17.sp)
        }
        if (recibo != null) {
            item { ReceiptSummaryCard(recibo, userName, userMatricula, onEditProfile, onOpen = { onOpen(recibo.pdfFilePath) }) }
            item {
                Text(stringResource(R.string.earnings).uppercase(), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), fontWeight = FontWeight.SemiBold)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { 
                    recibo.proventos.forEach { item -> 
                        ReceiptItemCard(item, Color(0xFF34C759)) { onSelectItem(item, true) }
                    } 
                }
            }
            item {
                Text(stringResource(R.string.deductions).uppercase(), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), fontWeight = FontWeight.SemiBold)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { 
                    recibo.descontos.forEach { item -> 
                        ReceiptItemCard(item, Color(0xFFFF3B30)) { onSelectItem(item, false) }
                    } 
                }
            }
            item { Spacer(modifier = Modifier.height(32.dp)) }
        }
    }
}

@Composable
fun ReceiptSummaryCard(recibo: ReciboPagamento, userName: String, matricula: String, onEdit: () -> Unit, onOpen: () -> Unit) {
    Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 4.dp) {
        Column(modifier = Modifier.background(Brush.verticalGradient(listOf(Color(0xFF34C759), Color(0xFF248A3D)))).padding(24.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(modifier = Modifier.clickable { onEdit() }, verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(userName.ifEmpty { stringResource(R.string.user_label) }, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black)
                            if (matricula.isNotEmpty()) Text("${stringResource(R.string.matricula_label)}: $matricula", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                        }
                        Icon(Icons.Outlined.Edit, null, tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(16.dp))
                    }
                }
                IconButton(onClick = onOpen) { Icon(Icons.Outlined.PictureAsPdf, null, tint = Color.White) }
            }
            Spacer(modifier = Modifier.height(16.dp))
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
        }
    }
}

@Composable
fun IosWidgetReceiptFullCard(recibo: ReciboPagamento, userName: String, matricula: String, modifier: Modifier, onClick: () -> Unit, onOpen: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed) 0.98f else 1f, label = "scale")
    Surface(modifier = modifier.scale(scale).clickable(interactionSource = interactionSource, indication = null, onClick = onClick), shape = RoundedCornerShape(32.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 8.dp) {
        Column(modifier = Modifier.background(Brush.linearGradient(listOf(Color(0xFF34C759), Color(0xFF248A3D)))).padding(24.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(userName.ifEmpty { stringResource(R.string.app_name) }, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text(recibo.periodo, color = Color.White.copy(alpha = 0.8f), fontSize = 22.sp, fontWeight = FontWeight.Black)
                }
                IconButton(onClick = onOpen) { Icon(Icons.Outlined.PictureAsPdf, null, tint = Color.White) }
            }
            Spacer(modifier = Modifier.height(20.dp))
            Text(stringResource(R.string.net_pay).uppercase(), color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text(text = "R$ ${recibo.valorLiquido}", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Black, letterSpacing = (-1).sp)
            Spacer(modifier = Modifier.height(20.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                SummaryItem(label = stringResource(R.string.earnings).uppercase(), value = "R$ ${recibo.totalProventos}", modifier = Modifier.weight(1f), small = true)
                SummaryItem(label = stringResource(R.string.deductions).uppercase(), value = "R$ ${recibo.totalDescontos}", modifier = Modifier.weight(1f), small = true)
            }
        }
    }
}

@Composable
fun ReceiptItemCard(item: ReciboItem, color: Color, onClick: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surface, 
        shape = RoundedCornerShape(16.dp), 
        modifier = Modifier.fillMaxWidth().clickable { onClick() }
    ) {
        Column(modifier = Modifier.padding(16.dp).animateContentSize()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val icon = getIconForReciboItem(item.descricao, color == Color(0xFF34C759))
                Box(modifier = Modifier.size(36.dp).background(color.copy(alpha = 0.1f), CircleShape), contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(12.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(item.descricao, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
                    Text("Ref: ${item.referencia}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                }
                Text("R$ ${item.valor}", fontWeight = FontWeight.Bold, color = color, fontSize = 16.sp)
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
                            Text(recibo.periodo, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                            Text("${stringResource(R.string.net_pay)}: R$ ${recibo.valorLiquido}", color = Color(0xFF34C759), fontSize = 14.sp)
                        }
                        IconButton(onClick = { onDelete(recibo) }) { Icon(Icons.Outlined.Delete, null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)) }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                }
            }
        },
        shape = RoundedCornerShape(22.dp)
    )
}

class WebAppInterface(private val onBlobReceived: (String, String) -> Unit) {
    @JavascriptInterface fun processBlob(base64Data: String, fileName: String) { onBlobReceived(base64Data, fileName) }
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
            } catch (_: Exception) {}
        }
    }
    fun handleBlob(url: String) {
        webView?.evaluateJavascript("(function() { var xhr = new XMLHttpRequest(); xhr.open('GET', '$url', true); xhr.responseType = 'blob'; xhr.onload = function(e) { if (this.status == 200) { var reader = new FileReader(); reader.readAsDataURL(this.response); reader.onloadend = function() { AndroidDownloadInterface.processBlob(reader.result, 'documento_' + new Date().getTime() + '.pdf'); } } }; xhr.send(); })();", null)
    }
    fun downloadDirectly(url: String, userAgent: String) {
        scope.launch(Dispatchers.IO) {
            try {
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.setRequestProperty("User-Agent", userAgent)
                connection.setRequestProperty("Cookie", CookieManager.getInstance().getCookie(url))
                connection.connect()
                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    saveAndImportPdf(connection.inputStream.readBytes(), "download_${System.currentTimeMillis()}.pdf")
                }
            } catch (_: Exception) {}
        }
    }
    Box(modifier = Modifier.fillMaxSize()) {
        if (hasError) {
            Column(modifier = Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Outlined.WifiOff, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                Text(stringResource(R.string.no_connection), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Button(onClick = { hasError = false; webView?.reload() }) { Text(stringResource(R.string.try_again)) }
            }
        }
        AndroidView(modifier = Modifier.fillMaxSize().alpha(if (hasError) 0f else 1f), factory = { ctx ->
            WebView(ctx).apply {
                webView = this
                val cookieManager = CookieManager.getInstance()
                cookieManager.setAcceptCookie(true)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    cookieManager.setAcceptThirdPartyCookies(this, true)
                }

                addJavascriptInterface(WebAppInterface { base64, name ->
                    val pureBase64 = if (base64.contains(",")) base64.split(",")[1] else base64
                    saveAndImportPdf(Base64.decode(pureBase64, Base64.DEFAULT), name)
                }, "AndroidDownloadInterface")
                settings.apply { javaScriptEnabled = true; domStorageEnabled = true; databaseEnabled = true; allowFileAccess = true; allowContentAccess = true; loadWithOverviewMode = true; useWideViewPort = true; mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW; userAgentString = "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/116.0.0.0 Mobile Safari/537.36" }
                webViewClient = object : WebViewClient() {
                    override fun onPageStarted(v: WebView?, u: String?, f: Bitmap?) { hasError = false }
                    override fun onReceivedError(v: WebView?, request: WebResourceRequest?, error: WebResourceError?) { if (request?.isForMainFrame == true) hasError = true }
                    override fun shouldOverrideUrlLoading(v: WebView?, request: WebResourceRequest?): Boolean {
                        val url = request?.url?.toString() ?: ""
                        if (url.lowercase().endsWith(".pdf") || url.contains("download", true)) {
                            if (url.startsWith("blob:")) handleBlob(url) else downloadDirectly(url, v?.settings?.userAgentString ?: "")
                            return true
                        }
                        return false
                    }
                    override fun onPageFinished(view: WebView?, url: String?) {
                        CookieManager.getInstance().flush()
                    }
                }
                setDownloadListener { url, userAgent, _, _, _ -> if (url.startsWith("blob:")) handleBlob(url) else downloadDirectly(url, userAgent) }
                loadUrl("https://app.epays.com.br/")
            }
        })
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    userName: String, 
    userMatricula: String, 
    selectedRecibo: ReciboPagamento?, 
    selectedEspelho: EspelhoPonto?, 
    onGoToRecibo: () -> Unit, 
    onGoToPonto: () -> Unit, 
    onOpenPdf: (String?) -> Unit, 
    onRefresh: () -> Unit,
    db: AppDatabase,
    gson: Gson,
    onSelectItem: (ReciboItem, Boolean) -> Unit
) {
    val scope = rememberCoroutineScope()
    var isRefreshing by remember { mutableStateOf(false) }
    var showSalaryGraph by remember { mutableStateOf(false) }
    val cardsPagerState = rememberPagerState(pageCount = { if (selectedRecibo != null && selectedEspelho != null) 2 else 1 })

    if (showSalaryGraph) {
        SalaryGraphDialog(db, gson) { showSalaryGraph = false }
    }

    PullToRefreshBox(isRefreshing = isRefreshing, onRefresh = { scope.launch { isRefreshing = true; onRefresh(); delay(1000); isRefreshing = false } }, modifier = Modifier.fillMaxSize()) {
        val scrollState = rememberScrollState()
        Column(modifier = Modifier.fillMaxSize().verticalScroll(scrollState).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Spacer(modifier = Modifier.height(12.dp))
            SectionHeader(stringResource(R.string.highlights))
            HorizontalPager(state = cardsPagerState, modifier = Modifier.fillMaxWidth(), pageSpacing = 16.dp) { page ->
                val fullCardModifier = Modifier.fillMaxWidth().height(350.dp)
                when {
                    selectedRecibo != null && selectedEspelho != null -> {
                        if (page == 0) IosWidgetReceiptFullCard(selectedRecibo, userName, userMatricula, fullCardModifier, onGoToRecibo, { onOpenPdf(selectedRecibo.pdfFilePath) })
                        else IosWidgetTimesheetFullCard(selectedEspelho, userName, userMatricula, fullCardModifier, onGoToPonto, { onOpenPdf(selectedEspelho.pdfFilePath) })
                    }
                    selectedRecibo != null -> IosWidgetReceiptFullCard(selectedRecibo, userName, userMatricula, fullCardModifier, onGoToRecibo, { onOpenPdf(selectedRecibo.pdfFilePath) })
                    selectedEspelho != null -> IosWidgetTimesheetFullCard(selectedEspelho, userName, userMatricula, fullCardModifier, onGoToPonto, { onOpenPdf(selectedEspelho.pdfFilePath) })
                    else -> IosWidgetFinanceWideCard(stringResource(R.string.import_data), "---", stringResource(R.string.use_epays_tab), MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f), Icons.Outlined.CloudDownload) {}
                }
            }
            if (selectedEspelho?.hasAbsences == true) {
                Spacer(modifier = Modifier.height(16.dp))
                AbsenceDetailCard(selectedEspelho!!)
            }

            SectionHeader(stringResource(R.string.last_receipt))
            if (selectedRecibo != null) {
                IosWidgetFinanceWideCard(
                    title = stringResource(R.string.net_pay_label), 
                    value = "R$ ${selectedRecibo.valorLiquido}", 
                    subtitle = selectedRecibo.periodo, 
                    color = Color(0xFF34C759), 
                    icon = Icons.Outlined.AccountBalanceWallet, 
                    onClick = { showSalaryGraph = true }
                )
                
                if (selectedRecibo.descontos.isNotEmpty()) {
                    SectionHeader(stringResource(R.string.other_deductions))
                    selectedRecibo.descontos.sortedByDescending { 
                        it.valor.replace(".", "").replace(",", ".").toDoubleOrNull() ?: 0.0 
                    }.take(3).forEach { item ->
                        ReceiptItemCard(item, Color(0xFFFF3B30)) {
                            onSelectItem(item, false)
                        }
                    }
                }
            }
            SectionHeader(stringResource(R.string.bank_hours_label))
            IosWidgetFinanceWideCard(stringResource(R.string.current_balance), selectedEspelho?.saldoFinalBH ?: "0:00", selectedEspelho?.periodo ?: stringResource(R.string.import_timesheet), if (selectedEspelho?.saldoFinalBH?.startsWith("-") == true) Color(0xFFFF3B30) else Color(0xFF007AFF), Icons.Outlined.Schedule, onGoToPonto)
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun SalaryGraphDialog(db: AppDatabase, gson: Gson, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var history by remember { mutableStateOf<List<ReciboPagamento>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val list = db.reciboDao().getAll().map { it.toModel(gson) }
                .sortedBy { it.periodo.extractStartDateForRecibo() }
            history = list
            isLoading = false
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
        title = { Text(stringResource(R.string.net_pay), fontWeight = FontWeight.Bold) },
        text = {
            if (isLoading) {
                Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (history.isEmpty()) {
                Text(stringResource(R.string.no_receipts_saved))
            } else {
                Column(Modifier.fillMaxWidth()) {
                    SalaryLineChart(history)
                    Spacer(Modifier.height(16.dp))
                    Text(stringResource(R.string.history), fontSize = 12.sp, color = Color.Gray)
                }
            }
        },
        shape = RoundedCornerShape(22.dp)
    )
}

@Composable
fun SalaryLineChart(data: List<ReciboPagamento>) {
    val points = data.map { it.valorLiquido.replace(".", "").replace(",", ".").toDoubleOrNull() ?: 0.0 }
    val maxVal = points.maxOrNull() ?: 1.0
    val minVal = points.minOrNull() ?: 0.0
    val range = (maxVal - minVal).coerceAtLeast(1.0)

    val primaryColor = MaterialTheme.colorScheme.primary

    Column(modifier = Modifier.fillMaxWidth().height(220.dp).padding(8.dp)) {
        Canvas(modifier = Modifier.weight(1f).fillMaxWidth()) {
            val width = size.width
            val height = size.height
            val spacing = width / (points.size - 1).coerceAtLeast(1)

            val path = Path()
            points.forEachIndexed { index, value ->
                val x = index * spacing
                val normalizedValue = (value - minVal) / range
                val y = height - (normalizedValue.toFloat() * height)
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                
                drawCircle(color = primaryColor, radius = 4.dp.toPx(), center = androidx.compose.ui.geometry.Offset(x, y))
            }

            drawPath(
                path = path,
                color = primaryColor,
                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
            )
        }
        
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            data.forEachIndexed { index, item ->
                if (index == 0 || index == data.size - 1 || data.size <= 5) {
                    val label = item.periodo.split(" ").firstOrNull() ?: ""
                    Text(label, fontSize = 10.sp, color = Color.Gray)
                }
            }
        }
    }
}

@Composable
fun IosWidgetTimesheetFullCard(espelho: EspelhoPonto, userName: String, matricula: String, modifier: Modifier, onClick: () -> Unit, onOpen: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed) 0.98f else 1f, label = "scale")
    Surface(modifier = modifier.scale(scale).clickable(interactionSource = interactionSource, indication = null, onClick = onClick), shape = RoundedCornerShape(32.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 8.dp) {
        Column(modifier = Modifier.background(Brush.linearGradient(listOf(Color(0xFF007AFF), Color(0xFF00C6FF)))).padding(24.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(userName.ifEmpty { stringResource(R.string.timesheet) }, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text(espelho.periodo, color = Color.White.copy(alpha = 0.8f), fontSize = 22.sp, fontWeight = FontWeight.Black)
                }
                IconButton(onClick = onOpen) { Icon(Icons.Outlined.PictureAsPdf, null, tint = Color.White) }
            }
            Spacer(modifier = Modifier.height(20.dp))
            Text(text = espelho.saldoFinalBH, color = Color.White, fontSize = 44.sp, fontWeight = FontWeight.Black, letterSpacing = (-1).sp)
            Spacer(modifier = Modifier.height(20.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                SummaryItem(label = stringResource(R.string.current_balance).uppercase(), value = espelho.saldoFinalBH, modifier = Modifier.weight(1f))
                SummaryItem(label = stringResource(R.string.worked).uppercase(), value = espelho.resumoItens.find { it.label == "label_worked_hours" }?.value ?: "0:00", modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun IosWidgetCardClickable(title: String, value: String, color: Color, icon: ImageVector, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }, 
        color = MaterialTheme.colorScheme.surface, 
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(36.dp).background(color.copy(alpha = 0.1f), CircleShape), contentAlignment = Alignment.Center) { 
                Icon(icon, null, tint = color, modifier = Modifier.size(20.dp)) 
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
            Text(value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = color)
        }
    }
}

@Composable
fun IosWidgetFinanceWideCard(title: String, value: String, subtitle: String, color: Color, icon: ImageVector, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed) 0.97f else 1f, animationSpec = spring(stiffness = Spring.StiffnessLow), label = "scale")
    Surface(modifier = Modifier.fillMaxWidth().scale(scale).clickable(interactionSource = interactionSource, indication = null, onClick = onClick), shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 2.dp) {
        Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(50.dp).background(color.copy(alpha = 0.1f), CircleShape), contentAlignment = Alignment.Center) { Icon(icon, null, tint = color, modifier = Modifier.size(28.dp)) }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                Text(value, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = color)
                Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
            }
            Icon(Icons.Outlined.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f))
        }
    }
}

@Composable
fun IosWidgetSummaryLargeCard(espelho: EspelhoPonto, userName: String, matricula: String, onEdit: () -> Unit, onOpen: () -> Unit) {
    Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 4.dp) {
        Column(modifier = Modifier.background(Brush.verticalGradient(listOf(Color(0xFF007AFF), Color(0xFF005BBF)))).padding(24.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(modifier = Modifier.clickable { onEdit() }, verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(userName.ifEmpty { stringResource(R.string.user_label) }, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black)
                            if (matricula.isNotEmpty()) Text("${stringResource(R.string.matricula_label)}: $matricula", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                        }
                        Icon(Icons.Outlined.Edit, null, tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(16.dp))
                    }
                }
                IconButton(onClick = onOpen) { Icon(Icons.Outlined.PictureAsPdf, null, tint = Color.White) }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(stringResource(R.string.current_balance).uppercase(), color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(text = espelho.saldoFinalBH, color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(modifier = Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.worked).uppercase(), color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
                    Text(espelho.resumoItens.find { it.label == "label_worked_hours" }?.value ?: "0:00", color = Color.White, fontWeight = FontWeight.Bold)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.label_extra_hours_50).uppercase(), color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
                    val credit = espelho.resumoItens.find { it.label.contains("credit", true) || it.label.contains("extra", true) }?.value ?: "0:00"
                    Text(credit, color = Color.White, fontWeight = FontWeight.Bold)
                }
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

fun String.extractStartDate(): Date {
    val dateRegex = """\d{2}/\d{2}/\d{4}""".toRegex()
    return try { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).parse(dateRegex.find(this)?.value ?: "") ?: Date(0) } catch (_: Exception) { Date(0) }
}

fun String.extractStartDateForRecibo(): Date {
    val monthsMap = mapOf("JAN" to "01", "FEV" to "02", "MAR" to "03", "ABR" to "04", "MAI" to "05", "JUN" to "06", "JUL" to "07", "AGO" to "08", "SET" to "09", "OUT" to "10", "NOV" to "11", "DEZ" to "12")
    val text = this.uppercase()
    val nameMatch = """([A-Z]{3})\s+(\d{4})""".toRegex().find(text)
    if (nameMatch != null) {
        val monthName = nameMatch.groupValues[1]
        val year = nameMatch.groupValues[2]
        val monthNum = monthsMap[monthName]
        if (monthNum != null) return try { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).parse("01/$monthNum/$year") ?: Date(0) } catch (_: Exception) { Date(0) }
    }
    val dateRegex = """(\d{2})/(\d{4})""".toRegex()
    val match = dateRegex.find(this)
    if (match != null) {
        val month = match.groupValues[1]
        val year = match.groupValues[2]
        return try { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).parse("01/$month/$year") ?: Date(0) } catch (_: Exception) { Date(0) }
    }
    return try { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).parse(this) ?: Date(0) } catch (_: Exception) { Date(0) }
}

@Composable
fun IosWidgetCardReadOnly(title: String, value: String, color: Color, icon: ImageVector) {
    Surface(
        modifier = Modifier.fillMaxWidth(), 
        color = MaterialTheme.colorScheme.surface, 
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(36.dp).background(color.copy(alpha = 0.1f), CircleShape), contentAlignment = Alignment.Center) { 
                Icon(icon, null, tint = color, modifier = Modifier.size(20.dp)) 
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
            Text(value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = color)
        }
    }
}

fun getIconForLabel(label: String, isNegative: Boolean): ImageVector {
    return when {
        label.contains("worked") -> Icons.Outlined.Schedule
        label.contains("night") -> Icons.Outlined.NightsStay
        label.contains("extra") -> Icons.Outlined.TrendingUp
        label.contains("absence") || label.contains("excused") -> Icons.Outlined.CheckCircle
        isNegative -> Icons.Outlined.TrendingDown
        else -> Icons.Outlined.Info
    }
}

fun getIconForReciboItem(descricao: String, isProvento: Boolean): ImageVector {
    val d = descricao.uppercase()
    return when {
        d.contains("SALARIO") || d.contains("VENCIMENTO") -> Icons.Outlined.AttachMoney
        d.contains("HORA EXTRA") || d.contains("H.E") -> Icons.Outlined.Timer
        d.contains("ADICIONAL NOTURNO") -> Icons.Outlined.NightsStay
        d.contains("13O") || d.contains("GRATIFICACAO") -> Icons.Outlined.CardGiftcard
        d.contains("PERICULOSIDADE") || d.contains("INSALUBRIDADE") -> Icons.Outlined.WarningAmber
        d.contains("DSR") || d.contains("REPOUSO") -> Icons.Outlined.EventRepeat
        d.contains("PREMIO") || d.contains("BONUS") -> Icons.Outlined.EmojiEvents
        d.contains("AUXILIO") || d.contains("ABONO") || d.contains("VALE") -> Icons.Outlined.Redeem
        d.contains("INSS") || d.contains("IRRF") || d.contains("RENDA") -> Icons.Outlined.AccountBalance
        d.contains("MEDICO") || d.contains("SAUDE") || d.contains("ODONTO") -> Icons.Outlined.MedicalServices
        d.contains("SINDICATO") -> Icons.Outlined.Groups
        d.contains("FALTA") || d.contains("ATRASO") -> Icons.Outlined.EventBusy
        d.contains("CONSIGNADO") || d.contains("EMPRESTIMO") -> Icons.Outlined.CreditScore
        else -> if (isProvento) Icons.Outlined.AddCircleOutline else Icons.Outlined.RemoveCircleOutline
    }
}
