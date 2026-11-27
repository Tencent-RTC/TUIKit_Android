package io.trtc.tuikit.chat.uikit.components.chatsetting.ui.groupchatsetting

import android.content.Context
import io.trtc.tuikit.chat.uikit.R
import io.trtc.tuikit.chat.uikit.components.chatsetting.model.ChatSettingCustomItem
import io.trtc.tuikit.chat.uikit.components.chatsetting.model.ChatSettingItemIDs
import io.trtc.tuikit.chat.uikit.components.chatsetting.model.ChatSettingSectionIDs
import io.trtc.tuikit.chat.uikit.components.chatsetting.model.GroupChatSettingItemContext
import io.trtc.tuikit.chat.uikit.components.chatsetting.ui.SettingRowNavigate
import io.trtc.tuikit.chat.uikit.components.chatsetting.ui.SettingRowToggle
import io.trtc.tuikit.chat.uikit.components.chatsetting.ui.TextInputDialog
import io.trtc.tuikit.chat.uikit.components.chatsetting.viewmodel.GroupChatSettingViewModel

internal class GroupChatSettingRowsController(
    private val context: Context,
    private val viewModelProvider: () -> GroupChatSettingViewModel?,
    private val onOpenGroupManagement: (GroupChatSettingViewModel) -> Unit,
    private val onShowJoinMethod: (GroupChatSettingViewModel) -> Unit,
    private val onShowInviteMethod: (GroupChatSettingViewModel) -> Unit,
    private val onShowChatBackgroundPicker: (GroupChatSettingViewModel) -> Unit,
) {
    private lateinit var groupNoticeRow: SettingRowNavigate
    private lateinit var groupTypeRow: SettingRowNavigate
    private lateinit var joinMethodRow: SettingRowNavigate
    private lateinit var inviteMethodRow: SettingRowNavigate
    private lateinit var myAliasRow: SettingRowNavigate
    private lateinit var groupManageRow: SettingRowNavigate
    private lateinit var chatBackgroundRow: SettingRowNavigate
    private lateinit var doNotDisturbRow: SettingRowToggle
    private lateinit var pinRow: SettingRowToggle

    fun buildItems(): List<ChatSettingCustomItem<GroupChatSettingItemContext>> {
        groupNoticeRow = SettingRowNavigate(context).apply {
            setTitle(context.getString(R.string.chat_setting_group_notice))
        }
        groupManageRow = SettingRowNavigate(context).apply {
            setTitle(context.getString(R.string.chat_setting_group_management))
            setShowArrow(true)
        }
        groupTypeRow = SettingRowNavigate(context).apply {
            setTitle(context.getString(R.string.chat_setting_group_type))
            setShowArrow(false)
        }
        joinMethodRow = SettingRowNavigate(context).apply {
            setTitle(context.getString(R.string.chat_setting_join_group_method))
        }
        inviteMethodRow = SettingRowNavigate(context).apply {
            setTitle(context.getString(R.string.chat_setting_group_invited_method))
        }
        myAliasRow = SettingRowNavigate(context).apply {
            setTitle(context.getString(R.string.chat_setting_my_alias_in_group))
        }
        doNotDisturbRow = SettingRowToggle(context).apply {
            setTitle(context.getString(R.string.chat_setting_do_not_disturb))
            onToggleChanged = { checked -> viewModelProvider()?.setDoNotDisturb(checked) }
        }
        pinRow = SettingRowToggle(context).apply {
            setTitle(context.getString(R.string.chat_setting_pin))
            onToggleChanged = { checked -> viewModelProvider()?.setPinChat(checked) }
        }
        chatBackgroundRow = SettingRowNavigate(context).apply {
            setTitle(context.getString(R.string.chat_setting_chat_background))
            setShowArrow(true)
            setOnClickListener {
                viewModelProvider()?.let(onShowChatBackgroundPicker)
            }
        }

        return listOf(
            ChatSettingCustomItem(
                ChatSettingItemIDs.GROUP_NOTICE,
                ChatSettingSectionIDs.GROUP_SETTINGS,
            ) { groupNoticeRow },
            ChatSettingCustomItem(
                ChatSettingItemIDs.GROUP_MANAGEMENT,
                ChatSettingSectionIDs.GROUP_SETTINGS,
            ) { groupManageRow },
            ChatSettingCustomItem(
                ChatSettingItemIDs.GROUP_TYPE,
                ChatSettingSectionIDs.GROUP_SETTINGS,
            ) { groupTypeRow },
            ChatSettingCustomItem(
                ChatSettingItemIDs.GROUP_JOIN_METHOD,
                ChatSettingSectionIDs.GROUP_SETTINGS,
            ) { joinMethodRow },
            ChatSettingCustomItem(
                ChatSettingItemIDs.GROUP_INVITE_METHOD,
                ChatSettingSectionIDs.GROUP_SETTINGS,
            ) { inviteMethodRow },
            ChatSettingCustomItem(
                ChatSettingItemIDs.GROUP_ALIAS,
                ChatSettingSectionIDs.GROUP_ALIAS,
            ) { myAliasRow },
            ChatSettingCustomItem(
                ChatSettingItemIDs.GROUP_DO_NOT_DISTURB,
                ChatSettingSectionIDs.GROUP_SWITCHES,
            ) { doNotDisturbRow },
            ChatSettingCustomItem(
                ChatSettingItemIDs.GROUP_PIN,
                ChatSettingSectionIDs.GROUP_SWITCHES,
            ) { pinRow },
            ChatSettingCustomItem(
                ChatSettingItemIDs.GROUP_CHAT_BACKGROUND,
                ChatSettingSectionIDs.GROUP_CHAT_BACKGROUND,
            ) { chatBackgroundRow },
        )
    }

    fun refresh(state: GroupChatSettingUiState, viewModel: GroupChatSettingViewModel) {
        val permissions = state.permissions

        groupNoticeRow.setValue(
            state.notification.ifEmpty { context.getString(R.string.chat_setting_no_group_notice) }
        )
        groupNoticeRow.setShowArrow(false)
        groupNoticeRow.setCustomAccessory(
            if (permissions.canEditGroupNotice) R.drawable.chat_setting_group_name_edit_icon else null
        )
        if (permissions.canEditGroupNotice) {
            groupNoticeRow.isClickable = true
            groupNoticeRow.setOnClickListener {
                TextInputDialog(
                    context = context,
                    title = context.getString(R.string.chat_setting_group_notice),
                    initialText = state.notification,
                    maxLength = 300,
                    multiline = true,
                    onConfirm = { value ->
                        if (value != state.notification) {
                            viewModel.setGroupNotice(value)
                        }
                    }
                ).show()
            }
        } else {
            groupNoticeRow.isClickable = false
            groupNoticeRow.setOnClickListener(null)
        }

        if (permissions.canOpenGroupManagement) {
            groupManageRow.setShowArrow(true)
            groupManageRow.setCustomAccessory(null)
            groupManageRow.isClickable = true
            groupManageRow.setOnClickListener {
                onOpenGroupManagement(viewModel)
            }
        } else {
            groupManageRow.isClickable = false
            groupManageRow.setOnClickListener(null)
        }

        groupTypeRow.setValue(
            context.getString(GroupChatSettingTextMapper.groupTypeTextRes(state.groupType))
        )
        groupTypeRow.setShowArrow(false)
        groupTypeRow.setCustomAccessory(null)
        groupTypeRow.isClickable = false

        joinMethodRow.setValue(
            GroupChatSettingTextMapper.joinOptionTextRes(state.joinOption)?.let { context.getString(it) } ?: ""
        )
        joinMethodRow.setCustomAccessory(null)
        joinMethodRow.setShowArrow(permissions.canEditJoinOption)
        if (permissions.canEditJoinOption) {
            joinMethodRow.isClickable = true
            joinMethodRow.setOnClickListener { onShowJoinMethod(viewModel) }
        } else {
            joinMethodRow.isClickable = false
            joinMethodRow.setOnClickListener(null)
        }

        inviteMethodRow.setValue(
            GroupChatSettingTextMapper.inviteOptionTextRes(state.inviteOption)?.let { context.getString(it) } ?: ""
        )
        inviteMethodRow.setCustomAccessory(null)
        inviteMethodRow.setShowArrow(permissions.canEditInviteOption)
        if (permissions.canEditInviteOption) {
            inviteMethodRow.isClickable = true
            inviteMethodRow.setOnClickListener { onShowInviteMethod(viewModel) }
        } else {
            inviteMethodRow.isClickable = false
            inviteMethodRow.setOnClickListener(null)
        }

        myAliasRow.setValue(
            state.nameCard?.takeIf { it.isNotBlank() } ?: context.getString(R.string.chat_setting_not_set)
        )
        myAliasRow.setShowArrow(false)
        myAliasRow.setCustomAccessory(
            if (permissions.canEditSelfNameCard) R.drawable.chat_setting_group_name_edit_icon else null
        )
        if (permissions.canEditSelfNameCard) {
            myAliasRow.isClickable = true
            myAliasRow.setOnClickListener {
                TextInputDialog(
                    context = context,
                    title = context.getString(R.string.chat_setting_modify_group_name_card),
                    initialText = viewModel.selfNameCard.value ?: "",
                    onConfirm = { value -> viewModel.setGroupNickname(value) }
                ).show()
            }
        } else {
            myAliasRow.isClickable = false
            myAliasRow.setOnClickListener(null)
        }

        doNotDisturbRow.setChecked(state.isNotDisturb)
        pinRow.setChecked(state.isPinned)

        chatBackgroundRow.setTitle(context.getString(R.string.chat_setting_chat_background))
        chatBackgroundRow.setValue(
            if (state.hasCustomChatBackground) {
                context.getString(R.string.chat_setting_chat_background_custom)
            } else {
                context.getString(R.string.chat_setting_chat_background_default)
            }
        )
        chatBackgroundRow.setShowArrow(true)
        chatBackgroundRow.setOnClickListener { onShowChatBackgroundPicker(viewModel) }
    }
}
