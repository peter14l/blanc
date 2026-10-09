package me.bnfy.blanc.tab

import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebChromeClient
import androidx.annotation.Keep
import me.bnfy.blanc.util.UserAgentUtils

/**
 * Factory for creating and configuring WebView instances.
 *
 * Centralizes all WebView configuration to ensure consistent behavior
 * across regular and private tabs. Applies Blanc's default settings
 * for security, privacy, and compatibility.
 */
@Keep
class WebViewFactory private constructor(
    private val context: Context,
    private val isPrivate: Boolean
) {

    /**
     * Creates a new WebViewFactory for regular (non-private) tabs.
     */
    companion object {
        fun createRegular(context: Context): WebViewFactory = WebViewFactory(context, false)

        /** Creates a new WebViewFactory for private/incognito tabs. */
        fun createPrivate(context: Context): WebViewFactory = WebViewFactory(context, true)
    }

    /**
     * Creates and configures a new WebView instance.
     *
     * @param webViewClient The WebViewClient to handle navigation and loading events.
     * @param webChromeClient The WebChromeClient to handle UI and permission events.
     * @return A fully configured WebView ready for use.
     */
    fun createWebView(
        webViewClient: WebViewClient,
        webChromeClient: WebChromeClient
    ): WebView {
        val webView = WebView(context).apply {
            // Basic configuration
            setWebViewClient(webViewClient)
            setWebChromeClient(webChromeClient)

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                overScrollMode = WebView.OVER_SCROLL_NEVER
            }

            // Apply all settings
            configureSettings(settings)

            // Configure cookies (regular vs private)
            configurePrivateCookies(this)
        }
        return webView
    }

    /**
     * Configures core WebSettings for security, performance, and compatibility.
     */
    private fun configureSettings(settings: WebSettings) {
        // JavaScript and DOM
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.cacheMode = WebSettings.LOAD_DEFAULT

        // Mixed content - allow HTTPS pages to load HTTP resources (with warning)
        // This matches desktop Blanc behavior for compatibility
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE

        // Viewport and zoom
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        settings.setSupportZoom(true)
        settings.builtInZoomControls = true
        settings.displayZoomControls = false

        // User agent - use system default with Blanc identifier
        settings.userAgentString = buildUserAgentString()

        // Security and privacy
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.allowFileAccessFromFileURLs = false
        settings.allowUniversalAccessFromFileURLs = false
        settings.blockNetworkImage = false
        settings.blockNetworkLoads = false

        // Media playback
        settings.mediaPlaybackRequiresUserGesture = true

        // Text encoding
        settings.defaultTextEncodingName = "UTF-8"

        // Form data
        settings.saveFormData = !isPrivate
        settings.savePassword = !isPrivate

        // Layout algorithm
        settings.layoutAlgorithm = WebSettings.LayoutAlgorithm.TEXT_AUTOSIZING

        // Safe browsing (API 27+)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            settings.safeBrowsingEnabled = true
        }

        // Blanc defaults
        settings.setGeolocationEnabled(false)
        settings.javaScriptCanOpenWindowsAutomatically = false
        settings.setSupportMultipleWindows(false)
    }

    /**
     * Applies private/incognito mode specific configuration.
     */
    private fun configurePrivateMode() {
        // Handled at CookieManager level
    }

    private fun buildUserAgentString(): String {
        return UserAgentUtils.buildMobileUserAgent(context)
    }

    /**
     * Configures cookie management for tabs.
     * Ensures cookies and third-party cookies are properly enabled for regular tabs,
     * which is required for search engines (like Google), login sessions, and verification challenges.
     */
    fun configurePrivateCookies(webView: WebView) {
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        if (isPrivate) {
            cookieManager.setAcceptThirdPartyCookies(webView, false)
            // In private mode, we use ephemeral cookies that are cleared on close
            cookieManager.removeSessionCookies(null)
        } else {
            cookieManager.setAcceptThirdPartyCookies(webView, true)
        }
    }

    /**
     * Applies ad-blocking related settings.
     * Called after AdblockEngine is initialized.
     */
    fun applyAdblockSettings(webView: WebView) {
        val settings = webView.settings
        // Enable content blocking via WebView's built-in mechanisms
        // The actual blocking is handled by AdblockEngine in shouldInterceptRequest
        settings.blockNetworkLoads = false // We handle blocking ourselves
    }
}