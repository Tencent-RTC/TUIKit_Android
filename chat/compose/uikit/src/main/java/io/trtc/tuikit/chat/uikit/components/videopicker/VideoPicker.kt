package io.trtc.tuikit.chat.uikit.components.videopicker

import android.content.ContentResolver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.Parcelable
import io.trtc.tuikit.atomicx.albumpicker.AlbumMedia
import io.trtc.tuikit.atomicx.albumpicker.AlbumMediaType
import io.trtc.tuikit.atomicx.albumpicker.AlbumPickerCompressQuality
import io.trtc.tuikit.atomicx.albumpicker.AlbumPickerConfig
import io.trtc.tuikit.atomicx.albumpicker.AlbumPickerListener
import io.trtc.tuikit.atomicx.albumpicker.AlbumPickerMediaFilter
import io.trtc.tuikit.atomicx.albumpicker.AlbumPickerStyle
import io.trtc.tuikit.atomicx.albumpicker.AlbumPickerTheme
import io.trtc.tuikit.chat.uikit.components.common.FileUtil
import io.trtc.tuikit.chat.uikit.components.common.appContext
import io.trtc.tuikit.chat.uikit.components.messageinput.utils.MessageInputAlbumPickerActivity
import kotlinx.parcelize.Parcelize
import java.util.concurrent.atomic.AtomicReference

data class VideoPickerModel(
    var id: ULong,
    val uri: Uri? = null,
    val mediaPath: String? = null,
    val mediaType: AlbumMediaType = AlbumMediaType.IMAGE,
    val videoThumbnailPath: String? = null,
    val duration: Long = 0,
)

interface VideoPickerListener {
    fun onFinishedSelect(medias: List<VideoPickerModel>)
    fun onProgress(model: VideoPickerModel, index: Int, progress: Double, error: Boolean = false) {}
    fun onCancel() {}
}

@Parcelize
data class VideoPickerConfig(
    val maxCount: Int = 1,
    var gridCount: Int = 4,
    val primaryColor: Int = -1,
    val showsCameraItem: Boolean = false,
    val compressQuality: AlbumPickerCompressQuality = AlbumPickerCompressQuality.STANDARD,
    val maxVideoDurationInSeconds: Int = 600,
    val maxOutputFileSizeInMB: Int = 100,
) : Parcelable

object VideoPicker {
    fun pickVideos(
        videoPickerConfig: VideoPickerConfig = VideoPickerConfig(),
        listener: VideoPickerListener
    ) {
        MessageInputAlbumPickerActivity.start(
            context = appContext,
            config = AlbumPickerConfig(
                mediaFilter = AlbumPickerMediaFilter.VIDEO_ONLY,
                maxSelectionCount = videoPickerConfig.maxCount,
                itemsPerRow = videoPickerConfig.gridCount,
                showsCameraItem = videoPickerConfig.showsCameraItem,
                style = AlbumPickerStyle.LIKE_WECHAT,
                compressQuality = videoPickerConfig.compressQuality,
                maxVideoDurationInSeconds = videoPickerConfig.maxVideoDurationInSeconds,
                maxOutputFileSizeInMB = videoPickerConfig.maxOutputFileSizeInMB,
            ),
            theme = AlbumPickerTheme().apply {
                if (videoPickerConfig.primaryColor != -1) {
                    currentPrimaryColor = videoPickerConfig.primaryColor
                }
            },
            listener = VideoPickerAlbumListener(listener)
        )
    }
}

private class VideoPickerAlbumListener(
    private val listener: VideoPickerListener
) : AlbumPickerListener {
    private var pickedMedias: List<AlbumMedia> = emptyList()
    private val deliveryState = AtomicReference(DeliveryState.OPEN)
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onPickConfirm(pickedAlbumMedias: List<AlbumMedia>, textMessage: String?) {
        pickedMedias = pickedAlbumMedias
    }

    override fun onMediaProcessing(albumMedia: AlbumMedia, progress: Float, error: Boolean) {
        if (deliveryState.get() != DeliveryState.OPEN) {
            return
        }
        val index = pickedMedias.indexOfFirst { it.id == albumMedia.id }.coerceAtLeast(0)
        val failed = error || (progress >= COMPLETED_PROGRESS && albumMedia.mediaPath.isNullOrBlank())
        listener.onProgress(albumMedia.toVideoPickerModel(), index, progress.toDouble(), failed)
    }

    override fun onMediaProcessed() {
        if (!deliveryState.compareAndSet(DeliveryState.OPEN, DeliveryState.SETTLED)) {
            return
        }
        Thread({
            val validMedias = collectValidMedias()
            mainHandler.post {
                if (validMedias.isEmpty()) {
                    listener.onCancel()
                } else {
                    listener.onFinishedSelect(validMedias.map { it.toVideoPickerModel() })
                }
            }
        }, "VideoPickerResolve").start()
    }

    override fun onCancel() {
        if (!deliveryState.compareAndSet(DeliveryState.OPEN, DeliveryState.CANCELLED)) {
            return
        }
        listener.onCancel()
    }

    private fun collectValidMedias(): List<AlbumMedia> {
        return pickedMedias.mapNotNull { media ->
            val path = resolveOriginalMediaPath(media) ?: return@mapNotNull null
            media.mediaPath = path
            media
        }
    }

    private companion object {
        const val COMPLETED_PROGRESS = 1.0f
    }
}

private enum class DeliveryState {
    OPEN,
    SETTLED,
    CANCELLED,
}

private fun AlbumMedia.toVideoPickerModel(): VideoPickerModel {
    return VideoPickerModel(
        id = id,
        uri = uri,
        mediaPath = mediaPath,
        mediaType = mediaType,
        videoThumbnailPath = videoThumbnailPath,
        duration = duration,
    )
}

private fun resolveOriginalMediaPath(media: AlbumMedia): String? {
    media.mediaPath?.takeIf { it.isNotBlank() }?.let { return it }
    val uri = media.uri ?: return null
    if (uri.scheme == ContentResolver.SCHEME_FILE) {
        return uri.path?.takeIf { it.isNotBlank() }
    }
    return FileUtil.copyUriToAppDir(appContext, uri)?.absolutePath
}
