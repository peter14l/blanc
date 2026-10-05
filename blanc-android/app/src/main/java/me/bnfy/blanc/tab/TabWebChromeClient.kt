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
import android.webkit.WebAuthnClient
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.widget.FrameLayout
import androidx.annotation.Keep
import androidx.annotation.RequiresApi
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import java.util.concurrent.Executor

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
 * - WebAuthn / Passkeys (Biometric authentication)
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
    
    // WebAuthn
    private var webAuthnClient: WebAuthnClient? = null
    private var biometricPrompt: BiometricPrompt? = null

    companion object {
        private const val TAG = "TabWebChromeClient"
        private const val FILE_CHOOSER_REQUEST_CODE = 0x1001
    }

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            webAuthnClient = WebAuthnClient(activity)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            biometricPrompt = BiometricPrompt(
                activity as androidx.fragment.app.FragmentActivity,
                ContextCompat.getMainExecutor(activity),
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        // WebAuthn authentication succeeded
                        // The WebAuthnClient handles the rest
                    }
                    
                    override fun onAuthenticationFailed() {
                        // WebAuthn authentication failed
                    }
                    
                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        // WebAuthn authentication error
                    }
                }
            )
        }
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

    /** Handles WebAuthn / Passkey requests (API 24+). */
    @RequiresApi(Build.VERSION_CODES.N)
    override fun onReceivedWebAuthnRequest(
        view: WebView?,
        request: WebAuthnClient.WebAuthnRequest?,
        callback: WebAuthnClient.Callback?
    ) {
        request?.let { webAuthnRequest ->
            callback?.let { webAuthnCallback ->
                // Handle WebAuthn request with biometric authentication
                handleWebAuthnRequest(webAuthnRequest, webAuthnCallback)
            }
        }
    }
    
    /**
     * Handles WebAuthn authentication requests using biometric prompt.
     */
    @RequiresApi(Build.VERSION_CODES.N)
    private fun handleWebAuthnRequest(
        request: WebAuthnClient.WebAuthnRequest,
        callback: WebAuthnClient.Callback
    ) {
        // For WebAuthn, we need to show biometric prompt
        // The WebAuthnClient handles the actual cryptographic operations
        // We just need to trigger the biometric authentication
        
        biometricPrompt?.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Verify your identity")
                .setSubtitle("Use biometric to authenticate with ${request.origin}")
                .setNegativeButtonText("Cancel")
                .build(),
            object : BiometricPrompt.CryptoObject(null) {
                // No crypto object needed for basic WebAuthn
            }
        ) { result ->
            // The WebAuthnClient will handle the response through the callback
            // We just need to signal that user authentication completed
            callback.onWebAuthnAuthenticationComplete(
                request.requestId,
                WebAuthnClient.Result.SUCCESS,
                null
            )
        }
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