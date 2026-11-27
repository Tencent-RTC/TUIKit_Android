package io.trtc.tuikit.chat.uikit.components.messageinput.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.trtc.tuikit.chat.uikit.compose.R
import io.trtc.tuikit.chat.uikit.components.widgets.Avatar
import io.trtc.tuikit.chat.uikit.components.widgets.AvatarSize
import io.trtc.tuikit.chat.uikit.components.widgets.FullScreenDialog
import io.trtc.tuikit.chat.uikit.components.theme.LocalTheme
import io.trtc.tuikit.chat.uikit.components.messageinput.model.MentionInfo
import io.trtc.tuikit.chat.uikit.components.userpicker.SelectionCheckBox
import io.trtc.tuikit.chat.uikit.components.userpicker.UserPicker
import io.trtc.tuikit.chat.uikit.components.userpicker.UserPickerData
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.group.GroupMember
import io.trtc.tuikit.atomicxcore.api.group.GroupMemberFilterRole
import io.trtc.tuikit.atomicxcore.api.group.GroupMemberStore

fun GroupMember.getDisplayName(): String {
    return nameCard?.takeIf { it.isNotBlank() }
        ?: friendRemark?.takeIf { it.isNotBlank() }
        ?: nickname?.takeIf { it.isNotBlank() }
        ?: userID
}

fun GroupMember.toUserPickerData(): UserPickerData<GroupMember> {
    return UserPickerData(
        key = userID,
        label = getDisplayName(),
        avatarUrl = avatarURL,
        extraData = this
    )
}

@Composable
fun MentionMemberDialog(
    isVisible: Boolean,
    groupID: String,
    onDismiss: () -> Unit,
    onConfirm: (List<MentionInfo>) -> Unit
) {
    if (!isVisible) return

    FullScreenDialog(onDismissRequest = onDismiss) {
        MentionMemberContent(
            groupID = groupID,
            onBackClick = onDismiss,
            onConfirm = { mentionInfoList ->
                onConfirm(mentionInfoList)
                onDismiss()
            }
        )
    }
}

@Composable
private fun MentionMemberContent(
    groupID: String,
    onBackClick: () -> Unit,
    onConfirm: (List<MentionInfo>) -> Unit
) {
    val colors = LocalTheme.current.colors
    val groupMemberStore = remember(groupID) { GroupMemberStore.create(groupID) }
    val groupMembers = remember { mutableStateListOf<GroupMember>() }
    var isLoadingMore by remember { mutableStateOf(false) }
    var hasLoadedInitial by remember { mutableStateOf(false) }
    var atAll by remember { mutableStateOf(false) }
    val atAllDisplayName = stringResource(R.string.message_input_mention_all)
    val selectedMembers = remember { mutableStateListOf<GroupMember>() }

    LaunchedEffect(groupID) {
        if (!hasLoadedInitial) {
            groupMemberStore.loadMembers(
                roleList = listOf(GroupMemberFilterRole.ALL),
                completion = object : CompletionHandler {
                    override fun onSuccess() {
                        groupMembers.clear()
                        groupMembers.addAll(groupMemberStore.state.memberList.value)
                        hasLoadedInitial = true
                    }

                    override fun onFailure(code: Int, desc: String) {
                        hasLoadedInitial = true
                    }
                }
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(color = colors.bgColorOperate)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.uikit_cancel),
                fontSize = 16.sp,
                fontWeight = FontWeight.W400,
                color = colors.textColorLink,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onBackClick() }
            )

            Text(
                text = stringResource(R.string.message_input_mention_select_member),
                fontSize = 16.sp,
                fontWeight = FontWeight.W600,
                color = colors.textColorPrimary
            )

            Text(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        val mentionInfos = buildList {
                            if (atAll) {
                                add(
                                    MentionInfo(
                                        userID = MentionInfo.AT_ALL_USER_ID,
                                        displayName = atAllDisplayName,
                                    )
                                )
                            }
                            addAll(selectedMembers.map {
                                MentionInfo(
                                    userID = it.userID,
                                    displayName = it.getDisplayName(),
                                )
                            })
                        }
                        onConfirm(mentionInfos)
                    },
                text = stringResource(R.string.uikit_confirm),
                fontSize = 16.sp,
                fontWeight = FontWeight.W400,
                color = colors.textColorLink
            )
        }

        HorizontalDivider(
            thickness = 0.5.dp,
            color = colors.strokeColorPrimary
        )

        AtAllItem(
            isSelected = atAll,
            onClick = {
                atAll = !atAll
            }
        )

        val dataSource = remember(groupMembers.toList()) {
            groupMembers.map { it.toUserPickerData() }
        }

        UserPicker(
            modifier = Modifier.fillMaxSize(),
            dataSource = dataSource,
            onSelectedChanged = { selected ->
                selectedMembers.clear()
                selectedMembers.addAll(selected.map { it.extraData })
            },
            onReachEnd = {
                if (!isLoadingMore && groupMemberStore.state.hasMoreMembers.value) {
                    isLoadingMore = true
                    groupMemberStore.loadMoreMembers(object : CompletionHandler {
                        override fun onSuccess() {
                            groupMembers.clear()
                            groupMembers.addAll(groupMemberStore.state.memberList.value)
                            isLoadingMore = false
                        }

                        override fun onFailure(code: Int, desc: String) {
                            isLoadingMore = false
                        }
                    })
                }
            }
        )
    }
}

@Composable
private fun AtAllItem(isSelected: Boolean = false, onClick: () -> Unit) {
    val colors = LocalTheme.current.colors
    val atAllText = stringResource(R.string.message_input_mention_all)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clickable { onClick() }
            .background(color = colors.bgColorOperate)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SelectionCheckBox(
            checked = isSelected,
            onCheckedChange = { _ ->
                onClick()
            }
        )
        Spacer(modifier = Modifier.width(16.dp))
        Avatar(url = null, name = atAllText, size = AvatarSize.M)
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = "@$atAllText",
            color = colors.textColorPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.W400,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(26.dp))
    }
}
