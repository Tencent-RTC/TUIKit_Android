package com.tencent.rtcube.v2.login.invitecode.utils

object InviteCodeValidator {

    fun isValid(code: String): Boolean {
        return code.length >= 6
    }
}
