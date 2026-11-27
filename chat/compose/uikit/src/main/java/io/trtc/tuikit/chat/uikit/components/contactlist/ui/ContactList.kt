package io.trtc.tuikit.chat.uikit.components.contactlist.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.trtc.tuikit.chat.uikit.compose.R
import io.trtc.tuikit.chat.uikit.components.widgets.azorderedlist.AZOrderedList
import io.trtc.tuikit.chat.uikit.components.widgets.azorderedlist.AZOrderedListItem
import io.trtc.tuikit.chat.uikit.components.theme.LocalTheme
import io.trtc.tuikit.chat.uikit.components.contactlist.config.ChatContactListConfig
import io.trtc.tuikit.chat.uikit.components.contactlist.config.ContactListConfigProtocol
import io.trtc.tuikit.chat.uikit.components.contactlist.model.ContactCustomItem
import io.trtc.tuikit.chat.uikit.components.contactlist.model.ContactCustomItemContext
import io.trtc.tuikit.chat.uikit.components.contactlist.model.buildContactListItems
import io.trtc.tuikit.chat.uikit.components.contactlist.ui.blacklist.BlacklistDialog
import io.trtc.tuikit.chat.uikit.components.contactlist.ui.friendapplication.FriendApplicationDialog
import io.trtc.tuikit.chat.uikit.components.contactlist.ui.groupapplication.GroupApplicationDialog
import io.trtc.tuikit.chat.uikit.components.contactlist.ui.mygroup.MyGroupDialog
import io.trtc.tuikit.chat.uikit.components.contactlist.utils.displayName
import io.trtc.tuikit.chat.uikit.components.contactlist.utils.matchesSearchQuery
import io.trtc.tuikit.chat.uikit.components.contactlist.viewmodel.ContactListViewModel
import io.trtc.tuikit.chat.uikit.components.contactlist.viewmodel.ContactListViewModelFactory
import io.trtc.tuikit.atomicxcore.api.contact.ContactInfo
import kotlinx.coroutines.launch
import kotlin.math.max

val LocalContactViewModel =
    compositionLocalOf<ContactListViewModel> { error("No ViewModel provided") }

