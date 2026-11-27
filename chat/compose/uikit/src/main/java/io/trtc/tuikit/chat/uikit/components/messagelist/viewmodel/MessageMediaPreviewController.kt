package io.trtc.tuikit.chat.uikit.components.messagelist.viewmodel

import android.content.Context
import android.util.Log
import io.trtc.tuikit.chat.uikit.components.imageviewer.EventHandler
import io.trtc.tuikit.chat.uikit.components.imageviewer.ImageElement
import io.trtc.tuikit.chat.uikit.components.imageviewer.ImageViewer
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.message.ImageMessagePayload
import io.trtc.tuikit.atomicxcore.api.message.MessageInfo
import io.trtc.tuikit.atomicxcore.api.message.MessageLoadDirection
import io.trtc.tuikit.atomicxcore.api.message.MessageType
import io.trtc.tuikit.atomicxcore.api.message.VideoMessagePayload
import kotlinx.coroutines.CoroutineScope

private data class ImageViewerMediaEntry(
    val messageId: String,
    val element: ImageElement
)

@Suppress("UNUSED_PARAMETER")
internal class MessageMediaPreviewController(
    scope: CoroutineScope,
    private val messageSource: MessageMediaPreviewMessageSource,
    private val downloadResource: (MessageInfo, MessageMediaFileType, CompletionHandler) -> Unit,
    private val enableBoundaryLoading: Boolean = true,
    private val reverseMessageOrderForPreview: Boolean = true,
    private val canPlayDirectlyByPath: (String) -> Boolean = {
        MessageVideoPlaybackController.canPlayDirectlyByPath(it)
    }
) {
    private var activeImageViewerSession: ImageViewer.Session? = null

    fun showImage(context: Context, messageInfo: MessageInfo) {
        try {
            messageSource.loadInitialMediaMessages(messageInfo) { messages ->
                openImageViewer(messages, messageInfo)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error showing media", e)
        }
    }

    private fun openImageViewer(messages: List<MessageInfo>, messageInfo: MessageInfo) {
        try {
            val mediaEntries = buildImageViewerMediaEntries(messages)
            if (mediaEntries.isEmpty()) {
                Log.w(TAG, "No media data available to show")
                return
            }
            val initialIndex = mediaEntries.indexOfFirst { it.messageId == messageInfo.msgID }
                .takeIf { it >= 0 } ?: 0
            var viewerSession: ImageViewer.Session? = null
            val eventHandler = createImageViewerEventHandler { viewerSession }
            viewerSession = ImageViewer.view(
                imageElements = mediaEntries.map { it.element },
                initialIndex = initialIndex,
                onEventTriggered = eventHandler
            )
            activeImageViewerSession = viewerSession
            viewerSession?.addOnClosedListener {
                if (activeImageViewerSession === viewerSession) {
                    activeImageViewerSession = null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error showing media", e)
        }
    }

    fun clear() {
        activeImageViewerSession = null
    }

    private fun createImageViewerEventHandler(sessionProvider: () -> ImageViewer.Session?): EventHandler {
        return object : EventHandler {
            override fun onEvent(eventData: Map<String, Any>, callback: (Any?) -> Unit) {
                val eventName = eventData.keys.firstOrNull()
                when (eventName) {
                    ImageViewer.EVENT_LOAD_MORE_OLDER -> {
                        if (!enableBoundaryLoading) {
                            callback(null)
                            return
                        }
                        loadMoreImageViewerMediaList(MessageLoadDirection.OLDER, callback, sessionProvider)
                    }

                    ImageViewer.EVENT_LOAD_MORE_NEWER -> {
                        if (!enableBoundaryLoading) {
                            callback(null)
                            return
                        }
                        loadMoreImageViewerMediaList(MessageLoadDirection.NEWER, callback, sessionProvider)
                    }

                    ImageViewer.EVENT_DOWNLOAD_VIDEO -> {
                        val payload = eventData[eventName] as? Map<*, *>
                        val pathParam = payload?.get("path") as? String
                        val stableIdParam = payload?.get("stableId") as? String
                        handleImageViewerDownloadVideo(pathParam, stableIdParam, callback)
                    }

                    ImageViewer.EVENT_SAVE_MEDIA -> {
                        val payload = eventData[eventName] as? Map<*, *>
                        val pathParam = payload?.get("path") as? String
                        val stableIdParam = payload?.get("stableId") as? String
                        val mediaTypeParam = payload?.get("mediaType") as? Int
                        handleImageViewerSaveMedia(pathParam, stableIdParam, mediaTypeParam, callback)
                    }

                    ImageViewer.EVENT_IMAGE_TAP -> callback(null)

                    else -> callback(null)
                }
            }
        }
    }

    private fun handleImageViewerDownloadVideo(
        imagePath: String?,
        stableId: String?,
        callback: (Any?) -> Unit
    ) {
        if (imagePath.isNullOrBlank() && stableId.isNullOrBlank()) {
            callback(null)
            return
        }
        val target = stableId?.takeIf { it.isNotBlank() }?.let { messageId ->
            messageSource.currentMediaMessages().firstOrNull { message ->
                message.messageType == MessageType.VIDEO && message.msgID == messageId
            }
        } ?: messageSource.currentMediaMessages().firstOrNull { message ->
            message.messageType == MessageType.VIDEO &&
                (message.messagePayload as? VideoMessagePayload)?.videoSnapshotPath == imagePath
        }
        if (target == null) {
            callback(null)
            return
        }
        downloadResource(
            target,
            MessageMediaFileType.VIDEO,
            object : CompletionHandler {
                override fun onSuccess() {
                    val resolvedPath = resolveDownloadedVideoPath(target, imagePath, stableId)
                    if (resolvedPath != null) {
                        callback(resolvedPath)
                        return
                    }
                    // Messages outside a MessageListStore (merged message detail) keep the payload
                    // they were created with, so the downloaded path only shows up after a refresh.
                    messageSource.refreshMediaMessages {
                        callback(resolveDownloadedVideoPath(target, imagePath, stableId))
                    }
                }

                override fun onFailure(code: Int, desc: String) {
                    callback(null)
                }
            }
        )
    }

    private fun resolveDownloadedVideoPath(target: MessageInfo, imagePath: String?, stableId: String?): String? {
        val refreshedTarget = findMediaMessage(path = imagePath, stableId = stableId, mediaType = IMAGE_VIEWER_VIDEO_TYPE)
            ?: target
        return (refreshedTarget.messagePayload as? VideoMessagePayload)
            ?.videoPath
            ?.takeIf { it.isNotBlank() }
    }

    private fun handleImageViewerSaveMedia(
        path: String?,
        stableId: String?,
        mediaType: Int?,
        callback: (Any?) -> Unit
    ) {
        val target = findMediaMessage(path, stableId, mediaType)
        if (target == null) {
            callback(null)
            return
        }
        val fileType = when (target.messageType) {
            MessageType.IMAGE -> MessageMediaFileType.ORIGINAL_IMAGE
            MessageType.VIDEO -> MessageMediaFileType.VIDEO
            else -> {
                callback(null)
                return
            }
        }
        downloadResource(
            target,
            fileType,
            object : CompletionHandler {
                override fun onSuccess() {
                    val savePath = resolveLocalSavePath(target)
                    if (savePath != null) {
                        callback(savePath)
                        return
                    }
                    messageSource.refreshMediaMessages {
                        val refreshedTarget = findMediaMessage(path, stableId, mediaType) ?: target
                        callback(resolveLocalSavePath(refreshedTarget))
                    }
                }

                override fun onFailure(code: Int, desc: String) {
                    callback(null)
                }
            }
        )
    }

    private fun findMediaMessage(path: String?, stableId: String?, mediaType: Int?): MessageInfo? {
        val messages = messageSource.currentMediaMessages()
        val stableTarget = stableId?.takeIf { it.isNotBlank() }?.let { messageId ->
            messages.firstOrNull { message ->
                message.msgID == messageId && matchesMediaType(message, mediaType)
            }
        }
        if (stableTarget != null) {
            return stableTarget
        }
        return path?.takeIf { it.isNotBlank() }?.let { sourcePath ->
            messages.firstOrNull { message ->
                matchesMediaType(message, mediaType) && mediaPaths(message).contains(sourcePath)
            }
        }
    }

    private fun matchesMediaType(message: MessageInfo, mediaType: Int?): Boolean {
        return when (mediaType) {
            0 -> message.messageType == MessageType.IMAGE
            1 -> message.messageType == MessageType.VIDEO
            else -> message.messageType == MessageType.IMAGE || message.messageType == MessageType.VIDEO
        }
    }

    private fun mediaPaths(message: MessageInfo): List<String> {
        return when (val payload = message.messagePayload) {
            is ImageMessagePayload -> listOfNotNull(
                payload.originalImagePath,
                payload.largeImagePath,
                payload.thumbImagePath
            ).filter { it.isNotBlank() }

            is VideoMessagePayload -> listOfNotNull(
                payload.videoSnapshotPath,
                payload.videoPath
            ).filter { it.isNotBlank() }

            else -> emptyList()
        }
    }

    private fun resolveLocalSavePath(message: MessageInfo): String? {
        return when (val payload = message.messagePayload) {
            is ImageMessagePayload -> payload.originalImagePath
                ?.takeIf { it.isNotBlank() }
                ?: payload.largeImagePath?.takeIf { it.isNotBlank() }
                ?: payload.thumbImagePath?.takeIf { it.isNotBlank() }

            is VideoMessagePayload -> payload.videoPath?.takeIf { it.isNotBlank() }
            else -> null
        }
    }

    private fun loadMoreImageViewerMediaList(
        direction: MessageLoadDirection,
        callback: (Any?) -> Unit,
        sessionProvider: () -> ImageViewer.Session?
    ) {
        val targetSession = sessionProvider()
        if (targetSession == null || targetSession.isClosed) {
            callback(null)
            return
        }
        messageSource.loadMoreMediaMessages(direction) { messages ->
            if (targetSession.isClosed) {
                callback(null)
                return@loadMoreMediaMessages
            }
            val updatedMediaList = buildImageViewerMediaEntries(messages).map { it.element }
            targetSession.submitMediaList(updatedMediaList)
            callback(null)
        }
    }

    private fun buildImageViewerMediaEntries(messages: List<MessageInfo>): List<ImageViewerMediaEntry> {
        val mediaEntries = mutableListOf<ImageViewerMediaEntry>()
        val orderedMessages = if (reverseMessageOrderForPreview) {
            messages.asReversed()
        } else {
            messages
        }
        orderedMessages.forEach { message ->
            val mediaElement = createImageViewerElement(message) ?: return@forEach
            val messageId = message.msgID ?: return@forEach
            mediaEntries.add(ImageViewerMediaEntry(messageId = messageId, element = mediaElement))
        }
        return mediaEntries
    }

    private fun createImageViewerElement(message: MessageInfo): ImageElement? {
        return when (message.messageType) {
            MessageType.IMAGE -> createImageViewerImageElement(message)
            MessageType.VIDEO -> createImageViewerVideoElement(message)
            else -> null
        }
    }

    private fun createImageViewerImageElement(message: MessageInfo): ImageElement? {
        val payload = message.messagePayload as? ImageMessagePayload
        val localOriginPath = payload?.originalImagePath?.takeIf { it.isNotBlank() }
        val originUrl = payload?.originalImageURL?.takeIf { it.isNotBlank() }
        val data = localOriginPath ?: originUrl ?: return null
        return ImageElement(
            data = data,
            type = 0,
            width = payload?.originalImageWidth ?: 0,
            height = payload?.originalImageHeight ?: 0,
            stableId = message.msgID
        )
    }

    private fun createImageViewerVideoElement(message: MessageInfo): ImageElement? {
        val payload = message.messagePayload as? VideoMessagePayload ?: return null
        val snapshotSource = payload.videoSnapshotPath?.takeIf { it.isNotBlank() }
            ?: return null
        val localVideoPath = payload.videoPath?.takeIf { path ->
            path.isNotBlank() && canPlayDirectlyByPath(path)
        }
        return ImageElement(
            data = snapshotSource,
            type = 1,
            width = payload.videoSnapshotWidth,
            height = payload.videoSnapshotHeight,
            videoData = localVideoPath,
            stableId = message.msgID
        )
    }

    private companion object {
        const val IMAGE_VIEWER_VIDEO_TYPE = 1
        const val TAG = "MessageListViewModel"
    }
}
