package com.tencent.rtcube.v2.login.emailverify.store

import android.app.Activity
import com.tencent.rtcube.v2.login.LoginEntry
import com.tencent.rtcube.v2.login.LoginSubStore
import com.tencent.rtcube.v2.login.R
import com.tencent.rtcube.v2.login.components.model.LoginError
import com.tencent.rtcube.v2.login.components.model.LoginResult
import com.tencent.rtcube.v2.login.components.service.CaptchaService
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

class EmailVerifyStore : LoginSubStore {

    private companion object {
        private const val VERIFY_CODE_LENGTH = 6
    }

    private data class LoginFailureFeedback(
        val toastMessage: String,
        val highlightVerifyCode: Boolean = false,
        val clearVerifyCode: Boolean = false,
        val focusVerifyCode: Boolean = false,
        val resetSession: Boolean = false,
    )

    private val _state = MutableStateFlow(EmailVerifyState())
    val state: StateFlow<EmailVerifyState> = _state.asStateFlow()

    private val _resultFlow = MutableSharedFlow<Result<LoginResult>>(replay = 1)
    override val resultFlow: SharedFlow<Result<LoginResult>> = _resultFlow.asSharedFlow()

    private val captchaService = CaptchaService()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var countdownJob: Job? = null

    fun updateEmail(email: String) {
        val normalizedEmail = email.trim()
        val currentState = state.value
        if (currentState.email == normalizedEmail) return

        // Reset verification state if a code was already sent
        val shouldResetVerification = currentState.sessionId.isNotEmpty()
                || currentState.verifyCode.isNotEmpty()
                || currentState.countdownSeconds > 0
                || currentState.isCodeInputError
        if (shouldResetVerification) {
            countdownJob?.cancel()
        }

        _state.update { current ->
            current.copy(
                email = normalizedEmail,
                verifyCode = if (shouldResetVerification) "" else current.verifyCode,
                sessionId = if (shouldResetVerification) "" else current.sessionId,
                countdownSeconds = if (shouldResetVerification) 0 else current.countdownSeconds,
                isCodeInputError = false,
            )
        }
    }

    fun updateVerifyCode(code: String) {
        val normalizedCode = code.filter(Char::isDigit).take(VERIFY_CODE_LENGTH)
        val currentState = state.value
        if (currentState.verifyCode == normalizedCode && !currentState.isCodeInputError) return

        _state.update { current ->
            current.copy(
                verifyCode = normalizedCode,
                isCodeInputError = false,
                autoLoginEvent = if (normalizedCode.length == VERIFY_CODE_LENGTH) {
                    current.autoLoginEvent + 1
                } else {
                    current.autoLoginEvent
                },
            )
        }
    }

    fun sendVerifyCode(activity: Activity) {
        val currentState = state.value
        val email = currentState.email.trim()
        if (email.isEmpty() || currentState.isLoading || currentState.countdownSeconds > 0) return

        val requestedEmail = email
        _state.update { it.copy(isLoading = true) }

        captchaService.verify(
            activity = activity,
            onSuccess = { captchaResult ->
                scope.launch {
                    LoginManager.sendEmailVerifyCode(
                        email = requestedEmail,
                        captcha = captchaResult,
                    ).onSuccess { sessionId ->
                        _state.update { current ->
                            current.copy(
                                isLoading = false,
                                sessionId = sessionId,
                                verifyCode = "",
                                toastMessage = LoginEntry.appContext.getString(R.string.login_verify_code_sent),
                                focusCodeInputEvent = current.focusCodeInputEvent + 1,
                                isCodeInputError = false,
                            )
                        }
                        startCountdown()
                    }.onFailure { error ->
                        _state.update {
                            it.copy(
                                isLoading = false,
                                toastMessage = error.message
                                    ?: LoginEntry.appContext.getString(R.string.login_error_send_failed)
                            )
                        }
                    }
                }
            },
            onFailed = { errorMessage ->
                _state.update {
                    it.copy(isLoading = false, toastMessage = errorMessage)
                }
            }
        )
    }

