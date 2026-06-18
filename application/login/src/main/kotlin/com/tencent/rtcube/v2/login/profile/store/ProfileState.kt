package com.tencent.rtcube.v2.login.profile.store

data class ProfileState(
    val userId: String = "",
    val nickname: String = "",
    val avatarUrl: String = "",
    /** Whether the nickname matches the rule: Chinese/letters/digits/underscore, length 2-20. */
    val isNicknameValid: Boolean = true,
    val isAvatarDialogVisible: Boolean = false,
    val isLoading: Boolean = false,
    val toastMessage: String = "",
)
