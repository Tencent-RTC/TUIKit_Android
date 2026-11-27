package io.trtc.tuikit.chat.uikit.components.conversationlist.ui

import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.roundToIntRect
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.trtc.tuikit.chat.uikit.compose.R
import io.trtc.tuikit.chat.uikit.components.chatbot.ChatbotUIRegistrations
import io.trtc.tuikit.chat.uikit.components.widgets.AlertDialog
import io.trtc.tuikit.chat.uikit.components.widgets.Avatar
import io.trtc.tuikit.chat.uikit.components.widgets.AvatarBadge
import io.trtc.tuikit.chat.uikit.components.widgets.AvatarContent
import io.trtc.tuikit.chat.uikit.components.widgets.AvatarSize
import io.trtc.tuikit.chat.uikit.components.theme.LocalTheme
import io.trtc.tuikit.chat.uikit.components.conversationlist.config.ChatConversationActionConfig
import io.trtc.tuikit.chat.uikit.components.conversationlist.config.ConversationActionConfigProtocol
import io.trtc.tuikit.chat.uikit.components.conversationlist.model.ConversationActionIDs
import io.trtc.tuikit.chat.uikit.components.conversationlist.model.ConversationCustomAction
import io.trtc.tuikit.chat.uikit.components.conversationlist.model.ConversationCustomActionContext
import io.trtc.tuikit.chat.uikit.components.conversationlist.model.resolveConversationActionTitle
import io.trtc.tuikit.chat.uikit.components.conversationlist.utils.isUnread
import io.trtc.tuikit.chat.uikit.components.conversationlist.utils.needShowBadge
import io.trtc.tuikit.chat.uikit.components.conversationlist.viewmodel.ConversationListViewModel
import io.trtc.tuikit.chat.uikit.components.conversationlist.viewmodel.ConversationListViewModelFactory
import io.trtc.tuikit.chat.uikit.components.conversationlist.viewmodel.applyConversationActionCustomizer
import io.trtc.tuikit.atomicxcore.api.conversation.ConversationInfo
import io.trtc.tuikit.atomicxcore.api.conversation.ConversationListStore
import io.trtc.tuikit.atomicxcore.api.conversation.ReceiveMessageOption

val LocalViewModel = compositionLocalOf<ConversationListViewModel> { error("No ViewModel provided") }
val LocalConversation =
    compositionLocalOf<ConversationInfo> { error("No ConversationInfo provided") }
val LocalConversationConfig =
    compositionLocalOf<ConversationActionConfigProtocol> { error("No ConversationConfig provided") }
val LocalCustomActions = compositionLocalOf<List<ConversationCustomAction>> { emptyList() }

private val highlightOverlayColor = Color(0x14000000)

internal fun resolveConversationAvatarBadge(conversation: ConversationInfo): AvatarBadge {
    val isReceiveMessage = conversation.receiveOption == ReceiveMessageOption.RECEIVE
    return when {
        conversation.isUnread && conversation.needShowBadge -> {
            val count = maxOf(conversation.unreadCount, 1)
            val badgeText = if (count > 99) "99+" else count.toString()
            AvatarBadge.Text(badgeText)
        }
        !isReceiveMessage && conversation.isUnread -> AvatarBadge.Dot
        else -> AvatarBadge.None
    }
}

