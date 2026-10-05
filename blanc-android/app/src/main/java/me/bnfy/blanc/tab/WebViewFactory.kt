package me.bnfy.blanc.tab

import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebChromeClient
import androidx.annotation.Keep

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

            // Apply all settings
            configureSettings(settings)

            // Private mode specific configuration
            if (isPrivate) {
                configurePrivateMode()
            }

            // Blanc-specific defaults
            configureBlancDefaults()
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
        settings.setAppCacheEnabled(true)
        settings.setAppCachePath(context.cacheDir.absolutePath)
        settings.cacheMode = WebSettings.LOAD_DEFAULT

        // Mixed content - allow HTTPS pages to load HTTP resources (with warning)
        // This matches desktop Blanc behavior for compatibility
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE

        // Viewport and zoom
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        settings.supportZoom = true
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

        // Hardware acceleration
        settings.renderPriority = WebSettings.RenderPriority.HIGH

        // Layout algorithm
        settings.layoutAlgorithm = WebSettings.LayoutAlgorithm.TEXT_AUTOSIZING

        // Safe browsing (API 27+)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            settings.safeBrowsingEnabled = true
        }

        // Prevent overscroll glow on Android 12+
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            settings.overScrollMode = WebView.OVER_SCROLL_NEVER
        }
    }

    /**
     * Applies private/incognito mode specific configuration.
     */
    private fun configurePrivateMode() {
        // Private mode settings are handled at the WebView level via
        // a separate WebViewDatabase and CookieManager configuration.
        // The WebView itself doesn't have a "private mode" flag,
        // so we rely on separate data directories and session handling.
    }

    /**
     * Applies Blanc-specific default configurations.
     */
    private fun configureBlancDefaults() {
        // Disable unnecessary features for privacy/performance
        settings?.let {
            it.setGeolocationEnabled(false) // Requires explicit permission
            it.javaScriptCanOpenWindowsAutomatically = false
            it.setSupportMultipleWindows(false)
        }
    }

    /**
     * Builds a custom user agent string identifying Blanc.
     */
    private fun buildUserAgentString(): String {
        val baseUa = WebSettings.getDefaultUserAgent(context)
        val versionName = try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        } catch (e: Exception) {
            "1.0"
        }
        return "$baseUa Blanc/$versionName"
    }

    /**
     * Configures cookie management for private tabs.
     * Should be called after WebView creation for private tabs.
     */
    fun configurePrivateCookies(webView: WebView) {
        if (isPrivate) {
            val cookieManager = CookieManager.getInstance()
            cookieManager.setAcceptCookie(true)
            cookieManager.setAcceptThirdPartyCookies(webView, false)
            // In private mode, we use ephemeral cookies that are cleared on close
            cookieManager.removeSessionCookies(null)
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