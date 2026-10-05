package me.bnfy.blanc.adblock

import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import me.bnfy.blanc.tab.Tab
import me.bnfy.blanc.tab.BlancBridge

/**
 * Adblock-enabled WebViewClient that intercepts and blocks requests.
 *
 * This client wraps the ad-blocking logic and integrates with the existing
 * TabWebViewClient pattern. It can be used standalone or composed with
 * the existing TabWebViewClient.
 */
class AdblockWebViewClient(
    private val tab: Tab,
    private val bridge: BlancBridge,
    private val adblockEngine: AdblockEngine
) : WebViewClient() {

    /** Called for each resource request - main ad blocking hook. */
    override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
        val url = request?.url?.toString() ?: return super.shouldInterceptRequest(view, request)

        // Check with adblock engine
        if (adblockEngine.shouldBlock(url, request)) {
            tab.blockedCount++
            bridge.onTabUpdated(tab.toBridgeTab())
            return adblockEngine.createBlockedResponse()
        }

        return super.shouldInterceptRequest(view, request)
    }

    /** Legacy API for older Android versions. */
    @Suppress("DEPRECATION")
    override fun shouldInterceptRequest(view: WebView?, url: String?): WebResourceResponse? {
        url?.let {
            val request = WebResourceRequestWrapper(it)
            return shouldInterceptRequest(view, request)
        }
        return super.shouldInterceptRequest(view, url)
    }

    /** Called when a page finishes loading - inject cosmetic filters. */
    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        url?.let {
            // Inject cosmetic filters after page load
            adblockEngine.cosmeticEngine.injectCosmeticFilters(view!!, it)
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

/**
 * Mixin interface for WebViewClients that want ad-blocking support.
 * Implement this in your WebViewClient to add ad-blocking capabilities.
 */
interface AdblockAwareClient {

    /** The adblock engine instance. */
    val adblockEngine: AdblockEngine

    /** Called when a request is blocked. */
    fun onRequestBlocked(url: String, tab: Tab)

    /** Called when cosmetic filters are injected. */
    fun onCosmeticFiltersInjected(tab: Tab, success: Boolean)
}

/**
 * Extension functions to add ad-blocking to existing WebViewClients.
 */
fun WebViewClient.withAdblock(
    tab: Tab,
    bridge: BlancBridge,
    engine: AdblockEngine
): AdblockWebViewClient = AdblockWebViewClient(tab, bridge, engine)

/**
 * A decorator that adds ad-blocking to an existing WebViewClient.
 * Use this when you need to preserve existing WebViewClient behavior.
 */
class AdblockWebViewClientDecorator(
    private val delegate: WebViewClient,
    private val tab: Tab,
    private val bridge: BlancBridge,
    private val adblockEngine: AdblockEngine
) : WebViewClient() {

    override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
        val url = request?.url?.toString() ?: return delegate.shouldInterceptRequest(view, request)

        // Check with adblock engine first
        if (adblockEngine.shouldBlock(url, request)) {
            tab.blockedCount++
            bridge.onTabUpdated(tab.toBridgeTab())
            return adblockEngine.createBlockedResponse()
        }

        // Delegate to wrapped client
        return delegate.shouldInterceptRequest(view, request)
    }

    @Suppress("DEPRECATION")
    override fun shouldInterceptRequest(view: WebView?, url: String?): WebResourceResponse? {
        url?.let {
            val request = WebResourceRequestWrapper(it)
            return shouldInterceptRequest(view, request)
        }
        return delegate.shouldInterceptRequest(view, url)
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        delegate.onPageFinished(view, url)
        url?.let {
            adblockEngine.cosmeticEngine.injectCosmeticFilters(view!!, it)
        }
    }

    // Delegate all other methods
    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean =
        delegate.shouldOverrideUrlLoading(view, request)

    @Suppress("DEPRECATION")
    override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean =
        delegate.shouldOverrideUrlLoading(view, url)

    override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) =
        delegate.onPageStarted(view, url, favicon)

    override fun onReceivedTitle(view: WebView?, title: String?) =
        delegate.onReceivedTitle(view, title)

    override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: android.webkit.WebResourceError?) =
        delegate.onReceivedError(view, request, error)

    @Suppress("DEPRECATION")
    override fun onReceivedError(view: WebView?, errorCode: Int, description: String, failingUrl: String) =
        delegate.onReceivedError(view, errorCode, description, failingUrl)

    override fun onReceivedSslError(view: WebView?, handler: android.webkit.SslErrorHandler?, error: android.net.http.SslError?) =
        delegate.onReceivedSslError(view, handler, error)

    override fun onReceivedHttpAuthRequest(view: WebView?, handler: android.webkit.HttpAuthHandler?, host: String?, realm: String?) =
        delegate.onReceivedHttpAuthRequest(view, handler, host, realm)

    override fun onReceivedClientCertRequest(view: WebView?, request: android.webkit.ClientCertRequest?) =
        delegate.onReceivedClientCertRequest(view, request)

    @androidx.annotation.RequiresApi(android.os.Build.VERSION_CODES.O)
    override fun onRenderProcessGone(view: WebView?, detail: android.webkit.RenderProcessGoneDetail?): Boolean =
        delegate.onRenderProcessGone(view, detail)

    @androidx.annotation.RequiresApi(android.os.Build.VERSION_CODES.O)
    override fun onSafeBrowsingHit(view: WebView?, request: WebResourceRequest?, threatType: Int, callback: android.webkit.SafeBrowsingResponse?) =
        delegate.onSafeBrowsingHit(view, request, threatType, callback)

    private class WebResourceRequestWrapper(private val url: String) : WebResourceRequest {
        override fun getUrl(): android.net.Uri = android.net.Uri.parse(url)
        override fun isForMainFrame(): Boolean = true
        override fun isRedirect(): Boolean = false
        override fun hasGesture(): Boolean = false
        override fun getMethod(): String = "GET"
        override fun getRequestHeaders(): MutableMap<String, String> = mutableMapOf()
    }
}