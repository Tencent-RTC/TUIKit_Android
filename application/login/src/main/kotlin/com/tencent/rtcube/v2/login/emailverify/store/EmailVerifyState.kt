package com.tencent.rtcube.v2.login.emailverify.store

data class EmailVerifyState(
    val email: String = "",
    val verifyCode: String = "",
    val sessionId: String = "",
    val isLoading: Boolean = false,
    val countdownSeconds: Int = 0,
    val toastMessage: String = "",
    val fullScreenLoadingMessage: String = "",
    val isFullScreenLoading: Boolean = false,
    val focusCodeInputEvent: Long = 0,
    val autoLoginEvent: Long = 0,
    val isCodeInputError: Boolean = false,
    val codeErrorEvent: Long = 0,
    val showIoaLogin: Boolean = true,
)
