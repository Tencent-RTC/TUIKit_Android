package io.trtc.tuikit.chat.uikit.components.messagelist.viewmodel
import io.trtc.tuikit.atomicxcore.api.message.MessageInfo

enum class MessageMediaFileType {
    THUMB_IMAGE,
    LARGE_IMAGE,
    ORIGINAL_IMAGE,
    VIDEO_SNAPSHOT,
    VIDEO,
    AUDIO,
    FILE
}

class MessageResourceCoordinator(
    private val downloader: (MessageInfo, MessageMediaFileType, Completion) -> Unit
) {
    private val inFlightRequests = mutableSetOf<RequestKey>()

    fun request(message: MessageInfo, resourceType: MessageMediaFileType) {
        val messageId = message.msgID ?: return
        val key = RequestKey(messageId, resourceType)
        if (!inFlightRequests.add(key)) {
            return
        }
        downloader(
            message,
            resourceType,
            object : Completion {
                override fun onComplete() {
                    inFlightRequests.remove(key)
                }
            }
        )
    }

    fun clear() {
        inFlightRequests.clear()
    }

    interface Completion {
        fun onComplete()
    }

    private data class RequestKey(
        val messageId: String,
        val resourceType: MessageMediaFileType
    )
}
