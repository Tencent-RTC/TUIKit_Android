package com.trtc.uikit.roomkit.aitranscription.config

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import androidx.core.content.ContextCompat
import com.trtc.uikit.roomkit.R

// MARK: - Data Model

data class AITranscriptionData(
    val segmentId: String = "",
    var speakerUserId: String = "",
    var speakerUserName: String = "",
    var sourceText: String = "",
    var translationText: String = "",
    var timestamp: Long = 0L,
    var isCompleted: Boolean = false,
)

// MARK: - Display Mode

enum class DisplayMode {
    SOURCE_ONLY,
    TRANSLATION_ONLY,
    DUAL,
    DUAL_REVERSED,
}

// MARK: - Text Style

data class TextStyle(
    var textColor: Int = Color.WHITE,
    var fontSize: Float = 16f,
    var typeface: Typeface = Typeface.DEFAULT,
    var shadowColor: Int = 0,
    var shadowDx: Float = 0f,
    var shadowDy: Float = 1f,
    var shadowRadius: Float = 2f,
) {
    companion object {
        fun defaultShadowColor(context: Context): Int =
            ContextCompat.getColor(context, R.color.roomkit_color_ai_shadow)
    }
}

// MARK: - Speaker Style

data class SpeakerStyle(
    var nameColor: Int = 0,
    var nameSize: Float = 16f,
    var nameTypeface: Typeface = Typeface.DEFAULT,
    var timestampColor: Int = 0,
    var timestampSize: Float = 14f,
    var timestampTypeface: Typeface = Typeface.DEFAULT,
    var nameTimestampSpacing: Int = 8,
    var bottomSpacing: Int = 2,
) {
    companion object {
        fun defaultNameColor(context: Context): Int =
            ContextCompat.getColor(context, R.color.roomkit_color_ai_text_white_b3)

        fun defaultTimestampColor(context: Context): Int =
            ContextCompat.getColor(context, R.color.roomkit_color_ai_timestamp_blue)
    }
}

// MARK: - Avatar Style

data class AvatarStyle(
    var sizeDp: Int = 32,
    var spacingDp: Int = 8,
    var placeholderResId: Int = 0,
    var topOffsetDp: Int = 0,
)
