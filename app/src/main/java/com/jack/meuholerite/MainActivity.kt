package com.jack.meuholerite

import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
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
import com.jack.meuholerite.ui.*
import com.jack.meuholerite.ui.theme.MeuHoleriteTheme
import com.jack.meuholerite.utils.BackupManager
import com.jack.meuholerite.utils.GlobalMessage
import com.jack.meuholerite.utils.GlobalMessageManager
import com.jack.meuholerite.utils.PdfReader
import com.jack.meuholerite.utils.StorageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        MobileAds.setRequestConfiguration(RequestConfiguration.Builder().build())
        MobileAds.initialize(this) { RewardedInterstitialAdManager.loadAd(this) }

        val storageManager = StorageManager(this)
        createNotificationChannel()

        setContentView(
            ComposeView(this).apply {
                setContent {
                    val systemInDarkTheme = isSystemInDarkTheme()
                    var useDarkTheme by remember {
                        val hasSet = storageManager.hasDarkModeSet()
                        mutableStateOf(if (hasSet) storageManager.isDarkMode() else systemInDarkTheme)
                    }

                    val lifecycleOwner = LocalLifecycleOwner.current
                    DisposableEffect(lifecycleOwner) {
                        val observer = LifecycleEventObserver { _, event ->
                            if (event == Lifecycle.Event.ON_RESUME) {
                                val hasSet = storageManager.hasDarkModeSet()
                                useDarkTheme = if (hasSet) storageManager.isDarkMode() else systemInDarkTheme
                                if (intent?.getBooleanExtra("SHOW_AD_ON_RESUME", false) == true) {
                                    RewardedInterstitialAdManager.showAd(this@MainActivity) {}
                                    intent.removeExtra("SHOW_AD_ON_RESUME")
                                }
                            }
                        }
                        lifecycleOwner.lifecycle.addObserver(observer)
                        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                    }

                    MeuHoleriteTheme(darkTheme = useDarkTheme) {
                        AppLockGate(storage = storageManager) {
                            MainScreen()
                        }
                    }
                }
            }
        )
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val absenceChannel = NotificationChannel("ABSENCE_ALERTS", getString(R.string.absence_alerts), NotificationManager.IMPORTANCE_DEFAULT)
            val paymentChannel = NotificationChannel("PAYMENT_ALERTS", "Avisos de Pagamento", NotificationManager.IMPORTANCE_DEFAULT)
            val adChannel = NotificationChannel("AD_REMINDER_CHANNEL", "Anúncios", NotificationManager.IMPORTANCE_HIGH)
            manager.createNotificationChannel(absenceChannel)
            manager.createNotificationChannel(paymentChannel)
            manager.createNotificationChannel(adChannel)
        }
    }
}

