package io.trtc.tuikit.chat.uikit.components.chatsetting.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.trtc.tuikit.chat.uikit.compose.R
import io.trtc.tuikit.chat.uikit.components.widgets.Avatar
import io.trtc.tuikit.chat.uikit.components.widgets.AvatarContent
import io.trtc.tuikit.chat.uikit.components.widgets.AvatarSize
import io.trtc.tuikit.chat.uikit.components.widgets.Toast
import io.trtc.tuikit.chat.uikit.components.theme.LocalTheme
import io.trtc.tuikit.chat.uikit.components.common.AtomicCallEventPublisher
import io.trtc.tuikit.chat.uikit.components.chatsetting.config.C2CChatSettingConfig
import io.trtc.tuikit.chat.uikit.components.chatsetting.config.C2CChatSettingConfigProtocol
import io.trtc.tuikit.chat.uikit.components.chatsetting.model.C2CChatSettingItemContext
import io.trtc.tuikit.chat.uikit.components.chatsetting.model.ChatSettingCustomItem
import io.trtc.tuikit.chat.uikit.components.chatsetting.model.ChatSettingItemIDs
import io.trtc.tuikit.chat.uikit.components.chatsetting.model.ChatSettingSectionIDs
import io.trtc.tuikit.chat.uikit.components.chatsetting.model.buildChatSettingItems
import io.trtc.tuikit.chat.uikit.components.chatsetting.viewmodel.C2CChatSettingViewModel
import io.trtc.tuikit.chat.uikit.components.chatsetting.viewmodel.C2CChatSettingViewModelFactory

