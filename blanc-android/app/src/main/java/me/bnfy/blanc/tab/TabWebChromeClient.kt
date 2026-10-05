package me.bnfy.blanc.tab

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Message
import android.util.Log
import android.view.View
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.widget.FrameLayout
import androidx.annotation.Keep
import androidx.annotation.RequiresApi

/**
 * WebChromeClient implementation for handling UI interactions, permissions, and window management.
 *
 * Handles:
 * - Page loading progress
 * - Page title updates
 * - JavaScript dialogs (alert, confirm, prompt)
 * - Permission requests (geolocation, camera, microphone)
 * - Window creation (target="_blank", window.open)
 * - Fullscreen video
 * - File chooser
 */
@Keep
class TabWebChromeClient(
    private val tab: Tab,
    private val bridge: BlancBridge,
    private val activity: Activity,
    private val tabManager: TabManager
) : WebChromeClient() {

    private var customView: View? = null
    private var customViewCallback: CustomViewCallback? = null
    private var fileChooserCallback: ValueCallback<Array<Uri>>? = null
    private var fileChooserCallbackSingle: ValueCallback<Uri>? = null

    companion object {
        private const val TAG = "TabWebChromeClient"
        private const val FILE_CHOOSER_REQUEST_CODE = 0x1001
    }

    /** Called when page loading progress changes. */
    override fun onProgressChanged(view: WebView?, newProgress: Int) {
        super.onProgressChanged(view, newProgress)
        tab.progress = newProgress
        bridge.onProgress(tab.id, newProgress)
        bridge.onTabUpdated(tab.toBridgeTab())
    }

    /** Called when the page title is received. */
    override fun onReceivedTitle(view: WebView?, title: String?) {
        super.onReceivedTitle(view, title)
        title?.let {
            tab.title = it
            bridge.onTabUpdated(tab.toBridgeTab())
        }
    }

    /** Called when the favicon is received. */
    override fun onReceivedIcon(view: WebView?, icon: Bitmap?) {
        super.onReceivedIcon(view, icon)
        // Could emit favicon via bridge if needed
    }

    /** Called when the touch icon URL is received. */
    override fun onReceivedTouchIconUrl(view: WebView?, url: String?, precomposed: Boolean) {
        super.onReceivedTouchIconUrl(view, url, precomposed)
    }

    // ===== JavaScript Dialogs =====

    override fun onJsAlert(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
        // Could show via bridge to React Native UI
        // For now, use default behavior
        return super.onJsAlert(view, url, message, result)
    }

    override fun onJsConfirm(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
        return super.onJsConfirm(view, url, message, result)
    }

    override fun onJsPrompt(view: WebView?, url: String?, message: String?, defaultValue: String?, result: JsPromptResult?): Boolean {
        return super.onJsPrompt(view, url, message, defaultValue, result)
    }

    override fun onJsBeforeUnload(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
        return super.onJsBeforeUnload(view, url, message, result)
    }

    // ===== Permission Requests =====

    /** Handles geolocation permission requests (legacy API). */
    override fun onGeolocationPermissionsShowPrompt(
        origin: String?,
        callback: GeolocationPermissions.Callback?
    ) {
        // Request permission via bridge
        requestPermission(origin, "geolocation") { granted ->
            callback?.invoke(origin ?: "", granted, false)
        }
    }

    /** Handles geolocation permission hide prompt. */
    override fun onGeolocationPermissionsHidePrompt() {
        super.onGeolocationPermissionsHidePrompt()
    }

    /** Handles permission requests for camera, microphone, etc. (API 21+). */
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    override fun onPermissionRequest(request: PermissionRequest?) {
        request?.let { permRequest ->
            val origin = permRequest.origin.toString()
            val resources = permRequest.resources

            val hasVideo = resources.contains(PermissionRequest.RESOURCE_VIDEO_CAPTURE)
            val hasAudio = resources.contains(PermissionRequest.RESOURCE_AUDIO_CAPTURE)
            val hasMidi = resources.contains(PermissionRequest.RESOURCE_MIDI_SYSEX)

            val permissionType = when {
                hasVideo && hasAudio -> "camera+mic"
                hasVideo -> "camera"
                hasAudio -> "microphone"
                hasMidi -> "midi"
                else -> "unknown"
            }

            requestPermission(origin, permissionType) { granted ->
                if (granted) {
                    permRequest.grant(resources)
                } else {
                    permRequest.deny()
                }
            }
        }
    }

    /** Handles permission request cancellation (API 21+). */
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    override fun onPermissionRequestCanceled(request: PermissionRequest?) {
        super.onPermissionRequestCanceled(request)
    }

    // ===== Window Management =====

    /** Handles window.open() and target="_blank" links. */
    override fun onCreateWindow(
        view: WebView?,
        isDialog: Boolean,
        isUserGesture: Boolean,
        resultMsg: Message?
    ): Boolean {
        // Create a new tab for the popup
        val newTab = tabManager.createTab(url = "about:blank", isPrivate = tab.isPrivate)

        if (newTab != null) {
            // Set up the new WebView as the target
            val targetWebView = newTab.webView
            if (targetWebView != null) {
                val transport = resultMsg.obj as? WebView.WebViewTransport
                transport?.webView = targetWebView
                resultMsg.sendToTarget()
            }
            return true
        }
        return false
    }

    /** Handles closing a window created by window.open(). */
    override fun onCloseWindow(window: WebView?) {
        super.onCloseWindow(window)
        // Find and close the tab associated with this WebView
        tabManager.getAllTabs().firstOrNull { it.webView == window }?.let { tabToClose ->
            tabManager.closeTab(tabToClose.id)
        }
    }

    // ===== File Chooser =====

    /** Handles file input (API 21+). */
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    override fun onShowFileChooser(
        webView: WebView?,
        filePathCallback: ValueCallback<Array<Uri>>?,
        fileChooserParams: FileChooserParams?
    ): Boolean {
        // Store callback for result
        fileChooserCallback = filePathCallback

        // Request file picker via bridge/activity
        requestFileChooser(fileChooserParams)
        return true
    }

    /** Legacy file chooser (API < 21). */
    @Suppress("DEPRECATION")
    override fun onShowFileChooser(
        webView: WebView?,
        valueCallback: ValueCallback<Uri>?,
        fileChooserParams: FileChooserParams?
    ): Boolean {
        fileChooserCallbackSingle = valueCallback
        requestFileChooser(fileChooserParams)
        return true
    }

    /** Legacy file chooser (API < 21, older signature). */
    @Suppress("DEPRECATION")
    override fun onShowFileChooser(
        webView: WebView?,
        valueCallback: ValueCallback<Uri>?,
        acceptType: String?,
        capture: String?
    ): Boolean {
        fileChooserCallbackSingle = valueCallback
        // Trigger file picker
        requestLegacyFileChooser(acceptType, capture)
        return true
    }

    // ===== Fullscreen Video =====

    /** Called when entering fullscreen video. */
    override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
        // Handle fullscreen video
        if (customView != null) {
            callback?.onCustomViewHidden()
            return
        }

        customView = view
        customViewCallback = callback

        // Add to activity's view hierarchy
        val frameLayout = FrameLayout(activity)
        frameLayout.addView(view)
        activity.setContentView(frameLayout)
    }

    /** Called when exiting fullscreen video. */
    override fun onHideCustomView() {
        super.onHideCustomView()
        customViewCallback?.onCustomViewHidden()
        customView = null
        customViewCallback = null
        // Restore WebView content
        activity.setContentView(tab.webView)
    }

    // ===== Console Messages =====

    override fun onConsoleMessage(consoleMessage: android.webkit.ConsoleMessage?): Boolean {
        // Forward console messages to bridge for debugging
        consoleMessage?.let { msg ->
            Log.d("WebConsole", "[${msg.sourceId()}:${msg.lineNumber()}] ${msg.message()}")
        }
        return super.onConsoleMessage(consoleMessage)
    }

    // ===== Helper Methods =====

    /**
     * Requests a permission from the user via the bridge.
     */
    private fun requestPermission(origin: String?, type: String, callback: (Boolean) -> Unit) {
        // This would typically show a permission dialog via React Native
        // For now, we'll use a simple callback mechanism
        // The bridge implementation should handle showing the permission UI
        bridge.onPermissionRequested(
            tab.id,
            origin ?: "",
            type,
            object : PermissionCallback {
                override fun onResult(granted: Boolean) {
                    callback(granted)
                }
            }
        )
    }

    /**
     * Requests a file chooser via the bridge/activity.
     */
    private fun requestFileChooser(params: FileChooserParams?) {
        // Delegate to bridge/activity to show file picker
        bridge.onFileChooserRequested(
            tab.id,
            params?.mode ?: FileChooserParams.MODE_OPEN,
            params?.acceptTypes,
            params?.isCaptureEnabled ?: false,
            object : FileChooserCallback {
                override fun onResult(uris: Array<Uri>?) {
                    fileChooserCallback?.onReceiveValue(uris)
                    fileChooserCallback = null
                }

                override fun onResultSingle(uri: Uri?) {
                    fileChooserCallbackSingle?.onReceiveValue(uri)
                    fileChooserCallbackSingle = null
                }
            }
        )
    }

    /**
     * Legacy file chooser request.
     */
    private fun requestLegacyFileChooser(acceptType: String?, capture: String?) {
        requestFileChooser(null)
    }
}

/**
 * Extension interface for BlancBridge to handle additional Chrome client events.
 * The main BlancBridge interface should be extended or these methods added there.
 */
interface BlancBridgeExtended : BlancBridge {

    /**
     * Called when a permission is requested by a web page.
     */
    fun onPermissionRequested(
        tabId: String,
        origin: String,
        type: String,
        callback: PermissionCallback
    )

    /**
     * Called when a file chooser is requested by a web page.
     */
    fun onFileChooserRequested(
        tabId: String,
        mode: Int,
        acceptTypes: Array<String>?,
        capture: Boolean,
        callback: FileChooserCallback
    )
}

/**
 * Callback for permission request results.
 */
interface PermissionCallback {
    fun onResult(granted: Boolean)
}

/**
 * Callback for file chooser results.
 */
interface FileChooserCallback {
    fun onResult(uris: Array<Uri>?)
    fun onResultSingle(uri: Uri?)
}