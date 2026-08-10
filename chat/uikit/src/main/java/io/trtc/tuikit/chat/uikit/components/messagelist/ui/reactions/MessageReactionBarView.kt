package io.trtc.tuikit.chat.uikit.components.messagelist.ui.reactions
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.View.MeasureSpec
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import androidx.core.os.ConfigurationCompat
import com.bumptech.glide.Glide
import io.trtc.tuikit.chat.uikit.R
import io.trtc.tuikit.chat.uikit.components.emojipicker.EmojiManager
import io.trtc.tuikit.atomicx.theme.ThemeStore
import io.trtc.tuikit.atomicxcore.api.message.MessageInfo
import io.trtc.tuikit.atomicxcore.api.message.MessageReaction
import java.util.Locale
import kotlin.math.roundToInt

private const val REACTION_FADE_IN_DURATION_MS = 200L
private const val MAX_DISPLAY_REACTIONS = 5
private const val CHIP_HORIZONTAL_SPACING_DP = 8
private const val CHIP_VERTICAL_SPACING_DP = 6
private const val CHIP_HORIZONTAL_PADDING_DP = 8
private const val CHIP_VERTICAL_PADDING_DP = 4
private const val CHIP_CORNER_RADIUS_DP = 12
private const val CHIP_EMOJI_SIZE_DP = 16
private const val CHIP_DIVIDER_WIDTH_DP = 1
private const val CHIP_DIVIDER_HEIGHT_DP = 14
private const val CHIP_TEXT_MAX_WIDTH_DP = 120
private const val CHIP_MAX_WIDTH_DP = 180
private const val SELF_CHIP_BG_ALPHA = 24
private const val OTHER_CHIP_BG_ALPHA = 16
private const val SELF_CHIP_DIVIDER_ALPHA = 64
private const val OTHER_CHIP_DIVIDER_ALPHA = 32

