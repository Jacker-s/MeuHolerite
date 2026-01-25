package com.jack.meuholerite

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAd
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAdLoadCallback
import com.jack.meuholerite.utils.UpdateManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Helper para encontrar a Activity a partir do Contexto
fun Context.findActivity(): Activity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}

@Composable
fun AboutDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val scope = rememberCoroutineScope()
    val updateManager = remember { UpdateManager(context) }
    
    var showHelpScreen by remember { mutableStateOf(false) }
    var nativeAd by remember { mutableStateOf<NativeAd?>(null) }
    var adLoadError by remember { mutableStateOf<String?>(null) }
    var rewardedInterstitialAd by remember { mutableStateOf<RewardedInterstitialAd?>(null) }
    var checkingUpdate by remember { mutableStateOf(false) }
    var updateInfo by remember { mutableStateOf<Pair<String, String>?>(null) }

    val currentVersion = remember(context) {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0"
        } catch (e: Exception) {
            "1.0"
        }
    }

    // Carrega os anúncios em background
    LaunchedEffect(Unit) {
        activity?.let { act ->
            // Carrega anúncio nativo
            val adLoader = AdLoader.Builder(act, "ca-app-pub-7931782163570852/1828597034")
                .forNativeAd { ad -> 
                    Log.d("AdMob", "Native Ad Carregado com Sucesso")
                    nativeAd = ad 
                }
                .withAdListener(object : AdListener() {
                    override fun onAdFailedToLoad(error: LoadAdError) {
                        Log.e("AdMob", "Falha Native: ${error.message} (Código: ${error.code})")
                        adLoadError = error.message
                    }
                })
                .build()
            adLoader.loadAd(AdRequest.Builder().build())

            // Carrega anúncio Intersticial Premiado
            RewardedInterstitialAd.load(act, "ca-app-pub-7931782163570852/5656161402", AdRequest.Builder().build(),
                object : RewardedInterstitialAdLoadCallback() {
                    override fun onAdLoaded(ad: RewardedInterstitialAd) {
                        rewardedInterstitialAd = ad
                        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                            override fun onAdDismissedFullScreenContent() {
                                rewardedInterstitialAd = null
                            }
                        }
                    }
                    override fun onAdFailedToLoad(error: LoadAdError) {
                        Log.e("AdMob", "Falha Rewarded Interstitial: ${error.message}")
                    }
                })
        }
    }

    DisposableEffect(Unit) {
        onDispose { nativeAd?.destroy() }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight(),
            shape = RoundedCornerShape(28.dp),
            color = Color.White
        ) {
            Crossfade(targetState = showHelpScreen, label = "ScreenTransition") { isHelpScreen ->
                if (isHelpScreen) {
                    HelpDeveloperContent(
                        nativeAd = nativeAd,
                        loadError = adLoadError,
                        onBack = { showHelpScreen = false }
                    )
                } else {
                    AboutMainContent(
                        currentVersion = currentVersion,
                        updateInfo = updateInfo,
                        checkingUpdate = checkingUpdate,
                        onHelpClick = {
                            if (rewardedInterstitialAd != null && activity != null) {
                                rewardedInterstitialAd?.show(activity) { reward ->
                                    Log.d("AdMob", "Usuário premiado: ${reward.amount}")
                                }
                                rewardedInterstitialAd = null
                            }
                            showHelpScreen = true
                        },
                        onCheckUpdate = {
                            scope.launch {
                                checkingUpdate = true
                                updateManager.checkForUpdates(
                                    currentVersion = currentVersion,
                                    onUpdateAvailable = { v, url -> updateInfo = v to url },
                                    onNoUpdate = { Toast.makeText(context, "Versão atualizada!", Toast.LENGTH_SHORT).show() },
                                    onError = { Toast.makeText(context, "Erro na atualização", Toast.LENGTH_SHORT).show() }
                                )
                                checkingUpdate = false
                            }
                        },
                        onUpdateNow = { updateInfo?.let { updateManager.downloadAndInstall(it.second, it.first) } },
                        onClose = onDismiss
                    )
                }
            }
        }
    }
}

@Composable
fun AboutMainContent(
    currentVersion: String,
    updateInfo: Pair<String, String>?,
    checkingUpdate: Boolean,
    onHelpClick: () -> Unit,
    onCheckUpdate: () -> Unit,
    onUpdateNow: () -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    Column(modifier = Modifier.padding(24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.about_title), fontWeight = FontWeight.Bold, fontSize = 22.sp, modifier = Modifier.weight(1f))
            IconButton(onClick = onClose) { Icon(Icons.Default.Close, null) }
        }
        
        Spacer(Modifier.height(16.dp))
        
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            AboutInfoRow(Icons.Default.Person, stringResource(R.string.developer_label), stringResource(R.string.developer_name))
            
            ContactActionRow(Icons.Default.Phone, "Telefone") {
                val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${context.getString(R.string.developer_phone)}"))
                context.startActivity(intent)
            }

            ContactActionRow(Icons.Default.Email, "E-mail") {
                val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:${context.getString(R.string.developer_email)}"))
                context.startActivity(intent)
            }

            Button(
                onClick = onHelpClick,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9500)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Favorite, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Ajude o Desenvolvedor", fontWeight = FontWeight.Bold)
            }

            AboutInfoRow(Icons.Default.Info, stringResource(R.string.version_label), currentVersion)

            if (updateInfo != null) {
                Surface(color = Color(0xFF34C759).copy(alpha = 0.1f), shape = RoundedCornerShape(12.dp)) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Nova versão v${updateInfo.first}", fontWeight = FontWeight.Bold, color = Color(0xFF248A3D))
                        Button(onClick = onUpdateNow, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF34C759)), modifier = Modifier.fillMaxWidth()) {
                            Text("Atualizar Agora")
                        }
                    }
                }
            } else {
                OutlinedButton(onClick = onCheckUpdate, enabled = !checkingUpdate, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                    if (checkingUpdate) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    else Text("Verificar Atualizações")
                }
            }
        }
    }
}