@Composable
fun ContactList(
    modifier: Modifier = Modifier,
    config: ContactListConfigProtocol = ChatContactListConfig(),
    onGroupClick: (ContactInfo) -> Unit = {},
    onContactClick: (ContactInfo) -> Unit = {},
    contactListViewModelFactory: ContactListViewModelFactory = ContactListViewModelFactory(),
    onCreateChat: (String) -> Unit = {},
    flowController: ContactListFlowController = rememberContactListFlowController(),
) {
    val colors = LocalTheme.current.colors
    val context = LocalContext.current
    val density = LocalDensity.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val coroutineScope = rememberCoroutineScope()
    val viewModelKey = remember {
        "${ContactListViewModel::class.java.name}:${System.identityHashCode(Any())}"
    }
    val contactViewModel = viewModel(
        ContactListViewModel::class,
        key = viewModelKey,
        factory = contactListViewModelFactory
    )
    val friendList by contactViewModel.friendList.collectAsState(emptyList())
    val initialLoadFinished by contactViewModel.initialLoadFinished.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    val isSearching = config.showSearchBar && searchQuery.isNotBlank()
    val filteredContacts = if (config.showSearchBar) {
        friendList.filter { contact ->
            contact.matchesSearchQuery(searchQuery)
        }
    } else {
        friendList
    }

    var isShowBlacklist by remember { mutableStateOf(false) }
    var isShowMyGroupList by remember { mutableStateOf(false) }
    var isShowFriendApplication by remember { mutableStateOf(false) }
    var isShowGroupApplication by remember { mutableStateOf(false) }

    val searchBarHeightPx = with(density) { ContactListSearchBarHeight.toPx() }
    var searchBarOffsetPx by remember { mutableFloatStateOf(0f) }
    val searchBarOffsetAnim = remember { Animatable(0f) }

    LaunchedEffect(config.showSearchBar) {
        if (!config.showSearchBar) {
            searchQuery = ""
            keyboardController?.hide()
            searchBarOffsetPx = 0f
            searchBarOffsetAnim.snapTo(0f)
        }
    }

    val headerItems = remember(
        config.showNewContacts,
        config.showGroupApplications,
        config.showMyGroups,
        config.showBlacklist,
        config.itemCustomizer,
        contactViewModel,
        context,
    ) {
        val defaultItems = contactViewModel.getDefaultItems(
            config = config,
            onNavigateToMyGroup = {
                isShowMyGroupList = true
            },
            onNavigateToBlacklist = {
                isShowBlacklist = true
            },
            onNavigateToFriendApplications = {
                isShowFriendApplication = true
            },
            onNavigateToGroupApplications = {
                isShowGroupApplication = true
            }
        )
        buildContactListItems(
            itemContext = ContactCustomItemContext(context),
            defaults = defaultItems,
            customizer = config.itemCustomizer,
        )
    }

    MyGroupDialog(
        isVisible = isShowMyGroupList,
        groupStore = contactViewModel.groupStore,
        onDismiss = { isShowMyGroupList = false },
        onGroupClick = onGroupClick,
    )

    BlacklistDialog(
        isVisible = isShowBlacklist,
        contactStore = contactViewModel.contactStore,
        onDismiss = { isShowBlacklist = false },
        onContactClick = onContactClick,
    )

    FriendApplicationDialog(
        isVisible = isShowFriendApplication,
        contactStore = contactViewModel.contactStore,
        onDismiss = { isShowFriendApplication = false },
    )

    GroupApplicationDialog(
        isVisible = isShowGroupApplication,
        groupStore = contactViewModel.groupStore,
        onDismiss = { isShowGroupApplication = false },
    )

    ContactListFlowHost(
        controller = flowController,
        onCreateChat = onCreateChat,
        contactStore = contactViewModel.contactStore,
        groupStore = contactViewModel.groupStore,
    )

    val listScrollConnection = remember(config.showSearchBar, searchBarHeightPx) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                keyboardController?.hide()
                if (!config.showSearchBar || searchBarHeightPx <= 0f) {
                    return Offset.Zero
                }
                val dy = available.y
                if (dy == 0f) {
                    return Offset.Zero
                }
                val current = searchBarOffsetPx
                val newOffset = (current - dy).coerceIn(0f, searchBarHeightPx)
                if (newOffset == current) {
                    return Offset.Zero
                }
                searchBarOffsetPx = newOffset
                coroutineScope.launch { searchBarOffsetAnim.snapTo(newOffset) }
                return Offset(0f, current - newOffset)
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (!config.showSearchBar || searchBarHeightPx <= 0f) {
                    return Velocity.Zero
                }
                val current = searchBarOffsetAnim.value
                val velocityY = consumed.y + available.y
                val target = when {
                    velocityY > 300f -> 0f
                    velocityY < -300f -> searchBarHeightPx
                    current >= searchBarHeightPx / 2f -> searchBarHeightPx
                    else -> 0f
                }
                if (current != target) {
                    searchBarOffsetPx = target
                    searchBarOffsetAnim.animateTo(
                        target,
                        animationSpec = tween(durationMillis = 180)
                    )
                }
                return Velocity.Zero
            }
        }
    }

    val displayedSearchOffsetPx = searchBarOffsetAnim.value
    val remainingSearchHeightPx = if (config.showSearchBar) {
        max(0f, searchBarHeightPx - displayedSearchOffsetPx)
    } else {
        0f
    }

    CompositionLocalProvider(
        LocalContactViewModel provides contactViewModel,
    ) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(color = colors.bgColorOperate)
                .nestedScroll(listScrollConnection)
        ) {
            if (config.showSearchBar) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(with(density) { remainingSearchHeightPx.toDp() })
                        .clipToBounds()
                ) {
                    ContactListSearchBar(
                        query = searchQuery,
                        onQueryChange = { query ->
                            searchQuery = query
                        },
                        modifier = Modifier.offset {
                            IntOffset(0, -displayedSearchOffsetPx.toInt())
                        }
                    )
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                awaitPointerEvent()
                                keyboardController?.hide()
                            }
                        }
                    }
            ) {
                AZOrderedList(
                    modifier = Modifier.fillMaxSize(),
                    header = {
                        if (!isSearching) {
                            ContactListHeader(items = headerItems)
                        }
                    },
                    dataSource = filteredContacts.map { contact ->
                        AZOrderedListItem(
                            key = contact.userID,
                            label = contact.displayName,
                            avatarUrl = contact.avatarURL,
                            extraData = contact
                        )
                    },
                    onItemClick = { item -> onContactClick(item.extraData) },
                    onUserInteraction = { keyboardController?.hide() }
                )

                if (!isSearching && initialLoadFinished && friendList.isEmpty()) {
                    ContactListEmptyView(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
        }
    }
}

@Composable
private fun ContactListHeader(items: List<ContactCustomItem>) {
    val colors = LocalTheme.current.colors
    val density = LocalDensity.current
    val dividerHeight = with(density) {
        0.5.dp.toPx().toInt().coerceAtLeast(1).toDp()
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(color = colors.bgColorOperate)
    ) {
        items.forEachIndexed { index, item ->
            DefaultContactItem(item = item)
            if (index < items.size - 1) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 68.dp)
                        .height(dividerHeight)
                        .background(color = colors.strokeColorPrimary)
                )
            }
        }
    }
}

@Composable
fun DefaultContactItem(item: ContactCustomItem) {
    val colors = LocalTheme.current.colors
    val badgeCount by item.badgeCount.collectAsState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
            .background(color = colors.bgColorOperate)
            .clickable { item.onClick() }
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (item.iconResID != 0) {
            Image(
                painter = painterResource(item.iconResID),
                contentDescription = null,
                modifier = Modifier.size(40.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
        }

        Text(
            text = item.title.ifEmpty {
                if (item.titleResID != 0) stringResource(item.titleResID) else ""
            },
            color = colors.textColorPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.W400,
            modifier = Modifier.weight(1f)
        )

        if (badgeCount > 0) {
            ContactItemBadge(count = badgeCount)
        }
    }
}

@Composable
private fun ContactItemBadge(count: Int) {
    val colors = LocalTheme.current.colors
    val density = LocalDensity.current
    val badgeTextSize = with(density) { 11.dp.toSp() }
    Box(
        modifier = Modifier
            .size(18.dp)
            .background(color = colors.textColorError, shape = CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (count > 99) "99+" else count.toString(),
            color = Color.White,
            fontSize = badgeTextSize,
            fontWeight = FontWeight.W400,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}

@Composable
fun ContactListEmptyView(modifier: Modifier = Modifier) {
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
