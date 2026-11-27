package io.trtc.tuikit.chat.uikit.components.imageviewer

import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import io.trtc.tuikit.chat.uikit.components.common.ContextProvider
import io.trtc.tuikit.chat.uikit.components.imageviewer.ui.ImageViewerActivity
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.MutableStateFlow

interface EventHandler {
    fun onEvent(eventData: Map<String, Any>, callback: (Any?) -> Unit)
}

data class ImageElement(
    val data: Any?,
    val type: Int = 0,
    val width: Int = 0,
    val height: Int = 0,
    val videoData: Any? = null,
    val stableId: String? = null
) {
    constructor(
        data: Any?,
        type: Int = 0,
        width: Int = 0,
        height: Int = 0,
        videoData: Any? = null
    ) : this(
        data = data,
        type = type,
        width = width,
        height = height,
        videoData = videoData,
        stableId = null
    )
}

object ImageViewer {
    private const val TAG = "ImageViewer"

    const val EXTRA_SESSION_ID = "io.trtc.tuikit.chat.uikit.components.imageviewer.SESSION_ID"

    const val EVENT_IMAGE_TAP = "onImageTap"
    const val EVENT_LOAD_MORE_OLDER = "onLoadMoreOlder"
    const val EVENT_LOAD_MORE_NEWER = "onLoadMoreNewer"
    const val EVENT_DOWNLOAD_VIDEO = "onDownloadVideo"
    const val EVENT_SAVE_MEDIA = "onSaveMedia"

    class Session internal constructor(
        val id: Long,
        internal val eventHandler: EventHandler?,
        internal val initDataInternal: ImageElement?,
        internal val mediaListFlow: MutableStateFlow<List<ImageElement>>
    ) {
        private val closeListeners = mutableSetOf<() -> Unit>()
        internal val downloadingKeys = mutableSetOf<String>()
        internal val savingKeys = mutableSetOf<String>()
        internal val videoOverrides = mutableMapOf<String, Any>()
        internal var isClosed: Boolean = false
            private set

        fun submitMediaList(list: List<ImageElement>) {
            if (isClosed) {
                return
            }
            mediaListFlow.value = list
        }

        fun addOnClosedListener(listener: () -> Unit): () -> Unit {
            synchronized(closeListeners) {
                closeListeners.add(listener)
            }
            return {
                synchronized(closeListeners) {
                    closeListeners.remove(listener)
                }
            }
        }

        internal fun markClosed() {
            if (isClosed) {
                return
            }
            isClosed = true
            val listeners = synchronized(closeListeners) {
                closeListeners.toList().also { closeListeners.clear() }
            }
            listeners.forEach { listener -> listener.invoke() }
        }
    }

    private val sessionIdGenerator = AtomicLong(0L)
    private val sessions = mutableMapOf<Long, Session>()
    private var latestGlobalSessionId: Long? = null

    var eventHandler: EventHandler? = null
        private set
    val mediaList: MutableStateFlow<List<ImageElement>> = MutableStateFlow(emptyList())
    var initDataInternal: ImageElement? = null
        private set

    @JvmStatic
    fun view(
        imageElements: List<ImageElement>,
        initialIndex: Int = 0,
        onEventTriggered: EventHandler? = null
    ): Session? {
        val context = runCatching { ContextProvider.appContext }.getOrNull() ?: return null
        val sessionId = sessionIdGenerator.incrementAndGet()
        val initIndex = ImageViewerSessionState.coerceInitialIndex(initialIndex, imageElements.size)
        val initElement = imageElements.getOrNull(initIndex)
        val session = Session(
            id = sessionId,
            eventHandler = onEventTriggered,
            initDataInternal = initElement,
            mediaListFlow = MutableStateFlow(imageElements)
        )
        synchronized(sessions) {
            sessions[sessionId] = session
        }

        eventHandler = onEventTriggered
        initDataInternal = initElement
        mediaList.value = imageElements
        latestGlobalSessionId = sessionId

        val intent = Intent(context, ImageViewerActivity::class.java).apply {
            putExtra(EXTRA_SESSION_ID, sessionId)
            if (context is Application) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
        if (context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY) == null) {
            releaseSession(sessionId)
            Log.w(TAG, "No activity can handle ImageViewer intent")
            return null
        }
        return try {
            context.startActivity(intent)
            session
        } catch (e: ActivityNotFoundException) {
            releaseSession(sessionId)
            Log.e(TAG, "Failed to start ImageViewerActivity", e)
            null
        }
    }

    internal fun acquireSession(sessionId: Long): Session? {
        return synchronized(sessions) { sessions[sessionId] }
    }

    internal fun releaseSession(sessionId: Long) {
        val session = synchronized(sessions) { sessions.remove(sessionId) }
        session?.markClosed()
        if (latestGlobalSessionId == sessionId) {
            clearRuntimeState()
        }
    }

    internal fun clearRuntimeState() {
        eventHandler = null
        initDataInternal = null
        mediaList.value = emptyList()
        latestGlobalSessionId = null
    }
}
