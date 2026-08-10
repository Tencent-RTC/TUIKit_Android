package io.trtc.tuikit.chat.uikit.components.conversationlist.config

import io.trtc.tuikit.chat.uikit.components.config.AppBuilderConfig
import io.trtc.tuikit.chat.uikit.components.config.ConversationAction

interface ConversationActionConfigProtocol {
    val isSupportDelete: Boolean
    val isSupportMute: Boolean
    val isSupportPin: Boolean
    val isSupportMarkUnread: Boolean
    val isSupportClearHistory: Boolean
    val actionCustomizer: ConversationActionCustomizer?
        get() = null
}

class ChatConversationActionConfig : ConversationActionConfigProtocol {

    private var _isSupportDelete: Boolean? = null
    private var _isSupportMute: Boolean? = null
    private var _isSupportPin: Boolean? = null
    private var _isSupportMarkUnread: Boolean? = null
    private var _isSupportClearHistory: Boolean? = null
    private var _actionCustomizer: ConversationActionCustomizer? = null

    constructor(
        isSupportDelete: Boolean? = null,
        isSupportMute: Boolean? = null,
        isSupportPin: Boolean? = null,
        isSupportMarkUnread: Boolean? = null,
        isSupportClearHistory: Boolean? = null
    ) {
        this._isSupportDelete = isSupportDelete
        this._isSupportMute = isSupportMute
        this._isSupportPin = isSupportPin
        this._isSupportMarkUnread = isSupportMarkUnread
        this._isSupportClearHistory = isSupportClearHistory
    }

    override var isSupportDelete: Boolean
        get() = _isSupportDelete
            ?: AppBuilderConfig.conversationActionList.contains(ConversationAction.DELETE)
        set(value) {
            _isSupportDelete = value
        }

    override var isSupportMute: Boolean
        get() = _isSupportMute
            ?: AppBuilderConfig.conversationActionList.contains(ConversationAction.MUTE)
        set(value) {
            _isSupportMute = value
        }

    override var isSupportPin: Boolean
        get() = _isSupportPin
            ?: AppBuilderConfig.conversationActionList.contains(ConversationAction.PIN)
        set(value) {
            _isSupportPin = value
        }

    override var isSupportMarkUnread: Boolean
        get() = _isSupportMarkUnread
            ?: AppBuilderConfig.conversationActionList.contains(ConversationAction.MARK_UNREAD)
        set(value) {
            _isSupportMarkUnread = value
        }

    override var isSupportClearHistory: Boolean
        get() = _isSupportClearHistory
            ?: AppBuilderConfig.conversationActionList.contains(ConversationAction.CLEAR_HISTORY)
        set(value) {
            _isSupportClearHistory = value
        }

    override val actionCustomizer: ConversationActionCustomizer?
        get() = _actionCustomizer

    fun customizeActions(block: ConversationActionEditor.() -> Unit): ChatConversationActionConfig = apply {
        _actionCustomizer = ConversationActionCustomizer { editor ->
            editor.block()
        }
    }
}
