package me.bnfy.blanc

import android.app.Application
import android.content.Context
import android.webkit.WebView
import androidx.lifecycle.ProcessLifecycleOwner
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel
import me.bnfy.blanc.adblock.AdblockEngine
import me.bnfy.blanc.storage.Repository
import me.bnfy.blanc.tab.TabManager
import me.bnfy.blanc.tab.WebViewFactory
import me.bnfy.blanc.bridge.BlancBridge
import me.bnfy.blanc.bridge.BridgeProtocol
import me.bnfy.blanc.download.DownloadService
import timber.log.Timber

/**
 * Blanc Application class.
 * Initializes core singletons and provides global access.
 */
class BlancApplication : Application() {

    companion object {
        @Suppress("UNUSED_PARAMETER")
        private var instance: BlancApplication? = null
        
        fun getInstance(): BlancApplication = instance!!
    }

    // ===== Core Components =====
    
    private var _repository: Repository? = null
    private var _adblockEngine: AdblockEngine? = null
    private var _tabManager: TabManager? = null
    private var _blancBridge: BlancBridge? = null
    
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    // ===== Lifecycle =====
    
    override fun onCreate() {
        super.onCreate()
        instance = this
        
        // Initialize Timber for logging
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        
        // Initialize WebView (must be called before any WebView creation)
        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)
        
        // Initialize core components lazily via getters
        // This ensures proper order: Repository -> AdblockEngine -> TabManager -> BlancBridge
        
        // Observe app lifecycle for background/foreground handling
        ProcessLifecycleOwner.get().lifecycle.addObserver(AppLifecycleObserver())
    }
    
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        _tabManager?.onTrimMemory(level)
        if (level >= TRIM_MEMORY_RUNNING_LOW) {
            _adblockEngine?.resetDailyStats()
        }
    }
    
    // ===== Component Accessors (Lazy Initialization) =====
    
    val repository: Repository
        get() = _repository ?: synchronized(this) {
            _repository ?: Repository.getInstance(this).also { _repository = it }
        }
    
    val adblockEngine: AdblockEngine
        get() = _adblockEngine ?: synchronized(this) {
            _adblockEngine ?: AdblockEngine.getInstance(this).also { _adblockEngine = it }
        }
    
    val tabManager: TabManager
        get() = _tabManager ?: synchronized(this) {
            _tabManager ?: TabManager(
                context = this,
                bridge = blancBridge,
                webViewFactory = WebViewFactory.createRegular(this),
                adblockEngine = adblockEngine
            ).also { _tabManager = it }
        }
    
    val blancBridge: BlancBridge
        get() = _blancBridge ?: synchronized(this) {
            _blancBridge ?: BlancBridge(
                context = this,
                repository = repository,
                tabManager = tabManager,
                adblockEngine = adblockEngine,
                lifecycleOwner = this as androidx.lifecycle.LifecycleOwner
            ).also { _blancBridge = it }
        }
    
    // ===== Initialization =====
    
    /**
     * Initializes all components asynchronously.
     * Call this early (e.g., in MainActivity.onCreate) to warm up the engine.
     */
    fun initializeAsync(onComplete: (Boolean) -> Unit) {
        scope.launch {
            try {
                // Initialize repository (creates default profile, search engines)
                repository.initializeDefaults()
                
                // Initialize adblock engine
                adblockEngine.initialize { success ->
                    if (success) {
                        Timber.d("Adblock engine initialized successfully")
                    } else {
                        Timber.w("Adblock engine initialization failed")
                    }
                    onComplete(success)
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to initialize Blanc components")
                onComplete(false)
            }
        }
    }
    
    /**
     * Shuts down all components.
     * Called when app is terminating.
     */
    fun shutdown() {
        scope.cancel()
        _tabManager?.destroy()
        _adblockEngine?.shutdown()
        _repository?.close()
    }
}

/**
 * App lifecycle observer for background/foreground handling.
 */
class AppLifecycleObserver : androidx.lifecycle.DefaultLifecycleObserver {
    override fun onStart(owner: androidx.lifecycle.LifecycleOwner) {
        BlancApplication.getInstance().tabManager.onResume()
    }
    
    override fun onStop(owner: androidx.lifecycle.LifecycleOwner) {
        BlancApplication.getInstance().tabManager.onPause()
    }
}