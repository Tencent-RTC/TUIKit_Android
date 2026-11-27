package io.trtc.tuikit.chat.uikit.components.messagelist.ui.widgets

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import io.trtc.tuikit.atomicxcore.api.login.UserProfile
import io.trtc.tuikit.atomicxcore.api.message.MessageReaction
import io.trtc.tuikit.chat.uikit.components.emojipicker.EmojiManager
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.LocalBubbleMaxWidth
import io.trtc.tuikit.chat.uikit.components.messagelist.viewmodel.MessageReactionActionPolicy
import io.trtc.tuikit.chat.uikit.components.theme.LocalTheme
import kotlin.math.ceil
import kotlin.math.roundToInt

private const val CHIP_BG_ALPHA = 16f / 255f
private const val CHIP_DIVIDER_ALPHA = 32f / 255f
private const val EMOJI_CLICK_THROTTLE_MS = 500L
private const val REACTION_HEADER_INLINE_ID = "reaction_header_spacer"
private const val OBJECT_REPLACEMENT_CHAR = "\uFFFC"
private const val LRM = "\u200E"
private const val RLM = "\u200F"
private const val WORD_JOINER = "\u2060"
private val ChipCornerRadius = 6.dp
private val ChipHorizontalPadding = 8.dp
private val ChipVerticalPadding = 4.dp
private val ChipInnerSpacing = 4.dp
private val ChipEmojiSize = 18.dp
private val ChipDividerWidth = 1.dp
private val ChipDividerHeight = 14.dp

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MessageReactionBar(
    modifier: Modifier = Modifier,
    reactionList: List<MessageReaction>,
    isLeftAligned: Boolean = true,
    onEmojiClick: ((MessageReaction) -> Unit)? = null,
    reactionUsersProvider: ((MessageReaction) -> List<UserProfile>)? = null
) {
    val context = LocalContext.current
    EmojiManager.initialize(context)
    val displayableReactions = remember(reactionList) {
        reactionList.filter { it.reactionID.isNotBlank() && it.totalUserCount > 0 }
    }
    if (displayableReactions.isEmpty()) {
        return
    }
    val bubbleMaxWidth = LocalBubbleMaxWidth.current
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val rowAlignment = if (isLeftAligned != isRtl) Alignment.Start else Alignment.End
    val lastClickTime = remember { longArrayOf(0L) }
    val throttledClick: ((MessageReaction) -> Unit)? = onEmojiClick?.let { callback ->
        { reaction ->
            val now = SystemClock.elapsedRealtime()
            if (now - lastClickTime[0] >= EMOJI_CLICK_THROTTLE_MS) {
                lastClickTime[0] = now
                callback(reaction)
            }
        }
    }
    FlowRow(
        modifier = modifier.widthIn(max = bubbleMaxWidth),
        horizontalArrangement = Arrangement.spacedBy(8.dp, rowAlignment),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        displayableReactions.forEach { reaction ->
            val users = reactionUsersProvider?.invoke(reaction) ?: reaction.partialUserList
            ReactionChip(
                reaction = reaction,
                users = users,
                onEmojiClick = throttledClick?.let { click -> { click(reaction) } }
            )
        }
    }
}

