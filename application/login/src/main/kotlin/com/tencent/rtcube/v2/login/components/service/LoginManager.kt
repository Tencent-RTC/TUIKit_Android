package com.tencent.rtcube.v2.login.components.service

import android.util.Log
import com.tencent.imsdk.v2.V2TIMCallback
import com.tencent.imsdk.v2.V2TIMManager
import com.tencent.imsdk.v2.V2TIMUserFullInfo
import com.tencent.imsdk.v2.V2TIMValueCallback
import com.tencent.qcloud.tuicore.TUILogin
import com.tencent.qcloud.tuicore.interfaces.TUICallback
import com.tencent.rtcube.v2.login.LoginEntry
import com.tencent.rtcube.v2.login.R
import com.tencent.rtcube.v2.login.components.model.LoginError
import com.tencent.rtcube.v2.login.components.model.LoginResult
import com.tencent.rtcube.v2.login.components.model.UserModel
import com.tencent.rtcube.v2.login.components.service.LoginManager.currentUserFlow
import com.tencent.rtcube.v2.login.tokenauth.utils.TokenCacheManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

object LoginManager {

    private const val TAG = "LoginManager"

    private val networkService = LoginNetworkService()
    private val tokenManager = TokenCacheManager

    private val _currentUserFlow = MutableStateFlow<UserModel?>(null)
    val currentUserFlow: StateFlow<UserModel?> = _currentUserFlow.asStateFlow()
    val currentUser: UserModel?
        get() = _currentUserFlow.value

    private const val KEEP_ALIVE_INTERVAL = 10000L  // keep-alive interval: 10s (in ms)
    private val keepAliveScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var keepAliveJob: Job? = null

    fun startKeepAlive() {
        if (keepAliveJob?.isActive == true) {
            return
        }
        keepAliveJob = keepAliveScope.launch {
            while (isActive) {
                delay(KEEP_ALIVE_INTERVAL)
                keepAlive()
            }
        }
    }

    fun stopKeepAlive() {
        keepAliveJob?.cancel()
        keepAliveJob = null
    }

    private fun saveLoginInfo(userModel: UserModel) {
        _currentUserFlow.value = userModel
        val userId = if (userModel.apaasUserId.isNotEmpty()) userModel.apaasUserId else userModel.userId
        tokenManager.saveToken(userModel.token, userId)
    }

    fun clearLoginInfo() {
        _currentUserFlow.value = null
        tokenManager.clear()
    }

    /** Run [block] and convert any thrown exception into [Result.failure] of [LoginError]. */
    private inline fun <T> runCatchingLogin(block: () -> Result<T>): Result<T> = try {
        block()
    } catch (e: Exception) {
        val error = e as? LoginError
            ?: LoginError.NetworkError(e.message ?: LoginEntry.appContext.getString(R.string.login_error_network))
        Result.failure(error)
    }

    /** Common post-login flow: save user info, login IM, start keep-alive, return [LoginResult]. */
    private suspend fun finishLogin(loginResult: LoginResult, appId: Int? = null): Result<LoginResult> {
        saveLoginInfo(loginResult.userModel)
        return loginIM(loginResult.userModel, appId).map {
            startKeepAlive()
            val updatedUser = currentUser ?: loginResult.userModel
            loginResult.copy(userModel = updatedUser)
        }
    }

    suspend fun loginByPhone(phone: String, sessionId: String, code: String): Result<LoginResult> = runCatchingLogin {
        networkService.loginByPhone(phone, sessionId, code).fold(
            onSuccess = { finishLogin(it) },
            onFailure = {
                if (it is LoginError.TokenExpired) {
                    LoginEntry.onUserTokenExpired()
                }
                Result.failure(it)
            }
        )
    }

    suspend fun loginByEmail(email: String, sessionId: String, code: String): Result<LoginResult> = runCatchingLogin {
        networkService.loginByEmail(email, sessionId, code).fold(
            onSuccess = { finishLogin(it) },
            onFailure = {
                if (it is LoginError.TokenExpired) {
                    LoginEntry.onUserTokenExpired()
                }
                Result.failure(it)
            }
        )
    }

    suspend fun loginByToken(): Result<LoginResult> {
        val token = tokenManager.getToken() ?: run {
            // No token cache (first install or cleared): return failure without firing expired notification
            return Result.failure(LoginError.TokenExpired)
        }
        val userId = tokenManager.getUserId() ?: run {
            return Result.failure(LoginError.TokenExpired)
        }
        return runCatchingLogin {
            networkService.loginByToken(userId, token).fold(
                onSuccess = { finishLogin(it) },
                onFailure = { error ->
                    clearLoginInfo()
                    LoginEntry.onUserTokenExpired()
                    Result.failure(error)
                }
            )
        }
    }

    suspend fun loginByInviteCode(inviteCode: String): Result<LoginResult> = runCatchingLogin {
        networkService.loginByInviteCode(inviteCode).fold(
            onSuccess = { finishLogin(it) },
            onFailure = { Result.failure(it) }
        )
    }

