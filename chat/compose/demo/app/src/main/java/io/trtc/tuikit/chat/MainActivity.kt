package io.trtc.tuikit.chat

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.fragment.app.FragmentContainerView
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
//import com.tencent.qcloud.tuikit.tuicallkit.view.component.recents.RecentCallsFragment
import io.trtc.tuikit.chat.uikit.components.conversationlist.utils.ConversationUnreadClearer
import io.trtc.tuikit.chat.uikit.components.widgets.Badge
import io.trtc.tuikit.chat.uikit.components.config.AppBuilderConfig
import io.trtc.tuikit.chat.uikit.components.theme.LocalTheme
import io.trtc.tuikit.chat.uikit.components.common.SetActivitySystemBarAppearance
import io.trtc.tuikit.chat.uikit.components.contactlist.ui.addfriendandgroup.AddContactAndGroupBottomSheet
import io.trtc.tuikit.chat.uikit.components.contactlist.ui.addnewchat.AddNewChatBottomSheet
import io.trtc.tuikit.chat.uikit.components.contactlist.viewmodel.AddType
import io.trtc.tuikit.chat.uikit.components.contactlist.viewmodel.ChatType
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.contact.ContactStore
import io.trtc.tuikit.atomicxcore.api.conversation.ConversationListStore
import io.trtc.tuikit.atomicxcore.api.group.GetGroupInfoCompletionHandler
import io.trtc.tuikit.atomicxcore.api.group.GroupEvent
import io.trtc.tuikit.atomicxcore.api.group.GroupInfo
import io.trtc.tuikit.atomicxcore.api.group.GroupStore
import io.trtc.tuikit.atomicxcore.api.login.LoginStore
import io.trtc.tuikit.chat.chat.ChatActivity
import io.trtc.tuikit.chat.common.DemoTabState
import io.trtc.tuikit.chat.customerservice.CustomerServiceManager
import io.trtc.tuikit.chat.uikit.pages.ContactsPage
import io.trtc.tuikit.chat.uikit.pages.ConversationsPage
import io.trtc.tuikit.chat.search.SearchActivity
import io.trtc.tuikit.chat.uikit.components.widgets.PageHeader
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private const val BADGE_CLEAR_DRAG_THRESHOLD_DP = 48
private const val TAB_BAR_CONTENT_HEIGHT_DP = 56
private const val TAB_ICON_SIZE_DP = 24
private const val TAB_ITEM_PADDING_TOP_DP = 8
private const val TAB_ITEM_PADDING_BOTTOM_DP = 9
private const val TAB_ICON_TEXT_SPACING_DP = 1
private const val TAB_BADGE_VERTICAL_OFFSET_DP = 4

// Gradient handle positions of the selected tab icon, normalized to the icon canvas.
private const val TAB_ICON_GRADIENT_LIGHT_X = 0.66f
private const val TAB_ICON_GRADIENT_LIGHT_Y = -0.33f
private const val TAB_ICON_GRADIENT_DARK_X = -0.24f
private const val TAB_ICON_GRADIENT_DARK_Y = 0.6875f

data class TabItem(
    val title: Int,
    val iconRes: Int,
    val route: String,
    val iconCutoutRes: Int = 0
)

class MainActivity : BaseActivity() {
    var conversationListStore by mutableStateOf<ConversationListStore?>(null)
    var contactStore by mutableStateOf<ContactStore?>(null)
    var groupStore by mutableStateOf<GroupStore?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (isFinishing) {
            return
        }

        pendingOfflinePush?.let { push ->
            pendingOfflinePush = null
            val currentUserID = LoginStore.shared.loginState.loginUserInfo.value?.userID.orEmpty()
            if (push.targetUserID.isEmpty() || push.targetUserID == currentUserID) {
                ChatActivity.start(this, push.conversationID)
            }
        }

        conversationListStore = ConversationListStore.create()
        conversationListStore?.loadConversations()
        contactStore = ContactStore.shared.apply {
            loadFriendApplications(object : CompletionHandler {
                override fun onSuccess() {

                }

                override fun onFailure(code: Int, desc: String) {
                }
            })
        }
        groupStore = GroupStore.shared.apply {
            loadApplications(object : CompletionHandler {
                override fun onSuccess() {

                }

                override fun onFailure(code: Int, desc: String) {
                }
            })
        }
        val conversationListState = conversationListStore?.state
        val contactState = contactStore?.state
        val groupState = groupStore?.state
        val messagesTab = TabItem(
            title = R.string.compose_demo_messages,
            iconRes = R.drawable.demo_ic_tab_messages,
            iconCutoutRes = R.drawable.demo_ic_tab_messages_lines,
            route = "Messages"
        )
        val callsTab = TabItem(
            title = R.string.compose_demo_calls,
            iconRes = R.drawable.demo_ic_tab_calls,
            route = "Calls"
        )
        val contactsTab = TabItem(
            title = R.string.compose_demo_contacts,
            iconRes = R.drawable.demo_ic_tab_contacts,
            route = "Contacts"
        )
        val settingsTab = TabItem(
            title = R.string.compose_demo_me,
            iconRes = R.drawable.demo_ic_tab_me,
            route = "Settings"
        )