@Composable
fun ConversationList(
    modifier: Modifier = Modifier,
    listKey: String? = null,
    config: ConversationActionConfigProtocol = ChatConversationActionConfig(),
    conversationListViewModelFactory: ConversationListViewModelFactory = ConversationListViewModelFactory(
        conversationListStore = ConversationListStore.create(),
        conversationActionConfig = config
    ),
    customActions: List<ConversationCustomAction> = emptyList(),
    onConversationClick: (ConversationInfo) -> Unit = {},
) {
    val colors = LocalTheme.current.colors
    val context = LocalContext.current

    val generatedListKey = remember {
        "${ConversationListViewModel::class.java.name}:${System.identityHashCode(Any())}"
    }
    val viewModelKey = listKey ?: generatedListKey
    val conversationListViewModel = viewModel(
        ConversationListViewModel::class,
        key = viewModelKey,
        factory = conversationListViewModelFactory
    )

    LaunchedEffect(Unit) {
        ChatbotUIRegistrations.ensureRegistered()
    }

    SideEffect {
        conversationListViewModel.updateConfig(config)
    }

    val conversationList by conversationListViewModel.conversationList.collectAsState()
    val initialLoadFinished by conversationListViewModel.initialLoadFinished.collectAsState()
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = 0)

    var menuState by remember { mutableStateOf<ConversationMenuState?>(null) }
    var confirmActionID by remember { mutableStateOf<String?>(null) }
    var confirmAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var containerBoundsInWindow by remember { mutableStateOf(IntRect.Zero) }

    val scrollAnchorState = remember {
        object {
            var previousList: List<ConversationInfo>? = null
            var wasFirstItemFullyVisible = false
        }
    }

    LaunchedEffect(listState) {
        snapshotFlow {
            Triple(
                conversationList,
                listState.firstVisibleItemIndex,
                listState.firstVisibleItemScrollOffset
            )
        }.collect { (conversations, firstVisibleIndex, firstVisibleOffset) ->
            val currentlyFullyVisible = conversations.isNotEmpty() &&
                firstVisibleIndex == 0 &&
                firstVisibleOffset == 0
            val previousList = scrollAnchorState.previousList
            if (previousList == null) {
                scrollAnchorState.previousList = conversations
                scrollAnchorState.wasFirstItemFullyVisible = currentlyFullyVisible
                return@collect
            }
            if (previousList !== conversations) {
                if (scrollAnchorState.wasFirstItemFullyVisible && conversations.isNotEmpty()) {
                    listState.scrollToItem(0)
                }
                scrollAnchorState.previousList = conversations
                scrollAnchorState.wasFirstItemFullyVisible = conversations.isNotEmpty() &&
                    listState.firstVisibleItemIndex == 0 &&
                    listState.firstVisibleItemScrollOffset == 0
            } else {
                scrollAnchorState.wasFirstItemFullyVisible = currentlyFullyVisible
            }
        }
    }

    LaunchedEffect(conversationList) {
        menuState = null
    }

    LaunchedEffect(colors) {
        menuState = null
    }

    CompositionLocalProvider(
        LocalViewModel provides conversationListViewModel,
        LocalConversationConfig provides config,
        LocalCustomActions provides customActions
    ) {

        Box(
            modifier = modifier
                .background(color = colors.bgColorTopBar)
                .onGloballyPositioned { containerBoundsInWindow = it.boundsInWindow().roundToIntRect() }
        ) {

            LaunchedEffect(conversationList, listState) {

                snapshotFlow {
                    conversationList to listState.layoutInfo.visibleItemsInfo
                }.collect { (list, visibleItems) ->
                    val lastVisibleIndex = visibleItems.lastOrNull()?.index ?: -1

                    if (lastVisibleIndex >= 0 && lastVisibleIndex >= list.size - 3) {
                        conversationListViewModel.loadMoreConversation()
                    }
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize(),
                state = listState,
            ) {
                itemsIndexed(
                    conversationList,
                    key = { index, item -> item.conversationID }) { index, conversation ->
                    ConversationItem(
                        modifier = Modifier.animateItem(),
                        conversationInfo = conversation,
                        highlighted = menuState?.conversationID == conversation.conversationID,
                        onConversationClick = onConversationClick,
                        onConversationLongClick = { target, boundsInWindow ->
                            val actions = buildMenuActions(
                                conversation = target,
                                viewModel = conversationListViewModel,
                                config = config,
                                customActions = customActions,
                                context = context,
                                onConfirmationRequested = { actionID, onConfirm ->
                                    confirmActionID = actionID
                                    confirmAction = onConfirm
                                }
                            )
                            if (actions.isNotEmpty()) {
                                menuState = ConversationMenuState(
                                    conversationID = target.conversationID,
                                    anchorBoundsInWindow = boundsInWindow,
                                    items = actions
                                )
                            }
                        }
                    )
                }
            }

            if (initialLoadFinished && conversationList.isEmpty()) {
                ConversationEmptyView(modifier = Modifier.align(Alignment.Center))
            }

            menuState?.let { state ->
                if (containerBoundsInWindow != IntRect.Zero) {
                    ConversationPopupMenu(
                        menuState = state,
                        containerBoundsInWindow = containerBoundsInWindow,
                        onDismissed = { pendingAction ->
                            menuState = null
                            pendingAction?.invoke()
                        }
                    )
                }
            }

            val confirmMessageResID = when (confirmActionID) {
                ConversationActionIDs.DELETE -> R.string.conversation_list_delete_confirmation
                ConversationActionIDs.CLEAR_HISTORY -> R.string.conversation_list_clear_history_confirmation
                else -> null
            }
            if (confirmMessageResID != null) {
                AlertDialog(
                    isVisible = true,
                    message = stringResource(confirmMessageResID),
                    isConfirmDestructive = true,
                    onDismiss = {
                        confirmActionID = null
                        confirmAction = null
                    },
                    onCancel = {
                        confirmActionID = null
                        confirmAction = null
                    },
                    onConfirm = {
                        val action = confirmAction
                        confirmActionID = null
                        confirmAction = null
                        action?.invoke()
                    }
                )
            }
        }
    }
}

