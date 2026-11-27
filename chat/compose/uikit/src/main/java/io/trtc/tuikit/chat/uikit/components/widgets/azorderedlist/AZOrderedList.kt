package io.trtc.tuikit.chat.uikit.components.widgets.azorderedlist

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.trtc.tuikit.chat.uikit.components.theme.LocalTheme
import io.trtc.tuikit.chat.uikit.components.widgets.Avatar
import io.trtc.tuikit.chat.uikit.components.widgets.azorderedlist.pinyinhelper.Pinyin
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

private data class AZGroup<T>(val letter: String, val items: List<AZOrderedListItem<T>>)

data class AZOrderedListItem<T>(
    val key: String,
    val label: String,
    val avatarUrl: Any? = null,
    val extraData: T
)

data class AZOrderedListConfig(
    val showIndexBar: Boolean = true
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun <T> AZOrderedList(
    modifier: Modifier = Modifier,
    dataSource: List<AZOrderedListItem<T>>,
    config: AZOrderedListConfig = AZOrderedListConfig(),
    header: (@Composable () -> Unit)? = null,
    footer: (@Composable () -> Unit)? = null,
    onItemClick: (AZOrderedListItem<T>) -> Unit,
    onItemLongClick: ((AZOrderedListItem<T>) -> Unit)? = null,
    onUserInteraction: (() -> Unit)? = null
) {
    val colors = LocalTheme.current.colors
    val density = LocalDensity.current
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    val groups = remember(dataSource) { groupAndSort(dataSource) }
    val indexLetters = remember(groups) { groups.map { it.letter } }
    val hasHeader = header != null
    val dividerHeight = with(density) { 0.5.dp.toPx().coerceAtLeast(1f).toDp() }

    var currentIndexBarLetter by remember { mutableStateOf<String?>(null) }

    val groupRanges = remember(groups, hasHeader) {
        var currentIndex = if (hasHeader) 1 else 0
        val ranges = mutableListOf<Pair<String, Int>>()
        groups.forEach { group ->
            ranges.add(group.letter to currentIndex)
            currentIndex += 1 + group.items.size
        }
        ranges.toMap()
    }

    LaunchedEffect(listState, groups, hasHeader) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .collectLatest { firstVisible ->
                var itemIndex = if (hasHeader) 1 else 0
                var letter: String? = null
                for (group in groups) {
                    val groupStart = itemIndex
                    val groupEnd = itemIndex + group.items.size
                    if (firstVisible in groupStart..groupEnd) {
                        letter = group.letter
                        break
                    }
                    itemIndex += 1 + group.items.size
                }
                if (letter != null) {
                    currentIndexBarLetter = letter
                }
            }
    }

    val userInteractionModifier = if (onUserInteraction != null) {
        Modifier.pointerInput(onUserInteraction) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    if (event.changes.any { it.changedToDownIgnoreConsumed() }) {
                        onUserInteraction()
                    }
                }
            }
        }
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .background(color = colors.bgColorOperate)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .then(userInteractionModifier),
            state = listState
        ) {
            if (header != null) {
                item(key = "az_list_header") {
                    header()
                }
            }

            groups.forEach { group ->
                stickyHeader(key = "az_header_${group.letter}") {
                    DefaultAZHeader(letter = group.letter)
                }

                itemsIndexed(
                    items = group.items,
                    key = { index, item -> "az_${group.letter}_${index}_${item.key}" }
                ) { index, item ->
                    val showDivider = index < group.items.lastIndex
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(60.dp)
                                .background(color = colors.bgColorOperate)
                                .combinedClickable(
                                    onClick = { onItemClick(item) },
                                    onLongClick = onItemLongClick?.let { listener ->
                                        { listener(item) }
                                    }
                                )
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Avatar(url = item.avatarUrl, name = item.label) {
                                onItemClick(item)
                            }
                            Spacer(modifier = Modifier.size(12.dp))
                            Text(
                                text = item.label,
                                color = colors.textColorPrimary,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.W400,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        if (showDivider) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                                    .padding(start = 68.dp)
                                    .height(dividerHeight)
                                    .background(color = colors.strokeColorPrimary)
                            )
                        }
                    }
                }
            }

            if (footer != null) {
                item(key = "az_list_footer") {
                    footer()
                }
            }
        }

        if (indexLetters.isNotEmpty() && config.showIndexBar) {
            IndexBar(
                letters = indexLetters,
                currentLetter = currentIndexBarLetter,
                onLetterSelected = { letter ->
                    groupRanges[letter]?.let { index ->
                        coroutineScope.launch {
                            listState.scrollToItem(index)
                        }
                    }
                },
                onLetterPressed = { _ -> },
                onLetterReleased = { },
                onDragStart = {
                    coroutineScope.launch {
                        listState.scroll { }
                    }
                },
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 8.dp, top = 16.dp, bottom = 16.dp)
            )
        }
    }
}

@Composable
private fun DefaultAZHeader(letter: String) {
    val colors = LocalTheme.current.colors
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(color = colors.bgColorInput)
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Text(
            text = letter,
            fontSize = 14.sp,
            fontWeight = FontWeight.W700,
            color = colors.textColorTertiary
        )
    }
}


private fun getFirstLetter(name: String): String {
    if (name.isEmpty()) return "#"
    val firstChar = name[0]
    if (firstChar.isLetter() && firstChar.code < 128) {
        return firstChar.uppercaseChar().toString()
    }
    if (firstChar.isDigit()) return "#"
    if (Pinyin.isChinese(firstChar)) {
        val pinyin = Pinyin.toPinyin(firstChar)
        return if (pinyin.isNotEmpty()) pinyin[0].uppercaseChar().toString() else "#"
    }
    return "#"
}

private fun <T> groupAndSort(items: List<AZOrderedListItem<T>>): List<AZGroup<T>> {
    val grouped = items.groupBy { item -> getFirstLetter(item.label) }
    val sortedGroups = grouped.map { (letter, groupItems) ->
        val sorted = groupItems.sortedBy { it.label.lowercase() }
        AZGroup(letter, sorted)
    }
    return sortedGroups.sortedWith { g1, g2 ->
        when {
            g1.letter == "#" && g2.letter != "#" -> 1
            g1.letter != "#" && g2.letter == "#" -> -1
            else -> g1.letter.compareTo(g2.letter)
        }
    }
}