        lifecycleScope.launch {
            groupStore?.groupEventFlow?.collectLatest { event ->
                when (event) {
                    is GroupEvent.OnKickedFromGroup -> showGroupEventToast(
                        groupID = event.groupID,
                        messageResId = R.string.compose_demo_group_event_kicked
                    )
                    is GroupEvent.OnGroupDismissed -> showGroupEventToast(
                        groupID = event.groupID,
                        messageResId = R.string.compose_demo_group_event_dismissed
                    )
                    else -> Unit
                }
            }
        }

        setContent {
            val conversationUnreadCount by (conversationListState?.totalUnreadCount
                ?: flowOf(0L)).collectAsState(0L)
            val friendApplicationUnreadCount by (contactState?.friendApplicationUnreadCount
                ?: flowOf(0)).collectAsState(0)
            val groupApplicationUnreadCount by (groupState?.unreadApplicationCount
                ?: flowOf(0)).collectAsState(0)
            val navController = rememberNavController()
            val currentRoute = navController.currentBackStackEntryAsState()

            val showCallsTab = false
            val tabList = if (showCallsTab) {
                listOf(messagesTab, callsTab, contactsTab, settingsTab)
            } else {
                listOf(messagesTab, contactsTab, settingsTab)
            }
            LaunchedEffect(showCallsTab) {
                if (!showCallsTab && navController.currentDestination?.route == callsTab.route) {
                    navController.navigate(messagesTab.route) {
                        launchSingleTop = true
                        popUpTo(0) { saveState = true }
                        restoreState = true
                    }
                }
            }

            val colors = LocalTheme.current.colors

            SetActivitySystemBarAppearance()

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(color = colors.bgColorOperate)
                    .statusBarsPadding()
            ) {
                NavHost(
                    modifier = Modifier
                        .weight(1f)
                        .background(color = colors.bgColorOperate),
                    navController = navController,
                    startDestination = messagesTab.route
                ) {
                    animatedComposable(messagesTab.route) { MessagesScreen() }
                    if (showCallsTab) {
                        animatedComposable(callsTab.route) { CallsScreen() }
                    }
                    animatedComposable(contactsTab.route) { ContactsScreen() }
                    animatedComposable(settingsTab.route) { SettingsScreenContent() }
                }
                DemoBottomBar(
                    tabList = tabList,
                    currentRoute = currentRoute.value?.destination?.route,
                    messagesRoute = messagesTab.route,
                    contactsRoute = contactsTab.route,
                    conversationUnreadCount = conversationUnreadCount.toInt(),
                    contactsUnreadCount = friendApplicationUnreadCount + groupApplicationUnreadCount,
                    onTabSelected = { tab ->
                        navController.navigate(tab.route) {
                            launchSingleTop = true
                            popUpTo(0) { saveState = true }
                            restoreState = true
                        }
                    },
                    onClearUnread = { clearAllUnread() }
                )
            }
        }
    }

    private fun clearAllUnread() {
        val store = conversationListStore ?: return
        ConversationUnreadClearer.clearAllUnread(store, object : CompletionHandler {
            override fun onSuccess() {
                runOnUiThread {
                    Toast.makeText(
                        this@MainActivity,
                        R.string.compose_demo_clear_all_unread_success,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            override fun onFailure(code: Int, desc: String) {
            }
        })
    }

    private fun showGroupEventToast(groupID: String, messageResId: Int) {
        val fallbackName = groupID.ifEmpty { getString(R.string.compose_demo_group_event_unknown_group) }
        groupStore?.getGroupInfo(
            groupID,
            object : GetGroupInfoCompletionHandler {
                override fun onSuccess(groupInfo: GroupInfo) {
                    val groupName = groupInfo.groupName?.takeIf { it.isNotEmpty() } ?: fallbackName
                    Toast.makeText(this@MainActivity, getString(messageResId, groupName), Toast.LENGTH_LONG).show()
                }

                override fun onFailure(code: Int, desc: String) {
                    Toast.makeText(this@MainActivity, getString(messageResId, fallbackName), Toast.LENGTH_LONG).show()
                }
            }
        )
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}

@Composable
private fun DemoBottomBar(
    tabList: List<TabItem>,
    currentRoute: String?,
    messagesRoute: String,
    contactsRoute: String,
    conversationUnreadCount: Int,
    contactsUnreadCount: Int,
    onTabSelected: (TabItem) -> Unit,
    onClearUnread: () -> Unit,
) {
    val colors = LocalTheme.current.colors
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    var draggingMessageBadge by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.bgColorBottomBar)
            .navigationBarsPadding()
            .height(TAB_BAR_CONTENT_HEIGHT_DP.dp)
    ) {
        tabList.forEach { tab ->
            key(tab.route) {
                val unreadCount = when (tab.route) {
                    messagesRoute -> conversationUnreadCount
                    contactsRoute -> contactsUnreadCount
                    else -> 0
                }
                DemoBottomTab(
                    tab = tab,
                    selected = currentRoute == tab.route,
                    unreadCount = unreadCount,
                    isRtl = isRtl,
                    draggableBadge = tab.route == messagesRoute,
                    dragging = tab.route == messagesRoute && draggingMessageBadge,
                    onDraggingChange = { dragging ->
                        if (tab.route == messagesRoute) {
                            draggingMessageBadge = dragging
                        }
                    },
                    onClearUnread = onClearUnread,
                    onClick = { onTabSelected(tab) }
                )
            }
        }
    }
}

@Composable
private fun RowScope.DemoBottomTab(
    tab: TabItem,
    selected: Boolean,
    unreadCount: Int,
    isRtl: Boolean,
    draggableBadge: Boolean,
    dragging: Boolean,
    onDraggingChange: (Boolean) -> Unit,
    onClearUnread: () -> Unit,
    onClick: () -> Unit,
) {
    val colors = LocalTheme.current.colors
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .zIndex(if (dragging) 1f else 0f)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(
                top = TAB_ITEM_PADDING_TOP_DP.dp,
                bottom = TAB_ITEM_PADDING_BOTTOM_DP.dp
            ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        TabIconWithBadge(
            iconRes = tab.iconRes,
            iconCutoutRes = tab.iconCutoutRes,
            selected = selected,
            isRtl = isRtl,
            unreadCount = unreadCount,
            draggableBadge = draggableBadge,
            onDraggingChange = onDraggingChange,
            onClearUnread = onClearUnread
        )
        BasicText(
            text = stringResource(tab.title),
            modifier = Modifier.padding(top = TAB_ICON_TEXT_SPACING_DP.dp),
            style = TextStyle(
                color = if (selected) colors.textColorLink else colors.textColorTertiary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Normal,
                letterSpacing = 0.sp,
                platformStyle = PlatformTextStyle(includeFontPadding = false)
            ),
            maxLines = 1
        )
    }
}

@Composable
private fun TabIconWithBadge(
    iconRes: Int,
    iconCutoutRes: Int,
    selected: Boolean,
    isRtl: Boolean,
    unreadCount: Int,
    draggableBadge: Boolean,
    onDraggingChange: (Boolean) -> Unit,
    onClearUnread: () -> Unit,
) {
    val density = LocalDensity.current
    val iconSizePx = with(density) { TAB_ICON_SIZE_DP.dp.roundToPx() }
    val badgeVerticalOffsetPx = with(density) { TAB_BADGE_VERTICAL_OFFSET_DP.dp.roundToPx() }
    val showBadge = unreadCount > 0

    SubcomposeLayout(modifier = Modifier.fillMaxWidth()) { constraints ->
        val iconPlaceable = subcompose("icon") {
            GradientTabIcon(
                iconRes = iconRes,
                selected = selected,
                cutoutRes = iconCutoutRes,
                modifier = Modifier.size(TAB_ICON_SIZE_DP.dp)
            )
        }.first().measure(Constraints.fixed(iconSizePx, iconSizePx))

        val badgePlaceable = if (showBadge) {
            subcompose("badge") {
                if (draggableBadge) {
                    DraggableUnreadBadge(
                        unreadCount = unreadCount,
                        onClearUnread = onClearUnread,
                        onDraggingChange = onDraggingChange
                    )
                } else {
                    Badge(text = if (unreadCount > 99) "99+" else unreadCount.toString())
                }
            }.first().measure(Constraints())
        } else {
            null
        }

        val width = if (constraints.hasBoundedWidth) {
            constraints.maxWidth
        } else {
            iconPlaceable.width
        }
        val height = iconPlaceable.height
        val iconLeft = (width - iconPlaceable.width) / 2
        layout(width, height) {
            iconPlaceable.place(iconLeft, 0)
            if (badgePlaceable != null) {
                val offset = tabBadgeTopEndOffset(
                    anchorLeft = iconLeft,
                    anchorTop = 0,
                    anchorWidth = iconPlaceable.width,
                    badgeWidth = badgePlaceable.width,
                    badgeHeight = badgePlaceable.height,
                    isRtl = isRtl,
                    verticalOffsetPx = badgeVerticalOffsetPx
                )
                badgePlaceable.place(offset.x, offset.y, zIndex = 1f)
            }
        }
    }
}

private fun tabBadgeTopEndOffset(
    anchorLeft: Int,
    anchorTop: Int,
    anchorWidth: Int,
    badgeWidth: Int,
    badgeHeight: Int,
    isRtl: Boolean,
    verticalOffsetPx: Int,
): IntOffset {
    val badgeCenterX = if (isRtl) {
        anchorLeft
    } else {
        anchorLeft + anchorWidth
    }
    val badgeCenterY = anchorTop + verticalOffsetPx
    return IntOffset(
        x = badgeCenterX - badgeWidth / 2,
        y = badgeCenterY - badgeHeight / 2
    )
}

@Composable
private fun GradientTabIcon(
    iconRes: Int,
    selected: Boolean,
    modifier: Modifier = Modifier,
    cutoutRes: Int = 0,
) {
    val colors = LocalTheme.current.colors
    val painter = painterResource(iconRes)
    val cutoutPainter = if (cutoutRes != 0) {
        painterResource(cutoutRes)
    } else {
        null
    }
    Box(
        modifier = modifier
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                with(painter) { draw(size) }
                if (selected) {
                    drawRect(
                        brush = Brush.linearGradient(
                            colors = listOf(colors.bgColorBubbleOwn, colors.textColorLink),
                            start = Offset(
                                size.width * TAB_ICON_GRADIENT_LIGHT_X,
                                size.height * TAB_ICON_GRADIENT_LIGHT_Y
                            ),
                            end = Offset(
                                size.width * TAB_ICON_GRADIENT_DARK_X,
                                size.height * TAB_ICON_GRADIENT_DARK_Y
                            ),
                            tileMode = TileMode.Clamp
                        ),
                        blendMode = BlendMode.SrcIn
                    )
                } else {
                    drawRect(color = colors.textColorTertiary, blendMode = BlendMode.SrcIn)
                }
                if (cutoutPainter != null) {
                    with(cutoutPainter) {
                        draw(
                            size = size,
                            colorFilter = ColorFilter.tint(
                                color = colors.bgColorBottomBar,
                                blendMode = BlendMode.SrcIn
                            )
                        )
                    }
                }
            }
    )
}

