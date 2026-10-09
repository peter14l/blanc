package me.bnfy.blanc.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.print.PrintAttributes
import android.print.PrintManager
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.DownloadListener
import android.webkit.GeolocationPermissions
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.PermissionRequest
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import me.bnfy.blanc.util.UserAgentUtils
import android.webkit.WebViewClient
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.google.gson.Gson
import me.bnfy.blanc.BlancApplication
import me.bnfy.blanc.download.DownloadService
import me.bnfy.blanc.storage.DownloadEntity
import me.bnfy.blanc.tab.Tab
import kotlinx.coroutines.launch
import me.bnfy.blanc.tab.TabManager
import timber.log.Timber

/**
 * Custom WebView for content tabs with full feature support.
 */
class ContentWebView(
    context: Context,
    attrs: AttributeSet? = null
) : WebView(context, attrs) {
    
    private var tab: Tab? = null
    private var tabManager: TabManager? = null
    private var onFileChooserCallback: FileChooserHandler? = null
    private var onPermissionRequestCallback: ((String, String, (Boolean) -> Unit) -> Unit)? = null
    
    init {
        setupWebView()
    }
    
    private fun setupWebView() {
        val settings = this.settings
        
        // JavaScript & DOM
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.cacheMode = WebSettings.LOAD_DEFAULT
        
        // Mixed content
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        
        // Viewport
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        settings.setSupportZoom(true)
        settings.builtInZoomControls = true
        settings.displayZoomControls = false
        
        // User agent
        settings.userAgentString = buildUserAgentString()
        
        // Security
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.allowFileAccessFromFileURLs = false
        settings.allowUniversalAccessFromFileURLs = false
        settings.blockNetworkImage = false
        settings.blockNetworkLoads = false
        
        // Media
        settings.mediaPlaybackRequiresUserGesture = true
        
        // Text encoding
        settings.defaultTextEncodingName = "UTF-8"
        
        // Forms
        settings.saveFormData = true
        settings.savePassword = true
        
        // Layout
        settings.layoutAlgorithm = WebSettings.LayoutAlgorithm.TEXT_AUTOSIZING
        
        // Safe browsing
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            settings.safeBrowsingEnabled = true
        }
        
        // Overscroll
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        
        // Geolocation
        settings.setGeolocationEnabled(true)
        
        // JavaScript windows
        settings.javaScriptCanOpenWindowsAutomatically = false
        settings.setSupportMultipleWindows(false)
        
        // Download listener
        setDownloadListener { url, userAgent, contentDisposition, mimeType, contentLength ->
            startDownload(url, userAgent, contentDisposition, mimeType, contentLength)
        }
        
        // Set clients
        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url?.toString() ?: return false
                
                // Handle special URLs
                return when {
                    url.startsWith("blanc://") -> {
                        handleBlancUrl(url)
                        true
                    }
                    url.startsWith("mailto:") || url.startsWith("tel:") || url.startsWith("sms:") || url.startsWith("intent:") -> {
                        false // Let OS handle
                    }
                    else -> {
                        tab?.url = url
                        tab?.resetForNewNavigation()
                        false
                    }
                }
            }
            
            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                val requestUrl = request?.url?.toString() ?: return super.shouldInterceptRequest(view, request)
                if (requestUrl.startsWith("blanc://")) {
                    return WebResourceResponse("text/html", "UTF-8", java.io.ByteArrayInputStream("".toByteArray()))
                }
                try {
                    val engine = BlancApplication.getInstance().adblockEngine
                    if (engine.shouldBlock(requestUrl, request)) {
                        tab?.blockedCount = (tab?.blockedCount ?: 0) + 1
                        return engine.createBlockedResponse()
                    }
                } catch (e: Exception) {
                    // Fall back to super
                }
                return super.shouldInterceptRequest(view, request)
            }
            
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                url?.let {
                    tab?.url = it
                    tab?.isLoading = true
                    tab?.progress = 0
                    tab?.blockedCount = 0
                }
            }
            
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                url?.let {
                    tab?.url = it
                    tab?.isLoading = false
                    tab?.progress = 100
                    
                    // Update history
                    if (tab?.history?.isEmpty() == false && tab?.history?.getOrNull(tab?.historyIndex ?: -1) != it) {
                        tab?.addToHistory(it)
                    }
                    
                    tab?.canGoBack = view?.canGoBack() ?: false
                    tab?.canGoForward = view?.canGoForward() ?: false

                    view?.let { wv ->
                        try {
                            BlancApplication.getInstance().adblockEngine.cosmeticEngine.injectCosmeticFilters(wv, it)
                        } catch (e: Exception) {
                            Timber.e(e, "Error injecting cosmetic filters")
                        }
                    }

                    if (tab?.isPrivate == false && !it.startsWith("blanc://") && it != "about:blank") {
                        BlancApplication.getInstance().scope.launch {
                            try {
                                BlancApplication.getInstance().repository.recordHistoryVisit(
                                    url = it,
                                    title = tab?.title?.ifEmpty { it } ?: it,
                                    favicon = tab?.favicon,
                                    profileId = tab?.profileId ?: "personal"
                                )
                            } catch (e: Exception) {
                                Timber.e(e, "Error recording history visit")
                            }
                        }
                    }
                }
            }
            
            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: android.webkit.WebResourceError?
            ) {
                val failingUrl = request?.url?.toString() ?: ""
                if (failingUrl.startsWith("blanc://")) {
                    return
                }
                super.onReceivedError(view, request, error)
                if (request?.isForMainFrame == true) {
                    tab?.isLoading = false
                    tab?.progress = 100
                }
            }
            
            @RequiresApi(Build.VERSION_CODES.O)
            override fun onRenderProcessGone(view: WebView?, detail: android.webkit.RenderProcessGoneDetail?): Boolean {
                return true
            }
        }
        
        webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                super.onProgressChanged(view, newProgress)
                tab?.progress = newProgress
            }
            
            override fun onReceivedTitle(view: WebView?, title: String?) {
                super.onReceivedTitle(view, title)
                title?.let {
                    tab?.title = it
                }
            }
            
            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                val acceptTypes = fileChooserParams?.acceptTypes?.toList() ?: listOf("*/*")
                onFileChooserCallback?.onFileChooser(filePathCallback, acceptTypes)
                return true
            }
            
            override fun onPermissionRequest(request: PermissionRequest?) {
                val origin = request?.origin?.toString() ?: ""
                val resources = request?.resources ?: emptyArray()
                
                resources.forEach { resource ->
                    val resourceName = when (resource) {
                        PermissionRequest.RESOURCE_VIDEO_CAPTURE -> "camera"
                        PermissionRequest.RESOURCE_AUDIO_CAPTURE -> "microphone"
                        PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID -> "protected-media"
                        else -> "unknown"
                    }
                    
                    onPermissionRequestCallback?.invoke(origin, resourceName) { granted ->
                        if (granted) {
                            request?.grant(arrayOf(resource))
                        } else {
                            request?.deny()
                        }
                    }
                }
            }
            
            override fun onGeolocationPermissionsShowPrompt(
                origin: String,
                callback: GeolocationPermissions.Callback?
            ) {
                onPermissionRequestCallback?.invoke(origin, "geolocation") { granted ->
                    callback?.invoke(origin, granted, true)
                }
            }
            
            override fun onJsAlert(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
                result?.confirm()
                return true
            }
            
            override fun onJsConfirm(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
                result?.confirm()
                return true
            }
            
            override fun onJsPrompt(view: WebView?, url: String?, message: String?, defaultValue: String?, result: JsPromptResult?): Boolean {
                result?.confirm()
                return true
            }
        }
    }
    
    fun bind(
        tab: Tab,
        tabManager: TabManager,
        onFileChooser: FileChooserHandler? = null,
        onPermissionRequest: ((String, String, (Boolean) -> Unit) -> Unit)? = null
    ) {
        this.tab = tab
        tab.webView = this
        this.tabManager = tabManager
        this.onFileChooserCallback = onFileChooser
        this.onPermissionRequestCallback = onPermissionRequest
        
        // Configure cookies for private vs regular mode
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        if (tab.isPrivate) {
            cookieManager.setAcceptThirdPartyCookies(this, false)
            cookieManager.removeSessionCookies(null)
        } else {
            cookieManager.setAcceptThirdPartyCookies(this, true)
        }
        
        // Load URL if not already loaded and not an internal surface
        if (tab.url != "about:blank" && !tab.url.startsWith("blanc://") && tab.url != url) {
            loadUrl(tab.url)
        }
    }
    
    private fun startDownload(
        url: String,
        userAgent: String?,
        contentDisposition: String?,
        mimeType: String?,
        contentLength: Long
    ) {
        val fileName = URLUtil.guessFileName(url, contentDisposition, mimeType)
        
        val entity = DownloadEntity(
            id = java.util.UUID.randomUUID().toString(),
            windowId = tab?.windowId ?: "default",
            tabId = tab?.id,
            url = url,
            fileName = fileName,
            mimeType = mimeType,
            totalBytes = contentLength,
            state = DownloadEntity.STATE_PENDING,
            profileId = tab?.profileId ?: "personal",
            isPrivate = tab?.isPrivate ?: false
        )
        
        // Start download service
        val intent = Intent(context, DownloadService::class.java).apply {
            action = DownloadService.ACTION_START
            putExtra(DownloadService.EXTRA_DOWNLOAD_JSON, Gson().toJson(entity))
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }
    
    private fun handleBlancUrl(url: String) {
        when {
            url == "blanc://newtab" -> tabManager?.createTab()
            url == "blanc://newtab?private=1" -> tabManager?.createPrivateTab()
            url == "blanc://settings" -> tabManager?.createTab("blanc://settings")
            url == "blanc://bookmarks" -> tabManager?.createTab("blanc://bookmarks")
            url == "blanc://history" -> tabManager?.createTab("blanc://history")
            url == "blanc://downloads" -> tabManager?.createTab("blanc://downloads")
            url == "blanc://shortcuts" -> tabManager?.createTab("blanc://shortcuts")
            url.startsWith("blanc://mahjong") -> tabManager?.createTab(url)
            else -> tabManager?.createTab(url)
        }
    }

    override fun loadUrl(url: String) {
        if (url.startsWith("blanc://")) {
            handleBlancUrl(url)
            super.loadUrl("about:blank")
            return
        }
        super.loadUrl(url)
    }

    override fun loadUrl(url: String, additionalHttpHeaders: MutableMap<String, String>) {
        if (url.startsWith("blanc://")) {
            handleBlancUrl(url)
            super.loadUrl("about:blank")
            return
        }
        super.loadUrl(url, additionalHttpHeaders)
    }
    
    private fun buildUserAgentString(): String {
        return UserAgentUtils.buildMobileUserAgent(context)
    }

    /**
     * Prints the current web page using Android PrintManager.
     */
    fun printPage() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
            val printAdapter = createPrintDocumentAdapter("Blanc Page")
            
            printManager.print("Blanc Page", printAdapter, PrintAttributes.Builder().build())
        }
    }

    /**
     * Captures the current WebView content as a bitmap for tab thumbnails.
     */
    fun captureThumbnail(): Bitmap? {
        return try {
            val width = measuredWidth
            val height = measuredHeight
            if (width <= 0 || height <= 0) return null
            
            // Scale down for thumbnail (max 200px width)
            val scale = 200f / width
            val thumbWidth = (width * scale).toInt().coerceAtLeast(1)
            val thumbHeight = (height * scale).toInt().coerceAtLeast(1)
            
            val bitmap = Bitmap.createBitmap(thumbWidth, thumbHeight, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bitmap)
            canvas.scale(scale, scale)
            draw(canvas)
            bitmap
        } catch (e: Exception) {
            Timber.e(e, "Failed to capture thumbnail")
            null
        }
    }
    var onScrollChangeCallback: ((dy: Int, scrollY: Int) -> Unit)? = null

    override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) {
        super.onScrollChanged(l, t, oldl, oldt)
        val dy = t - oldt
        onScrollChangeCallback?.invoke(dy, t)
    }
}

