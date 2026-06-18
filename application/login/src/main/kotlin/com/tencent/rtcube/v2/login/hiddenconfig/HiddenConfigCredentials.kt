package com.tencent.rtcube.v2.login.hiddenconfig

import java.io.Serializable

data class HiddenConfigCredentials(
    val sdkAppId: String,
    val userId: String,
    val userSig: String,
) : Serializable
