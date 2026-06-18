package com.tencent.rtcube.v2.login.invitecode.store

import com.tencent.rtcube.v2.login.LoginEntry
import com.tencent.rtcube.v2.login.LoginSubStore
import com.tencent.rtcube.v2.login.R
import com.tencent.rtcube.v2.login.components.model.LoginError
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

class InviteCodeStore : LoginSubStore {

    private val _state = MutableStateFlow(InviteCodeState())
    val state: StateFlow<InviteCodeState> = _state.asStateFlow()

    private val _resultFlow = MutableSharedFlow<Result<LoginResult>>(replay = 1)
    override val resultFlow: SharedFlow<Result<LoginResult>> = _resultFlow.asSharedFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    fun updateInviteCode(code: String) {
        _state.update { it.copy(inviteCode = code, isErrorState = false) }
    }

    fun updateAgreeTerms(agree: Boolean) {
        _state.update {
            it.copy(
                isAgreeTerms = agree,
                showTermsTooltip = if (agree) false else it.showTermsTooltip
            )
        }
    }

    fun dismissTermsTooltip() {
        _state.update { it.copy(showTermsTooltip = false) }
    }

    fun login() {
        val code = state.value.inviteCode.trim()
        if (code.isEmpty()) {
            _state.update {
                it.copy(toastMessage = LoginEntry.appContext.getString(R.string.login_error_invite_empty))
            }
            return
        }

        if (!state.value.isAgreeTerms) {
            _state.update { it.copy(showTermsTooltip = true) }
            return
        }

        _state.update { it.copy(isVerifying = true, isLoading = true) }

        scope.launch {
            LoginManager.loginByInviteCode(code)
                .onSuccess { loginResult ->
                    _state.update { it.copy(isVerifying = false, isLoading = false) }
                    _resultFlow.tryEmit(Result.success(loginResult))
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isVerifying = false,
                            isLoading = false,
                            isErrorState = true,
                            toastMessage = (error as? LoginError)?.message
                                ?: LoginEntry.appContext.getString(R.string.login_error_login_failed)
                        )
                    }
                }
        }
    }

    fun destroy() {
        scope.cancel()
    }
}
