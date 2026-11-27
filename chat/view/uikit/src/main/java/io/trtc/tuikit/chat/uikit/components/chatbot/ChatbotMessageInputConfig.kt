package io.trtc.tuikit.chat.uikit.components.chatbot

import io.trtc.tuikit.chat.uikit.components.messageinput.config.MessageInputActionCustomizer
import io.trtc.tuikit.chat.uikit.components.messageinput.config.MessageInputAppearance
import io.trtc.tuikit.chat.uikit.components.messageinput.config.MessageInputConfigProtocol

internal class ChatbotMessageInputConfig(
    private val base: MessageInputConfigProtocol
) : MessageInputConfigProtocol {
    override val isShowAudioRecorder: Boolean
        get() = base.isShowAudioRecorder
    override val isShowPhotoTaker: Boolean
        get() = base.isShowPhotoTaker
    override val isShowAudioCall: Boolean = false
    override val isShowVideoCall: Boolean = false
    override val isShowMore: Boolean
        get() = base.isShowMore
    override val isShowEmoji: Boolean
        get() = base.isShowEmoji
    override val enableMention: Boolean = false
    override val enableLongPressToTalk: Boolean
        get() = base.enableLongPressToTalk
    override val enableRealtimeVoiceToText: Boolean
        get() = base.enableRealtimeVoiceToText
    override val enableSendRecordedVoice: Boolean
        get() = base.enableSendRecordedVoice
    override val audioMaxRecordDurationMs: Int
        get() = base.audioMaxRecordDurationMs
    override val actionCustomizer: MessageInputActionCustomizer?
        get() = base.actionCustomizer
    override val appearance: MessageInputAppearance?
        get() = base.appearance
    override val keepSendButtonVisible: Boolean
        get() = base.keepSendButtonVisible
    override val initialInputText: String?
        get() = base.initialInputText
    override val shouldShowSendButtonRedDot: (() -> Boolean)?
        get() = base.shouldShowSendButtonRedDot
    override val onSendButtonRedDotClick: (() -> Unit)?
        get() = base.onSendButtonRedDotClick
}
