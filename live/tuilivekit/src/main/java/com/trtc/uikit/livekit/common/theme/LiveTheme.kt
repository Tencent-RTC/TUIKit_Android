package com.trtc.uikit.livekit.common.theme

import android.content.res.ColorStateList
import androidx.annotation.ColorInt

internal object LiveTheme {
    enum class TextRole(val sizeSp: Float, val semibold: Boolean) {
        SHEET_TITLE(15f, true), DIALOG_TITLE(16f, true), PAGE_TITLE(21f, true), SECTION_TITLE(12f, true), ITEM_TITLE(
            13f, false
        ),
        LIST_TITLE(15f, false), BODY(14f, false), PROMINENT(17f, true), CAPTION(12f, false), METADATA(
            10f, false
        ),
        CONTROL(11f, false), ACTION(13f, true),
    }

    enum class TextTone {
        PRIMARY, SECONDARY, TERTIARY, DISABLED,
    }

    object Color {
        @ColorInt
        val panelBackground = 0xFF1F2024.toInt()

        @ColorInt
        val sheetScrim = 0x8C000000.toInt()

        @ColorInt
        val actionPrimary = 0xFF4086FF.toInt()

        @ColorInt
        val actionPrimaryPressed = 0xFF5C9DFF.toInt()

        @ColorInt
        val actionPrimaryDisabled = 0xFF243047.toInt()

        @ColorInt
        val actionDanger = 0xFFE6594C.toInt()

        @ColorInt
        val actionDangerPressed = 0xFFE57A6E.toInt()

        @ColorInt
        val actionDangerDisabled = 0xFF422324.toInt()

        @ColorInt
        fun text(tone: TextTone): Int = when (tone) {
            TextTone.PRIMARY -> 0xFFFFFFFF.toInt()
            TextTone.SECONDARY -> 0xBFFFFFFF.toInt()
            TextTone.TERTIARY -> 0x7AFFFFFF.toInt()
            TextTone.DISABLED -> 0x61FFFFFF.toInt()
        }

        fun actionTextColors(destructive: Boolean): ColorStateList {
            val disabled = if (destructive) actionDangerDisabled else actionPrimaryDisabled
            val pressed = if (destructive) actionDangerPressed else actionPrimaryPressed
            val normal = if (destructive) actionDanger else actionPrimary
            return ColorStateList(
                arrayOf(
                    intArrayOf(-android.R.attr.state_enabled),
                    intArrayOf(android.R.attr.state_pressed),
                    intArrayOf(),
                ),
                intArrayOf(disabled, pressed, normal),
            )
        }
    }
}
