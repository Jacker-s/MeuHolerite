package com.jack.meuholerite

import android.Manifest
import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.TrendingDown
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.animation.core.*
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import coil.compose.AsyncImage
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import com.google.androidbrowserhelper.trusted.NotificationUtils
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.tasks.await
import com.google.gson.Gson
import com.jack.meuholerite.ads.AdsDataStore
import com.jack.meuholerite.ads.RewardedInterstitialAdManager
import com.jack.meuholerite.database.AppDatabase
import com.jack.meuholerite.database.toEntity
import com.jack.meuholerite.database.toModel
import com.jack.meuholerite.model.EspelhoPonto
import com.jack.meuholerite.model.ReciboItem
import com.jack.meuholerite.model.ReciboPagamento
import com.jack.meuholerite.model.InformeRendimento
import com.jack.meuholerite.parser.AiParser
import com.jack.meuholerite.parser.PontoParser
import com.jack.meuholerite.parser.ReciboParser
import com.jack.meuholerite.parser.InformeParser
import com.jack.meuholerite.scanner.PdfScanWorker
import com.jack.meuholerite.service.EspelhoReminderWorker
import com.jack.meuholerite.ui.*
import com.jack.meuholerite.ui.FullscreenPdfViewerDialog
import com.jack.meuholerite.ui.PremiumEvolutionChart
import com.jack.meuholerite.ui.theme.MeuHoleriteTheme
import com.jack.meuholerite.utils.*
import com.jack.meuholerite.utils.SalaryRanking
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt
import java.util.Locale

@Composable
private fun rememberEntranceMotion(delayMillis: Int = 0): State<Float> {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(delayMillis) {
        delay(delayMillis.toLong())
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing)
        )
    }
    return progress.asState()
}

private fun Modifier.entranceMotion(progress: Float, offsetY: Float = 48f, scaleStart: Float = 0.96f): Modifier {
    val eased = FastOutSlowInEasing.transform(progress.coerceIn(0f, 1f))
    val translationY = ((1f - eased) * offsetY).roundToInt()
    val scale = scaleStart + ((1f - scaleStart) * eased)
    return this
        .offset { IntOffset(0, translationY) }
        .scale(scale)
        .alpha(eased)
}

data class SmartAlert(
    val title: String,
    val message: String,
    val color: Color,
    val icon: ImageVector
)

private data class MonthlySummary(
    val gross: Double,
    val net: Double,
    val discounts: Double,
    val expenses: Double,
    val debts: Double,
    val remaining: Double
)