@Composable
fun C2CChatSetting(
    userID: String,
    modifier: Modifier = Modifier,
    onSendMessageClick: () -> Unit = {},
    onContactDeleted: () -> Unit = {},
    onVoiceCallClick: (() -> Unit)? = null,
    onVideoCallClick: (() -> Unit)? = null,
    customActions: (@Composable () -> Unit)? = null,
    config: C2CChatSettingConfigProtocol = C2CChatSettingConfig(),
    c2cChatSettingViewModelFactory: C2CChatSettingViewModelFactory = C2CChatSettingViewModelFactory(userID)
) {
    val colors = LocalTheme.current.colors
    val context = LocalContext.current
    val viewModel = viewModel(
        C2CChatSettingViewModel::class,
        key = userID,
        factory = c2cChatSettingViewModelFactory
    )

    val nickname by viewModel.nickname.collectAsState()
    val avatar by viewModel.avatar.collectAsState()
    val signature by viewModel.signature.collectAsState()
    val remark by viewModel.remark.collectAsState()
    val isNotDisturb by viewModel.isNotDisturb.collectAsState()
    val isPinned by viewModel.isPinned.collectAsState()
    val isInBlacklist by viewModel.isInBlacklist.collectAsState()
    val chatBackgroundImageUri by viewModel.chatBackgroundImageUri.collectAsState()

    var showRemarkDialog by remember { mutableStateOf(false) }
    var showBackgroundPicker by remember { mutableStateOf(false) }
    var showClearHistoryDialog by remember { mutableStateOf(false) }
    var showDeleteFriendDialog by remember { mutableStateOf(false) }

    val itemContext = remember(userID) {
        C2CChatSettingItemContext(androidContext = context, userID = userID)
    }
    val items = buildChatSettingItems(
        itemContext = itemContext,
        defaults = buildList {
                if (config.isShowHeader) {
                    add(
                        ChatSettingCustomItem<C2CChatSettingItemContext>(ChatSettingItemIDs.C2C_HEADER) {
                            C2CHeaderSection(viewModel)
                        }
                    )
                }
                if (config.isShowRemark) {
                    add(
                        ChatSettingCustomItem(
                            ChatSettingItemIDs.C2C_REMARK,
                            ChatSettingSectionIDs.C2C_REMARK,
                        ) {
                            SettingRowNavigate(
                                title = stringResource(R.string.chat_setting_remark_name),
                                value = remark,
                                showArrow = false,
                                customAccessoryResId = R.drawable.chat_setting_group_name_edit_icon,
                                onClick = { showRemarkDialog = true }
                            )
                        }
                    )
                }
                if (config.isShowDoNotDisturb) {
                    add(
                        ChatSettingCustomItem(
                            ChatSettingItemIDs.C2C_DO_NOT_DISTURB,
                            ChatSettingSectionIDs.C2C_SWITCHES,
                        ) {
                            SettingRowToggle(
                                title = stringResource(R.string.chat_setting_do_not_disturb),
                                checked = isNotDisturb,
                                onCheckedChange = { checked -> viewModel.setDoNotDisturb(checked) }
                            )
                        }
                    )
                }
                if (config.isShowPin) {
                    add(
                        ChatSettingCustomItem(
                            ChatSettingItemIDs.C2C_PIN,
                            ChatSettingSectionIDs.C2C_SWITCHES,
                        ) {
                            SettingRowToggle(
                                title = stringResource(R.string.chat_setting_pin),
                                checked = isPinned,
                                onCheckedChange = { checked -> viewModel.setPinChat(checked) }
                            )
                        }
                    )
                }
                if (config.isShowChatBackground) {
                    add(
                        ChatSettingCustomItem(
                            ChatSettingItemIDs.C2C_CHAT_BACKGROUND,
                            ChatSettingSectionIDs.C2C_CHAT_BACKGROUND,
                        ) {
                            SettingRowNavigate(
                                title = stringResource(R.string.chat_setting_chat_background),
                                value = stringResource(
                                    if (chatBackgroundImageUri.isNullOrBlank()) {
                                        R.string.chat_setting_chat_background_default
                                    } else {
                                        R.string.chat_setting_chat_background_custom
                                    }
                                ),
                                showArrow = true,
                                onClick = { showBackgroundPicker = true }
                            )
                        }
                    )
                }
                if (config.isShowBlacklist) {
                    add(
                        ChatSettingCustomItem(
                            ChatSettingItemIDs.C2C_BLACKLIST,
                            ChatSettingSectionIDs.C2C_BLACKLIST,
                        ) {
                            SettingRowToggle(
                                title = stringResource(R.string.chat_setting_add_blacklist),
                                checked = isInBlacklist,
                                onCheckedChange = { viewModel.toggleBlacklist() }
                            )
                        }
                    )
                }
                if (config.isShowSendMessage) {
                    add(
                        ChatSettingCustomItem(
                            ChatSettingItemIDs.C2C_SEND_MESSAGE,
                            ChatSettingSectionIDs.C2C_ACTIONS,
                        ) {
                            SettingRowButton(
                                title = stringResource(R.string.chat_setting_send_messages),
                                style = SettingRowButtonStyle.LINK,
                                onClick = onSendMessageClick
                            )
                        }
                    )
                }
                if (config.isShowVoiceCall) {
                    add(
                        ChatSettingCustomItem(
                            ChatSettingItemIDs.C2C_VOICE_CALL,
                            ChatSettingSectionIDs.C2C_ACTIONS,
                        ) {
                            SettingRowButton(
                                title = stringResource(R.string.chat_setting_voice_call),
                                style = SettingRowButtonStyle.LINK,
                                onClick = {
                                    onVoiceCallClick?.invoke()
                                        ?: publishStartCallEvent(userID, MEDIA_TYPE_AUDIO)
                                }
                            )
                        }
                    )
                }
                if (config.isShowVideoCall) {
                    add(
                        ChatSettingCustomItem(
                            ChatSettingItemIDs.C2C_VIDEO_CALL,
                            ChatSettingSectionIDs.C2C_ACTIONS,
                        ) {
                            SettingRowButton(
                                title = stringResource(R.string.chat_setting_video_call),
                                style = SettingRowButtonStyle.LINK,
                                onClick = {
                                    onVideoCallClick?.invoke()
                                        ?: publishStartCallEvent(userID, MEDIA_TYPE_VIDEO)
                                }
                            )
                        }
                    )
                }
                if (config.isShowClearHistory) {
                    add(
                        ChatSettingCustomItem(
                            ChatSettingItemIDs.C2C_CLEAR_HISTORY,
                            ChatSettingSectionIDs.C2C_ACTIONS,
                        ) {
                            SettingRowButton(
                                title = stringResource(R.string.chat_setting_clear_history_messages),
                                style = SettingRowButtonStyle.DANGER,
                                onClick = { showClearHistoryDialog = true }
                            )
                        }
                    )
                }
                if (config.isShowDeleteFriend) {
                    add(
                        ChatSettingCustomItem(
                            ChatSettingItemIDs.C2C_DELETE_FRIEND,
                            ChatSettingSectionIDs.C2C_ACTIONS,
                        ) {
                            SettingRowButton(
                                title = stringResource(R.string.chat_setting_delete_friend),
                                style = SettingRowButtonStyle.DANGER,
                                onClick = { showDeleteFriendDialog = true }
                            )
                        }
                    )
                }
                if (customActions != null) {
                    add(
                        ChatSettingCustomItem(
                            "chatSetting.c2c.customActions",
                            ChatSettingSectionIDs.C2C_ACTIONS,
                        ) { customActions() }
                    )
                }
            },
        customizer = config.itemCustomizer,
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(color = colors.bgColorInput)
            .verticalScroll(rememberScrollState())
    ) {
        ChatSettingItemColumn(
            itemContext = itemContext,
            items = items
        )
    }

    TextInputBottomSheet(
        isVisible = showRemarkDialog,
        title = stringResource(R.string.chat_setting_modify_contact_remark),
        initialText = remark,
        onDismiss = { showRemarkDialog = false },
        onConfirm = { viewModel.setFriendRemark(it) }
    )

    ChatBackgroundPickerDialog(
        isVisible = showBackgroundPicker,
        selectedImageUri = chatBackgroundImageUri,
        onDismiss = { showBackgroundPicker = false },
        onBackgroundSelected = { imageUri ->
            if (imageUri.isNullOrBlank()) {
                viewModel.clearChatBackground()
            } else {
                viewModel.setChatBackground(imageUri)
            }
        }
    )

    ChatSettingConfirmDialog(
        isVisible = showClearHistoryDialog,
        message = stringResource(R.string.chat_setting_clear_contact_history_messages_tips),
        onDismiss = { showClearHistoryDialog = false },
        onCancel = { showClearHistoryDialog = false },
        onConfirm = {
            showClearHistoryDialog = false
            viewModel.clearChatHistory()
        }
    )

    ChatSettingConfirmDialog(
        isVisible = showDeleteFriendDialog,
        message = stringResource(R.string.chat_setting_delete_friend_tips),
        isConfirmDestructive = true,
        onDismiss = { showDeleteFriendDialog = false },
        onCancel = { showDeleteFriendDialog = false },
        onConfirm = {
            showDeleteFriendDialog = false
            viewModel.deleteFriend(
                onSuccess = { onContactDeleted() },
                onFailure = { _, desc ->
                    Toast.error(context, desc)
                }
            )
        }
    )
}

