package com.tencent.rtcube.v2.mine.store

data class MineState(
    val userId: String = "",
    val nickname: String = "",
    val avatarUrl: String = "",
    val toastMessage: String = "",
    val isLogoutDialogVisible: Boolean = false,
    val isLoggingOut: Boolean = false,
    val isStatementDialogVisible: Boolean = false,
    val isAboutVisible: Boolean = false,
    val isLogOffVisible: Boolean = false,
    val isLogOffDialogVisible: Boolean = false,
    val isLogOffLoading: Boolean = false,
    val isExperienceVisible: Boolean = false,
)