    fun login() {
        val currentState = state.value
        if (currentState.isFullScreenLoading) return

        if (currentState.sessionId.isEmpty()) {
            _state.update {
                it.copy(toastMessage = LoginEntry.appContext.getString(R.string.login_error_get_code_first))
            }
            return
        }
        if (currentState.verifyCode.length != VERIFY_CODE_LENGTH) {
            _state.update {
                it.copy(toastMessage = LoginEntry.appContext.getString(R.string.login_error_code_empty))
            }
            return
        }

        _state.update {
            it.copy(
                isFullScreenLoading = true,
                fullScreenLoadingMessage = LoginEntry.appContext.getString(R.string.login_status_logging_in)
            )
        }

        scope.launch {
            LoginManager.loginByEmail(
                email = currentState.email,
                sessionId = currentState.sessionId,
                code = currentState.verifyCode,
            ).onSuccess { loginResult ->
                _state.update { it.copy(isFullScreenLoading = false, isCodeInputError = false) }
                _resultFlow.tryEmit(Result.success(loginResult))
            }.onFailure { error ->
                val feedback = resolveLoginFailureFeedback(error)
                if (feedback.resetSession) {
                    countdownJob?.cancel()
                }
                _state.update { current ->
                    current.copy(
                        isFullScreenLoading = false,
                        toastMessage = feedback.toastMessage,
                        verifyCode = if (feedback.clearVerifyCode) "" else current.verifyCode,
                        sessionId = if (feedback.resetSession) "" else current.sessionId,
                        countdownSeconds = if (feedback.resetSession) 0 else current.countdownSeconds,
                        isCodeInputError = feedback.highlightVerifyCode,
                        focusCodeInputEvent = if (feedback.focusVerifyCode) {
                            current.focusCodeInputEvent + 1
                        } else {
                            current.focusCodeInputEvent
                        },
                        codeErrorEvent = if (feedback.highlightVerifyCode) {
                            current.codeErrorEvent + 1
                        } else {
                            current.codeErrorEvent
                        },
                    )
                }
            }
        }
    }

    fun getInviteCodeAndNavigate(onSuccess: () -> Unit) {
        val email = state.value.email.trim()
        if (email.isEmpty() || state.value.isLoading) return

        _state.update {
            it.copy(
                isLoading = true,
                toastMessage = "",
                isFullScreenLoading = false,
                isCodeInputError = false
            )
        }

        scope.launch {
            LoginManager.getInviteCode(email)
                .onSuccess {
                    _state.update {
                        it.copy(
                            isLoading = false,
                            toastMessage = "",
                            isFullScreenLoading = false,
                            isCodeInputError = false
                        )
                    }
                    onSuccess()
                }
                .onFailure { error ->
                    _state.update { currentState ->
                        currentState.copy(
                            isLoading = false,
                            toastMessage = error.message ?: LoginEntry.appContext.getString(R.string.login_error_send_failed),
                            isFullScreenLoading = false,
                            isCodeInputError = false
                        )
                    }
                }
        }
    }

    fun destroy() {
        countdownJob?.cancel()
        captchaService.destroy()
        scope.cancel()
    }

    private fun startCountdown() {
        countdownJob?.cancel()
        _state.update { it.copy(countdownSeconds = 60) }
        countdownJob = scope.launch {
            for (i in 59 downTo 0) {
                delay(1000)
                _state.update { it.copy(countdownSeconds = i) }
            }
        }
    }

    private fun resolveLoginFailureFeedback(error: Throwable): LoginFailureFeedback {
        return when (error) {
            is LoginError.NetworkError -> LoginFailureFeedback(
                toastMessage = error.message ?: LoginEntry.appContext.getString(R.string.login_error_login_failed)
            )

            is LoginError.TokenExpired -> LoginFailureFeedback(
                toastMessage = LoginEntry.appContext.getString(R.string.login_token_expired)
            )

            is LoginError.LoginFailed -> {
                when {
                    isVerifyCodeExpired(error.message) -> LoginFailureFeedback(
                        toastMessage = LoginEntry.appContext.getString(R.string.login_error_code_expired),
                        highlightVerifyCode = true,
                        clearVerifyCode = true,
                        focusVerifyCode = true,
                        resetSession = true,
                    )

                    isVerifyCodeInvalid(error.message) -> LoginFailureFeedback(
                        toastMessage = LoginEntry.appContext.getString(R.string.login_error_code_invalid),
                        highlightVerifyCode = true,
                        clearVerifyCode = true,
                        focusVerifyCode = true,
                    )

                    else -> LoginFailureFeedback(
                        toastMessage = error.message.ifEmpty {
                            LoginEntry.appContext.getString(R.string.login_error_login_failed)
                        }
                    )
                }
            }

            else -> LoginFailureFeedback(
                toastMessage = error.message ?: LoginEntry.appContext.getString(R.string.login_error_login_failed)
            )
        }
    }

    private fun isVerifyCodeExpired(message: String): Boolean {
        val normalizedMessage = message.lowercase()
        return normalizedMessage.contains("expired")
                || normalizedMessage.contains("expire")
                || message.contains("过期")
                || message.contains("失效")
    }

    private fun isVerifyCodeInvalid(message: String): Boolean {
        val normalizedMessage = message.lowercase()
        val hasVerifyKeyword = message.contains("验证码")
                || normalizedMessage.contains("verification code")
                || normalizedMessage.contains("verify code")
        val hasInvalidKeyword = message.contains("错误")
                || normalizedMessage.contains("invalid")
                || normalizedMessage.contains("wrong")
                || normalizedMessage.contains("error")
        return hasVerifyKeyword || (normalizedMessage.contains("code") && hasInvalidKeyword)
    }
}
