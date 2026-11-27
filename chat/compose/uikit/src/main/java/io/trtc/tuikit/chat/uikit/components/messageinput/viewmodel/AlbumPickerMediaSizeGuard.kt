package io.trtc.tuikit.chat.uikit.components.messageinput.viewmodel

import io.trtc.tuikit.atomicx.albumpicker.AlbumMediaType

internal object AlbumPickerMediaSizeGuard {
    fun maxSizeBytes(
        mediaType: AlbumMediaType,
        mimeType: String?,
        fileName: String?
    ): Long {
        if (mediaType == AlbumMediaType.VIDEO) {
            return VIDEO_MAX_SIZE.toLong()
        }
        return if (isGifImage(mimeType, fileName)) {
            GIF_IMAGE_MAX_SIZE.toLong()
        } else {
            IMAGE_MAX_SIZE.toLong()
        }
    }

    fun isTooLarge(
        fileSize: Long,
        mediaType: AlbumMediaType,
        mimeType: String?,
        fileName: String?
    ): Boolean {
        return fileSize > maxSizeBytes(mediaType, mimeType, fileName)
    }

    private fun isGifImage(mimeType: String?, fileName: String?): Boolean {
        if (mimeType.equals(GIF_MIME_TYPE, ignoreCase = true)) {
            return true
        }
        return fileName
            ?.substringAfterLast('.', missingDelimiterValue = "")
            ?.equals(GIF_EXTENSION, ignoreCase = true) == true
    }

    private const val GIF_MIME_TYPE = "image/gif"
    private const val GIF_EXTENSION = "gif"
}
