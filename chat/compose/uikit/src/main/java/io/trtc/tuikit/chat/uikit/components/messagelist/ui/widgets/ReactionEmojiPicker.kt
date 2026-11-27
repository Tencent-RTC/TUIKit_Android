package io.trtc.tuikit.chat.uikit.components.messagelist.ui.widgets

import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.trtc.tuikit.chat.uikit.compose.R
import io.trtc.tuikit.chat.uikit.components.theme.LocalTheme
import io.trtc.tuikit.chat.uikit.components.emojipicker.EmojiManager
import io.trtc.tuikit.chat.uikit.components.emojipicker.RecentEmojiManager
import io.trtc.tuikit.chat.uikit.components.emojipicker.model.Emoji

private const val MAX_QUICK_EMOJI_COUNT = 6
private const val MIN_QUICK_EMOJI_COUNT = 2

// Layout constants in dp
private const val ITEM_SIZE_DP = 28
private const val ITEM_SPACING_DP = 8
private const val HORIZONTAL_PADDING_DP = 12

@Composable
fun ReactionEmojiPicker(
    modifier: Modifier = Modifier,
    onEmojiClick: (Emoji) -> Unit,
    onExpandClick: () -> Unit
) {
    val colors = LocalTheme.current.colors
    val context = LocalContext.current
    val density = LocalDensity.current

    EmojiManager.initialize(context)
    RecentEmojiManager.initialize(context)

    val recentEmojiVersion by RecentEmojiManager.recentEmojiVersion.collectAsState()
    val recentEmojis = remember(recentEmojiVersion) {
        EmojiManager.reactionEmojiGroup
            ?.let { RecentEmojiManager.getRecentEmojiList(it.id) }
            .orEmpty()
    }

    @SuppressLint("UnusedBoxWithConstraintsScope")
    BoxWithConstraints(modifier = modifier) {
        val availableWidthPx = with(density) { maxWidth.toPx() }

        val visibleEmojiCount = remember(availableWidthPx) {
            calculateVisibleEmojiCount(availableWidthPx, density.density)
        }

        val quickEmojis = getQuickEmojis(recentEmojis, visibleEmojiCount)

        Row(
            modifier = Modifier
                .background(color = colors.dropdownColorDefault, shape = RoundedCornerShape(16.dp))
                .padding(horizontal = HORIZONTAL_PADDING_DP.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(ITEM_SPACING_DP.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            quickEmojis.forEach { emoji ->
                ReactionEmojiItem(
                    emoji = emoji,
                    onClick = { onEmojiClick(emoji) }
                )
            }

            Box(
                modifier = Modifier
                    .size(ITEM_SIZE_DP.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onExpandClick() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.message_list_reaction_expand_icon),
                    contentDescription = stringResource(R.string.message_list_reaction_expand),
                    modifier = Modifier.size(20.dp),
                    tint = colors.textColorSecondary
                )
            }
        }
    }
}

private fun calculateVisibleEmojiCount(availableWidthPx: Float, density: Float): Int {
    // Total width needed = padding * 2 + (itemCount + 1) * itemSize + itemCount * spacing
    // (itemCount + 1) because we have itemCount emojis + 1 more button
    // Solve for itemCount:
    // availableWidth >= padding * 2 + (itemCount + 1) * itemSize + itemCount * spacing
    // availableWidth - padding * 2 - itemSize >= itemCount * (itemSize + spacing)
    // itemCount <= (availableWidth - padding * 2 - itemSize) / (itemSize + spacing)

    val paddingPx = HORIZONTAL_PADDING_DP * density * 2
    val itemSizePx = ITEM_SIZE_DP * density
    val spacingPx = ITEM_SPACING_DP * density

    val availableForEmojis = availableWidthPx - paddingPx - itemSizePx // Reserve space for "more" button
    val perEmojiWidth = itemSizePx + spacingPx

    val maxEmojis = (availableForEmojis / perEmojiWidth).toInt()

    return maxEmojis.coerceIn(MIN_QUICK_EMOJI_COUNT, MAX_QUICK_EMOJI_COUNT)
}

@Composable
private fun ReactionEmojiItem(
    emoji: Emoji,
    onClick: () -> Unit
) {
    val colors = LocalTheme.current.colors

    Box(
        modifier = Modifier
            .size(ITEM_SIZE_DP.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
                color = colors.dropdownColorDefault
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = emoji.emojiUrl,
            contentDescription = emoji.emojiName,
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(24.dp)
        )
    }
}

private fun getQuickEmojis(recentEmojiKeys: List<String>, count: Int): List<Emoji> {
    val allEmojis = EmojiManager.reactionEmojiListForPicker()
    if (allEmojis.isEmpty()) return emptyList()

    val emojisByKey = allEmojis.associateBy { it.key }
    val result = mutableListOf<Emoji>()

    recentEmojiKeys.forEach { key ->
        if (result.size >= count) return@forEach
        val emoji = emojisByKey[key] ?: return@forEach
        if (result.none { it.key == key }) {
            result.add(emoji)
        }
    }

    allEmojis.forEach { emoji ->
        if (result.size >= count) return@forEach
        if (result.none { it.key == emoji.key }) {
            result.add(emoji)
        }
    }

    return result
}
