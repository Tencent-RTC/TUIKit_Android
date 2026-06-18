package com.tencent.rtcube.v2.main.overseas.store

import com.tencent.rtcube.v2.main.model.ResolvedModule

data class OverSeasEntranceState(
    val productsModules: List<ResolvedModule> = emptyList(),
    val discoveryModules: List<ResolvedModule> = emptyList(),
    val selectedTabIndex: Int = 0,
    val userAvatarUrl: String = "",
)
