package com.example.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.PermissionRequest
import android.webkit.ServiceWorkerController
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SignalWifiOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.R
import com.example.ui.theme.AlertRed
import com.example.ui.theme.DarkGunmetal
import com.example.ui.theme.RadarGreen
import com.example.ui.theme.TacticalGreen
import com.example.ui.theme.TacticalSurface
import com.example.ui.theme.TacticalSurfaceVariant
import com.example.ui.theme.TextMuted
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

const val URL_GAME = "https://playigi.com/game/?touch=1"
const val URL_DOWNLOAD = "https://playigi.com/download.html"

/**
 * Checks active network connectivity.
 */
fun isNetworkAvailable(context: Context): Boolean {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
    val activeNetwork = cm.activeNetwork ?: return false
    val caps = cm.getNetworkCapabilities(activeNetwork) ?: return false
    return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}

/**
 * Maps resource extensions to standard MIME types.
 */
fun getMimeType(path: String): String {
    val clean = path.substringBefore("?").substringBefore("#").lowercase()
    return when {
        clean.endsWith(".html") || clean.endsWith(".htm") -> "text/html"
        clean.endsWith(".js") || clean.endsWith(".mjs") -> "application/javascript"
        clean.endsWith(".css") -> "text/css"
        clean.endsWith(".wasm") -> "application/wasm"
        clean.endsWith(".json") -> "application/json"
        clean.endsWith(".webmanifest") -> "application/manifest+json"
        clean.endsWith(".woff") -> "font/woff"
        clean.endsWith(".woff2") -> "font/woff2"
        clean.endsWith(".png") -> "image/png"
        clean.endsWith(".jpg") || clean.endsWith(".jpeg") -> "image/jpeg"
        clean.endsWith(".svg") -> "image/svg+xml"
        clean.endsWith(".ico") -> "image/x-icon"
        clean.endsWith(".mp3") -> "audio/mpeg"
        clean.endsWith(".ogg") -> "audio/ogg"
        clean.endsWith(".wav") -> "audio/wav"
        clean.endsWith(".gz") -> "application/gzip"
        clean.endsWith(".mp4") -> "video/mp4"
        else -> "application/octet-stream"
    }
}

/**
 * Computes a safe unique cache filename for any URL.
 */