@Composable
private fun C2CHeaderSection(viewModel: C2CChatSettingViewModel) {
    val colors = LocalTheme.current.colors
    val nickname by viewModel.nickname.collectAsState()
    val avatar by viewModel.avatar.collectAsState()
    val signature by viewModel.signature.collectAsState()
    val displayName = nickname.ifEmpty { viewModel.userID }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.bgColorOperate)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Avatar(
            content = AvatarContent.Image(url = avatar, fallbackName = displayName),
            size = AvatarSize.L
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 16.dp)
        ) {
            Text(
                text = displayName,
                fontSize = 18.sp,
                fontWeight = FontWeight.W400,
                color = colors.textColorPrimary,
                maxLines = 1
            )
            Text(
                text = "${stringResource(R.string.chat_setting_user_id)}: ${viewModel.userID}",
                fontSize = 13.sp,
                fontWeight = FontWeight.W400,
                color = colors.textColorTertiary,
                modifier = Modifier.padding(top = 4.dp)
            )
            if (signature.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.chat_setting_signature_prefix) + signature,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.W400,
                    color = colors.textColorTertiary,
                    maxLines = 1,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}

private const val MEDIA_TYPE_AUDIO = AtomicCallEventPublisher.MEDIA_TYPE_AUDIO
private const val MEDIA_TYPE_VIDEO = AtomicCallEventPublisher.MEDIA_TYPE_VIDEO

private fun publishStartCallEvent(userID: String, mediaType: String) {
    AtomicCallEventPublisher.publishStartCall(
        participantIds = listOf(userID),
        mediaType = mediaType
    )
}
