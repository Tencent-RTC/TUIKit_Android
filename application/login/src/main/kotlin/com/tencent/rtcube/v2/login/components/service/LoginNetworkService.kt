package com.tencent.rtcube.v2.login.components.service

import android.util.Log
import androidx.core.os.ConfigurationCompat
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.tencent.rtcube.v2.login.LoginEntry
import com.tencent.rtcube.v2.login.R
import com.tencent.rtcube.v2.login.components.model.LoginError
import com.tencent.rtcube.v2.login.components.model.LoginResult
import com.tencent.rtcube.v2.login.components.model.UserModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.QueryMap
import java.util.concurrent.TimeUnit

class LoginNetworkService {

    companion object {
        private const val TAG = "LoginNetworkService"
        private const val ERROR_SYSTEM_MAINTENANCE = 120
        private const val ERROR_CODE_USER_TOKEN_EXPIRED = 203
        private const val NETWORK_ERROR_INVALID_HOST_NAME = "No address associated with hostname"
    }

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(HttpLoggingInterceptor())
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private var cachedBaseUrl: String = ""
    private var cachedApi: LoginApi? = null

    private val api: LoginApi
        get() {
            val currentBaseUrl = resolveBaseUrl()
            val existing = cachedApi
            if (existing != null && currentBaseUrl == cachedBaseUrl) {
                return existing
            }
            return Retrofit.Builder()
                .baseUrl(currentBaseUrl)
                .client(okHttpClient)
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build()
                .create(LoginApi::class.java)
                .also {
                    cachedBaseUrl = currentBaseUrl
                    cachedApi = it
                }
        }

    private fun resolveBaseUrl(): String {
        val baseUrl = LoginEntry.config.httpBaseUrl
        return if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
    }

    private fun resolveApaasAppId(): String {
        return LoginEntry.config.apaasAppId
    }

    suspend fun getImageCaptcha(): Result<String> = withContext(Dispatchers.IO) {
        try {
            val response = api.getImageCaptcha().execute()
            val body = response.body()
            if (response.isSuccessful && body != null && body.errorCode == 0) {
                Result.success(body.data?.captchaWebAppId.orEmpty())
            } else {
                Result.failure(LoginError.NetworkError(
                    body?.errorMessage ?: LoginEntry.appContext.getString(R.string.login_error_captcha_config_failed)
                )
                )
            }
        } catch (e: Exception) {
            handleNetworkException(e)
        }
    }

