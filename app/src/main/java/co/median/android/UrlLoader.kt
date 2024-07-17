package co.median.android

import co.median.median_core.GoNativeWebviewInterface
import org.json.JSONObject

class UrlLoader(
    private val mainActivity: MainActivity,
    private val usingNpmPackage: Boolean
) {
    private val mWebView: GoNativeWebviewInterface = mainActivity.webView
    lateinit var urlNavigation: UrlNavigation
    private var hasCalledOnPageStarted: Boolean = false
    private var hasCalledShouldOverrideUrlLoading: Boolean = false

    fun loadUrl(url: String?) {
        loadUrl(url, enableNpmCallback = false, isFromTab = false)
    }

    fun loadUrl(url: String?, enableNpmCallback: Boolean) {
        loadUrl(url, enableNpmCallback = enableNpmCallback, isFromTab = false);
    }

    fun loadUrl(url: String?, enableNpmCallback: Boolean, isFromTab: Boolean) {
        if (url == null) return
        mainActivity.postLoadJavascript = null
        mainActivity.postLoadJavascriptForRefresh = null
        if (url.equals("median_logout", ignoreCase = true) || url.equals("gonative_logout", ignoreCase = true))
            mainActivity.logout()
        else
            this.load(url, enableNpmCallback)
        if (!isFromTab && mainActivity.tabManager != null) mainActivity.tabManager.selectTab(url, null)
    }

    fun loadUrlAndJavascript(url: String?, javascript: String, enableNpmCallback: Boolean, isFromTab: Boolean) {
        val currentUrl: String? = this.mWebView.url
        if (url.isNullOrBlank() && currentUrl.isNullOrBlank() && url == currentUrl) {
            mainActivity.runJavascript(javascript)
            mainActivity.postLoadJavascriptForRefresh = javascript
        } else {
            mainActivity.postLoadJavascript = javascript
            mainActivity.postLoadJavascriptForRefresh = javascript
            load(url, enableNpmCallback)
        }
        if (!isFromTab && mainActivity.tabManager != null) mainActivity.tabManager.selectTab(url, javascript)
    }

    private fun load(url: String?, enableNpmCallback: Boolean) {
        if (url.isNullOrBlank()) return
        if (usingNpmPackage && enableNpmCallback && mainActivity.eventsManager.hasCallbackEvent(NPM_CALLBACK)) {
            // intercept and execute if javascript
            if (url.startsWith("javascript:")) {
                mainActivity.runJavascript(url.substring("javascript:".length))
                return
            }

            if (urlNavigation.shouldOverrideUrlLoadingNoIntercept(mWebView, url, false))
                // intercepted by the app
                return

            runUrlChangedEvent(url)
        } else {
            mWebView.loadUrl(url)
        }
    }

    fun notifyOverrideUrlCalled() {
        hasCalledShouldOverrideUrlLoading = true
    }

    fun notifyOnPageStartedCalled() {
        hasCalledOnPageStarted = true
    }

    // For single-page apps, ensure proper URL handling by manually invoking UrlNavigation.shouldOverrideUrlLoading() and
    // UrlNavigation.onPageStarted() when they are not called during load.
    fun onHistoryUpdated(url: String?) {
        if (!usingNpmPackage) return

        if (!hasCalledShouldOverrideUrlLoading &&
                urlNavigation.shouldOverrideUrlLoadingNoIntercept(mWebView, url, false)) {
            this.hasCalledShouldOverrideUrlLoading = true
            mWebView.stopLoading()
            mWebView.goBack()
            return
        }

        if (!hasCalledOnPageStarted)
            urlNavigation.onPageStarted(url)

        // reset
        hasCalledOnPageStarted = false
        hasCalledShouldOverrideUrlLoading = false
    }

    private fun runUrlChangedEvent(url: String?) {
        if (url.isNullOrBlank()) return
        mainActivity.invokeCallback(NPM_CALLBACK, JSONObject().put("url", url))
    }

    companion object {
        private const val NPM_CALLBACK = "_median_url_changed"
    }
}