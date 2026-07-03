package com.jack.meuholerite.ui

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Message
import android.util.Base64
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.webkit.*
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.jack.meuholerite.R
import com.jack.meuholerite.utils.BackupManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

class WebAppInterface(
    val onBlobReceived: (String, String, String) -> Unit,
    val onCredentialsCaptured: (String, String) -> Unit
) {
    @JavascriptInterface
    fun processBlob(base64: String, name: String, blobUrl: String) {
        onBlobReceived(base64, name, blobUrl)
    }

    @JavascriptInterface
    fun saveLogin(login: String, pass: String) {
        onCredentialsCaptured(login, pass)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun EpaysWebViewPage(startUrl: String? = null, onPdfDownloaded: (Uri, String) -> Unit) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val scope = rememberCoroutineScope()
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var hasError by remember { mutableStateOf(false) }
    var progress by remember { mutableIntStateOf(0) }
    var isRefreshing by remember { mutableStateOf(false) }
    
    var showSaveCredentialDialog by remember { mutableStateOf<Pair<String, String>?>(null) }

    val baseUrl = "https://app.epays.com.br/"
    val backupManager = remember { BackupManager(context) }
    val cookieStore = remember { EpaysCookieStore(context) }

    // File Chooser
    var filePathCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uris = WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data)
        filePathCallback?.onReceiveValue(uris)
        filePathCallback = null
    }

    // Permissions
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> }

    fun requestPermissions(perms: Array<String>) {
        val needed = perms.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (needed.isNotEmpty()) {
            permissionLauncher.launch(needed.toTypedArray())
        }
    }

    fun saveAndImportPdf(file: File, url: String) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        onPdfDownloaded(uri, url)
    }

    fun saveBytesToPdf(bytes: ByteArray, fileName: String, url: String) {
        scope.launch(Dispatchers.IO) {
            try {
                val file = File(context.cacheDir, fileName)
                FileOutputStream(file).use { it.write(bytes) }
                saveAndImportPdf(file, url)
            } catch (e: Exception) {
                Log.e("EpaysWebView", "Error saving PDF bytes", e)
            }
        }
    }

    fun handleBlob(url: String) {
        val script = """
            (function() {
                console.log('Capturando blob: ' + '$url');
                fetch('$url')
                    .then(response => response.blob())
                    .then(blob => {
                        var reader = new FileReader();
                        reader.onloadend = function() {
                            console.log('Blob processado, enviado para o Android');
                            AndroidDownloadInterface.processBlob(reader.result, 'documento_' + Date.now() + '.pdf', '$url');
                        };
                        reader.readAsDataURL(blob);
                    })
                    .catch(err => {
                        console.error('Erro no fetch blob, tentando XHR: ', err);
                        var xhr = new XMLHttpRequest();
                        xhr.open('GET', '$url', true);
                        xhr.responseType = 'blob';
                        xhr.onload = function() {
                            if (this.status == 200) {
                                var r = new FileReader();
                                r.onloadend = function() {
                                    AndroidDownloadInterface.processBlob(r.result, 'documento_xhr_' + Date.now() + '.pdf', '$url');
                                };
                                r.readAsDataURL(this.response);
                            }
                        };
                        xhr.send();
                    });
            })();
        """.trimIndent()
        webViewRef?.evaluateJavascript(script, null)
    }

    fun downloadDirectly(url: String, userAgent: String) {
        val referer = webViewRef?.url ?: baseUrl
        scope.launch(Dispatchers.IO) {
            try {
                Log.d("EpaysWebView", "Iniciando download direto: $url")
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.apply {
                    setRequestProperty("User-Agent", userAgent)
                    setRequestProperty("Cookie", CookieManager.getInstance().getCookie(url))
                    setRequestProperty("Referer", referer)
                    connectTimeout = 10000
                    readTimeout = 20000
                    doInput = true
                }
                
                val contentType = connection.contentType
                Log.d("EpaysWebView", "Resposta recebida: ${connection.responseCode}, Tipo: $contentType")

                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    val file = File(context.cacheDir, "download_${System.currentTimeMillis()}.pdf")
                    connection.inputStream.use { input ->
                        file.outputStream().use { output ->
                            input.copyTo(output, bufferSize = 8192)
                        }
                    }
                    saveAndImportPdf(file, url)
                }
                connection.disconnect()
            } catch (e: Exception) {
                Log.e("EpaysWebView", "Error downloading directly", e)
            }
        }
    }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = {
            isRefreshing = true
            webViewRef?.reload()
        }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (hasError) {
                Column(modifier = Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Outlined.WifiOff, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                    Text("Sem Conexão", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Button(onClick = { hasError = false; webViewRef?.reload() }) { Text("Tentar Novamente") }
                }
            }

            AndroidView(
                modifier = Modifier.fillMaxSize().alpha(if (hasError) 0f else 1f),
                factory = { ctx ->
                    WebView(ctx).apply {
                        webViewRef = this

                        overScrollMode = WebView.OVER_SCROLL_NEVER
                        isVerticalScrollBarEnabled = false
                        isHorizontalScrollBarEnabled = false


                        // Configuração automática de Cookies
                        val cm = CookieManager.getInstance()
                        cm.setAcceptCookie(true)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            cm.setAcceptThirdPartyCookies(this, true)
                        }

                        restoreCookiesBeforeLoad(baseUrl, cm, cookieStore)

                        val commonClient = object : WebViewClient() {
                            override fun onPageStarted(v: WebView?, u: String?, f: Bitmap?) { hasError = false }
                            override fun onReceivedError(v: WebView?, request: WebResourceRequest?, error: WebResourceError?) { if (request?.isForMainFrame == true) hasError = true }

                            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                val url = request?.url?.toString() ?: ""
                                if (url.startsWith("tel:") || url.startsWith("mailto:") || url.startsWith("intent:") || url.startsWith("whatsapp:")) {
                                    try {
                                        val intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
                                        context.startActivity(intent)
                                        return true
                                    } catch (_: Exception) { return false }
                                }
                                return false
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                view?.evaluateJavascript("""
                                    (function() {
                                        // 0. Forçar Layout
                                        var style = document.createElement('style');
                                        style.innerHTML = 'pdf-viewer, canvas, .pdf-container { display: block !important; visibility: visible !important; min-height: 500px !important; width: 100% !important; }';
                                        document.head.appendChild(style);

                                        // 1. Interceptar a criação de Blobs
                                        var originalCreateObjectURL = URL.createObjectURL;
                                        URL.createObjectURL = function(obj) {
                                            var url = originalCreateObjectURL(obj);
                                            try {
                                                if (obj instanceof Blob && obj.type === 'application/pdf') {
                                                    var fr = new FileReader();
                                                    fr.onloadend = function() { AndroidDownloadInterface.processBlob(fr.result, 'blob_doc_' + Date.now() + '.pdf', url); };
                                                    fr.readAsDataURL(obj);
                                                }
                                            } catch(e) {}
                                            return url;
                                        };

                                        // 2. Interceptar Fetch global
                                        var originalFetch = window.fetch;
                                        window.fetch = function() {
                                            return originalFetch.apply(this, arguments).then(function(response) {
                                                var contentType = response.headers.get('content-type');
                                                if (contentType && contentType.indexOf('application/pdf') !== -1) {
                                                    response.clone().blob().then(function(blob) {
                                                        var fr = new FileReader();
                                                        fr.onloadend = function() { AndroidDownloadInterface.processBlob(fr.result, 'fetch_doc_' + Date.now() + '.pdf', response.url); };
                                                        fr.readAsDataURL(blob);
                                                    });
                                                }
                                                return response;
                                            });
                                        };

                                        // 3. Interceptar XHR global
                                        var originalXHROpen = XMLHttpRequest.prototype.open;
                                        XMLHttpRequest.prototype.open = function() {
                                            this.addEventListener('load', function() {
                                                var contentType = this.getResponseHeader('content-type');
                                                if (contentType && contentType.indexOf('application/pdf') !== -1) {
                                                    var blob = new Blob([this.response], { type: 'application/pdf' });
                                                    var fr = new FileReader();
                                                    fr.onloadend = function() { AndroidDownloadInterface.processBlob(fr.result, 'xhr_doc_' + Date.now() + '.pdf', ''); };
                                                    fr.readAsDataURL(blob);
                                                }
                                            });
                                            originalXHROpen.apply(this, arguments);
                                        };

                                        // 4. Sniffing periódico do DOM (Angular/PDF.js)
                                        setInterval(function() {
                                            if (window.PDFViewerApplication && window.PDFViewerApplication.url && !window.PDF_CAPTURED) {
                                                window.PDF_CAPTURED = true;
                                                fetch(window.PDFViewerApplication.url).then(r => r.blob()).then(blob => {
                                                    var fr = new FileReader();
                                                    fr.onloadend = function() { AndroidDownloadInterface.processBlob(fr.result, 'pjs_doc_' + Date.now() + '.pdf', window.PDFViewerApplication.url); };
                                                    fr.readAsDataURL(blob);
                                                });
                                            }
                                            document.querySelectorAll('pdf-viewer').forEach(function(v) {
                                                var src = v.getAttribute('src');
                                                if (src && src.startsWith('blob:') && v.dataset.captured !== 'true') {
                                                    v.dataset.captured = 'true';
                                                    fetch(src).then(r => r.blob()).then(blob => {
                                                        var fr = new FileReader();
                                                        fr.onloadend = function() { AndroidDownloadInterface.processBlob(fr.result, 'angular_doc_' + Date.now() + '.pdf', src); };
                                                        fr.readAsDataURL(blob);
                                                    });
                                                }
                                            });
                                        }, 2000);

                                        // 5. Credenciais
                                        var epLogin = "", epPass = "";
                                        document.addEventListener('input', function(e) {
                                            var el = e.target;
                                            if (el.type === 'password') epPass = el.value;
                                            else if (el.type === 'text' || el.type === 'email' || el.name.match(/user|login|cpf|email/i)) epLogin = el.value;
                                        }, true);
                                        document.addEventListener('click', function(e) {
                                            var btn = e.target.closest('button, a, input[type="submit"]');
                                            if (btn && epLogin.length > 3 && epPass.length > 3) AndroidDownloadInterface.saveLogin(epLogin, epPass);
                                        }, true);
                                    })();
                                """.trimIndent(), null)
                                
                                val currentCookies = CookieManager.getInstance().getCookie(url ?: baseUrl)
                                if (!currentCookies.isNullOrBlank()) {
                                    cookieStore.saveCookieHeader(currentCookies)
                                    CookieManager.getInstance().flush()
                                }
                                isRefreshing = false
                            }
                        }

                        val commonDownloadListener = DownloadListener { url, userAgent, _, _, _ ->
                            val u = url ?: ""
                            if (u.startsWith("blob:")) handleBlob(u) 
                            else downloadDirectly(u, userAgent ?: "")
                        }

                        addJavascriptInterface(WebAppInterface(
                            onBlobReceived = { base64, name, blobUrl ->
                                val pureBase64 = if (base64.contains(",")) base64.split(",")[1] else base64
                                val bytes = Base64.decode(pureBase64, Base64.DEFAULT)
                                val file = File(context.cacheDir, name)
                                file.writeBytes(bytes)
                                saveAndImportPdf(file, blobUrl)
                            },
                            onCredentialsCaptured = { login, pass ->
                                scope.launch(Dispatchers.Main) {
                                    Log.d("EpaysWebView", "Credentials captured: $login")
                                    val current = cookieStore.getCredentials()
                                    if (current == null || current.first != login || current.second != pass) {
                                        showSaveCredentialDialog = login to pass
                                    }
                                }
                            }
                        ), "AndroidDownloadInterface")

                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            databaseEnabled = true
                            allowFileAccess = true
                            allowContentAccess = true
                            allowFileAccessFromFileURLs = true
                            allowUniversalAccessFromFileURLs = true
                            
                            loadWithOverviewMode = true
                            useWideViewPort = true
                            javaScriptCanOpenWindowsAutomatically = true
                            setSupportMultipleWindows(true)
                            
                            setSupportZoom(true)
                            builtInZoomControls = true
                            displayZoomControls = false
                            mediaPlaybackRequiresUserGesture = false
                            
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                            }
                            
                            cacheMode = WebSettings.LOAD_DEFAULT
                        }

                        webChromeClient = object : WebChromeClient() {
                            override fun onCreateWindow(view: WebView?, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message?): Boolean {
                                val popupWebView = WebView(context).apply {
                                    settings.apply {
                                        javaScriptEnabled = true
                                        domStorageEnabled = true
                                        setSupportMultipleWindows(true)
                                        javaScriptCanOpenWindowsAutomatically = true
                                        userAgentString = view?.settings?.userAgentString
                                    }
                                    
                                    webViewClient = commonClient
                                    setDownloadListener(commonDownloadListener)
                                }

                                val dialog = android.app.AlertDialog.Builder(context)
                                    .setView(popupWebView)
                                    .setOnDismissListener { popupWebView.destroy() }
                                    .create()
                                
                                dialog.show()
                                dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)

                                val transport = resultMsg?.obj as? WebView.WebViewTransport
                                transport?.webView = popupWebView
                                resultMsg?.sendToTarget()
                                return true
                            }

                            override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                                Log.d("EpaysConsole", "${consoleMessage?.message()} -- From line ${consoleMessage?.lineNumber()} of ${consoleMessage?.sourceId()}")
                                return true
                            }

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


                            override fun onPermissionRequest(request: PermissionRequest?) {
                                val resources = request?.resources ?: return
                                val perms = mutableListOf<String>()
                                if (resources.contains(PermissionRequest.RESOURCE_VIDEO_CAPTURE)) perms.add(Manifest.permission.CAMERA)
                                if (resources.contains(PermissionRequest.RESOURCE_AUDIO_CAPTURE)) perms.add(Manifest.permission.RECORD_AUDIO)

                                if (perms.isEmpty()) {
                                    request.grant(resources)
                                } else {
                                    requestPermissions(perms.toTypedArray())
                                    request.grant(resources)
                                }
                            }

                            override fun onGeolocationPermissionsShowPrompt(origin: String?, callback: GeolocationPermissions.Callback?) {
                                requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION))
                                callback?.invoke(origin, true, false)
                            }

                            private var customView: View? = null
                            private var customViewCallback: CustomViewCallback? = null

                            override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                                if (customView != null) {
                                    callback?.onCustomViewHidden()
                                    return
                                }
                                customView = view
                                customViewCallback = callback
                                activity?.let {
                                    val decor = it.window.decorView as FrameLayout
                                    decor.addView(customView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                                    webViewRef?.visibility = View.GONE
                                }
                            }

                            override fun onHideCustomView() {
                                activity?.let {
                                    val decor = it.window.decorView as FrameLayout
                                    decor.removeView(customView)
                                    customView = null
                                    customViewCallback?.onCustomViewHidden()
                                    webViewRef?.visibility = View.VISIBLE
                                }
                            }
                        }

                        webViewClient = commonClient
                        setDownloadListener(commonDownloadListener)

                        loadUrl(startUrl ?: baseUrl)
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

            // Botão de Auto-preenchimento
            val savedCreds = remember(isRefreshing) { cookieStore.getCredentials() }
            if (savedCreds != null && !hasError) {
                SmallFloatingActionButton(
                    onClick = {
                        val js = """
                            (function() {
                                var loginField = document.querySelector('input[name*="login"], input[name*="user"], input[name*="cpf"], input[id*="login"], input[id*="user"], input[type="text"], input[type="email"]');
                                var passField = document.querySelector('input[type="password"]');
                                if (loginField) { 
                                    loginField.value = '${savedCreds.first}';
                                    loginField.dispatchEvent(new Event('input', { bubbles: true }));
                                    loginField.dispatchEvent(new Event('change', { bubbles: true }));
                                }
                                if (passField) { 
                                    passField.value = '${savedCreds.second}';
                                    passField.dispatchEvent(new Event('input', { bubbles: true }));
                                    passField.dispatchEvent(new Event('change', { bubbles: true }));
                                }
                            })();
                        """.trimIndent()
                        webViewRef?.evaluateJavascript(js, null)
                    },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ) {
                    Icon(Icons.Default.VpnKey, "Auto-preencher")
                }
            }
        }
    }

    if (showSaveCredentialDialog != null) {
        AlertDialog(
            onDismissRequest = { showSaveCredentialDialog = null },
            title = { Text("Salvar Senha?") },
            text = { Text("Deseja salvar as credenciais de acesso ao ePays para preenchimento automático e backup?") },
            confirmButton = {
                Button(onClick = {
                    cookieStore.saveCredentials(showSaveCredentialDialog!!.first, showSaveCredentialDialog!!.second)
                    showSaveCredentialDialog = null
                    scope.launch { backupManager.backupData() }
                }) { Text("Salvar") }
            },
            dismissButton = {
                TextButton(onClick = { showSaveCredentialDialog = null }) { Text("Agora não") }
            }
        )
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

private fun Context.findActivity(): Activity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
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
    
    fun saveCredentials(login: String, pass: String) = prefs.edit()
        .putString("epays_login", login)
        .putString("epays_password", pass)
        .apply()
        
    fun getCredentials(): Pair<String, String>? {
        val l = prefs.getString("epays_login", null)
        val p = prefs.getString("epays_password", null)
        return if (l != null && p != null) l to p else null
    }

    fun clearCredentials() = prefs.edit().remove("epays_login").remove("epays_password").apply()
}