sealed class Screen(val route: String, val label: Int, val icon: ImageVector) {
    object Home : Screen("home", R.string.home, Icons.Filled.Home)
    object Epays : Screen("epays", R.string.epays_label, Icons.Outlined.Public)
    object Recibos : Screen("recibos", R.string.receipts, Icons.Outlined.Description)
    object Ponto : Screen("ponto", R.string.timesheet, Icons.Outlined.Schedule)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MainScreen() {
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val db = remember { AppDatabase.getDatabase(context) }
    val gson = remember { Gson() }
    val prefs = remember { context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE) }
    val appPrefs = remember { context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE) }
    val backupManager = remember { BackupManager(context) }
    val globalMessageManager = remember { GlobalMessageManager(context) }

    var selectedEspelho by remember { mutableStateOf<EspelhoPonto?>(null) }
    var selectedRecibo by remember { mutableStateOf<ReciboPagamento?>(null) }
    var showAbsenceWarning by remember { mutableStateOf(false) }
    var userName by remember { mutableStateOf(prefs.getString("user_name", "") ?: "") }
    var userMatricula by remember { mutableStateOf(prefs.getString("user_matricula", "") ?: "") }
    var showOnboarding by remember { mutableStateOf(!appPrefs.getBoolean("onboarding_completed", false)) }
    
    // Popup states para Recibos e Ponto integrados
    var selectedReciboItemForPopup by remember { mutableStateOf<Pair<ReciboItem, Boolean>?>(null) }
    var selectedPontoItemForPopup by remember { mutableStateOf<Triple<String, String, Boolean>?>(null) }
    
    var globalMessageToShow by remember { mutableStateOf<GlobalMessage?>(null) }

    // Estado para controlar se o ePays já foi aberto pelo menos uma vez
    var hasLoadedEpays by remember { mutableStateOf(false) }

    suspend fun refreshData() {
        withContext(Dispatchers.IO) {
            val newName = prefs.getString("user_name", "") ?: ""
            val newMatricula = prefs.getString("user_matricula", "") ?: ""
            withContext(Dispatchers.Main) {
                userName = newName
                userMatricula = newMatricula
            }
            val list = db.espelhoDao().getAll().map { it.toModel(gson) }
            if (list.isNotEmpty()) selectedEspelho = list.sortedByDescending { it.periodo.extractStartDate() }.first()
            val listRecibos = db.reciboDao().getAll().map { it.toModel(gson) }
            if (listRecibos.isNotEmpty()) selectedRecibo = listRecibos.sortedByDescending { it.periodo.extractStartDateForRecibo() }.first()
            
            // Buscar mensagem global
            val latestMessage = globalMessageManager.fetchLatestMessage()
            if (latestMessage != null && globalMessageManager.isMessageNew(latestMessage.id)) {
                withContext(Dispatchers.Main) {
                    globalMessageToShow = latestMessage
                }
            }
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) scope.launch { refreshData() } }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (globalMessageToShow != null) {
        AlertDialog(
            onDismissRequest = { 
                globalMessageManager.markMessageAsSeen(globalMessageToShow?.id ?: "")
                globalMessageToShow = null 
            },
            title = { Text(globalMessageToShow?.title ?: "", fontWeight = FontWeight.Bold) },
            text = { Text(globalMessageToShow?.content ?: "") },
            confirmButton = {
                Button(onClick = {
                    globalMessageManager.markMessageAsSeen(globalMessageToShow?.id ?: "")
                    globalMessageToShow = null
                }) { Text(stringResource(R.string.understood)) }
            },
            shape = RoundedCornerShape(22.dp)
        )
    }

    fun openPdf(filePath: String?) {
        if (filePath == null) return
        val file = File(filePath)
        if (!file.exists()) return
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intentView = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
        }
        try { context.startActivity(Intent.createChooser(intentView, context.getString(R.string.open_pdf_with))) } catch (_: Exception) {
            Toast.makeText(context, context.getString(R.string.no_pdf_app), Toast.LENGTH_SHORT).show()
        }
    }

    suspend fun savePdfPermanently(uri: Uri, fileName: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                val directory = File(context.filesDir, "pdfs")
                if (!directory.exists()) directory.mkdirs()
                val destFile = File(directory, fileName)
                context.contentResolver.openInputStream(uri)?.use { input -> FileOutputStream(destFile).use { output -> input.copyTo(output) } }
                destFile.absolutePath
            } catch (_: Exception) { null }
        }
    }

    LaunchedEffect(Unit) {
        refreshData()
        delay(120000)
        val shown = AdsDataStore.wasShownHomeTimed(context)
        if (!shown) (context as? Activity)?.let { activity -> RewardedInterstitialAdManager.showAd(activity) { scope.launch { AdsDataStore.markShownHomeTimed(context) } } }
    }

    LaunchedEffect(selectedEspelho) {
        if (selectedEspelho?.hasAbsences == true) {
            showAbsenceWarning = true
            showAbsenceNotification(context, selectedEspelho?.periodo ?: "", selectedEspelho?.diasFaltas?.size ?: 0)
        }
    }

    if (showOnboarding) {
        OnboardingDialog {
            appPrefs.edit().putBoolean("onboarding_completed", true).apply()
            showOnboarding = false
            scope.launch { backupManager.backupData() }
        }
    }

    val activityIntent = (context as? Activity)?.intent
    LaunchedEffect(activityIntent) {
        if (activityIntent?.action == Intent.ACTION_VIEW && activityIntent.type == "application/pdf") {
            activityIntent.data?.let { uri ->
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
                        scope.launch { backupManager.backupData() }
                        navController.navigate(Screen.Ponto.route)
                    } else if (isRecibo) {
                        var novo = ReciboParser().parse(text)
                        if (novo.funcionario == context.getString(R.string.label_unknown) && userName.isNotEmpty()) {
                            novo = novo.copy(funcionario = userName)
                        }
                        val path = savePdfPermanently(uri, "recibo_${novo.periodo.replace("/", "_")}.pdf")
                        val updatedNovo = novo.copy(pdfFilePath = path)
                        selectedRecibo = updatedNovo
                        db.reciboDao().insert(updatedNovo.toEntity(gson, path))
                        scope.launch { backupManager.backupData() }
                        navController.navigate(Screen.Recibos.route)
                        if (updatedNovo.dataPagamento.isNotEmpty()) {
                            showPaymentNotification(context, updatedNovo.periodo, updatedNovo.dataPagamento)
                        }
                    }
                }
            }
        }
    }

    if (showAbsenceWarning) AbsenceWarningDialog { showAbsenceWarning = false }
    
    // Popup do Holerite
    if (selectedReciboItemForPopup != null) {
        DeductionDetailDialog(selectedReciboItemForPopup!!.first, selectedReciboItemForPopup!!.second) { selectedReciboItemForPopup = null }
    }
    
    // Popup do Ponto
    if (selectedPontoItemForPopup != null) {
        PontoDetailDialog(
            labelKey = selectedPontoItemForPopup!!.first,
            value = selectedPontoItemForPopup!!.second,
            isNegative = selectedPontoItemForPopup!!.third,
            onDismiss = { selectedPontoItemForPopup = null }
        )
    }

    val navigateTo = { screen: Screen ->
        if (screen == Screen.Epays) hasLoadedEpays = true
        navController.navigate(screen.route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { 
            IosTopBar(
                userName = userName,
                jornada = selectedEspelho?.jornada
            ) { 
                context.startActivity(Intent(context, SettingsActivity::class.java)) 
            } 
        },
        bottomBar = {
            val navBackStackEntry by navController.currentBackStackEntryAsState()
            val currentRoute = navBackStackEntry?.destination?.route
            val items = listOf(Screen.Home, Screen.Epays, Screen.Recibos, Screen.Ponto)

            NavigationBar(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)) {
                items.forEach { screen ->
                    NavigationBarItem(
                        selected = currentRoute == screen.route,
                        onClick = { navigateTo(screen) },
                        icon = { Icon(screen.icon, stringResource(screen.label)) },
                        label = { Text(stringResource(screen.label)) }
                    )
                }
            }
        }
    ) { innerPadding ->
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = navBackStackEntry?.destination?.route

        Box(modifier = Modifier.padding(innerPadding)) {
            // Camada do ePays WebView (Sempre viva mas só visível na rota ePays)
            if (hasLoadedEpays) {
                val isEpaysVisible = currentRoute == Screen.Epays.route
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .alpha(if (isEpaysVisible) 1f else 0f)
                        .zIndex(if (isEpaysVisible) 1f else -1f)
                ) {
                    EpaysWebViewPage { uri ->
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
                                    withContext(Dispatchers.IO) { db.espelhoDao().insert(updatedNovo.toEntity(gson, path)) }
                                    backupManager.backupData()
                                    refreshData()
                                    withContext(Dispatchers.Main) { navigateTo(Screen.Ponto) }
                                } else if (isRecibo) {
                                    var novo = ReciboParser().parse(text)
                                    if (novo.funcionario == context.getString(R.string.label_unknown) && userName.isNotEmpty()) {
                                        novo = novo.copy(funcionario = userName)
                                    }
                                    val path = savePdfPermanently(uri, "recibo_${novo.periodo.replace("/", "_")}.pdf")
                                    val updatedNovo = novo.copy(pdfFilePath = path)
                                    withContext(Dispatchers.IO) { db.reciboDao().insert(updatedNovo.toEntity(gson, path)) }
                                    backupManager.backupData()
                                    refreshData()
                                    if (updatedNovo.dataPagamento.isNotEmpty()) {
                                        showPaymentNotification(context, updatedNovo.periodo, updatedNovo.dataPagamento)
                                    }
                                    withContext(Dispatchers.Main) { navigateTo(Screen.Recibos) }
                                }
                            }
                        }
                    }
                }
            }

            NavHost(
                navController, 
                startDestination = Screen.Home.route,
                modifier = Modifier.zIndex(0f),
                enterTransition = { fadeIn(animationSpec = tween(300)) + slideInHorizontally(animationSpec = tween(300), initialOffsetX = { it / 2 }) },
                exitTransition = { fadeOut(animationSpec = tween(300)) + slideOutHorizontally(animationSpec = tween(300), targetOffsetX = { -it / 2 }) },
                popEnterTransition = { fadeIn(animationSpec = tween(300)) + slideInHorizontally(animationSpec = tween(300), initialOffsetX = { -it / 2 }) },
                popExitTransition = { fadeOut(animationSpec = tween(300)) + slideOutHorizontally(animationSpec = tween(300), targetOffsetX = { it / 2 }) }
            ) {
                composable(Screen.Home.route) {
                    HomeScreen(
                        selectedRecibo = selectedRecibo,
                        selectedEspelho = selectedEspelho,
                        onGoToRecibo = { navigateTo(Screen.Recibos) },
                        onGoToPonto = { navigateTo(Screen.Ponto) },
                        onRefresh = { scope.launch { refreshData() } },
                        db = db,
                        gson = gson,
                        onSelectItem = { item, isP -> selectedReciboItemForPopup = item to isP }
                    )
                }
                composable(Screen.Epays.route) {
                    // Placeholder vazio para que o NavHost mostre a rota, mas o conteúdo real é o WebView abaixo
                    Box(Modifier.fillMaxSize())
                }
                composable(Screen.Recibos.route) {
                    ReceiptsScreen(
                        recibo = selectedRecibo,
                        db = db,
                        gson = gson,
                        userName = userName,
                        userMatricula = userMatricula,
                        onEditProfile = {},
                        onOpen = { openPdf(it) },
                        onSelect = { selectedRecibo = it },
                        onRefresh = { scope.launch { refreshData() } },
                        onSelectItem = { item, isP -> selectedReciboItemForPopup = item to isP }
                    )
                }
                composable(Screen.Ponto.route) {
                    TimesheetScreen(
                        espelho = selectedEspelho,
                        db = db,
                        gson = gson,
                        userName = userName,
                        userMatricula = userMatricula,
                        onEditProfile = {},
                        onSelect = { selectedEspelho = it },
                        onOpen = { openPdf(it) },
                        onRefresh = { scope.launch { refreshData() } },
                        onSelectItem = { label, value, isNeg -> 
                            selectedPontoItemForPopup = Triple(label, value, isNeg)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun AbsenceWarningDialog(onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.understood)) } }, title = { Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Filled.Warning, null, tint = Color(0xFFFF3B30)); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.absences_detected), fontWeight = FontWeight.Bold) } }, text = { Text(stringResource(R.string.warning_absences), lineHeight = 20.sp) }, shape = RoundedCornerShape(22.dp))
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun OnboardingDialog(onFinish: () -> Unit) {
    val steps = listOf(OnboardingStep(R.string.onboarding_welcome_title, R.string.onboarding_welcome_desc, Icons.Outlined.WavingHand, Color(0xFF007AFF)), OnboardingStep(R.string.onboarding_import_title, R.string.onboarding_import_desc, Icons.Outlined.CloudDownload, Color(0xFF5856D6)), OnboardingStep(R.string.onboarding_receipts_title, R.string.onboarding_receipts_desc, Icons.Outlined.AccountBalanceWallet, Color(0xFF34C759)), OnboardingStep(R.string.onboarding_ponto_title, R.string.onboarding_ponto_desc, Icons.Outlined.Schedule, Color(0xFFFF9500)), OnboardingStep(R.string.onboarding_privacy_title, R.string.onboarding_privacy_desc, Icons.Outlined.Shield, Color(0xFFFF3B30)), OnboardingStep(R.string.onboarding_finish_title, R.string.onboarding_finish_desc, Icons.Outlined.Person, Color(0xFF8E8E93)))
    val pagerState = rememberPagerState(pageCount = { steps.size })
    val scope = rememberCoroutineScope()
    AlertDialog(onDismissRequest = {}, properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false, usePlatformDefaultWidth = false), modifier = Modifier.fillMaxSize().padding(12.dp), content = { Surface(modifier = Modifier.fillMaxWidth().wrapContentHeight(), shape = RoundedCornerShape(32.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 8.dp) { Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) { HorizontalPager(state = pagerState, modifier = Modifier.fillMaxWidth().height(600.dp)) { page -> val step = steps[page]; Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center, modifier = Modifier.fillMaxSize()) { Box(modifier = Modifier.size(160.dp).background(step.color.copy(alpha = 0.1f), CircleShape), contentAlignment = Alignment.Center) { Icon(step.icon, null, tint = step.color, modifier = Modifier.size(90.dp)) }; Spacer(Modifier.height(40.dp)); Text(stringResource(step.titleRes), fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center, lineHeight = 38.sp); Spacer(Modifier.height(24.dp)); Text(stringResource(step.descRes), fontSize = 19.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), textAlign = TextAlign.Center, lineHeight = 28.sp) } }; Spacer(Modifier.height(24.dp)); Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { repeat(steps.size) { iteration -> val color = if (pagerState.currentPage == iteration) steps[iteration].color else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f); Box(modifier = Modifier.padding(2.dp).clip(CircleShape).background(color).size(12.dp)) } }; Button(onClick = { if (pagerState.currentPage < steps.size - 1) scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) } else onFinish() }, shape = RoundedCornerShape(16.dp)) { Text(if (pagerState.currentPage == steps.size - 1) stringResource(R.string.start) else stringResource(R.string.next)) } } } } })
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(selectedRecibo: ReciboPagamento?, selectedEspelho: EspelhoPonto?, onGoToRecibo: () -> Unit, onGoToPonto: () -> Unit, onRefresh: () -> Unit, db: AppDatabase, gson: Gson, onSelectItem: (ReciboItem, Boolean) -> Unit) {
    val scope = rememberCoroutineScope()
    var isRefreshing by remember { mutableStateOf(false) }
    var showSalaryGraph by remember { mutableStateOf(false) }
    val context = LocalContext.current

    if (showSalaryGraph) { SalaryGraphDialog(db, gson) { showSalaryGraph = false } }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = {
            scope.launch {
                isRefreshing = true
                onRefresh()
                (context as? Activity)?.let { activity ->
                    RewardedInterstitialAdManager.showAd(activity) { }
                }
                delay(1000)
                isRefreshing = false
            }
        },
        modifier = Modifier.fillMaxSize()
    ) {
        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = selectedRecibo?.periodo?.uppercase() ?: stringResource(R.string.welcome_user).uppercase(),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 1.sp
                    )

                    if (selectedEspelho != null) {
                        Surface(
                            onClick = onGoToPonto,
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.Schedule, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(6.dp))
                                Text(selectedEspelho.saldoFinalBH, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                Column(modifier = Modifier.clickable { showSalaryGraph = true }) {
                    Column {
                        Text(
                            stringResource(R.string.net_pay_label),
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        Text(
                            text = if (selectedRecibo != null) "R$ ${selectedRecibo.valorLiquido}" else "R$ 0,00",
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis, softWrap = false
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.earnings), fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.Medium)
                        Text(
                            "R$ ${selectedRecibo?.totalProventos ?: "0,00"}",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF34C759)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.deductions), fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.Medium)
                        Text(
                            "R$ ${selectedRecibo?.totalDescontos ?: "0,00"}",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFF3B30)
                        )
                    }
                    if (selectedEspelho != null) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.worked), fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.Medium)
                            Text(
                                selectedEspelho.resumoItens.find { it.label == "label_worked_hours" }?.value ?: "0:00",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))
                HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
            }

            Column(modifier = Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (selectedEspelho?.hasAbsences == true) {
                    Spacer(modifier = Modifier.height(4.dp))
                    AbsenceDetailCard(selectedEspelho)
                }
                
                SectionHeader("Simuladores e Ferramentas")
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                   IosWidgetFinanceWideCard(
                        title = "13º Salário", 
                        value = "Projetar", 
                        subtitle = "Média das parcelas", 
                        color = Color(0xFF5856D6), 
                        icon = Icons.Outlined.Redeem, 
                        onClick = { context.startActivity(Intent(context, ThirteenthActivity::class.java)) },
                        modifier = Modifier.weight(1f)
                    )
                }
                
                IosWidgetFinanceWideCard(
                    title = "Rescisão", 
                    value = "Calcular", 
                    subtitle = "Pedido ou Demissão", 
                    color = Color(0xFFFF9500), 
                    icon = Icons.Outlined.Gavel, 
                    onClick = { context.startActivity(Intent(context, ResignationActivity::class.java)) }
                )

                SectionHeader("Gestão de Férias")
                IosWidgetFinanceWideCard(
                    title = "Minhas Férias", 
                    value = "Consultar", 
                    subtitle = "Projeção e dias acumulados", 
                    color = Color(0xFF007AFF),
                    icon = Icons.Outlined.BeachAccess, 
                    onClick = { context.startActivity(Intent(context, VacationActivity::class.java)) }
                )

                if (selectedRecibo != null && selectedRecibo.descontos.isNotEmpty()) {
                    SectionHeader(stringResource(R.string.main_deductions))
                    val sortedDescontos = selectedRecibo.descontos.sortedByDescending { it.valor.replace(".", "").replace(",", ".").toDoubleOrNull() ?: 0.0 }.take(3)

                    if (sortedDescontos.isNotEmpty()) {
                        ReceiptItemCard(
                            item = sortedDescontos[0],
                            color = Color(0xFFFF3B30),
                            compact = false,
                            modifier = Modifier.fillMaxWidth()
                        ) { onSelectItem(sortedDescontos[0], false) }
                    }

                    if (sortedDescontos.size > 1) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            sortedDescontos.drop(1).forEach { item ->
                                ReceiptItemCard(
                                    item = item,
                                    color = Color(0xFFFF3B30),
                                    compact = true,
                                    modifier = Modifier.weight(1f)
                                ) { onSelectItem(item, false) }
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
}

@Composable
fun SalaryGraphDialog(db: AppDatabase, gson: Gson, onDismiss: () -> Unit) {
    var history by remember { mutableStateOf<List<ReciboPagamento>>(emptyList()) }; var isLoading by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) { withContext(Dispatchers.IO) { val list = db.reciboDao().getAll().map { it.toModel(gson) }.sortedBy { it.periodo.extractStartDateForRecibo() }; history = list; isLoading = false } }
    AlertDialog(onDismissRequest = onDismiss, confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } }, title = { Text(stringResource(R.string.salary_evolution), fontWeight = FontWeight.Bold) }, text = { if (isLoading) { Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } } else if (history.isEmpty()) { Text(stringResource(R.string.no_chart_data)) } else { Column(Modifier.fillMaxWidth()) { SalaryLineChart(history); Spacer(Modifier.height(16.dp)); Text(stringResource(R.string.chart_basis, history.size), fontSize = 12.sp, color = Color.Gray) } } }, shape = RoundedCornerShape(22.dp))
}

