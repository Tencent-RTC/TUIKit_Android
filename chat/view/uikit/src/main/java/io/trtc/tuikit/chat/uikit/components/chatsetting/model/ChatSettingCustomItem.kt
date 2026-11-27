package io.trtc.tuikit.chat.uikit.components.chatsetting.model

import android.content.Context
import android.view.View
import io.trtc.tuikit.chat.uikit.components.common.uicustom.CustomEditor
import io.trtc.tuikit.chat.uikit.components.common.uicustom.CustomItem
import io.trtc.tuikit.chat.uikit.components.common.uicustom.Customizer
import io.trtc.tuikit.chat.uikit.components.common.uicustom.EditorContext

object ChatSettingItemIDs {
    const val C2C_HEADER = "chatSetting.c2c.header"
    const val C2C_REMARK = "chatSetting.c2c.remark"
    const val C2C_DO_NOT_DISTURB = "chatSetting.c2c.doNotDisturb"
    const val C2C_PIN = "chatSetting.c2c.pin"
    const val C2C_CHAT_BACKGROUND = "chatSetting.c2c.chatBackground"
    const val C2C_BLACKLIST = "chatSetting.c2c.blacklist"
    const val C2C_SEND_MESSAGE = "chatSetting.c2c.sendMessage"
    const val C2C_VOICE_CALL = "chatSetting.c2c.voiceCall"
    const val C2C_VIDEO_CALL = "chatSetting.c2c.videoCall"
    const val C2C_CLEAR_HISTORY = "chatSetting.c2c.clearHistory"
    const val C2C_DELETE_FRIEND = "chatSetting.c2c.deleteFriend"

    const val GROUP_HEADER = "chatSetting.group.header"
    const val GROUP_MEMBER_PREVIEW = "chatSetting.group.memberPreview"
    const val GROUP_NOTICE = "chatSetting.group.notice"
    const val GROUP_MANAGEMENT = "chatSetting.group.management"
    const val GROUP_TYPE = "chatSetting.group.type"
    const val GROUP_JOIN_METHOD = "chatSetting.group.joinMethod"
    const val GROUP_INVITE_METHOD = "chatSetting.group.inviteMethod"
    const val GROUP_ALIAS = "chatSetting.group.alias"
    const val GROUP_DO_NOT_DISTURB = "chatSetting.group.doNotDisturb"
    const val GROUP_PIN = "chatSetting.group.pin"
    const val GROUP_CHAT_BACKGROUND = "chatSetting.group.chatBackground"
    const val GROUP_TRANSFER_OWNER = "chatSetting.group.transferOwner"
    const val GROUP_CLEAR_HISTORY = "chatSetting.group.clearHistory"
    const val GROUP_DELETE_AND_QUIT = "chatSetting.group.deleteAndQuit"
    const val GROUP_DISMISS = "chatSetting.group.dismiss"
}

object ChatSettingSectionIDs {
    const val C2C_REMARK = "chatSetting.section.c2c.remark"
    const val C2C_SWITCHES = "chatSetting.section.c2c.switches"
    const val C2C_CHAT_BACKGROUND = "chatSetting.section.c2c.chatBackground"
    const val C2C_BLACKLIST = "chatSetting.section.c2c.blacklist"
    const val C2C_ACTIONS = "chatSetting.section.c2c.actions"

    const val GROUP_SETTINGS = "chatSetting.section.group.settings"
    const val GROUP_ALIAS = "chatSetting.section.group.alias"
    const val GROUP_SWITCHES = "chatSetting.section.group.switches"
    const val GROUP_CHAT_BACKGROUND = "chatSetting.section.group.chatBackground"
    const val GROUP_ACTIONS = "chatSetting.section.group.actions"
}

data class ChatSettingCustomItem<C : EditorContext>(
    override val ID: String,
    val sectionID: String? = null,
    val viewFactory: (C) -> View,
) : CustomItem {
    init {
        require(sectionID == null || sectionID.isNotBlank()) {
            "Section ID must not be blank"
        }
    }
}

data class C2CChatSettingItemContext(
    override val androidContext: Context,
    val userID: String,
) : EditorContext

data class GroupChatSettingItemContext(
    override val androidContext: Context,
    val groupID: String,
) : EditorContext

internal fun <C : EditorContext> buildChatSettingItems(
    itemContext: C,
    defaults: List<ChatSettingCustomItem<C>>,
    customizer: Customizer<C, ChatSettingCustomItem<C>>?,
): List<ChatSettingCustomItem<C>> {
    val editor = CustomEditor(itemContext, defaults)
    customizer?.customize(editor)
    return editor.build()
}
