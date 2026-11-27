package io.trtc.tuikit.chat.uikit.components.emojipicker.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import coil3.compose.AsyncImage
import io.trtc.tuikit.chat.uikit.compose.R
import io.trtc.tuikit.chat.uikit.components.theme.Colors
import io.trtc.tuikit.chat.uikit.components.theme.LocalTheme
import io.trtc.tuikit.chat.uikit.components.emojipicker.EmojiManager
import io.trtc.tuikit.chat.uikit.components.emojipicker.RecentEmojiManager
import io.trtc.tuikit.chat.uikit.components.emojipicker.model.Emoji
import io.trtc.tuikit.chat.uikit.components.emojipicker.model.EmojiGroup
import kotlinx.coroutines.launch

private const val LITTLE_EMOJI_GRID_SPAN_COUNT = 8
private const val BIG_EMOJI_GRID_SPAN_COUNT = 5
private const val RECENT_EMOJI_DISPLAY_COUNT = 8

private val includeFontPaddingTextStyle = TextStyle(
    platformStyle = PlatformTextStyle(includeFontPadding = true)
)

@Composable
fun EmojiPicker(
    modifier: Modifier = Modifier,
    onEmojiClick: (EmojiGroup, Emoji) -> Unit,
    onDeleteClick: () -> Unit = {},
    onSendClick: () -> Unit = {},
) {
    val colors = LocalTheme.current.colors
    val context = LocalContext.current
    EmojiManager.initialize(context)
    RecentEmojiManager.initialize(context)

    val emojiGroups by EmojiManager.emojiGroupState.collectAsState()
    val pickerGroups = remember(emojiGroups) { EmojiManager.filterEmojiGroupsForPicker(emojiGroups) }
    val pagerState = rememberPagerState(pageCount = { pickerGroups.size.coerceAtLeast(1) })
    val scope = rememberCoroutineScope()
    var previewEmoji by remember { mutableStateOf<Emoji?>(null) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(color = colors.bgColorOperate)
    ) {
        if (pickerGroups.isEmpty()) {
            return@Column
        }

        EmojiTab(
            emojiGroupList = pickerGroups,
            selectedIndex = pagerState.currentPage.coerceIn(0, pickerGroups.lastIndex),
            onSelectedChanged = { index ->
                scope.launch { pagerState.animateScrollToPage(index) }
            }
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(color = colors.bgColorBubbleReciprocal)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            EmojiGrid(
                emojiGroupList = pickerGroups,
                pagerState = pagerState,
                onEmojiClick = onEmojiClick,
                onEmojiLongClick = { emoji -> previewEmoji = emoji },
                modifier = Modifier.fillMaxSize()
            )

            val currentGroup = pickerGroups.getOrNull(pagerState.currentPage)
            if (currentGroup?.isLittleEmoji == true) {
                EmojiBottomBar(
                    onDeleteClick = onDeleteClick,
                    onSendClick = onSendClick,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp)
                )
            }
        }
    }

    previewEmoji?.let { emoji ->
        EmojiPreviewPopup(
            emoji = emoji,
            onDismiss = { previewEmoji = null }
        )
    }
}

@Composable
fun EmojiTab(
    emojiGroupList: List<EmojiGroup>,
    selectedIndex: Int = 0,
    onSelectedChanged: (Int) -> Unit
) {
    val colors = LocalTheme.current.colors
    LazyRow(
        modifier = Modifier.padding(10.dp)
    ) {
        itemsIndexed(emojiGroupList) { index, emojiGroup ->
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .clickable { onSelectedChanged(index) }
                    .background(
                        color = if (selectedIndex == index) {
                            colors.bgColorBubbleReciprocal
                        } else {
                            Colors.Transparent
                        }
                    )
                    .padding(4.dp)
            ) {
                AsyncImage(
                    model = emojiGroup.emojiGroupIconUrl,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                    contentDescription = emojiGroup.name
                )
            }
        }
    }
}

@Composable
fun EmojiGrid(
    emojiGroupList: List<EmojiGroup>,
    pagerState: PagerState,
    onEmojiClick: (EmojiGroup, Emoji) -> Unit,
    onEmojiLongClick: (Emoji) -> Unit,
    modifier: Modifier = Modifier
) {
    HorizontalPager(state = pagerState, modifier = modifier) { page ->
        val currentGroup = emojiGroupList.getOrNull(page) ?: return@HorizontalPager
        EmojiGridPage(
            group = currentGroup,
            onEmojiClick = onEmojiClick,
            onEmojiLongClick = onEmojiLongClick
        )
    }
}

