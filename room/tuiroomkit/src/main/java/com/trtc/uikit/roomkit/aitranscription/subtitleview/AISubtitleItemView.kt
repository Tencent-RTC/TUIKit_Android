package com.trtc.uikit.roomkit.aitranscription.subtitleview

import android.content.Context
import android.util.AttributeSet
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import com.trtc.uikit.roomkit.R
import com.trtc.uikit.roomkit.base.utils.dpToPx
import com.trtc.uikit.roomkit.aitranscription.config.AISubtitleConfig
import com.trtc.uikit.roomkit.aitranscription.config.AITranscriptionData
import com.trtc.uikit.roomkit.aitranscription.config.DisplayMode

/**
 * Single-speaker subtitle item view with avatar, speaker label, and dual-line subtitles.
 */
class AISubtitleItemView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : ConstraintLayout(context, attrs, defStyleAttr) {

    private val avatarImageView: ImageView
    private val speakerLabel: TextView
    private val sourceLineContainer: FrameLayout
    private val translationLineContainer: FrameLayout

    private val sourceLineView: AISubtitleLineView = AISubtitleLineView(context)
    private val translationLineView: AISubtitleLineView = AISubtitleLineView(context)

    private var config: AISubtitleConfig = AISubtitleConfig.default(context)

    init {
        LayoutInflater.from(context).inflate(R.layout.roomkit_view_ai_subtitle_item, this, true)

        avatarImageView = findViewById(R.id.iv_avatar)
        speakerLabel = findViewById(R.id.tv_speaker_name)
        sourceLineContainer = findViewById(R.id.fl_source_line)
        translationLineContainer = findViewById(R.id.fl_translation_line)

        sourceLineContainer.addView(sourceLineView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT
        ))
        translationLineContainer.addView(translationLineView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT
        ))
    }

    // MARK: - Configuration

    fun applyConfig(config: AISubtitleConfig) {
        this.config = config

        setPadding(
            dpToPx(config.contentPaddingLeftDp),
            dpToPx(config.contentPaddingTopDp),
            dpToPx(config.contentPaddingRightDp),
            dpToPx(config.contentPaddingBottomDp),
        )

        // Avatar
        val avatarSizePx = dpToPx(config.avatarStyle.sizeDp)
        avatarImageView.visibility = if (config.showAvatar) View.VISIBLE else View.GONE
        (avatarImageView.layoutParams as? ConstraintLayout.LayoutParams)?.let { lp ->
            lp.width = avatarSizePx
            lp.height = avatarSizePx
            lp.marginEnd = dpToPx(config.avatarStyle.spacingDp)
            avatarImageView.layoutParams = lp
        }
        val radius = avatarSizePx / 2f
        avatarImageView.clipToOutline = true
        avatarImageView.outlineProvider = RoundOutlineProvider(radius)

        // Speaker label
        speakerLabel.setTextColor(config.speakerStyle.nameColor)
        speakerLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, config.speakerStyle.nameSize)
        speakerLabel.typeface = config.speakerStyle.nameTypeface
        (speakerLabel.layoutParams as? MarginLayoutParams)?.bottomMargin = dpToPx(config.speakerStyle.bottomSpacing)

        // Line views
        sourceLineView.configure(config.sourceStyle, config.streamAnimationIntervalMs)
        translationLineView.configure(config.translationStyle, config.streamAnimationIntervalMs)

        // Spacing
        val lineSpacingPx = dpToPx(config.lineSpacingDp)
        (sourceLineContainer.layoutParams as? MarginLayoutParams)?.bottomMargin = lineSpacingPx
        (translationLineContainer.layoutParams as? MarginLayoutParams)?.bottomMargin = lineSpacingPx

        updateDisplayMode(config)
    }

    fun bindData(data: AITranscriptionData, config: AISubtitleConfig) {
        applyConfig(config)

        if (config.showSpeaker && data.speakerUserName.isNotEmpty()) {
            speakerLabel.text = data.speakerUserName
            speakerLabel.visibility = View.VISIBLE
        } else {
            speakerLabel.visibility = View.GONE
        }

        updateAvatar("", config)

        if (config.displayMode != DisplayMode.TRANSLATION_ONLY) {
            sourceLineView.updateText(data.sourceText, animated = false)
        }
        if (config.displayMode != DisplayMode.SOURCE_ONLY) {
            translationLineView.updateText(data.translationText, animated = false)
        }
    }

    // MARK: - Max Lines

    fun updateMaxLines(maxLines: Int) {
        sourceLineView.updateMaxLines(maxLines)
        translationLineView.updateMaxLines(maxLines)
    }

    fun appendSourceText(text: String, animated: Boolean = true) {
        sourceLineView.appendText(text, animated)
    }

    fun appendTranslationText(text: String, animated: Boolean = true) {
        translationLineView.appendText(text, animated)
    }

    fun updateSourceText(text: String, animated: Boolean = true) {
        sourceLineView.updateText(text, animated)
    }

    fun updateTranslationText(text: String, animated: Boolean = true) {
        translationLineView.updateText(text, animated)
    }

    // MARK: - Private

    private fun updateDisplayMode(config: AISubtitleConfig) {
        when (config.displayMode) {
            DisplayMode.SOURCE_ONLY -> {
                sourceLineContainer.visibility = View.VISIBLE
                translationLineContainer.visibility = View.GONE
                // Reorder: source on top
                updateConstraintOrder(sourceFirst = true)
            }
            DisplayMode.TRANSLATION_ONLY -> {
                sourceLineContainer.visibility = View.GONE
                translationLineContainer.visibility = View.VISIBLE
                updateConstraintOrder(sourceFirst = true)
            }
            DisplayMode.DUAL -> {
                sourceLineContainer.visibility = View.VISIBLE
                translationLineContainer.visibility = View.VISIBLE
                updateConstraintOrder(sourceFirst = true)
            }
            DisplayMode.DUAL_REVERSED -> {
                sourceLineContainer.visibility = View.VISIBLE
                translationLineContainer.visibility = View.VISIBLE
                updateConstraintOrder(sourceFirst = false)
            }
        }
    }

    private fun updateConstraintOrder(sourceFirst: Boolean) {
        val sourceLp = sourceLineContainer.layoutParams as? ConstraintLayout.LayoutParams ?: return
        val translationLp = translationLineContainer.layoutParams as? ConstraintLayout.LayoutParams ?: return

        if (sourceFirst) {
            sourceLp.topToBottom = R.id.tv_speaker_name
            translationLp.topToBottom = R.id.fl_source_line
        } else {
            translationLp.topToBottom = R.id.tv_speaker_name
            sourceLp.topToBottom = R.id.fl_translation_line
        }

        sourceLineContainer.layoutParams = sourceLp
        translationLineContainer.layoutParams = translationLp
    }

    private fun updateAvatar(urlString: String, config: AISubtitleConfig) {
        if (!config.showAvatar) {
            avatarImageView.visibility = View.GONE
            return
        }
        if (config.avatarStyle.placeholderResId != 0) {
            avatarImageView.setImageResource(config.avatarStyle.placeholderResId)
            avatarImageView.visibility = View.VISIBLE
        } else {
            avatarImageView.visibility = if (urlString.isEmpty()) View.GONE else View.VISIBLE
        }
    }
}

/**
 * Simple round outline provider for clipping views to circles.
 */
private class RoundOutlineProvider(private val radius: Float) : android.view.ViewOutlineProvider() {
    override fun getOutline(view: View, outline: android.graphics.Outline) {
        outline.setRoundRect(0, 0, view.width, view.height, radius)
    }
}
