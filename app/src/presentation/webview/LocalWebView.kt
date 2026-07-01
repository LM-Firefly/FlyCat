/*
 * This file is part of YumeBox.
 *
 * YumeBox is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * Copyright (c)  YumeYucca 2025 - Present
 *
 */

package com.github.yumelira.yumebox.presentation.webview

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.github.yumelira.yumebox.BuildConfig
import com.github.yumelira.yumebox.WebViewActivity
import dev.oom_wg.purejoy.mlang.MLang
import timber.log.Timber
import top.yukonga.miuix.kmp.basic.Text

private object NoOpWebViewClient : WebViewClient()

private object NoOpWebChromeClient : WebChromeClient()

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LocalWebView(
    initialUrl: String,
    modifier: Modifier = Modifier,
    enableDebug: Boolean = BuildConfig.DEBUG,
    onPageFinished: (String) -> Unit = {},
    onPageError: (String, String) -> Unit = { _, _ -> },
) {
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val webViewRef = remember { mutableStateOf<WebView?>(null) }

    LaunchedEffect(enableDebug) {
        try {
            WebView.setWebContentsDebuggingEnabled(enableDebug)
        } catch (_: Exception) {}
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    webViewRef.value?.onPause()
                }

                Lifecycle.Event.ON_RESUME -> {
                    webViewRef.value?.onResume()
                }

                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            webViewRef.value?.let { webView ->
                try {
                    webView.stopLoading()
                    webView.onPause()
                    webView.visibility = View.GONE
                    webView.webChromeClient = NoOpWebChromeClient
                    webView.webViewClient = NoOpWebViewClient
                    try {
                        webView.removeJavascriptInterface("interface_name")
                    } catch (_: Exception) {
                    }
                    webView.clearCache(true)
                    webView.clearFormData()
                    webView.clearHistory()
                    webView.loadUrl("about:blank")
                    webView.removeAllViews()
                    (webView.parent as? ViewGroup)?.removeView(webView)
                    webView.destroy()
                } catch (e: Exception) {
                    Timber.w(e, "Error destroying WebView")
                }
            }
            webViewRef.value = null
        }
    }

    if (initialUrl.isEmpty()) {
        Box(
            modifier = modifier.windowInsetsPadding(WindowInsets.safeDrawing),
            contentAlignment = Alignment.Center,
        ) {
            Text(MLang.Component.WebView.InvalidUrl)
        }
        return
    }

    AndroidView(
        factory = { ctx ->
            createWebView(ctx, initialUrl, onPageFinished, onPageError).also {
                webViewRef.value = it
            }
        },
        modifier = modifier.windowInsetsPadding(WindowInsets.safeDrawing),
    )
}

@SuppressLint("SetJavaScriptEnabled")
private fun createWebView(
    context: Context,
    initialUrl: String,
    onPageFinished: (String) -> Unit,
    onPageError: (String, String) -> Unit,
): WebView {
    val activity = context as? WebViewActivity

    return WebView(context).apply {
        val isLocalFileUrl = initialUrl.startsWith("file://", ignoreCase = true)
        layoutParams =
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )

        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true

            allowFileAccess = isLocalFileUrl
            allowContentAccess = isLocalFileUrl

            setSupportZoom(true)
            builtInZoomControls = false
            displayZoomControls = false

            useWideViewPort = true
            loadWithOverviewMode = true
            mediaPlaybackRequiresUserGesture = true

            cacheMode = WebSettings.LOAD_DEFAULT

            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        }

        webViewClient =
            object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?,
                ): Boolean {
                    val scheme = request?.url?.scheme

                    if (
                        scheme != null && scheme != "http" && scheme != "https" && scheme != "file"
                    ) {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, request.url)
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(intent)
                            return true
                        } catch (_: Exception) {
                            return true
                        }
                    }

                    return false
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    onPageFinished(url ?: "")
                }

                @Suppress("DEPRECATION")
                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: WebResourceError?,
                ) {
                    @Suppress("DEPRECATION") super.onReceivedError(view, request, error)
                    if (request?.isForMainFrame == true) {
                        val errorUrl = request.url?.toString() ?: "unknown"
                        val errorCode = error?.errorCode ?: -1
                        val errorMessage = error?.description?.toString() ?: "unknown error"
                        onPageError(errorUrl, "Error $errorCode: $errorMessage")
                    }
                }

                override fun onReceivedHttpError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    errorResponse: WebResourceResponse?,
                ) {
                    super.onReceivedHttpError(view, request, errorResponse)
                    if (request?.isForMainFrame == true) {
                        val errorUrl = request.url?.toString() ?: "unknown"
                        val statusCode = errorResponse?.statusCode ?: -1
                        if (statusCode == 404) {
                            onPageError(errorUrl, "404 Not Found")
                        }
                    }
                }

                @Deprecated("Deprecated in Java")
                override fun onReceivedError(
                    view: WebView?,
                    errorCode: Int,
                    description: String?,
                    failingUrl: String?,
                ) {
                    @Suppress("DEPRECATION")
                    super.onReceivedError(view, errorCode, description, failingUrl)
                }
            }

        webChromeClient =
            object : WebChromeClient() {
                override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean = true

                override fun onShowFileChooser(
                    webView: WebView?,
                    filePathCallback: ValueCallback<Array<Uri>>?,
                    fileChooserParams: FileChooserParams?,
                ): Boolean {
                    if (activity == null) {
                        filePathCallback?.onReceiveValue(null)
                        return true
                    }
                    val mimeTypes = fileChooserParams?.acceptTypes ?: arrayOf("*/*")
                    activity.launchFilePicker(filePathCallback, mimeTypes)
                    return true
                }
            }

        loadUrl(initialUrl)
    }
}
