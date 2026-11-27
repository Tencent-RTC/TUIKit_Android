package com.trtc.uikit.livekit.voicelounge.settings.audioeffect

import android.content.Context
import com.trtc.uikit.livekit.R
import com.trtc.uikit.livekit.common.theme.BottomSheetView
import com.trtc.uikit.livekit.voicelounge.MATCH
import com.trtc.uikit.livekit.voicelounge.dp
import com.trtc.uikit.livekit.voicelounge.frameParams
import com.trtc.uikit.livekit.voicelounge.settings.theme.VoiceLoungeSkin

internal class VoiceLoungeAudioEffectPanel(context: Context) : BottomSheetView(context) {
    var onSetCaptureVolume: ((Int) -> Unit)? = null

    private val audioEffectView = VoiceLoungeAudioEffectView(context)
    private var pendingSkin: VoiceLoungeSkin = VoiceLoungeSkin.defaultSkin

    override val sheetHeight: Int
        get() {
            val screenHeight = context.resources.displayMetrics.heightPixels
            return maxOf(screenHeight / 2, context.dp(MIN_HEIGHT_DP)).coerceAtMost(screenHeight - context.dp(40))
        }

    init {
        contentView.addView(audioEffectView, frameParams(MATCH, MATCH, top = context.dp(CONTENT_TOP_DP)))
        sheetTitle = context.getString(R.string.livekit_voicelounge_audio_title)
        audioEffectView.onSetCaptureVolume = { volume -> onSetCaptureVolume?.invoke(volume) }
    }

    fun show(captureVolume: Int, skin: VoiceLoungeSkin = pendingSkin) {
        pendingSkin = skin
        audioEffectView.bindCaptureVolume(captureVolume)
        audioEffectView.applySkin(skin)
        show()
    }

    override fun sheetWillPresent() {
        audioEffectView.setActive(true)
    }

    override fun sheetWillDismiss() {
        audioEffectView.setActive(false)
    }

    private companion object {
        const val MIN_HEIGHT_DP = 420
        const val CONTENT_TOP_DP = 58
    }
}