@Composable
private fun DraggableUnreadBadge(
    unreadCount: Int,
    onClearUnread: () -> Unit,
    modifier: Modifier = Modifier,
    onDraggingChange: (Boolean) -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val clearThresholdPx = with(LocalDensity.current) { BADGE_CLEAR_DRAG_THRESHOLD_DP.dp.toPx() }
    val badgeOffset = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    var dragging by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .offset {
                IntOffset(
                    badgeOffset.value.x.roundToInt(),
                    badgeOffset.value.y.roundToInt()
                )
            }
            .graphicsLayer {
                val progress = (-badgeOffset.value.y / clearThresholdPx).coerceIn(0f, 1f)
                val scale = if (dragging) 1f + progress * 0.2f else 1f
                scaleX = scale
                scaleY = scale
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = {
                        dragging = true
                        onDraggingChange(true)
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        scope.launch {
                            val current = badgeOffset.value
                            badgeOffset.snapTo(
                                Offset(
                                    x = current.x + dragAmount.x,
                                    y = (current.y + dragAmount.y).coerceAtMost(0f)
                                )
                            )
                        }
                    },
                    onDragEnd = {
                        dragging = false
                        onDraggingChange(false)
                        val shouldClear = -badgeOffset.value.y >= clearThresholdPx
                        if (shouldClear) {
                            scope.launch {
                                badgeOffset.animateTo(
                                    Offset(0f, -clearThresholdPx * 1.5f),
                                    tween(180)
                                )
                                badgeOffset.snapTo(Offset.Zero)
                            }
                            onClearUnread()
                        } else {
                            scope.launch { badgeOffset.animateTo(Offset.Zero, tween(180)) }
                        }
                    },
                    onDragCancel = {
                        dragging = false
                        onDraggingChange(false)
                        scope.launch { badgeOffset.animateTo(Offset.Zero, tween(180)) }
                    }
                )
            }
            .wrapContentSize()
    ) {
        Badge(text = if (unreadCount > 99) "99+" else unreadCount.toString())
    }
}

