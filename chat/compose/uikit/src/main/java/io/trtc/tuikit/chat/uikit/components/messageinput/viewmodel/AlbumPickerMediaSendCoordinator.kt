package io.trtc.tuikit.chat.uikit.components.messageinput.viewmodel

import io.trtc.tuikit.atomicx.albumpicker.AlbumMedia
import io.trtc.tuikit.atomicx.albumpicker.AlbumPickerListener
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt

internal class AlbumPickerMediaSendCoordinator(
    private val onProcessingStarted: (AlbumMedia) -> Unit,
    private val onProcessingProgress: (AlbumMedia, Int) -> Unit,
    private val onProcessingFinished: (AlbumMedia) -> Unit,
    private val onSendProcessedMedia: (AlbumMedia, String) -> Unit,
    private val onSendOriginalMedia: (AlbumMedia) -> Unit,
    private val onSendText: (String) -> Unit,
    private val shouldProcessMedia: (AlbumMedia) -> Boolean = { true },
    private val onMediaRejected: (AlbumMedia) -> Unit = {}
) : AlbumPickerListener {
    private val sentMediaIds = ConcurrentHashMap.newKeySet<ULong>()
    private val rejectedMediaIds = ConcurrentHashMap.newKeySet<ULong>()
    private val pendingTextSent = AtomicBoolean(false)
    private var pendingText: String? = null

    override fun onPickConfirm(
        pickedAlbumMedias: List<AlbumMedia>,
        textMessage: String?
    ) {
        pendingText = textMessage
        pickedAlbumMedias.forEach { media ->
            if (shouldProcessMedia(media)) {
                onProcessingStarted(media)
            } else {
                rejectedMediaIds.add(media.id)
                onMediaRejected(media)
            }
        }
    }

    override fun onMediaProcessing(
        albumMedia: AlbumMedia,
        progress: Float,
        error: Boolean
    ) {
        if (rejectedMediaIds.contains(albumMedia.id)) {
            return
        }
        if (error) {
            sendOnce(albumMedia) {
                onProcessingFinished(albumMedia)
                onSendOriginalMedia(albumMedia)
            }
            return
        }

        if (progress < COMPLETED_PROGRESS) {
            onProcessingProgress(albumMedia, progress.toPercent())
            return
        }

        val path = albumMedia.mediaPath
        if (path.isNullOrBlank()) {
            sendOnce(albumMedia) {
                onProcessingFinished(albumMedia)
                onSendOriginalMedia(albumMedia)
            }
            return
        }
        sendOnce(albumMedia) {
            onProcessingFinished(albumMedia)
            onSendProcessedMedia(albumMedia, path)
        }
    }

    override fun onMediaProcessed() {
        val text = pendingText
        if (!text.isNullOrEmpty() && pendingTextSent.compareAndSet(false, true)) {
            onSendText(text)
        }
    }

    override fun onCancel() {}

    private fun sendOnce(albumMedia: AlbumMedia, action: () -> Unit) {
        if (sentMediaIds.add(albumMedia.id)) {
            action()
        }
    }

    private fun Float.toPercent(): Int {
        return (coerceIn(0f, COMPLETED_PROGRESS) * 100).roundToInt()
    }

    private companion object {
        const val COMPLETED_PROGRESS = 1.0f
    }
}