class MainActivity : FragmentActivity() {



    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) schedulePdfScanWorker()
        else Toast.makeText(this, "Permissão necessária para escanear PDFs.", Toast.LENGTH_LONG).show()
    }

    private var currentIntentState = mutableStateOf<Intent?>(null)

    private val showRemoveAdsDialog = mutableStateOf(false)
    private lateinit var billingManager: com.jack.meuholerite.utils.BillingManager

    private lateinit var appUpdateManager: AppUpdateManager
    private val installStateListener = InstallStateUpdatedListener { state ->
        if (state.installStatus() == InstallStatus.DOWNLOADED) {
            // No modo imediato o SDK geralmente já cuida disso, 
            // mas mantemos um log ou aviso por segurança.
            Log.d("Update", "Atualização baixada.")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        appUpdateManager = AppUpdateManagerFactory.create(this)
        checkForUpdates()
        appUpdateManager.registerListener(installStateListener)

        val prefs = getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("terms_accepted", false)) {
            startActivity(Intent(this, TermsAgreementActivity::class.java))
            finish()
            return
        }

        askNotificationPermission()
        schedulePdfScanWorker()
        scheduleEspelhoReminderWorker()
        if (getSharedPreferences("user_prefs", Context.MODE_PRIVATE).getBoolean("promo_notif_enabled", true)) {
            FirebaseMessaging.getInstance().subscribeToTopic("promocoes")
        }
        
        lifecycleScope.launch { 
            com.jack.meuholerite.ads.AdsDataStore.incrementAppOpenCount(this@MainActivity) 
        }

        // Garante que o usuário complete a atualização se já tiver começado
        appUpdateManager.appUpdateInfo.addOnSuccessListener { appUpdateInfo ->
            if (appUpdateInfo.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS) {
                appUpdateManager.startUpdateFlowForResult(appUpdateInfo, AppUpdateType.IMMEDIATE, this, 100)
            }
        }

        // Google AdMob Initialization
        MobileAds.initialize(this) { RewardedInterstitialAdManager.loadAd(this) }




        billingManager = com.jack.meuholerite.utils.BillingManager(this)
        billingManager.startConnection()

        val storageManager = StorageManager(this)
        val googleDriveBackupManager = GoogleDriveBackupManager(this)
        val backupManager = com.jack.meuholerite.utils.BackupManager(this)
        createNotificationChannel()
        
        lifecycleScope.launch {
            backupManager.checkAndRestoreIfEmpty()
        }

        currentIntentState.value = intent

        setContentView(
            ComposeView(this).apply {
                setContent {
                    val systemInDarkTheme = isSystemInDarkTheme()
                    var useDarkTheme by remember {
                        val hasSet = storageManager.hasDarkModeSet()
                        mutableStateOf(if (hasSet) storageManager.isDarkMode() else systemInDarkTheme)
                    }
                    var isPrivacyActive by remember { mutableStateOf(storageManager.isHideValuesEnabled()) }

                    val lifecycleOwner = LocalLifecycleOwner.current
                    val scope = rememberCoroutineScope()

                    DisposableEffect(lifecycleOwner) {
                        val observer = LifecycleEventObserver { _, event ->
                            if (event == Lifecycle.Event.ON_RESUME) {
                                val hasSet = storageManager.hasDarkModeSet()
                                useDarkTheme = if (hasSet) storageManager.isDarkMode() else systemInDarkTheme
                                isPrivacyActive = storageManager.isHideValuesEnabled()
                            }
                        }
                        lifecycleOwner.lifecycle.addObserver(observer)
                        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                    }

                    // In-App Review Loop (Every 30 seconds)
                    LaunchedEffect(Unit) {
                        while (isActive) {
                            delay(30_000)
                            if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                                ReviewHelper.incrementUsageTime(this@MainActivity, 30_000)
                                val totalTime = ReviewHelper.getTotalUsageTime(this@MainActivity)
                                val alreadyReviewed = ReviewHelper.hasReviewed(this@MainActivity)
                                
                                android.util.Log.d("ReviewHelper", "Tempo total de uso: ${totalTime / 1000}s, Já avaliou: $alreadyReviewed")
     
                                if (totalTime >= 120_000 && !alreadyReviewed) {
                                    android.util.Log.d("ReviewHelper", "Tentando solicitar avaliação...")
                                    ReviewHelper.requestReview(this@MainActivity) {
                                        scope.launch {
                                            ReviewHelper.markAsReviewed(this@MainActivity)
                                            android.util.Log.d("ReviewHelper", "Avaliação marcada como concluída.")
                                        }
                                    }
                                }
                            }
                        }
                    }

                    MeuHoleriteTheme(darkTheme = useDarkTheme) {
                        AppLockGate(storage = storageManager) {
                            CompositionLocalProvider(LocalPrivacyActive provides isPrivacyActive) {
                                // Admin Suggestion Listener

                                val auth = FirebaseAuth.getInstance()
                                val currentUser = auth.currentUser
                                if (currentUser != null && currentUser.email == "ssj53415170@gmail.com") {
                                    val firestore = FirebaseFirestore.getInstance()
                                    DisposableEffect(Unit) {
                                        val listener = firestore.collection("suggestions")
                                            .whereEqualTo("status", "PENDENTE")
                                            .addSnapshotListener { snapshot, _ ->
                                                snapshot?.documentChanges?.forEach { dc ->
                                                    if (dc.type == DocumentChange.Type.ADDED) {
                                                        val suggestionTxt = dc.document.getString("suggestion") ?: ""
                                                        val userNm = dc.document.getString("userName") ?: "Alguém"
                                                        showSuggestionNotification(userNm, suggestionTxt)
                                                    }
                                                }
                                            }
                                        onDispose { listener.remove() }
                                    }
                                }

                                MainScreen(currentIntentState.value, storageManager, googleDriveBackupManager, showRemoveAdsDialog, billingManager, isPrivacyActive) { newState ->
                                    isPrivacyActive = newState
                                    storageManager.setHideValues(newState)
                                }
                            }
                        }
                    }
                }
            }
        )
    }

    private fun showSuggestionNotification(from: String, text: String) {
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(this, 1001, intent, PendingIntent.FLAG_IMMUTABLE)

        val notification = NotificationCompat.Builder(this, "GLOBAL_MESSAGES")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Nova Sugestão de $from")
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager


            val absenceChannel = NotificationChannel(
                "ABSENCE_ALERTS",
                getString(R.string.absence_alerts),
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val paymentChannel = NotificationChannel(
                "PAYMENT_ALERTS",
                "Avisos de Pagamento",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val adChannel = NotificationChannel(
                "AD_REMINDER_CHANNEL",
                "Anúncios",
                NotificationManager.IMPORTANCE_HIGH
            )
            val globalChannel = NotificationChannel(
                "GLOBAL_MESSAGES",
                "Mensagens Globais",
                NotificationManager.IMPORTANCE_HIGH
            )
            val promoChannel = NotificationChannel(
                "PROMO_NOTIFICATIONS",
                "Promoções",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            manager.createNotificationChannel(absenceChannel)
            manager.createNotificationChannel(paymentChannel)
            manager.createNotificationChannel(adChannel)
            manager.createNotificationChannel(globalChannel)
            manager.createNotificationChannel(promoChannel)
        }
    }

    private fun checkForUpdates() {
        val appUpdateInfoTask = appUpdateManager.appUpdateInfo
        appUpdateInfoTask.addOnSuccessListener { appUpdateInfo ->
            if (appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE
                && appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)) {
                appUpdateManager.startUpdateFlowForResult(
                    appUpdateInfo,
                    AppUpdateType.IMMEDIATE,
                    this,
                    100
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        appUpdateManager.appUpdateInfo.addOnSuccessListener { appUpdateInfo ->
            if (appUpdateInfo.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS) {
                appUpdateManager.startUpdateFlowForResult(
                    appUpdateInfo,
                    AppUpdateType.IMMEDIATE,
                    this,
                    100
                )
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 100) {
            if (resultCode != RESULT_OK) {
                // Se o usuário cancelar ou a atualização imediata falhar, 
                // chamamos o checkForUpdates novamente para forçar o fluxo.
                checkForUpdates()
            }
        }
    }

    private fun schedulePdfScanWorker() {
        val constraints = androidx.work.Constraints.Builder()
            .setRequiresStorageNotLow(true)
            .build()
        val workRequest = PeriodicWorkRequestBuilder<PdfScanWorker>(24, TimeUnit.HOURS)
            .setConstraints(constraints)
            .setInitialDelay(10, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "PdfScanWorker",
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }

    private fun scheduleEspelhoReminderWorker() {
        val workRequest = PeriodicWorkRequestBuilder<EspelhoReminderWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(1, TimeUnit.HOURS)
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "EspelhoReminderWorker",
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }

    private fun showAdWithDialog(onAdDismissed: () -> Unit = {}) {
        RewardedInterstitialAdManager.showAd(this) {
            lifecycleScope.launch {
                com.jack.meuholerite.ads.AdsDataStore.incrementAdsShown(this@MainActivity)
                val adsRemoved = com.jack.meuholerite.ads.AdsDataStore.isAdsRemoved(this@MainActivity)
                val promptShown = com.jack.meuholerite.ads.AdsDataStore.wasRemoveAdsPromptShown(this@MainActivity)
                val adsCount = com.jack.meuholerite.ads.AdsDataStore.getTotalAdsShown(this@MainActivity)
                
                if (!adsRemoved && !promptShown && adsCount >= 3) {
                    showRemoveAdsDialog.value = true
                    com.jack.meuholerite.ads.AdsDataStore.markRemoveAdsPromptShown(this@MainActivity)
                }
                onAdDismissed()
            }
        }
    }
}

sealed class Screen(val route: String, val label: Int, val icon: ImageVector) {
    object Home : Screen("home", R.string.home, Icons.Filled.Home)
    object Epays : Screen("epays", R.string.epays_label, Icons.Outlined.CloudSync)
    object Recibos : Screen("recibos", R.string.receipts, Icons.Outlined.Description)
    object Ponto : Screen("ponto", R.string.timesheet, Icons.Outlined.Schedule)
    object Tools : Screen("tools", R.string.tools_label, Icons.Outlined.Handyman)
    object Promocoes : Screen("promocoes_feed", R.string.home, Icons.Outlined.LocalOffer)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MainScreen(
    activityIntent: Intent?, 
    storage: StorageManager, 
    backupManager: GoogleDriveBackupManager,
    showRemoveAdsDialog: MutableState<Boolean>,
    billingManager: com.jack.meuholerite.utils.BillingManager,
    isPrivacyActive: Boolean,
    onPrivacyChange: (Boolean) -> Unit
) {
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val db = remember { AppDatabase.getDatabase(context) }
    val gson = remember { Gson() }
    val firestoreManager = remember { com.jack.meuholerite.utils.BackupManager(context) }
    val prefs = remember { context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE) }
    val appPrefs = remember { context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE) }
    val globalMessageManager = remember { GlobalMessageManager(context) }
    val adsRemovedState by com.jack.meuholerite.ads.AdsDataStore.isAdsRemovedFlow(context).collectAsState(initial = false)

    var showRestorePrompt by remember { 
        mutableStateOf(activityIntent?.getBooleanExtra("JUST_LOGGED_IN", false) == true)
    }

    var isRestoring by remember { mutableStateOf(false) }
    var restoreProgress by remember { mutableStateOf(0f) }
    var googleAccount by remember { mutableStateOf(com.google.android.gms.auth.api.signin.GoogleSignIn.getLastSignedInAccount(context)) }

    var selectedEspelho by remember { mutableStateOf<EspelhoPonto?>(null) }
    var selectedRecibo by remember { mutableStateOf<ReciboPagamento?>(null) }
    var showAbsenceWarning by remember { mutableStateOf(false) }
    var userName by remember { mutableStateOf(prefs.getString("user_name", "") ?: "") }
    var userMatricula by remember { mutableStateOf(prefs.getString("user_matricula", "") ?: "") }
    var userCargo by remember { mutableStateOf(prefs.getString("user_cargo", "") ?: "") }
    var showOnboarding by remember { mutableStateOf(!appPrefs.getBoolean("onboarding_completed", false)) }
    var showLaborAiChat by remember { mutableStateOf(false) }
    var importSuccessData by remember { mutableStateOf<Triple<String, String, String>?>(null) }
    var showSignReminder by remember { mutableStateOf(false) }
    var globalMessageToShow by remember { mutableStateOf<GlobalMessage?>(null) }
    var hasLoadedEpays by remember { mutableStateOf(false) }
    var promoList by remember { mutableStateOf<List<com.jack.meuholerite.model.Promocao>>(emptyList()) }
    var promoNotifEnabled by remember { mutableStateOf(prefs.getBoolean("promo_notif_enabled", true)) }

    // Real-time listener for promocoes (elimina get() redundante)
    DisposableEffect(Unit) {
        val ref = FirebaseFirestore.getInstance().collection("promocoes")
        val listener = ref.addSnapshotListener { snapshot, error ->
            if (error != null) {
                android.util.Log.e("Promocoes", "Erro no listener: ${error.message}")
                return@addSnapshotListener
            }
            if (snapshot != null) {
                val now = System.currentTimeMillis()
                promoList = snapshot.documents.mapNotNull { doc ->
                    val data = doc.data ?: return@mapNotNull null
                    val expirada = data["expirada"] as? Boolean ?: false
                    val expiraEm = (data["expiraEm"] as? Number)?.toLong() ?: 0L
                    if (expirada || (expiraEm > 0 && expiraEm < now)) return@mapNotNull null
                    com.jack.meuholerite.model.Promocao(
                        id = doc.id,
                        titulo = data["titulo"] as? String ?: "",
                        descricao = data["descricao"] as? String ?: "",
                        imagemUrl = data["imagemUrl"] as? String ?: "",
                        precoAntes = (data["precoAntes"] as? Number)?.toDouble() ?: 0.0,
                        precoDepois = (data["precoDepois"] as? Number)?.toDouble() ?: 0.0,
                        link = data["link"] as? String ?: "",
                        loja = data["loja"] as? String ?: "",
                        cupom = data["cupom"] as? String ?: "",
                        verificado = data["verificado"] as? Boolean ?: true,
                        expirada = false,
                        expiraEm = expiraEm,
                        curtidas = (data["curtidas"] as? Number)?.toLong() ?: 0L,
                        timestamp = (data["timestamp"] as? Number)?.toLong() ?: 0L
                    )
                }.sortedByDescending { it.timestamp }
                android.util.Log.d("Promocoes", "Listener atualizou: ${promoList.size} promoções")
            }
        }
        onDispose { listener.remove() }
    }

    fun safeShowAd(onAdDismissed: () -> Unit = {}) {
        (context as? Activity)?.let { activity ->
            RewardedInterstitialAdManager.showAd(activity) {
                scope.launch {
                    com.jack.meuholerite.ads.AdsDataStore.incrementAdsShown(context)
                    val removed = com.jack.meuholerite.ads.AdsDataStore.isAdsRemoved(context)
                    val promptShown = com.jack.meuholerite.ads.AdsDataStore.wasRemoveAdsPromptShown(context)
                    val adsCount = com.jack.meuholerite.ads.AdsDataStore.getTotalAdsShown(context)
                    
                    if (!removed && !promptShown && adsCount >= 3) {
                        showRemoveAdsDialog.value = true
                        com.jack.meuholerite.ads.AdsDataStore.markRemoveAdsPromptShown(context)
                    }
                    onAdDismissed()
                }
            }
        }
    }

    suspend fun refreshData() {
        withContext(Dispatchers.IO) {
            val newName = prefs.getString("user_name", "") ?: ""
            val newMatricula = prefs.getString("user_matricula", "") ?: ""
            withContext(Dispatchers.Main) {
                userName = newName
                userMatricula = newMatricula
                userCargo = prefs.getString("user_cargo", "") ?: ""
            }

            val pontos = db.espelhoDao().getAll().map { it.toModel(gson) }
            if (pontos.isNotEmpty()) selectedEspelho = pontos.sortedByDescending { it.periodo.extractStartDate() }.firstOrNull()

            val recibos = db.reciboDao().getAll().map { it.toModel(gson) }
            if (recibos.isNotEmpty()) selectedRecibo = recibos.sortedByDescending { it.periodo.extractStartDateForRecibo() }.firstOrNull()

            val latestMessage = globalMessageManager.fetchLatestMessage()
            if (latestMessage != null && globalMessageManager.isMessageNew(latestMessage.id)) {
                withContext(Dispatchers.Main) { globalMessageToShow = latestMessage }
            }
        }
    }

    val googleSignInClient = remember {
        val gso = com.google.android.gms.auth.api.signin.GoogleSignInOptions.Builder(com.google.android.gms.auth.api.signin.GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(com.google.android.gms.common.api.Scope(com.google.api.services.drive.DriveScopes.DRIVE_FILE))
            .build()
        com.google.android.gms.auth.api.signin.GoogleSignIn.getClient(context, gso)
    }

    val googleSignInLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val task = com.google.android.gms.auth.api.signin.GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(com.google.android.gms.common.api.ApiException::class.java)
                if (account != null) {
                    googleAccount = account
                    // Gatilho da restauração dual
                    scope.launch {
                        isRestoring = true
                        restoreProgress = 0.1f
                        
                        // 1. Restaurar PDFs (Drive)
                        backupManager.restoreNow(account)
                        restoreProgress = 0.5f
                        
                        // 2. Restaurar Dados (Firestore)
                        firestoreManager.restoreData()
                        restoreProgress = 1.0f
                        
                        delay(500)
                        isRestoring = false
                        refreshData()
                        Toast.makeText(context, "Backup restaurado com sucesso!", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Erro ao conectar conta Google.", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    // Backup account recovery
    LaunchedEffect(Unit) {
        if (googleAccount == null) {
            googleSignInClient.silentSignIn().addOnSuccessListener { restored ->
                googleAccount = restored
            }
        }
    }

    if (showRestorePrompt) {
        AlertDialog(
            onDismissRequest = { showRestorePrompt = false },
            title = { Text("Restaurar Backup?", fontWeight = FontWeight.Bold) },
            text = { Text("Deseja restaurar seus arquivos PDF do Google Drive e seus dados da nuvem agora? Isso exige acesso aos arquivos do Drive.") },
            confirmButton = {
                Button(onClick = {
                    showRestorePrompt = false
                    googleSignInLauncher.launch(googleSignInClient.signInIntent)
                }) { Text("Sim, Restaurar") }
            },
            dismissButton = {
                TextButton(onClick = { showRestorePrompt = false }) { Text("Agora não") }
            },
            shape = RoundedCornerShape(22.dp)
        )
    }

    if (isRestoring) {
        Dialog(onDismissRequest = {}) {
            Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.padding(24.dp).fillMaxWidth()) {
                Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(progress = { restoreProgress })
                    Spacer(Modifier.height(16.dp))
                    Text("Restaurando seus PDFs e dados...", fontWeight = FontWeight.Bold)
                    Text("${(restoreProgress * 100).toInt()}%", fontSize = 12.sp)
                }
            }
        }
    }



    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                scope.launch {
                    refreshData()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (globalMessageToShow != null && !showOnboarding && !showRestorePrompt && !isRestoring) {
        AlertDialog(
            onDismissRequest = {
                globalMessageManager.markMessageAsSeen(globalMessageToShow?.id ?: "")
                globalMessageToShow = null
            },
            properties = DialogProperties(usePlatformDefaultWidth = false),
            modifier = Modifier.padding(24.dp).wrapContentHeight(),
            content = {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 6.dp,
                    modifier = Modifier.fillMaxWidth().wrapContentHeight()
                ) {
                    Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                        globalMessageToShow?.imageUrl?.let { url ->
                            AsyncImage(
                                model = url,
                                contentDescription = null,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .wrapContentHeight()
                                    .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)),
                                contentScale = ContentScale.FillWidth
                            )
                        }
                        Column(modifier = Modifier.padding(24.dp)) {
                            Text(
                                globalMessageToShow?.title ?: "",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                globalMessageToShow?.content ?: "",
                                fontSize = 16.sp,
                                lineHeight = 22.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                            )

                            if (!globalMessageToShow?.buttonText.isNullOrEmpty() && !globalMessageToShow?.buttonUrl.isNullOrEmpty()) {
                                Spacer(Modifier.height(24.dp))
                                Button(
                                    onClick = {
                                        try {
                                            context.startActivity(
                                                Intent(Intent.ACTION_VIEW, Uri.parse(globalMessageToShow?.buttonUrl))
                                            )
                                        } catch (_: Exception) {
                                            Toast.makeText(context, "Erro ao abrir link", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text(globalMessageToShow?.buttonText ?: "", fontWeight = FontWeight.Bold)
                                }
                            }

                            Spacer(Modifier.height(12.dp))
                            TextButton(
                                onClick = {
                                    globalMessageManager.markMessageAsSeen(globalMessageToShow?.id ?: "")
                                    globalMessageToShow = null
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) { Text(stringResource(R.string.understood), fontWeight = FontWeight.Bold) }
                        }
                    }
                }
            }
        )
    }

    fun openPdf(filePath: String?) {
        if (filePath.isNullOrBlank()) return
        val file = File(filePath)
        if (!file.exists()) return

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intentView = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
        }
        try {
            context.startActivity(Intent.createChooser(intentView, context.getString(R.string.open_pdf_with)))
        } catch (_: Exception) {
            storage.setAppLockEnabled(false)
            val account = com.google.android.gms.auth.api.signin.GoogleSignIn.getLastSignedInAccount(context)
            if (account != null) {
                scope.launch { backupManager.backupNow(account) }
            }
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
                    FileOutputStream(destFile).use { output -> input.copyTo(output) }
                }
                destFile.absolutePath
            } catch (_: Exception) {
                null
            }
        }
    }

    suspend fun processAndSaveRecibo(
        novo: ReciboPagamento, 
        uri: Uri,
        scope: kotlinx.coroutines.CoroutineScope,
        db: AppDatabase,
        gson: Gson,
        userName: String,
        refreshData: suspend () -> Unit,
        navigateTo: (Screen) -> Unit
    ) {
        scope.launch {
            var finalNovo = novo
            if (finalNovo.funcionario == context.getString(R.string.label_unknown) && userName.isNotEmpty()) {
                finalNovo = finalNovo.copy(funcionario = userName)
            }
            val path = savePdfPermanently(uri, "recibo_${finalNovo.periodo.replace("/", "_")}.pdf")
            val updated = finalNovo.copy(pdfFilePath = path)
            
            withContext(Dispatchers.IO) {
                db.reciboDao().insert(updated.toEntity(gson, path)!!)
                
                // Coleta automática e anônima para o ranking global (usando Salário Base)
                val baseSalaryToReport = updated.salarioBase.replace(".", "").replace(",", ".").toDoubleOrNull() ?: 0.0
                val firestoreManager = com.jack.meuholerite.utils.BackupManager(context)
                if (updated.cargo.isNotEmpty() && baseSalaryToReport > 0) {
                    firestoreManager.saveAnonymousSalaryStat(updated)
                }
                
                // Backup silencioso
                firestoreManager.backupData()
            }
            
            val account = com.google.android.gms.auth.api.signin.GoogleSignIn.getLastSignedInAccount(context)
            if (account != null) {
                backupManager.backupNow(account)
            }
            
            refreshData()
            if (updated.dataPagamento.isNotEmpty()) {
                showPaymentNotification(context, updated.periodo, updated.dataPagamento)
            }
            withContext(Dispatchers.Main) { navigateTo(Screen.Recibos) }
        }
    }

    val navigateTo = { screen: Screen ->
        val currentRoute = navController.currentBackStackEntry?.destination?.route
        val shouldShowToolsExitAd = currentRoute == Screen.Tools.route && screen != Screen.Tools

        fun performNavigation() {
            if (screen == Screen.Epays) hasLoadedEpays = true
            navController.navigate(screen.route) {
                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }

        if (shouldShowToolsExitAd) {
            scope.launch {
                val activity = context as? Activity
                if (activity != null && AdsDataStore.canShowIntervalAd(context)) {
                    RewardedInterstitialAdManager.showAd(activity) {
                        scope.launch {
                            AdsDataStore.incrementAdsShown(context)
                            AdsDataStore.markIntervalAdShown(context)
                            performNavigation()
                        }
                    }
                } else {
                    performNavigation()
                }
            }
        } else {
            performNavigation()
        }
    }

    LaunchedEffect(selectedEspelho) {
        if (selectedEspelho?.hasAbsences == true) {
            showAbsenceWarning = true
            showAbsenceNotification(context, selectedEspelho?.periodo ?: "", selectedEspelho?.diasFaltas?.size ?: 0)
        }
    }

    if (showOnboarding) {
        FirstLaunchIntroDialog(
            onFinish = {
                appPrefs.edit().putBoolean("onboarding_completed", true).apply()
                showOnboarding = false
                                val account = com.google.android.gms.auth.api.signin.GoogleSignIn.getLastSignedInAccount(context)
                if (account != null) {
                    scope.launch { backupManager.backupNow(account) }
                }
            },
            onImportPdf = {
                appPrefs.edit().putBoolean("onboarding_completed", true).apply()
                showOnboarding = false
                                val account = com.google.android.gms.auth.api.signin.GoogleSignIn.getLastSignedInAccount(context)
                if (account != null) {
                    scope.launch { backupManager.backupNow(account) }
                }
                navigateTo(Screen.Epays)
            },
            onOpenPrivacy = {
                context.startActivity(Intent(context, PrivacyPolicyActivity::class.java))
            }
        )
    }

    LaunchedEffect(activityIntent) {
        if (activityIntent?.action == Intent.ACTION_VIEW && activityIntent.type == "application/pdf") {
            activityIntent.data?.let { uri ->
                val pdfReader = PdfReader(context)
                val text = pdfReader.extractTextFromUri(uri)
                if (text != null) {
                    val textToAnalyze = text.uppercase()
                    val isInforme = textToAnalyze.contains("INFORME DE RENDIMENTOS") || (textToAnalyze.contains("FONTE PAGADORA") && textToAnalyze.contains("RENDIMENTOS TRIBUTÁVEIS"))
                    val isPonto = !isInforme && (text.contains("PONTO", true) || text.contains("ESPELHO", true) || text.contains("BATIDA", true))
                    val isRecibo = !isInforme && (text.contains("PAGAMENTO", true) || text.contains("DEMONSTRATIVO", true) || text.contains("HOLERITE", true) || text.contains("PROVENTOS", true))

                    if (isInforme) {
                        val novo = InformeParser().parse(text)
                        val path = savePdfPermanently(uri, "informe_${novo.anoCalendario}.pdf")
                        val updated = novo.copy(pdfFilePath = path)
                        db.informeDao().insert(updated.toEntity(path))
                        
                        scope.launch(Dispatchers.IO) {
                            firestoreManager.backupData()
                            val account = com.google.android.gms.auth.api.signin.GoogleSignIn.getLastSignedInAccount(context)
                            if (account != null) backupManager.backupNow(account)
                        }
                        
                        val intent = Intent(context, com.jack.meuholerite.InformesActivity::class.java)
                        context.startActivity(intent)
                    } else if (isPonto && !text.contains("DEMONSTRATIVO", true)) {
                        val novo = PontoParser().parse(text)
                        val path = savePdfPermanently(uri, "ponto_${novo.periodo.replace("/", "_")}.pdf")
                        val updated = novo.copy(pdfFilePath = path)
                        selectedEspelho = updated
                        db.espelhoDao().insert(updated.toEntity(gson, path))
                        
                        // Backup em segundo plano
                        scope.launch(Dispatchers.IO) {
                            firestoreManager.backupData()
                            val account = com.google.android.gms.auth.api.signin.GoogleSignIn.getLastSignedInAccount(context)
                            if (account != null) backupManager.backupNow(account)
                        }
                        
                        navController.navigate(Screen.Ponto.route)
                    } else if (isRecibo) {
                        var novo = ReciboParser().parse(text)

                        // Fallback para IA se o parser convencional falhar
                        if (novo.periodo == "Não identificado" || novo.valorLiquido == "0,00") {
                            safeShowAd {
                                scope.launch {
                                    val aiNovo = AiParser().parseRecibo(text)
                                    if (aiNovo != null) {
                                        // Update state with AI result
                                        selectedRecibo = aiNovo
                                    }
                                }
                            }
                        }

                        if (novo.funcionario == context.getString(R.string.label_unknown) && userName.isNotEmpty()) {
                            novo = novo.copy(funcionario = userName)
                        }
                        val path = savePdfPermanently(uri, "recibo_${novo.periodo.replace("/", "_")}.pdf")
                        val updated = novo.copy(pdfFilePath = path)
                        selectedRecibo = updated
                        db.reciboDao().insert(updated.toEntity(gson, path)!!)
                        
                        // Coleta automática e anônima para o ranking global (usando Salário Base)
                        scope.launch {
                            val baseSalaryToReport = updated.salarioBase.replace(".", "").replace(",", ".").toDoubleOrNull() ?: 0.0
                            if (updated.cargo.isNotEmpty() && baseSalaryToReport > 0) {
                                firestoreManager.saveAnonymousSalaryStat(updated)
                            }
                            
                            // Backup em segundo plano
                            firestoreManager.backupData()
                            val account = com.google.android.gms.auth.api.signin.GoogleSignIn.getLastSignedInAccount(context)
                            if (account != null) backupManager.backupNow(account)
                        }

                        navController.navigate(Screen.Recibos.route)
                        if (updated.dataPagamento.isNotEmpty()) {
                            showPaymentNotification(context, updated.periodo, updated.dataPagamento)
                        }
                    }
                }
            }
        }

        if (activityIntent?.getBooleanExtra("FROM_NOTIFICATION", false) == true) {
            val msgId = activityIntent.getStringExtra("MSG_ID")
            if (msgId != null) {
                scope.launch {
                    val savedMsg = globalMessageManager.getSavedMessage()
                    if (savedMsg != null && (savedMsg.id == msgId || msgId == "latest")) {
                        globalMessageToShow = savedMsg
                    }
                }
            }
        }

    }

    if (showLaborAiChat) {
        LaborAiChatDialog(
            db = db,
            onDismiss = { showLaborAiChat = false }
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {},
        topBar = {
            val navBackStackEntry by navController.currentBackStackEntryAsState()
            val currentRoute = navBackStackEntry?.destination?.route
            androidx.compose.animation.AnimatedVisibility(visible = currentRoute != Screen.Epays.route) {
                IosTopBar(
                    userName = userName,
                    jornada = selectedEspelho?.jornada,
                    isPrivacyActive = isPrivacyActive,
                    onPrivacyToggle = {
                        onPrivacyChange(!isPrivacyActive)
                    },
                    onRankingClick = {
                        context.startActivity(Intent(context, SalaryRankingActivity::class.java))
                    }
                ) {
                    context.startActivity(Intent(context, SettingsActivity::class.java))
                }
            }
        },
        bottomBar = {
            val navBackStackEntry by navController.currentBackStackEntryAsState()
            val currentRoute = navBackStackEntry?.destination?.route
            val items = listOf(Screen.Home, Screen.Epays, Screen.Recibos, Screen.Ponto)

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
                shadowElevation = 16.dp,
                tonalElevation = 6.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 0.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items.forEach { screen ->
                        val isSelected = currentRoute == screen.route
                        PremiumNavigationBarItem(
                            selected = isSelected,
                            onClick = { navigateTo(screen) },
                            icon = { isSel, color ->
                                when (screen) {
                                    Screen.Home -> AnimatedHomeIcon(isSel, color)
                                    Screen.Epays -> AnimatedGlobeIcon(isSel, color)
                                    Screen.Recibos -> AnimatedReceiptIcon(isSel, color)
                                    Screen.Ponto -> AnimatedClockIcon(isSel, color)
                                    else -> Icon(screen.icon, null, tint = color)
                                }
                            },
                            label = stringResource(screen.label),
                            accentColor = when(screen) {
                                Screen.Home -> MaterialTheme.colorScheme.primary
                                Screen.Epays -> Color(0xFF007AFF) // IosBlue
                                Screen.Recibos -> Color(0xFF34C759) // IosGreen
                                Screen.Ponto -> Color(0xFF5856D6) // IosIndigo
                                else -> MaterialTheme.colorScheme.primary
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = navBackStackEntry?.destination?.route

        Box(modifier = Modifier.padding(innerPadding)) {

            if (hasLoadedEpays) {
                val isEpaysVisible = currentRoute == Screen.Epays.route
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .alpha(if (isEpaysVisible) 1f else 0f)
                        .zIndex(if (isEpaysVisible) 1f else -1f)
                ) {
                    EpaysWebViewPage { uri, url ->
                        Toast.makeText(context, "PDF Detectado! Processando...", Toast.LENGTH_SHORT).show()
                        scope.launch {
                            val pdfReader = PdfReader(context)
                            val text = withContext(Dispatchers.IO) { pdfReader.extractTextFromUri(uri) }
                            if (text == null) {
                                Toast.makeText(context, "Falha ao extrair texto do PDF", Toast.LENGTH_SHORT).show()
                                return@launch
                            }
                            
                            val textToAnalyze = text.uppercase()
                            val isInforme = textToAnalyze.contains("INFORME DE RENDIMENTOS") || (textToAnalyze.contains("FONTE PAGADORA") && textToAnalyze.contains("RENDIMENTOS TRIBUTÁVEIS"))
                            val isEspelho = !isInforme && (textToAnalyze.contains("ESPELHO DE PONTO") || textToAnalyze.contains("CARTÃO DE PONTO") || textToAnalyze.contains("HORAS"))
                            val isRecibo = !isInforme && (textToAnalyze.contains("PAGAMENTO") || textToAnalyze.contains("DEMONSTRATIVO") || textToAnalyze.contains("HOLERITE") || 
                                           textToAnalyze.contains("CONTRACHEQUE") || textToAnalyze.contains("PROVENTOS") || textToAnalyze.contains("RECIBO") ||
                                           textToAnalyze.contains("LÍQUIDO") || textToAnalyze.contains("FGTS"))

                            if (!isRecibo && !isEspelho && !isInforme) {
                                Log.w("MainActivity", "Doc não reconhecido: ${textToAnalyze.take(200)}")
                                Toast.makeText(context, "Documento não reconhecido como Holerite, Ponto ou Informe", Toast.LENGTH_LONG).show()
                                return@launch
                            }

                            if (isInforme) {
                                val novo = InformeParser().parse(text)
                                val path = savePdfPermanently(uri, "informe_${novo.anoCalendario}.pdf")
                                withContext(Dispatchers.IO) {
                                    db.informeDao().insert(novo.copy(pdfFilePath = path).toEntity(path))
                                }
                                refreshData()
                                importSuccessData = Triple("INFORME", novo.anoCalendario, path ?: "")
                            } else if (isRecibo) {
                                var novo = ReciboParser().parse(text)
                                if (novo.periodo == "Não identificado" || novo.valorLiquido == "0,00" || novo.cargo.isEmpty()) {
                                    val activity = context as? Activity
                                    if (activity != null) {
                                        if (AdsDataStore.canShowIntervalAd(context)) {
                                            RewardedInterstitialAdManager.showAd(activity) {
                                                scope.launch {
                                                    AdsDataStore.incrementAdsShown(context)
                                                    AdsDataStore.markIntervalAdShown(context)
                                                    val aiNovo = AiParser().parseRecibo(text)
                                                    val finalNovo = aiNovo ?: novo
                                                    val path = savePdfPermanently(uri, "recibo_${finalNovo.periodo.replace("/", "_")}.pdf")
                                                    withContext(Dispatchers.IO) {
                                                        val toSave = finalNovo.copy(pdfFilePath = path)
                                                        db.reciboDao().insert(toSave.toEntity(gson, path)!!)
                                                    }
                                                    refreshData()
                                                    importSuccessData = Triple("RECIBO", finalNovo.periodo, path ?: "")
                                                }
                                            }
                                        } else {
                                            val aiNovo = AiParser().parseRecibo(text)
                                            val finalNovo = aiNovo ?: novo
                                            val path = savePdfPermanently(uri, "recibo_${finalNovo.periodo.replace("/", "_")}.pdf")
                                            withContext(Dispatchers.IO) {
                                                val toSave = finalNovo.copy(pdfFilePath = path)
                                                db.reciboDao().insert(toSave.toEntity(gson, path)!!)
                                            }
                                            refreshData()
                                            importSuccessData = Triple("RECIBO", finalNovo.periodo, path ?: "")
                                        }
                                    } else {
                                        val path = savePdfPermanently(uri, "recibo_${novo.periodo.replace("/", "_")}.pdf")
                                        withContext(Dispatchers.IO) {
                                            db.reciboDao().insert(novo.copy(pdfFilePath = path).toEntity(gson, path)!!)
                                        }
                                        refreshData()
                                        importSuccessData = Triple("RECIBO", novo.periodo, path ?: "")
                                    }
                                } else {
                                    val path = savePdfPermanently(uri, "recibo_${novo.periodo.replace("/", "_")}.pdf")
                                    withContext(Dispatchers.IO) {
                                        db.reciboDao().insert(novo.copy(pdfFilePath = path).toEntity(gson, path)!!)
                                    }
                                    refreshData()
                                    importSuccessData = Triple("RECIBO", novo.periodo, path ?: "")
                                }
                            } else if (isEspelho) {
                                val novo = PontoParser().parse(text)
                                val path = savePdfPermanently(uri, "ponto_${novo.periodo.replace("/", "_")}.pdf")
                                withContext(Dispatchers.IO) {
                                    db.espelhoDao().insert(novo.copy(pdfFilePath = path).toEntity(gson, path))
                                }
                                refreshData()
                                importSuccessData = Triple("PONTO", novo.periodo, path ?: "")
                            }
                            
                            // Backup em segundo plano
                            kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
                                firestoreManager.backupData()
                                val account = com.google.android.gms.auth.api.signin.GoogleSignIn.getLastSignedInAccount(context)
                                if (account != null) backupManager.backupNow(account)
                            }
                        }
                    }

                    importSuccessData?.let { (type, id, path) ->
                        FullscreenPdfViewerDialog(
                            type = type,
                            filePath = path,
                            onConfirm = {
                                importSuccessData = null
                                when (type) {
                                    "RECIBO" -> navigateTo(Screen.Recibos)
                                    "PONTO" -> navigateTo(Screen.Ponto)
                                    "INFORME" -> {
                                        val intent = Intent(context, com.jack.meuholerite.InformesActivity::class.java)
                                        context.startActivity(intent)
                                    }
                                }
                            },
                            onDismiss = { 
                                val wasPonto = type == "PONTO"
                                importSuccessData = null
                                if (wasPonto) showSignReminder = true
                            }
                        )
                    }

                    if (showSignReminder) {
                        AlertDialog(
                            onDismissRequest = { showSignReminder = false },
                            title = { Text("Assinar Ponto?", fontWeight = FontWeight.Bold) },
                            text = { Text("Se as informações do espelho de ponto estiverem corretas, lembre-se de realizar a assinatura agora mesmo no site do ePays.") },
                            confirmButton = {
                                Button(onClick = { showSignReminder = false }) {
                                    Text("Entendido")
                                }
                            },
                            shape = RoundedCornerShape(22.dp)
                        )
                    }
                }
            }

            NavHost(navController, startDestination = Screen.Home.route, modifier = Modifier.zIndex(0f)) {
                composable(Screen.Home.route) {
                    HomeScreen(
                        selectedRecibo = selectedRecibo,
                        selectedEspelho = selectedEspelho,
                        onGoToRecibo = { navigateTo(Screen.Recibos) },
                        onGoToPonto = { navigateTo(Screen.Ponto) },
                        onGoToTools = {
                            navigateTo(Screen.Tools)
                        },
                        onOpenSorteios = {
                            context.startActivity(Intent(context, SorteiosActivity::class.java))
                        },
                        onOpenPromocoesFeed = {
                            context.startActivity(Intent(context, PromocoesFeedActivity::class.java))
                        },
                        onRefresh = { scope.launch { refreshData() } },
                        db = db,
                        gson = gson,
                        navController = navController,
                        backupManager = backupManager,
                        safeShowAd = ::safeShowAd,
                        onOpenPdf = { openPdf(it) },
                        promoList = promoList,
                        promoNotifEnabled = promoNotifEnabled,
                        onPromoNotifToggle = {
                            promoNotifEnabled = it
                            appPrefs.edit().putBoolean("promo_notif_enabled", it).apply()
                            if (it) {
                                FirebaseMessaging.getInstance().subscribeToTopic("promocoes")
                            } else {
                                FirebaseMessaging.getInstance().unsubscribeFromTopic("promocoes")
                            }
                        }
                    )





                }
                composable(Screen.Epays.route) { Box(Modifier.fillMaxSize()) }
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
                        onRefresh = { scope.launch { refreshData() } }
                    )
                }
                composable(Screen.Ponto.route) {
                    TimesheetScreen(
                        espelho = selectedEspelho,
                        db = db,
                        gson = gson,
                        userName = selectedEspelho?.let { if (it.funcionario.isNotBlank() && it.funcionario != "Não encontrado") it.funcionario else userName } ?: userName,
                        userMatricula = selectedEspelho?.let { if (it.matricula.isNotBlank()) it.matricula else userMatricula } ?: userMatricula,
                        userCargo = selectedEspelho?.let { if (it.cargo.isNotBlank()) it.cargo else userCargo } ?: userCargo,
                        onEditProfile = {},
                        onSelect = { selectedEspelho = it },
                        onOpen = { openPdf(it) },
                        onRefresh = { scope.launch { refreshData() } }
                    )
                }
                composable(Screen.Tools.route) {
                    ToolsScreen(navController)
                }
                composable(Screen.Promocoes.route) {
                    PromocoesFeedScreen(
                        promocoes = promoList,
                        onBack = { navController.popBackStack() }
                    )
                }
            }
        }
    }

    if (showRemoveAdsDialog.value && !adsRemovedState) {
        RemoveAdsDialog(
            onDismiss = { showRemoveAdsDialog.value = false },
            onPurchase = {
                (context as? Activity)?.let { activity ->
                    billingManager.launchPurchaseFlow(activity)
                }
            }
        )
    }

}


@Composable
fun AbsenceWarningDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.understood)) } },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Warning, null, tint = Color(0xFFFF3B30))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.absences_detected), fontWeight = FontWeight.Bold)
            }
        },
        text = { Text(stringResource(R.string.warning_absences), lineHeight = 20.sp) },
        shape = RoundedCornerShape(22.dp)
    )
}

