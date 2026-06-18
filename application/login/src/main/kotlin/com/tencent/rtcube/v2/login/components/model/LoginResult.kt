package com.tencent.rtcube.v2.login.components.model

import com.tencent.rtcube.v2.login.LoginMode

data class LoginResult(
    val userModel: UserModel,
    val loginMode: LoginMode? = null,
) : java.io.Serializable