@Composable
private fun MessagesScreen() {
    val context = LocalContext.current
    var showAddChatSheet by remember { mutableStateOf(false) }
    var chatType by remember { mutableStateOf(ChatType.SINGLE) }

    ConversationsPage(
        headerTitle = stringResource(R.string.compose_demo_messages),
        onConversationClick = {
            if (CustomerServiceManager.isCustomerServiceConversation(it.conversationID)) {
                CustomerServiceManager.openCustomerServiceChat(context)
            } else {
                ChatActivity.start(context, it.conversationID)
            }
        },
        onSearchClick = {
            SearchActivity.start(context)
        }
    ) {
        if (AppBuilderConfig.enableCreateConversation) {
            AddMoreButton(
                menuItems = createChatMenuItems(
                    onAddC2CChatClick = {
                        chatType = ChatType.SINGLE
                        showAddChatSheet = true
                    },
                    onAddGroupChatClick = {
                        chatType = ChatType.GROUP
                        showAddChatSheet = true
                    },
                )
            )
        }
    }

    if (showAddChatSheet) {
        AddNewChatBottomSheet(
            chatType = chatType,
            onDismiss = { showAddChatSheet = false },
            onCreateChat = { conversationId ->
                showAddChatSheet = false
                ChatActivity.start(context, conversationId)
            }
        )
    }
}

