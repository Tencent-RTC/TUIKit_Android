package io.trtc.tuikit.chat.uikit.components.chatsetting.config

interface C2CChatSettingConfigProtocol {
    val isShowHeader: Boolean
    val isShowRemark: Boolean
    val isShowDoNotDisturb: Boolean
    val isShowPin: Boolean
    val isShowChatBackground: Boolean
    val isShowBlacklist: Boolean
    val isShowSendMessage: Boolean
    val isShowVoiceCall: Boolean
    val isShowVideoCall: Boolean
    val isShowClearHistory: Boolean
    val isShowDeleteFriend: Boolean
    val itemCustomizer: C2CChatSettingItemCustomizer?
        get() = null
}

class C2CChatSettingConfig(
    override var isShowHeader: Boolean = true,
    override var isShowRemark: Boolean = true,
    override var isShowDoNotDisturb: Boolean = true,
    override var isShowPin: Boolean = true,
    override var isShowChatBackground: Boolean = true,
    override var isShowBlacklist: Boolean = true,
    override var isShowSendMessage: Boolean = true,
    override var isShowVoiceCall: Boolean = true,
    override var isShowVideoCall: Boolean = true,
    override var isShowClearHistory: Boolean = true,
    override var isShowDeleteFriend: Boolean = true,
) : C2CChatSettingConfigProtocol {

    override var itemCustomizer: C2CChatSettingItemCustomizer? = null
        private set

    fun customizeItems(block: C2CChatSettingItemEditor.() -> Unit): C2CChatSettingConfig = apply {
        itemCustomizer = C2CChatSettingItemCustomizer { editor ->
            editor.block()
        }
    }
}