class MessageReactionBarView(
    context: Context
) : LinearLayout(context) {

    private val density = context.resources.displayMetrics.density
    private var lastBoundMsgId: String? = null
    private var lastReactionSignature: String = ""

    init {
        orientation = VERTICAL
        gravity = Gravity.START
        layoutDirection = View.LAYOUT_DIRECTION_LTR
    }

    fun bind(
        message: MessageInfo,
        maxWidth: Int,
        rowHorizontalGravity: Int = Gravity.START,
        onClick: (MessageInfo) -> Unit
    ) {
        removeAllViews()
        if (message.reactionList.isEmpty()) {
            visibility = View.GONE
            setOnClickListener(null)
            animate().cancel()
            alpha = 1f
            lastBoundMsgId = message.msgID
            lastReactionSignature = ""
            return
        }
        val signature = buildReactionSignature(message)
        val shouldFadeIn = message.msgID != null &&
            message.msgID == lastBoundMsgId &&
            signature != lastReactionSignature
        lastBoundMsgId = message.msgID
        lastReactionSignature = signature
        visibility = View.VISIBLE
        gravity = rowHorizontalGravity
        setOnClickListener { onClick(message) }

        val availableWidth = maxWidth.coerceAtLeast(160.dp)
        val displayReactions = message.reactionList.take(MAX_DISPLAY_REACTIONS)
        buildReactionRows(
            reactions = displayReactions,
            isSelf = message.isSentBySelf,
            maxRowWidth = availableWidth,
            rowHorizontalGravity = rowHorizontalGravity
        )

        animate().cancel()
        if (shouldFadeIn) {
            alpha = 0f
            animate().alpha(1f).setDuration(REACTION_FADE_IN_DURATION_MS).start()
        } else {
            alpha = 1f
        }
    }

    private fun buildReactionSignature(message: MessageInfo): String {
        return message.reactionList.joinToString("|") { reaction ->
            "${reaction.reactionID}:${reaction.totalUserCount}"
        }
    }

    private fun buildReactionRows(
        reactions: List<MessageReaction>,
        isSelf: Boolean,
        maxRowWidth: Int,
        rowHorizontalGravity: Int
    ) {
        var currentRow: LinearLayout? = null
        var currentRowWidth = 0

        val chipMaxWidthPx = CHIP_MAX_WIDTH_DP.dp
        val measureCap = minOf(maxRowWidth, chipMaxWidthPx).coerceAtLeast(0)
        val chipWidthSpec = MeasureSpec.makeMeasureSpec(measureCap, MeasureSpec.AT_MOST)

        reactions.forEach { reaction ->
            val chipView = createReactionChip(reaction, isSelf)
            chipView.measure(chipWidthSpec, MeasureSpec.UNSPECIFIED)
            val chipWidth = chipView.measuredWidth
            val shouldWrap =
                currentRow != null &&
                    currentRow!!.childCount > 0 &&
                    currentRowWidth + CHIP_HORIZONTAL_SPACING_DP.dp + chipWidth > maxRowWidth

            if (currentRow == null || shouldWrap) {
                currentRow = createReactionRow()
                addView(
                    currentRow,
                    LayoutParams(
                        LayoutParams.WRAP_CONTENT,
                        LayoutParams.WRAP_CONTENT
                    ).apply {
                        if (childCount > 0) {
                            topMargin = CHIP_VERTICAL_SPACING_DP.dp
                        }
                        gravity = rowHorizontalGravity
                    }
                )
                currentRowWidth = 0
            }

            val hasLeadingChip = currentRow!!.childCount > 0
            currentRow!!.addView(
                chipView,
                LayoutParams(
                    LayoutParams.WRAP_CONTENT,
                    LayoutParams.WRAP_CONTENT
                ).apply {
                    if (hasLeadingChip) {
                        marginStart = CHIP_HORIZONTAL_SPACING_DP.dp
                    }
                }
            )
            currentRowWidth += chipWidth + if (hasLeadingChip) CHIP_HORIZONTAL_SPACING_DP.dp else 0
        }
    }

    private fun createReactionRow(): LinearLayout {
        return LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_LTR
        }
    }

    private fun createReactionChip(
        reaction: MessageReaction,
        isSelf: Boolean
    ): View {
        val colors = ThemeStore.shared(context).themeState.value.currentTheme.tokens.color
        val baseTextColor = if (isSelf) {
            colors.textColorAntiPrimary
        } else {
            colors.textColorPrimary
        }
        val chipBackgroundAlpha = if (isSelf) SELF_CHIP_BG_ALPHA else OTHER_CHIP_BG_ALPHA
        val dividerAlpha = if (isSelf) SELF_CHIP_DIVIDER_ALPHA else OTHER_CHIP_DIVIDER_ALPHA
        val textColor = if (isSelf) {
            colors.textColorAntiPrimary
        } else {
            colors.textColorSecondary
        }
        val label = buildReactionLabel(reaction)
        val chipMaxWidthPx = CHIP_MAX_WIDTH_DP.dp

        return object : LinearLayout(context) {
            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                val originalMode = MeasureSpec.getMode(widthMeasureSpec)
                val originalSize = MeasureSpec.getSize(widthMeasureSpec)
                val cappedSize = when (originalMode) {
                    MeasureSpec.EXACTLY -> originalSize
                    MeasureSpec.AT_MOST -> minOf(originalSize, chipMaxWidthPx)
                    else -> chipMaxWidthPx
                }
                val cappedMode = if (originalMode == MeasureSpec.EXACTLY) {
                    MeasureSpec.EXACTLY
                } else {
                    MeasureSpec.AT_MOST
                }
                val newWidthSpec = MeasureSpec.makeMeasureSpec(cappedSize, cappedMode)
                super.onMeasure(newWidthSpec, heightMeasureSpec)
            }
        }.apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = CHIP_CORNER_RADIUS_DP.dp.toFloat()
                setColor(ColorUtils.setAlphaComponent(baseTextColor, chipBackgroundAlpha))
            }
            setPadding(
                CHIP_HORIZONTAL_PADDING_DP.dp,
                CHIP_VERTICAL_PADDING_DP.dp,
                CHIP_HORIZONTAL_PADDING_DP.dp,
                CHIP_VERTICAL_PADDING_DP.dp
            )

            val emoji = EmojiManager.findEmojiByKey(reaction.reactionID)
            if (emoji != null) {
                addView(ImageView(context).apply {
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    layoutParams = LayoutParams(CHIP_EMOJI_SIZE_DP.dp, CHIP_EMOJI_SIZE_DP.dp)
                    contentDescription = emoji.emojiName
                    val drawable = EmojiManager.getCachedEmojiDrawable(reaction.reactionID)
                    if (drawable != null) {
                        setImageDrawable(drawable)
                    } else {
                        Glide.with(context)
                            .load(emoji.emojiUrl)
                            .into(this)
                    }
                })
            }

            if (!label.isNullOrBlank()) {
                if (emoji != null) {
                    addView(createSpacer(CHIP_HORIZONTAL_SPACING_DP.dp / 2))
                    addView(View(context).apply {
                        background = GradientDrawable().apply {
                            shape = GradientDrawable.RECTANGLE
                            cornerRadius = CHIP_DIVIDER_WIDTH_DP.dp.toFloat()
                            setColor(ColorUtils.setAlphaComponent(baseTextColor, dividerAlpha))
                        }
                    }, LayoutParams(CHIP_DIVIDER_WIDTH_DP.dp, CHIP_DIVIDER_HEIGHT_DP.dp))
                    addView(createSpacer(CHIP_HORIZONTAL_SPACING_DP.dp / 2))
                }
                addView(createTextView(label, textColor))
            }
        }
    }

    private fun buildReactionLabel(reaction: MessageReaction): String {
        val totalUserCount = reaction.totalUserCount.toInt()
        if (totalUserCount <= 0) {
            return ""
        }

        val firstUser = reaction.partialUserList.firstOrNull()
        val displayName = firstUser?.nickname?.takeIf { it.isNotBlank() }
            ?: firstUser?.userID?.takeIf { it.isNotBlank() }

        if (displayName.isNullOrBlank()) {
            return totalUserCount.toString()
        }

        return if (totalUserCount == 1) {
            displayName
        } else {
            buildMultiUserLabel(displayName, totalUserCount)
        }
    }

    private fun buildMultiUserLabel(
        displayName: String,
        totalUserCount: Int
    ): String {
        val trimmedDisplayName = displayName.trim()
        val isChineseLocale = currentLocale().language.equals(Locale.CHINESE.language, ignoreCase = true)
        val count = if (isChineseLocale) {
            totalUserCount
        } else {
            (totalUserCount - 1).coerceAtLeast(1)
        }
        val suffix = context.getString(R.string.message_list_user_count_suffix, count)
        return if (isChineseLocale) {
            trimmedDisplayName + suffix
        } else {
            "$trimmedDisplayName $suffix"
        }
    }

    private fun currentLocale(): Locale {
        return ConfigurationCompat.getLocales(resources.configuration)[0] ?: Locale.getDefault()
    }

    private fun createTextView(text: String, color: Int): TextView {
        return TextView(context).apply {
            layoutParams = LayoutParams(
                LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT
            )
            this.text = text
            textSize = 12f
            isSingleLine = true
            setMaxWidth(CHIP_TEXT_MAX_WIDTH_DP.dp)
            ellipsize = TextUtils.TruncateAt.END
            includeFontPadding = false
            setTextColor(color)
        }
    }

    private fun createSpacer(width: Int): View {
        return View(context).apply {
            layoutParams = LayoutParams(width, 1)
        }
    }

    private val Int.dp: Int
        get() = (this * density).roundToInt()
}
