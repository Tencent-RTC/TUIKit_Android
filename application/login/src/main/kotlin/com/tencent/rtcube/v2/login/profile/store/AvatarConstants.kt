package com.tencent.rtcube.v2.login.profile.store

internal object AvatarConstants {
    val USER_AVATAR_ARRAY: List<String> = (1..24).map {
        "https://liteav.sdk.qcloud.com/app/res/picture/voiceroom/avatar/user_avatar$it.png"
    }
}