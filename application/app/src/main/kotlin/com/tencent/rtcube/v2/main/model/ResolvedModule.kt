package com.tencent.rtcube.v2.main.model

import com.tencent.rtcube.assembly.ModuleConfig
import com.tencent.rtcube.assembly.ModuleProvider

data class ResolvedModule(
    val config: ModuleConfig,
    var badgeCount: Long = 0L,
    var isVisible: Boolean = true,
    val provider: ModuleProvider? = null,
)