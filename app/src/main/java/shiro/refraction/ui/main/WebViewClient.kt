package shiro.refraction.ui.main

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Message
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import shiro.refraction.data.model.NetworkRequest
import shiro.refraction.data.model.RequestSource

class RefractionWebChromeClient(
    private val onProgressChanged: (Int) -> Unit,
    private val userAgentProvider: () -> String = { "" }
) : WebChromeClient() {

    override fun onProgressChanged(view: WebView?, newProgress: Int) {
        onProgressChanged(newProgress)
    }

    override fun onCreateWindow(
        view: WebView?,
        isDialog: Boolean,
        isUserGesture: Boolean,
        resultMsg: Message?
    ): Boolean {
        val hostView = view ?: return false
        val popup = WebView(hostView.context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            val ua = userAgentProvider()
            if (ua.isNotEmpty()) settings.userAgentString = ua
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(v: WebView, request: WebResourceRequest): Boolean {
                    hostView.loadUrl(request.url.toString())
                    v.destroy()
                    return true
                }
            }
        }
        (resultMsg?.obj as? WebView.WebViewTransport)?.webView = popup
        resultMsg?.sendToTarget()
        return true
    }
}

class RefractionWebViewClient(
    private val onPageFinished: (String) -> Unit,
    private val onPageStarted: (String) -> Unit = {},
    private val isRecording: () -> Boolean = { false },
    private val onRequestIntercepted: ((NetworkRequest) -> Unit)? = null
) : WebViewClient() {

    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
        val uri = request?.url ?: return false
        val scheme = uri.scheme?.lowercase() ?: return false
        val context = view?.context ?: return false

        if (scheme == "http" || scheme == "https") {
            return false
        }

        if (scheme == "intent") {
            return handleIntentScheme(view, uri)
        }

        if (scheme in UTILITY_SCHEMES) {
            return try {
                context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                true
            } catch (e: Exception) {
                false
            }
        }

        // App deep links (fb://, google://, ...) never reach a native app here,
        // so drop them to keep the session inside the WebView.
        return true
    }

    private fun handleIntentScheme(view: WebView, uri: Uri): Boolean {
        return try {
            val intent = Intent.parseUri(uri.toString(), Intent.URI_INTENT_SCHEME)
            val fallback = intent.getStringExtra("browser_fallback_url")
            if (fallback != null) {
                view.loadUrl(fallback)
                return true
            }
            val data = intent.dataString
            if (data != null && data.startsWith("http")) {
                view.loadUrl(data)
                return true
            }
            false
        } catch (e: Exception) {
            false
        }
    }

    override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
        url?.let { onPageStarted(it) }
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        url?.let { onPageFinished(it) }
    }

    override fun shouldInterceptRequest(
        view: WebView?,
        request: WebResourceRequest?
    ): WebResourceResponse? {
        if (isRecording() && onRequestIntercepted != null && request != null) {
            onRequestIntercepted(
                NetworkRequest(
                    method = request.method,
                    url = request.url.toString(),
                    headers = request.requestHeaders ?: emptyMap(),
                    source = RequestSource.RESOURCE
                )
            )
        }
        return null
    }

    private companion object {
        val UTILITY_SCHEMES = setOf("mailto", "tel", "sms", "smsto", "whatsapp", "tg")
    }
}
