package com.trtc.uikit.livekit.voicelounge.settings.audioeffect

import com.tencent.trtc.TRTCCloud
import com.trtc.uikit.livekit.common.ContextProvider
import org.json.JSONObject

internal object VoiceLoungeVoiceTuner {
    var isCustom: Boolean = false
        private set

    var customX: Float = 0f
        private set
    var customY: Float = 0f
        private set

    private var lastPitch: Double? = null
    private var lastFormant: Double? = null

    fun applyCustom(x: Float, y: Float) {
        isCustom = true
        customX = x
        customY = y
        send(pitch = x.toDouble(), formant = (-y).toDouble(), force = false)
    }

    fun clearCustom() {
        if (!isCustom && lastPitch == null && lastFormant == null) return
        isCustom = false
        send(pitch = 0.0, formant = 0.0, force = false)
    }

    fun reapply() {
        val x = if (isCustom) customX else 0f
        val y = if (isCustom) customY else 0f
        send(pitch = x.toDouble(), formant = (-y).toDouble(), force = true)
    }

    fun reset() {
        isCustom = false
        customX = 0f
        customY = 0f
        send(pitch = 0.0, formant = 0.0, force = true)
        lastPitch = null
        lastFormant = null
    }

    private fun send(pitch: Double, formant: Double, force: Boolean) {
        val normalizedPitch = normalize(pitch)
        val normalizedFormant = normalize(formant)
        val context = ContextProvider.applicationContext ?: return
        val cloud = TRTCCloud.sharedInstance(context)
        if (force || lastPitch != normalizedPitch) {
            lastPitch = normalizedPitch
            cloud.audioEffectManager?.setVoicePitch(normalizedPitch)
        }
        if (force || lastFormant != normalizedFormant) {
            lastFormant = normalizedFormant
            setFormant(cloud, normalizedFormant)
        }
    }

    private fun setFormant(cloud: TRTCCloud, value: Double) {
        val payload =
            JSONObject().put("api", "setAudioFormant").put("params", JSONObject().put("formantRatio", value)).toString()
        cloud.callExperimentalAPI(payload)
    }

    private fun normalize(value: Double): Double {
        val clipped = value.coerceIn(-1.0, 1.0)
        return Math.round(clipped * 100) / 100.0
    }
}