fun sanitizeUrlToFilename(url: String): String {
    val md = MessageDigest.getInstance("MD5")
    val digest = md.digest(url.toByteArray(Charsets.UTF_8))
    return digest.joinToString("") { "%02x".format(it) }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun IgiMobileScreen() {
    val context = LocalContext.current
    val activity = context as? Activity

    val currentUrl = remember { URL_GAME }
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var loadProgress by remember { mutableIntStateOf(0) }
    var hasError by remember { mutableStateOf(false) }
    var showExitDialog by remember { mutableStateOf(false) }
    var customView by remember { mutableStateOf<View?>(null) }
    var customViewCallback by remember { mutableStateOf<WebChromeClient.CustomViewCallback?>(null) }

    // Configure ServiceWorker caching for offline capabilities
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                val swController = ServiceWorkerController.getInstance()
                swController.serviceWorkerWebSettings.cacheMode = WebSettings.LOAD_CACHE_ELSE_NETWORK
                swController.serviceWorkerWebSettings.allowContentAccess = true
                swController.serviceWorkerWebSettings.allowFileAccess = true
            } catch (_: Exception) {
                // Not supported on all vendor devices
            }
        }
    }

    // Intercept back button to avoid accidental exits during gameplay
    BackHandler {
        val webView = webViewInstance
        if (customView != null) {
            customViewCallback?.onCustomViewHidden()
            customView = null
        } else if (webView != null && webView.canGoBack()) {
            webView.goBack()
        } else {
            showExitDialog = true
        }
    }

    // Keep screen awake during gameplay
    DisposableEffect(Unit) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            webViewInstance?.destroy()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("igi_root_container")
    ) {
        // Main Game WebView
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .testTag("igi_game_webview"),
            factory = { ctx ->
                WebView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    setBackgroundColor(android.graphics.Color.BLACK)
                    isFocusable = true
                    isFocusableInTouchMode = true
                    setLayerType(View.LAYER_TYPE_HARDWARE, null)

                    // Enable cookies
                    val cookieManager = CookieManager.getInstance()
                    cookieManager.setAcceptCookie(true)
                    cookieManager.setAcceptThirdPartyCookies(this, true)

                    // WebSettings tailored for WebAssembly, WebGL 3D, and Instant Offline Startup
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        databaseEnabled = true
                        mediaPlaybackRequiresUserGesture = false
                        allowFileAccess = true
                        allowContentAccess = true
                        loadsImagesAutomatically = true
                        useWideViewPort = true
                        loadWithOverviewMode = true
                        mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW

                        // In addition to our asset interceptor, enable local cache fallback
                        cacheMode = WebSettings.LOAD_DEFAULT

                        // Disable zoom controls to prevent multi-touch interference with virtual controls
                        setSupportZoom(false)
                        builtInZoomControls = false
                        displayZoomControls = false

                        defaultTextEncodingName = "utf-8"
                    }

                    webChromeClient = object : WebChromeClient() {
                        override fun onProgressChanged(view: WebView?, newProgress: Int) {
                            loadProgress = newProgress
                            if (newProgress >= 90) {
                                isLoading = false
                            }
                        }

                        override fun onPermissionRequest(request: PermissionRequest?) {
                            request?.resources?.let {
                                request.grant(it)
                            }
                        }

                        override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                            customView = view
                            customViewCallback = callback
                        }

                        override fun onHideCustomView() {
                            customView = null
                            customViewCallback = null
                        }

                        override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                            return true
                        }
                    }

                    webViewClient = object : WebViewClient() {
                        /**
                         * Offline Interceptor: Serves bundled assets (HTML, JS, CSS, fonts, workers)
                         * directly from local disk and APK assets, eliminating ANY need for internet on startup!
                         */
                        override fun shouldInterceptRequest(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): WebResourceResponse? {
                            val url = request?.url ?: return null
                            val host = url.host ?: return null
                            if (!host.contains("playigi.com")) {
                                return super.shouldInterceptRequest(view, request)
                            }

                            val path = url.path ?: ""
                            val isMainDocument = (path == "/game" || path == "/game/" || path == "/game/index.html") &&
                                    (url.query?.contains("touch=1") == true || url.query.isNullOrEmpty())

                            // 1. Main Document HTML: always serve instantly from local bundled assets
                            if (isMainDocument) {
                                return try {
                                    val stream = ctx.assets.open("game/index.html")
                                    WebResourceResponse(
                                        "text/html",
                                        "UTF-8",
                                        200,
                                        "OK",
                                        mapOf(
                                            "Access-Control-Allow-Origin" to "*",
                                            "Cache-Control" to "public, max-age=31536000",
                                            "Content-Type" to "text/html; charset=UTF-8"
                                        ),
                                        stream
                                    )
                                } catch (_: Exception) {
                                    super.shouldInterceptRequest(view, request)
                                }
                            }

                            // 2. Map path to pre-bundled game assets in APK
                            val assetSubPath = when {
                                path.startsWith("/game/assets/") -> "game/assets/" + path.removePrefix("/game/assets/")
                                path.startsWith("/game/icons/") -> "game/icons/" + path.removePrefix("/game/icons/")
                                path == "/game/manifest.webmanifest" -> "game/manifest.webmanifest"
                                path == "/game/coopbodies.json" -> "game/coopbodies.json"
                                path == "/game/sw.js" -> "game/sw.js"
                                path.startsWith("/game/") && !path.contains("..") -> "game/" + path.removePrefix("/game/")
                                else -> null
                            }

                            if (assetSubPath != null) {
                                try {
                                    val assetStream = ctx.assets.open(assetSubPath)
                                    val mimeType = getMimeType(assetSubPath)
                                    return WebResourceResponse(
                                        mimeType,
                                        if (mimeType.startsWith("text/") || mimeType.contains("javascript") || mimeType.contains("json")) "UTF-8" else null,
                                        200,
                                        "OK",
                                        mapOf(
                                            "Access-Control-Allow-Origin" to "*",
                                            "Cache-Control" to "public, max-age=31536000",
                                            "Content-Type" to mimeType
                                        ),
                                        assetStream
                                    )
                                } catch (_: Exception) {
                                    // Not in bundled assets, try dynamic cache below
                                }
                            }

                            // 3. Persistent dynamic disk cache in filesDir/game_cache/
                            val cacheDir = File(ctx.filesDir, "game_cache")
                            val cacheFile = File(cacheDir, sanitizeUrlToFilename(url.toString()))
                            if (cacheFile.exists() && cacheFile.length() > 0) {
                                try {
                                    val mimeType = getMimeType(path)
                                    return WebResourceResponse(
                                        mimeType,
                                        if (mimeType.startsWith("text/") || mimeType.contains("javascript") || mimeType.contains("json")) "UTF-8" else null,
                                        200,
                                        "OK",
                                        mapOf(
                                            "Access-Control-Allow-Origin" to "*",
                                            "Cache-Control" to "public, max-age=31536000"
                                        ),
                                        FileInputStream(cacheFile)
                                    )
                                } catch (_: Exception) {
                                    // Fallthrough
                                }
                            }

                            // 4. If online, fetch asset and persist in dynamic disk cache
                            if (isNetworkAvailable(ctx)) {
                                try {
                                    val connection = (URL(url.toString()).openConnection() as HttpURLConnection).apply {
                                        requestMethod = "GET"
                                        connectTimeout = 6000
                                        readTimeout = 10000
                                        setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
                                        setRequestProperty("Referer", "https://playigi.com/game/?touch=1")
                                    }
                                    if (connection.responseCode in 200..299) {
                                        val bytes = connection.inputStream.readBytes()
                                        cacheDir.mkdirs()
                                        cacheFile.writeBytes(bytes)
                                        val mimeType = connection.contentType?.split(";")?.firstOrNull()?.trim() ?: getMimeType(path)
                                        return WebResourceResponse(
                                            mimeType,
                                            connection.contentEncoding ?: "UTF-8",
                                            200,
                                            "OK",
                                            mapOf(
                                                "Access-Control-Allow-Origin" to "*",
                                                "Cache-Control" to "public, max-age=31536000"
                                            ),
                                            ByteArrayInputStream(bytes)
                                        )
                                    }
                                } catch (_: Exception) {
                                    // Network request failed
                                }
                            }

                            return super.shouldInterceptRequest(view, request)
                        }

                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): Boolean {
                            val target = request?.url?.toString() ?: return false
                            if (target.startsWith("http://") || target.startsWith("https://")) {
                                return false // Load internally
                            }
                            // External intents
                            return try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(target))
                                ctx.startActivity(intent)
                                true
                            } catch (_: Exception) {
                                false
                            }
                        }

                        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                            isLoading = true
                            hasError = false
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            isLoading = false
                            hasError = false
                        }

                        override fun onReceivedError(
                            view: WebView?,
                            request: WebResourceRequest?,
                            error: WebResourceError?
                        ) {
                            // Since all app shell assets are bundled, main page loads never fail offline.
                            // Only set hasError if main frame couldn't be loaded at all
                            if (request?.isForMainFrame == true && view?.url == null) {
                                hasError = true
                                isLoading = false
                            }
                        }
                    }

                    loadUrl(currentUrl)
                    webViewInstance = this
                }
            },
            update = { webView ->
                webViewInstance = webView
            }
        )

        // Custom full-screen video/canvas layer
        if (customView != null) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    FrameLayout(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        addView(customView)
                    }
                }
            )
        }

        // Tactical HUD Loading Overlay (Smoothly fades out once game canvas is ready)
        AnimatedVisibility(
            visible = isLoading && !hasError,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .testTag("tactical_loading_screen"),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(110.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .border(2.dp, TacticalGreen, RoundedCornerShape(16.dp))
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_igi_logo),
                            contentDescription = "IGI MOBILE Logo",
                            modifier = Modifier
                                .size(100.dp)
                                .clip(RoundedCornerShape(12.dp))
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Text(
                        text = "PROJECT I.G.I. MOBILE",
                        color = RadarGreen,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 3.sp
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "LAUNCHING OFFLINE WEBASSEMBLY ENGINE // INSTANT START",
                        color = TextMuted,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    LinearProgressIndicator(
                        progress = { loadProgress / 100f },
                        modifier = Modifier
                            .fillMaxWidth(0.5f)
                            .height(6.dp)
                            .clip(CircleShape),
                        color = TacticalGreen,
                        trackColor = TacticalSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "READY: $loadProgress%",
                        color = TacticalGreen,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        // Tactical Error Fallback (Only in catastrophic cases)
        if (hasError) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xF0050806))
                    .testTag("tactical_error_screen"),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = TacticalSurface),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .border(1.dp, AlertRed, RoundedCornerShape(16.dp))
                        .padding(2.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SignalWifiOff,
                            contentDescription = "Alert",
                            tint = AlertRed,
                            modifier = Modifier.size(44.dp)
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "INITIALIZATION RECONNECT",
                            color = AlertRed,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Tap below to reload the game engine from bundled offline storage.",
                            color = TextMuted,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        Button(
                            onClick = {
                                hasError = false
                                isLoading = true
                                webViewInstance?.loadUrl(currentUrl)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = TacticalGreen),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("retry_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                tint = Color.Black,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "START GAME",
                                color = Color.Black,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }

        // Mission Exit Confirmation Dialog
        if (showExitDialog) {
            AlertDialog(
                onDismissRequest = { showExitDialog = false },
                containerColor = DarkGunmetal,
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = TacticalGreen,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            "ABORT MISSION?",
                            color = TacticalGreen,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 18.sp
                        )
                    }
                },
                text = {
                    Text(
                        "Are you sure you want to exit IGI MOBILE? Any unsaved progress will be lost.",
                        color = Color.White,
                        fontSize = 14.sp
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showExitDialog = false
                            activity?.finish()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AlertRed),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.testTag("dialog_exit_button")
                    ) {
                        Text("EXIT GAME", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    OutlinedButton(
                        onClick = { showExitDialog = false },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.testTag("dialog_resume_button")
                    ) {
                        Text("RESUME", color = TacticalGreen)
                    }
                }
            )
        }
    }
}
