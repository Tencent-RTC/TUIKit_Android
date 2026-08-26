package io.trtc.tuikit.chat.uikit.components.chatsetting.ui.groupchatsetting

import android.content.Context
import io.trtc.tuikit.chat.uikit.R
import io.trtc.tuikit.chat.uikit.components.chatsetting.model.ChatSettingCustomItem
import io.trtc.tuikit.chat.uikit.components.chatsetting.model.ChatSettingItemIDs
import io.trtc.tuikit.chat.uikit.components.chatsetting.model.ChatSettingSectionIDs
import io.trtc.tuikit.chat.uikit.components.chatsetting.model.GroupChatSettingItemContext
import io.trtc.tuikit.chat.uikit.components.chatsetting.ui.GroupMemberPickerDialog
import io.trtc.tuikit.chat.uikit.components.chatsetting.ui.SettingRowButton
import io.trtc.tuikit.chat.uikit.components.chatsetting.viewmodel.GroupChatSettingViewModel
import io.trtc.tuikit.atomicx.widget.basicwidget.alertdialog.AtomicAlertDialog
import io.trtc.tuikit.atomicx.widget.basicwidget.alertdialog.cancelButton
import io.trtc.tuikit.atomicx.widget.basicwidget.alertdialog.confirmButton
import io.trtc.tuikit.atomicx.widget.basicwidget.toast.AtomicToast
import io.trtc.tuikit.atomicxcore.api.group.GroupMemberRole

internal class GroupChatSettingActionSection(
    private val context: Context,
    private val onGroupDeletedProvider: () -> (() -> Unit)?,
) {
    fun buildItems(viewModel: GroupChatSettingViewModel): List<ChatSettingCustomItem<GroupChatSettingItemContext>> {
        return listOf(
            ChatSettingCustomItem(
                ChatSettingItemIDs.GROUP_TRANSFER_OWNER,
                ChatSettingSectionIDs.GROUP_ACTIONS,
            ) { createTransferOwnerRow(viewModel) },
            ChatSettingCustomItem(
                ChatSettingItemIDs.GROUP_CLEAR_HISTORY,
                ChatSettingSectionIDs.GROUP_ACTIONS,
            ) { createClearHistoryRow(viewModel) },
            ChatSettingCustomItem(
                ChatSettingItemIDs.GROUP_DELETE_AND_QUIT,
                ChatSettingSectionIDs.GROUP_ACTIONS,
            ) { createDeleteAndQuitRow(viewModel) },
            ChatSettingCustomItem(
                ChatSettingItemIDs.GROUP_DISMISS,
                ChatSettingSectionIDs.GROUP_ACTIONS,
            ) { createDismissGroupRow(viewModel) },
        )
    }

    private fun createTransferOwnerRow(viewModel: GroupChatSettingViewModel): SettingRowButton {
        return SettingRowButton(context).apply {
            setTitle(context.getString(R.string.chat_setting_transfer_group_owner))
            setButtonStyle(SettingRowButton.Style.LINK)
            setOnClickListener {
                viewModel.loadAllGroupMembers {
                    val candidates = viewModel.memberList.value
                        .filter { it.role != GroupMemberRole.OWNER }
                    GroupMemberPickerDialog(
                        context = context,
                        title = context.getString(R.string.chat_setting_transfer_group_owner),
                        candidates = candidates,
                        maxSelection = 1,
                        onConfirm = { selected ->
                            val member = selected.firstOrNull() ?: return@GroupMemberPickerDialog
                            AtomicAlertDialog(context).apply {
                                init {
                                    content = context.getString(R.string.chat_setting_tansfer_owner_tips)
                                    confirmButton(
                                        context.getString(R.string.uikit_confirm),
                                        type = AtomicAlertDialog.TextColorPreset.RED
                                    ) { _ ->
                                        viewModel.changeOwner(member.userID)
                                    }
                                    cancelButton(context.getString(R.string.uikit_cancel))
                                }
                                show()
                            }
                        }
                    ).show()
                }
            }
        }
    }

    private fun createClearHistoryRow(viewModel: GroupChatSettingViewModel): SettingRowButton {
        return SettingRowButton(context).apply {
            setTitle(context.getString(R.string.chat_setting_clear_history_messages))
            setDangerStyle(true)
            setOnClickListener {
                AtomicAlertDialog(context).apply {
                    init {
                        content = context.getString(R.string.chat_setting_clear_group_history_messages_tips)
                        confirmButton(context.getString(R.string.uikit_confirm)) { _ ->
                            viewModel.clearChatHistory()
                        }
                        cancelButton(context.getString(R.string.uikit_cancel))
                    }
                    show()
                }
            }
        }
    }

    private fun createDeleteAndQuitRow(viewModel: GroupChatSettingViewModel): SettingRowButton {
        return SettingRowButton(context).apply {
            setTitle(context.getString(R.string.chat_setting_delete_and_quit))
            setDangerStyle(true)
            setOnClickListener {
                AtomicAlertDialog(context).apply {
                    init {
                        content = context.getString(R.string.chat_setting_delete_and_quit_tips)
                        confirmButton(
                            context.getString(R.string.uikit_confirm),
                            type = AtomicAlertDialog.TextColorPreset.RED
                        ) { _ ->
                            viewModel.quitGroup(
                                onSuccess = { onGroupDeletedProvider()?.invoke() },
                                onFailure = { _, desc ->
                                    AtomicToast.show(context, desc, style = AtomicToast.Style.ERROR)
                                }
                            )
                        }
                        cancelButton(context.getString(R.string.uikit_cancel))
                    }
                    show()
                }
            }
        }
    }

    private fun createDismissGroupRow(viewModel: GroupChatSettingViewModel): SettingRowButton {
        return SettingRowButton(context).apply {
            setTitle(context.getString(R.string.chat_setting_dismiss_group))
            setDangerStyle(true)
            setOnClickListener {
                AtomicAlertDialog(context).apply {
                    init {
                        content = context.getString(R.string.chat_setting_dismiss_group_tips)
                        confirmButton(
                            context.getString(R.string.uikit_confirm),
                            type = AtomicAlertDialog.TextColorPreset.RED
                        ) { _ ->
                            viewModel.dismissGroup(
                                onSuccess = { onGroupDeletedProvider()?.invoke() },
                                onFailure = { _, desc ->
                                    AtomicToast.show(context, desc, style = AtomicToast.Style.ERROR)
                                }
                            )
                        }
                        cancelButton(context.getString(R.string.uikit_cancel))
                    }
                    show()
                }
            }
        }
    }
}
