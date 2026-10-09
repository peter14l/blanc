package me.bnfy.blanc.adblock

import android.webkit.WebView
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantReadWriteLock

/**
 * Engine for injecting cosmetic CSS filters into WebViews.
 *
 * Converts cosmetic filter selectors (e.g., `##.ad-banner`) into CSS rules
 * that hide matching elements (`display: none !important`).
 * Supports domain-specific filters (e.g., `example.com##.ad`).
 */
class CosmeticFilterEngine {

    private val cosmeticFilters = ConcurrentHashMap<String, MutableList<Filter>>()
    private val exceptionFilters = ConcurrentHashMap<String, MutableList<Filter>>()
    private val lock = ReentrantReadWriteLock()
    private val domainCssCache = ConcurrentHashMap<String, String>()

    /**
     * Adds a cosmetic filter to the engine.
     * @param filter The cosmetic filter to add
     */
    fun addCosmeticFilter(filter: Filter) {
        lock.writeLock().lock()
        try {
            val targetMap = if (filter.isException) exceptionFilters else cosmeticFilters
            for (domain in filter.domains) {
                targetMap.computeIfAbsent(domain) { mutableListOf() }.add(filter)
            }
            if (filter.domains.isEmpty()) {
                targetMap.computeIfAbsent("*") { mutableListOf() }.add(filter)
            }
            domainCssCache.clear()
        } finally {
            lock.writeLock().unlock()
        }
    }

    /**
     * Removes a cosmetic filter from the engine.
     * @param filterId The ID of the filter to remove
     */
    fun removeCosmeticFilter(filterId: String) {
        lock.writeLock().lock()
        try {
            for (map in listOf(cosmeticFilters, exceptionFilters)) {
                for (list in map.values) {
                    list.removeIf { it.id == filterId }
                }
            }
            domainCssCache.clear()
        } finally {
            lock.writeLock().unlock()
        }
    }

    /**
     * Clears all cosmetic filters.
     */
    fun clearCosmeticFilters() {
        lock.writeLock().lock()
        try {
            cosmeticFilters.clear()
            exceptionFilters.clear()
            domainCssCache.clear()
        } finally {
            lock.writeLock().unlock()
        }
    }

    /**
     * Generates CSS for all cosmetic filters applicable to the given URL.
     * @param url The URL of the page
     * @return CSS string to inject, or empty string if no filters apply
     */
    fun generateCssForUrl(url: String): String {
        val host = extractHost(url).lowercase()
        domainCssCache[host]?.let { return it }

        lock.readLock().lock()
        try {
            val applicableFilters = mutableListOf<Filter>()
            val exceptionSelectors = mutableSetOf<String>()

            // Collect filters for specific domain
            for (domain in cosmeticFilters.keys) {
                if (domain == "*" || matchesDomain(host, domain)) {
                    applicableFilters.addAll(cosmeticFilters[domain]!!)
                }
            }

            // Collect exception selectors for specific domain
            for (domain in exceptionFilters.keys) {
                if (domain == "*" || matchesDomain(host, domain)) {
                    exceptionSelectors.addAll(exceptionFilters[domain]!!.map { it.pattern })
                }
            }

            // Build CSS, excluding exception selectors
            val cssRules = StringBuilder()
            for (filter in applicableFilters) {
                if (filter.pattern !in exceptionSelectors) {
                    cssRules.append(selectorToCss(filter.pattern)).append("\n")
                }
            }

            val result = cssRules.toString()
            domainCssCache[host] = result
            return result
        } finally {
            lock.readLock().unlock()
        }
    }

    /**
     * Injects cosmetic filters into a WebView for the given URL.
     * @param webView The WebView to inject CSS into
     * @param url The URL of the page being loaded
     * @param callback Optional callback when injection completes
     */
    fun injectCosmeticFilters(
        webView: WebView,
        url: String,
        callback: ((Boolean) -> Unit)? = null
    ) {
        val css = generateCssForUrl(url)
        if (css.isBlank()) {
            callback?.invoke(true)
            return
        }

        // Wrap CSS in a style element injection script
        val js = """
            (function() {
                var style = document.createElement('style');
                style.id = 'blanc-cosmetic-filters';
                style.textContent = ${escapeForJs(css)};
                document.documentElement.appendChild(style);
                true;
            })()
        """.trimIndent()

        webView.evaluateJavascript(js) { result ->
            callback?.invoke(result != "false")
        }
    }

    /**
     * Removes previously injected cosmetic filters from a WebView.
     * @param webView The WebView to clean up
     * @param callback Optional callback when removal completes
     */
    fun removeCosmeticFilters(
        webView: WebView,
        callback: ((Boolean) -> Unit)? = null
    ) {
        val js = """
            (function() {
                var style = document.getElementById('blanc-cosmetic-filters');
                if (style) style.remove();
                true;
            })()
        """.trimIndent()

        webView.evaluateJavascript(js) { result ->
            callback?.invoke(result != "false")
        }
    }

    /**
     * Updates cosmetic filters for a specific URL in an already-loaded page.
     * @param webView The WebView to update
     * @param url The URL of the page
     * @param callback Optional callback when update completes
     */
    fun updateCosmeticFilters(
        webView: WebView,
        url: String,
        callback: ((Boolean) -> Unit)? = null
    ) {
        removeCosmeticFilters(webView) { success ->
            if (success) {
                injectCosmeticFilters(webView, url, callback)
            } else {
                callback?.invoke(false)
            }
        }
    }

    /**
     * Gets the count of cosmetic filters for a specific domain.
     */
    fun getCosmeticFilterCount(domain: String): Int {
        lock.readLock().lock()
        try {
            return cosmeticFilters[domain]?.size ?: 0
        } finally {
            lock.readLock().unlock()
        }
    }

    /**
     * Gets the total count of all cosmetic filters.
     */
    fun getTotalCosmeticFilterCount(): Int {
        lock.readLock().lock()
        try {
            return cosmeticFilters.values.sumOf { it.size }
        } finally {
            lock.readLock().unlock()
        }
    }

    /**
     * Gets the total count of exception cosmetic filters.
     */
    fun getTotalExceptionFilterCount(): Int {
        lock.readLock().lock()
        try {
            return exceptionFilters.values.sumOf { it.size }
        } finally {
            lock.readLock().unlock()
        }
    }

    /**
     * Converts a CSS selector to a hiding rule.
     * e.g., `.ad-banner` -> `.ad-banner { display: none !important; }`
     */
    private fun selectorToCss(selector: String): String {
        // Handle special pseudo-elements and combinators
        val cleanSelector = selector.trim()
        return "$cleanSelector { display: none !important; }"
    }

    /**
     * Escapes a string for use in JavaScript string literal.
     */
    private fun escapeForJs(str: String): String {
        return str
            .replace("\\", "\\\\")
            .replace("'", "\\'")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
            .replace("<", "\\u003C")
            .replace(">", "\\u003E")
            .let { "\"$it\"" }
    }

    private fun extractHost(url: String): String {
        return try {
            java.net.URL(url).host.lowercase()
        } catch (e: Exception) {
            ""
        }
    }

    private fun matchesDomain(host: String, domain: String): Boolean {
        val cleanDomain = domain.removePrefix("~").lowercase()
        val isExclude = domain.startsWith("~")
        val matches = host == cleanDomain || host.endsWith(".${cleanDomain}")
        return if (isExclude) !matches else matches
    }
}