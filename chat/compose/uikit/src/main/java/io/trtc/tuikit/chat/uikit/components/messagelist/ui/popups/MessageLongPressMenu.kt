package io.trtc.tuikit.chat.uikit.components.messagelist.ui.popups

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import io.trtc.tuikit.chat.uikit.compose.R
import io.trtc.tuikit.chat.uikit.components.theme.LocalTheme
import io.trtc.tuikit.chat.uikit.components.emojipicker.EmojiManager
import io.trtc.tuikit.chat.uikit.components.emojipicker.RecentEmojiManager
import io.trtc.tuikit.chat.uikit.components.emojipicker.model.Emoji
import io.trtc.tuikit.chat.uikit.components.messagelist.config.MessageListConfigProtocol
import io.trtc.tuikit.chat.uikit.components.messagelist.model.MessageCustomActionContext
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.LocalCustomActions
import io.trtc.tuikit.chat.uikit.components.messagelist.viewmodel.MessageListViewModel
import io.trtc.tuikit.chat.uikit.components.messagelist.viewmodel.composeMessageLongPressActions
import io.trtc.tuikit.atomicxcore.api.message.MessageInfo
import io.trtc.tuikit.atomicxcore.api.message.MessageStatus

@Composable
fun MessageLongPressMenu(
    message: MessageInfo,
    viewModel: MessageListViewModel,
    config: MessageListConfigProtocol,
    anchorBounds: Rect?,
    onDismiss: () -> Unit,
    onDeleteRequested: (onConfirm: () -> Unit) -> Unit
) {
    val context = LocalContext.current
    val customActions = LocalCustomActions.current
    val defaults = remember(message, config) {
        viewModel.getActions(context, message, config)
    }
    val actions = remember(defaults, customActions, message) {
        composeMessageLongPressActions(
            actionContext = MessageCustomActionContext(
                androidContext = context,
                conversationID = viewModel.conversationID,
                message = message
            ),
            defaults = defaults,
            customizer = config.actionCustomizer,
            onDeleteRequested = { _, onConfirm -> onDeleteRequested(onConfirm) }
        ) + customActions
    }
    if (actions.isEmpty()) {
        LaunchedEffect(Unit) { onDismiss() }
        return
    }

    EmojiManager.initialize(context)
    val popupActions = remember(actions, config.isSupportReaction, message.status) {
        val items = actions.map { action ->
            LongPressPopupAction(
                title = action.title,
                iconResId = action.iconResID,
                onClick = { action.action(message) }
            )
        }.toMutableList()
        if (config.isSupportReaction &&
            message.status == MessageStatus.SEND_SUCCESS &&
            EmojiManager.reactionEmojiListForPicker().isNotEmpty()
        ) {
            items += LongPressPopupAction(
                title = context.getString(R.string.message_list_menu_reaction),
                iconResId = R.drawable.message_list_menu_reaction_icon,
                onClick = {},
                isReactionEntry = true
            )
        }
        items
    }
    var emojiExpanded by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    val hasReactionEntry = popupActions.any { it.isReactionEntry }
    val columnCount = if (popupActions.size > LongPressDimens.PAGE_SIZE || hasReactionEntry) {
        LongPressDimens.COLUMNS
    } else {
        popupActions.size.coerceIn(1, LongPressDimens.COLUMNS)
    }
    val contentWidth = (LongPressDimens.CELL_WIDTH_DP * columnCount + 8).dp
    val hairline = with(density) { 1f.toDp() }
    val pages = popupActions.chunked(LongPressDimens.PAGE_SIZE)
    val menuContentHeight = if (pages.size > 1) {
        (8 + LongPressDimens.CELL_HEIGHT_DP * LongPressDimens.MAX_ROWS +
            LongPressDimens.PAGE_INDICATOR_VERTICAL_PADDING_DP * 2 +
            LongPressDimens.PAGE_INDICATOR_DOT_SIZE_DP).dp + hairline
    } else {
        val rows = ((popupActions.size + columnCount - 1) / columnCount).coerceAtLeast(1)
        (8 + LongPressDimens.CELL_HEIGHT_DP * rows).dp + hairline * (rows - 1)
    }
    val emojiContentHeight = (12 + 14 + 8 +
        LongPressDimens.EMOJI_CELL_SIZE_DP * LongPressDimens.EMOJI_PANEL_ROWS +
        LongPressDimens.EMOJI_ROW_DIVIDER_VERTICAL_MARGIN_DP * 2 + 8).dp + hairline

    LongPressBubblePopup(
        anchorBounds = anchorBounds,
        contentWidth = contentWidth,
        menuContentHeight = menuContentHeight,
        emojiContentHeight = if (hasReactionEntry) emojiContentHeight else null,
        emojiExpanded = emojiExpanded,
        onDismiss = onDismiss
    ) { animatedDismiss ->
        if (emojiExpanded) {
            LongPressEmojiPanel(
                message = message,
                viewModel = viewModel,
                cardWidthDp = contentWidth,
                onDismiss = animatedDismiss,
                onCollapse = { emojiExpanded = false },
                onShowAllEmoji = {
                    animatedDismiss()
                    viewModel.showEmojiPicker(message)
                }
            )
        } else {
            LongPressActionGrid(
                actions = popupActions,
                columnCount = columnCount,
                onDismiss = animatedDismiss,
                onReactionEntry = { emojiExpanded = true }
            )
        }
    }
}

