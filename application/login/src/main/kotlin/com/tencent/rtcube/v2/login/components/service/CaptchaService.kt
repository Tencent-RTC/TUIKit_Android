package com.tencent.rtcube.v2.login.components.service

import android.app.Activity
import android.graphics.Color
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.PopupWindow
import androidx.core.os.ConfigurationCompat
import com.tencent.rtcube.v2.login.LoginEntry
import com.tencent.rtcube.v2.login.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Loads TCaptcha.js in a WebView to perform image verification and returns ticket / randStr on success.
 */
class CaptchaService {

    companion object {
        private const val TAG = "CaptchaService"
        private const val T_CAPTCHA_URL = "https://turing.captcha.qcloud.com/TCaptcha.js"
        private const val NETWORK_DISABLED_TICKET = "terror_1001_"
        private const val VERIFICATION_HTML_PATH = "file:///android_asset/verification.html"
        private const val JS_BRIDGE_NAME = "imageVerificationJsBridge"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val networkService = LoginNetworkService()

    private var popupWindow: PopupWindow? = null
    private var webView: WebView? = null
    private var isInitialized = false

    private var currentWebAppId: String = ""

    /**
     * Initialize WebView and PopupWindow in advance to avoid per-verify creation cost.
     */
    @Suppress("SetJavaScriptEnabled")
    private fun initWebView(
        activity: Activity,
        onSuccess: (CaptchaResult) -> Unit,
        onFailed: (String) -> Unit,
    ) {
        val wv = WebView(activity).apply {
            setBackgroundColor(Color.TRANSPARENT)
            settings.apply {
                useWideViewPort = true
                loadWithOverviewMode = true
                cacheMode = WebSettings.LOAD_NO_CACHE
                javaScriptEnabled = true
            }
        }

        wv.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, url: String?): Boolean {
                url?.let { view.loadUrl(it) }
                return true
            }
        }

        wv.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(message: String?, lineNumber: Int, sourceID: String?) {
                Log.d(TAG, "JS: $message")
            }
        }

        setupJsBridge(wv, activity, onSuccess, onFailed)

        val pop = PopupWindow(wv, WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT, true).apply {
            setBackgroundDrawable(null)
        }

        webView = wv
        popupWindow = pop
        isInitialized = true
    }

    private fun setupJsBridge(wv: WebView, activity: Activity, onSuccess: (CaptchaResult) -> Unit, onFailed: (String) -> Unit) {
        wv.addJavascriptInterface(ImageVerificationJsBridge(
            onVerifySuccess = { ticket, randStr ->
                activity.runOnUiThread {
                    dismissPopup()
                    onSuccess(CaptchaResult(ticket, randStr, currentWebAppId))
                }
            },
            onVerifyError = { errorCode, errorMsg ->
                activity.runOnUiThread {
                    dismissPopup()
                    onFailed(LoginEntry.appContext.getString(R.string.login_error_captcha_verify_failed, errorMsg, errorCode))
                }
            }
        ), JS_BRIDGE_NAME)
    }

    fun verify(
        activity: Activity,
        onSuccess: (CaptchaResult) -> Unit,
        onFailed: (String) -> Unit,
    ) {
        if (!isInitialized) {
            initWebView(activity, onSuccess, onFailed)
        }

        scope.launch {
            val captchaReachable = withContext(Dispatchers.IO) {
                checkTCaptchaReachable()
            }

            if (captchaReachable) {
                startImageVerification(activity, onSuccess, onFailed)
            } else {
                Log.w(TAG, "TCaptcha.js unreachable, fallback to local ticket")
                startRequestVerifyCodeWithLocalData(onSuccess, onFailed)
            }
        }
    }

    private fun startImageVerification(
        activity: Activity,
        onSuccess: (CaptchaResult) -> Unit,
        onFailed: (String) -> Unit,
    ) {
        val wv = webView ?: return
        val popup = popupWindow ?: return
        if (popup.isShowing) {
            popup.dismiss()
        }

        wv.removeJavascriptInterface(JS_BRIDGE_NAME)
        setupJsBridge(wv, activity, onSuccess, onFailed)

        wv.loadUrl(VERIFICATION_HTML_PATH)
        wv.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String?) {
                super.onPageFinished(view, url)
                scope.launch {
                    val webAppId = fetchWebAppId()
                    val lang = if (getCurrentLanguage(activity) == "en") "en" else "zh-cn"
                    if (webAppId.isNotEmpty()) {
                        currentWebAppId = webAppId
                        view.evaluateJavascript("javascript:callVerifyJS($webAppId,'$lang')") { value ->
                            Log.d(TAG, "onReceiveValue: $value")
                        }
                    } else {
                        activity.runOnUiThread { onFailed("Failed to get webAppId") }
                    }
                }
            }
        }

        val contentView = activity.window.decorView
        popup.showAtLocation(contentView, Gravity.CENTER, 0, 0)
    }

    /**
     * Build a fallback ticket locally when TCaptcha.js is unreachable.
     */
    private suspend fun startRequestVerifyCodeWithLocalData(
        onSuccess: (CaptchaResult) -> Unit,
        onFailed: (String) -> Unit,
    ) {
        val webAppId = fetchWebAppId()
        if (webAppId.isEmpty()) {
            onFailed("Failed to get webAppId")
            return
        }
        val ticket = NETWORK_DISABLED_TICKET + webAppId + "_" + (System.currentTimeMillis() / 1000)
        val randStr = "@" + getRandomString(11)
        onSuccess(CaptchaResult(ticket, randStr, webAppId))
    }

    private suspend fun fetchWebAppId(): String {
        return try {
            val result = networkService.getImageCaptcha()
            result.getOrNull().orEmpty()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch captchaWebAppId", e)
            ""
        }
    }

    private fun checkTCaptchaReachable(): Boolean {
        return try {
            val connection = URL(T_CAPTCHA_URL).openConnection() as HttpURLConnection
            connection.connectTimeout = 3000
            connection.readTimeout = 3000
            // Use GET instead of HEAD: Tencent Cloud CDN returns 404 on HEAD for JS files
            connection.requestMethod = "GET"
            connection.instanceFollowRedirects = true
            val code = connection.responseCode
            connection.disconnect()
            Log.d(TAG, "TCaptcha reachable check: responseCode=$code")
            code in 200..399
        } catch (e: Exception) {
            Log.e(TAG, "TCaptcha reachable check failed", e)
            false
        }
    }

    private fun dismissPopup() {
        popupWindow?.let { if (it.isShowing) it.dismiss() }
    }

    fun destroy() {
        dismissPopup()
        webView?.destroy()
        webView = null
        popupWindow = null
        isInitialized = false
        currentWebAppId = ""
        scope.cancel()
    }

    private fun getRandomString(length: Int): String {
        val chars = "abcdefghijklmnopqrstuvwxyz0123456789"
        return (1..length).map { chars.random() }.joinToString("")
    }
    
    private fun getCurrentLanguage(activity: Activity): String {
        return ConfigurationCompat.getLocales(activity.resources.configuration)[0]?.language ?: "zh-cn"
    }
}

/**
 * JS-Native bridge for image verification.
 */
private class ImageVerificationJsBridge(
    private val onVerifySuccess: (ticket: String, randStr: String) -> Unit,
    private val onVerifyError: (errorCode: Int, errorMsg: String) -> Unit,
) {
    @JavascriptInterface
    fun verifySuccess(ticket: String, randStr: String) {
        onVerifySuccess(ticket, randStr)
    }

    @JavascriptInterface
    fun verifyError(errorCode: Int, errorMsg: String) {
        onVerifyError(errorCode, errorMsg)
    }
}
