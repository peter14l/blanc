package me.bnfy.blanc.util

import android.content.Context
import android.webkit.WebSettings

object UserAgentUtils {
    /**
     * Builds a clean Chrome-compatible mobile user agent string.
     *
     * Standard Android WebView injects WebView tokens ('; wv', 'Version/4.0')
     * and custom app tokens (like 'Blanc/1.0.9') which cause major search engines
     * (particularly Google Search, reCAPTCHA, and Cloudflare) to flag requests as
     * coming from automated in-app scrapers, triggering CAPTCHA / "you are not a robot"
     * challenges.
     *
     * This method cleans the default User-Agent to match standard mobile Chrome,
     * mirroring desktop Blanc's chromeLikeUserAgent policy.
     */
    fun buildMobileUserAgent(context: Context): String {
        val baseUa = try {
            WebSettings.getDefaultUserAgent(context)
        } catch (e: Exception) {
            "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36"
        }
        return baseUa
            .replace("; wv", "")
            .replace(";wv", "")
            .replace("Version/4.0 ", "")
            .replace(Regex("""\sBlanc/[\d.]+""", RegexOption.IGNORE_CASE), "")
            .trim()
    }

    const val DESKTOP_USER_AGENT =
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36"
}