@Composable
private fun FirstLaunchIntroDialog(
    onFinish: () -> Unit,
    onImportPdf: () -> Unit,
    onOpenPrivacy: () -> Unit
) {
    Dialog(
        onDismissRequest = { },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        FirstLaunchIntroScreen(
            onFinish = onFinish,
            onImportPdf = onImportPdf,
            onOpenPrivacy = onOpenPrivacy
        )
    }
}

@Composable
private fun FirstLaunchIntroScreen(
    onFinish: () -> Unit,
    onImportPdf: () -> Unit,
    onOpenPrivacy: () -> Unit
) {
    val scroll = rememberScrollState()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .padding(horizontal = 18.dp)
                .padding(top = 18.dp, bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Spacer(Modifier.height(6.dp))

            IntroHeader(onFinish = onFinish)

            IntroHeroCard(
                title = "Seu holerite e seu ponto,\ncom clareza e controle.",
                subtitle = "Importe PDFs e tenha um painel pronto com líquido, descontos, banco de horas e histórico."
            )

            IntroCompanySupportCard()

            IntroFeatureGrid()
            IntroBackupCard()
            IntroPrivacyCard(onOpenPrivacy = onOpenPrivacy)

            IntroActionArea(
                onFinish = onFinish,
                onImportPdf = onImportPdf
            )

            Spacer(Modifier.height(10.dp))

            Text(
                text = "Você pode rever tudo isso em Ajustes.",
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp)
            )
        }
    }
}

