package io.trtc.tuikit.chat.uikit.components.imageviewer
internal object ImageViewerMediaIdentity {

    fun keyFor(element: ImageElement): String? {
        val stableId = element.stableId?.takeIf { it.isNotBlank() }
        if (stableId != null) {
            return "stable:$stableId"
        }
        val data = element.data?.toString()?.takeIf { it.isNotBlank() } ?: return null
        return "data:${element.type}:$data"
    }

    fun sameMedia(first: ImageElement, second: ImageElement): Boolean {
        val firstKey = keyFor(first)
        val secondKey = keyFor(second)
        return if (firstKey != null && secondKey != null) {
            firstKey == secondKey
        } else {
            first == second
        }
    }
}

internal object ImageViewerSessionState {

    fun coerceInitialIndex(initialIndex: Int, itemCount: Int): Int {
        return if (itemCount <= 0) {
            0
        } else {
            initialIndex.coerceIn(0, itemCount - 1)
        }
    }

    fun findBestIndex(
        mediaList: List<ImageElement>,
        previousItem: ImageElement?,
        previousIndex: Int
    ): Int {
        if (mediaList.isEmpty()) {
            return 0
        }
        if (previousItem != null) {
            val previousKey = ImageViewerMediaIdentity.keyFor(previousItem)
            val keyIndex = previousKey?.let { key ->
                mediaList.indexOfFirst { item -> ImageViewerMediaIdentity.keyFor(item) == key }
            } ?: -1
            if (keyIndex >= 0) {
                return keyIndex
            }
            val valueIndex = mediaList.indexOfFirst { item ->
                ImageViewerMediaIdentity.sameMedia(item, previousItem)
            }
            if (valueIndex >= 0) {
                return valueIndex
            }
        }
        return previousIndex.coerceIn(0, mediaList.lastIndex)
    }
}

internal class ImageViewerBoundaryLoadState {
    private val lastTriggeredListSizeByEvent = mutableMapOf<String, Int>()
    private val loadingEvents = mutableSetOf<String>()

    fun eventsForPosition(position: Int, itemCount: Int): List<String> {
        if (itemCount <= 0 || position !in 0 until itemCount) {
            return emptyList()
        }
        return buildList {
            if (position == 0) {
                add(ImageViewer.EVENT_LOAD_MORE_OLDER)
            }
            if (position == itemCount - 1) {
                add(ImageViewer.EVENT_LOAD_MORE_NEWER)
            }
        }
    }

    fun tryStart(
        eventName: String,
        itemCount: Int,
        hasHandler: Boolean,
        allowSameItemCount: Boolean = false
    ): Boolean {
        if (!hasHandler || isLoading(eventName)) {
            return false
        }
        if (!allowSameItemCount && lastTriggeredListSizeByEvent[eventName] == itemCount) {
            return false
        }
        lastTriggeredListSizeByEvent[eventName] = itemCount
        loadingEvents.add(eventName)
        return true
    }

    fun finish(eventName: String) {
        loadingEvents.remove(eventName)
    }

    fun finishAll() {
        loadingEvents.clear()
    }

    fun isLoading(eventName: String): Boolean {
        return loadingEvents.contains(eventName)
    }

    fun hasActiveLoad(): Boolean {
        return loadingEvents.isNotEmpty()
    }
}

internal class ImageViewerBoundaryLoadPolicy {
    private var dragStartBoundaryPosition: Int? = null
    private var dragStartItemCount: Int = 0

    fun markDragStarted(position: Int, itemCount: Int) {
        if (isBoundaryPosition(position, itemCount)) {
            dragStartBoundaryPosition = position
            dragStartItemCount = itemCount
        } else {
            clearPendingDrag()
        }
    }

    fun shouldDispatchBoundaryLoad(position: Int, itemCount: Int): Boolean {
        val shouldDispatch = dragStartBoundaryPosition == position &&
            dragStartItemCount == itemCount &&
            isBoundaryPosition(position, itemCount)
        clearPendingDrag()
        return shouldDispatch
    }

    private fun clearPendingDrag() {
        dragStartBoundaryPosition = null
        dragStartItemCount = 0
    }

    private fun isBoundaryPosition(position: Int, itemCount: Int): Boolean {
        return itemCount > 0 && position in 0 until itemCount && (position == 0 || position == itemCount - 1)
    }
}

internal data class ImageViewerSafeAreaInsets(
    val left: Int = 0,
    val top: Int = 0,
    val right: Int = 0,
    val bottom: Int = 0
)

internal object ImageViewerOverlayInsetPolicy {
    fun bottomMargin(baseBottom: Int, insets: ImageViewerSafeAreaInsets, extraBottom: Int = 0): Int {
        return baseBottom + insets.bottom + extraBottom
    }

    fun endMargin(baseEnd: Int, insets: ImageViewerSafeAreaInsets, isRtl: Boolean): Int {
        return baseEnd + if (isRtl) insets.left else insets.right
    }
}

internal enum class ImageViewerVideoContentMode {
    SnapshotPreview,
    PlayerPreview
}

internal enum class ImageViewerVideoTapAction {
    None,
    RequestDownload
}

internal enum class ImageViewerVideoCloseAction {
    ExitViewer
}

internal object ImageViewerVideoPlaybackPolicy {
    fun contentMode(hasVideoData: Boolean): ImageViewerVideoContentMode {
        return if (hasVideoData) {
            ImageViewerVideoContentMode.PlayerPreview
        } else {
            ImageViewerVideoContentMode.SnapshotPreview
        }
    }

    fun centerTapAction(isDownloading: Boolean, hasVideoData: Boolean): ImageViewerVideoTapAction {
        return when {
            isDownloading -> ImageViewerVideoTapAction.None
            hasVideoData -> ImageViewerVideoTapAction.None
            else -> ImageViewerVideoTapAction.RequestDownload
        }
    }

    fun closeAction(): ImageViewerVideoCloseAction {
        return ImageViewerVideoCloseAction.ExitViewer
    }
}

internal object ImageViewerVideoPageReleasePolicy {
    fun shouldRelease(holderPosition: Int, currentPosition: Int): Boolean {
        return holderPosition >= 0 && holderPosition != currentPosition
    }
}

internal object ImageViewerVideoPageRefreshPolicy {
    const val MEDIA_TYPE_VIDEO = 1

    fun shouldRefreshOnPageSelected(itemType: Int): Boolean {
        return itemType == MEDIA_TYPE_VIDEO
    }
}
