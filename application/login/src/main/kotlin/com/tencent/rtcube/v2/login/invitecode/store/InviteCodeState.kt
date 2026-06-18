package com.tencent.rtcube.v2.login.invitecode.store

data class InviteCodeState(
    val inviteCode: String = "",
    val isErrorState: Boolean = false,
    val isVerifying: Boolean = false,
    val isLoading: Boolean = false,
    val toastMessage: String = "",
    val isAgreeTerms: Boolean = true,
    val showTermsTooltip: Boolean = false,
)
