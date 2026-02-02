package com.jack.meuholerite

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.CookieManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import com.google.gson.Gson
import com.jack.meuholerite.ads.AdsDataStore
import com.jack.meuholerite.ads.RewardedInterstitialAdManager
import com.jack.meuholerite.database.AppDatabase
import com.jack.meuholerite.database.toEntity
import com.jack.meuholerite.parser.PontoParser
import com.jack.meuholerite.parser.ReciboParser
import com.jack.meuholerite.ui.theme.MeuHoleriteTheme
import com.jack.meuholerite.utils.BackupManager
import com.jack.meuholerite.utils.PdfReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

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
        val scope = (this as? ComponentActivity)?.let { androidx.lifecycle.lifecycleScope } ?: return
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

class EpaysWebAppInterface(private val onBlobReceived: (String, String) -> Unit) {
    @JavascriptInterface fun processBlob(base64Data: String, fileName: String) { onBlobReceived(base64Data, fileName) }
}

@SuppressLint("SetJavaScriptEnabled")
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

                addJavascriptInterface(EpaysWebAppInterface { base64, name ->
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
