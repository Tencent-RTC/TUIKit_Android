package com.tencent.rtcube.v2.main.domestic.store

import com.tencent.rtcube.v2.main.model.ResolvedModule

data class EntranceState(
    val modules: List<ResolvedModule> = emptyList(),
    val isReportViewVisible: Boolean = false,
    val userAvatarUrl: String = "",
    val isNeedFaceAuth: Boolean = false,
)