@Composable
private fun ReactionChip(
    reaction: MessageReaction,
    users: List<UserProfile>,
    onEmojiClick: (() -> Unit)?
) {
    val colors = LocalTheme.current.colors
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val isRtl = layoutDirection == LayoutDirection.Rtl
    val baseTextColor = colors.textColorPrimary
    val textColor = colors.textColorSecondary
    val chipBackground = baseTextColor.copy(alpha = CHIP_BG_ALPHA)
    val dividerColor = baseTextColor.copy(alpha = CHIP_DIVIDER_ALPHA)
    val emoji = EmojiManager.findEmojiByKey(reaction.reactionID)
    val label = MessageReactionActionPolicy.buildUsersLabel(reaction, users)
    val hasLabel = label.isNotBlank()
    val textStyle = LocalTextStyle.current.merge(
        TextStyle(
            fontSize = 12.sp,
            color = textColor,
            textAlign = TextAlign.Start,
            textDirection = if (isRtl) TextDirection.Rtl else TextDirection.Ltr,
            platformStyle = PlatformTextStyle(includeFontPadding = false)
        )
    )

    if (emoji != null && hasLabel) {
        val headerInsetWidth = ChipEmojiSize + ChipInnerSpacing + ChipDividerWidth + ChipInnerSpacing
        val headerPlaceholder = remember(density, headerInsetWidth) {
            Placeholder(
                width = with(density) { headerInsetWidth.toSp() },
                height = with(density) { ChipEmojiSize.toSp() },
                placeholderVerticalAlign = PlaceholderVerticalAlign.Center
            )
        }
        val annotatedLabel = remember(label, isRtl) {
            buildAnnotatedString {
                appendInlineContent(REACTION_HEADER_INLINE_ID, OBJECT_REPLACEMENT_CHAR)
                append(if (isRtl) RLM else LRM)
                append(WORD_JOINER)
                append(label)
            }
        }
        val inlineContent = remember(headerPlaceholder) {
            mapOf(
                REACTION_HEADER_INLINE_ID to InlineTextContent(headerPlaceholder) {
                    Spacer(Modifier.fillMaxSize())
                }
            )
        }
        val textMeasurer = rememberTextMeasurer()

        SubcomposeLayout(
            modifier = Modifier
                .clip(RoundedCornerShape(ChipCornerRadius))
                .background(chipBackground)
        ) { constraints ->
            val horizontalPaddingPx = ChipHorizontalPadding.roundToPx()
            val verticalPaddingPx = ChipVerticalPadding.roundToPx()
            val emojiSizePx = ChipEmojiSize.roundToPx()
            val innerSpacingPx = ChipInnerSpacing.roundToPx()
            val dividerWidthPx = ChipDividerWidth.roundToPx()
            val dividerHeightPx = ChipDividerHeight.roundToPx()
            val emojiBoxWidthPx = horizontalPaddingPx + emojiSizePx + innerSpacingPx

            val measureTextConstraints = if (constraints.hasBoundedWidth) {
                Constraints(maxWidth = (constraints.maxWidth - horizontalPaddingPx * 2).coerceAtLeast(0))
            } else {
                Constraints()
            }
            val textLayoutResult = textMeasurer.measure(
                text = annotatedLabel,
                style = textStyle,
                placeholders = listOf(AnnotatedString.Range(headerPlaceholder, 0, 1)),
                constraints = measureTextConstraints,
                layoutDirection = layoutDirection
            )
            val lineCount = textLayoutResult.lineCount.coerceAtLeast(1)
            val isMultiLine = lineCount > 1
            val firstLineHeightPx = (textLayoutResult.getLineBottom(0) - textLayoutResult.getLineTop(0))
                .roundToInt()
                .coerceAtLeast(emojiSizePx)

            val textSubcomposeConstraints = if (isMultiLine && constraints.hasBoundedWidth) {
                val maxLineWidthPx = (0 until lineCount).maxOf { line ->
                    ceil(textLayoutResult.getLineRight(line) - textLayoutResult.getLineLeft(line)).toInt()
                }
                val compactWidthPx = (maxLineWidthPx + horizontalPaddingPx * 2 + 1)
                    .coerceIn(constraints.minWidth, constraints.maxWidth)
                constraints.copy(minWidth = constraints.minWidth, maxWidth = compactWidthPx)
            } else {
                constraints
            }

            val textPlaceable = subcompose("label") {
                Text(
                    modifier = Modifier.padding(
                        horizontal = ChipHorizontalPadding,
                        vertical = ChipVerticalPadding
                    ),
                    text = annotatedLabel,
                    inlineContent = inlineContent,
                    style = textStyle
                )
            }[0].measure(textSubcomposeConstraints)

            val chipWidth = textPlaceable.width
            val chipHeight = textPlaceable.height
            val firstLineCenterY = verticalPaddingPx + firstLineHeightPx / 2
            val emojiClickableHeightPx = if (isMultiLine) {
                (verticalPaddingPx + firstLineHeightPx).coerceAtMost(chipHeight)
            } else {
                chipHeight
            }
            val emojiTopPaddingDp = (firstLineCenterY - emojiSizePx / 2).coerceAtLeast(0).toDp()
            val dividerTopY = (firstLineCenterY - dividerHeightPx / 2).coerceAtLeast(0)
            val emojiClipShape = RoundedCornerShape(
                topStart = ChipCornerRadius,
                bottomStart = if (isMultiLine) 0.dp else ChipCornerRadius
            )

            val emojiPlaceable = subcompose("emoji") {
                Box(
                    modifier = Modifier
                        .clip(emojiClipShape)
                        .then(
                            if (onEmojiClick != null) {
                                Modifier.clickable(role = Role.Button) { onEmojiClick() }
                            } else {
                                Modifier
                            }
                        )
                        .padding(
                            start = ChipHorizontalPadding,
                            end = ChipInnerSpacing,
                            top = emojiTopPaddingDp
                        ),
                    contentAlignment = Alignment.TopCenter
                ) {
                    AsyncImage(
                        model = emoji.emojiUrl,
                        contentDescription = emoji.emojiName,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.size(ChipEmojiSize)
                    )
                }
            }[0].measure(Constraints.fixed(emojiBoxWidthPx, emojiClickableHeightPx))

            val dividerPlaceable = subcompose("divider") {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(dividerColor, RoundedCornerShape(ChipDividerWidth))
                )
            }[0].measure(Constraints.fixed(dividerWidthPx, dividerHeightPx))

            layout(chipWidth, chipHeight) {
                textPlaceable.placeRelative(0, 0)
                dividerPlaceable.placeRelative(emojiBoxWidthPx, dividerTopY)
                emojiPlaceable.placeRelative(0, 0)
            }
        }
        return
    }

    if (emoji != null) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(ChipCornerRadius))
                .background(chipBackground)
                .then(
                    if (onEmojiClick != null) {
                        Modifier.clickable(role = Role.Button) { onEmojiClick() }
                    } else {
                        Modifier
                    }
                )
                .padding(horizontal = ChipHorizontalPadding, vertical = ChipVerticalPadding),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = emoji.emojiUrl,
                contentDescription = emoji.emojiName,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(ChipEmojiSize)
            )
        }
        return
    }

    if (hasLabel) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(ChipCornerRadius))
                .background(chipBackground)
                .padding(horizontal = ChipHorizontalPadding, vertical = ChipVerticalPadding)
        ) {
            Text(
                text = label,
                style = textStyle
            )
        }
    }
}