@Composable
fun SalaryLineChart(data: List<ReciboPagamento>) {
    val points = data.map { it.valorLiquido.replace(".", "").replace(",", ".").toDoubleOrNull() ?: 0.0 }; val maxVal = points.maxOrNull() ?: 1.0; val minVal = points.minOrNull() ?: 0.0; val range = (maxVal - minVal).coerceAtLeast(1.0); val primaryColor = MaterialTheme.colorScheme.primary
    Column(modifier = Modifier.fillMaxWidth().height(220.dp).padding(8.dp)) { Canvas(modifier = Modifier.weight(1f).fillMaxWidth()) { val width = size.width; val height = size.height; val spacing = width / (points.size - 1).coerceAtLeast(1); val path = Path(); points.forEachIndexed { index, value -> val x = index * spacing; val normalizedValue = (value - minVal) / range; val y = height - (normalizedValue.toFloat() * height); if (index == 0) path.moveTo(x, y) else path.lineTo(x, y); drawCircle(color = primaryColor, radius = 4.dp.toPx(), center = androidx.compose.ui.geometry.Offset(x, y)) }; drawPath(path = path, color = primaryColor, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)) }; Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) { data.forEachIndexed { index, item -> if (index == 0 || index == data.size - 1 || data.size <= 5) { val label = item.periodo.split(" ").firstOrNull() ?: ""; Text(label, fontSize = 10.sp, color = Color.Gray) } } } } }