/**
 * Composable wrapper for ContentWebView.
 */
@Composable
fun ContentWebViewContainer(
    tabManager: TabManager,
    activeTab: Tab?,
    onFileChooser: FileChooserHandler,
    onRequestPermission: (String, String, (Boolean) -> Unit) -> Unit,
    onScrollChange: ((dy: Int, scrollY: Int) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    if (activeTab != null) {
        androidx.compose.runtime.key(activeTab.id) {
            AndroidView(
                factory = { ctx ->
                    ContentWebView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        this.onScrollChangeCallback = onScrollChange
                        bind(activeTab, tabManager, onFileChooser, onRequestPermission)
                    }
                },
                update = { webView ->
                    webView.onScrollChangeCallback = onScrollChange
                    webView.bind(activeTab, tabManager, onFileChooser, onRequestPermission)
                },
                modifier = modifier
            )
        }
    } else {
        // No active tab - show new tab page
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No tabs open. Tap + to create a new tab.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

@Composable
fun ContentWebViewContainer(
    tabManager: TabManager,
    activeTabId: String?,
    tabs: List<Tab>,
    onFileChooser: FileChooserHandler,
    onRequestPermission: (String, String, (Boolean) -> Unit) -> Unit,
    onScrollChange: ((dy: Int, scrollY: Int) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    ContentWebViewContainer(
        tabManager = tabManager,
        activeTab = tabs.find { it.id == activeTabId },
        onFileChooser = onFileChooser,
        onRequestPermission = onRequestPermission,
        onScrollChange = onScrollChange,
        modifier = modifier
    )
}