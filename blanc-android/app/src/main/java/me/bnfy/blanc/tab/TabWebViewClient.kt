package me.bnfy.blanc.tab

import android.graphics.Bitmap
import android.net.http.SslError
import android.os.Build
import android.webkit.ClientCertRequest
import android.webkit.HttpAuthHandler
import android.webkit.RenderProcessGoneDetail
import android.webkit.SafeBrowsingResponse
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.annotation.Keep
import androidx.annotation.RequiresApi
import me.bnfy.blanc.adblock.AdblockEngine

/**
 * WebViewClient implementation for handling tab navigation, loading, and errors.
 *
 * This client manages the tab's navigation lifecycle, integrates with the
 * ad-blocking engine, and communicates state changes to the bridge.
 */
@Keep
class TabWebViewClient(
    private val tab: Tab,
    private val bridge: BlancBridge,
    private val adblockEngine: AdblockEngine? = null,
    private val onPageFinishedCallback: ((String) -> Unit)? = null
) : WebViewClient() {

    /** Tracks whether the current navigation was initiated by user action. */
    private var isUserNavigation = false

    /** Called when the WebView is about to load a new URL. */
    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
        val url = request?.url?.toString() ?: return false

        // Handle special URLs
        return when {
            url.startsWith("blanc://") -> {
                handleBlancUrl(url)
                true
            }
            url.startsWith("mailto:") || url.startsWith("tel:") || url.startsWith("sms:") || url.startsWith("intent:") -> {
                // Let the OS handle these
                false
            }
            else -> {
                // Normal navigation - update tab state and let WebView handle it
                isUserNavigation = true
                tab.url = url
                tab.resetForNewNavigation()
                bridge.onNavigation(tab.id, url, null)
                false // Let WebView load the URL
            }
        }
    }

    /** Legacy API for older Android versions. */
    @Suppress("DEPRECATION")
    override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
        url?.let {
            val request = WebResourceRequestWrapper(it)
            return shouldOverrideUrlLoading(view, request)
        }
        return false
    }

    /** Called when a page starts loading. */
    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        url?.let {
            tab.url = it
            tab.isLoading = true
            tab.progress = 0
            tab.blockedCount = 0
            bridge.onNavigation(tab.id, it, null)
            bridge.onTabUpdated(tab.toBridgeTab())
        }
    }

    /** Called when a page finishes loading. */
    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        url?.let {
            tab.url = it
            tab.isLoading = false
            tab.progress = 100

            // Update history
            if (isUserNavigation || tab.history.isEmpty() || tab.history[tab.historyIndex] != it) {
                tab.addToHistory(it)
            }
            isUserNavigation = false

            // Update navigation state from WebView
            tab.canGoBack = view?.canGoBack() ?: false
            tab.canGoForward = view?.canGoForward() ?: false

            bridge.onTabUpdated(tab.toBridgeTab())
            
            // Capture thumbnail after page load
            onPageFinishedCallback?.invoke(it)
        }
    }

    /** Called when an error occurs during loading. */
    override fun onReceivedError(
        view: WebView?,
        request: WebResourceRequest?,
        error: WebResourceError?
    ) {
        super.onReceivedError(view, request, error)
        // Don't override error pages for main frame navigation errors
        // Let the WebView show its default error page
        if (request?.isForMainFrame == true) {
            tab.isLoading = false
            tab.progress = 100
            bridge.onTabUpdated(tab.toBridgeTab())
        }
    }

    /** Legacy error callback. */
    @Suppress("DEPRECATION")
    override fun onReceivedError(
        view: WebView?,
        errorCode: Int,
        description: String,
        failingUrl: String
    ) {
        super.onReceivedError(view, errorCode, description, failingUrl)
        if (failingUrl == tab.url) {
            tab.isLoading = false
            tab.progress = 100
            bridge.onTabUpdated(tab.toBridgeTab())
        }
    }

    /** Called when an SSL error occurs. */
    override fun onReceivedSslError(view: WebView?, handler: android.webkit.SslErrorHandler?, error: SslError?) {
        // In private mode or for security, we could allow proceeding
        // For now, cancel the request (safe default)
        handler?.cancel()
    }

    /** Called for each resource request - hook for ad blocking. */
    override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
        val url = request?.url?.toString() ?: return super.shouldInterceptRequest(view, request)

        // Check with adblock engine
        adblockEngine?.let { engine ->
            if (engine.shouldBlock(url, request)) {
                tab.blockedCount++
                bridge.onTabUpdated(tab.toBridgeTab())
                return engine.createBlockedResponse()
            }
        }

        return super.shouldInterceptRequest(view, request)
    }

    /** Legacy intercept request. */
    @Suppress("DEPRECATION")
    override fun shouldInterceptRequest(view: WebView?, url: String?): WebResourceResponse? {
        url?.let {
            val request = WebResourceRequestWrapper(it)
            return shouldInterceptRequest(view, request)
        }
        return super.shouldInterceptRequest(view, url)
    }

    /** Handles HTTP authentication requests. */
    override fun onReceivedHttpAuthRequest(
        view: WebView?,
        handler: HttpAuthHandler?,
        host: String?,
        realm: String?
    ) {
        // Could prompt user for credentials via bridge
        // For now, cancel
        handler?.cancel()
    }

    /** Handles client certificate requests. */
    override fun onReceivedClientCertRequest(view: WebView?, request: ClientCertRequest?) {
        request?.cancel()
    }

    /** Called when the render process crashes or is killed. */
    @RequiresApi(Build.VERSION_CODES.O)
    override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
        val didCrash = detail?.didCrash() == true
        val reason = if (didCrash) "Renderer process crashed" else "Renderer process killed"
        
        // Notify bridge
        bridge.onNavigation(tab.id, "about:blank", reason)
        
        // Schedule tab recreation on main thread
        if (tab.webView != null) {
            val tabId = tab.id
            val tabUrl = tab.url
            val isPrivate = tab.isPrivate
            val tabManager = tab.webView?.tag as? TabManager
            
            if (tabManager != null) {
                tabManager.mainHandler.post {
                    // Recreate the tab
                    tabManager.recreateTabAfterCrash(tabId, tabUrl, isPrivate)
                }
            }
        }
        
        return true // Indicates we handled it
    }

    /** Handles safe browsing hits. */
    @RequiresApi(Build.VERSION_CODES.O)
    override fun onSafeBrowsingHit(view: WebView?, request: WebResourceRequest?, threatType: Int, callback: SafeBrowsingResponse?) {
        // Show warning via bridge, allow user to proceed or go back
        callback?.backToSafety(true)
    }

    /** Handles Blanc internal URLs (blanc://newtab, blanc://settings, etc.) */
    private fun handleBlancUrl(url: String) {
        when {
            url == "blanc://newtab" -> {
                // Handled by TabManager creating a new tab
            }
            url == "blanc://settings" -> {
                // Handled by UI layer
            }
            else -> {
                // Other blanc:// URLs
            }
        }
    }

    /**
     * Wrapper to unify WebResourceRequest API across Android versions.
     */
    private class WebResourceRequestWrapper(private val url: String) : WebResourceRequest {
        override fun getUrl(): android.net.Uri = android.net.Uri.parse(url)
        override fun isForMainFrame(): Boolean = true
        override fun isRedirect(): Boolean = false
        override fun hasGesture(): Boolean = false
        override fun getMethod(): String = "GET"
        override fun getRequestHeaders(): MutableMap<String, String> = mutableMapOf()
    }
}