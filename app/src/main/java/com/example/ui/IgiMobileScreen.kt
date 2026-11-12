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
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
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
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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

private const val PREFS_NAME = "igi_mobile_prefs"
private const val KEY_LAUNCH_COUNT = "launch_count"
private const val URL_DOWNLOAD = "https://playigi.com/download.html"
private const val URL_GAME = "https://playigi.com/game/?touch=1"

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun IgiMobileScreen() {
    val context = LocalContext.current
    val activity = context as? Activity

    // Determine initial target URL based on launch count
    val prefs = remember { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }
    var currentUrl by remember {
        val launchCount = prefs.getInt(KEY_LAUNCH_COUNT, 0)
        val initialUrl = if (launchCount == 0) URL_DOWNLOAD else URL_GAME
        // Update launch count
        prefs.edit().putInt(KEY_LAUNCH_COUNT, launchCount + 1).apply()
        mutableStateOf(initialUrl)
    }

    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var loadProgress by remember { mutableIntStateOf(0) }
    var hasError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var showExitDialog by remember { mutableStateOf(false) }
    var customView by remember { mutableStateOf<View?>(null) }
    var customViewCallback by remember { mutableStateOf<WebChromeClient.CustomViewCallback?>(null) }

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

    // Keep screen awake for gaming
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

                    // Cookie setup
                    val cookieManager = CookieManager.getInstance()
                    cookieManager.setAcceptCookie(true)
                    cookieManager.setAcceptThirdPartyCookies(this, true)

                    // WebSettings tailored for WebAssembly & 3D WebGL HTML5 games
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
                        cacheMode = WebSettings.LOAD_DEFAULT
                        mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW

                        // Disable zoom controls to prevent multi-touch interference with game touch joystick
                        setSupportZoom(false)
                        builtInZoomControls = false
                        displayZoomControls = false

                        // Performance & viewport
                        defaultTextEncodingName = "utf-8"
                    }

                    webChromeClient = object : WebChromeClient() {
                        override fun onProgressChanged(view: WebView?, newProgress: Int) {
                            loadProgress = newProgress
                            if (newProgress >= 95) {
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
                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): Boolean {
                            val url = request?.url?.toString() ?: return false
                            if (url.startsWith("http://") || url.startsWith("https://")) {
                                return false // Load internally
                            }
                            // External protocols (tel, mailto, etc.)
                            return try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                ctx.startActivity(intent)
                                true
                            } catch (e: Exception) {
                                false
                            }
                        }

                        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                            isLoading = true
                            hasError = false
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            isLoading = false
                        }

                        override fun onReceivedError(
                            view: WebView?,
                            request: WebResourceRequest?,
                            error: WebResourceError?
                        ) {
                            if (request?.isForMainFrame == true) {
                                hasError = true
                                errorMessage = error?.description?.toString() ?: "Network Connection Error"
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

        // Tactical HUD Loading Overlay (Shown during initial page load)
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
                        text = if (currentUrl.contains("download")) {
                            "DOWNLOADING GAME PROTOCOLS & ASSETS..."
                        } else {
                            "STARTING WEBASSEMBLY ENGINE // TACTICAL MISSION"
                        },
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
                        text = "INITIALIZING: $loadProgress%",
                        color = TacticalGreen,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        // Tactical Error / Offline Overlay
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
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Connection Alert",
                            tint = AlertRed,
                            modifier = Modifier.size(44.dp)
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "COMMUNICATION SIGNAL LOST",
                            color = AlertRed,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Unable to connect to IGI servers. Check your internet connection and try again.",
                            color = TextMuted,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Button(
                                onClick = {
                                    hasError = false
                                    isLoading = true
                                    webViewInstance?.reload()
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
                                    "RECONNECT",
                                    color = Color.Black,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }

                            OutlinedButton(
                                onClick = {
                                    hasError = false
                                    isLoading = true
                                    val nextUrl = if (currentUrl == URL_DOWNLOAD) URL_GAME else URL_DOWNLOAD
                                    currentUrl = nextUrl
                                    webViewInstance?.loadUrl(nextUrl)
                                },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = TacticalGreen),
                                modifier = Modifier.testTag("switch_mode_button")
                            ) {
                                Text(
                                    if (currentUrl == URL_DOWNLOAD) "OPEN GAME DIRECTLY" else "OPEN DOWNLOAD PAGE",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp
                                )
                            }
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
