package com.tencent.rtcube.modules.call.online.model
data class CallingMenuModel(
    val title: String,
    val content: String,
    val stressContent: List<String> = emptyList(),
)
