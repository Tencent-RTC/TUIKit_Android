package io.trtc.tuikit.chat.uikit.components.messagelist.ui.widgets

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import io.trtc.tuikit.chat.uikit.compose.R
import io.trtc.tuikit.chat.uikit.components.theme.LocalTheme
import io.trtc.tuikit.chat.uikit.components.emojipicker.EmojiManager
import io.trtc.tuikit.chat.uikit.components.emojipicker.RecentEmojiManager
import io.trtc.tuikit.chat.uikit.components.emojipicker.model.Emoji
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private const val REACTION_GRID_SPAN_COUNT = 8
private const val PICKER_HEIGHT_SCREEN_RATIO = 0.6f
private const val PICKER_MIN_HEIGHT_DP = 400
private const val PICKER_MAX_HEIGHT_DP = 520
private const val DRAG_DISMISS_THRESHOLD_RATIO = 0.15f
private const val DRAG_ANIMATION_DURATION_MS = 180

@Composable
fun ReactionEmojiPickerSheet(
    onDismiss: () -> Unit,
    onEmojiClick: (Emoji) -> Unit
) {
    val colors = LocalTheme.current.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    EmojiManager.initialize(context)
    RecentEmojiManager.initialize(context)

    val recentEmojiVersion by RecentEmojiManager.recentEmojiVersion.collectAsState()
    val allEmojis = remember(recentEmojiVersion) { EmojiManager.reactionEmojiListForPicker() }
    val recentEmojis = remember(recentEmojiVersion) {
        val emojisByKey = allEmojis.associateBy { it.key }
        EmojiManager.reactionEmojiGroup
            ?.let { RecentEmojiManager.getRecentEmojiList(it.id) }
            .orEmpty()
            .mapNotNull { emojisByKey[it] }
    }

    val screenHeightDp = LocalConfiguration.current.screenHeightDp
    val sheetHeight = (screenHeightDp * PICKER_HEIGHT_SCREEN_RATIO)
        .coerceIn(PICKER_MIN_HEIGHT_DP.toFloat(), PICKER_MAX_HEIGHT_DP.toFloat())
        .dp

    val dragOffsetY = remember { Animatable(0f) }
    var contentHeightPx by remember { mutableIntStateOf(0) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onDismiss() },
            verticalArrangement = Arrangement.Bottom
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(sheetHeight)
                    .onSizeChanged { contentHeightPx = it.height }
                    .offset { IntOffset(0, dragOffsetY.value.roundToInt()) },
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                color = colors.bgColorDialog
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 24.dp)
                        .navigationBarsPadding()
                ) {
                    DragHandle(
                        onDismiss = onDismiss,
                        onDragBy = { deltaY ->
                            scope.launch {
                                dragOffsetY.snapTo((dragOffsetY.value + deltaY).coerceAtLeast(0f))
                            }
                        },
                        onDragEnd = {
                            scope.launch {
                                val dismissThreshold = contentHeightPx * DRAG_DISMISS_THRESHOLD_RATIO
                                if (dragOffsetY.value >= dismissThreshold) {
                                    dragOffsetY.animateTo(
                                        contentHeightPx.toFloat(),
                                        tween(DRAG_ANIMATION_DURATION_MS)
                                    )
                                    onDismiss()
                                } else {
                                    dragOffsetY.animateTo(0f, tween(DRAG_ANIMATION_DURATION_MS))
                                }
                            }
                        }
                    )

                    LazyVerticalGrid(
                        columns = GridCells.Fixed(REACTION_GRID_SPAN_COUNT),
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        if (recentEmojis.isNotEmpty()) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                ReactionSectionHeader(
                                    text = stringResource(R.string.message_list_reaction_recent_used)
                                )
                            }
                            items(recentEmojis.size) { index ->
                                ReactionEmojiCell(emoji = recentEmojis[index], onClick = onEmojiClick)
                            }
                        }
                        if (allEmojis.isNotEmpty()) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                ReactionSectionHeader(
                                    text = stringResource(R.string.message_list_reaction_all_emojis)
                                )
                            }
                            items(allEmojis.size) { index ->
                                ReactionEmojiCell(emoji = allEmojis[index], onClick = onEmojiClick)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DragHandle(
    onDismiss: () -> Unit,
    onDragBy: (Float) -> Unit,
    onDragEnd: () -> Unit
) {
    val colors = LocalTheme.current.colors
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(32.dp)
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        onDragBy(dragAmount)
                    },
                    onDragEnd = { onDragEnd() },
                    onDragCancel = { onDragEnd() }
                )
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(R.drawable.message_list_chevron_down),
            contentDescription = stringResource(R.string.message_list_reaction_collapse),
            tint = colors.strokeColorPrimary,
            modifier = Modifier.size(40.dp, 16.dp)
        )
    }
}

@Composable
private fun ReactionSectionHeader(text: String) {
    val colors = LocalTheme.current.colors
    Text(
        text = text,
        color = colors.textColorSecondary,
        fontSize = 12.sp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    )
}

@Composable
private fun ReactionEmojiCell(
    emoji: Emoji,
    onClick: (Emoji) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick(emoji) },
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = emoji.emojiUrl,
            contentDescription = emoji.emojiName,
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(32.dp)
        )
    }
}
