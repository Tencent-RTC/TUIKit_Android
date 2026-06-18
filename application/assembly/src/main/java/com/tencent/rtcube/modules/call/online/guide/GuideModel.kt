package com.tencent.rtcube.modules.call.online.guide


enum class AvatarType(val value: Int) {
    RIGHT(1),
    LEFT(2);

    companion object {
        fun fromValue(value: Int): AvatarType =
            entries.firstOrNull { it.value == value } ?: RIGHT
    }
}

data class GuideModel(
    val avatarType: AvatarType,
    val avatarImageName: String,
    val name: String,
    val text: String,
    val hasCopyButton: Boolean = false,
    val leftContextImageName: String = "",
    val rightContextImageName: String = "",
)

data class GuideHomeModel(
    val singlePlayerJsonName: String? = null,
    val withAppJsonName: String? = null,
    val withWebJsonName: String? = null,
)
