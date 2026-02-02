package com.jack.meuholerite.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.util.Base64
import android.util.Log
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import com.jack.meuholerite.R
import com.jack.meuholerite.utils.BackupManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

class WebAppInterface(private val onBlobReceived: (String, String) -> Unit) {
    @JavascriptInterface
    fun processBlob(base64Data: String, fileName: String) {
        onBlobReceived(base64Data, fileName)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun EpaysWebViewPage(onPdfDownloaded: (Uri) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var webView: WebView? by remember { mutableStateOf(null) }
    var hasError by remember { mutableStateOf(false) }
    var progress by remember { mutableIntStateOf(0) }
    var isRefreshing by remember { mutableStateOf(false) }

    val baseUrl = "https://app.epays.com.br/"
    val backupManager = remember { BackupManager(context) }
    val cookieStore = remember { EpaysCookieStore(context) }

    var filePathCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uris = WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data)
        filePathCallback?.onReceiveValue(uris)
        filePathCallback = null
    }

    fun saveAndImportPdf(bytes: ByteArray, fileName: String) {
        scope.launch(Dispatchers.IO) {
            try {
                val file = File(context.cacheDir, fileName)
                FileOutputStream(file).use { it.write(bytes) }
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                withContext(Dispatchers.Main) { 
                    onPdfDownloaded(uri)
                    // Resetar para a página inicial após o download para não ficar travado na tela de PDF
                    webView?.loadUrl(baseUrl)
                }
            } catch (e: Exception) {
                Log.e("EpaysWebView", "Error saving PDF", e)
            }
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
            } catch (e: Exception) {
                Log.e("EpaysWebView", "Error downloading directly", e)
            }
        }
    }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = {
            isRefreshing = true
            webView?.reload()
        }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (hasError) {
                Column(modifier = Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Outlined.WifiOff, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                    Text(stringResource(R.string.no_connection), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Button(onClick = { hasError = false; webView?.reload() }) { Text(stringResource(R.string.try_again)) }
                }
            }

            AndroidView(
                modifier = Modifier.fillMaxSize().alpha(if (hasError) 0f else 1f),
                factory = { ctx ->
                    WebView(ctx).apply {
                        webView = this
                        
                        overScrollMode = WebView.OVER_SCROLL_NEVER
                        isVerticalScrollBarEnabled = false
                        isHorizontalScrollBarEnabled = false
                        
                        val cm = CookieManager.getInstance()
                        cm.setAcceptCookie(true)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            cm.setAcceptThirdPartyCookies(this, true)
                        }

                        // Restaurar cookies salvos antes de carregar
                        restoreCookiesBeforeLoad(baseUrl, cm, cookieStore)

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
                            allowFileAccessFromFileURLs = true
                            allowUniversalAccessFromFileURLs = true
                            javaScriptCanOpenWindowsAutomatically = true
                            setSupportMultipleWindows(false)
                            setLayerType(WebView.LAYER_TYPE_HARDWARE, null)
                            setSupportZoom(true)
                            builtInZoomControls = true
                            displayZoomControls = false
                            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                            userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                        }

                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                progress = newProgress
                                if (newProgress == 100) isRefreshing = false
                            }

                            override fun onShowFileChooser(webView: WebView?, filePathCallbackIn: ValueCallback<Array<Uri>>?, fileChooserParams: FileChooserParams?): Boolean {
                                filePathCallback?.onReceiveValue(null)
                                filePathCallback = filePathCallbackIn
                                try {
                                    val intent = fileChooserParams?.createIntent()
                                    if (intent != null) filePickerLauncher.launch(intent)
                                } catch (e: Exception) {
                                    filePathCallback = null
                                    return false
                                }
                                return true
                            }
                        }

                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(v: WebView?, u: String?, f: Bitmap?) { hasError = false }
                            override fun onReceivedError(v: WebView?, request: WebResourceRequest?, error: WebResourceError?) { if (request?.isForMainFrame == true) hasError = true }

                            override fun shouldOverrideUrlLoading(v: WebView?, request: WebResourceRequest?): Boolean {
                                val url = request?.url?.toString() ?: ""
                                if (url.lowercase().contains(".pdf") || url.contains("download", true) || url.contains("content-type=application/pdf")) {
                                    if (url.startsWith("blob:")) handleBlob(url) 
                                    else downloadDirectly(url, v?.settings?.userAgentString ?: "")
                                    return true
                                }
                                return false
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                val currentCookies = CookieManager.getInstance().getCookie(baseUrl)
                                if (!currentCookies.isNullOrBlank()) {
                                    cookieStore.saveCookieHeader(currentCookies)
                                    scope.launch { backupManager.backupData() }
                                }
                                isRefreshing = false
                            }
                        }

                        setDownloadListener { url, userAgent, _, _, _ ->
                            if (url.startsWith("blob:")) handleBlob(url) 
                            else downloadDirectly(url, userAgent)
                        }

                        loadUrl(baseUrl)
                    }
                }
            )

            if (progress < 100) {
                LinearProgressIndicator(
                    progress = { progress / 100f },
                    modifier = Modifier.fillMaxWidth().height(2.dp).align(Alignment.TopCenter),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            try {
                CookieManager.getInstance().flush()
                scope.launch { backupManager.backupData() }
            } catch (_: Exception) {}
        }
    }
}

private fun restoreCookiesBeforeLoad(baseUrl: String, cm: CookieManager, store: EpaysCookieStore) {
    try {
        val saved = store.getCookieHeader() ?: return
        cm.setAcceptCookie(true)
        val parts = saved.split("; ")
        for (p in parts) {
            if (p.contains("=")) {
                cm.setCookie(baseUrl, p)
            }
        }
        cm.flush()
    } catch (e: Exception) {
        Log.e("EPAYS", "Erro restore cookies", e)
    }
}

private class EpaysCookieStore(context: Context) {
    private val prefs = context.getSharedPreferences("epays_cookies", Context.MODE_PRIVATE)
    fun saveCookieHeader(cookieHeader: String) = prefs.edit().putString("cookie_header", cookieHeader).apply()
    fun getCookieHeader(): String? = prefs.getString("cookie_header", null)
}