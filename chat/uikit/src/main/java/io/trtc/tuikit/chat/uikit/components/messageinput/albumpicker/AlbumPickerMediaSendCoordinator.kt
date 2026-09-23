package io.trtc.tuikit.chat.uikit.components.messageinput.albumpicker
import android.content.Context
import io.trtc.tuikit.atomicx.albumpicker.AlbumMedia
import io.trtc.tuikit.atomicx.albumpicker.AlbumMediaType
import io.trtc.tuikit.atomicx.albumpicker.AlbumPickerCompressQuality
import io.trtc.tuikit.atomicx.albumpicker.AlbumPickerConfig
import io.trtc.tuikit.atomicx.albumpicker.AlbumPickerListener
import io.trtc.tuikit.atomicx.albumpicker.AlbumPickerMediaFilter
import io.trtc.tuikit.atomicx.albumpicker.AlbumPickerStyle
import io.trtc.tuikit.atomicx.albumpicker.AlbumPickerTheme
import io.trtc.tuikit.atomicx.theme.ThemeStore
import io.trtc.tuikit.chat.uikit.components.messageinput.utils.MessageInputAlbumPickerActivity
import java.util.Collections
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal class AlbumPickerMediaSendCoordinator(
    private val conversationID: String,
    private val onSendMedia: (AlbumMedia, String) -> Unit,
    private val onSendText: (String) -> Unit,
    private val onSendFailed: () -> Unit
) : AlbumPickerListener {

    private data class PlaceholderState(
        val placeholderID: String,
        val mediaType: AlbumMediaType,
        val videoDurationSeconds: Int,
        var thumbnailPath: String?,
        var isThumbnailFinal: Boolean,
        var thumbnailWidth: Int,
        var thumbnailHeight: Int,
        var mediaPath: String?,
        var progress: Int
    )

    private val sessionID = UUID.randomUUID().toString()
    private val placeholderStates = ConcurrentHashMap<String, PlaceholderState>()
    private val sentMediaIds: MutableSet<ULong> =
        Collections.newSetFromMap(ConcurrentHashMap<ULong, Boolean>())

    @Volatile
    private var pendingText: String? = null
    private val pendingTextSent = AtomicBoolean(false)
    private val isFinished = AtomicBoolean(false)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var thumbnailLoader: AlbumPickerThumbnailLoader? = null

    fun showAlbumPicker(context: Context): Boolean {
        if (conversationID.isEmpty() || !isPickerVisible.compareAndSet(false, true)) {
            return false
        }
        activeCoordinators.add(this)
        thumbnailLoader = AlbumPickerThumbnailLoader(context.applicationContext)
        val config = AlbumPickerConfig(
            mediaFilter = AlbumPickerMediaFilter.ALL,
            maxSelectionCount = MAX_SELECTION_COUNT,
            style = AlbumPickerStyle.LIKE_WECHAT,
            compressQuality = AlbumPickerCompressQuality.STANDARD,
            maxVideoDurationInSeconds = MAX_VIDEO_DURATION_SECONDS,
            maxOutputFileSizeInMB = MAX_OUTPUT_FILE_SIZE_MB
        )
        val theme = AlbumPickerTheme(
            currentPrimaryColor = ThemeStore.shared(context)
                .themeState.value.currentTheme.tokens.color.buttonColorPrimaryDefault
        )
        MessageInputAlbumPickerActivity.start(
            context = context,
            config = config,
            theme = theme,
            listener = this,
            onDismissed = { isPickerVisible.set(false) }
        )
        return true
    }

    override fun onPickConfirm(pickedAlbumMedias: List<AlbumMedia>, textMessage: String?) {
        pendingText = textMessage
        pickedAlbumMedias.forEach { media ->
            insertPlaceholder(media)
        }
    }

    override fun onMediaProcessing(albumMedia: AlbumMedia, progress: Float, error: Boolean) {
        if (error) {
            markSendFailed(albumMedia)
            return
        }
        refreshThumbnailIfNeeded(albumMedia)
        if (progress < COMPLETED_PROGRESS) {
            updatePlaceholderProgress(albumMedia, progress)
            return
        }
        val path = albumMedia.mediaPath
        if (!path.isNullOrEmpty()) {
            sendOnce(albumMedia) {
                markPlaceholderSending(albumMedia, path)
                onSendMedia(albumMedia, path)
            }
            return
        }
        markSendFailed(albumMedia)
    }

    override fun onMediaProcessed() {
        sendPendingTextIfNeeded()
        finish()
    }

    override fun onCancel() {
        finish()
    }

    private fun insertPlaceholder(media: AlbumMedia) {
        val placeholderID = makePlaceholderID(media.id)
        placeholderStates[placeholderID] = PlaceholderState(
            placeholderID = placeholderID,
            mediaType = media.mediaType,
            videoDurationSeconds = (media.duration / MILLIS_PER_SECOND_FLOAT).roundToInt(),
            thumbnailPath = null,
            isThumbnailFinal = false,
            thumbnailWidth = 0,
            thumbnailHeight = 0,
            mediaPath = null,
            progress = 0
        )
        applyPlaceholder(placeholderID)
        requestThumbnail(media)
    }

    private fun requestThumbnail(media: AlbumMedia) {
        val placeholderID = makePlaceholderID(media.id)
        thumbnailLoader?.loadThumbnail(media) { path, width, height, isFinal ->
            updateThumbnail(placeholderID, path, width, height, isFinal)
        }
    }

    private fun refreshThumbnailIfNeeded(media: AlbumMedia) {
        val placeholderID = makePlaceholderID(media.id)
        val state = placeholderStates[placeholderID] ?: return
        synchronized(state) {
            if (state.isThumbnailFinal) {
                return
            }
        }
        val loader = thumbnailLoader ?: return
        if (loader.localThumbnailPath(media) == null) {
            return
        }
        loader.loadThumbnail(media) { path, width, height, isFinal ->
            updateThumbnail(placeholderID, path, width, height, isFinal)
        }
    }

    private fun updateThumbnail(placeholderID: String, path: String?, width: Int, height: Int, isFinal: Boolean) {
        val state = placeholderStates[placeholderID] ?: return
        synchronized(state) {
            if (state.isThumbnailFinal) {
                return
            }
            if (path != null) {
                state.thumbnailPath = path
            }
            if (width > 0 && height > 0) {
                state.thumbnailWidth = width
                state.thumbnailHeight = height
            }
            state.isThumbnailFinal = isFinal && path != null
        }
        applyPlaceholder(placeholderID)
    }

    private fun updatePlaceholderProgress(media: AlbumMedia, progress: Float) {
        val placeholderID = makePlaceholderID(media.id)
        val state = placeholderStates[placeholderID] ?: return
        val percent = (progress.coerceIn(0f, COMPLETED_PROGRESS) * PROGRESS_PERCENT_SCALE).roundToInt()
        synchronized(state) {
            if (percent - state.progress < PROGRESS_UPDATE_STEP) {
                return
            }
            state.progress = percent
        }
        applyPlaceholder(placeholderID)
    }

    private fun markPlaceholderSending(media: AlbumMedia, path: String) {
        val placeholderID = makePlaceholderID(media.id)
        val state = placeholderStates[placeholderID] ?: return
        synchronized(state) {
            state.mediaPath = path
        }
        applyPlaceholder(placeholderID)
    }

    private fun applyPlaceholder(placeholderID: String) {
        val state = placeholderStates[placeholderID] ?: return
        val info = synchronized(state) {
            AlbumPickerPlaceholderInfo(
                placeholderID = state.placeholderID,
                mediaType = state.mediaType,
                thumbnailPath = state.thumbnailPath,
                thumbnailWidth = state.thumbnailWidth,
                thumbnailHeight = state.thumbnailHeight,
                mediaPath = state.mediaPath,
                videoDurationSeconds = state.videoDurationSeconds,
                progress = state.progress
            )
        }
        AlbumPickerProcessingMessageStore.upsert(conversationID, info)
    }

    private fun sendOnce(media: AlbumMedia, action: () -> Unit) {
        if (sentMediaIds.add(media.id)) {
            action()
        }
    }

    private fun markSendFailed(media: AlbumMedia) {
        if (sentMediaIds.add(media.id)) {
            removePlaceholder(media)
            onSendFailed()
        }
    }

    private fun removePlaceholder(media: AlbumMedia) {
        val placeholderID = makePlaceholderID(media.id)
        if (placeholderStates.remove(placeholderID) != null) {
            AlbumPickerProcessingMessageStore.remove(conversationID, placeholderID)
        }
    }

    private fun sendPendingTextIfNeeded() {
        val text = pendingText
        if (!text.isNullOrEmpty() && pendingTextSent.compareAndSet(false, true)) {
            onSendText(text)
        }
    }

    private fun finish() {
        if (!isFinished.compareAndSet(false, true)) {
            return
        }
        cleanupPlaceholders()
        activeCoordinators.remove(this)
    }

    private fun cleanupPlaceholders() {
        val states = placeholderStates.values.toList()
        placeholderStates.clear()
        states.filter { state ->
            synchronized(state) { state.mediaPath == null }
        }.forEach { state ->
            AlbumPickerProcessingMessageStore.remove(conversationID, state.placeholderID)
        }
        val sendingIDs = states.filter { state ->
            synchronized(state) { state.mediaPath != null }
        }.map { it.placeholderID }
        if (sendingIDs.isEmpty()) {
            return
        }
        scope.launch {
            delay(PLACEHOLDER_CLEANUP_DELAY_MS)
            sendingIDs.forEach { placeholderID ->
                AlbumPickerProcessingMessageStore.remove(conversationID, placeholderID)
            }
        }
    }

    private fun makePlaceholderID(mediaID: ULong): String {
        return "$PLACEHOLDER_ID_PREFIX${sessionID}_$mediaID"
    }

    companion object {
        private val isPickerVisible = AtomicBoolean(false)
        private val activeCoordinators: MutableSet<AlbumPickerMediaSendCoordinator> =
            Collections.newSetFromMap(ConcurrentHashMap<AlbumPickerMediaSendCoordinator, Boolean>())

        private const val MAX_SELECTION_COUNT = 9
        private const val MAX_VIDEO_DURATION_SECONDS = 600
        private const val MAX_OUTPUT_FILE_SIZE_MB = 100
        private const val COMPLETED_PROGRESS = 1.0f
        private const val PROGRESS_PERCENT_SCALE = 100f
        private const val PROGRESS_UPDATE_STEP = 5
        private const val PLACEHOLDER_CLEANUP_DELAY_MS = 30000L
        private const val PLACEHOLDER_ID_PREFIX = "album_picker_processing_"
        private const val MILLIS_PER_SECOND_FLOAT = 1000f
    }
}