@Composable
private fun EmojiGridPage(
    group: EmojiGroup,
    onEmojiClick: (EmojiGroup, Emoji) -> Unit,
    onEmojiLongClick: (Emoji) -> Unit
) {
    val isLittle = group.isLittleEmoji
    val recentVersion by RecentEmojiManager.recentEmojiVersion.collectAsState()
    val recentEmojis = remember(recentVersion, group.id, group.emojis) {
        if (!isLittle) {
            emptyList()
        } else {
            val groupEmojisByKey = group.emojis.associateBy { it.key }
            RecentEmojiManager.getRecentEmojiList(group.id)
                .mapNotNull { groupEmojisByKey[it] }
                .take(RECENT_EMOJI_DISPLAY_COUNT)
        }
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(if (isLittle) LITTLE_EMOJI_GRID_SPAN_COUNT else BIG_EMOJI_GRID_SPAN_COUNT),
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp)
    ) {
        if (isLittle && recentEmojis.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                EmojiSectionHeader(text = stringResource(R.string.emoji_picker_recent))
            }
            itemsIndexed(recentEmojis) { _, emoji ->
                EmojiItem(
                    emoji = emoji,
                    onLongPress = { onEmojiLongClick(emoji) },
                    onClick = {
                        RecentEmojiManager.updateRecentEmoji(group.id, emoji.key)
                        onEmojiClick(group, emoji)
                    },
                    modifier = Modifier
                        .padding(4.dp)
                        .aspectRatio(1f)
                )
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        if (isLittle) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                EmojiSectionHeader(text = stringResource(R.string.emoji_picker_all))
            }
        }

        itemsIndexed(group.emojis) { _, emoji ->
            EmojiItem(
                emoji = emoji,
                onLongPress = { onEmojiLongClick(emoji) },
                onClick = {
                    if (isLittle) {
                        RecentEmojiManager.updateRecentEmoji(group.id, emoji.key)
                    }
                    onEmojiClick(group, emoji)
                },
                modifier = Modifier
                    .padding(4.dp)
                    .aspectRatio(1f)
            )
        }

        if (isLittle) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Spacer(modifier = Modifier.height(60.dp))
            }
        }
    }
}

@Composable
private fun EmojiSectionHeader(text: String) {
    val colors = LocalTheme.current.colors
    Text(
        text = text,
        color = colors.textColorSecondary,
        fontSize = 12.sp,
        style = includeFontPaddingTextStyle,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp, horizontal = 8.dp)
    )
}

@Composable
private fun EmojiBottomBar(
    onDeleteClick: () -> Unit,
    onSendClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalTheme.current.colors
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(R.drawable.emoji_picker_delete_icon),
            contentDescription = null,
            colorFilter = ColorFilter.tint(colors.textColorPrimary),
            modifier = Modifier
                .width(40.dp)
                .height(30.dp)
                .clip(RoundedCornerShape(15.dp))
                .background(color = colors.buttonColorSecondaryDefault)
                .clickable { onDeleteClick() }
                .padding(6.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Box(
            modifier = Modifier
                .width(50.dp)
                .height(30.dp)
                .clip(RoundedCornerShape(15.dp))
                .background(color = colors.buttonColorPrimaryDefault)
                .clickable { onSendClick() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.emoji_picker_send),
                fontSize = 14.sp,
                color = colors.textColorButton,
                style = includeFontPaddingTextStyle
            )
        }
    }
}

@Composable
private fun EmojiPreviewPopup(
    emoji: Emoji,
    onDismiss: () -> Unit
) {
    val colors = LocalTheme.current.colors
    Popup(alignment = Alignment.TopCenter, onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .background(color = colors.bgColorInput, shape = RoundedCornerShape(12.dp))
                .padding(6.dp)
        ) {
            AsyncImage(
                model = emoji.emojiUrl,
                contentDescription = emoji.emojiName,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
fun EmojiItem(
    emoji: Emoji,
    onLongPress: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongPress
            )
    ) {
        AsyncImage(
            model = emoji.emojiUrl,
            contentDescription = emoji.emojiName,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize()
        )
    }
}