@Composable
private fun CallsScreen() {
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            FragmentContainerView(context).apply {
                id = R.id.compose_calls_fragment_container
                // Fragment transactions targeting this container must wait until it is
                // attached to the window, otherwise FragmentManager crashes with
                // "No view found for id".
                addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                    override fun onViewAttachedToWindow(view: View) {
                        removeOnAttachStateChangeListener(this)
                        val activity = context as? AppCompatActivity ?: return
                        val fragmentManager = activity.supportFragmentManager
                        val existing = fragmentManager.findFragmentById(id)
                        if (existing != null && existing.view?.parent === view) {
                            return
                        }
                        // After recreation, FragmentManager restores the old fragment but its
                        // view is never attached to this new container, so replace it with a
                        // fresh one.
                        fragmentManager.beginTransaction().apply {
                            existing?.let { remove(it) }
//                            add(id, RecentCallsFragment())
                        }.commit()
                    }

                    override fun onViewDetachedFromWindow(view: View) {
                    }
                })
            }
        }
    )
}

@Composable
private fun ContactsScreen() {
    var showAddContactSheet by remember { mutableStateOf(false) }
    var addType by remember { mutableStateOf(AddType.CONTACT) }
    val context = LocalContext.current
    ContactsPage(onGroupClick = {
        ChatActivity.start(context, "group_${it.userID}")
    }, onContactClick = {
        ChatActivity.start(context, "c2c_${it.userID}")
    }) {
        AddMoreButton(
            menuItems = createAddContactMenuItems(
                onAddFriendClick = {
                    addType = AddType.CONTACT
                    showAddContactSheet = true
                },
                onAddGroupClick = {
                    addType = AddType.GROUP
                    showAddContactSheet = true
                }
            )
        )
    }

    if (showAddContactSheet) {
        AddContactAndGroupBottomSheet(
            addType = addType,
            onDismiss = { showAddContactSheet = false }
        )
    }
}

@Composable
private fun SettingsScreenContent() {
    val colors = LocalTheme.current.colors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(color = colors.bgColorTopBar)
    ) {
        PageHeader(stringResource(R.string.compose_demo_me))
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            SettingsScreen()
        }
    }
}

private fun NavGraphBuilder.animatedComposable(
    route: String,
    content: @Composable AnimatedContentScope.(NavBackStackEntry) -> Unit
) {
    composable(
        route = route,
        enterTransition = {
            EnterTransition.None
        },
        exitTransition = {
            ExitTransition.None
        },
        popEnterTransition = {
            EnterTransition.None
        },
        popExitTransition = {
            ExitTransition.None
        },
        content = content
    )
}