@Composable
private fun IntroHeader(onFinish: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.AutoMirrored.Outlined.ReceiptLong,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Meu Holerite",
                fontSize = 18.sp,
                fontWeight = FontWeight.Black
            )
            Text(
                text = "Configuração inicial",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
            )
        }

        TextButton(
            onClick = onFinish,
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Text("Pular", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun IntroHeroCard(title: String, subtitle: String) {
    val shape = RoundedCornerShape(24.dp)

    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 3.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = title,
                fontSize = 26.sp,
                lineHeight = 30.sp,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = subtitle,
                fontSize = 14.5.sp,
                lineHeight = 20.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.70f)
            )

            Spacer(Modifier.height(2.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                IntroPill(icon = Icons.Outlined.Shield, label = "Privado")
                IntroPill(icon = Icons.Outlined.History, label = "Histórico")
                IntroPill(icon = Icons.Outlined.AutoGraph, label = "Insights")
            }
        }
    }
}

@Composable
private fun IntroPill(icon: ImageVector, label: String) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun IntroFeatureGrid() {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "O que você ganha",
            fontSize = 13.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.primary,
            letterSpacing = 0.6.sp
        )

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            IntroFeatureCard(
                icon = Icons.Outlined.AccountBalanceWallet,
                title = "Resumo do mês",
                desc = "Líquido, proventos e descontos\nbem organizados.",
                modifier = Modifier.weight(1f)
            )
            IntroFeatureCard(
                icon = Icons.Outlined.Schedule,
                title = "Ponto & B.H.",
                desc = "Banco de horas, jornada\n+ alertas de falta.",
                modifier = Modifier.weight(1f)
            )
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            IntroFeatureCard(
                icon = Icons.Outlined.CloudDownload,
                title = "Importação fácil",
                desc = "Abra o PDF e importe\nem poucos toques.",
                modifier = Modifier.weight(1f)
            )
            IntroFeatureCard(
                icon = Icons.Outlined.Lock,
                title = "Proteção",
                desc = "Bloqueio por PIN/biometria\n(se você quiser).",
                modifier = Modifier.weight(1f)
            )
        }

        IntroRankingTeaserCard(
            onClick = { context.startActivity(Intent(context, SalaryRankingActivity::class.java)) }
        )
    }
}

@Composable
private fun IntroRankingTeaserCard(onClick: () -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    Surface(
        onClick = onClick,
        shape = shape,
        color = Color.Transparent,
        shadowElevation = 6.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .background(
                    brush = Brush.linearGradient(
                        listOf(
                            Color(0xFF12253B),
                            Color(0xFF244A72),
                            Color(0xFFF0D18B)
                        )
                    )
                )
                .padding(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = Color.White.copy(alpha = 0.12f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Outlined.EmojiEvents, null, tint = Color(0xFFFFD166), modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "DESCUBRA O TOPO DA BASE",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFFFFD166),
                            letterSpacing = 0.8.sp
                        )
                    }
                }

                Text(
                    text = "Quanto será que o maior cargo da sua base está pagando hoje?",
                    fontSize = 20.sp,
                    lineHeight = 24.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White
                )

                Text(
                    text = "Abra o card do ranking, troque cargos no dropdown e veja o maior salário reportado, faixa real e empresas que mais aparecem.",
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    color = Color.White.copy(alpha = 0.82f)
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IntroDarkPill(Icons.Outlined.AutoGraph, "Maior salário")
                    IntroDarkPill(Icons.Outlined.Groups, "Cargos reais")
                    IntroDarkPill(Icons.Outlined.Business, "Empresas")
                }

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White.copy(alpha = 0.12f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                "Toque para explorar o ranking",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                "Veja quem está puxando a faixa para cima",
                                fontSize = 11.sp,
                                color = Color.White.copy(alpha = 0.72f)
                            )
                        }
                        Icon(
                            Icons.AutoMirrored.Outlined.TrendingUp,
                            null,
                            tint = Color(0xFFFFD166),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun IntroDarkPill(icon: ImageVector, label: String) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = Color.White.copy(alpha = 0.10f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
    }
}

@Composable
private fun IntroCompanySupportCard() {
    val shape = RoundedCornerShape(22.dp)
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.08f),
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Business, null, tint = MaterialTheme.colorScheme.secondary)
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "Empresas Suportadas",
                    fontWeight = FontWeight.Black,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
            Text(
                text = "Suporte oficial para holerites e espelhos de ponto da Marfrig / MBRF.",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Para novas empresas, você pode enviar sugestões nos Ajustes do app para análise e inclusão futura.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                lineHeight = 18.sp
            )
        }
    }
}

@Composable
private fun IntroFeatureCard(
    icon: ImageVector,
    title: String,
    desc: String,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(22.dp)

    Surface(
        modifier = modifier,
        shape = shape,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            }

            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1
            )

            Text(
                text = desc,
                fontSize = 12.5.sp,
                lineHeight = 18.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f)
            )
        }
    }
}

