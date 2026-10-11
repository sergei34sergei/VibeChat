package com.vibe.chat

import android.Manifest
import android.content.pm.PackageManager
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat

private val AlexFullBackground = Color(0xFF080D1B)
private val AlexFullCyan = Color(0xFF35E0F5)
private val AlexFullPurple = Color(0xFF963CFF)

private const val ALEX_PAGE =
    "https://sergei34sergei.github.io/VibeChat/alex.html"

@Composable
fun AlexVoiceScreen(onBack: () -> Unit = {}) {
    var webView by remember { mutableStateOf<WebView?>(null) }
    var loading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf(false) }
    var pendingRequest by remember { mutableStateOf<PermissionRequest?>(null) }

    val microphonePermission =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { granted ->
            val request = pendingRequest
            pendingRequest = null

            if (request == null) return@rememberLauncherForActivityResult

            if (granted && request.resources.contains(
                    PermissionRequest.RESOURCE_AUDIO_CAPTURE
                ) && isTrustedDidHost(request.origin.host.orEmpty())
            ) {
                request.grant(
                    arrayOf(PermissionRequest.RESOURCE_AUDIO_CAPTURE)
                )
            } else {
                request.deny()
            }
        }

    BackHandler {
        onBack()
    }

    DisposableEffect(Unit) {
        onDispose {
            pendingRequest?.deny()
            pendingRequest = null

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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AlexFullBackground)
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                WebView(context).apply {
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
                            loading = true
                            loadError = false
                        }

                        override fun onPageFinished(
                            view: WebView?,
                            url: String?
                        ) {
                            loading = false
                        }

                        override fun onReceivedError(
                            view: WebView?,
                            request: WebResourceRequest?,
                            error: android.webkit.WebResourceError?
                        ) {
                            if (request?.isForMainFrame == true) {
                                loading = false
                                loadError = true
                            }
                        }

                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): Boolean {
                            val uri = request?.url ?: return true

                            // Разрешаем основную страницу Vibe Chat.
                            // Внешние переходы не открываем внутри приложения.
                            return uri.scheme != "https" ||
                                uri.host != "sergei34sergei.github.io"
                        }
                    }

                    webChromeClient = object : WebChromeClient() {
                        override fun onPermissionRequest(
                            request: PermissionRequest
                        ) {
                            post {
                                val trusted = isTrustedDidHost(
                                    request.origin.host.orEmpty()
                                )
                                val wantsAudio = request.resources.contains(
                                    PermissionRequest.RESOURCE_AUDIO_CAPTURE
                                )

                                if (!trusted || !wantsAudio) {
                                    request.deny()
                                    return@post
                                }

                                val hasPermission =
                                    ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.RECORD_AUDIO
                                    ) == PackageManager.PERMISSION_GRANTED

                                if (hasPermission) {
                                    request.grant(
                                        arrayOf(
                                            PermissionRequest.RESOURCE_AUDIO_CAPTURE
                                        )
                                    )
                                } else {
                                    pendingRequest = request
                                    microphonePermission.launch(
                                        Manifest.permission.RECORD_AUDIO
                                    )
                                }
                            }
                        }

                        override fun onPermissionRequestCanceled(
                            request: PermissionRequest
                        ) {
                            if (pendingRequest === request) {
                                pendingRequest = null
                            }
                        }
                    }

                    loadUrl(ALEX_PAGE)
                }
            },
            update = {
                webView = it
            }
        )

        if (loading && !loadError) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(AlexFullBackground.copy(alpha = 0.94f))
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator(color = AlexFullCyan)
                Spacer(Modifier.height(14.dp))
                Text(
                    "Алекс подключается...",
                    color = Color.White,
                    fontSize = 16.sp
                )
            }
        }

        if (loadError) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Не удалось загрузить Алекса",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    "Проверь интернет и доступность страницы alex.html.",
                    color = Color.LightGray
                )

                Button(
                    onClick = {
                        loading = true
                        loadError = false
                        webView?.reload()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AlexFullPurple
                    )
                ) {
                    Text("Попробовать снова")
                }
            }
        }

        Button(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xDD171D38)
            )
        ) {
            Text("← Назад", color = Color.White)
        }
    }
}

private fun isTrustedDidHost(host: String): Boolean {
    return host == "d-id.com" || host.endsWith(".d-id.com")
}
