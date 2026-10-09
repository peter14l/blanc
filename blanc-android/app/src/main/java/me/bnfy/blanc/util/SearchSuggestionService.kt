package me.bnfy.blanc.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class NavSite(
    val url: String,
    val title: String,
    val domain: String,
    val badge: String = "Suggested Website"
)

data class SuggestionResult(
    val navSites: List<NavSite> = emptyList(),
    val querySuggestions: List<String> = emptyList()
)

/**
 * Service to fetch live search suggestions and auto-suggested websites (Navigation matches),
 * matching the desktop/tauri omnibox algorithm where websites are prioritized at the top.
 */
object SearchSuggestionService {

    private val POPULAR_SITES = listOf(
        NavSite("https://www.google.com", "Google", "google.com"),
        NavSite("https://www.youtube.com", "YouTube", "youtube.com"),
        NavSite("https://www.github.com", "GitHub", "github.com"),
        NavSite("https://www.reddit.com", "Reddit", "reddit.com"),
        NavSite("https://www.wikipedia.org", "Wikipedia", "wikipedia.org"),
        NavSite("https://www.twitter.com", "X (Twitter)", "twitter.com"),
        NavSite("https://www.instagram.com", "Instagram", "instagram.com"),
        NavSite("https://www.amazon.com", "Amazon", "amazon.com"),
        NavSite("https://www.netflix.com", "Netflix", "netflix.com"),
        NavSite("https://www.linkedin.com", "LinkedIn", "linkedin.com"),
        NavSite("https://stackoverflow.com", "Stack Overflow", "stackoverflow.com"),
        NavSite("https://www.twitch.tv", "Twitch", "twitch.tv"),
        NavSite("https://discord.com", "Discord", "discord.com"),
        NavSite("https://chatgpt.com", "ChatGPT", "chatgpt.com"),
        NavSite("https://www.spotify.com", "Spotify", "spotify.com"),
        NavSite("https://duckduckgo.com", "DuckDuckGo", "duckduckgo.com"),
        NavSite("https://news.ycombinator.com", "Hacker News", "news.ycombinator.com"),
        NavSite("https://medium.com", "Medium", "medium.com"),
        NavSite("https://www.facebook.com", "Facebook", "facebook.com")
    )

    suspend fun fetchSuggestions(
        query: String,
        engine: String = "duckduckgo",
        favorites: List<me.bnfy.blanc.storage.Favorite> = emptyList()
    ): SuggestionResult = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isEmpty() || trimmed.startsWith("/") || trimmed.length > 100) {
            return@withContext SuggestionResult()
        }

        val seenUrls = mutableSetOf<String>()
        val navSites = mutableListOf<NavSite>()
        val querySuggestions = mutableListOf<String>()
        val qLower = trimmed.lowercase()

        // 1. Check local bookmarks/favorites first
        for (fav in favorites) {
            val titleMatch = fav.title.lowercase().contains(qLower)
            val urlMatch = fav.url.lowercase().contains(qLower)
            if (titleMatch || urlMatch) {
                if (seenUrls.add(fav.url)) {
                    val domain = try {
                        URL(fav.url).host.removePrefix("www.")
                    } catch (e: Exception) {
                        fav.url
                    }
                    navSites.add(NavSite(fav.url, fav.title.ifBlank { domain }, domain, "Bookmark"))
                }
            }
        }

        // 2. Check local popular sites for instant offline suggested websites
        for (site in POPULAR_SITES) {
            if (site.domain.contains(qLower) || site.title.lowercase().startsWith(qLower)) {
                if (seenUrls.add(site.url)) {
                    navSites.add(site)
                }
            }
        }

        if (trimmed.length < 2) {
            return@withContext SuggestionResult(navSites = navSites.take(3))
        }

        val encoded = try {
            URLEncoder.encode(trimmed, "UTF-8")
        } catch (e: Exception) {
            return@withContext SuggestionResult(navSites = navSites.take(3))
        }

        // 2. Fetch from Google Suggest API (which provides NAVIGATION type websites matching queries)
        try {
            val googleUrl = URL("https://suggestqueries.google.com/complete/search?client=chrome&q=$encoded")
            val connection = (googleUrl.openConnection() as HttpURLConnection).apply {
                connectTimeout = 1500
                readTimeout = 1500
                setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/128.0.0.0")
                requestMethod = "GET"
            }

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val response = reader.readText()
                reader.close()
                connection.disconnect()

                val data = JSONArray(response)
                val queries = data.optJSONArray(1)
                val descs = data.optJSONArray(2)
                val extraData = data.optJSONObject(4)
                val types = extraData?.optJSONArray("google:suggesttype")

                if (queries != null) {
                    for (i in 0 until queries.length()) {
                        val item = queries.optString(i, "")
                        val desc = descs?.optString(i, "") ?: ""
                        val type = types?.optString(i, "") ?: ""

                        val isNav = type.equals("NAVIGATION", ignoreCase = true) ||
                                item.startsWith("http://") ||
                                item.startsWith("https://")

                        if (isNav && item.isNotBlank()) {
                            val cleanUrl = if (!item.startsWith("http://") && !item.startsWith("https://")) {
                                "https://$item"
                            } else item
                            if (seenUrls.add(cleanUrl)) {
                                val domain = try {
                                    URL(cleanUrl).host.removePrefix("www.")
                                } catch (e: Exception) {
                                    cleanUrl
                                }
                                val title = if (desc.isNotBlank()) desc else {
                                    val name = domain.split('.').firstOrNull() ?: domain
                                    name.replaceFirstChar { it.uppercase() }
                                }
                                navSites.add(NavSite(cleanUrl, title, domain, "Suggested Website"))
                            }
                        } else if (item.isNotBlank() && !item.startsWith("http")) {
                            if (!querySuggestions.contains(item)) {
                                querySuggestions.add(item)
                            }
                        }
                    }
                }
            } else {
                connection.disconnect()
            }
        } catch (e: Exception) {
            // Ignore and fallback to DuckDuckGo if query suggestions empty
        }

        // 3. Fallback to DuckDuckGo if query suggestions empty
        if (querySuggestions.isEmpty()) {
            try {
                val ddgUrl = URL("https://duckduckgo.com/ac/?q=$encoded&type=list")
                val connection = (ddgUrl.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 1500
                    readTimeout = 1500
                    setRequestProperty("User-Agent", "Mozilla/5.0 (Android; Mobile)")
                    requestMethod = "GET"
                }

                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    val reader = BufferedReader(InputStreamReader(connection.inputStream))
                    val response = reader.readText()
                    reader.close()
                    connection.disconnect()

                    val jsonArray = JSONArray(response)
                    val suggestionsArray = jsonArray.optJSONArray(1)
                    if (suggestionsArray != null) {
                        for (i in 0 until suggestionsArray.length().coerceAtMost(6)) {
                            val item = suggestionsArray.optString(i, "")
                            if (item.isNotBlank() && !querySuggestions.contains(item)) {
                                querySuggestions.add(item)
                            }
                        }
                    }
                } else {
                    connection.disconnect()
                }
            } catch (e: Exception) {}
        }

        SuggestionResult(
            navSites = navSites.take(3),
            querySuggestions = querySuggestions.take(5)
        )
    }
}
