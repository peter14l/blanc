package me.bnfy.blanc.tab

import android.content.Context
import android.webkit.WebView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented tests for WebViewFactory and WebView configuration.
 * These tests run on an Android device/emulator.
 */
@RunWith(AndroidJUnit4::class)
class WebViewFactoryInstrumentedTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun testCreateRegularWebView() {
        val factory = WebViewFactory.createRegular(context)
        val webViewClient = android.webkit.WebViewClient()
        val webChromeClient = android.webkit.WebChromeClient()

        val webView = factory.createWebView(webViewClient, webChromeClient)

        assertNotNull(webView)
        assertEquals(webViewClient, webView.webViewClient)
        assertEquals(webChromeClient, webView.webChromeClient)

        // Test settings
        val settings = webView.settings
        assertTrue(settings.javaScriptEnabled)
        assertTrue(settings.domStorageEnabled)
        assertTrue(settings.databaseEnabled)
        assertEquals(android.webkit.WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE, settings.mixedContentMode)
        assertTrue(settings.useWideViewPort)
        assertTrue(settings.loadWithOverviewMode)
        assertTrue(settings.supportZoom)
        assertTrue(settings.builtInZoomControls)
        assertFalse(settings.displayZoomControls)
        assertFalse(settings.allowFileAccess)
        assertFalse(settings.allowContentAccess)

        webView.destroy()
    }

    @Test
    fun testCreatePrivateWebView() {
        val factory = WebViewFactory.createPrivate(context)
        val webViewClient = android.webkit.WebViewClient()
        val webChromeClient = android.webkit.WebChromeClient()

        val webView = factory.createWebView(webViewClient, webChromeClient)

        assertNotNull(webView)
        val settings = webView.settings
        // Private mode should have same base settings
        assertTrue(settings.javaScriptEnabled)
        assertTrue(settings.domStorageEnabled)

        webView.destroy()
    }

    @Test
    fun testUserAgentContainsBlanc() {
        val factory = WebViewFactory.createRegular(context)
        val webView = factory.createWebView(android.webkit.WebViewClient(), android.webkit.WebChromeClient())

        val userAgent = webView.settings.userAgentString
        assertTrue(userAgent.contains("Blanc/"))

        webView.destroy()
    }

    @Test
    fun testConfigurePrivateCookies() {
        val factory = WebViewFactory.createPrivate(context)
        val webView = factory.createWebView(android.webkit.WebViewClient(), android.webkit.WebChromeClient())

        factory.configurePrivateCookies(webView)

        val cookieManager = android.webkit.CookieManager.getInstance()
        assertTrue(cookieManager.acceptCookie)
        assertFalse(cookieManager.acceptThirdPartyCookies(webView))

        webView.destroy()
    }
}