package com.tencent.rtcube.v2.login.hiddenconfig.store

data class HiddenConfigState(
    val sdkAppId: String = "",
    val userId: String = "",
    val userSig: String = "",
    val isConfirmEnabled: Boolean = false,
    val isLoading: Boolean = false,
)