@Composable
private fun IntroBackupCard() {
    val shape = RoundedCornerShape(22.dp)

    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.05f),
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.CloudSync, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "Backup Inteligente",
                    fontWeight = FontWeight.Black,
                    fontSize = 14.sp
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    Icon(Icons.Outlined.Folder, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp).padding(top = 2.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = buildAnnotatedString {
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("Google Drive: ") }
                            append("Armazena seus arquivos PDF originais com segurança.")
                        },
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                    )
                }
                Row(verticalAlignment = Alignment.Top) {
                    Icon(Icons.Outlined.CloudDone, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp).padding(top = 2.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = buildAnnotatedString {
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("Nuvem (Firebase): ") }
                            append("Sincroniza seus cálculos, configurações e banco de horas.")
                        },
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                    )
                }
            }
            
            Text(
                text = "Ao entrar com sua conta Google, tudo é restaurado automaticamente.",
                fontSize = 11.sp,
                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun IntroPrivacyCard(onOpenPrivacy: () -> Unit) {
    val shape = RoundedCornerShape(22.dp)

    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.PrivacyTip, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "Privacidade",
                    fontWeight = FontWeight.Black,
                    fontSize = 14.sp
                )
            }

            Text(
                text = buildAnnotatedString {
                    append("Seus dados ficam sob seu controle. ")
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("Backup é opcional") }
                    append(" e pode ser usado apenas se você quiser sincronizar.")
                },
                fontSize = 12.8.sp,
                lineHeight = 18.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f)
            )

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = onOpenPrivacy,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Outlined.Description, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Ver política", fontWeight = FontWeight.Bold)
                }

                FilledTonalButton(
                    onClick = onOpenPrivacy,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Outlined.Info, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Entendi", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun IntroActionArea(
    onFinish: () -> Unit,
    onImportPdf: () -> Unit
) {
    val shape = RoundedCornerShape(22.dp)

    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Pronto para começar?",
                fontSize = 15.sp,
                fontWeight = FontWeight.Black
            )

            Text(
                text = "Você pode importar um PDF agora ou entrar e fazer isso depois.",
                fontSize = 12.8.sp,
                lineHeight = 18.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f)
            )

            Button(
                onClick = onImportPdf,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Começar agora", fontWeight = FontWeight.Black)
            }

            FilledTonalButton(
                onClick = onImportPdf,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Outlined.FileOpen, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Text("Importar PDF agora", fontWeight = FontWeight.Bold)
            }

            TextButton(
                onClick = onFinish,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Configurar depois", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    selectedRecibo: ReciboPagamento?,
    selectedEspelho: EspelhoPonto?,
    onGoToRecibo: () -> Unit,
    onGoToPonto: () -> Unit,
    onGoToTools: () -> Unit,
    onOpenSorteios: () -> Unit,
    onOpenPromocoesFeed: () -> Unit,
    onRefresh: () -> Unit,
    db: AppDatabase,
    gson: Gson,
    navController: androidx.navigation.NavController,
    backupManager: GoogleDriveBackupManager,
    safeShowAd: (() -> Unit) -> Unit,
    onOpenPdf: (String?) -> Unit,
    promoList: List<com.jack.meuholerite.model.Promocao> = emptyList(),
    promoNotifEnabled: Boolean = true,
    onPromoNotifToggle: (Boolean) -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    var isRefreshing by remember { mutableStateOf(false) }
    var showSalaryGraph by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val adsRemovedState by com.jack.meuholerite.ads.AdsDataStore.isAdsRemovedFlow(context).collectAsState(initial = false)
    val aiParser = remember { AiParser() }
    var aiInsight by remember { mutableStateOf<String?>(null) }
    var isGeneratingAi by remember { mutableStateOf(false) }
    var aiPrediction by remember { mutableStateOf<String?>(null) }
    var isGeneratingPrediction by remember { mutableStateOf(false) }
    var showAiAssistantModal by remember { mutableStateOf(false) }
    var shouldShowAdAfterAiModal by remember { mutableStateOf(false) }

    val expenses by db.financeExpenseDao().getAllFlow().collectAsState(initial = emptyList())
    val debts by db.financeDebtDao().getAllFlow().collectAsState(initial = emptyList())
    
    val totalExpenses = remember(expenses) { expenses.sumOf { it.value } }
    val totalDebtInstallments = remember(debts) { debts.sumOf { it.monthlyValue } }
    val totalDeductionsFinance = totalExpenses + totalDebtInstallments

    val netSalary = remember(selectedRecibo) { selectedRecibo?.valorLiquido?.toMoneyDoubleOrZero() ?: 0.0 }
    val proventosVal = remember(selectedRecibo) { selectedRecibo?.totalProventos?.toMoneyDoubleOrZero() ?: 0.0 }
    val descontosVal = remember(selectedRecibo) { selectedRecibo?.totalDescontos?.toMoneyDoubleOrZero() ?: 0.0 }
    val remaining = netSalary - totalDeductionsFinance

    val retentionRatio = remember(proventosVal, descontosVal) {
        val base = if (proventosVal <= 0.0) 1.0 else proventosVal
        (descontosVal / base).coerceIn(0.0, 1.0).toFloat()
    }
    val chipsMotion by rememberEntranceMotion(40)
    val promoMotion by rememberEntranceMotion(80)
    val salaryMotion by rememberEntranceMotion(200)
    val financeMotion by rememberEntranceMotion(280)
    val jornadaMotion by rememberEntranceMotion(360)
    val benefitsMotion by rememberEntranceMotion(520)
    val marketMotion by rememberEntranceMotion(600)
    val summaryMotion by rememberEntranceMotion(680)
    val marketDataManager = remember { BackupManager(context) }
    var topRankingCard by remember { mutableStateOf<SalaryRanking?>(null) }
    var marketLoading by remember { mutableStateOf(false) }

    // Cálculo determinístico do próximo pagamento (5º dia útil do mês seguinte)
    val proximoPagamento = remember { com.jack.meuholerite.utils.calcularProximoPagamento() }

    val worked = remember(selectedEspelho) {
        selectedEspelho?.resumoItens?.find { it.label == "label_worked_hours" }?.value ?: "0:00"
    }
    val extra = remember(selectedEspelho) {
        selectedEspelho?.resumoItens?.find { it.label.contains("extra", true) }?.value ?: "0:00"
    }
    val night = remember(selectedEspelho) {
        selectedEspelho?.resumoItens?.find { it.label.contains("night", true) }?.value ?: "0:00"
    }
    val extraItems = remember(selectedEspelho) {
        selectedEspelho?.resumoItens?.filter { it.label.contains("extra", true) || it.label.contains("adic", true) } ?: emptyList()
    }
    val nightItems = remember(selectedEspelho) {
        selectedEspelho?.resumoItens?.filter { it.label.contains("noturno", true) || it.label.contains("night", true) } ?: emptyList()
    }
    val hasAbsences = selectedEspelho?.hasAbsences == true
    val absCount = selectedEspelho?.diasFaltas?.size ?: 0

    val smartAlerts = remember(
        hasAbsences,
        absCount,
        totalDeductionsFinance,
        netSalary,
        retentionRatio,
        remaining,
        proximoPagamento.diasRestantes
    ) {
        buildList {
            if (hasAbsences) {
                add(
                    SmartAlert(
                        title = "Faltas detectadas",
                        message = "$absCount registro(s) podem impactar o próximo fechamento do ponto.",
                        color = Color(0xFFFF3B30),
                        icon = Icons.Filled.Warning
                    )
                )
            }
            if (netSalary > 0.0 && totalDeductionsFinance >= netSalary * 0.30) {
                add(
                    SmartAlert(
                        title = "Salário comprometido",
                        message = "Seus gastos fixos já consomem ${((totalDeductionsFinance / netSalary) * 100).toInt()}% do líquido.",
                        color = Color(0xFFFF9500),
                        icon = Icons.Outlined.AccountBalanceWallet
                    )
                )
            }
            if (retentionRatio >= 0.35f) {
                add(
                    SmartAlert(
                        title = "Descontos acima do normal",
                        message = "Os descontos deste holerite estão em ${(retentionRatio * 100).toInt()}% do bruto.",
                        color = Color(0xFF5856D6),
                        icon = Icons.AutoMirrored.Outlined.TrendingDown
                    )
                )
            }
            if (proximoPagamento.diasRestantes in 0..3) {
                add(
                    SmartAlert(
                        title = "Pagamento próximo",
                        message = "Seu próximo pagamento está previsto para ${proximoPagamento.dataFormatada}.",
                        color = Color(0xFF34C759),
                        icon = Icons.Outlined.DateRange
                    )
                )
            }
            if (remaining < 0) {
                add(
                    SmartAlert(
                        title = "Saldo projetado negativo",
                        message = "Pelas contas atuais, faltariam R$ ${kotlin.math.abs(remaining).formatBrMoney()} para fechar o mês.",
                        color = Color(0xFFFF3B30),
                        icon = Icons.Outlined.ErrorOutline
                    )
                )
            }
        }.take(3)
    }
    val monthlySummary = remember(
        proventosVal,
        netSalary,
        descontosVal,
        totalExpenses,
        totalDebtInstallments,
        remaining
    ) {
        MonthlySummary(
            gross = proventosVal,
            net = netSalary,
            discounts = descontosVal,
            expenses = totalExpenses,
            debts = totalDebtInstallments,
            remaining = remaining
        )
    }

    LaunchedEffect(Unit) {
        marketLoading = true
        marketDataManager.getTopSalaries()
            .onSuccess { rankingList ->
                topRankingCard = rankingList.maxByOrNull { it.maxReportedSalary }
            }
            .onFailure {
                topRankingCard = null
            }
        marketLoading = false
    }

    fun generateInsight() {
        if (selectedRecibo == null && selectedEspelho == null) return
        shouldShowAdAfterAiModal = true
        scope.launch {
            isGeneratingAi = true
            val contextData = """
                Recibo: ${selectedRecibo?.periodo ?: ""} - Líquido: ${selectedRecibo?.valorLiquido ?: ""}
                Proventos: ${selectedRecibo?.totalProventos ?: ""} - Descontos: ${selectedRecibo?.totalDescontos ?: ""}
                Ponto: ${selectedEspelho?.periodo ?: ""} - Saldo BH ATUAL: ${selectedEspelho?.saldoFinalBH ?: ""}
                Faltas: $absCount
            """.trimIndent()
            aiInsight = aiParser.getAiAnalysis(contextData)
            isGeneratingAi = false
        }
    }

    fun generatePaymentPrediction() {
        if (selectedRecibo == null && selectedEspelho == null) return
        shouldShowAdAfterAiModal = true
        scope.launch {
            isGeneratingPrediction = true

            // Buscar histórico para comparação (últimos 2 registros de cada)
            val historicoRecibos = db.reciboDao().getAll().take(3) // Atual + 2 anteriores
            val historicoEspelhos = db.espelhoDao().getAll().take(2) // 2 anteriores

            val proximoPgto = com.jack.meuholerite.utils.calcularProximoPagamento()
            val dataCalculada = proximoPgto.dataFormatada
            val diasRestantes = proximoPgto.diasRestantes
            val mesReferencia = proximoPgto.mesReferencia
            val aviso = when {
                diasRestantes < 0 -> "O pagamento já ocorreu (há ${-diasRestantes} dias)."
                diasRestantes == 0 -> "⚡ O pagamento é HOJE!"
                diasRestantes == 1 -> "Falta 1 dia para o pagamento."
                else -> "Faltam $diasRestantes dias para o pagamento."
            }

            val currentDate = java.text.SimpleDateFormat(
                "dd/MM/yyyy", java.util.Locale("pt", "BR")
            ).format(java.util.Date())

            // Formatar histórico para o prompt
            val recibosCtx = historicoRecibos.joinToString("\n") { r ->
                "  • ${r.periodo}: Líquido R$ ${r.valorLiquido}, Base R$ ${r.salarioBase}, Total Proventos R$ ${r.totalProventos}"
            }
            val espelhosCtx = historicoEspelhos.joinToString("\n") { e ->
                "  • ${e.periodo}: Saldo BH ${e.saldoFinalBH}"
            }

            val contextData = """
                HOJE: $currentDate
                
                ════ DADOS REAIS DO PORTAL EPAYS (ANCORAGEM) ════
                ⚠️ Use estes valores como base absoluta para a previsão.
                
                • SALÁRIO BASE: R$ 3.418,57
                • DESCONTO FIXO CRÍTICO: R$ 1.225,42 (Empréstimo Consignado) ← VOCÊ DEVE DESCONTAR ISSO!
                • OUTROS DESCONTOS MÉDIOS (INSS, Sindicato, Farmácia): ~R$ 800,00
                
                ════ ESPELHO DE PONTO ATUAL (ABRIL/2026) ════
                • Adicional Noturno 30%: 132:21 (Transforme em 132,35 horas para o cálculo)
                    • Hora Extra 100%: 08:09 (Transforme em 8,15 horas)
                    • Hora Extra 50%: 03:40 (Transforme em 3,66 horas)
                    • Faltas: $absCount dias
                    
                    ════ REGRAS DE CÁLCULO PARA A IA (ESTRITO) ════
                    1. VALOR DA HORA: R$ 3.418,57 / 220 = R$ 15,54 por hora.
                    2. ADICIONAL NOTURNO: (R$ 15,54 * 0,30) * 132,35 horas = R$ 617,00 (Aprox).
                    3. HORAS EXTRAS: 
                       - 100%: (R$ 15,54 * 2) * 8,15 = R$ 253,00 (Aprox).
                       - 50%: (R$ 15,54 * 1,5) * 3,66 = R$ 85,00 (Aprox).
                    4. SOMA DE TUDO: Salário Base + Noturno + Extras - Faltas - EMPRÉSTIMO ($1225) - Impostos (~$800).
                    
                    ════ FORMATO DE RESPOSTA ════
                    📅 Pagamento: $dataCalculada
                    💰 Valor Líquido Estimado: R$ [resultado na faixa de 2.400 a 2.700]
                    
                    📊 MEMÓRIA DE CÁLCULO:
                    • (+) Salário Base: R$ 3.418,57
                    • (+) Adicional Noturno: R$ ...
                    • (+) Horas Extras: R$ ...
                    • (-) Empréstimo Consignado: R$ 1.225,42
                    • (-) Impostos/Outros: R$ ...
                    • (=) TOTAL LÍQUIDO PREVISTO: R$ ...
                    
                    💡 NOTA: Explique que o valor é realista comparado aos R$ 2.504,02 do mês passado.
                    ⚠️ TRAVA: Se der mais de R$ 4.000,00, você errou. Refaça.
            """.trimIndent()

            aiPrediction = aiParser.getAiAnalysis("PREVISÃO COMPARATIVA DE PAGAMENTO:\n$contextData")
            isGeneratingPrediction = false
        }
    }

    val displayAiText = when {
        aiPrediction != null -> aiPrediction!!
        aiInsight != null -> aiInsight!!
        isGeneratingAi -> "Analisando seus dados..."
        isGeneratingPrediction -> "Calculando previsão de pagamento..."
        else -> "Toque para gerar uma análise inteligente dos seus rendimentos e jornada."
    }

    fun dismissAiAssistantModal() {
        showAiAssistantModal = false
        aiInsight = null
        aiPrediction = null
        isGeneratingAi = false
        isGeneratingPrediction = false

        if (shouldShowAdAfterAiModal) {
            shouldShowAdAfterAiModal = false
            safeShowAd { }
        }
    }

    if (showSalaryGraph) SalaryGraphDialog(db, gson) { showSalaryGraph = false }
    if (showAiAssistantModal) {
        AiAssistantDialog(
            text = displayAiText,
            isGeneratingAi = isGeneratingAi,
            isGeneratingPrediction = isGeneratingPrediction,
            canRefreshInsight = aiInsight != null,
            onDismiss = { dismissAiAssistantModal() },
            onRefreshInsight = { generateInsight() },
            onGenerateInsight = { generateInsight() },
            onGeneratePrediction = { generatePaymentPrediction() }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                scope.launch {
                    isRefreshing = true
                    onRefresh()
                    safeShowAd { }
                    delay(900)
                    isRefreshing = false
                }
            },
            modifier = Modifier.fillMaxSize()
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(bottom = 110.dp)
            ) {
                item {
                    Spacer(modifier = Modifier.height(10.dp))
                }

                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .entranceMotion(chipsMotion, offsetY = 28f, scaleStart = 0.98f)
                    ) {
                        Text(
                            text = (selectedRecibo?.periodo ?: stringResource(R.string.welcome_user)).uppercase(Locale.getDefault()),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 1.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (hasAbsences) {
                                StatusChip(
                                    icon = Icons.Filled.Warning,
                                    label = "FALTAS",
                                    value = absCount.toString(),
                                    color = Color(0xFFFF3B30),
                                    onClick = { onGoToPonto() }
                                )
                            }
                            val diasLabel = when {
                                proximoPagamento.diasRestantes < 0 -> "Pago"
                                proximoPagamento.diasRestantes == 0 -> "Hoje!"
                                else -> "Em ${proximoPagamento.diasRestantes}d"
                            }
                            val pgtoColor = when {
                                proximoPagamento.diasRestantes <= 0 -> Color(0xFF34C759)
                                proximoPagamento.diasRestantes <= 7 -> Color(0xFFFF9500)
                                else -> Color(0xFF007AFF)
                            }
                            StatusChip(
                                icon = Icons.Outlined.DateRange,
                                label = "PGTO ${proximoPagamento.dataFormatada.take(5)}",
                                value = diasLabel,
                                color = pgtoColor,
                                onClick = { }
                            )
                            StatusChip(
                                icon = Icons.Outlined.AccountBalanceWallet,
                                label = "GASTOS",
                                value = "R$ ${totalDeductionsFinance.formatBrMoney()}",
                                color = Color(0xFF5856D6),
                                onClick = {
                                    safeShowAd {
                                        context.startActivity(Intent(context, FinanceActivity::class.java))
                                    }
                                }
                            )
                        }
                    }
                }

                if (promoList.isNotEmpty()) {
                    item {
                        PromocoesMiniCard(
                            promocoes = promoList,
                            notifEnabled = promoNotifEnabled,
                            onNotifToggle = onPromoNotifToggle,
                            onOpenAllPromos = onOpenPromocoesFeed,
                            modifier = Modifier
                                .padding(horizontal = 16.dp)
                                .entranceMotion(promoMotion)
                        )
                    }
                }

                item {
                    Surface(
                        onClick = onOpenSorteios,
                        shape = RoundedCornerShape(22.dp),
                        color = Color.Transparent,
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .fillMaxWidth()
                            .entranceMotion(promoMotion)
                    ) {
                        Row(
                            modifier = Modifier
                                .background(
                                    Brush.linearGradient(
                                        listOf(
                                            Color(0xFF1D2A3A),
                                            Color(0xFF2F5D7C),
                                            Color(0xFF3F7F59)
                                        )
                                    )
                                )
                                .padding(horizontal = 18.dp, vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = Color.White.copy(alpha = 0.14f)
                            ) {
                                Icon(
                                    Icons.Outlined.EmojiEvents,
                                    contentDescription = null,
                                    tint = Color(0xFFFFD166),
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.raffles_button).uppercase(Locale.getDefault()),
                                    color = Color.White.copy(alpha = 0.82f),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 1.2.sp
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "Acompanhe prêmios, tarefas, regulamento e ganhadores anteriores.",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    lineHeight = 19.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Icon(
                                Icons.Filled.ChevronRight,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                item {
                    Column(
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .entranceMotion(salaryMotion)
                    ) {
                        val heroTransition = rememberInfiniteTransition(label = "salary_hero")
                        val heroGlow by heroTransition.animateFloat(
                            initialValue = 0.10f,
                            targetValue = 0.22f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(durationMillis = 2600, easing = EaseInOutSine),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "salary_hero_glow"
                        )
                        val heroFloat by heroTransition.animateFloat(
                            initialValue = 0f,
                            targetValue = -6f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(durationMillis = 3200, easing = EaseInOutSine),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "salary_hero_float"
                        )
                        Surface(
                            onClick = { showSalaryGraph = true },
                            shape = RoundedCornerShape(22.dp),
                            color = Color.Transparent,
                            tonalElevation = 2.dp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .offset { IntOffset(0, heroFloat.roundToInt()) }
                        ) {
                            Column(
                                modifier = Modifier
                                    .background(
                                        Brush.linearGradient(
                                            listOf(
                                                MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.98f),
                                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = heroGlow),
                                                MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.96f)
                                            )
                                        )
                                    )
                                    .padding(16.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.net_pay_label).uppercase(Locale.getDefault()),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    letterSpacing = 1.1.sp
                                )
                                Spacer(Modifier.height(6.dp))
                                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    PrivacyValueText(
                                        value = if (selectedRecibo != null) "R$ ${selectedRecibo.valorLiquido}" else "R$ 0,00",
                                        fontSize = 36.sp,
                                        fontWeight = FontWeight.Black,
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (selectedRecibo?.pdfFilePath != null) {
                                        IconButton(onClick = { onOpenPdf(selectedRecibo.pdfFilePath) }) {
                                            Icon(Icons.Outlined.PictureAsPdf, "Ver PDF Holerite", tint = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                }

                                Spacer(Modifier.height(14.dp))

                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    MetricMini(
                                        title = stringResource(R.string.earnings).uppercase(Locale.getDefault()),
                                        value = "R$ ${selectedRecibo?.totalProventos ?: "0,00"}",
                                        valueColor = Color(0xFF34C759),
                                        modifier = Modifier.weight(1f)
                                    )
                                    MetricMini(
                                        title = stringResource(R.string.deductions).uppercase(Locale.getDefault()),
                                        value = "R$ ${selectedRecibo?.totalDescontos ?: "0,00"}",
                                        valueColor = Color(0xFFFF3B30),
                                        modifier = Modifier.weight(1f)
                                    )
                                }

                                Spacer(Modifier.height(14.dp))

                                LinearProgressIndicator(
                                    progress = { retentionRatio },
                                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                                    color = Color(0xFFFF3B30),
                                    trackColor = Color(0xFF34C759),
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Retenção: ${(retentionRatio * 100).toInt()}%", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("Líquido: ${((1 - retentionRatio) * 100).toInt()}%", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }

                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = "Toque para ver evolução salarial",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.align(Alignment.End)
                                )
                            }
                        }
                    }
                }

                if (selectedRecibo != null || topRankingCard != null || marketLoading) {
                    item {
                        Column(
                            modifier = Modifier
                                .padding(horizontal = 16.dp)
                                .entranceMotion(marketMotion)
                        ) {
                            SectionHeader("Ranking de salários")
                            SalaryRankingHomeCard(
                                ranking = topRankingCard,
                                isLoading = marketLoading,
                                onClick = {
                                    context.startActivity(Intent(context, SalaryRankingActivity::class.java))
                                }
                            )
                        }
                    }
                }

                item {
                    Column(
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .entranceMotion(financeMotion),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        IosWidgetFinanceHighlightCard(
                            remaining = remaining,
                            totalExpenses = totalExpenses,
                            totalDebts = totalDebtInstallments,
                            onClick = {
                                context.startActivity(Intent(context, FinanceActivity::class.java))
                            }
                        )

                        if (!adsRemovedState) {
                            com.jack.meuholerite.ui.NativeInlineAd(
                                adUnitId = "ca-app-pub-7931782163570852/1526069738",
                                size = com.jack.meuholerite.ui.NativeAdSize.Regular
                            )
                        }

                        QuickActionsRow(
                            onRecibos = onGoToRecibo,
                            onPonto = onGoToPonto,
                            onTools = onGoToTools,
                            onFinance = {
                                context.startActivity(Intent(context, FinanceActivity::class.java))
                            }
                        )

                        SectionHeader("Resumo mensal automático")
                        MonthlySummaryCard(summary = monthlySummary)
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(40.dp))
                }
            }
        }

        FloatingActionButton(
            onClick = { showAiAssistantModal = true },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
                .windowInsetsPadding(WindowInsets.navigationBars)
        ) {
            Icon(Icons.Filled.AutoAwesome, contentDescription = "Abrir assistente IA")
        }
    }
}

