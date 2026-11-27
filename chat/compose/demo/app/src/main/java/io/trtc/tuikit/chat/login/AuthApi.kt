package io.trtc.tuikit.chat.login

import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.util.Log
import com.tencent.mmkv.MMKV
import io.trtc.tuikit.chat.common.AppConstants
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

object AuthApi {
    private const val TAG = "AuthApi"

    private const val GS_BASE_URL = "https://demos.trtc.tencent-cloud.com"
    private const val APAAS_APP_ID = "1026227964"
    private const val ENV_PROD = "/prod"
    private const val API_GSLB = "/base/v1/gslb"
    private const val API_GET_SMS = "/base/v1/auth_users/user_verify_by_picture"
    private const val API_LOGIN_CODE = "/base/v1/auth_users/user_login_code"
    private const val API_LOGIN_TOKEN = "/base/v1/auth_users/user_login_token"
    private const val API_DELETE_ACCOUNT = "/base/v1/auth_users/user_delete"

    interface RequestCallback<T> {
        fun onError(code: Int, message: String?)
        fun onSuccess(result: T)
    }

    data class LoginResult(
        val userId: String,
        val userSig: String,
        val sdkAppId: Int,
        val token: String
    )

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val ioExecutor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    private val sensitiveParamRegexes = listOf(
        "token", "phone", "code", "sessionId", "ticket", "randstr", "userId", "userSig"
    ).map { Regex("(${Regex.escape(it)}=)[^&\\s]*", RegexOption.IGNORE_CASE) }

    private fun redactSensitive(text: String?): String {
        if (text.isNullOrEmpty()) {
            return text.orEmpty()
        }
        var sanitized: String = text
        for (regex in sensitiveParamRegexes) {
            sanitized = regex.replace(sanitized, "$1***")
        }
        return sanitized
    }

    @Volatile private var service: String = ""
    @Volatile private var captchaWebAppId: String = ""
    @Volatile private var inited = false

    private val pendingInit = mutableListOf<Pair<() -> Unit, (Int, String?) -> Unit>>()
    private var initing = false

    private fun currentLanguage(): String {
        val tag = MMKV.defaultMMKV().decodeString(AppConstants.KEY_APP_LANGUAGE, "").orEmpty()
            .ifBlank { Locale.getDefault().language }
        return when {
            tag.startsWith("zh", ignoreCase = true) -> "zh"
            tag.startsWith("ar", ignoreCase = true) -> "ar"
            tag.startsWith("en", ignoreCase = true) -> "en"
            else -> "en"
        }
    }

    fun captchaUrlLanguage(): String {
        return when (currentLanguage()) {
            "zh" -> "zh-cn"
            "en" -> "en"
            else -> ""
        }
    }

    private fun <T> postSuccess(cb: RequestCallback<T>, result: T) {
        mainHandler.post { cb.onSuccess(result) }
    }

    private fun <T> postError(cb: RequestCallback<T>, code: Int, message: String?) {
        mainHandler.post { cb.onError(code, message) }
    }