    suspend fun sendSms(
        phone: String,
        captcha: CaptchaResult,
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val data = LinkedHashMap<String, String>()
            data["appId"] = captcha.webAppId
            data["phone"] = phone
            data["ticket"] = captcha.ticket
            data["randstr"] = captcha.randStr
            data["apaasAppId"] = resolveApaasAppId()
            val response = api.getSms(data).execute()
            val body = response.body()
            if (response.isSuccessful && body != null && body.errorCode == 0) {
                Result.success(body.data?.sessionId.orEmpty())
            } else {
                Result.failure(LoginError.VerifyCodeFailed(
                    body?.errorMessage ?: LoginEntry.appContext.getString(R.string.login_error_send_failed)
                )
                )
            }
        } catch (e: Exception) {
            handleNetworkException(e)
        }
    }

    suspend fun sendEmailVerifyCode(
        email: String,
        captcha: CaptchaResult,
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val data = LinkedHashMap<String, String>()
            data["appId"] = captcha.webAppId
            data["email"] = email
            data["ticket"] = captcha.ticket
            data["randstr"] = captcha.randStr
            data["apaasAppId"] = resolveApaasAppId()
            val response = api.getSms(data).execute()
            val body = response.body()
            if (response.isSuccessful && body != null && body.errorCode == 0) {
                Result.success(body.data?.sessionId.orEmpty())
            } else {
                Result.failure(LoginError.VerifyCodeFailed(body?.errorMessage
                    ?: LoginEntry.appContext.getString(R.string.login_error_send_failed)
                )
                )
            }
        } catch (e: Exception) {
            handleNetworkException(e)
        }
    }

    suspend fun loginByPhone(
        phone: String,
        sessionId: String,
        code: String,
    ): Result<LoginResult> = withContext(Dispatchers.IO) {
        try {
            val data = LinkedHashMap<String, String>()
            data["phone"] = phone
            data["code"] = code
            data["sessionId"] = sessionId
            data["apaasAppId"] = resolveApaasAppId()
            val response = api.login(data).execute()
            val body = response.body()
            val userEntity = body?.toUserEntity()
            if (response.isSuccessful && body != null && body.errorCode == 0 && userEntity != null) {
                Result.success(LoginResult(userModel = userEntity.toUserModel()))
            } else {
                Log.e(TAG, "loginByPhone failed: errorCode=${body?.errorCode}, msg=${body?.errorMessage}")
                handleLoginFailure(body)
            }
        } catch (e: Exception) {
            Log.e(TAG, "loginByPhone, errorMsg: ${e.message}")
            handleNetworkException(e)
        }
    }

    suspend fun loginByEmail(
        email: String,
        sessionId: String,
        code: String,
    ): Result<LoginResult> = withContext(Dispatchers.IO) {
        try {
            val data = LinkedHashMap<String, String>()
            data["email"] = email
            data["code"] = code
            data["sessionId"] = sessionId
            data["apaasAppId"] = resolveApaasAppId()
            val response = api.login(data).execute()
            val body = response.body()
            val userEntity = body?.toUserEntity()
            if (response.isSuccessful && body != null && body.errorCode == 0 && userEntity != null) {
                Result.success(LoginResult(userModel = userEntity.toUserModel()))
            } else {
                Log.e(TAG, "loginByEmail failed: errorCode=${body?.errorCode}, msg=${body?.errorMessage}")
                handleLoginFailure(body)
            }
        } catch (e: Exception) {
            Log.e(TAG, "loginByEmail, errorMsg: ${e.message}")
            handleNetworkException(e)
        }
    }

    suspend fun loginByToken(
        userId: String,
        token: String,
    ): Result<LoginResult> = withContext(Dispatchers.IO) {
        try {
            val data = LinkedHashMap<String, String>()
            data["apaasUserId"] = userId
            data["token"] = token
            data["apaasAppId"] = resolveApaasAppId()
            val response = api.loginByToken(data).execute()
            val body = response.body()
            val userEntity = body?.toUserEntity()
            if (response.isSuccessful && body != null && body.errorCode == 0 && userEntity != null) {
                Result.success(LoginResult(userModel = userEntity.toUserModel()))
            } else {
                Log.e(TAG, "loginByToken failed: errorCode=${body?.errorCode}, msg=${body?.errorMessage}")
                Result.failure(LoginError.TokenExpired)
            }
        } catch (e: Exception) {
            Log.e(TAG, "loginByToken exception: ${e.message}", e)
            handleNetworkException(e)
        }
    }

    suspend fun logout(
        userId: String = "",
        token: String = "",
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val data = LinkedHashMap<String, String>()
            data["apaasUserId"] = userId
            data["token"] = token
            api.logout(data).execute()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.success(Unit)
        }
    }

    suspend fun updateUser(
        apaasUserId: String,
        token: String,
        nickname: String,
        avatarUrl: String,
    ): Result<UserModel> = withContext(Dispatchers.IO) {
        try {
            val data = LinkedHashMap<String, String>()
            data["apaasUserId"] = apaasUserId
            data["token"] = token
            data["name"] = nickname
            data["tag"] = "im"
            data["avatar"] = avatarUrl
            val response = api.updateUser(data).execute()
            val body = response.body()
            if (response.isSuccessful && body != null && body.errorCode == 0) {
                Result.success(UserModel(apaasUserId = apaasUserId, name = nickname, avatar = avatarUrl))
            } else {
                Log.w(TAG, "updateUser failed: code=${body?.errorCode}, msg=${body?.errorMessage}")
                Result.failure(LoginError.NetworkError(
                    body?.errorMessage ?: LoginEntry.appContext.getString(R.string.login_error_update_failed)
                )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "updateUser exception: ${e.message}", e)
            handleNetworkException(e)
        }
    }

    suspend fun loginByInviteCode(
        inviteCode: String,
    ): Result<LoginResult> = withContext(Dispatchers.IO) {
        try {
            val data = LinkedHashMap<String, String>()
            data["inviteCode"] = inviteCode
            data["apaasAppId"] = resolveApaasAppId()
            val response = api.noneAuthLogin(data).execute()
            val body = response.body()
            val userEntity = body?.toUserEntity()
            if (response.isSuccessful && body != null && body.errorCode == 0 && userEntity != null) {
                Result.success(LoginResult(userModel = userEntity.toUserModel()))
            } else {
                Log.e(TAG, "loginByInviteCode failed: errorCode=${body?.errorCode}, msg=${body?.errorMessage}")
                handleLoginFailure(body, LoginEntry.appContext.getString(R.string.login_error_invite_login_failed))
            }
        } catch (e: Exception) {
            Log.e(TAG, "loginByInviteCode: errorMsg: ${e.message}")
            handleNetworkException(e)
        }
    }

    suspend fun requestInvitationCode(email: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val data = LinkedHashMap<String, String>()
            data["email"] = email
            data["apaasAppId"] = resolveApaasAppId()
            val response = api.requestInvitationCode(data).execute()
            val body = response.body()
            if (response.isSuccessful && body != null && body.errorCode == 0) {
                Result.success(Unit)
            } else {
                Log.e(TAG, "requestInvitationCode failed: errorCode=${body?.errorCode}, msg=${body?.errorMessage}")
                Result.failure(LoginError.NetworkError(
                    body?.errorMessage ?: LoginEntry.appContext.getString(R.string.login_error_request_invite_failed)
                )
                )
            }
        } catch (e: Exception) {
            handleNetworkException(e)
        }
    }

    suspend fun loginByIOA(ticket: String): Result<LoginResult> = withContext(Dispatchers.IO) {
        try {
            val request = MoaLoginRequest(key = ticket, apaasAppId = resolveApaasAppId(), tag = "trtc")
            val response = api.loginByMOA(request).execute()
            val body = response.body()
            val userEntity = body?.toUserEntity()
            if (response.isSuccessful && body != null && body.errorCode == 0 && userEntity != null) {
                Result.success(LoginResult(userModel = userEntity.toUserModel()))
            } else {
                Log.e(TAG, "loginByIOA failed: errorCode=${body?.errorCode}, msg=${body?.errorMessage}")
                handleLoginFailure(body, LoginEntry.appContext.getString(R.string.login_error_ioa_login_failed))
            }
        } catch (e: Exception) {
            Log.e(TAG, "loginByIOA: errorMsg: ${e.message}")
            handleNetworkException(e)
        }
    }

    suspend fun getUserModuleBlackList(userId: String): Result<BlackListResult> = withContext(Dispatchers.IO) {
        try {
            val request = BlackListRequest(userId = userId)
            val response = api.getUserModuleBlackList(request).execute()
            val body = response.body()
            if (response.isSuccessful && body != null && body.errorCode == 0) {
                val entity = body.data
                Result.success(BlackListResult(module = entity?.module ?: emptyMap(), feature = entity?.feature ?: emptyMap())
                )
            } else {
                Result.failure(LoginError.NetworkError(
                    body?.errorMessage ?: LoginEntry.appContext.getString(R.string.login_error_blacklist_query_failed)
                )
                )
            }
        } catch (e: Exception) {
            handleNetworkException(e)
        }
    }

    suspend fun keepAlive(userId: String, token: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val data = LinkedHashMap<String, String>()
            data["apaasUserId"] = userId
            data["token"] = token
            val response = api.keepAlive(data).execute()
            val body = response.body()
            if (response.isSuccessful && body != null && body.errorCode == 0) {
                Result.success(Unit)
            } else {
                Result.failure(LoginError.NetworkError(
                    body?.errorMessage ?: LoginEntry.appContext.getString(R.string.login_error_keepalive_failed)
                )
                )
            }
        } catch (e: Exception) {
            handleNetworkException(e)
        }
    }

    suspend fun getInviteCode(email: String): Result<InviteCodeResult> = withContext(Dispatchers.IO) {
        try {
            val data = LinkedHashMap<String, String>()
            data["email"] = email
            data["apaasAppId"] = resolveApaasAppId()
            val response = api.getInviteCode(data).execute()
            val body = response.body()
            if (response.isSuccessful && body != null && body.errorCode == 0 && body.data != null) {
                Result.success(InviteCodeResult(
                    userId = body.data.userId,
                    inviteCode = body.data.inviteCode
                )
                )
            } else {
                Log.e(TAG, "getInviteCode failed: errorCode=${body?.errorCode}, msg=${body?.errorMessage}")
                Result.failure(LoginError.NetworkError(
                    body?.errorMessage ?: LoginEntry.appContext.getString(R.string.login_error_request_invite_failed)
                )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "getInviteCode: errorMsg: ${e.message}")
            handleNetworkException(e)
        }
    }

    suspend fun createLeaveUserSendEmail(email: String, marketingStatus: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val request = LeaveUserEmailRequest(email = email, marketingStatus = marketingStatus)
            val response = api.createLeaveUserSendEmail(request).execute()
            val body = response.body()
            if (response.isSuccessful && body != null && body.errorCode == 0) {
                Result.success(Unit)
            } else {
                Result.failure(LoginError.NetworkError(
                    body?.errorMessage ?: LoginEntry.appContext.getString(R.string.login_error_network)
                )
                )
            }
        } catch (e: Exception) {
            handleNetworkException(e)
        }
    }

    suspend fun deleteUser(userId: String, token: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val data = LinkedHashMap<String, String>()
            data["apaasUserId"] = userId
            data["token"] = token
            api.deleteUser(data).execute()
            Result.success(Unit)
        } catch (e: Exception) {
            handleNetworkException(e)
        }
    }

    private fun handleLoginFailure(
        body: LoginApiResponse?,
        defaultErrorMessage: String = LoginEntry.appContext.getString(R.string.login_error_login_failed)
    ): Result<LoginResult> {
        val errorCode = body?.errorCode ?: -1
        return when (errorCode) {
            ERROR_CODE_USER_TOKEN_EXPIRED -> Result.failure(LoginError.TokenExpired)
            ERROR_SYSTEM_MAINTENANCE -> {
                val noticeMsg = if (getCurrentLocale().startsWith("zh", ignoreCase = true)) body?.notice?.zh else body?.notice?.en
                Result.failure(LoginError.LoginFailed(errorCode, noticeMsg ?: defaultErrorMessage))
            }

            else -> Result.failure(LoginError.LoginFailed(errorCode, body?.errorMessage ?: defaultErrorMessage))
        }
    }

    private fun <T> handleNetworkException(e: Exception): Result<T> {
        val errorMsg = if (e.message?.contains(NETWORK_ERROR_INVALID_HOST_NAME) == true) {
            LoginEntry.appContext.getString(R.string.login_error_network)
        } else {
            e.message ?: LoginEntry.appContext.getString(R.string.login_error_network)
        }
        return Result.failure(LoginError.NetworkError(errorMsg))
    }

    private fun getCurrentLocale(): String {
        return ConfigurationCompat.getLocales(LoginEntry.appContext.resources.configuration)[0]?.language ?: "zh"
    }
}

