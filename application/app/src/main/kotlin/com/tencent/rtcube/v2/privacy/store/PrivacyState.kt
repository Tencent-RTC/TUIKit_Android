package com.tencent.rtcube.v2.privacy.store

data class PrivacyState(
    val userId: String = "",
    val userName: String = "",
    val userAvatar: String = "",
    val phone: String = "",
    val email: String = "",
    val cameraGranted: Boolean = false,
    val storageGranted: Boolean = false,
    val micGranted: Boolean = false,
    val beautyAllowed: Boolean = false,
    val avatarAllowed: Boolean = false,
)
