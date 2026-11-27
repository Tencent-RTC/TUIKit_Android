package io.trtc.tuikit.chat.uikit.components.messagelist.ui.reactions

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.SystemClock
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.ReplacementSpan
import android.view.Gravity
import android.view.View
import android.view.View.MeasureSpec
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import com.bumptech.glide.Glide
import io.trtc.tuikit.atomicx.theme.ThemeStore
import io.trtc.tuikit.atomicxcore.api.login.UserProfile
import io.trtc.tuikit.atomicxcore.api.message.MessageInfo
import io.trtc.tuikit.atomicxcore.api.message.MessageReaction
import io.trtc.tuikit.chat.uikit.components.common.RtlUtils
import io.trtc.tuikit.chat.uikit.components.emojipicker.EmojiManager
import io.trtc.tuikit.chat.uikit.components.emojipicker.model.Emoji
import kotlin.math.ceil
import kotlin.math.roundToInt

private const val REACTION_FADE_IN_DURATION_MS = 200L
private const val CHIP_HORIZONTAL_SPACING_DP = 8
private const val CHIP_VERTICAL_SPACING_DP = 6
private const val CHIP_HORIZONTAL_PADDING_DP = 8
private const val CHIP_VERTICAL_PADDING_DP = 4
private const val CHIP_CORNER_RADIUS_DP = 6
private const val CHIP_EMOJI_SIZE_DP = 18
private const val CHIP_INNER_SPACING_DP = 4
private const val CHIP_DIVIDER_WIDTH_DP = 1
private const val CHIP_DIVIDER_HEIGHT_DP = 14
private const val CHIP_BG_ALPHA = 16
private const val CHIP_DIVIDER_ALPHA = 32
private const val CHIP_RIPPLE_ALPHA = 31
private const val EMOJI_CLICK_THROTTLE_MS = 500L
private const val OBJECT_REPLACEMENT_CHAR = "\uFFFC"
private const val LRM = "\u200E"
private const val RLM = "\u200F"
private const val WORD_JOINER = "\u2060"

