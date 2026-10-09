package me.bnfy.blanc

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.Lifecycle
import kotlinx.coroutines.launch
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import me.bnfy.blanc.adblock.AdblockEngine
import me.bnfy.blanc.bridge.BlancBridge
import me.bnfy.blanc.bridge.BridgeProtocol
import me.bnfy.blanc.download.DownloadService
import me.bnfy.blanc.storage.DownloadEntity
import me.bnfy.blanc.storage.Repository
import me.bnfy.blanc.tab.Tab
import me.bnfy.blanc.tab.TabManager
import me.bnfy.blanc.tab.WebViewFactory
import me.bnfy.blanc.ui.BrowserScreen
import me.bnfy.blanc.ui.ContentWebView
import timber.log.Timber

/**
 * Main Activity - Entry point for Blanc Browser.
 * Uses Jetpack Compose for the browser chrome UI.
 * Content tabs are native WebViews managed by TabManager.
 */
class MainActivity : AppCompatActivity() {

    // ===== State =====
    private var tabManager: TabManager? = null
    private var repository: Repository? = null
    private var adblockEngine: AdblockEngine? = null
    private var blancBridge: BlancBridge? = null
    private var isInitialized = false
    
    // File chooser callback for WebView
    private var fileChooserCallback: ValueCallback<Array<Uri>>? = null
    private val fileChooserLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.data
        val uris = if (uri != null) arrayOf(uri) else emptyArray()
        fileChooserCallback?.onReceiveValue(uris)
        fileChooserCallback = null
    }

    // ===== Lifecycle =====

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Get app instance and components
        val app = BlancApplication.getInstance()
        tabManager = app.tabManager
        repository = app.repository
        adblockEngine = app.adblockEngine
        blancBridge = app.blancBridge
        
        // Set activity reference on TabManager for WebChromeClient
        tabManager?.setActivity(this)
        
        // Initialize components
        app.initializeAsync { success ->
            isInitialized = success
            if (success) {
                Timber.d("Blanc initialized successfully")
            } else {
                Timber.e("Blanc initialization failed")
            }
        }
        
        // Handle intent (deep links, etc.)
        handleIntent(intent)
        
        // Ensure at least one tab exists if not opened from a specific intent
        if (tabManager?.getTabCount() == 0) {
            tabManager?.createTab()
        }
        
        // Set up Compose UI
        setContent {
            val themeMode by repository!!.theme.collectAsState(initial = "system")
            me.bnfy.blanc.ui.theme.BlancTheme(theme = themeMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    BrowserScreen(
                        tabManager = tabManager!!,
                        repository = repository!!,
                        adblockEngine = adblockEngine!!,
                        blancBridge = blancBridge!!,
                        onFileChooser = { callback, acceptTypes ->
                            fileChooserCallback = callback
                            val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                                addCategory(Intent.CATEGORY_OPENABLE)
                                type = acceptTypes.firstOrNull() ?: "*/*"
                                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                            }
                            fileChooserLauncher.launch(intent)
                        },
                        onRequestPermission = { origin, resource, callback ->
                            requestPermission(origin, resource, callback)
                        }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        val action = intent.action
        val data = intent.data
        
        when {
            action == Intent.ACTION_VIEW && data != null -> {
                val url = data.toString()
                if (url.startsWith("blanc://")) {
                    handleBlancUrl(url)
                } else {
                    tabManager?.createTab(url)
                }
            }
            action == Intent.ACTION_SEND -> {
                // Handle share intent
                intent.getStringExtra(Intent.EXTRA_TEXT)?.let { text ->
                    tabManager?.createTab(text)
                }
            }
        }
    }

    private fun handleBlancUrl(url: String) {
        when {
            url == "blanc://newtab" -> tabManager?.createTab()
            url == "blanc://newtab?private=1" -> tabManager?.createPrivateTab()
            url == "blanc://settings" -> {
                // Navigate to settings panel - handled by Compose UI
            }
            url.startsWith("blanc://mahjong") -> tabManager?.createTab(url)
            else -> tabManager?.createTab(url)
        }
    }

    private fun requestPermission(origin: String, resource: String, callback: (Boolean) -> Unit) {
        val permission = when (resource) {
            "camera" -> android.Manifest.permission.CAMERA
            "microphone" -> android.Manifest.permission.RECORD_AUDIO
            "geolocation" -> android.Manifest.permission.ACCESS_FINE_LOCATION
            else -> return callback(false)
        }
        
        val rationale = "Allow $resource access for $origin?"
        
        if (shouldShowRequestPermissionRationale(permission)) {
            // Show rationale dialog then request
            // For now, just request
        }
        
        requestPermissions(arrayOf(permission), 1000)
        // Note: In real implementation, use ActivityResultContracts.RequestPermission
        // and correlate with callback
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        // Handle permission results
    }

    override fun onResume() {
        super.onResume()
        tabManager?.onResume()
    }

    override fun onPause() {
        super.onPause()
        tabManager?.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Cleanup is handled by Application shutdown
    }
}