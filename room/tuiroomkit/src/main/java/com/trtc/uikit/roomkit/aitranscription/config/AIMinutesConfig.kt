package com.trtc.uikit.roomkit.aitranscription.config

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import androidx.core.content.ContextCompat
import com.trtc.uikit.roomkit.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class AIMinutesConfig(
    var displayMode: DisplayMode = DisplayMode.DUAL,
    var sourceStyle: TextStyle = TextStyle(
        fontSize = 15f,
    ),
    var translationStyle: TextStyle = TextStyle(
        fontSize = 12f,
    ),
    var speakerStyle: SpeakerStyle = SpeakerStyle(
        nameSize = 14f,
        nameTypeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD),
        timestampSize = 13f,
        nameTimestampSpacing = 6,
        bottomSpacing = 8,
    ),
    var showSpeaker: Boolean = true,
    var showTimestamp: Boolean = true,
    var backgroundColor: Int = Color.WHITE,
    var itemBackgroundColor: Int = 0,
    var itemCornerRadiusDp: Float = 10f,
    var itemContentPaddingLeftDp: Int = 16,
    var itemContentPaddingTopDp: Int = 12,
    var itemContentPaddingRightDp: Int = 16,
    var itemContentPaddingBottomDp: Int = 12,
    var itemSpacingDp: Int = 0,
    var lineSpacingDp: Int = 12,
    var listPaddingTopDp: Int = 0,
    var listPaddingBottomDp: Int = 12,
    var listPaddingLeftDp: Int = 0,
    var listPaddingRightDp: Int = 0,
    var timestampFormatter: ((Long) -> String)? = null,
) {
    companion object {
        fun default(context: Context): AIMinutesConfig {
            return AIMinutesConfig(
                sourceStyle = TextStyle(
                    textColor = ContextCompat.getColor(context, R.color.roomkit_color_ai_minutes_text_primary),
                    fontSize = 15f,
                ),
                translationStyle = TextStyle(
                    textColor = ContextCompat.getColor(context, R.color.roomkit_color_ai_minutes_text_secondary),
                    fontSize = 12f,
                ),
                speakerStyle = SpeakerStyle(
                    nameColor = ContextCompat.getColor(context, R.color.roomkit_color_ai_minutes_text_primary),
                    nameSize = 14f,
                    nameTypeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD),
                    timestampColor = ContextCompat.getColor(context, R.color.roomkit_color_ai_minutes_timestamp),
                    timestampSize = 13f,
                    nameTimestampSpacing = 6,
                    bottomSpacing = 8,
                ),
                itemBackgroundColor = ContextCompat.getColor(context, R.color.roomkit_color_ai_minutes_item_bg),
            )
        }

        private val defaultDateFormat = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
    }

    fun formatTimestamp(timestamp: Long): String {
        timestampFormatter?.let { return it(timestamp) }
        return defaultDateFormat.format(Date(timestamp))
    }
}