class MessageReactionBarView(
    context: Context
) : LinearLayout(context) {

    private val density = context.resources.displayMetrics.density
    private var lastBoundMsgId: String? = null
    private var lastReactionSignature: String = ""
    private val chipViews = mutableListOf<ReactionChipView>()
    private var builtMaxRowWidth: Int = 0
    private var rowGravity: Int = Gravity.LEFT
    private var lastEmojiClickTime: Long = 0L

    var maxBarWidth: Int = 0
        set(value) {
            if (field != value) {
                field = value
                requestLayout()
            }
        }

    init {
        orientation = VERTICAL
        gravity = Gravity.START
        layoutDirection = View.LAYOUT_DIRECTION_LTR
    }

    fun bind(
        message: MessageInfo,
        maxWidth: Int,
        isLeftAligned: Boolean = true,
        onEmojiClick: ((MessageReaction) -> Unit)? = null,
        reactionUsersProvider: ((MessageReaction) -> List<UserProfile>)? = null
    ) {
        rowGravity = if (isLeftAligned) Gravity.LEFT else Gravity.RIGHT
        gravity = rowGravity
        removeAllViews()
        chipViews.clear()
        builtMaxRowWidth = 0
        val reactions = MessageReactionPanelPolicy.displayable(message.reactionList)
        if (reactions.isEmpty()) {
            visibility = View.GONE
            setOnClickListener(null)
            animate().cancel()
            alpha = 1f
            lastBoundMsgId = message.msgID
            lastReactionSignature = ""
            return
        }
        val signature = buildReactionSignature(reactions)
        val shouldFadeIn = message.msgID.isNotEmpty() &&
            message.msgID == lastBoundMsgId &&
            signature != lastReactionSignature
        lastBoundMsgId = message.msgID
        lastReactionSignature = signature
        visibility = View.VISIBLE

        val isRtl = RtlUtils.isLocaleRtl(context)
        reactions.forEach { reaction ->
            val users = reactionUsersProvider?.invoke(reaction) ?: reaction.partialUserList
            chipViews.add(createReactionChip(reaction, users, isRtl, onEmojiClick))
        }
        val availableWidth = maxWidth.coerceAtLeast(160.dp)
        buildReactionRows(maxRowWidth = availableWidth)

        animate().cancel()
        if (shouldFadeIn) {
            alpha = 0f
            animate().alpha(1f).setDuration(REACTION_FADE_IN_DURATION_MS).start()
        } else {
            alpha = 1f
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val cappedWidthSpec = capWidthMeasureSpec(widthMeasureSpec)
        val widthMode = MeasureSpec.getMode(cappedWidthSpec)
        if (widthMode != MeasureSpec.UNSPECIFIED && chipViews.isNotEmpty()) {
            val availableWidth = (MeasureSpec.getSize(cappedWidthSpec) - paddingLeft - paddingRight)
                .coerceAtLeast(0)
            if (availableWidth > 0 && availableWidth != builtMaxRowWidth) {
                buildReactionRows(maxRowWidth = availableWidth)
            }
        }
        super.onMeasure(cappedWidthSpec, heightMeasureSpec)
    }

    private fun capWidthMeasureSpec(widthMeasureSpec: Int): Int {
        if (maxBarWidth <= 0) {
            return widthMeasureSpec
        }
        val mode = MeasureSpec.getMode(widthMeasureSpec)
        val size = MeasureSpec.getSize(widthMeasureSpec)
        return when (mode) {
            MeasureSpec.EXACTLY -> MeasureSpec.makeMeasureSpec(minOf(size, maxBarWidth), MeasureSpec.EXACTLY)
            MeasureSpec.AT_MOST -> MeasureSpec.makeMeasureSpec(minOf(size, maxBarWidth), MeasureSpec.AT_MOST)
            else -> MeasureSpec.makeMeasureSpec(maxBarWidth, MeasureSpec.AT_MOST)
        }
    }

    private fun buildReactionSignature(reactions: List<MessageReaction>): String {
        return reactions.joinToString("|") { reaction ->
            "${reaction.reactionID}:${reaction.totalUserCount}"
        }
    }

    private fun buildReactionRows(
        maxRowWidth: Int
    ) {
        builtMaxRowWidth = maxRowWidth
        for (i in 0 until childCount) {
            (getChildAt(i) as? ViewGroup)?.removeAllViews()
        }
        removeAllViews()

        val isRtl = RtlUtils.isLocaleRtl(context)
        var currentRow: LinearLayout? = null
        var currentRowWidth = 0

        val measureCap = maxRowWidth.coerceAtLeast(0)
        val chipWidthSpec = MeasureSpec.makeMeasureSpec(measureCap, MeasureSpec.AT_MOST)

        chipViews.forEach { chipView ->
            chipView.maxChipWidth = measureCap
            chipView.measure(chipWidthSpec, MeasureSpec.UNSPECIFIED)
            val chipWidth = chipView.measuredWidth
            val shouldWrap =
                currentRow != null &&
                    currentRow!!.childCount > 0 &&
                    currentRowWidth + CHIP_HORIZONTAL_SPACING_DP.dp + chipWidth > maxRowWidth

            if (currentRow == null || shouldWrap) {
                currentRow = createReactionRow(isRtl)
                addView(
                    currentRow,
                    LayoutParams(
                        LayoutParams.WRAP_CONTENT,
                        LayoutParams.WRAP_CONTENT
                    ).apply {
                        if (childCount > 0) {
                            topMargin = CHIP_VERTICAL_SPACING_DP.dp
                        }
                        gravity = rowGravity
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
                        if (isRtl) {
                            rightMargin = CHIP_HORIZONTAL_SPACING_DP.dp
                        } else {
                            leftMargin = CHIP_HORIZONTAL_SPACING_DP.dp
                        }
                    }
                }
            )
            currentRowWidth += chipWidth + if (hasLeadingChip) CHIP_HORIZONTAL_SPACING_DP.dp else 0
        }
    }

    private fun createReactionRow(isRtl: Boolean): LinearLayout {
        return LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = rowGravity or Gravity.TOP
            layoutDirection = if (isRtl) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
        }
    }

    private fun createReactionChip(
        reaction: MessageReaction,
        users: List<UserProfile>,
        isRtl: Boolean,
        onEmojiClick: ((MessageReaction) -> Unit)?
    ): ReactionChipView {
        val colors = ThemeStore.shared(context).themeState.value.currentTheme.tokens.color
        val baseTextColor = colors.textColorPrimary
        val textColor = colors.textColorSecondary
        val label = MessageReactionPanelPolicy.buildUsersLabel(reaction, users)
        val emoji = EmojiManager.findEmojiByKey(reaction.reactionID)

        return ReactionChipView(
            context = context,
            density = density,
            isRtl = isRtl,
            baseTextColor = baseTextColor,
            textColor = textColor,
            reaction = reaction,
            emoji = emoji,
            label = label,
            onEmojiClick = onEmojiClick?.let { callback ->
                {
                    val now = SystemClock.elapsedRealtime()
                    if (now - lastEmojiClickTime >= EMOJI_CLICK_THROTTLE_MS) {
                        lastEmojiClickTime = now
                        callback(reaction)
                    }
                }
            }
        )
    }

    private class ReactionChipView(
        context: Context,
        private val density: Float,
        private val isRtl: Boolean,
        private val baseTextColor: Int,
        textColor: Int,
        reaction: MessageReaction,
        emoji: Emoji?,
        label: String,
        private val onEmojiClick: (() -> Unit)?
    ) : ViewGroup(context) {

        private val hasLabel = label.isNotBlank()
        private val horizontalPaddingPx = CHIP_HORIZONTAL_PADDING_DP.dp
        private val verticalPaddingPx = CHIP_VERTICAL_PADDING_DP.dp
        private val emojiSizePx = CHIP_EMOJI_SIZE_DP.dp
        private val innerSpacingPx = CHIP_INNER_SPACING_DP.dp
        private val dividerWidthPx = CHIP_DIVIDER_WIDTH_DP.dp
        private val dividerHeightPx = CHIP_DIVIDER_HEIGHT_DP.dp
        private val headerInsetWidthPx = emojiSizePx + innerSpacingPx + dividerWidthPx + innerSpacingPx
        private val emojiBoxWidthPx = if (hasLabel) {
            horizontalPaddingPx + emojiSizePx + innerSpacingPx
        } else {
            horizontalPaddingPx + emojiSizePx + horizontalPaddingPx
        }

        private val labelView: CompactReactionTextView?
        private val emojiView: ImageView?
        private val dividerView: View?

        private var dividerTopPx: Int = 0
        private var lastRippleMultiLine: Boolean? = null

        var maxChipWidth: Int = 0

        init {
            layoutDirection = if (isRtl) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = CHIP_CORNER_RADIUS_DP.dp.toFloat()
                setColor(ColorUtils.setAlphaComponent(baseTextColor, CHIP_BG_ALPHA))
            }

            labelView = if (hasLabel) {
                CompactReactionTextView(context).apply {
                    layoutDirection = if (isRtl) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
                    textDirection = if (isRtl) View.TEXT_DIRECTION_RTL else View.TEXT_DIRECTION_LTR
                    textAlignment = View.TEXT_ALIGNMENT_VIEW_START
                    gravity = if (isRtl) Gravity.RIGHT else Gravity.LEFT
                    textSize = 12f
                    isSingleLine = false
                    maxLines = Int.MAX_VALUE
                    ellipsize = null
                    includeFontPadding = false
                    setTextColor(textColor)
                    setPadding(horizontalPaddingPx, verticalPaddingPx, horizontalPaddingPx, verticalPaddingPx)
                    text = if (emoji != null) {
                        SpannableStringBuilder().apply {
                            append(OBJECT_REPLACEMENT_CHAR)
                            setSpan(
                                InlineHeaderSpacerSpan(headerInsetWidthPx, emojiSizePx),
                                0,
                                1,
                                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                            )
                            append(if (isRtl) RLM else LRM)
                            append(WORD_JOINER)
                            append(label)
                        }
                    } else {
                        label
                    }
                }.also { addView(it) }
            } else {
                null
            }

            dividerView = if (emoji != null && hasLabel) {
                View(context).apply {
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        cornerRadius = dividerWidthPx.toFloat()
                        setColor(ColorUtils.setAlphaComponent(baseTextColor, CHIP_DIVIDER_ALPHA))
                    }
                }.also { addView(it) }
            } else {
                null
            }

            emojiView = if (emoji != null) {
                ImageView(context).apply {
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    contentDescription = emoji.emojiName
                    if (onEmojiClick != null) {
                        setOnClickListener { onEmojiClick.invoke() }
                    }
                    val drawable = EmojiManager.getCachedEmojiDrawable(reaction.reactionID)
                    if (drawable != null) {
                        setImageDrawable(drawable)
                    } else {
                        Glide.with(context)
                            .load(emoji.emojiUrl)
                            .into(this)
                    }
                }.also { addView(it) }
            } else {
                null
            }
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val originalMode = MeasureSpec.getMode(widthMeasureSpec)
            val originalSize = MeasureSpec.getSize(widthMeasureSpec)
            val cappedSize = when {
                maxChipWidth <= 0 -> originalSize
                originalMode == MeasureSpec.EXACTLY -> minOf(originalSize, maxChipWidth)
                originalMode == MeasureSpec.AT_MOST -> minOf(originalSize, maxChipWidth)
                else -> maxChipWidth
            }
            val effectiveWidthMode = if (originalMode == MeasureSpec.UNSPECIFIED && maxChipWidth > 0) {
                MeasureSpec.AT_MOST
            } else {
                originalMode
            }

            val textView = labelView
            val iconView = emojiView
            val separatorView = dividerView

            if (textView != null) {
                val textWidthSpec = MeasureSpec.makeMeasureSpec(cappedSize, effectiveWidthMode)
                textView.measure(textWidthSpec, MeasureSpec.UNSPECIFIED)
                val chipWidth = textView.measuredWidth
                val chipHeight = textView.measuredHeight

                if (iconView != null) {
                    val textLayout = textView.layout
                    val lineCount = textLayout?.lineCount ?: 1
                    val isMultiLine = lineCount > 1
                    val firstLineHeight = if (textLayout != null && lineCount > 0) {
                        (textLayout.getLineBottom(0) - textLayout.getLineTop(0)).coerceAtLeast(emojiSizePx)
                    } else {
                        emojiSizePx
                    }
                    val firstLineCenterY = verticalPaddingPx + firstLineHeight / 2
                    val emojiClickableHeight = if (isMultiLine) {
                        (verticalPaddingPx + firstLineHeight).coerceAtMost(chipHeight)
                    } else {
                        chipHeight
                    }
                    val emojiTopPadding = (firstLineCenterY - emojiSizePx / 2).coerceAtLeast(0)
                    val emojiBottomPadding = (emojiClickableHeight - emojiTopPadding - emojiSizePx).coerceAtLeast(0)
                    if (isRtl) {
                        iconView.setPadding(innerSpacingPx, emojiTopPadding, horizontalPaddingPx, emojiBottomPadding)
                    } else {
                        iconView.setPadding(horizontalPaddingPx, emojiTopPadding, innerSpacingPx, emojiBottomPadding)
                    }
                    updateEmojiRippleIfNeeded(iconView, isMultiLine)
                    iconView.measure(
                        MeasureSpec.makeMeasureSpec(emojiBoxWidthPx, MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(emojiClickableHeight, MeasureSpec.EXACTLY)
                    )
                    dividerTopPx = (firstLineCenterY - dividerHeightPx / 2).coerceAtLeast(0)
                    separatorView?.measure(
                        MeasureSpec.makeMeasureSpec(dividerWidthPx, MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(dividerHeightPx, MeasureSpec.EXACTLY)
                    )
                }

                setMeasuredDimension(chipWidth, chipHeight)
                return
            }

            if (iconView != null) {
                val chipHeight = emojiSizePx + verticalPaddingPx * 2
                iconView.setPadding(horizontalPaddingPx, verticalPaddingPx, horizontalPaddingPx, verticalPaddingPx)
                updateEmojiRippleIfNeeded(iconView, isMultiLine = false)
                iconView.measure(
                    MeasureSpec.makeMeasureSpec(emojiBoxWidthPx, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(chipHeight, MeasureSpec.EXACTLY)
                )
                setMeasuredDimension(emojiBoxWidthPx, chipHeight)
                return
            }

            setMeasuredDimension(0, 0)
        }

        override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
            val chipWidth = right - left
            labelView?.layout(0, 0, labelView.measuredWidth, labelView.measuredHeight)
            val iconView = emojiView ?: return
            if (isRtl) {
                val iconLeft = (chipWidth - iconView.measuredWidth).coerceAtLeast(0)
                iconView.layout(iconLeft, 0, chipWidth, iconView.measuredHeight)
                dividerView?.let { divider ->
                    val dividerRight = iconLeft
                    val dividerLeft = (dividerRight - divider.measuredWidth).coerceAtLeast(0)
                    divider.layout(dividerLeft, dividerTopPx, dividerRight, dividerTopPx + divider.measuredHeight)
                }
            } else {
                iconView.layout(0, 0, iconView.measuredWidth, iconView.measuredHeight)
                dividerView?.let { divider ->
                    val dividerLeft = iconView.measuredWidth
                    divider.layout(
                        dividerLeft,
                        dividerTopPx,
                        dividerLeft + divider.measuredWidth,
                        dividerTopPx + divider.measuredHeight
                    )
                }
            }
        }

        private fun updateEmojiRippleIfNeeded(iconView: ImageView, isMultiLine: Boolean) {
            if (onEmojiClick == null || lastRippleMultiLine == isMultiLine) {
                return
            }
            lastRippleMultiLine = isMultiLine
            iconView.background = createEmojiRippleBackground(
                baseColor = baseTextColor,
                hasLabel = hasLabel,
                isMultiLine = isMultiLine,
                isRtl = isRtl
            )
        }

        private fun createEmojiRippleBackground(
            baseColor: Int,
            hasLabel: Boolean,
            isMultiLine: Boolean,
            isRtl: Boolean
        ): RippleDrawable {
            val radius = CHIP_CORNER_RADIUS_DP.dp.toFloat()
            val radii = if (!hasLabel) {
                floatArrayOf(radius, radius, radius, radius, radius, radius, radius, radius)
            } else {
                val bottomStartRadius = if (isMultiLine) 0f else radius
                if (isRtl) {
                    floatArrayOf(0f, 0f, radius, radius, bottomStartRadius, bottomStartRadius, 0f, 0f)
                } else {
                    floatArrayOf(radius, radius, 0f, 0f, 0f, 0f, bottomStartRadius, bottomStartRadius)
                }
            }
            val mask = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadii = radii
                setColor(baseColor)
            }
            return RippleDrawable(
                ColorStateList.valueOf(ColorUtils.setAlphaComponent(baseColor, CHIP_RIPPLE_ALPHA)),
                null,
                mask
            )
        }

        private val Int.dp: Int
            get() = (this * density).roundToInt()
    }

    private class CompactReactionTextView(context: Context) : TextView(context) {
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            val textLayout = layout ?: return
            val originalLineCount = textLayout.lineCount
            if (originalLineCount <= 1 || MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.EXACTLY) {
                return
            }
            var maxLineWidth = 0f
            for (line in 0 until originalLineCount) {
                maxLineWidth = maxOf(maxLineWidth, textLayout.getLineWidth(line))
            }
            val compactWidth = ceil(maxLineWidth).toInt() + compoundPaddingLeft + compoundPaddingRight
            if (compactWidth in 1 until measuredWidth) {
                super.onMeasure(
                    MeasureSpec.makeMeasureSpec(compactWidth, MeasureSpec.EXACTLY),
                    heightMeasureSpec
                )
                if ((layout?.lineCount ?: originalLineCount) > originalLineCount) {
                    super.onMeasure(widthMeasureSpec, heightMeasureSpec)
                }
            }
        }
    }

    private class InlineHeaderSpacerSpan(
        private val widthPx: Int,
        private val minHeightPx: Int
    ) : ReplacementSpan() {
        override fun getSize(
            paint: Paint,
            text: CharSequence?,
            start: Int,
            end: Int,
            fm: Paint.FontMetricsInt?
        ): Int {
            if (fm != null) {
                val fontMetrics = paint.fontMetricsInt
                val textHeight = fontMetrics.descent - fontMetrics.ascent
                if (minHeightPx > textHeight) {
                    val extra = minHeightPx - textHeight
                    val topExtra = extra / 2
                    val bottomExtra = extra - topExtra
                    fm.ascent = fontMetrics.ascent - topExtra
                    fm.descent = fontMetrics.descent + bottomExtra
                    fm.top = minOf(fontMetrics.top, fm.ascent)
                    fm.bottom = maxOf(fontMetrics.bottom, fm.descent)
                } else {
                    fm.ascent = fontMetrics.ascent
                    fm.descent = fontMetrics.descent
                    fm.top = fontMetrics.top
                    fm.bottom = fontMetrics.bottom
                }
            }
            return widthPx
        }

        override fun draw(
            canvas: Canvas,
            text: CharSequence?,
            start: Int,
            end: Int,
            x: Float,
            top: Int,
            y: Int,
            bottom: Int,
            paint: Paint
        ) = Unit
    }

    private val Int.dp: Int
        get() = (this * density).roundToInt()
}
