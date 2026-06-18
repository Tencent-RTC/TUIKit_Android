package com.tencent.rtcube.v2.login.emailverify.store

/**
 * Email invite-code input screen state (replaces InviteCodeInputActivity).
 */
data class EmailInviteCodeState(
    val email: String = "",
    val inviteCode: String = "",
    val isErrorState: Boolean = false,
    val isVerifying: Boolean = false,
    val countdownSeconds: Int = 60,
    val toastMessage: String = "",
    val isFullScreenLoading: Boolean = false,
    val fullScreenLoadingMessage: String = "",
    val isAgreeTerms: Boolean = false,
    val isMarketing: Boolean = false,
    val showTermsTooltip: Boolean = false,
)
