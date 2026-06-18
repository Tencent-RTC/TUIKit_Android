package com.tencent.rtcube.v2.login.debugauth.store

import com.tencent.rtcube.v2.login.LoginMode
import com.tencent.rtcube.v2.login.LoginSubStore
import com.tencent.rtcube.v2.login.components.model.LoginResult
import com.tencent.rtcube.v2.login.components.service.LoginManager
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

class DebugAuthStore : LoginSubStore {

    private val _state = MutableStateFlow(DebugAuthState())
    val state: StateFlow<DebugAuthState> = _state.asStateFlow()

    private val _resultFlow = MutableSharedFlow<Result<LoginResult>>(replay = 1)
    override val resultFlow: SharedFlow<Result<LoginResult>> = _resultFlow.asSharedFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    fun updateUserId(userId: String) {
        _state.update { it.copy(userId = userId) }
    }

    fun login() {
        val uid = state.value.userId.trim()
        if (uid.isEmpty()) return
        loginWithUserId(uid)
    }

    fun loginWithUserId(userId: String) {
        if (state.value.isLoading) return
        _state.update { it.copy(isLoading = true) }
        scope.launch {
            LoginManager.loginByDebug(userId)
                .onSuccess { loginResult ->
                    _state.update { it.copy(isLoading = false) }
                    _resultFlow.tryEmit(Result.success(loginResult.copy(loginMode = LoginMode.DEBUG_AUTH)))
                }
                .onFailure { error ->
                    _state.update { it.copy(isLoading = false) }
                    _resultFlow.tryEmit(Result.failure(error))
                }
        }
    }

    fun destroy() {
        scope.cancel()
    }

    fun clearResult() {
        _resultFlow.resetReplayCache()
    }
}
