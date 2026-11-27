package io.trtc.tuikit.chat.uikit.components.imagepicker

import android.content.ContentResolver
import android.os.Handler
import android.os.Looper
import android.os.Parcelable
import io.trtc.tuikit.atomicx.albumpicker.AlbumMedia
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

interface ImagePickerListener {
    fun onFinishedSelect(medias: List<AlbumMedia>)
    fun onProgress(media: AlbumMedia, index: Int, progress: Double, error: Boolean = false) {}
    fun onCancel() {}
}

@Parcelize
data class ImagePickerConfig(
    val maxCount: Int = 1,
    var gridCount: Int = 4,
    val primaryColor: Int = -1,
    val showsCameraItem: Boolean = false,
    val compressQuality: AlbumPickerCompressQuality = AlbumPickerCompressQuality.STANDARD,
    val maxOutputFileSizeInMB: Int = 100,
) : Parcelable

object ImagePicker {
    fun pickImages(
        imagePickerConfig: ImagePickerConfig = ImagePickerConfig(),
        listener: ImagePickerListener
    ) {
        MessageInputAlbumPickerActivity.start(
            context = appContext,
            config = AlbumPickerConfig(
                mediaFilter = AlbumPickerMediaFilter.IMAGE_ONLY,
                maxSelectionCount = imagePickerConfig.maxCount,
                itemsPerRow = imagePickerConfig.gridCount,
                showsCameraItem = imagePickerConfig.showsCameraItem,
                style = AlbumPickerStyle.LIKE_WECHAT,
                compressQuality = imagePickerConfig.compressQuality,
                maxOutputFileSizeInMB = imagePickerConfig.maxOutputFileSizeInMB,
            ),
            theme = AlbumPickerTheme().apply {
                if (imagePickerConfig.primaryColor != -1) {
                    currentPrimaryColor = imagePickerConfig.primaryColor
                }
            },
            listener = ImagePickerAlbumListener(listener)
        )
    }
}

private class ImagePickerAlbumListener(
    private val listener: ImagePickerListener
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
        listener.onProgress(albumMedia, index, progress.toDouble(), failed)
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
                    listener.onFinishedSelect(validMedias)
                }
            }
        }, "ImagePickerResolve").start()
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

private fun resolveOriginalMediaPath(media: AlbumMedia): String? {
    media.mediaPath?.takeIf { it.isNotBlank() }?.let { return it }
    val uri = media.uri ?: return null
    if (uri.scheme == ContentResolver.SCHEME_FILE) {
        return uri.path?.takeIf { it.isNotBlank() }
    }
    return FileUtil.copyUriToAppDir(appContext, uri)?.absolutePath
}
