package com.trtc.uikit.livekit.voicelounge.settings.aitranscriber

import android.content.Context
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.trtc.uikit.livekit.R
import com.trtc.uikit.livekit.common.theme.BottomSheetView
import com.trtc.uikit.livekit.common.theme.LiveSwitch
import com.trtc.uikit.livekit.common.theme.LiveTheme
import com.trtc.uikit.livekit.voicelounge.MATCH
import com.trtc.uikit.livekit.voicelounge.WRAP
import com.trtc.uikit.livekit.voicelounge.dp
import com.trtc.uikit.livekit.voicelounge.frameParams
import com.trtc.uikit.livekit.voicelounge.linearParams
import com.trtc.uikit.livekit.voicelounge.roundedDrawable
import com.trtc.uikit.livekit.voicelounge.settings.theme.VoiceLoungeColor
import com.trtc.uikit.livekit.voicelounge.settings.theme.VoiceLoungeSkin
import com.trtc.uikit.livekit.voicelounge.voiceLoungeText

internal class VoiceLoungeAITranscriberPanel(context: Context) : BottomSheetView(context) {
    var onToggle: ((Boolean) -> Unit)? = null

    private val card = FrameLayout(context)
    private val titleLabel = voiceLoungeText(context, LiveTheme.TextRole.ACTION, LiveTheme.TextTone.PRIMARY)
    private val subtitleLabel = voiceLoungeText(context, LiveTheme.TextRole.CAPTION, LiveTheme.TextTone.TERTIARY)
    private val toggleSwitch = LiveSwitch(context)
    private var skin = VoiceLoungeSkin.defaultSkin

    override val sheetHeight: Int get() = context.dp(200)

    init {
        val textColumn = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        textColumn.addView(titleLabel, linearParams(WRAP, WRAP))
        textColumn.addView(subtitleLabel, linearParams(WRAP, WRAP, top = context.dp(6)))
        card.addView(
            textColumn,
            frameParams(
                MATCH, WRAP, Gravity.TOP, start = context.dp(16), top = context.dp(16), end = context.dp(16 + 51 + 10),
            ),
        )
        card.addView(
            toggleSwitch,
            frameParams(context.dp(51), context.dp(31), Gravity.END or Gravity.CENTER_VERTICAL, end = context.dp(16)),
        )
        contentView.addView(
            card,
            frameParams(MATCH, context.dp(80), start = context.dp(16), end = context.dp(16), top = context.dp(66)),
        )
        sheetTitle = context.getString(R.string.livekit_voiceroom_caption_title)
        card.background = roundedDrawable(VoiceLoungeColor.overlayHair, context.dp(16).toFloat())
        titleLabel.text = context.getString(R.string.livekit_voiceroom_caption_title)
        subtitleLabel.text = context.getString(R.string.livekit_voiceroom_caption_subtitle)
        toggleSwitch.onCheckedChange = { isOn -> onToggle?.invoke(isOn) }
    }

    fun show(isOn: Boolean, skin: VoiceLoungeSkin) {
        toggleSwitch.setChecked(isOn, animate = false)
        this.skin = skin
        toggleSwitch.onTrackColor = skin.mid
        show()
    }
}
