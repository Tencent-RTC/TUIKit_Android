package com.tencent.rtcube.v2.login.components.model

import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.Serializable

@OptIn(InternalSerializationApi::class)
@Serializable
data class UserModel(
    val userId: String = "",
    val apaasUserId: String = "",  // In phone login tests, apaasUserId equals userId, but kept here in case of unknown differences.
    val token: String = "",
    val userSig: String = "",
    val sdkAppId: Int = 0,   // sdkAppId returned by the login API, paired with userSig.
    val phone: String = "",
    val email: String = "",
    val name: String = "",
    val avatar: String = "",
    val isHighRiskUser: Boolean = false,
    val isHighRiskIp: Boolean = false,
    val loginType: String = "",
) : java.io.Serializable {

    companion object {
        const val INTERNAL_USER_PREFIX = "moa"
    }

    fun isLoginByMOA(): Boolean = loginType == INTERNAL_USER_PREFIX
}