@Composable
private fun AiAssistantDialog(
    text: String,
    isGeneratingAi: Boolean,
    isGeneratingPrediction: Boolean,
    canRefreshInsight: Boolean,
    onDismiss: () -> Unit,
    onRefreshInsight: () -> Unit,
    onGenerateInsight: () -> Unit,
    onGeneratePrediction: () -> Unit
) {
    val scrollState = rememberScrollState()
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(26.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight()
        ) {
            Column(
                modifier = Modifier
                    .padding(18.dp)
                    .verticalScroll(scrollState)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("ASSISTENTE IA", fontSize = 13.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.weight(1f))
                    if (canRefreshInsight) {
                        IconButton(onClick = onRefreshInsight, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Outlined.Refresh, null, modifier = Modifier.size(18.dp))
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                AnimatedContent(targetState = text, label = "ai_modal_text") { currentText ->
                    Text(
                        text = currentText,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (isGeneratingAi || isGeneratingPrediction) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp)
                            .height(3.dp)
                    )
                } else {
                    Spacer(Modifier.height(14.dp))

                    Button(
                        onClick = onGenerateInsight,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(10.dp)
                    ) {
                        Icon(Icons.Filled.AutoAwesome, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Gerar Análise", fontWeight = FontWeight.Bold)
                    }

                    Spacer(Modifier.height(8.dp))

                    OutlinedButton(
                        onClick = onGeneratePrediction,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(10.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                    ) {
                        Icon(Icons.Outlined.Analytics, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Previsão de Próximo Pagamento", fontWeight = FontWeight.Bold)
                    }

                    Spacer(Modifier.height(8.dp))

                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Fechar", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}


private fun formatTimestampRelativo(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    val min = diff / 60000
    val h = diff / 3600000
    val d = diff / 86400000
    return when {
        min < 1 -> "agora"
        min < 60 -> "${min}m"
        h < 24 -> "${h}h"
        d < 7 -> "${d}d"
        else -> java.text.SimpleDateFormat("dd/MM", java.util.Locale("pt", "BR")).format(java.util.Date(timestamp))
    }
}

private fun formatPromoTempoRestante(expiraEm: Long): String? {
    if (expiraEm <= 0L) return null
    val remaining = expiraEm - System.currentTimeMillis()
    if (remaining <= 0L) return "Encerrando"

    val totalMinutes = remaining / 60000
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60

    return when {
        hours >= 24 -> {
            val days = hours / 24
            if (days == 1L) "Valida por mais 1 dia" else "Valida por mais $days dias"
        }
        hours > 0 -> {
            if (minutes > 0) "Valida por mais ${hours}h ${minutes}m" else "Valida por mais ${hours}h"
        }
        else -> "Valida por mais ${minutes.coerceAtLeast(1)} min"
    }
}

@Composable
private fun PromocaoDetailDialog(
    promo: com.jack.meuholerite.model.Promocao,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var curtidas by remember { mutableStateOf(promo.curtidas) }
    var liked by remember { mutableStateOf(false) }
    val hasDiscount = promo.precoAntes > 0 && promo.precoDepois > 0
    val discountPct = if (hasDiscount) {
        ((1.0 - promo.precoDepois / promo.precoAntes) * 100).toInt()
    } else 0
    val tempoRestantePromo = remember(promo.expiraEm) { formatPromoTempoRestante(promo.expiraEm) }
    var comentarios by remember { mutableStateOf<List<com.jack.meuholerite.model.Comentario>>(emptyList()) }
    var novoComentario by remember { mutableStateOf("") }
    var enviando by remember { mutableStateOf(false) }
    var erroComentario by remember { mutableStateOf<String?>(null) }
    val scrollState = rememberScrollState()

    LaunchedEffect(promo.id) {
        try {
            val snapshot = FirebaseFirestore.getInstance()
                .collection("promocoes").document(promo.id)
                .collection("comentarios")
                .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .get()
                .await()
            comentarios = snapshot.documents.mapNotNull { doc ->
                val data = doc.data ?: return@mapNotNull null
                com.jack.meuholerite.model.Comentario(
                    id = doc.id,
                    autor = data["autor"] as? String ?: "",
                    texto = data["texto"] as? String ?: "",
                    timestamp = (data["timestamp"] as? Number)?.toLong() ?: 0L
                )
            }
        } catch (_: Exception) {}
    }

    fun enviarComentario() {
        val texto = novoComentario.trim()
        if (texto.isBlank()) return
        if (promo.id.isBlank()) {
            erroComentario = "Promoção inválida para comentário."
            android.widget.Toast.makeText(context, "Não foi possível identificar a promoção.", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser == null) {
            erroComentario = "Você precisa estar logado para comentar nessa oferta."
            android.widget.Toast.makeText(context, "Faça login para comentar.", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        enviando = true
        erroComentario = null
        val prefs = context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
        val autor = prefs.getString("user_name", "")?.takeIf { it.isNotBlank() } ?: "Anônimo"
        val timestamp = System.currentTimeMillis()
        scope.launch {
            try {
                val commentRef = FirebaseFirestore.getInstance()
                    .collection("promocoes").document(promo.id)
                    .collection("comentarios")
                    .document()

                val data = hashMapOf(
                    "autor" to autor,
                    "texto" to texto,
                    "timestamp" to timestamp,
                    "promoId" to promo.id,
                    "uid" to currentUser.uid,
                    "userId" to currentUser.uid
                )

                commentRef.set(data).await()

                comentarios = listOf(
                    com.jack.meuholerite.model.Comentario(
                        id = commentRef.id,
                        autor = autor,
                        texto = texto,
                        timestamp = timestamp
                    )
                ) + comentarios.filterNot { it.id == commentRef.id }
                novoComentario = ""
                enviando = false
                android.widget.Toast.makeText(context, "Comentário enviado.", android.widget.Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                enviando = false
                erroComentario = if (
                    e is com.google.firebase.firestore.FirebaseFirestoreException &&
                    e.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED
                ) {
                    "O Firestore recusou a gravação do comentário. A regra de segurança da coleção de comentários precisa permitir escrita para o usuário autenticado."
                } else {
                    e.message ?: "Erro ao enviar comentário."
                }
                android.util.Log.e("Promocoes", "Erro ao salvar comentário da promoção ${promo.id}", e)
                android.widget.Toast.makeText(context, "Não foi possível salvar o comentário.", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun toggleLike() {
        val willLike = !liked
        if (willLike) {
            curtidas++
            liked = true
        } else {
            curtidas--
            liked = false
        }
        FirebaseFirestore.getInstance().collection("promocoes").document(promo.id)
            .update("curtidas", com.google.firebase.firestore.FieldValue.increment(if (willLike) 1 else -1))
            .addOnFailureListener { e ->
                android.util.Log.e("Promocoes", "Erro ao curtir: ${e.message}")
                curtidas = promo.curtidas
                liked = false
            }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
            shadowElevation = 12.dp,
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .fillMaxHeight(0.9f)
        ) {
            Box {
                Column(modifier = Modifier.fillMaxSize().verticalScroll(scrollState)) {
                    // Hero image with gradient overlay
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 180.dp, max = 260.dp)
                    ) {
                        if (promo.imagemUrl.isNotBlank()) {
                            AsyncImage(
                                model = promo.imagemUrl,
                                contentDescription = null,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)),
                                contentScale = ContentScale.Crop
                            )
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                                .align(Alignment.BottomCenter)
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            Color.Transparent,
                                            MaterialTheme.colorScheme.surface
                                        )
                                    )
                                )
                        )
                        if (hasDiscount && discountPct > 0) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(12.dp)
                                    .background(
                                        Brush.linearGradient(
                                            colors = listOf(Color(0xFFFF2D55), Color(0xFFFF6B35))
                                        ),
                                        RoundedCornerShape(10.dp)
                                    )
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    "-$discountPct%",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Black
                                )
                            }
                        }
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(8.dp)
                                .size(32.dp)
                                .background(Color.Black.copy(alpha = 0.3f), CircleShape)
                                .clickable(onClick = onDismiss),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.Close,
                                null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Column(modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 0.dp, bottom = 20.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                promo.titulo,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = (-0.5).sp,
                                lineHeight = 26.sp,
                                modifier = Modifier.weight(1f)
                            )
                            if (promo.verificado) {
                                Spacer(Modifier.width(6.dp))
                                Icon(
                                    Icons.Outlined.Verified,
                                    null,
                                    tint = Color(0xFF007AFF),
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }

                        if (promo.loja.isNotBlank()) {
                            Spacer(Modifier.height(2.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Outlined.Store,
                                    null,
                                    tint = MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    promo.loja,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }

                        if (promo.descricao.isNotBlank()) {
                            Spacer(Modifier.height(14.dp))
                            Text(
                                promo.descricao,
                                fontSize = 14.sp,
                                lineHeight = 20.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }

                        if (tempoRestantePromo != null) {
                            Spacer(Modifier.height(14.dp))
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = Color(0xFFFFF4D6),
                                border = BorderStroke(1.dp, Color(0xFFFFC85C).copy(alpha = 0.35f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Outlined.Schedule,
                                        null,
                                        tint = Color(0xFFB7791F),
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        tempoRestantePromo,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF8A5A16)
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(18.dp))
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color(0xFF34C759).copy(alpha = 0.08f)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    if (promo.precoAntes > 0) {
                                        Text(
                                            "De: R$ ${"%.2f".format(promo.precoAntes)}",
                                            fontSize = 13.sp,
                                            color = Color.Gray,
                                            textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough
                                        )
                                        Spacer(Modifier.height(2.dp))
                                    }
                                    if (promo.precoDepois == 0.0) {
                                        Text(
                                            "Grátis",
                                            fontSize = 26.sp,
                                            fontWeight = FontWeight.Black,
                                            color = Color(0xFF34C759),
                                            letterSpacing = (-0.5).sp
                                        )
                                    } else {
                                        Text(
                                            "Por: R$ ${"%.2f".format(promo.precoDepois)}",
                                            fontSize = 26.sp,
                                            fontWeight = FontWeight.Black,
                                            color = Color(0xFF34C759),
                                            letterSpacing = (-0.5).sp
                                        )
                                    }
                                }
                                if (hasDiscount && discountPct > 0) {
                                    Box(
                                        modifier = Modifier
                                            .background(
                                                Color(0xFF34C759).copy(alpha = 0.2f),
                                                RoundedCornerShape(8.dp)
                                            )
                                            .padding(horizontal = 10.dp, vertical = 5.dp)
                                    ) {
                                        Text(
                                            "${discountPct}% OFF",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Black,
                                            color = Color(0xFF34C759)
                                        )
                                    }
                                }
                            }
                        }

                        // Coupon
                        if (promo.cupom.isNotBlank()) {
                            Spacer(Modifier.height(16.dp))
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = Color(0xFFFFD700).copy(alpha = 0.1f),
                                border = BorderStroke(1.dp, Color(0xFFFFD700).copy(alpha = 0.3f))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            val clip = android.content.ClipData.newPlainText("cupom", promo.cupom)
                                            (context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager).setPrimaryClip(clip)
                                            android.widget.Toast.makeText(context, "Cupom copiado!", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Outlined.LocalOffer,
                                        null,
                                        tint = Color(0xFFB8860B),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            "CUPOM",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFFB8860B),
                                            letterSpacing = 2.sp
                                        )
                                        Text(
                                            promo.cupom,
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF8B6914)
                                        )
                                    }
                                    Icon(
                                        Icons.Outlined.ContentCopy,
                                        null,
                                        tint = Color(0xFFB8860B),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }

                        // Like row + action button
                        Spacer(Modifier.height(20.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                onClick = { toggleLike() },
                                shape = RoundedCornerShape(14.dp),
                                color = if (liked) Color(0xFFFF2D55).copy(alpha = 0.1f) else MaterialTheme.colorScheme.surfaceVariant,
                                border = BorderStroke(
                                    1.dp,
                                    if (liked) Color(0xFFFF2D55).copy(alpha = 0.3f) else MaterialTheme.colorScheme.outlineVariant
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        if (liked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                        null,
                                        tint = if (liked) Color(0xFFFF2D55) else MaterialTheme.colorScheme.outline,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        "$curtidas",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (liked) Color(0xFFFF2D55) else MaterialTheme.colorScheme.outline
                                    )
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            if (promo.link.isNotBlank()) {
                                Button(
                                    onClick = {
                                        try {
                                            context.startActivity(
                                                android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(promo.link))
                                            )
                                        } catch (_: Exception) {
                                            android.widget.Toast.makeText(context, "Erro ao abrir link", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    modifier = Modifier.weight(1f).height(50.dp),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary
                                    )
                                ) {
                                    Text(
                                        if (promo.loja.isNotBlank()) "Ir para ${promo.loja}" else "Ver produto",
                                        fontWeight = FontWeight.Black,
                                        fontSize = 15.sp
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Os preços e ofertas podem mudar sem aviso prévio.",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                            lineHeight = 14.sp
                        )

                        // Comments section
                        Spacer(Modifier.height(20.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                        Spacer(Modifier.height(16.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Outlined.ChatBubbleOutline,
                                null,
                                tint = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "Comentários (${comentarios.size})",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Spacer(Modifier.height(12.dp))

                        if (comentarios.isEmpty()) {
                            Text(
                                "Seja o primeiro a comentar!",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        } else {
                            comentarios.forEach { comentario ->
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                comentario.autor,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Spacer(Modifier.weight(1f))
                                            Text(
                                                formatTimestampRelativo(comentario.timestamp),
                                                fontSize = 10.sp,
                                                color = MaterialTheme.colorScheme.outline
                                            )
                                        }
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            comentario.texto,
                                            fontSize = 13.sp,
                                            lineHeight = 18.sp,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(12.dp))
                        val podeEnviar = novoComentario.trim().isNotBlank() && !enviando
                        Surface(
                            shape = RoundedCornerShape(24.dp),
                            color = Color.Transparent,
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
                        ) {
                            Column(
                                modifier = Modifier
                                    .background(
                                        Brush.linearGradient(
                                            listOf(
                                                MaterialTheme.colorScheme.surface,
                                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f),
                                                Color(0xFFE8F4EC)
                                            )
                                        )
                                    )
                                    .padding(14.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedTextField(
                                        value = novoComentario,
                                        onValueChange = {
                                            if (it.length <= 500) {
                                                novoComentario = it
                                                if (erroComentario != null) erroComentario = null
                                            }
                                        },
                                        placeholder = {
                                            Text(
                                                "Escreva um comentário"
                                            )
                                        },
                                        modifier = Modifier.weight(1f),
                                        textStyle = LocalTextStyle.current.copy(fontSize = 14.sp),
                                        minLines = 1,
                                        maxLines = 3,
                                        shape = RoundedCornerShape(12.dp)
                                    )

                                    IconButton(
                                        onClick = { if (podeEnviar) enviarComentario() },
                                        enabled = podeEnviar && !enviando,
                                        modifier = Modifier.size(48.dp)
                                    ) {
                                        if (enviando) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(18.dp),
                                                strokeWidth = 2.dp,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        } else {
                                            Icon(
                                                Icons.AutoMirrored.Filled.Send,
                                                contentDescription = "Enviar comentário",
                                                tint = if (podeEnviar) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                                            )
                                        }
                                    }
                                }

                                Spacer(Modifier.height(8.dp))

                                Text(
                                    text = erroComentario ?: "${novoComentario.length}/500",
                                    fontSize = 11.sp,
                                    color = if (erroComentario != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PromocoesMiniCard(
    promocoes: List<com.jack.meuholerite.model.Promocao>,
    notifEnabled: Boolean = true,
    onNotifToggle: (Boolean) -> Unit = {},
    onOpenAllPromos: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var selectedPromo by remember { mutableStateOf<com.jack.meuholerite.model.Promocao?>(null) }
    val context = LocalContext.current
    val recentes = remember(promocoes) { promocoes.sortedByDescending { it.timestamp }.take(5) }
    val tooltipPrefs = remember { context.getSharedPreferences("promo_tooltip", Context.MODE_PRIVATE) }
    var showNotifTooltip by remember { mutableStateOf(!tooltipPrefs.getBoolean("notif_tooltip_shown", false)) }
    LaunchedEffect(Unit) {
        if (showNotifTooltip) {
            tooltipPrefs.edit().putBoolean("notif_tooltip_shown", true).apply()
        }
    }
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f)
                ) {
                    Icon(
                        Icons.Outlined.LocalOffer,
                        null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.padding(6.dp).size(16.dp)
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    "PROMOÇÕES",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.tertiary,
                    letterSpacing = 1.5.sp
                )
                Spacer(Modifier.weight(1f))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { onNotifToggle(!notifEnabled) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            if (notifEnabled) Icons.Filled.Notifications else Icons.Outlined.NotificationsNone,
                            null,
                            tint = if (notifEnabled) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(Modifier.width(4.dp))
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.1f)
                    ) {
                        Text(
                            "${promocoes.size} ofertas",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }
            if (showNotifTooltip) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Text(
                        "🔔 Ative ou desative notificações de promoções",
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.55f),
                        lineHeight = 14.sp,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "5 mais recentes",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Surface(
                    onClick = onOpenAllPromos,
                    shape = RoundedCornerShape(999.dp),
                    color = Color.Transparent,
                    shadowElevation = 6.dp
                ) {
                    Row(
                        modifier = Modifier
                            .background(
                                Brush.horizontalGradient(
                                    listOf(
                                        Color(0xFF13263C),
                                        Color(0xFF1C4463),
                                        Color(0xFF1F7A5A)
                                    )
                                )
                            )
                            .padding(horizontal = 14.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            Icons.Outlined.LocalOffer,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            "Todas as promoções",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(recentes) { promo ->
                    val hasDiscount = promo.precoAntes > 0 && promo.precoDepois > 0
                    val discountPct = if (hasDiscount) ((1.0 - promo.precoDepois / promo.precoAntes) * 100).toInt() else 0
                    val tempoRestantePromo = formatPromoTempoRestante(promo.expiraEm)
                    Surface(
                        onClick = { selectedPromo = promo },
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 3.dp,
                        shadowElevation = 4.dp,
                        modifier = Modifier.width(200.dp)
                    ) {
                        Column {
                            Box {
                                if (promo.imagemUrl.isNotBlank()) {
                                    AsyncImage(
                                        model = promo.imagemUrl,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(100.dp)
                                            .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(100.dp)
                                            .background(
                                                MaterialTheme.colorScheme.primaryContainer,
                                                RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Outlined.ShoppingBag,
                                            null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(32.dp)
                                        )
                                    }
                                }
                                if (hasDiscount && discountPct > 0) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopStart)
                                            .padding(6.dp)
                                            .background(
                                                Brush.linearGradient(
                                                    listOf(Color(0xFFFF2D55), Color(0xFFFF6B35))
                                                ),
                                                RoundedCornerShape(6.dp)
                                            )
                                            .padding(horizontal = 6.dp, vertical = 3.dp)
                                    ) {
                                        Text("-$discountPct%", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Black)
                                    }
                                }
                                if (tempoRestantePromo != null) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(6.dp)
                                            .background(
                                                Color(0xFF13263C).copy(alpha = 0.82f),
                                                RoundedCornerShape(999.dp)
                                            )
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            tempoRestantePromo.replace("Valida por mais ", ""),
                                            color = Color.White,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    promo.titulo,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (promo.loja.isNotBlank()) {
                                    Text(
                                        promo.loja,
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.outline,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        if (promo.precoDepois == 0.0) "Grátis" else "R$ ${"%.2f".format(promo.precoDepois)}",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Black,
                                        color = Color(0xFF34C759),
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (promo.verificado) {
                                        Icon(
                                            Icons.Outlined.Verified,
                                            null,
                                            tint = Color(0xFF007AFF),
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                                if (promo.precoAntes > 0 && promo.precoDepois > 0) {
                                    Text(
                                        "R$ ${"%.2f".format(promo.precoAntes)}",
                                        fontSize = 10.sp,
                                        color = Color.Gray,
                                        textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    selectedPromo?.let { promo ->
        PromocaoDetailDialog(
            promo = promo,
            onDismiss = { selectedPromo = null }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PromocoesFeedScreen(
    promocoes: List<com.jack.meuholerite.model.Promocao>,
    onBack: () -> Unit
) {
    var selectedPromo by remember { mutableStateOf<com.jack.meuholerite.model.Promocao?>(null) }
    val orderedPromos = remember(promocoes) { promocoes.sortedByDescending { it.timestamp } }
    val destaque = orderedPromos.firstOrNull()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Promoções")
                        Text(
                            "${orderedPromos.size} ofertas recentes",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Voltar")
                    }
                }
            )
        }
    ) { padding ->
        if (orderedPromos.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Nenhuma promoção disponível agora.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    Surface(
                        shape = RoundedCornerShape(28.dp),
                        color = Color.Transparent,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .background(
                                    Brush.linearGradient(
                                        listOf(
                                            Color(0xFF13263C),
                                            Color(0xFF1A3C5A),
                                            Color(0xFF245B47)
                                        )
                                    )
                                )
                                .padding(20.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = Color.White.copy(alpha = 0.12f)
                            ) {
                                Text(
                                    "FEED DE OFERTAS",
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 1.2.sp
                                )
                            }
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "As promoções mais recentes em um feed separado da home.",
                                color = Color.White,
                                fontSize = 22.sp,
                                lineHeight = 28.sp,
                                fontWeight = FontWeight.Black
                            )
                            Spacer(Modifier.height(10.dp))
                            Text(
                                destaque?.let { "Último destaque: ${it.titulo}" } ?: "Explore as melhores oportunidades do momento.",
                                color = Color.White.copy(alpha = 0.78f),
                                fontSize = 14.sp,
                                lineHeight = 20.sp
                            )
                        }
                    }
                }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Todas as promoções",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                "${orderedPromos.size} itens",
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                items(orderedPromos) { promo ->
                    PromocaoFeedCard(
                        promo = promo,
                        onClick = { selectedPromo = promo }
                    )
                }
            }
        }
    }

    selectedPromo?.let { promo ->
        PromocaoDetailDialog(
            promo = promo,
            onDismiss = { selectedPromo = null }
        )
    }
}

@Composable
private fun PromocaoFeedCard(
    promo: com.jack.meuholerite.model.Promocao,
    onClick: () -> Unit
) {
    val hasDiscount = promo.precoAntes > 0 && promo.precoDepois > 0
    val discountPct = if (hasDiscount) ((1.0 - promo.precoDepois / promo.precoAntes) * 100).toInt() else 0
    val tempoRestantePromo = formatPromoTempoRestante(promo.expiraEm)

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp,
        shadowElevation = 6.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
            ) {
                if (promo.imagemUrl.isNotBlank()) {
                    AsyncImage(
                        model = promo.imagemUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        MaterialTheme.colorScheme.primaryContainer,
                                        MaterialTheme.colorScheme.tertiaryContainer
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Outlined.LocalOffer,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(52.dp)
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (hasDiscount && discountPct > 0) {
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = Color(0xFFFF5A36)
                        ) {
                            Text(
                                "-$discountPct%",
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }
                    if (promo.verificado) {
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = Color(0xFF007AFF)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Outlined.Verified,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text("Verificada", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                if (tempoRestantePromo != null) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(12.dp),
                        shape = RoundedCornerShape(999.dp),
                        color = Color(0xFF13263C).copy(alpha = 0.85f)
                    ) {
                        Text(
                            tempoRestantePromo.replace("Valida por mais ", ""),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Column(modifier = Modifier.padding(16.dp)) {
                if (promo.loja.isNotBlank()) {
                    Text(
                        promo.loja.uppercase(Locale.getDefault()),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 1.sp
                    )
                    Spacer(Modifier.height(6.dp))
                }

                Text(
                    promo.titulo,
                    fontSize = 18.sp,
                    lineHeight = 24.sp,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface
                )

                if (promo.descricao.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        promo.descricao,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.height(14.dp))

                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        if (promo.precoDepois == 0.0) "Grátis" else "R$ ${"%.2f".format(promo.precoDepois)}",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Black,
                        color = Color(0xFF169B62)
                    )
                    if (promo.precoAntes > 0 && promo.precoDepois > 0) {
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "R$ ${"%.2f".format(promo.precoAntes)}",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.outline,
                            textDecoration = TextDecoration.LineThrough
                        )
                    }
                }

                if (promo.cupom.isNotBlank()) {
                    Spacer(Modifier.height(12.dp))
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.8f)
                    ) {
                        Text(
                            "Cupom: ${promo.cupom}",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }
        }
    }
}


@Composable
fun SalaryGraphDialog(db: AppDatabase, gson: Gson, onDismiss: () -> Unit) {
    var history by remember { mutableStateOf<List<ReciboPagamento>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val list = db.reciboDao()
                .getAll()
                .map { it.toModel(gson) }
                .sortedBy { it.periodo.extractStartDateForRecibo() }
            history = list
            isLoading = false
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight(),
            shape = RoundedCornerShape(32.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                if (isLoading) {
                    Box(Modifier.fillMaxWidth().height(250.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else if (history.isEmpty()) {
                    Box(Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.no_chart_data))
                    }
                } else {
                    PremiumEvolutionChart(
                        history = history,
                        showGross = true,
                        modifier = Modifier.padding(4.dp)
                    )
                    
                    Spacer(Modifier.height(12.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(
                            onClick = onDismiss,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Fechar", fontWeight = FontWeight.ExtraBold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SmartAlertCard(alert: SmartAlert) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = alert.color.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, alert.color.copy(alpha = 0.18f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(alert.color.copy(alpha = 0.14f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(alert.icon, null, tint = alert.color, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(alert.title, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(4.dp))
                Text(
                    alert.message,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            }
        }
    }
}

@Composable
private fun SalaryRankingHomeCard(
    ranking: SalaryRanking?,
    isLoading: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        color = Color.Transparent,
        shadowElevation = 6.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .background(
                    brush = Brush.verticalGradient(
                        listOf(
                            Color(0xFF12253B),
                            Color(0xFF1D3B5A),
                            Color(0xFF2F5C85)
                        )
                    )
                )
                .padding(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = Color.White.copy(alpha = 0.12f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Outlined.EmojiEvents, null, tint = Color(0xFFFFD166), modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "RANKING DE SALARIOS",
                                color = Color(0xFFFFD166),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 0.8.sp
                            )
                        }
                    }
                    Surface(
                        shape = CircleShape,
                        color = Color.White.copy(alpha = 0.12f)
                    ) {
                        Box(
                            modifier = Modifier.size(40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.AutoMirrored.Outlined.TrendingUp, null, tint = Color(0xFFFFD166), modifier = Modifier.size(20.dp))
                        }
                    }
                }

                Text(
                    text = if (ranking != null) "Compare cargos em cards expansivos e veja quem lidera o radar salarial agora." else "Abra o radar salarial para explorar cargos, faixas e medias reportadas.",
                    color = Color.White,
                    fontSize = 21.sp,
                    lineHeight = 26.sp,
                    fontWeight = FontWeight.Black
                )

                Text(
                    text = if (ranking != null) "Cargo em destaque: ${ranking.cargo}" else "Toque para abrir a lista completa de cargos",
                    color = Color.White.copy(alpha = 0.74f),
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )

                if (isLoading) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().height(4.dp),
                        color = Color.White,
                        trackColor = Color.White.copy(alpha = 0.2f)
                    )
                } else {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color.White.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "Maior salario reportado",
                                        color = Color(0xFFFFD166),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Black,
                                        letterSpacing = 0.9.sp
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        if (ranking != null) "R$ ${ranking.maxReportedSalary.formatBrMoney()}" else "Sem dados",
                                        color = Color.White,
                                        fontSize = 30.sp,
                                        lineHeight = 34.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = Color(0xFFFFD166).copy(alpha = 0.14f)
                                ) {
                                    Text(
                                        text = if (ranking != null) "${ranking.count} relatos" else "Radar",
                                        color = Color(0xFFFFD166),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                                    )
                                }
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                HomeRankingMetric(
                                    label = "Cargo",
                                    value = ranking?.cargo ?: "Sem dados",
                                    modifier = Modifier.weight(1f)
                                )
                                HomeRankingMetric(
                                    label = "Empresas",
                                    value = if (ranking != null) ranking.empresasCount.toString() else "-",
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HomeRankingPill(Icons.Outlined.QueryStats, "Lista por cargos")
                    HomeRankingPill(Icons.Outlined.Business, "Faixas reais")
                }

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                "Toque para abrir o ranking",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Abra a lista e expanda qualquer cargo para ver os detalhes",
                                color = Color.White.copy(alpha = 0.72f),
                                fontSize = 11.sp
                            )
                        }
                        Icon(Icons.Outlined.ExpandMore, null, tint = Color(0xFFFFD166))
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeRankingPill(icon: ImageVector, label: String) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = Color.White.copy(alpha = 0.10f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
    }
}

@Composable
private fun HomeRankingMetric(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = Color.White.copy(alpha = 0.08f)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = label,
                color = Color.White.copy(alpha = 0.66f),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = value,
                color = Color.White,
                fontSize = 13.sp,
                lineHeight = 17.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun MonthlySummaryCard(summary: MonthlySummary) {
    val statusColor = if (summary.remaining >= 0) Color(0xFF34C759) else Color(0xFFFF3B30)
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "Fechamento rápido do mês com base no último holerite e no financeiro atual.",
                fontSize = 13.sp,
                lineHeight = 18.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                MetricMini(
                    title = "BRUTO",
                    value = "R$ ${summary.gross.formatBrMoney()}",
                    valueColor = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                MetricMini(
                    title = "LÍQUIDO",
                    value = "R$ ${summary.net.formatBrMoney()}",
                    valueColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                MetricMini(
                    title = "DESCONTOS",
                    value = "R$ ${summary.discounts.formatBrMoney()}",
                    valueColor = Color(0xFFFF9500),
                    modifier = Modifier.weight(1f)
                )
                MetricMini(
                    title = "GASTOS+PARCELAS",
                    value = "R$ ${(summary.expenses + summary.debts).formatBrMoney()}",
                    valueColor = Color(0xFF5856D6),
                    modifier = Modifier.weight(1f)
                )
            }

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = statusColor.copy(alpha = 0.10f),
                border = BorderStroke(1.dp, statusColor.copy(alpha = 0.18f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        "Saldo projetado",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = statusColor
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "R$ ${summary.remaining.formatBrMoney()}",
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Black,
                        color = statusColor
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (summary.remaining >= 0) "Pelas contas atuais, você ainda fecha o mês no positivo."
                        else "Pelas contas atuais, vale revisar gastos para evitar fechar o mês no vermelho.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusChip(
    icon: ImageVector,
    label: String,
    value: String,
    color: Color,
    onClick: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "status_chip")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.08f,
        targetValue = 0.18f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "status_chip_glow"
    )
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = color.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, color.copy(alpha = glowAlpha))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = color, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = color)
            Spacer(Modifier.width(6.dp))
            PrivacyValueText(
                value = value,
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun MetricMini(
    title: String,
    value: String,
    valueColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.68f),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                title,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.4.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            PrivacyValueText(
                value = value,
                fontSize = 16.sp,
                fontWeight = FontWeight.Black,
                color = valueColor
            )
        }
    }
}

@Composable
private fun QuickActionsRow(
    onRecibos: () -> Unit,
    onPonto: () -> Unit,
    onTools: () -> Unit,
    onFinance: () -> Unit
) {
    SectionHeader("Ações rápidas")
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        QuickActionButton("Recibos", Icons.Outlined.Description, onRecibos, Modifier.weight(1f))
        QuickActionButton("Ponto", Icons.Outlined.Schedule, onPonto, Modifier.weight(1f))
        QuickActionButton("Financeiro", Icons.Outlined.AccountBalanceWallet, onFinance, Modifier.weight(1f))
        QuickActionButton("Tools", Icons.Outlined.Handyman, onTools, Modifier.weight(1f))
    }
}

@Composable
private fun QuickActionButton(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "quick_action")
    val iconScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "quick_action_icon_scale"
    )
    val iconAlpha by infiniteTransition.animateFloat(
        initialValue = 0.10f,
        targetValue = 0.18f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "quick_action_icon_alpha"
    )
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 1.dp,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .scale(iconScale)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = iconAlpha), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            }
            Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun ModernDeductionItem(rank: Int, item: ReciboItem, percentage: Double) {
    var expanded by remember { mutableStateOf(false) }
    val coralColor = Color(0xFFFF4D4D)
    
    Surface(
        onClick = { expanded = !expanded },
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(16.dp),
        shadowElevation = 2.dp,
        tonalElevation = 1.dp,
        border = BorderStroke(1.dp, coralColor.copy(alpha = 0.14f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Rank Circle
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(coralColor.copy(alpha = 0.12f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "#$rank",
                        style = MaterialTheme.typography.labelLarge,
                        color = coralColor,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
                
                Spacer(Modifier.width(14.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.descricao,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = item.referencia.ifBlank { "Sem ref." },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Column(horizontalAlignment = Alignment.End) {
                    PrivacyValueText(
                        value = "R$ ${item.valor}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = coralColor
                    )
                    Text(
                        text = "${(percentage * 100).format(1)}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = coralColor.copy(alpha = 0.7f)
                    )
                }
            }
            
            Spacer(Modifier.height(12.dp))
            
            // Progress Bar relative to total deductions
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .background(coralColor.copy(alpha = 0.05f), CircleShape)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(percentage.toFloat().coerceIn(0f, 1f))
                        .fillMaxHeight()
                        .background(
                            brush = Brush.horizontalGradient(
                                colors = listOf(coralColor.copy(alpha = 0.6f), coralColor)
                            ),
                            shape = CircleShape
                        )
                )
            }
            androidx.compose.animation.AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                        .background(coralColor.copy(alpha = 0.04f), RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    Text(
                        text = "O que é este desconto?",
                        fontWeight = FontWeight.Bold,
                        color = coralColor,
                        fontSize = 12.sp
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = com.jack.meuholerite.ui.getDetalheParaItem(item.descricao, false),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                }
            }
        }
    }
}

// Helper to format doubles
fun Double.format(digits: Int) = "%.${digits}f".format(this)

@Composable
fun DeductionRow(rank: Int, item: ReciboItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(28.dp).background(Color(0xFFFF3B30).copy(alpha = 0.12f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(rank.toString(), fontWeight = FontWeight.Black, color = Color(0xFFFF3B30))
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(item.descricao, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (item.referencia.isNotEmpty()) {
                Text("Ref: ${item.referencia}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun Double.formatPct(): String {
    return String.format(java.util.Locale.getDefault(), "%.0f%%", this).replace(".", ",")
}
