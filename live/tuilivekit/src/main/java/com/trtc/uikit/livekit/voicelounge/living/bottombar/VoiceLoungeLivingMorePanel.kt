package com.trtc.uikit.livekit.voicelounge.living.bottombar

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.trtc.uikit.livekit.R
import com.trtc.uikit.livekit.common.theme.BottomSheetView
import com.trtc.uikit.livekit.common.theme.LiveTheme
import com.trtc.uikit.livekit.voicelounge.MATCH
import com.trtc.uikit.livekit.voicelounge.WRAP
import com.trtc.uikit.livekit.voicelounge.dp
import com.trtc.uikit.livekit.voicelounge.frameParams
import com.trtc.uikit.livekit.voicelounge.linearParams
import com.trtc.uikit.livekit.voicelounge.living.VoiceLoungeLivingActions
import com.trtc.uikit.livekit.voicelounge.ovalDrawable
import com.trtc.uikit.livekit.voicelounge.setPressAlpha
import com.trtc.uikit.livekit.voicelounge.settings.VoiceLoungeSettings
import com.trtc.uikit.livekit.voicelounge.settings.aitranscriber.VoiceLoungeAITranscriberPanel
import com.trtc.uikit.livekit.voicelounge.settings.seatlayout.VoiceLoungeSeatLayoutPanel
import com.trtc.uikit.livekit.voicelounge.settings.theme.VoiceLoungeColor
import com.trtc.uikit.livekit.voicelounge.voiceLoungeText
import com.trtc.uikit.livekit.voicelounge.whiteAlpha

internal class VoiceLoungeLivingMorePanel(
    context: Context,
    private val settings: VoiceLoungeSettings,
    private val livingActions: VoiceLoungeLivingActions,
) : BottomSheetView(context) {
    var onComment: (() -> Unit)? = null

    private val layoutPanel by lazy {
        VoiceLoungeSeatLayoutPanel(context).apply { onConfirm = { livingActions.changeLayout(it) } }
    }
    private val transcriberPanel by lazy {
        VoiceLoungeAITranscriberPanel(context).apply {
            onToggle = { isOn -> livingActions.toggleTranscriber(isOn) }
        }
    }
    private var bottomInset = 0

    override val sheetHeight: Int get() = context.dp(CONTENT_HEIGHT_DP) + bottomInset

    init {
        sheetTitle = null
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.TOP
        }
        val hInset = context.dp(HORIZONTAL_INSET_DP)
        contentView.addView(
            row, frameParams(MATCH, WRAP, Gravity.TOP, start = hInset, end = hInset, top = context.dp(TOP_INSET_DP)),
        )

        val pauseItem = makeItem(R.drawable.livekit_living_setting_pause, R.string.livekit_voiceroom_living_pause)
        val layoutItem = makeItem(R.drawable.livekit_living_more, R.string.livekit_voiceroom_living_layout_setting)
        val transcriberItem = makeItem(
            iconFactory = { TranscriberIconView(context) },
            title = R.string.livekit_voiceroom_caption_title,
        )
        val commentItem = makeItem(R.drawable.livekit_living_setting_comment, R.string.livekit_voiceroom_living_comment)
        val items = listOf<View>(pauseItem, layoutItem, transcriberItem, commentItem, View(context))
        items.forEach { row.addView(it, linearParams(0, WRAP, 1f)) }

        pauseItem.setOnClickListener {
            dismiss()
            livingActions.pauseLive()
        }
        layoutItem.setOnClickListener {
            dismiss()
            layoutPanel.show(settings.seatLayout, setAsDefault = true, skin = settings.skin)
        }
        transcriberItem.setOnClickListener {
            dismiss()
            transcriberPanel.show(settings.isTranscriberEnabled, settings.skin)
        }
        commentItem.setOnClickListener {
            dismiss()
            onComment?.invoke()
        }
    }

    override fun show() {
        bottomInset = navigationBarHeight()
        updateSheetHeight(animated = false)
        super.show()
    }

    fun dismissChildren() {
        if (layoutPanel.isShowing) layoutPanel.dismiss()
        if (transcriberPanel.isShowing) transcriberPanel.dismiss()
    }

    private fun navigationBarHeight(): Int {
        val insets = (context as? android.app.Activity)?.window?.decorView?.rootWindowInsets ?: return 0
        return androidx.core.view.WindowInsetsCompat.toWindowInsetsCompat(insets)
            .getInsets(androidx.core.view.WindowInsetsCompat.Type.navigationBars()).bottom
    }

    private fun makeItem(@DrawableRes icon: Int, @StringRes title: Int): LinearLayout = makeItem(
        iconFactory = {
            ImageView(context).apply {
                setImageResource(icon)
                scaleType = ImageView.ScaleType.FIT_CENTER
            }
        },
        title = title,
    )

    private fun makeItem(iconFactory: () -> View, @StringRes title: Int): LinearLayout {
        val item = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        val backgroundSize = context.dp(ICON_PLATE_DP)
        val iconSize = context.dp(ICON_SIZE_DP)
        val iconBackground = FrameLayout(context).apply { background = ovalDrawable(VoiceLoungeColor.overlaySoft) }
        iconBackground.addView(iconFactory(), frameParams(iconSize, iconSize, Gravity.CENTER))
        item.addView(iconBackground, linearParams(backgroundSize, backgroundSize, top = context.dp(4)))
        val label = voiceLoungeText(
            context, LiveTheme.TextRole.CONTROL, LiveTheme.TextTone.SECONDARY, Gravity.CENTER,
            text = context.getString(title),
        )
        label.setSingleLine(true)
        item.addView(label, linearParams(MATCH, WRAP, top = context.dp(8)))
        item.setPressAlpha()
        return item
    }

    private class TranscriberIconView(context: Context) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.8f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            color = whiteAlpha(0.92f)
        }
        private val path = Path()

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val scale = minOf(width, height) / 24f
            path.reset()
            path.addRoundRect(RectF(3.5f, 5.5f, 20.5f, 18.5f), 3.2f, 3.2f, Path.Direction.CW)
            path.moveTo(7.5f, 10.2f)
            path.lineTo(16.5f, 10.2f)
            path.moveTo(7.5f, 13.8f)
            path.lineTo(12.5f, 13.8f)
            canvas.save()
            canvas.scale(scale, scale)
            canvas.drawPath(path, paint)
            canvas.restore()
        }
    }

    private companion object {
        const val HORIZONTAL_INSET_DP = 20
        const val TOP_INSET_DP = 30
        const val ICON_PLATE_DP = 50
        const val ICON_SIZE_DP = 26
        const val CONTENT_HEIGHT_DP = 124
    }
}
