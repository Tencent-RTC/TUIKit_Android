package io.trtc.tuikit.chat.uikit.components.chatbot

import io.trtc.tuikit.chat.uikit.components.messageinput.config.MessageInputActionCustomizer
import io.trtc.tuikit.chat.uikit.components.messageinput.config.MessageInputConfigProtocol

internal class ChatbotMessageInputConfig(
    private val base: MessageInputConfigProtocol
) : MessageInputConfigProtocol {
    override val isShowAudioRecorder: Boolean = false
    override val isShowPhotoTaker: Boolean = false
    override val isShowAudioCall: Boolean = false
    override val isShowVideoCall: Boolean = false
    override val isShowMore: Boolean = false
    override val isShowEmoji: Boolean = false
    override val enableMention: Boolean = false
    override val enableLongPressToTalk: Boolean = false
    override val audioMaxRecordDurationMs: Int
        get() = base.audioMaxRecordDurationMs
    override val actionCustomizer: MessageInputActionCustomizer?
        get() = base.actionCustomizer
}
