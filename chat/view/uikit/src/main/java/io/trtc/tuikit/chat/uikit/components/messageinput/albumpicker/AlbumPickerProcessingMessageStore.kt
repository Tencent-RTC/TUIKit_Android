package io.trtc.tuikit.chat.uikit.components.messageinput.albumpicker
import io.trtc.tuikit.atomicx.albumpicker.AlbumMediaType
import io.trtc.tuikit.atomicxcore.api.conversation.ConversationType
import io.trtc.tuikit.atomicxcore.api.login.LoginStore
import io.trtc.tuikit.atomicxcore.api.message.ImageMessagePayload
import io.trtc.tuikit.atomicxcore.api.message.MessageInfo
import io.trtc.tuikit.atomicxcore.api.message.MessagePayload
import io.trtc.tuikit.atomicxcore.api.message.MessageSenderInfo
import io.trtc.tuikit.atomicxcore.api.message.MessageStatus
import io.trtc.tuikit.atomicxcore.api.message.MessageType
import io.trtc.tuikit.atomicxcore.api.message.VideoMessagePayload
import io.trtc.tuikit.chat.uikit.components.common.ConversationIDUtil
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

internal data class AlbumPickerPlaceholderInfo(
    val placeholderID: String,
    val mediaType: AlbumMediaType,
    val thumbnailPath: String?,
    val thumbnailWidth: Int,
    val thumbnailHeight: Int,
    val mediaPath: String?,
    val videoDurationSeconds: Int,
    val progress: Int
)

internal object AlbumPickerProcessingMessageStore {
    private val _messagesByConversation =
        MutableStateFlow<Map<String, List<MessageInfo>>>(emptyMap())

    val messagesByConversation: StateFlow<Map<String, List<MessageInfo>>> =
        _messagesByConversation.asStateFlow()

    fun upsert(conversationID: String, info: AlbumPickerPlaceholderInfo) {
        _messagesByConversation.update { current ->
            val messages = current[conversationID].orEmpty()
            val existingIndex = messages.indexOfFirst { it.msgID == info.placeholderID }
            val timestamp = if (existingIndex >= 0) {
                messages[existingIndex].timestamp ?: (System.currentTimeMillis() / MILLIS_PER_SECOND)
            } else {
                System.currentTimeMillis() / MILLIS_PER_SECOND
            }
            val message = makePlaceholderMessage(conversationID, info, timestamp)
            if (existingIndex < 0) {
                current + (conversationID to messages + message)
            } else if (isEquivalent(messages[existingIndex], message)) {
                current
            } else {
                val updated = messages.toMutableList()
                updated[existingIndex] = message
                current + (conversationID to updated)
            }
        }
    }

    fun remove(conversationID: String, placeholderID: String) {
        _messagesByConversation.update { current ->
            val messages = current[conversationID].orEmpty()
            if (messages.none { it.msgID == placeholderID }) {
                return@update current
            }
            val updated = messages.filterNot { it.msgID == placeholderID }
            if (updated.isEmpty()) {
                current - conversationID
            } else {
                current + (conversationID to updated)
            }
        }
    }

    fun localMediaPath(message: MessageInfo): String? {
        return when (val payload = message.messagePayload) {
            is ImageMessagePayload -> payload.originalImagePath?.takeIf { it.isNotEmpty() }
            is VideoMessagePayload -> payload.videoPath?.takeIf { it.isNotEmpty() }
            else -> null
        }
    }

    private fun makePlaceholderMessage(
        conversationID: String,
        info: AlbumPickerPlaceholderInfo,
        timestamp: Long
    ): MessageInfo {
        val isGroup = ConversationIDUtil.isGroup(conversationID)
        val targetId = if (isGroup) {
            ConversationIDUtil.groupId(conversationID)
        } else {
            conversationID.removePrefix(ConversationIDUtil.C2C_PREFIX)
        }
        val loginUserInfo = LoginStore.shared.loginState.loginUserInfo.value
        return MessageInfo(
            msgID = info.placeholderID,
            status = MessageStatus.SENDING,
            timestamp = timestamp,
            from = MessageSenderInfo(
                userID = loginUserInfo?.userID.orEmpty(),
                avatarURL = loginUserInfo?.avatarURL,
                nickname = loginUserInfo?.nickname
            ),
            to = targetId,
            isSentBySelf = true,
            conversationType = if (isGroup) ConversationType.GROUP else ConversationType.C2C,
            messageType = if (info.mediaType == AlbumMediaType.VIDEO) MessageType.VIDEO else MessageType.IMAGE,
            messagePayload = makePlaceholderPayload(info),
            uploadMediaProgress = info.progress.coerceIn(0, MAX_PROGRESS)
        )
    }

    private fun makePlaceholderPayload(info: AlbumPickerPlaceholderInfo): MessagePayload {
        if (info.mediaType == AlbumMediaType.VIDEO) {
            return VideoMessagePayload(
                videoSnapshotPath = info.thumbnailPath,
                videoSnapshotWidth = info.thumbnailWidth,
                videoSnapshotHeight = info.thumbnailHeight,
                videoType = DEFAULT_VIDEO_TYPE,
                videoDuration = info.videoDurationSeconds,
                videoPath = info.mediaPath
            )
        }
        return ImageMessagePayload(
            largeImagePath = info.thumbnailPath,
            originalImagePath = info.mediaPath,
            originalImageWidth = info.thumbnailWidth,
            originalImageHeight = info.thumbnailHeight
        )
    }

    private fun isEquivalent(lhs: MessageInfo, rhs: MessageInfo): Boolean {
        if (lhs.uploadMediaProgress != rhs.uploadMediaProgress) {
            return false
        }
        if (localMediaPath(lhs) != localMediaPath(rhs)) {
            return false
        }
        return thumbnailDescriptor(lhs) == thumbnailDescriptor(rhs)
    }

    private fun thumbnailDescriptor(message: MessageInfo): String? {
        return when (val payload = message.messagePayload) {
            is VideoMessagePayload ->
                "${payload.videoSnapshotPath.orEmpty()}_${payload.videoSnapshotWidth}x${payload.videoSnapshotHeight}"
            is ImageMessagePayload ->
                "${payload.largeImagePath.orEmpty()}_${payload.originalImageWidth}x${payload.originalImageHeight}"
            else -> null
        }
    }

    private const val MILLIS_PER_SECOND = 1000
    private const val MAX_PROGRESS = 100
    private const val DEFAULT_VIDEO_TYPE = "mp4"
}
