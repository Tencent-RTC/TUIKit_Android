package com.tencent.rtcube.v2.login.components.model

import com.tencent.rtcube.v2.login.R
import com.tencent.rtcube.v2.login.LoginEntry

sealed class LoginError : Exception() {

    object Cancelled : LoginError() {
        private fun readResolve(): Any = Cancelled
        override val message: String get() = LoginEntry.appContext.getString(R.string.login_user_cancelled)
    }

    data class NetworkError(override val message: String) : LoginError()

    data class VerifyCodeFailed(override val message: String) : LoginError()

    data class LoginFailed(val code: Int, override val message: String) : LoginError()

    object TokenExpired : LoginError() {
        private fun readResolve(): Any = TokenExpired
        override val message: String get() = LoginEntry.appContext.getString(R.string.login_token_expired)
    }

    data class IOAAuthFailed(override val message: String) : LoginError()

    data class Unknown(override val message: String) : LoginError()
}
