package io.trtc.tuikit.chat.uikit.components.messageinput.utils
import android.graphics.Bitmap
import android.graphics.Color
import android.media.MediaMetadataRetriever
import android.os.Build

object VideoFrameExtractor {

    data class VideoFrameInfo(
        val bitmap: Bitmap,
        val durationMs: Long
    )

    fun extractVideoFrameInfo(filePath: String): VideoFrameInfo? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(filePath)
            val bitmap = extractNonBlackFrame(retriever) ?: return null
            val durationMs = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_DURATION
            )?.toLongOrNull() ?: 0L
            VideoFrameInfo(bitmap, durationMs)
        } catch (_: Exception) {
            null
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
            }
        }
    }

    private fun extractNonBlackFrame(retriever: MediaMetadataRetriever): Bitmap? {
        val firstFrame = retriever.frameAtTime
        if (firstFrame != null && !isBitmapBlack(firstFrame)) {
            return firstFrame
        }

        val durationMs = retriever.extractMetadata(
            MediaMetadataRetriever.METADATA_KEY_DURATION
        )?.toLongOrNull() ?: 0L
        val durationUs = durationMs * 1000
        var seekTimeUs = 200_000L
        while (seekTimeUs <= durationUs && seekTimeUs <= 5_000_000L) {
            val frame = retriever.getFrameAtTime(
                seekTimeUs,
                MediaMetadataRetriever.OPTION_CLOSEST
            )
            if (frame != null && !isBitmapBlack(frame)) {
                firstFrame?.takeIf { it != frame && !it.isRecycled }?.recycle()
                return frame
            }
            frame?.takeIf { it != firstFrame && !it.isRecycled }?.recycle()
            seekTimeUs *= 2
        }
        return firstFrame
    }

    private fun isBitmapBlack(bitmap: Bitmap): Boolean {
        val targetBitmap = if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
            && bitmap.config == Bitmap.Config.HARDWARE
        ) {
            bitmap.copy(Bitmap.Config.ARGB_8888, false) ?: return false
        } else {
            bitmap
        }

        val width = targetBitmap.width
        val height = targetBitmap.height
        val step = maxOf(1, minOf(width, height) / 10)
        var sampleCount = 0
        var blackCount = 0
        for (x in step / 2 until width step step) {
            for (y in step / 2 until height step step) {
                val pixel = targetBitmap.getPixel(x, y)
                if (Color.red(pixel) < 25 && Color.green(pixel) < 25 && Color.blue(pixel) < 25) {
                    blackCount++
                }
                sampleCount++
            }
        }

        if (targetBitmap !== bitmap && !targetBitmap.isRecycled) {
            targetBitmap.recycle()
        }
        return sampleCount > 0 && blackCount.toFloat() / sampleCount > 0.9f
    }
}
