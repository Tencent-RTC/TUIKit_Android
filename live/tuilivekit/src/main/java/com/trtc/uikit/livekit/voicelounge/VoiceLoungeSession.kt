package com.trtc.uikit.livekit.voicelounge

import io.trtc.tuikit.atomicxcore.api.live.VoiceLounge

object VoiceLoungeSession {
    private val instances = HashMap<String, VoiceLounge>()

    @JvmStatic
    fun of(liveID: String): VoiceLounge =
        synchronized(instances) {
            instances.getOrPut(liveID) { VoiceLounge() }
        }

    @JvmStatic
    fun release(liveID: String) {
        synchronized(instances) { instances.remove(liveID) }
    }
}