fun String.extractStartDate(): Date { val dateRegex = """\d{2}/\d{2}/\d{4}""".toRegex(); return try { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).parse(dateRegex.find(this)?.value ?: "") ?: Date(0) } catch (_: Exception) { Date(0) } }

fun String.extractStartDateForRecibo(): Date {
    val monthsMap = mapOf("JAN" to "01", "FEV" to "02", "MAR" to "03", "ABR" to "04", "MAI" to "05", "JUN" to "06", "JUL" to "07", "AGO" to "08", "SET" to "09", "OUT" to "10", "NOV" to "11", "DEZ" to "12"); val text = this.uppercase(); val nameMatch = """([A-Z]{3})\s+(\d{4})""".toRegex().find(text)
    if (nameMatch != null) { val monthName = nameMatch.groupValues[1]; val year = nameMatch.groupValues[2]; val monthNum = monthsMap[monthName]; if (monthNum != null) return try { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).parse("01/$monthNum/$year") ?: Date(0) } catch (_: Exception) { Date(0) } }
    val dateRegex = """(\d{2})/(\d{4})""".toRegex(); val match = dateRegex.find(this)
    if (match != null) { val month = match.groupValues[1]; val year = match.groupValues[2]; return try { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).parse("01/$month/$year") ?: Date(0) } catch (_: Exception) { Date(0) } }
    return try { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).parse(this) ?: Date(0) } catch (_: Exception) { Date(0) }
}

data class OnboardingStep(val titleRes: Int, val descRes: Int, val icon: ImageVector, val color: Color)
