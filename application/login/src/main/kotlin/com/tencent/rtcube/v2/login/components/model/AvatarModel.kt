package com.tencent.rtcube.v2.login.components.model

import kotlinx.serialization.Serializable

@Serializable
data class AvatarModel(
    val avatarUrl: String = "",
    val isSelected: Boolean = false,
)
