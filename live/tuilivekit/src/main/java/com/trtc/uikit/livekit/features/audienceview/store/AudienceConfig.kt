package com.trtc.uikit.livekit.features.audienceview.store

import kotlinx.coroutines.flow.MutableStateFlow

object AudienceConfig {
    val disableSliding: MutableStateFlow<Boolean> = MutableStateFlow(false)

    @JvmStatic
    fun disableSliding(disable: Boolean) {
        disableSliding.value = disable
    }
}