    private fun ensureInit(onReady: () -> Unit, onError: (Int, String?) -> Unit) {
        if (inited) {
            onReady()
            return
        }
        val shouldRequest: Boolean
        synchronized(pendingInit) {
            pendingInit.add(onReady to onError)
            shouldRequest = !initing
            if (shouldRequest) {
                initing = true
            }
        }
        if (!shouldRequest) {
            return
        }
        val request = Request.Builder().url(GS_BASE_URL + ENV_PROD + API_GSLB).get().build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "gslb failed: ${redactSensitive(e.message)}")
                flushInit(false, -1, e.message)
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    val resJson = JSONObject(response.body?.string().orEmpty())
                    val errorCode = resJson.optInt("errorCode")
                    if (errorCode != 0) {
                        val msg = errorMessage(resJson, resJson.optString("errorMessage"))
                        flushInit(false, errorCode, msg)
                        return
                    }
                    val data = resJson.getJSONObject("data")
                    captchaWebAppId = data.getString("captcha_web_appid")
                    service = data.getString("service")
                    inited = true
                    flushInit(true, 0, null)
                } catch (e: Exception) {
                    Log.e(TAG, "gslb parse error: ${redactSensitive(e.message)}")
                    flushInit(false, -1, e.message)
                }
            }
        })
    }

    private fun flushInit(success: Boolean, code: Int, message: String?) {
        val callbacks: List<Pair<() -> Unit, (Int, String?) -> Unit>>
        synchronized(pendingInit) {
            callbacks = pendingInit.toList()
            pendingInit.clear()
            initing = false
        }
        mainHandler.post {
            callbacks.forEach { (onReady, onError) ->
                if (success) onReady() else onError(code, message)
            }
        }
    }

    private fun buildQuery(params: Map<String, String>): String {
        val sb = StringBuilder("?")
        params.entries.forEachIndexed { index, entry ->
            if (index > 0) {
                sb.append("&")
            }
            sb.append(URLEncoder.encode(entry.key, "UTF-8"))
                .append("=")
                .append(URLEncoder.encode(entry.value, "UTF-8"))
        }
        sb.append("&apaasAppId=").append(APAAS_APP_ID)
        return sb.toString()
    }

    fun getCaptchaAppId(cb: RequestCallback<String>) {
        ensureInit({ postSuccess(cb, captchaWebAppId) }, { code, msg -> postError(cb, code, msg) })
    }

    fun getSms(
        captchaAppId: String,
        ticket: String,
        randStr: String,
        phone: String,
        cb: RequestCallback<String>
    ) {
        val params = linkedMapOf(
            "appId" to captchaAppId,
            "ticket" to ticket,
            "randstr" to randStr,
            "phone" to phone
        )
        request(API_GET_SMS, params, object : RequestCallback<JSONObject> {
            override fun onError(code: Int, message: String?) = postError(cb, code, message)
            override fun onSuccess(result: JSONObject) {
                val sessionId = result.optString("sessionId")
                postSuccess(cb, sessionId)
            }
        })
    }

    fun loginByCode(
        phone: String,
        code: String,
        sessionId: String,
        cb: RequestCallback<LoginResult>
    ) {
        val params = linkedMapOf(
            "sessionId" to sessionId,
            "code" to code,
            "phone" to phone
        )
        request(API_LOGIN_CODE, params, object : RequestCallback<JSONObject> {
            override fun onError(code2: Int, message: String?) = postError(cb, code2, message)
            override fun onSuccess(result: JSONObject) = parseLogin(result, cb)
        })
    }

    fun deleteAccount(userId: String, token: String, cb: RequestCallback<Unit>) {
        if (TextUtils.isEmpty(userId) || TextUtils.isEmpty(token)) {
            postError(cb, -1, null)
            return
        }
        val params = linkedMapOf("userId" to userId, "token" to token)
        ensureInit({
            ioExecutor.execute {
                try {
                    val url = service + ENV_PROD + API_DELETE_ACCOUNT + buildQuery(params)
                    val response = client.newCall(Request.Builder().url(url).build()).execute()
                    val resJson = JSONObject(response.body?.string().orEmpty())
                    val errorCode = resJson.optInt("errorCode")
                    val errorMsg = errorMessage(resJson, resJson.optString("errorMessage"))
                    if (!response.isSuccessful || errorCode != 0) {
                        mainHandler.post { cb.onError(errorCode, errorMsg) }
                    } else {
                        mainHandler.post { cb.onSuccess(Unit) }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "deleteAccount error: ${redactSensitive(e.message)}")
                    mainHandler.post { cb.onError(-1, e.message) }
                }
            }
        }, { code, msg -> cb.onError(code, msg) })
    }

    fun loginByToken(userId: String, token: String, cb: RequestCallback<LoginResult>) {
        val params = linkedMapOf("userId" to userId, "token" to token)
        request(API_LOGIN_TOKEN, params, object : RequestCallback<JSONObject> {
            override fun onError(code: Int, message: String?) = postError(cb, code, message)
            override fun onSuccess(result: JSONObject) = parseLogin(result, cb, fallbackUserId = userId)
        })
    }

    private fun parseLogin(
        data: JSONObject,
        cb: RequestCallback<LoginResult>,
        fallbackUserId: String? = null
    ) {
        val userId = data.optString("userId", fallbackUserId.orEmpty())
        val userSig = data.optString("userSig")
        val sdkAppId = data.optInt("sdkAppId")
        val token = data.optString("token")
        if (TextUtils.isEmpty(userId) || TextUtils.isEmpty(userSig) || sdkAppId <= 0) {
            postError(cb, -8881, null)
            return
        }
        postSuccess(cb, LoginResult(userId, userSig, sdkAppId, token))
    }

    private fun request(api: String, params: Map<String, String>, cb: RequestCallback<JSONObject>) {
        ensureInit({
            ioExecutor.execute {
                try {
                    val url = service + ENV_PROD + api + buildQuery(params)
                    val response = client.newCall(Request.Builder().url(url).build()).execute()
                    val resJson = JSONObject(response.body?.string().orEmpty())
                    val errorCode = resJson.optInt("errorCode")
                    val errorMsg = errorMessage(resJson, resJson.optString("errorMessage"))
                    val data = resJson.optJSONObject("data")
                    if (!response.isSuccessful || errorCode != 0 || data == null) {
                        mainHandler.post { cb.onError(errorCode, errorMsg) }
                    } else {
                        mainHandler.post { cb.onSuccess(data) }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "request error: ${redactSensitive(e.message)}")
                    mainHandler.post { cb.onError(-1, e.message) }
                }
            }
        }, { code, msg -> cb.onError(code, msg) })
    }

    private fun errorMessage(resJson: JSONObject, fallback: String): String {
        val notice = resJson.optJSONObject("notice") ?: return fallback
        val message = if (currentLanguage() == "zh") notice.optString("zh") else notice.optString("en")
        return if (message.isNullOrEmpty()) fallback else message
    }
}