private interface LoginApi {
    @GET("base/v1/gslb")
    fun getImageCaptcha(): Call<ApiResponse<ImageCaptchaEntity>>

    @GET("base/v1/auth_users/user_verify_by_picture")
    fun getSms(@QueryMap map: LinkedHashMap<String, String>): Call<ApiResponse<SmsEntity>>

    @GET("base/v1/auth_users/none_auth")
    fun noneAuthLogin(@QueryMap map: LinkedHashMap<String, String>): Call<LoginApiResponse>

    @GET("base/v1/auth_users/user_login_code")
    fun login(@QueryMap map: LinkedHashMap<String, String>): Call<LoginApiResponse>

    @GET("base/v1/auth_users/user_login_token")
    fun loginByToken(@QueryMap map: LinkedHashMap<String, String>): Call<LoginApiResponse>

    @GET("base/v1/auth_users/user_logout")
    fun logout(@QueryMap map: LinkedHashMap<String, String>): Call<ApiResponse<Unit>>

    @GET("base/v1/auth_users/user_update")
    fun updateUser(@QueryMap map: LinkedHashMap<String, String>): Call<ApiResponse<Unit>>

    @GET("base/v1/auth_users/apply_invite_code")
    fun requestInvitationCode(@QueryMap map: LinkedHashMap<String, String>): Call<ApiResponse<Unit>>

