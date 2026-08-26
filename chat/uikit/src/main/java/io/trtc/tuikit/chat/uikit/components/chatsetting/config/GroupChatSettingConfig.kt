package io.trtc.tuikit.chat.uikit.components.chatsetting.config

interface GroupChatSettingConfigProtocol {
    val isShowHeader: Boolean
    val isShowMemberPreview: Boolean
    val isShowNotice: Boolean
    val isShowManagement: Boolean
    val isShowGroupType: Boolean
    val isShowJoinMethod: Boolean
    val isShowInviteMethod: Boolean
    val isShowAlias: Boolean
    val isShowDoNotDisturb: Boolean
    val isShowPin: Boolean
    val isShowChatBackground: Boolean
    val isShowTransferOwner: Boolean
    val isShowClearHistory: Boolean
    val isShowDeleteAndQuit: Boolean
    val isShowDismiss: Boolean
    val itemCustomizer: GroupChatSettingItemCustomizer?
        get() = null
}

class GroupChatSettingConfig(
    override var isShowHeader: Boolean = true,
    override var isShowMemberPreview: Boolean = true,
    override var isShowNotice: Boolean = true,
    override var isShowManagement: Boolean = true,
    override var isShowGroupType: Boolean = true,
    override var isShowJoinMethod: Boolean = true,
    override var isShowInviteMethod: Boolean = true,
    override var isShowAlias: Boolean = true,
    override var isShowDoNotDisturb: Boolean = true,
    override var isShowPin: Boolean = true,
    override var isShowChatBackground: Boolean = true,
    override var isShowTransferOwner: Boolean = true,
    override var isShowClearHistory: Boolean = true,
    override var isShowDeleteAndQuit: Boolean = true,
    override var isShowDismiss: Boolean = true,
) : GroupChatSettingConfigProtocol {

    override var itemCustomizer: GroupChatSettingItemCustomizer? = null
        private set

    fun customizeItems(block: GroupChatSettingItemEditor.() -> Unit): GroupChatSettingConfig = apply {
        itemCustomizer = GroupChatSettingItemCustomizer { editor ->
            editor.block()
        }
    }
}
