package io.trtc.tuikit.chat.uikit.components.messagelist

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.trtc.tuikit.chat.uikit.compose.R
import io.trtc.tuikit.chat.uikit.components.widgets.Avatar
import io.trtc.tuikit.chat.uikit.components.widgets.AvatarSize
import io.trtc.tuikit.chat.uikit.components.widgets.DialogNavBar
import io.trtc.tuikit.chat.uikit.components.widgets.DialogNavBarMode
import io.trtc.tuikit.chat.uikit.components.widgets.FullScreenDialog
import io.trtc.tuikit.chat.uikit.components.theme.LocalTheme
import io.trtc.tuikit.chat.uikit.components.chatsetting.ui.displayName
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.MessageContent
import io.trtc.tuikit.chat.uikit.components.messagelist.viewmodel.MessageReadReceiptViewModel
import io.trtc.tuikit.atomicxcore.api.group.GroupMember
import io.trtc.tuikit.atomicxcore.api.message.MessageInfo

@Composable
fun MessageReadReceiptDialog(
    isVisible: Boolean,
    message: MessageInfo?,
    onDismiss: () -> Unit,
    onUserClick: (String) -> Unit = {}
) {
    if (isVisible && message != null) {
        FullScreenDialog(
            onDismissRequest = onDismiss
        ) {
            val viewModel: MessageReadReceiptViewModel = viewModel(
                factory = MessageReadReceiptViewModel.Factory(message)
            )

            MessageReadReceiptScreen(
                message = message,
                viewModel = viewModel,
                onBack = onDismiss,
                onUserClick = onUserClick
            )
        }
    }
}

@Composable
fun MessageReadReceiptScreen(
    message: MessageInfo,
    viewModel: MessageReadReceiptViewModel,
    onBack: () -> Unit,
    onUserClick: (String) -> Unit = {}
) {
    val colors = LocalTheme.current.colors

    var selectedTabIndex by remember { mutableIntStateOf(0) }

    val readMemberList by viewModel.readMemberList.collectAsState()
    val hasMoreReadMembers by viewModel.hasMoreReadMembers.collectAsState()
    val unReadMemberList by viewModel.unReadMemberList.collectAsState()
    val hasMoreUnReadMembers by viewModel.hasMoreUnReadMembers.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadReadMembers()
        viewModel.loadUnreadMembers()
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bgColorOperate)
            .systemBarsPadding(),
        topBar = {
            Column {
                DialogNavBar(
                    mode = DialogNavBarMode.BackTitle,
                    title = stringResource(R.string.message_list_read_receipt_detail),
                    onLeadingClick = onBack
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(colors.strokeColorSecondary)
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(colors.bgColorOperate)
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            MessageBubble(message)
            Spacer(modifier = Modifier.height(24.dp))

            CustomTabBar(
                selectedTabIndex = selectedTabIndex,
                onTabSelected = { selectedTabIndex = it },
                message = message,
                readCount = readMemberList.size,
                unreadCount = unReadMemberList.size
            )

            when (selectedTabIndex) {
                0 -> {
                    MemberList(
                        memberList = readMemberList,
                        hasMoreMembers = hasMoreReadMembers,
                        onLoadMore = { viewModel.loadMoreReadMembers() },
                        onUserClick = onUserClick
                    )
                }

                1 -> {
                    MemberList(
                        memberList = unReadMemberList,
                        hasMoreMembers = hasMoreUnReadMembers,
                        onLoadMore = { viewModel.loadMoreUnreadMembers() },
                        onUserClick = onUserClick
                    )
                }
            }
        }
    }
}

@Composable
fun CustomTabBar(
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit,
    message: MessageInfo,
    readCount: Int = 0,
    unreadCount: Int = 0
) {
    val colors = LocalTheme.current.colors

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TabItem(
                title = "${stringResource(R.string.message_list_read_receipt_read_by)} ($readCount)",
                iconResId = R.drawable.message_list_all_read_icon,
                isSelected = selectedTabIndex == 0,
                iconTintColor = colors.textColorLink,
                onClick = { onTabSelected(0) },
                modifier = Modifier.weight(1f)
            )

            TabItem(
                title = "${stringResource(R.string.message_list_read_receipt_delivered_to)} ($unreadCount)",
                iconResId = R.drawable.message_list_unread_icon,
                isSelected = selectedTabIndex == 1,
                iconTintColor = colors.textColorSecondary,
                onClick = { onTabSelected(1) },
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
    }
}

@Composable
fun TabItem(
    title: String,
    iconResId: Int,
    iconTintColor: Color,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalTheme.current.colors

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(40.dp))
            .background(
                if (isSelected) {
                    colors.tabColorSelected
                } else {
                    colors.tabColorUnselected
                }
            )
            .clickable { onClick() }
            .padding(horizontal = 20.dp, vertical = 20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    painter = painterResource(id = iconResId),
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = iconTintColor
                )

                Text(
                    text = title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.W400,
                    color = if (isSelected) colors.textColorPrimary else colors.textColorSecondary
                )
            }

        }
    }
}

@Composable
fun MessageBubble(
    message: MessageInfo
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.End
    ) {
        val maxContentHeight = LocalWindowInfo.current.containerSize.let {
            with(LocalDensity.current) {
                it.height.toDp()
            }
        }
        Column(
            modifier = Modifier
                .heightIn(max = maxContentHeight * 0.4f)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            MessageContent(message)
        }
    }
}

@Composable
fun MemberList(
    memberList: List<GroupMember>,
    hasMoreMembers: Boolean,
    onLoadMore: () -> Unit,
    onUserClick: (String) -> Unit
) {
    val listState = rememberLazyListState()

    val shouldLoadMore by remember {
        derivedStateOf {
            val lastVisibleItem = listState.layoutInfo.visibleItemsInfo.lastOrNull()
            lastVisibleItem != null && lastVisibleItem.index >= memberList.size - 1 && hasMoreMembers
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) {
            onLoadMore()
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        items(memberList, key = { it.userID }) { member ->
            UserRow(
                member = member,
                onUserClick = onUserClick
            )
        }

    }
}

@Composable
fun UserRow(
    member: GroupMember,
    onUserClick: (String) -> Unit
) {
    val colors = LocalTheme.current.colors

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp)
            .clickable { onUserClick(member.userID) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Avatar(
                modifier = Modifier.size(36.dp),
                url = member.avatarURL,
                name = member.displayName,
                size = AvatarSize.S
            )

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = member.displayName,
                fontSize = 14.sp,
                fontWeight = FontWeight.W600,
                color = colors.textColorPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

    }
}
