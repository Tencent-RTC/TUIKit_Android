package io.trtc.tuikit.chat.uikit.components.messageinput.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.trtc.tuikit.chat.uikit.components.messageinput.data.MessageInputMenuAction
import io.trtc.tuikit.chat.uikit.components.theme.LocalTheme
import io.trtc.tuikit.chat.uikit.components.widgets.Badge
import io.trtc.tuikit.chat.uikit.components.widgets.BadgeType

private const val COLUMNS = 4
private const val ROWS_PER_PAGE = 2
private const val PAGE_SIZE = COLUMNS * ROWS_PER_PAGE

@Composable
fun MoreActionsPanel(
    actions: List<MessageInputMenuAction>,
    modifier: Modifier = Modifier,
    dismissedRedDotIds: Set<String> = emptySet(),
    onRedDotDismissed: (actionID: String) -> Unit = {}
) {
    val colors = LocalTheme.current.colors
    val pages = remember(actions) {
        if (actions.isEmpty()) listOf(emptyList()) else actions.chunked(PAGE_SIZE)
    }
    val pagerState = rememberPagerState(pageCount = { pages.size })

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(color = colors.bgColorOperate)
            .padding(16.dp)
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) { pageIndex ->
            MoreActionsGridPage(
                actions = pages.getOrNull(pageIndex).orEmpty(),
                dismissedRedDotIds = dismissedRedDotIds,
                onActionClick = { action ->
                    if (action.isEnabled && action.showRedDot) {
                        onRedDotDismissed(action.ID)
                    }
                    action.onClick()
                }
            )
        }

        if (pages.size > 1) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(pages.size) { index ->
                    val isActive = pagerState.currentPage == index
                    Box(
                        modifier = Modifier
                            .padding(start = if (index > 0) 4.dp else 0.dp)
                            .size(5.dp)
                            .alpha(if (isActive) 1f else PAGE_INDICATOR_INACTIVE_ALPHA)
                            .background(color = colors.textColorPrimary, shape = CircleShape)
                    )
                }
            }
        }
    }
}

@Composable
private fun MoreActionsGridPage(
    actions: List<MessageInputMenuAction>,
    dismissedRedDotIds: Set<String>,
    onActionClick: (MessageInputMenuAction) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(COLUMNS),
        modifier = Modifier.fillMaxSize(),
        userScrollEnabled = false
    ) {
        itemsIndexed(actions) { _, action ->
            MoreActionItem(
                action = action,
                showRedDot = action.showRedDot && action.ID !in dismissedRedDotIds,
                onClick = { onActionClick(action) }
            )
        }
    }
}

@Composable
private fun MoreActionItem(
    action: MessageInputMenuAction,
    showRedDot: Boolean,
    onClick: () -> Unit
) {
    val colors = LocalTheme.current.colors
    val iconTint = if (action.isEnabled) colors.textColorPrimary else colors.textColorDisable
    val titleColor = if (action.isEnabled) colors.textColorSecondary else colors.textColorDisable
    val containerAlpha = if (action.isEnabled) 1f else DISABLED_ACTION_CONTAINER_ALPHA
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clickable(
                onClick = onClick,
                role = Role.Button,
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .alpha(containerAlpha)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(12.dp))
                    .background(color = colors.bgColorTopBar),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(action.iconResID),
                    contentDescription = action.title,
                    modifier = Modifier.size(28.dp),
                    contentScale = ContentScale.Fit,
                    colorFilter = ColorFilter.tint(iconTint, BlendMode.SrcAtop)
                )
            }
            if (showRedDot) {
                Badge(
                    modifier = Modifier.align(Alignment.TopEnd),
                    type = BadgeType.Dot
                )
            }
        }
        Text(
            text = action.title,
            color = titleColor,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = true)),
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

private const val PAGE_INDICATOR_INACTIVE_ALPHA = 64f / 255f
private const val DISABLED_ACTION_CONTAINER_ALPHA = 0.6f
