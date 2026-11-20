package com.trtc.uikit.livekit.voicelounge.prepare

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
import com.trtc.uikit.livekit.voicelounge.ovalDrawable
import com.trtc.uikit.livekit.voicelounge.setPressAlpha
import com.trtc.uikit.livekit.voicelounge.settings.theme.VoiceLoungeColor
import com.trtc.uikit.livekit.voicelounge.tint
import com.trtc.uikit.livekit.voicelounge.voiceLoungeText
import com.trtc.uikit.livekit.voicelounge.whiteAlpha

internal class VoiceLoungePrepareMorePanel(context: Context) : BottomSheetView(context) {
    var onLayout: (() -> Unit)? = null
    var onTranscriber: (() -> Unit)? = null
    var onAudio: (() -> Unit)? = null

    override val sheetHeight: Int get() = context.dp(SHEET_HEIGHT)

    init {
        val grid = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.TOP
        }
        listOf(
            Triple(
                R.string.livekit_voiceroom_prepare_more_layout, R.drawable.livekit_living_more
            ) { onLayout?.invoke() },
            Triple(R.string.livekit_voiceroom_prepare_more_caption, 0) { onTranscriber?.invoke() },
            Triple(R.string.livekit_voiceroom_prepare_more_audio, R.drawable.livekit_living_setting_audio) {
                onAudio?.invoke()
            },
        ).forEach { (title, icon, action) ->
            val item = toolButton(title, icon)
            item.setOnClickListener {
                dismiss()
                action()
            }
            grid.addView(item, linearParams(0, WRAP, weight = 1f))
        }
        contentView.addView(
            grid,
            frameParams(
                MATCH, WRAP, Gravity.TOP, start = context.dp(20), top = context.dp(GRID_TOP), end = context.dp(20)
            ),
        )
    }

    private fun toolButton(@StringRes title: Int, @DrawableRes icon: Int): View {
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        val iconBackground = FrameLayout(context).apply {
            background =
                ovalDrawable(VoiceLoungeColor.prepareIconBackground, context.dp(1), VoiceLoungeColor.prepareIconBorder)
        }
        val iconView: View = if (icon != 0) {
            ImageView(context).apply {
                setImageResource(icon)
                scaleType = ImageView.ScaleType.FIT_CENTER
                tint(ICON_STROKE)
            }
        } else {
            TranscriberIconView(context)
        }
        val iconSizeDp = when {
            icon == R.drawable.livekit_living_more -> 22f
            icon == R.drawable.livekit_living_setting_audio -> 18f
            else -> 23f
        }
        iconBackground.addView(iconView, frameParams(context.dp(iconSizeDp), context.dp(iconSizeDp), Gravity.CENTER))
        column.addView(iconBackground, linearParams(context.dp(ICON_SIZE), context.dp(ICON_SIZE)))
        val label = voiceLoungeText(
            context, LiveTheme.TextRole.CONTROL, LiveTheme.TextTone.SECONDARY,
            gravity = Gravity.CENTER, emphasized = true, text = context.getString(title),
        )
        label.maxLines = 1
        column.addView(label, linearParams(MATCH, WRAP, top = context.dp(8)))
        column.setPressAlpha()
        return column
    }

    private class TranscriberIconView(context: Context) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.8f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            color = ICON_STROKE
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
        const val ICON_SIZE = 56
        const val GRID_TOP = 28
        const val SHEET_HEIGHT = GRID_TOP + 56 + 8 + 18 + 28
        val ICON_STROKE = whiteAlpha(0.92f)
    }
}
