package shiro.refraction.ui.main

import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient

class RefractionWebChromeClient(
    private val onProgressChanged: (Int) -> Unit
) : WebChromeClient() {

    override fun onProgressChanged(view: WebView?, newProgress: Int) {
        onProgressChanged(newProgress)
    }
}

class RefractionWebViewClient(
    private val onPageFinished: (String) -> Unit
) : WebViewClient() {

    override fun onPageFinished(view: WebView?, url: String?) {
        url?.let { onPageFinished(it) }
    }
}
