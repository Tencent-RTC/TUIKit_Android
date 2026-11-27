package io.trtc.tuikit.chat.uikit.pages

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.trtc.tuikit.chat.uikit.components.config.AppBuilderConfig
import io.trtc.tuikit.chat.uikit.components.conversationlist.config.ChatConversationActionConfig
import io.trtc.tuikit.chat.uikit.components.conversationlist.config.ConversationActionConfigProtocol
import io.trtc.tuikit.chat.uikit.components.theme.LocalTheme
import io.trtc.tuikit.chat.uikit.components.conversationlist.ui.ConversationList
import io.trtc.tuikit.atomicxcore.api.conversation.ConversationInfo
import io.trtc.tuikit.chat.uikit.compose.R
import io.trtc.tuikit.chat.uikit.components.widgets.PageHeader
import kotlinx.coroutines.launch
import kotlin.math.max

@Composable
fun ConversationsPage(
    onConversationClick: (ConversationInfo) -> Unit = {},
    onSearchClick: () -> Unit = {},
    config: ConversationActionConfigProtocol = ChatConversationActionConfig(),
    headerTitle: String = stringResource(R.string.chat_uikit_chat),
    showSearchBar: Boolean = !AppBuilderConfig.hideSearch,
    editContent: @Composable () -> Unit = {},
) {
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()
    var headerHeightPx by rememberSaveable { mutableStateOf(0f) }
    var searchMaxHeightPx by rememberSaveable { mutableStateOf(0f) }
    var searchOffsetPx by rememberSaveable { mutableStateOf(0f) }
    val searchOffsetAnim = remember { Animatable(searchOffsetPx) }

    val nestedConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val dy = available.y
                if (dy < 0 && searchMaxHeightPx > 0f) {
                    val newOffset = (searchOffsetPx - dy).coerceIn(0f, searchMaxHeightPx)
                    val consumed = newOffset - searchOffsetPx
                    searchOffsetPx = newOffset
                    coroutineScope.launch { searchOffsetAnim.snapTo(newOffset) }
                    return Offset(0f, -consumed)
                }
                if (dy > 0 && searchOffsetPx > 0f) {
                    val newOffset = (searchOffsetPx - dy).coerceIn(0f, searchMaxHeightPx)
                    val consumed = searchOffsetPx - newOffset
                    searchOffsetPx = newOffset
                    coroutineScope.launch { searchOffsetAnim.snapTo(newOffset) }
                    return Offset(0f, consumed)
                }
                return Offset.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (searchMaxHeightPx <= 0f) return Velocity.Zero
                val current = searchOffsetAnim.value
                val target = if (current >= searchMaxHeightPx / 2f) searchMaxHeightPx else 0f
                if (current != target) {
                    searchOffsetPx = target
                    searchOffsetAnim.animateTo(target, animationSpec = tween(durationMillis = 180))
                }
                return Velocity.Zero
            }
        }
    }

    LaunchedEffect(showSearchBar) {
        if (!showSearchBar) {
            searchMaxHeightPx = 0f
            searchOffsetPx = 0f
            searchOffsetAnim.snapTo(0f)
        }
    }

    val displayedSearchOffsetPx = searchOffsetAnim.value
    val remainingSearchHeightPx = max(0f, searchMaxHeightPx - displayedSearchOffsetPx)
    val contentTopPadding = with(density) { (headerHeightPx + remainingSearchHeightPx).toDp() }
    val colors = LocalTheme.current.colors

    Box(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(nestedConnection)
            .background(colors.bgColorOperate)
    ) {
        Box(modifier = Modifier
            .fillMaxSize()
            .padding(top = contentTopPadding), contentAlignment = Alignment.Center) {
            ConversationList(
                config = config,
                onConversationClick = onConversationClick
            )
        }

        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { headerHeightPx = it.size.height.toFloat() }
            ) {
                PageHeader(headerTitle) {
                    editContent()
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clipToBounds()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset { IntOffset(0, -displayedSearchOffsetPx.toInt()) }
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(colors.bgColorOperate)
                            .onGloballyPositioned {
                                if (searchMaxHeightPx == 0f) searchMaxHeightPx =
                                    it.size.height.toFloat()
                            }
                    ) {
                        if (showSearchBar) {
                            SearchBar(onSearchClick)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SearchBar(onSearchClick: () -> Unit) {
    val colors = LocalTheme.current.colors
    Row(
        modifier = Modifier
            .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(shape = RoundedCornerShape(10.dp), color = colors.bgColorInput)
            .clickable { onSearchClick() }
            .fillMaxWidth()
            .height(36.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(R.drawable.search_ic_search),
            contentDescription = "Search",
            colorFilter = ColorFilter.tint(colors.textColorTertiary, BlendMode.SrcAtop),
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = stringResource(io.trtc.tuikit.chat.uikit.compose.R.string.search_search_text),
            color = colors.textColorTertiary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Normal,
        )
    }
}
