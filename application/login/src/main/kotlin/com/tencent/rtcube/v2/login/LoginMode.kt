package com.tencent.rtcube.v2.login

enum class LoginMode {
    PHONE_VERIFY,
    EMAIL_VERIFY,
    IOA_AUTH,
    INVITE_CODE,
    TOKEN_AUTH,  // Token auto login (no UI, silent execution)
    DEBUG_AUTH,
    MENU,
}