@Composable
private fun LongPressEmojiPanel(
    message: MessageInfo,
    viewModel: MessageListViewModel,
    cardWidthDp: Dp,
    onDismiss: () -> Unit,
    onCollapse: () -> Unit,
    onShowAllEmoji: () -> Unit
) {
    val colors = LocalTheme.current.colors
    val context = LocalContext.current
    EmojiManager.initialize(context)
    RecentEmojiManager.initialize(context)
    val emojis = remember(message.reactionList) { quickEmojis(message) }
    val lastIndex = LongPressDimens.EMOJI_PANEL_COLUMNS * LongPressDimens.EMOJI_PANEL_ROWS - 1
    Column(
        modifier = Modifier
            .width(cardWidthDp)
            .padding(top = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier
                .width(cardWidthDp)
                .height(14.dp)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.message_list_menu_reaction),
                fontSize = 10.sp,
                color = colors.textColorPrimary,
                modifier = Modifier.weight(1f)
            )
            EmojiPanelIconButton(
                onClick = onCollapse,
                sizeDp = 14,
                cornerRadiusDp = LongPressDimens.ACTION_ITEM_CORNER_RADIUS_DP,
                rippleAlpha = LongPressDimens.ACTION_ITEM_RIPPLE_ALPHA
            ) {
                Icon(
                    painter = painterResource(R.drawable.message_list_menu_reaction_collapse_icon),
                    contentDescription = stringResource(R.string.message_list_reaction_collapse),
                    modifier = Modifier.size(14.dp),
                    tint = colors.textColorPrimary
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Column(modifier = Modifier.padding(horizontal = 12.dp)) {
            repeat(LongPressDimens.EMOJI_PANEL_ROWS) { row ->
                Row {
                    repeat(LongPressDimens.EMOJI_PANEL_COLUMNS) { col ->
                        val index = row * LongPressDimens.EMOJI_PANEL_COLUMNS + col
                        when {
                            index == lastIndex -> {
                                EmojiPanelIconButton(
                                    onClick = onShowAllEmoji,
                                    sizeDp = LongPressDimens.EMOJI_CELL_SIZE_DP,
                                    cornerRadiusDp = LongPressDimens.EMOJI_CELL_SIZE_DP / 2,
                                    rippleAlpha = LongPressDimens.EMOJI_CELL_RIPPLE_ALPHA
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clip(CircleShape)
                                            .background(colors.dropdownColorHover),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            painter = painterResource(R.drawable.message_list_menu_more_icon),
                                            contentDescription = stringResource(R.string.message_list_reaction_expand),
                                            modifier = Modifier.size(14.dp),
                                            tint = colors.textColorPrimary
                                        )
                                    }
                                }
                            }
                            index < emojis.size -> {
                                val emoji = emojis[index]
                                EmojiPanelIconButton(
                                    onClick = {
                                        val isReacted = message.reactionList.any {
                                            it.reactionID == emoji.key && it.reactedByMyself
                                        }
                                        if (isReacted) {
                                            viewModel.removeMessageReaction(message, emoji.key)
                                        } else {
                                            viewModel.addMessageReaction(message, emoji.key)
                                            RecentEmojiManager.updateRecentEmoji(emoji.key)
                                        }
                                        onDismiss()
                                    },
                                    sizeDp = LongPressDimens.EMOJI_CELL_SIZE_DP,
                                    cornerRadiusDp = LongPressDimens.EMOJI_CELL_SIZE_DP / 2,
                                    rippleAlpha = LongPressDimens.EMOJI_CELL_RIPPLE_ALPHA
                                ) {
                                    AsyncImage(
                                        model = emoji.emojiUrl,
                                        contentDescription = emoji.emojiName,
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                            else -> {
                                Spacer(modifier = Modifier.size(LongPressDimens.EMOJI_CELL_SIZE_DP.dp))
                            }
                        }
                    }
                }
                if (row < LongPressDimens.EMOJI_PANEL_ROWS - 1) {
                    Box(
                        modifier = Modifier
                            .width((LongPressDimens.EMOJI_CELL_SIZE_DP * LongPressDimens.EMOJI_PANEL_COLUMNS).dp)
                            .padding(vertical = LongPressDimens.EMOJI_ROW_DIVIDER_VERTICAL_MARGIN_DP.dp)
                            .height(Dp.Hairline)
                            .background(colors.strokeColorPrimary.copy(alpha = 140 / 255f))
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EmojiPanelIconButton(
    onClick: () -> Unit,
    sizeDp: Int,
    cornerRadiusDp: Int,
    rippleAlpha: Int,
    content: @Composable () -> Unit
) {
    val colors = LocalTheme.current.colors
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    Box(
        modifier = Modifier
            .size(sizeDp.dp)
            .clip(RoundedCornerShape(cornerRadiusDp.dp))
            .background(if (isPressed) colors.dropdownColorHover else Color.Transparent)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(
                    color = colors.textColorPrimary.copy(alpha = rippleAlpha / 255f),
                    bounded = true
                )
            ) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

private fun quickEmojis(message: MessageInfo): List<Emoji> {
    val allEmojis = EmojiManager.reactionEmojiListForPicker()
    if (allEmojis.isEmpty()) {
        return emptyList()
    }
    val emojiMap = allEmojis.associateBy { it.key }
    val orderedKeys = mutableListOf<String>()
    orderedKeys += message.reactionList.filter { it.reactedByMyself }.map { it.reactionID }
    EmojiManager.reactionEmojiGroup?.let { group ->
        orderedKeys += RecentEmojiManager.getRecentEmojiList(group.id)
    }
    orderedKeys += allEmojis.map { it.key }
    val result = mutableListOf<Emoji>()
    val usedKeys = mutableSetOf<String>()
    for (key in orderedKeys) {
        val emoji = emojiMap[key] ?: continue
        if (!usedKeys.add(key)) continue
        result.add(emoji)
        if (result.size >= LongPressDimens.MAX_QUICK_EMOJI_COUNT) break
    }
    return result
}