    @GET("base/v1/auth_users/user_delete")
    fun deleteUser(@QueryMap map: LinkedHashMap<String, String>): Call<ApiResponse<Unit>>

    @GET("base/v1/auth_users/user_keepalive")
    fun keepAlive(@QueryMap map: LinkedHashMap<String, String>): Call<ApiResponse<Unit>>

    @GET("base/v1/auth_users/apply_invite_code")
    fun getInviteCode(@QueryMap map: LinkedHashMap<String, String>): Call<ApiResponse<InviteCodeEntity>>

    @POST("base/v1/auth_users/create_leave_user_send_email")
    fun createLeaveUserSendEmail(@Body body: LeaveUserEmailRequest): Call<ApiResponse<Unit>>

    @POST("base/v1/auth_users/module_blacklist")
    fun getUserModuleBlackList(@Body body: BlackListRequest): Call<ApiResponse<BlackListEntity>>

    @POST("base/v1/auth_users/user_login_moa")
    fun loginByMOA(@Body body: MoaLoginRequest): Call<LoginApiResponse>
}

@OptIn(InternalSerializationApi::class)
@Serializable
private data class ApiResponse<T>(
    val errorCode: Int = 0,
    val errorMessage: String = "",
    val data: T? = null,
    val notice: NoticeEntity? = null,
)

