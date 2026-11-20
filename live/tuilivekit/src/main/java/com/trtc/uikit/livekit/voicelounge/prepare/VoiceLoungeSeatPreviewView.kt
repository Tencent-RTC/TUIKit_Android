package com.trtc.uikit.livekit.voicelounge.prepare

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import com.trtc.uikit.livekit.R
import com.trtc.uikit.livekit.voicelounge.MATCH
import com.trtc.uikit.livekit.voicelounge.VoiceLoungeFont
import com.trtc.uikit.livekit.voicelounge.WRAP
import com.trtc.uikit.livekit.voicelounge.dp
import com.trtc.uikit.livekit.voicelounge.frameParams
import com.trtc.uikit.livekit.voicelounge.linearParams
import com.trtc.uikit.livekit.voicelounge.settings.VoiceLoungeSeatLayout
import com.trtc.uikit.livekit.voicelounge.voiceLoungeText
import com.trtc.uikit.livekit.voicelounge.whiteAlpha

internal class VoiceLoungeSeatPreviewView(context: Context) : FrameLayout(context) {
    private val gridStack = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
    }
    private val columnStack = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
    }

    init {
        isClickable = false
        isFocusable = false
        clipChildren = false
        addView(gridStack, frameParams(WRAP, WRAP, Gravity.CENTER))
        addView(columnStack, frameParams(WRAP, WRAP, Gravity.CENTER))
        applyLayout(VoiceLoungeSeatLayout.ONE_V8)
    }

    fun applyLayout(layout: VoiceLoungeSeatLayout) {
        when (layout) {
            VoiceLoungeSeatLayout.ONE_V8, VoiceLoungeSeatLayout.TWO_V8 -> {
                columnStack.removeAllViews()
                columnStack.visibility = GONE
                rebuildGrid(startIndex = if (layout == VoiceLoungeSeatLayout.TWO_V8) 2 else 1)
                gridStack.visibility = VISIBLE
            }

            VoiceLoungeSeatLayout.ONE_V8X -> {
                gridStack.removeAllViews()
                gridStack.visibility = GONE
                rebuildColumn(startIndex = 1)
                columnStack.visibility = VISIBLE
            }

            VoiceLoungeSeatLayout.DUO -> {
                gridStack.removeAllViews()
                columnStack.removeAllViews()
                gridStack.visibility = GONE
                columnStack.visibility = GONE
            }
        }
    }

    private fun rebuildGrid(startIndex: Int) {
        gridStack.removeAllViews()
        var seatIndex = startIndex
        for (row in 0 until ROWS) {
            val rowLayout = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            }
            for (col in 0 until COLUMNS) {
                rowLayout.addView(
                    SeatPreviewCell(context, seatIndex, isColumn = false),
                    linearParams(dp(CELL_WIDTH), WRAP),
                )
                seatIndex++
            }
            gridStack.addView(
                rowLayout,
                linearParams(WRAP, WRAP, top = if (row == 0) 0 else dp(ROW_SPACING)),
            )
        }
    }

    private fun rebuildColumn(startIndex: Int) {
        columnStack.removeAllViews()
        for (offset in 0 until COLUMN_COUNT) {
            columnStack.addView(
                SeatPreviewCell(context, startIndex + offset, isColumn = true),
                linearParams(dp(CELL_WIDTH), WRAP, top = if (offset == 0) 0 else dp(COLUMN_SPACING)),
            )
        }
    }

    private companion object {
        const val ROWS = 2
        const val COLUMNS = 4
        const val COLUMN_COUNT = 8
        const val ROW_SPACING = 14
        const val COLUMN_SPACING = 4
        const val CELL_WIDTH = 75
    }
}

private class SeatPreviewCell(context: Context, index: Int, isColumn: Boolean) : LinearLayout(context) {
    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        isClickable = false
        isFocusable = false

        val ring = FrameLayout(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(whiteAlpha(0.035f))
                setStroke(context.dp(1), whiteAlpha(0.075f))
            }
        }
        val mic = ImageView(context).apply {
            setImageResource(R.drawable.livekit_voiceprepare_seat_mic)
            scaleType = ImageView.ScaleType.FIT_CENTER
            imageTintList = ColorStateList.valueOf(whiteAlpha(0.4f))
        }
        ring.addView(mic, frameParams(context.dp(MIC_SIZE), context.dp(MIC_SIZE), Gravity.CENTER))
        addView(ring, linearParams(context.dp(RING_SIZE), context.dp(RING_SIZE)))

        val label = voiceLoungeText(
            context, com.trtc.uikit.livekit.common.theme.LiveTheme.TextRole.CAPTION,
            gravity = Gravity.CENTER,
            text = context.getString(R.string.livekit_voiceroom_prepare_seat_label, index),
        )
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, VoiceLoungeFont.size(10f))
        label.setTextColor(whiteAlpha(0.44f))
        label.maxLines = 1
        label.gravity = Gravity.CENTER
        addView(label, linearParams(MATCH, WRAP, top = context.dp(if (isColumn) COLUMN_LABEL_TOP else LABEL_TOP)))
    }

    private companion object {
        const val RING_SIZE = 48
        const val MIC_SIZE = 16
        const val LABEL_TOP = 6
        const val COLUMN_LABEL_TOP = 2
    }
}
