package me.bnfy.blanc.ui

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintManager
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.View
import android.webkit.CookieManager
import android.webkit.DownloadListener
import android.webkit.GeolocationPermissions
import android.webkit.JsResult
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.annotation.RequiresApi
import androidx.compose.ui.platform.LocalContext
import me.bnfy.blanc.adblock.AdblockEngine
import me.bnfy.blanc.bridge.BridgeProtocol
import me.bnfy.blanc.bridge.BlancBridge
import me.bnfy.blanc.tab.Tab
import me.bnfy.blanc.tab.TabManager
import me.bnfy.blanc.tab.TabWebViewClient
import me.bnfy.blanc.tab.WebViewFactory
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
    private var adblockEngine: AdblockEngine? = null
    private var blancBridge: BlancBridge? = null
    private var onFileChooserCallback: ((ValueCallback<Array<Uri>>, List<String>) -> Unit)? = null
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
        settings.setAppCacheEnabled(true)
        settings.cacheMode = WebSettings.LOAD_DEFAULT
        
        // Mixed content
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        
        // Viewport
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        settings.supportZoom = true
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
        
        // Hardware acceleration
        settings.renderPriority = WebSettings.RenderPriority.HIGH
        
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
        downloadListener = DownloadListener { url, userAgent, contentDisposition, mimeType, contentLength ->
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
                        blancBridge?.onNavigation(tab?.id ?: "", url, null)
                        false
                    }
                }
            }
            
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                url?.let {
                    tab?.url = it
                    tab?.isLoading = true
                    tab?.progress = 0
                    tab?.blockedCount = 0
                    blancBridge?.onNavigation(tab?.id ?: "", it, null)
                    blancBridge?.onTabUpdated(tab?.toBridgeTab()!!)
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
                    
                    blancBridge?.onTabUpdated(tab?.toBridgeTab()!!)
                }
            }
            
            override fun onReceivedTitle(view: WebView?, title: String?) {
                super.onReceivedTitle(view, title)
                title?.let {
                    tab?.title = it
                    blancBridge?.onTabUpdated(tab?.toBridgeTab()!!)
                }
            }
            
            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: android.webkit.WebResourceError?
            ) {
                super.onReceivedError(view, request, error)
                if (request?.isForMainFrame == true) {
                    tab?.isLoading = false
                    tab?.progress = 100
                    blancBridge?.onTabUpdated(tab?.toBridgeTab()!!)
                }
            }
            
            @RequiresApi(Build.VERSION_CODES.O)
            override fun onRenderProcessGone(view: WebView?, detail: android.webkit.RenderProcessGoneDetail?): Boolean {
                blancBridge?.onNavigation(tab?.id ?: "", "about:blank", "Renderer process gone")
                return true
            }
        }
        
        webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                super.onProgressChanged(view, newProgress)
                tab?.progress = newProgress
                blancBridge?.onTabUpdated(tab?.toBridgeTab()!!)
            }
            
            override fun onReceivedTitle(view: WebView?, title: String?) {
                super.onReceivedTitle(view, title)
                title?.let {
                    tab?.title = it
                    blancBridge?.onTabUpdated(tab?.toBridgeTab()!!)
                }
            }
            
            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                val acceptTypes = fileChooserParams?.acceptTypes?.toList() ?: listOf("*/*")
                onFileChooserCallback?.invoke(filePathCallback!!, acceptTypes)
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
                // Could show custom dialog
                result?.confirm()
                return true
            }
            
            override fun onJsConfirm(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
                result?.confirm()
                return true
            }
            
            override fun onJsPrompt(view: WebView?, url: String?, message: String?, defaultValue: String?, result: JsResult?): Boolean {
                result?.confirm()
                return true
            }
        }
    }
    
    fun bind(
        tab: Tab,
        tabManager: TabManager,
        adblockEngine: AdblockEngine,
        blancBridge: BlancBridge,
        onFileChooser: (ValueCallback<Array<Uri>>, List<String>) -> Unit,
        onPermissionRequest: (String, String, (Boolean) -> Unit) -> Unit
    ) {
        this.tab = tab
        this.tabManager = tabManager
        this.adblockEngine = adblockEngine
        this.blancBridge = blancBridge
        this.onFileChooserCallback = onFileChooser
        this.onPermissionRequestCallback = onPermissionRequest
        
        // Update WebViewClient with adblock
        webViewClient = TabWebViewClient(tab, blancBridge, adblockEngine)
        
        // Configure cookies for private mode
        if (tab.isPrivate) {
            val cookieManager = CookieManager.getInstance()
            cookieManager.setAcceptCookie(true)
            cookieManager.setAcceptThirdPartyCookies(this, false)
            cookieManager.removeSessionCookies(null)
        }
        
        // Load URL if not already loaded
        if (tab.url != "about:blank" && tab.url != url) {
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
        val intent = Intent(context, me.bnfy.blanc.download.DownloadService::class.java).apply {
            action = me.bnfy.blanc.download.DownloadService.ACTION_START
            putExtra(me.bnfy.blanc.download.DownloadService.EXTRA_DOWNLOAD, entity)
        }
        context.startForegroundService(intent)
    }
    
    private fun handleBlancUrl(url: String) {
        when {
            url == "blanc://newtab" -> tabManager?.createTab()
            url == "blanc://newtab?private=1" -> tabManager?.createPrivateTab()
            url == "blanc://settings" -> blancBridge?.openSurface("settings", false)
            url == "blanc://bookmarks" -> blancBridge?.openSurface("favorites", false)
            url == "blanc://history" -> blancBridge?.openSurface("history", false)
            url == "blanc://downloads" -> blancBridge?.openSurface("downloads", false)
            url == "blanc://shortcuts" -> blancBridge?.openSurface("shortcuts", false)
            url.startsWith("blanc://mahjong") -> tabManager?.createTab(url)
            else -> tabManager?.createTab(url)
        }
    }
    
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
     * Uses PixelCopy for hardware-accelerated capture on API 24+.
     */
    fun captureThumbnail(): Bitmap? {
        return try {
            val width = measuredWidth
            val height = measuredHeight
            if (width <= 0 || height <= 0) return null
            
            // Scale down for thumbnail (max 200px width)
            val scale = 200f / width
            val thumbWidth = (width * scale).toInt()
            val thumbHeight = (height * scale).toInt()
            
            val bitmap = Bitmap.createBitmap(thumbWidth, thumbHeight, Bitmap.Config.ARGB_8888)
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // Use PixelCopy for hardware-accelerated capture
                val pixelCopy = android.view.PixelCopy()
                val result = pixelCopy.request(this, bitmap, { copyResult ->
                    // Copy completed
                }, Handler(Looper.getMainLooper()))
                if (result == android.view.PixelCopy.SUCCESS) {
                    bitmap
                } else {
                    // Fallback to draw
                    draw(android.graphics.Canvas(bitmap))
                    bitmap
                }
            } else {
                // Fallback for older APIs
                draw(android.graphics.Canvas(bitmap))
                bitmap
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to capture thumbnail")
            null
        }
    }
}