    suspend fun loginByIOA(ticket: String): Result<LoginResult> = runCatchingLogin {
        networkService.loginByIOA(ticket).fold(
            onSuccess = { finishLogin(it) },
            onFailure = { Result.failure(it) }
        )
    }

    suspend fun loginByDebug(userId: String): Result<LoginResult> = runCatchingLogin {
        val debugAppId = LoginEntry.config.debugSdkAppId
        val debugSecretKey = LoginEntry.config.debugSecretKey
        val userSig = LoginEntry.userSigGenerator?.invoke(userId, debugAppId, debugSecretKey) ?: ""
        val userModel = UserModel(userId = userId, token = "", userSig = userSig, name = "")
        saveLoginInfo(userModel)
        // Debug login does not need keep-alive
        loginIM(userModel, appId = debugAppId).map { LoginResult(userModel = userModel) }
    }

    suspend fun loginIMWithCredentials(userModel: UserModel, sdkAppId: Int): Result<Unit> {
        saveLoginInfo(userModel)
        return loginIM(userModel, appId = sdkAppId)
    }

    suspend fun updateProfile(nickname: String, avatarUrl: String): Result<UserModel> = runCatchingLogin {
        val user = currentUser ?: return@runCatchingLogin Result.failure(LoginError.NetworkError("user is not logged in"))
        networkService.updateUser(user.apaasUserId, user.token, nickname, avatarUrl).also { result ->
            result.onSuccess { updatedUser ->
                _currentUserFlow.value = user.copy(name = updatedUser.name, avatar = updatedUser.avatar)
                syncIMUserInfo(nickname = updatedUser.name, avatar = updatedUser.avatar.ifEmpty { null })
            }
        }
    }

    suspend fun setNickName(nickname: String): Result<Unit> = runCatchingLogin {
        val user = currentUser ?: return@runCatchingLogin Result.failure(LoginError.NetworkError("user is not logged in"))
        networkService.updateUser(user.apaasUserId, user.token, nickname, user.avatar).fold(
            onSuccess = {
                _currentUserFlow.value = user.copy(name = nickname)
                syncIMUserInfo(nickname = nickname, avatar = null)
            },
            onFailure = { Result.failure(it) }
        )
    }

    suspend fun setAvatar(avatarUrl: String): Result<Unit> = runCatchingLogin {
        val user = currentUser ?: return@runCatchingLogin Result.failure(LoginError.NetworkError("user is not logged in"))
        networkService.updateUser(user.apaasUserId, user.token, user.name, avatarUrl).fold(
            onSuccess = {
                _currentUserFlow.value = user.copy(avatar = avatarUrl)
                syncIMUserInfo(nickname = null, avatar = avatarUrl)
            },
            onFailure = { Result.failure(it) }
        )
    }

    suspend fun setNicknameAndAvatar(nickname: String, avatarUrl: String): Result<Unit> = runCatchingLogin {
        val user = currentUser ?: return@runCatchingLogin Result.failure(LoginError.NetworkError("user is not logged in"))
        networkService.updateUser(user.apaasUserId, user.token, nickname, avatarUrl).fold(
            onSuccess = {
                _currentUserFlow.value = user.copy(name = nickname, avatar = avatarUrl)
                syncIMUserInfo(nickname = nickname, avatar = avatarUrl)
            },
            onFailure = { Result.failure(it) }
        )
    }

    suspend fun logout(): Result<Unit> = runCatchingLogin {
        val apaasUserId = currentUser?.apaasUserId.orEmpty()
        val token = tokenManager.getToken().orEmpty()
        try {
            networkService.logout(apaasUserId, token)
        } finally {
            logoutIM()
            clearLoginInfo()
            stopKeepAlive()
        }
    }

    /**
     * delete account (delete user data).
     */
    suspend fun logoff(): Result<Unit> = runCatchingLogin {
        val apaasUserId = currentUser?.apaasUserId.orEmpty()
        val token = tokenManager.getToken().orEmpty()
        networkService.deleteUser(apaasUserId, token).onSuccess {
            syncIMUserInfo(nickname = "", avatar = "")
            logoutIM()
        }.also {
            clearLoginInfo()
            stopKeepAlive()
        }
    }

    suspend fun sendSms(phone: String, captcha: CaptchaResult): Result<String> = runCatchingLogin {
        networkService.sendSms(phone, captcha)
    }

    suspend fun sendEmailVerifyCode(email: String, captcha: CaptchaResult): Result<String> = runCatchingLogin {
        networkService.sendEmailVerifyCode(email, captcha)
    }

    suspend fun requestInvitationCode(email: String): Result<Unit> = runCatchingLogin {
        networkService.requestInvitationCode(email)
    }

    suspend fun getInviteCode(email: String): Result<InviteCodeResult> = runCatchingLogin {
        networkService.getInviteCode(email)
    }

    /** Send instant consultation email. */
    suspend fun createLeaveUserSendEmail(email: String, marketingStatus: Boolean): Result<Unit> = runCatchingLogin {
        networkService.createLeaveUserSendEmail(email, marketingStatus)
    }