@OptIn(InternalSerializationApi::class)
@Serializable
private data class LoginApiResponse(
    val errorCode: Int = 0,
    val errorMessage: String = "",
    val data: UserEntity? = null,
    val notice: NoticeEntity? = null,
) {
    fun toUserEntity(): UserEntity? = data
}

@OptIn(InternalSerializationApi::class)
@Serializable
private data class NoticeEntity(
    val zh: String? = null,
    val en: String? = null,
) {
}

@OptIn(InternalSerializationApi::class)
@Serializable
private data class ImageCaptchaEntity(
    val service: String = "",
    @SerialName("captcha_web_appid")
    val captchaWebAppId: String = "",
    @SerialName("captcha_wxmini_appid")
    val captchaWXminiAppId: String = "",
)

@OptIn(InternalSerializationApi::class)
@Serializable
private data class SmsEntity(
    val sessionId: String = "",
)

@OptIn(InternalSerializationApi::class)
@Serializable
private data class UserEntity(
    val userId: String = "",
    val apaasAppId: String = "",
    val apaasUserId: String = "",
    val sdkAppId: String = "",
    val sdkUserSig: String = "",
    val token: String = "",
    val expire: String = "",
    val phone: String = "",
    val email: String = "",
    val loginType: String = "",
    val name: String = "",
    val avatar: String = "",
    val isHighRiskUser: Int = 0,
    val isHighRiskIp: Int = 0,
) {
    fun toUserModel(): UserModel = UserModel(
        userId = userId,
        apaasUserId = apaasUserId,
        token = token,
        userSig = sdkUserSig,
        sdkAppId = sdkAppId.toIntOrNull() ?: 0,
        phone = phone,
        email = email,
        name = name,
        avatar = avatar,
        isHighRiskUser = isHighRiskUser != 0,
        isHighRiskIp = isHighRiskIp != 0,
        loginType = loginType,
    )
}

data class CaptchaResult(
    val ticket: String = "",
    val randStr: String = "",
    val webAppId: String = "",
)

@OptIn(InternalSerializationApi::class)
@Serializable
private data class InviteCodeEntity(
    val userId: String = "",
    val inviteCode: String = "",
)

@OptIn(InternalSerializationApi::class)
@Serializable
private data class BlackListEntity(
    val module: Map<String, Boolean> = emptyMap(),
    val feature: Map<String, Boolean> = emptyMap(),
)

data class InviteCodeResult(
    val userId: String,
    val inviteCode: String,
)

data class BlackListResult(
    val module: Map<String, Boolean>,
    val feature: Map<String, Boolean>,
)

@OptIn(InternalSerializationApi::class)
@Serializable
private data class BlackListRequest(
    val userId: String,
)

@OptIn(InternalSerializationApi::class)
@Serializable
private data class LeaveUserEmailRequest(
    val email: String,
    val marketingStatus: Boolean,
    val source: String = "tencent_rtc_app",
    val scene: String = "product-trtc",
)

@OptIn(InternalSerializationApi::class)
@Serializable
private data class MoaLoginRequest(
    val key: String,
    val apaasAppId: String,
    val tag: String = "trtc",
)
