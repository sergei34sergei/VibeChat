package com.vibe.chat

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat

private val AlexScreenBackground = Color(0xFF080D1B)
private val AlexScreenCyan = Color(0xFF35E0F5)
private val AlexScreenPurple = Color(0xFF963CFF)

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun AlexVoiceScreen(
    onBack: () -> Unit = {}
) {
    val context = LocalContext.current

    var webView by remember {
        mutableStateOf<WebView?>(null)
    }

    var pageLoading by remember {
        mutableStateOf(true)
    }

    var pageError by remember {
        mutableStateOf(false)
    }

    var pendingPermission by remember {
        mutableStateOf<PermissionRequest?>(null)
    }

    val microphoneLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            val request = pendingPermission
            pendingPermission = null

            if (granted && request != null) {
                val audioResources = request.resources.filter {
                    it == PermissionRequest.RESOURCE_AUDIO_CAPTURE
                }.toTypedArray()

                if (audioResources.isNotEmpty()) {
                    request.grant(audioResources)
                } else {
                    request.deny()
                }
            } else {
                request?.deny()
            }
        }

    BackHandler {
        val currentWebView = webView

        if (
            currentWebView != null &&
            currentWebView.canGoBack()
        ) {
            currentWebView.goBack()
        } else {
            onBack()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            pendingPermission?.deny()
            pendingPermission = null

            webView?.apply {
                stopLoading()
                webChromeClient = null
                webViewClient = WebViewClient()
                loadUrl("about:blank")
                removeAllViews()
                destroy()
            }

            webView = null
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = AlexScreenBackground
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(AlexScreenBackground)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF10182D))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onBack,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF202B45)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("← Назад", color = Color.White)
                }

                Spacer(Modifier.width(12.dp))

                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "Алекс",
                        color = Color.White,
                        fontSize = 21.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = "Голосовой AI-помощник",
                        color = AlexScreenCyan,
                        fontSize = 12.sp
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
                    .background(AlexScreenBackground),
                contentAlignment = Alignment.Center
            ) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { viewContext ->
                        WebView(viewContext).apply {
                            webView = this

                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.mediaPlaybackRequiresUserGesture = false
                            settings.javaScriptCanOpenWindowsAutomatically = true

                            setBackgroundColor(
                                android.graphics.Color.rgb(8, 13, 27)
                            )

                            webViewClient = object : WebViewClient() {
                                override fun onPageStarted(
                                    view: WebView?,
                                    url: String?,
                                    favicon: android.graphics.Bitmap?
                                ) {
                                    pageLoading = true
                                    pageError = false
                                }

                                override fun onPageFinished(
                                    view: WebView?,
                                    url: String?
                                ) {
                                    pageLoading = false
                                }

                                override fun onReceivedError(
                                    view: WebView?,
                                    request: android.webkit.WebResourceRequest?,
                                    error: android.webkit.WebResourceError?
                                ) {
                                    if (request?.isForMainFrame == true) {
                                        pageLoading = false
                                        pageError = true
                                    }
                                }
                            }

                            webChromeClient = object : WebChromeClient() {
                                override fun onPermissionRequest(
                                    request: PermissionRequest
                                ) {
                                    post {
                                        val wantsAudio =
                                            request.resources.contains(
                                                PermissionRequest
                                                    .RESOURCE_AUDIO_CAPTURE
                                            )

                                        if (!wantsAudio) {
                                            request.deny()
                                            return@post
                                        }

                                        val hasMicrophone =
                                            ContextCompat.checkSelfPermission(
                                                context,
                                                Manifest.permission.RECORD_AUDIO
                                            ) == PackageManager.PERMISSION_GRANTED

                                        if (hasMicrophone) {
                                            val allowed =
                                                request.resources.filter {
                                                    it == PermissionRequest
                                                        .RESOURCE_AUDIO_CAPTURE
                                                }.toTypedArray()

                                            if (allowed.isNotEmpty()) {
                                                request.grant(allowed)
                                            } else {
                                                request.deny()
                                            }
                                        } else {
                                            pendingPermission = request
                                            microphoneLauncher.launch(
                                                Manifest.permission.RECORD_AUDIO
                                            )
                                        }
                                    }
                                }
                            }

                            loadUrl(
                                "https://sergei34sergei.github.io/VibeChat/alex.html"
                            )
                        }
                    }
                )

                if (pageLoading) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(
                            color = AlexScreenCyan
                        )

                        Spacer(Modifier.height(12.dp))

                        Text(
                            text = "Загружаю Алекса...",
                            color = Color.White
                        )
                    }
                }

                if (pageError) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Не удалось загрузить Алекса",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(Modifier.height(10.dp))

                        Text(
                            text = "Проверь интернет и доступность страницы GitHub Pages.",
                            color = Color.LightGray
                        )

                        Spacer(Modifier.height(14.dp))

                        Button(
                            onClick = {
                                pageError = false
                                pageLoading = true
                                webView?.reload()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AlexScreenPurple
                            )
                        ) {
                            Text("Попробовать снова")
                        }
                    }
                }
            }
        }
    }
}
