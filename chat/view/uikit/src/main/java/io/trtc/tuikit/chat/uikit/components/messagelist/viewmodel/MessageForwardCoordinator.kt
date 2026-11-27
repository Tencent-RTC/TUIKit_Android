package io.trtc.tuikit.chat.uikit.components.messagelist.viewmodel
import io.trtc.tuikit.atomicxcore.api.message.MessageInfo
import io.trtc.tuikit.atomicxcore.api.message.OfflinePushInfo

class MessageForwardCoordinator(
    private val currentVisibleMessages: () -> List<MessageInfo>,
    private val offlinePushInfoFactory: (conversationID: String) -> OfflinePushInfo
) {
    fun prepareMessagesForForward(
        messages: List<MessageInfo>,
        conversationID: String,
        needReadReceipt: Boolean
    ): List<MessageInfo> {
        val messageIdToIndex = currentVisibleMessages()
            .withIndex()
            .associate { it.value.msgID to it.index }
        return messages
            .sortedWith(
                compareBy<MessageInfo> { message ->
                    messageIdToIndex[message.msgID]?.let { -it } ?: Int.MAX_VALUE
                }
                    .thenBy { it.msgID.orEmpty() }
            )
            .map { message ->
                message.copy(
                    needReadReceipt = needReadReceipt,
                    isExtensionEnabled = false,
                    offlinePushInfo = offlinePushInfoFactory(conversationID)
                )
            }
    }
}
