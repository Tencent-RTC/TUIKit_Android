package io.trtc.tuikit.chat.uikit.components.messageinput.albumpicker
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import io.trtc.tuikit.atomicx.albumpicker.AlbumMedia
import io.trtc.tuikit.atomicx.albumpicker.AlbumMediaType
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

internal class AlbumPickerThumbnailLoader(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun loadThumbnail(
        media: AlbumMedia,
        onUpdate: (path: String?, width: Int, height: Int, isFinal: Boolean) -> Unit
    ) {
        val localPath = localThumbnailPath(media)
        if (localPath != null) {
            val size = readImageSize(localPath)
            onUpdate(localPath, size?.first ?: 0, size?.second ?: 0, true)
            return
        }
        scope.launch {
            when (media.mediaType) {
                AlbumMediaType.VIDEO -> loadVideoThumbnail(media, onUpdate)
                else -> loadImageThumbnail(media, onUpdate)
            }
        }
    }

    fun localThumbnailPath(media: AlbumMedia): String? {
        if (media.mediaType == AlbumMediaType.VIDEO) {
            return media.videoThumbnailPath?.takeIf { isReadableFile(it) }
        }
        return media.mediaPath?.takeIf { isReadableFile(it) }
    }

    private fun loadVideoThumbnail(
        media: AlbumMedia,
        onUpdate: (path: String?, width: Int, height: Int, isFinal: Boolean) -> Unit
    ) {
        val videoPath = media.mediaPath?.takeIf { isReadableFile(it) }
        val bitmap = if (videoPath != null) {
            extractVideoFrame(videoPath)
        } else {
            media.uri?.let { extractVideoFrame(it) }
        }
        val path = bitmap?.let { writeThumbnail(it, media.id) }
        val size = path?.let { readImageSize(it) }
        onUpdate(path, size?.first ?: 0, size?.second ?: 0, true)
    }

    private fun loadImageThumbnail(
        media: AlbumMedia,
        onUpdate: (path: String?, width: Int, height: Int, isFinal: Boolean) -> Unit
    ) {
        val uri = media.uri
        if (uri == null) {
            onUpdate(null, 0, 0, true)
            return
        }
        val bitmap = runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input, null, bounds)
            }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                return@runCatching null
            }
            val sampleSize = calculateSampleSize(bounds.outWidth, bounds.outHeight)
            val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            context.contentResolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input, null, decodeOptions)
            }
        }.getOrNull()
        val path = bitmap?.let { writeThumbnail(it, media.id) }
        val size = path?.let { readImageSize(it) }
        onUpdate(path, size?.first ?: 0, size?.second ?: 0, true)
    }

    private fun extractVideoFrame(videoPath: String): Bitmap? {
        return runCatching {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(videoPath)
                retriever.getFrameAtTime(0)
            } finally {
                retriever.release()
            }
        }.getOrNull()
    }

    private fun extractVideoFrame(uri: Uri): Bitmap? {
        return runCatching {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, uri)
                retriever.getFrameAtTime(0)
            } finally {
                retriever.release()
            }
        }.getOrNull()
    }

    private fun writeThumbnail(bitmap: Bitmap, mediaID: ULong): String? {
        return runCatching {
            val directory = File(context.cacheDir, THUMBNAIL_DIRECTORY_NAME)
            if (!directory.exists()) {
                directory.mkdirs()
            }
            val destination = File(directory, "$mediaID.jpg")
            FileOutputStream(destination).use { output ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, THUMBNAIL_COMPRESS_QUALITY, output)
            }
            destination.absolutePath
        }.getOrNull()
    }

    private fun readImageSize(path: String): Pair<Int, Int>? {
        return runCatching {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, options)
            if (options.outWidth > 0 && options.outHeight > 0) {
                options.outWidth to options.outHeight
            } else {
                null
            }
        }.getOrNull()
    }

    private fun calculateSampleSize(width: Int, height: Int): Int {
        var sampleSize = 1
        val maxSide = maxOf(width, height)
        while (maxSide / (sampleSize * SAMPLE_SIZE_MULTIPLIER) > THUMBNAIL_MAX_PIXEL) {
            sampleSize *= SAMPLE_SIZE_MULTIPLIER
        }
        return sampleSize
    }

    private fun isReadableFile(path: String?): Boolean {
        return !path.isNullOrEmpty() && File(path).exists()
    }

    companion object {
        private const val THUMBNAIL_DIRECTORY_NAME = "album_picker_processing_thumbnail"
        private const val THUMBNAIL_MAX_PIXEL = 720
        private const val THUMBNAIL_COMPRESS_QUALITY = 80
        private const val SAMPLE_SIZE_MULTIPLIER = 2
    }
}
