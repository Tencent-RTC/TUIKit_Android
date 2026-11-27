package io.trtc.tuikit.chat.uikit.components.contactlist.ui.addnewchat

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.trtc.tuikit.chat.uikit.compose.R
import io.trtc.tuikit.chat.uikit.components.theme.LocalTheme
import io.trtc.tuikit.chat.uikit.components.contactlist.viewmodel.AddNewChatViewModel

@Composable
fun GroupSettingsBottomSheet(
    viewModel: AddNewChatViewModel,
    onBack: () -> Unit,
    onShowGroupTypeSelection: () -> Unit
) {
    val colors = LocalTheme.current.colors
    val uiState by viewModel.uiState.collectAsState()
    val currentSelectedType by viewModel.currentSelectedGroupType.collectAsState()
    val context = LocalContext.current

    val displayedGroupName = uiState.groupName.ifBlank {
        viewModel.generateGroupName(uiState.selectedContacts)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        AddNewChatHeader(
            title = stringResource(R.string.contact_list_create_group),
            onBack = onBack
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            EditableSettingRow(
                title = stringResource(R.string.contact_list_group_name),
                value = uiState.groupName,
                onValueChange = { viewModel.updateGroupName(it) }
            )
            SettingsDivider()
            EditableSettingRow(
                title = stringResource(R.string.contact_list_group_id),
                value = uiState.groupID.orEmpty(),
                hint = stringResource(R.string.contact_list_group_id_option),
                onValueChange = { viewModel.updateGroupID(it.ifBlank { null }) }
            )
            SettingsDivider()
            GroupTypeRow(
                selectedTypeName = stringResource(currentSelectedType.displayNameResID),
                onClick = onShowGroupTypeSelection
            )

            Text(
                text = stringResource(currentSelectedType.descriptionResID),
                fontSize = 12.sp,
                lineHeight = 15.sp,
                color = colors.textColorSecondary,
                modifier = Modifier.padding(start = 16.dp, top = 8.dp, end = 16.dp)
            )

            GroupAvatarSelector(
                displayedGroupName = displayedGroupName,
                selectedAvatarUrl = uiState.groupAvatarUrl,
                onAvatarSelected = { viewModel.updateGroupAvatarUrl(it) }
            )

            SelectedMembersPreview(selectedContacts = uiState.selectedContacts)
        }

        CreateGroupButton(
            isCreating = uiState.isCreating,
            onClick = {
                submitCreateGroup(
                    viewModel = viewModel,
                    onBlockedReservedGroupID = {
                        Toast.makeText(
                            context,
                            context.getString(R.string.contact_list_group_id_edit_format_tips),
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    onBlockedCommunityGroupID = {
                        Toast.makeText(
                            context,
                            context.getString(R.string.contact_list_community_id_edit_format_tips),
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    onFailure = { _, desc ->
                        Toast.makeText(context, desc, Toast.LENGTH_SHORT).show()
                    }
                )
            }
        )
    }
}

private fun submitCreateGroup(
    viewModel: AddNewChatViewModel,
    onBlockedReservedGroupID: () -> Unit,
    onBlockedCommunityGroupID: () -> Unit,
    onFailure: (code: Int, desc: String) -> Unit
) {
    val currentState = viewModel.uiState.value
    val groupId = currentState.groupID
    val groupType = viewModel.currentSelectedGroupType.value.type
    when (CreateGroupSubmissionPolicy.evaluate(currentState.isCreating, groupId, groupType)) {
        CreateGroupSubmissionPolicy.Decision.BLOCKED_CREATING -> return
        CreateGroupSubmissionPolicy.Decision.BLOCKED_RESERVED_GROUP_ID -> {
            onBlockedReservedGroupID()
            return
        }
        CreateGroupSubmissionPolicy.Decision.BLOCKED_COMMUNITY_GROUP_ID -> {
            onBlockedCommunityGroupID()
            return
        }
        CreateGroupSubmissionPolicy.Decision.ALLOW -> Unit
    }
    viewModel.createGroupChatWithSettings(
        groupName = currentState.groupName,
        groupID = groupId,
        groupAvatarUrl = currentState.groupAvatarUrl,
        onSuccess = {},
        onFailure = onFailure
    )
}

@Composable
private fun EditableSettingRow(
    title: String,
    value: String,
    onValueChange: (String) -> Unit,
    hint: String = ""
) {
    val colors = LocalTheme.current.colors

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(colors.bgColorTopBar)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            fontSize = 16.sp,
            color = colors.textColorPrimary
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .padding(start = 8.dp),
            contentAlignment = Alignment.CenterEnd
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                textStyle = TextStyle(
                    fontSize = 16.sp,
                    color = colors.textColorPrimary,
                    textAlign = TextAlign.End
                ),
                singleLine = true,
                cursorBrush = SolidColor(colors.textColorLink),
                modifier = Modifier.fillMaxWidth()
            )

            if (value.isEmpty() && hint.isNotEmpty()) {
                Text(
                    text = hint,
                    fontSize = 16.sp,
                    color = colors.textColorSecondary,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun GroupTypeRow(
    selectedTypeName: String,
    onClick: () -> Unit
) {
    val colors = LocalTheme.current.colors

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(colors.bgColorTopBar)
            .clickable { onClick() }
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.contact_list_group_type_text),
            fontSize = 16.sp,
            color = colors.textColorPrimary
        )

        Text(
            text = selectedTypeName,
            fontSize = 16.sp,
            color = colors.textColorPrimary,
            textAlign = TextAlign.End,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )

        Icon(
            imageVector = Icons.AutoMirrored.Default.KeyboardArrowRight,
            contentDescription = stringResource(R.string.contact_list_group_type_select_text),
            tint = colors.textColorSecondary,
            modifier = Modifier
                .padding(start = 8.dp)
                .size(20.dp)
        )
    }
}

@Composable
private fun SettingsDivider() {
    val colors = LocalTheme.current.colors
    HorizontalDivider(
        thickness = 0.5.dp,
        color = colors.strokeColorPrimary
    )
}

@Composable
private fun CreateGroupButton(
    isCreating: Boolean,
    onClick: () -> Unit
) {
    val colors = LocalTheme.current.colors

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.bgColorOperate)
            .padding(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 20.dp)
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .size(width = 76.dp, height = 30.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(
                    if (isCreating) {
                        colors.textColorDisable
                    } else {
                        colors.textColorLink
                    }
                )
                .clickable(
                    enabled = !isCreating,
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { onClick() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.contact_list_create),
                fontSize = 16.sp,
                color = colors.textColorButton
            )
        }
    }
}
