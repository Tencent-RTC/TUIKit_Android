package com.tencent.rtcube.v2.login.emailverify.store

import com.tencent.rtcube.v2.login.LoginEntry
import com.tencent.rtcube.v2.login.LoginSubStore
import com.tencent.rtcube.v2.login.R
import com.tencent.rtcube.v2.login.components.model.LoginResult
import com.tencent.rtcube.v2.login.components.service.LoginManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class EmailInviteCodeStore(email: String) : LoginSubStore {

    private companion object {
        private const val INVITE_CODE_LENGTH = 6
        private const val COUNTDOWN_SECONDS = 60
    }

    private val _state = MutableStateFlow(
        EmailInviteCodeState(
            email = email,
            countdownSeconds = COUNTDOWN_SECONDS
        )
    )
    val state: StateFlow<EmailInviteCodeState> = _state.asStateFlow()

    private val _resultFlow = MutableSharedFlow<Result<LoginResult>>(replay = 1)
    override val resultFlow: SharedFlow<Result<LoginResult>> = _resultFlow.asSharedFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var countdownJob: Job? = null

    init {
        startCountdown()
    }

    fun updateInviteCode(code: String) {
        val normalizedCode = code.take(INVITE_CODE_LENGTH)
        _state.update { current ->
            current.copy(
                inviteCode = normalizedCode,
                isErrorState = if (current.isErrorState && normalizedCode.isNotEmpty()) false else current.isErrorState
            )
        }
    }

    fun toggleAgreeTerms(checked: Boolean) {
        _state.update {
            it.copy(
                isAgreeTerms = checked,
                showTermsTooltip = if (checked) false else it.showTermsTooltip
            )
        }
    }

    fun toggleMarketing(checked: Boolean) {
        _state.update { it.copy(isMarketing = checked) }
    }

    fun hideTermsTooltip() {
        _state.update { it.copy(showTermsTooltip = false) }
    }

    fun submitInviteCode() {
        val currentState = state.value
        if (!isInviteCodeComplete(currentState.inviteCode)) return
        if (currentState.isErrorState) return
        if (!currentState.isAgreeTerms) {
            _state.update { it.copy(showTermsTooltip = true) }
            return
        }

        _state.update {
            it.copy(
                isVerifying = true,
                isFullScreenLoading = true,
                fullScreenLoadingMessage = LoginEntry.appContext.getString(R.string.login_status_logging_in)
            )
        }

        scope.launch {
            LoginManager.loginByInviteCode(currentState.inviteCode)
                .onSuccess { loginResult ->
                    _state.update { it.copy(isVerifying = false, isFullScreenLoading = false) }
                    _resultFlow.tryEmit(Result.success(loginResult))
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isVerifying = false,
                            isFullScreenLoading = false,
                            isErrorState = true,
                            toastMessage = error.message
                                ?: LoginEntry.appContext.getString(R.string.login_error_login_failed)
                        )
                    }
                }
        }
    }

    fun resendInviteCode() {
        val email = state.value.email
        if (email.isEmpty() || state.value.countdownSeconds > 0) return

        scope.launch {
            LoginManager.getInviteCode(email)
                .onSuccess {
                    _state.update {
                        it.copy(
                            toastMessage = LoginEntry.appContext.getString(R.string.login_email_invite_code_resent)
                        )
                    }
                    startCountdown()
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            toastMessage = error.message
                                ?: LoginEntry.appContext.getString(R.string.login_error_send_failed)
                        )
                    }
                }
        }
    }

    fun destroy() {
        countdownJob?.cancel()
        scope.cancel()
    }

    private fun startCountdown() {
        countdownJob?.cancel()
        _state.update { it.copy(countdownSeconds = COUNTDOWN_SECONDS) }
        countdownJob = scope.launch {
            for (i in (COUNTDOWN_SECONDS - 1) downTo 0) {
                delay(1000)
                _state.update { it.copy(countdownSeconds = i) }
            }
        }
    }

    private fun isInviteCodeComplete(code: String): Boolean {
        return code.length == INVITE_CODE_LENGTH
    }
}
