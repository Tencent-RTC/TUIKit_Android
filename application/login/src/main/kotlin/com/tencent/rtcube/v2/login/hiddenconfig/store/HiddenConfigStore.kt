package com.tencent.rtcube.v2.login.hiddenconfig.store

import com.tencent.rtcube.v2.login.LoginEntry
import com.tencent.rtcube.v2.login.LoginMode
import com.tencent.rtcube.v2.login.LoginSubStore
import com.tencent.rtcube.v2.login.R
import com.tencent.rtcube.v2.login.components.model.LoginError
import com.tencent.rtcube.v2.login.components.model.LoginResult
import com.tencent.rtcube.v2.login.components.model.UserModel
import com.tencent.rtcube.v2.login.components.service.LoginManager
import com.tencent.rtcube.v2.login.hiddenconfig.HiddenConfigCredentials
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject

class HiddenConfigStore : LoginSubStore {

    private val _state = MutableStateFlow(HiddenConfigState())
    val state: StateFlow<HiddenConfigState> = _state.asStateFlow()
    
    private val _resultFlow = MutableSharedFlow<Result<LoginResult>>(
        replay = 0,
        extraBufferCapacity = 1,
    )
    override val resultFlow: SharedFlow<Result<LoginResult>> = _resultFlow.asSharedFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    fun updateSdkAppId(value: String) {
        _state.update { it.copy(sdkAppId = value.trim()).withConfirmEnabled() }
    }

    fun updateUserId(value: String) {
        _state.update { it.copy(userId = value.trim()).withConfirmEnabled() }
    }

    fun updateUserSig(value: String) {
        _state.update { it.copy(userSig = value.trim()).withConfirmEnabled() }
    }

    /** JSON：{ "sdkAppId": xx, "userId": "xx", "userSig": "xx" } */
    fun handleQrCodeResult(result: String) {
        if (result.isEmpty()) return
        runCatching {
            val json = JSONObject(result)
            val sdkAppIdValue = json.optString("sdkAppId", "")
            val userIdValue = json.optString("userId", "")
            val userSigValue = json.optString("userSig", "")
            _state.update {
                it.copy(
                    sdkAppId = sdkAppIdValue.ifEmpty { it.sdkAppId },
                    userId = userIdValue.ifEmpty { it.userId },
                    userSig = userSigValue.ifEmpty { it.userSig },
                ).withConfirmEnabled()
            }
        }
    }

    fun buildCredentials(): HiddenConfigCredentials {
        val data = state.value
        return HiddenConfigCredentials(
            sdkAppId = data.sdkAppId,
            userId = data.userId,
            userSig = data.userSig,
        )
    }

    fun restoreDefault() {
        _state.update { HiddenConfigState() }
    }

    fun loginWithCredentials(credentials: HiddenConfigCredentials) {
        if (state.value.isLoading) return
        val sdkAppIdInt = credentials.sdkAppId.toIntOrNull()
        if (sdkAppIdInt == null || sdkAppIdInt <= 0) {
            val msg = LoginEntry.appContext.getString(R.string.login_error_login_failed)
            _resultFlow.tryEmit(Result.failure(LoginError.LoginFailed(-1, msg)))
            return
        }
        _state.update { it.copy(isLoading = true) }
        scope.launch {
            val userModel = UserModel(
                userId = credentials.userId,
                token = "",
                userSig = credentials.userSig,
                name = "",
            )
            try {
                LoginManager.loginIMWithCredentials(userModel, sdkAppIdInt)
                    .onSuccess {
                        _resultFlow.tryEmit(
                            Result.success(
                                LoginResult(
                                    userModel = userModel,
                                    loginMode = LoginMode.DEBUG_AUTH,
                                )
                            )
                        )
                    }
                    .onFailure { error ->
                        _resultFlow.tryEmit(Result.failure(error))
                    }
            } finally {
                _state.update { it.copy(isLoading = false) }
            }
        }
    }

    fun destroy() {
        scope.cancel()
    }

    private fun HiddenConfigState.withConfirmEnabled(): HiddenConfigState {
        val enabled = sdkAppId.isNotEmpty() && userId.isNotEmpty() && userSig.isNotEmpty()
        return copy(isConfirmEnabled = enabled)
    }
}
