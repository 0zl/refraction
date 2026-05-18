package shiro.refraction.ui.main

import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import shiro.refraction.data.model.NetworkRequest
import shiro.refraction.data.model.RequestSource

class RefractionWebChromeClient(
    private val onProgressChanged: (Int) -> Unit
) : WebChromeClient() {

    override fun onProgressChanged(view: WebView?, newProgress: Int) {
        onProgressChanged(newProgress)
    }
}

class RefractionWebViewClient(
    private val onPageFinished: (String) -> Unit,
    private val onPageStarted: (String) -> Unit = {},
    private val isRecording: () -> Boolean = { false },
    private val onRequestIntercepted: ((NetworkRequest) -> Unit)? = null
) : WebViewClient() {

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
}
