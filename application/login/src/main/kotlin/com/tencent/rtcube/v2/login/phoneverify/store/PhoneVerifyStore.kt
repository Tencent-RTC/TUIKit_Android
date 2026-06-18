package com.tencent.rtcube.v2.login.phoneverify.store

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

class PhoneVerifyStore : LoginSubStore {

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

    private val _state = MutableStateFlow(PhoneVerifyState())
    val state: StateFlow<PhoneVerifyState> = _state.asStateFlow()

    private val _resultFlow = MutableSharedFlow<Result<LoginResult>>(replay = 1)
    override val resultFlow: SharedFlow<Result<LoginResult>> = _resultFlow.asSharedFlow()

    private val captchaService = CaptchaService()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var countdownJob: Job? = null

    fun updatePhoneNumber(phone: String) {
        val normalizedPhone = phone.trim()
        val currentState = state.value
        if (currentState.phoneNumber == normalizedPhone) {
            return
        }

        val shouldResetVerification = currentState.sessionId.isNotEmpty()
                || currentState.verifyCode.isNotEmpty()
                || currentState.countdownSeconds > 0
                || currentState.isCodeInputError
        if (shouldResetVerification) {
            countdownJob?.cancel()
        }

        _state.update { current ->
            current.copy(
                phoneNumber = normalizedPhone,
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
        if (currentState.verifyCode == normalizedCode && !currentState.isCodeInputError) {
            return
        }

        _state.update { current ->
            current.copy(
                verifyCode = normalizedCode,
                isCodeInputError = false,
            )
        }
    }

    fun sendVerifyCode(activity: Activity) {
        val currentState = state.value
        val phoneNumber = currentState.phoneNumber.trim()
        if (phoneNumber.isEmpty() || currentState.isLoading || currentState.countdownSeconds > 0) {
            return
        }

        val requestedPhone = currentState.regionCode + phoneNumber
        _state.update { it.copy(isLoading = true) }

        captchaService.verify(
            activity = activity,
            onSuccess = { captchaResult ->
                scope.launch {
                    LoginManager.sendSms(
                        phone = requestedPhone,
                        captcha = captchaResult,
                    ).onSuccess { sessionId ->
                        if (!isCurrentPhoneRequest(requestedPhone)) {
                            _state.update { it.copy(isLoading = false) }
                            return@onSuccess
                        }

                        _state.update { current ->
                            current.copy(
                                isLoading = false,
                                sessionId = sessionId,
                                toastMessage = LoginEntry.appContext.getString(R.string.login_verify_code_sent),
                                focusCodeInputEvent = current.focusCodeInputEvent + 1,
                                isCodeInputError = false,
                            )
                        }
                        startCountdown()
                    }.onFailure { error ->
                        if (!isCurrentPhoneRequest(requestedPhone)) {
                            _state.update { it.copy(isLoading = false) }
                            return@onFailure
                        }

                        val errorMessage = resolveSmsSendErrorMessage(error)
                        _state.update {
                            it.copy(
                                isLoading = false,
                                toastMessage = errorMessage,
                            )
                        }
                    }
                }
            },
            onFailed = { errorMessage ->
                if (!isCurrentPhoneRequest(requestedPhone)) {
                    _state.update { it.copy(isLoading = false) }
                    return@verify
                }

                _state.update {
                    it.copy(
                        isLoading = false,
                        toastMessage = resolveNetworkErrorMessage(errorMessage),
                    )
                }
            }
        )
    }

    fun login() {
        val currentState = state.value
        if (currentState.isFullScreenLoading) {
            return
        }
        if (currentState.sessionId.isEmpty()) {
            _state.update {
                it.copy(toastMessage = LoginEntry.appContext.getString(R.string.login_error_get_code_first))
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
            LoginManager.loginByPhone(
                phone = currentState.regionCode + currentState.phoneNumber,
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

    fun destroy() {
        countdownJob?.cancel()
        captchaService.destroy()
        scope.cancel()
    }

    private fun isCurrentPhoneRequest(requestedPhone: String): Boolean {
        val currentState = state.value
        return currentState.regionCode + currentState.phoneNumber.trim() == requestedPhone
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
                toastMessage = resolveNetworkErrorMessage(error.message)
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

    private fun resolveSmsSendErrorMessage(error: Throwable): String {
        val message = error.message ?: ""
        // Error code 223: IoT SIM card (LOGIN_ERROR_IOT = 223)
        if (message.contains("223") || message.contains("iot", ignoreCase = true)
            || message.contains("iccid", ignoreCase = true)
        ) {
            return LoginEntry.appContext.getString(R.string.login_error_iot_phone)
        }
        return resolveNetworkErrorMessage(message.ifEmpty {
            LoginEntry.appContext.getString(R.string.login_error_send_failed)
        })
    }

    private fun resolveNetworkErrorMessage(message: String): String {
        val normalizedMessage = message.lowercase()
        return if (
            normalizedMessage.contains("timeout")
            || normalizedMessage.contains("timed out")
            || message.contains("超时")
        ) {
            LoginEntry.appContext.getString(R.string.login_error_network_timeout)
        } else {
            message
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
