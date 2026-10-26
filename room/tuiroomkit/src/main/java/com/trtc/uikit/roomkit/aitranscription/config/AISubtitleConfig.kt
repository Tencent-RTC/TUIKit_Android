package com.trtc.uikit.roomkit.aitranscription.config

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import androidx.core.content.ContextCompat
import com.trtc.uikit.roomkit.R

data class AISubtitleConfig(
    var displayMode: DisplayMode = DisplayMode.DUAL,
    var sourceStyle: TextStyle = TextStyle(
        textColor = Color.WHITE,
        fontSize = 16f,
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD),
    ),
    var translationStyle: TextStyle = TextStyle(
        fontSize = 14f,
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD),
    ),
    var speakerStyle: SpeakerStyle = SpeakerStyle(),
    var showSpeaker: Boolean = true,
    var avatarStyle: AvatarStyle = AvatarStyle(),
    var showAvatar: Boolean = true,
    var backgroundColor: Int = 0,
    var backgroundCornerRadiusDp: Float = 8f,
    var contentPaddingLeftDp: Int = 12,
    var contentPaddingTopDp: Int = 8,
    var contentPaddingRightDp: Int = 12,
    var contentPaddingBottomDp: Int = 8,
    var lineSpacingDp: Int = 4,
    /** Auto-fade duration in milliseconds. 0 disables auto-fade. */
    var fadeOutDurationMs: Long = 500L,
    /** Display hold time in milliseconds before fade-out begins. */
    var displayDurationMs: Long = 5000L,
    /** Per-character streaming animation interval in milliseconds. */
    var streamAnimationIntervalMs: Long = 30L,
    /** Maximum width ratio relative to the parent view. */
    var maxWidthRatio: Float = 0.9f,
    /** Maximum number of visible speaker subtitle cells. */
    var maxVisibleSpeakers: Int = 2,
    /** Spacing between speaker subtitle items in dp. */
    var speakerItemSpacingDp: Int = 8,
) {
    companion object {
        fun default(context: Context): AISubtitleConfig {
            return AISubtitleConfig(
                sourceStyle = TextStyle(
                    textColor = Color.WHITE,
                    fontSize = 16f,
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD),
                    shadowColor = ContextCompat.getColor(context, R.color.roomkit_color_ai_shadow),
                ),
                translationStyle = TextStyle(
                    textColor = ContextCompat.getColor(context, R.color.roomkit_color_ai_text_white_b3),
                    fontSize = 14f,
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD),
                    shadowColor = ContextCompat.getColor(context, R.color.roomkit_color_ai_shadow),
                ),
                speakerStyle = SpeakerStyle(
                    nameColor = ContextCompat.getColor(context, R.color.roomkit_color_ai_text_white_b3),
                    timestampColor = ContextCompat.getColor(context, R.color.roomkit_color_ai_timestamp_blue),
                ),
                backgroundColor = ContextCompat.getColor(context, R.color.roomkit_color_ai_subtitle_bg),
            )
        }
    }
}