/**
 * Composable wrapper for ContentWebView.
 */
@Composable
fun ContentWebViewContainer(
    tabManager: TabManager,
    activeTabId: String?,
    tabs: List<Tab>,
    onFileChooser: (ValueCallback<Array<Uri>>, List<String>) -> Unit,
    onRequestPermission: (String, String, (Boolean) -> Unit) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val view = LocalView.current
    
    // Get the active tab
    val activeTab = tabs.find { it.id == activeTabId }
    
    // Create WebView for each tab
    // In a real implementation, you'd use a ViewPager or similar for tab switching
    // For now, we'll show the active tab's WebView
    
    if (activeTab != null) {
        AndroidView(
            factory = { ctx ->
                val webView = ContentWebView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
                webView
            },
            update = { webView ->
                (webView as ContentWebView).bind(
                    tab = activeTab,
                    tabManager = tabManager,
                    adblockEngine = me.bnfy.blanc.BlancApplication.getInstance().adblockEngine,
                    blancBridge = me.bnfy.blanc.BlancApplication.getInstance().blancBridge!!,
                    onFileChooser = onFileChooser,
                    onPermissionRequest = onRequestPermission
                )
            },
            modifier = modifier
        )
    } else {
        // No active tab - show new tab page
        androidx.compose.foundation.Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            androidx.compose.material3.Text(
                text = "No tabs open. Tap + to create a new tab.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}