private fun buildMenuActions(
    conversation: ConversationInfo,
    viewModel: ConversationListViewModel,
    config: ConversationActionConfigProtocol,
    customActions: List<ConversationCustomAction>,
    context: Context,
    onConfirmationRequested: (actionID: String, onConfirm: () -> Unit) -> Unit,
): List<ConversationMenuItemData> {
    val defaults = viewModel.getDefaultActions(conversation)
    val customized = applyConversationActionCustomizer(
        actionContext = ConversationCustomActionContext(
            androidContext = context,
            conversation = conversation,
        ),
        defaults = defaults,
        customizer = config.actionCustomizer,
    )
    return (customized + customActions)
        .withDestructiveActionConfirmation(onConfirmationRequested)
        .map { action ->
            ConversationMenuItemData(
                title = resolveConversationActionTitle(
                    title = action.title,
                    titleResID = action.titleResID,
                    getString = { resID -> context.getString(resID) },
                ),
                dangerous = action.dangerous,
                action = { action.action(conversation) }
            )
        }
        .filter { it.title.isNotEmpty() }
}

@Composable
fun ConversationEmptyView(modifier: Modifier = Modifier) {
    val colors = LocalTheme.current.colors
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(R.drawable.uikit_empty_illustration),
            contentDescription = null,
            modifier = Modifier.width(88.dp),
            contentScale = ContentScale.FillWidth
        )
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = stringResource(R.string.uikit_empty_content),
            fontSize = 14.sp,
            color = colors.textColorTertiary
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ConversationItem(
    conversationInfo: ConversationInfo,
    onConversationClick: (ConversationInfo) -> Unit,
    onConversationLongClick: (ConversationInfo, IntRect) -> Unit,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
) {
    val colors = LocalTheme.current.colors
    var itemBoundsInWindow by remember { mutableStateOf(IntRect.Zero) }

    Box(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .onGloballyPositioned { itemBoundsInWindow = it.boundsInWindow().roundToIntRect() }
                .background(color = if (conversationInfo.isPinned) colors.bgColorInput else colors.bgColorTopBar)
                .combinedClickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = { onConversationClick(conversationInfo) },
                    onLongClick = { onConversationLongClick(conversationInfo, itemBoundsInWindow) }
                )
                .drawBehind {
                    val lineHeightPx = 0.5.dp.toPx().coerceAtLeast(1f)
                    val startInsetPx = 76.dp.toPx()
                    val isRtl = layoutDirection == LayoutDirection.Rtl
                    val startX = if (isRtl) 0f else startInsetPx
                    val endX = if (isRtl) size.width - startInsetPx else size.width
                    if (endX > startX) {
                        drawLine(
                            color = colors.strokeColorPrimary,
                            start = Offset(startX, size.height - lineHeightPx / 2f),
                            end = Offset(endX, size.height - lineHeightPx / 2f),
                            strokeWidth = lineHeightPx
                        )
                    }
                }
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CompositionLocalProvider(LocalConversation provides conversationInfo) {
                Avatar(
                    content = AvatarContent.Image(
                        url = conversationInfo.avatarURL,
                        fallbackName = conversationInfo.title ?: conversationInfo.conversationID
                    ),
                    size = AvatarSize.L,
                    badge = resolveConversationAvatarBadge(conversationInfo),
                    onClick = { onConversationClick(conversationInfo) },
                    onLongClick = { onConversationLongClick(conversationInfo, itemBoundsInWindow) }
                )
                Spacer(modifier = Modifier.width(12.dp))
                ConversationContent()
            }
        }

        if (highlighted) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(highlightOverlayColor)
            )
        }
    }
}