    suspend fun keepAlive(): Result<Unit> = runCatchingLogin {
        val user = currentUser
        networkService.keepAlive(userId = user?.userId.orEmpty(), token = user?.token.orEmpty())
    }

    suspend fun getUserModuleBlackList(userId: String): Result<BlackListResult> = runCatchingLogin {
        networkService.getUserModuleBlackList(userId)
    }

    private suspend fun loginIM(userModel: UserModel, appId: Int? = null): Result<Unit> {
        val finalAppId = appId ?: userModel.sdkAppId.takeIf { it != 0 } ?: LoginEntry.config.sdkAppId
        if (finalAppId == 0 || userModel.userId.isEmpty() || userModel.userSig.isEmpty()) {
            Log.e(TAG,
                "loginIM failed: appId=$finalAppId userId=${userModel.userId}, userSigEmpty=${userModel.userSig.isEmpty()}"
            )
            return Result.failure(
                LoginError.LoginFailed(-1, LoginEntry.appContext.getString(R.string.login_error_login_failed))
            )
        }
        Log.i(TAG, "loginIM: appId=$finalAppId, userId=${userModel.userId}")
        val context = LoginEntry.appContext
        return suspendCancellableCoroutine { cont ->
            TUILogin.login(context, finalAppId, userModel.userId, userModel.userSig, object : TUICallback() {
                override fun onSuccess() {
                    Log.i(TAG, "TUILogin.login success, userId=${userModel.userId}")
                    V2TIMManager.getInstance().getUsersInfo(
                        listOf(userModel.userId),
                        object : V2TIMValueCallback<List<V2TIMUserFullInfo>> {
                            override fun onSuccess(infoList: List<V2TIMUserFullInfo>) {
                                val info = infoList.firstOrNull()
                                if (info != null) {
                                    val cur = currentUser
                                    _currentUserFlow.value = cur?.copy(
                                        name = info.getNickName().orEmpty().ifEmpty { cur.name },
                                        avatar = info.faceUrl.orEmpty().ifEmpty { cur.avatar }
                                    )
                                }
                                if (!cont.isCompleted) cont.resume(Result.success(Unit))
                            }

                            override fun onError(code: Int, msg: String?) {
                                Log.w(TAG, "getUsersInfo failed code=$code msg=$msg")
                                // IM is logged in; profile fetch failure should not fail login.
                                if (!cont.isCompleted) cont.resume(Result.success(Unit))
                            }
                        }
                    )
                }

                override fun onError(code: Int, msg: String?) {
                    Log.e(TAG, "TUILogin.login failed, code=$code msg=$msg")
                    if (!cont.isCompleted) cont.resume(
                        Result.failure(LoginError.LoginFailed(code,
                            msg ?: LoginEntry.appContext.getString(R.string.login_error_login_failed)
                        )
                        )
                    )
                }
            })
        }
    }

    private suspend fun logoutIM() {
        suspendCancellableCoroutine { cont ->
            TUILogin.logout(object : TUICallback() {
                override fun onSuccess() {
                    Log.i(TAG, "TUILogin.logout success")
                    if (!cont.isCompleted) cont.resume(Unit)
                }

                override fun onError(code: Int, msg: String?) {
                    Log.w(TAG, "TUILogin.logout failed, code=$code msg=$msg")
                    if (!cont.isCompleted) cont.resume(Unit)
                }
            })
        }
    }

    private suspend fun syncIMUserInfo(nickname: String?, avatar: String?): Result<Unit> {
        return suspendCancellableCoroutine { cont ->
            val info = V2TIMUserFullInfo()
            nickname?.let { info.setNickname(it) }
            avatar?.let { info.faceUrl = it }
            V2TIMManager.getInstance().setSelfInfo(info, object : V2TIMCallback {
                override fun onSuccess() {
                    Log.i(TAG, "syncIMUserInfo success")
                    if (!cont.isCompleted) cont.resume(Result.success(Unit))
                }

                override fun onError(code: Int, msg: String?) {
                    Log.e(TAG, "syncIMUserInfo failed, code=$code msg=$msg")
                    if (!cont.isCompleted) cont.resume(
                        Result.failure(LoginError.NetworkError(msg ?: "IM updateProfile failed"))
                    )
                }
            })
        }
    }

    /** Called when IM SDK invokes onSelfInfoUpdated; syncs latest nickname/avatar to [currentUserFlow]. */
    fun onIMSelfInfoUpdated(info: V2TIMUserFullInfo) {
        val cur = currentUser ?: return
        val newName = info.nickName.orEmpty().ifEmpty { cur.name }
        val newAvatar = info.faceUrl.orEmpty().ifEmpty { cur.avatar }
        if (newName == cur.name && newAvatar == cur.avatar) {
            return
        }
        Log.i(TAG, "onIMSelfInfoUpdated: name=$newName avatar=$newAvatar")
        _currentUserFlow.value = cur.copy(name = newName, avatar = newAvatar)
    }
}