@Composable
fun HelpDeveloperContent(nativeAd: NativeAd?, loadError: String?, onBack: () -> Unit) {
    var timeLeft by remember { mutableIntStateOf(10) }
    
    LaunchedEffect(Unit) {
        while (timeLeft > 0) {
            delay(1000)
            timeLeft--
        }
    }

    Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            IconButton(onClick = onBack, enabled = timeLeft == 0) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = if (timeLeft == 0) Color.Black else Color.Gray)
            }
            Text("Obrigado pelo Apoio!", fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
            Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                if (timeLeft > 0) Text(timeLeft.toString(), fontWeight = FontWeight.Bold, color = Color(0xFF007AFF))
                else Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF34C759))
            }
        }

        Spacer(Modifier.height(16.dp))
        Text("Visualizar este anúncio ajuda a manter o aplicativo gratuito.", fontSize = 14.sp, color = Color.Gray, textAlign = TextAlign.Center)
        Spacer(Modifier.height(20.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(340.dp)
                .background(Color(0xFFF2F2F7), RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (nativeAd != null) {
                AndroidView(
                    modifier = Modifier.fillMaxSize().padding(8.dp),
                    factory = { ctx ->
                        NativeAdView(ctx).apply {
                            val root = LinearLayout(ctx).apply {
                                orientation = LinearLayout.VERTICAL
                                setPadding(16, 16, 16, 16)
                            }
                            val headline = TextView(ctx).apply { 
                                textSize = 16f
                                setTypeface(null, android.graphics.Typeface.BOLD)
                                setTextColor(android.graphics.Color.BLACK) 
                            }
                            val body = TextView(ctx).apply { 
                                textSize = 13f
                                setTextColor(android.graphics.Color.DKGRAY)
                                setPadding(0, 4, 0, 8) 
                            }
                            val media = MediaView(ctx).apply { 
                                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 160.toPx(ctx))
                            }
                            val btn = android.widget.Button(ctx).apply { 
                                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                                setBackgroundColor(android.graphics.Color.parseColor("#007AFF"))
                                setTextColor(android.graphics.Color.WHITE)
                            }

                            root.addView(headline)
                            root.addView(body)
                            root.addView(media)
                            root.addView(btn)
                            
                            this.addView(root)
                            this.headlineView = headline
                            this.bodyView = body
                            this.mediaView = media
                            this.callToActionView = btn
                        }
                    },
                    update = { adView ->
                        (adView.headlineView as? TextView)?.text = nativeAd.headline
                        (adView.bodyView as? TextView)?.text = nativeAd.body
                        nativeAd.mediaContent?.let { adView.mediaView?.setMediaContent(it) }
                        (adView.callToActionView as? android.widget.Button)?.text = nativeAd.callToAction
                        adView.setNativeAd(nativeAd)
                    }
                )
            } else if (loadError != null) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(16.dp)) {
                    Icon(Icons.Default.ErrorOutline, null, tint = Color.Gray, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(8.dp))
                    Text("Não foi possível carregar o anúncio no momento.", textAlign = TextAlign.Center, fontSize = 12.sp, color = Color.Gray)
                    Text("Erro: $loadError", fontSize = 10.sp, color = Color.LightGray)
                }
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color(0xFF007AFF))
                    Spacer(Modifier.height(8.dp))
                    Text("Carregando apoio...", fontSize = 12.sp, color = Color.Gray)
                }
            }
        }
        
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = onBack, 
            enabled = timeLeft == 0, 
            modifier = Modifier.fillMaxWidth(), 
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = if (timeLeft == 0) Color(0xFF34C759) else Color.LightGray)
        ) {
            Text(if (timeLeft > 0) "Aguarde $timeLeft seg..." else "Concluir")
        }
    }
}

// Extensão para converter DP em PX (Corrigida para evitar receiver mismatch)
fun Int.toPx(context: Context): Int = (this * context.resources.displayMetrics.density).toInt()

@Composable
fun AboutInfoRow(icon: ImageVector, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = Color(0xFF007AFF), modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(label, fontSize = 12.sp, color = Color.Gray)
            Text(value, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
fun ContactActionRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = Color(0xFFF2F2F7),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = Color(0xFF007AFF), modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(label, fontSize = 11.sp, color = Color.Gray)
                Text("Entrar em contato", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF007AFF))
            }
            Icon(Icons.Default.ChevronRight, null, tint = Color.LightGray, modifier = Modifier.size(16.dp))
        }
    }
}
