package com.tencent.rtcube.modules.call.online.service

import android.util.Log
import com.tencent.rtcube.modules.call.online.model.CallingRequestRobotModel
import com.tencent.rtcube.modules.call.online.model.CallingVirtualRobotArrayModel
import com.tencent.rtcube.modules.call.online.service.HttpRequestBotService.requestInitCallBot
import com.tencent.rtcube.modules.call.online.service.HttpRequestBotService.requestWaitingCall
import com.tencent.rtcube.v2.login.LoginEntry
import com.tencent.rtcube.v2.login.components.service.LoginManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.FieldMap
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST
import kotlin.coroutines.resume

private interface CallApi {
    /** Wait for the robot to call back. */
    @POST("base/v1/virtual_call/waiting_caller")
    @FormUrlEncoded
    fun waitingCaller(@FieldMap params: Map<String, String>): Call<ResponseEntity<Void>>

    /** Query the list of available virtual users. */
    @POST("base/v1/auth_users/virtual_users_query")
    @FormUrlEncoded
    fun queryVirtualUser(@FieldMap params: Map<String, String>): Call<ResponseEntity<CallingVirtualRobotArrayModel>>
}

data class ResponseEntity<T>(
    val errorCode: Int,
    val errorMessage: String,
    val data: T?,
)

object HttpRequestBotService {
    private const val TAG = "HttpRequestBotService"
    private val BASE_URL: String
        get() {
            val url = LoginEntry.config.httpBaseUrl
            return if (url.endsWith("/")) url else "$url/"
        }

    private var cachedBaseUrl: String = ""
    private var cachedApi: CallApi? = null

    private val api: CallApi
        get() {
            val currentBaseUrl = BASE_URL
            val existing = cachedApi
            if (existing != null && currentBaseUrl == cachedBaseUrl) return existing
            return Retrofit.Builder()
                .baseUrl(currentBaseUrl)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(CallApi::class.java)
                .also {
                    cachedBaseUrl = currentBaseUrl
                    cachedApi = it
                }
        }

    /**
     * Actively call the robot — query the list of available virtual users.
     *
     * @param onSuccess callback with the parsed [CallingRequestRobotModel]
     * @param onFailed  failure callback with the error message
     */
    suspend fun requestInitCallBot(
        onSuccess: (CallingRequestRobotModel) -> Unit,
        onFailed: (String) -> Unit,
    ) = withContext(Dispatchers.IO) {
        val localUser = LoginManager.currentUser
        val params = mapOf(
            "userId" to (localUser?.userId ?: ""),
            "token" to (localUser?.token ?: ""),
            "apaasAppId" to LoginEntry.config.apaasAppId,
        )
        val result = api.queryVirtualUser(params).awaitResult()
        withContext(Dispatchers.Main) {
            result.fold(
                onSuccess = { entity ->
                    if (entity.errorCode == 0 && entity.data != null) {
                        onSuccess(
                            CallingRequestRobotModel(
                                errorCode = entity.errorCode,
                                errorMessage = entity.errorMessage,
                                data = entity.data,
                            )
                        )
                    } else {
                        onFailed(entity.errorMessage)
                    }
                },
                onFailure = { e ->
                    Log.e(TAG, "[requestInitCallBot] failed: ${e.message}")
                    onFailed(e.message ?: "unknown error")
                },
            )
        }
    }

    /**
     * Wait for the robot to call back — notify the server to prepare.
     *
     * @param language  language parameter
     * @param onSuccess success callback
     * @param onFailed  failure callback with the error message
     */
    suspend fun requestWaitingCall(
        language: String = "zh",
        onSuccess: () -> Unit,
        onFailed: (String) -> Unit,
    ) = withContext(Dispatchers.IO) {
        val localUser = LoginManager.currentUser
        val params = mapOf(
            "userId" to (localUser?.userId ?: ""),
            "token" to (localUser?.token ?: ""),
            "apaasAppId" to LoginEntry.config.apaasAppId,
            "lang" to language,
        )
        val result = api.waitingCaller(params).awaitResult()
        withContext(Dispatchers.Main) {
            result.fold(
                onSuccess = { entity ->
                    if (entity.errorCode == 0) {
                        onSuccess()
                    } else {
                        onFailed(entity.errorMessage)
                    }
                },
                onFailure = { e ->
                    Log.e(TAG, "[requestWaitingCall] failed: ${e.message}")
                    onFailed(e.message ?: "unknown error")
                },
            )
        }
    }

    private suspend fun <T> Call<T>.awaitResult(): Result<T> =
        suspendCancellableCoroutine { cont ->
            enqueue(object : Callback<T> {
                override fun onResponse(call: Call<T>, response: Response<T>) {
                    val body = response.body()
                    if (response.isSuccessful && body != null) {
                        cont.resume(Result.success(body))
                    } else {
                        cont.resume(Result.failure(Exception("HTTP ${response.code()}: ${response.message()}")))
                    }
                }

                override fun onFailure(call: Call<T>, t: Throwable) {
                    cont.resume(Result.failure(t))
                }
            })
            cont.invokeOnCancellation { cancel() }
        }
}