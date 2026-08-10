package io.trtc.tuikit.chat.uikit.components.filepicker.util

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import io.trtc.tuikit.chat.uikit.components.common.FileUtil
import java.io.File
import java.io.IOException
import java.io.InputStream

object FilePickerUtils {

    fun getFileName(context: Context, uri: Uri): String? = FileUtil.getFileName(context, uri)

    fun getFileSize(context: Context, uri: Uri): Long = FileUtil.getFileSize(context, uri)

    fun getMimeType(context: Context, uri: Uri): String? {
        var mimeType: String? = context.contentResolver.getType(uri)

        if (mimeType == null) {
            val extension = MimeTypeMap.getFileExtensionFromUrl(uri.toString())
            if (extension != null) {
                mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.lowercase())
            }
        }

        return mimeType
    }

    fun copyFileToAppDir(context: Context, uri: Uri, directory: String = "files"): File? =
        FileUtil.copyUriToAppDir(context, uri, directory)

    fun countBytes(inputStream: InputStream): Long {
        val buffer = ByteArray(8 * 1024)
        var total = 0L
        while (true) {
            val read = inputStream.read(buffer)
            if (read == -1) {
                break
            }
            total += read
        }
        return total
    }

    fun sanitizeFileName(fileName: String): String? = FileUtil.sanitizeFileName(fileName)

    fun readTextFromUri(context: Context, uri: Uri): String? {
        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                return inputStream.bufferedReader().use { it.readText() }
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return null
    }

    fun formatFileSize(size: Long): String = FileUtil.formatFileSize(size)
}
