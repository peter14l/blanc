package me.bnfy.blanc.ui

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.annotation.Keep
import me.bnfy.blanc.bridge.BlancBridge
import me.bnfy.blanc.tab.TabManager

/**
 * UI WebViewClient for the main React UI WebView.
 * Loads the React UI from file:///android_asset/ui/index.html
 * Handles blanc:// internal surfaces via BlancBridge.
 * Transparent background, full-screen overlay.
 */
@Keep
class UiWebViewClient(
    private val context: Context,
    private val bridge: BlancBridge,
    private val tabManager: TabManager
) : WebViewClient() {

    private var uiLoaded = false

    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
        val url = request?.url?.toString() ?: return false

        return when {
            url.startsWith("blanc://") -> {
                handleBlancUrl(url)
                true
            }
            url.startsWith("file:///android_asset/") -> {
                // Allow local asset loads
                false
            }
            url.startsWith("http://") || url.startsWith("https://") -> {
                // External links - open in a new tab via TabManager
                tabManager.createTab(url)
                true
            }
            url.startsWith("mailto:") || url.startsWith("tel:") || url.startsWith("sms:") || url.startsWith("intent:") -> {
                // Let the OS handle these
                false
            }
            else -> {
                // Allow other schemes
                false
            }
        }
    }

    @Suppress("DEPRECATION")
    override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
        url?.let {
            val request = WebResourceRequestWrapper(it)
            return shouldOverrideUrlLoading(view, request)
        }
        return false
    }

    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        if (url?.startsWith("file:///android_asset/ui/") == true) {
            uiLoaded = true
            // Configure transparent background
            view?.setBackgroundColor(0x00000000)
            view?.background?.setAlpha(0)
        }
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        if (url?.startsWith("file:///android_asset/ui/") == true && !uiLoaded) {
            uiLoaded = true
            view?.setBackgroundColor(0x00000000)
        }
    }

    override fun onReceivedError(
        view: WebView?,
        request: WebResourceRequest?,
        error: android.webkit.WebResourceError?
    ) {
        super.onReceivedError(view, request, error)
        // If UI fails to load, show error
        if (request?.url?.toString()?.startsWith("file:///android_asset/ui/") == true) {
            view?.loadUrl("file:///android_asset/ui/error.html")
        }
    }

    @Suppress("DEPRECATION")
    override fun onReceivedError(
        view: WebView?,
        errorCode: Int,
        description: String,
        failingUrl: String
    ) {
        super.onReceivedError(view, errorCode, description, failingUrl)
        if (failingUrl.startsWith("file:///android_asset/ui/")) {
            view?.loadUrl("file:///android_asset/ui/error.html")
        }
    }

    private fun handleBlancUrl(url: String) {
        when {
            url == "blanc://newtab" -> {
                tabManager.createTab()
            }
            url == "blanc://newtab?private=1" -> {
                tabManager.createPrivateTab()
            }
            url == "blanc://bookmarks" || url == "blanc://favorites" -> {
                bridge.openSurface("favorites", false)
            }
            url == "blanc://history" -> {
                bridge.openSurface("history", false)
            }
            url == "blanc://downloads" -> {
                bridge.openSurface("downloads", false)
            }
            url == "blanc://settings" -> {
                bridge.openSurface("settings", false)
            }
            url == "blanc://shortcuts" -> {
                bridge.openSurface("shortcuts", false)
            }
            url.startsWith("blanc://mahjong") -> {
                tabManager.createTab(url)
            }
            else -> {
                // Unknown blanc:// URL - could log or ignore
            }
        }
    }

    private class WebResourceRequestWrapper(private val url: String) : WebResourceRequest {
        override fun getUrl(): Uri = Uri.parse(url)
        override fun isForMainFrame(): Boolean = true
        override fun isRedirect(): Boolean = false
        override fun hasGesture(): Boolean = false
        override fun getMethod(): String = "GET"
        override fun getRequestHeaders(): MutableMap<String, String> = mutableMapOf()
    }
}

/**
 * Factory for creating the UI WebView that hosts the React interface.
 */
class UiWebViewFactory(private val context: Context) {

    fun createUiWebView(
        bridge: BlancBridge,
        tabManager: TabManager
    ): WebView {
        val webView = WebView(context).apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
                mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                useWideViewPort = true
                loadWithOverviewMode = true
                setSupportZoom(false)
                builtInZoomControls = false
                displayZoomControls = false
                allowFileAccess = false
                allowContentAccess = false
                allowFileAccessFromFileURLs = false
                allowUniversalAccessFromFileURLs = false
                mediaPlaybackRequiresUserGesture = true
                defaultTextEncodingName = "UTF-8"
                saveFormData = false
                layoutAlgorithm = android.webkit.WebSettings.LayoutAlgorithm.TEXT_AUTOSIZING
            }

            // Transparent background for overlay
            setBackgroundColor(0x00000000)
            background?.setAlpha(0)

            // Configure clients
            webViewClient = UiWebViewClient(context, bridge, tabManager)
            // WebChromeClient not needed for UI WebView (no permissions, dialogs, etc.)

            // Add the BlancBridge as JavaScript interface
            addJavascriptInterface(bridge, "BlancBridge")

            // Load the React UI from assets
            loadUrl("file:///android_asset/ui/index.html")
        }
        return webView
    }
}