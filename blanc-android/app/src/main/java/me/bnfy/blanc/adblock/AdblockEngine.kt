package me.bnfy.blanc.adblock

import android.content.Context
import android.webkit.WebResourceResponse
import android.webkit.WebResourceRequest
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.locks.ReentrantReadWriteLock
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Core ad-blocking engine for Blanc Android.
 *
 * Loads and parses EasyList + EasyPrivacy filter lists from assets,
 * provides thread-safe blocking decisions for network requests,
 * and manages cosmetic filter injection.
 *
 * Features:
 * - Network request blocking (||example.com^)
 * - Exception rules (@@||example.com^)
 * - Cosmetic element hiding (##.ad-banner)
 * - Domain-specific filters (example.com##.ad)
 * - Per-host exceptions (allowlist)
 * - Statistics tracking
 * - Filter list reloading
 */
class AdblockEngine private constructor(
    private val context: Context,
    private val cosmeticEngine: CosmeticFilterEngine
) {

    private val networkFilters = ConcurrentHashMap<String, MutableList<Filter>>()
    private val exceptionFilters = ConcurrentHashMap<String, MutableList<Filter>>()
    private val lock = ReentrantReadWriteLock()
    private val enabled = AtomicBoolean(true)
    private val initialized = AtomicBoolean(false)

    // Statistics
    private val totalBlocked = AtomicLong(0)
    private val todayBlocked = AtomicLong(0)
    private val exceptions = ConcurrentHashMap<String, Boolean>()

    // Background executor for filter loading
    private val backgroundExecutor = Executors.newSingleThreadExecutor()

    companion object {
        @Suppress("UNUSED_PARAMETER")
        private var instance: AdblockEngine? = null
        private val instanceLock = Any()

        /**
         * Gets the singleton AdblockEngine instance, creating it if necessary.
         * @param context Application context
         * @return The AdblockEngine instance
         */
        fun getInstance(context: Context): AdblockEngine {
            synchronized(instanceLock) {
                if (instance == null) {
                    val cosmeticEngine = CosmeticFilterEngine()
                    instance = AdblockEngine(context.applicationContext, cosmeticEngine)
                }
                return instance!!
            }
        }

        /**
         * Creates a new AdblockEngine instance (for testing).
         */
        fun createForTesting(context: Context): AdblockEngine {
            val cosmeticEngine = CosmeticFilterEngine()
            return AdblockEngine(context.applicationContext, cosmeticEngine)
        }

        /**
         * Resets the singleton instance (for testing).
         */
        fun resetInstance() {
            synchronized(instanceLock) {
                instance?.shutdown()
                instance = null
            }
        }
    }

    /**
     * Initializes the engine by loading filter lists from assets.
     * Must be called before using the engine.
     * @param callback Called when initialization completes
     */
    fun initialize(callback: (Boolean) -> Unit) {
        if (initialized.getAndSet(true)) {
            callback(true)
            return
        }

        backgroundExecutor.execute {
            try {
                loadFilterLists()
                callback(true)
            } catch (e: Exception) {
                callback(false)
            }
        }
    }

    /**
     * Loads EasyList and EasyPrivacy from assets.
     */
    private fun loadFilterLists() {
        val parser = FilterListParser()

        // Load EasyList
        loadAssetFilterList("filters/easylist.txt", parser) { filters ->
            addFilters(filters)
        }

        // Load EasyPrivacy
        loadAssetFilterList("filters/easyprivacy.txt", parser) { filters ->
            addFilters(filters)
        }
    }

    /**
     * Loads a filter list from assets and parses it.
     */
    private fun loadAssetFilterList(assetPath: String, parser: FilterListParser, onParsed: (List<Filter>) -> Unit) {
        context.assets.open(assetPath).use { inputStream ->
            val content = String(inputStream.readAllBytes(), StandardCharsets.UTF_8)
            val filters = parser.parseFilterList(content)
            onParsed(filters)
        }
    }

    /**
     * Adds parsed filters to the engine.
     */
    private fun addFilters(filters: List<Filter>) {
        lock.writeLock().lock()
        try {
            for (filter in filters) {
                when (filter.type) {
                    FilterType.NETWORK -> {
                        val targetMap = if (filter.isException) exceptionFilters else networkFilters
                        for (domain in filter.domains) {
                            targetMap.computeIfAbsent(domain) { mutableListOf() }.add(filter)
                        }
                        if (filter.domains.isEmpty()) {
                            targetMap.computeIfAbsent("*") { mutableListOf() }.add(filter)
                        }
                    }
                    FilterType.COSMETIC, FilterType.HTML -> {
                        cosmeticEngine.addCosmeticFilter(filter)
                    }
                }
            }
        } finally {
            lock.writeLock().unlock()
        }
    }

    /**
     * Determines if a network request should be blocked.
     * @param url The URL of the resource being requested
     * @param request The WebResourceRequest (for additional context like request type)
     * @return true if the request should be blocked
     */
    fun shouldBlock(url: String, request: WebResourceRequest?): Boolean {
        if (!enabled.get()) return false

        val sourceUrl = request?.let { getReferrerUrl(it) }
        val requestType = request?.let { getRequestType(it) } ?: "other"

        // Check exception filters first (allowlist)
        if (matchesAnyFilter(url, sourceUrl, requestType, exceptionFilters)) {
            return false
        }

        // Check per-host exceptions (user allowlist)
        val host = extractHost(url)
        if (exceptions.containsKey(host)) {
            return false
        }

        // Check network blocking filters
        return matchesAnyFilter(url, sourceUrl, requestType, networkFilters)
    }

    /**
     * Checks if URL matches any filter in the given map.
     */
    private fun matchesAnyFilter(
        url: String,
        sourceUrl: String?,
        requestType: String,
        filterMap: ConcurrentHashMap<String, MutableList<Filter>>
    ): Boolean {
        lock.readLock().lock()
        try {
            // Check domain-specific filters
            val host = extractHost(url)
            for (domain in filterMap.keys) {
                if (domain == "*" || matchesDomain(host, domain)) {
                    for (filter in filterMap[domain]!!) {
                        if (filter.matches(url, sourceUrl, requestType)) {
                            return true
                        }
                    }
                }
            }
            return false
        } finally {
            lock.readLock().unlock()
        }
    }

    /**
     * Creates a blocked response (empty 204).
     */
    fun createBlockedResponse(): WebResourceResponse {
        totalBlocked.incrementAndGet()
        todayBlocked.incrementAndGet()
        return WebResourceResponse(
            "text/plain",
            "utf-8",
            ByteArrayInputStream(emptyByteArray())
        )
    }

    /**
     * Adds a hostname to the exception list (user allowlist).
     */
    fun addException(hostname: String) {
        exceptions[hostname.lowercase()] = true
    }

    /**
     * Removes a hostname from the exception list.
     */
    fun removeException(hostname: String) {
        exceptions.remove(hostname.lowercase())
    }

    /**
     * Checks if a hostname is in the exception list.
     */
    fun isException(hostname: String): Boolean {
        return exceptions.containsKey(hostname.lowercase())
    }

    /**
     * Gets all exception hostnames.
     */
    fun getExceptions(): List<String> {
        return exceptions.keys.toList()
    }

    /**
     * Enables or disables the adblock engine.
     */
    fun setEnabled(enabled: Boolean) {
        this.enabled.set(enabled)
    }

    /**
     * Checks if the adblock engine is enabled.
     */
    fun isEnabled(): Boolean = enabled.get()

    /**
     * Reloads filter lists from assets.
     */
    fun reloadLists(callback: (Boolean) -> Unit) {
        backgroundExecutor.execute {
            lock.writeLock().lock()
            try {
                networkFilters.clear()
                exceptionFilters.clear()
                cosmeticEngine.clearCosmeticFilters()
            } finally {
                lock.writeLock().unlock()
            }

            try {
                loadFilterLists()
                callback(true)
            } catch (e: Exception) {
                callback(false)
            }
        }
    }

    /**
     * Gets the CosmeticFilterEngine for cosmetic filter operations.
     */
    fun getCosmeticEngine(): CosmeticFilterEngine = cosmeticEngine

    /**
     * Gets ad-blocking statistics.
     */
    fun getStats(): AdblockStats {
        lock.readLock().lock()
        try {
            var filterCount = 0
            var exceptionFilterCount = 0
            for (list in networkFilters.values) filterCount += list.size
            for (list in exceptionFilters.values) exceptionFilterCount += list.size

            return AdblockStats(
                totalBlocked = totalBlocked.get(),
                todayBlocked = todayBlocked.get(),
                exceptionCount = exceptions.size,
                networkFilterCount = filterCount,
                exceptionFilterCount = exceptionFilterCount,
                cosmeticFilterCount = cosmeticEngine.getTotalCosmeticFilterCount(),
                isEnabled = enabled.get()
            )
        } finally {
            lock.readLock().unlock()
        }
    }

    /**
     * Resets today's blocked count (call daily).
     */
    fun resetDailyStats() {
        todayBlocked.set(0)
    }

    /**
     * Shuts down the engine and releases resources.
     */
    fun shutdown() {
        backgroundExecutor.shutdown()
    }

    // ===== Helper Methods =====

    private fun getReferrerUrl(request: WebResourceRequest): String? {
        val headers = request.requestHeaders
        return headers["Referer"] ?: headers["referer"]
    }

    private fun getRequestType(request: WebResourceRequest): String {
        // Determine request type from headers and URL
        val url = request.url.toString()
        val contentType = request.requestHeaders["Content-Type"] ?: request.requestHeaders["content-type"] ?: ""

        return when {
            url.endsWith(".js") || contentType.contains("javascript") -> "script"
            url.matches(".*\\.(png|jpg|jpeg|gif|webp|svg|ico)(\\?.*)?$") || contentType.startsWith("image/") -> "image"
            url.matches(".*\\.(css)(\\?.*)?$") || contentType.contains("css") -> "stylesheet"
            request.method == "POST" || contentType.contains("xml") || contentType.contains("json") -> "xmlhttprequest"
            url.matches(".*\\.(woff|woff2|ttf|eot)(\\?.*)?$") -> "font"
            request.isForMainFrame -> "document"
            true -> "other"
        }
    }

    private fun extractHost(url: String): String {
        return try {
            java.net.URL(url).host.toLowerCase()
        } catch (e: Exception) {
            ""
        }
    }

    private fun matchesDomain(host: String, domain: String): Boolean {
        val cleanDomain = domain.trimStart("~").lowercase()
        val isExclude = domain.startsWith("~")
        val matches = host == cleanDomain || host.endsWith(".${cleanDomain}")
        return if (isExclude) !matches else matches
    }

    private val emptyByteArray = ByteArray(0)
}

/**
 * Statistics for the adblock engine.
 */
data class AdblockStats(
    val totalBlocked: Long,
    val todayBlocked: Long,
    val exceptionCount: Int,
    val networkFilterCount: Int,
    val exceptionFilterCount: Int,
    val cosmeticFilterCount: Int,
    val isEnabled: Boolean
)