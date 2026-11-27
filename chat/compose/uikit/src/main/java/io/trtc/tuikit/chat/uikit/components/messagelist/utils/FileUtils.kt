package io.trtc.tuikit.chat.uikit.components.messagelist.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.text.TextUtils
import android.util.Log
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import io.trtc.tuikit.chat.uikit.compose.R
import java.io.File
import java.util.Locale

object FileUtils {
    const val FILE_PROVIDER_AUTH: String = ".MessageList.FileProvider"

    /**
     * Convert bytes to human-readable format (KB/MB/GB)
     * @param bytes File size in bytes
     * @param decimals Number of decimal places to show (default 2)
     * @return Formatted string like "1.23 MB"
     */
    fun formatFileSize(bytes: Long?, decimals: Int = 2): String {
        if (bytes == null) return "0 B"
        if (bytes <= 0) return "0 B"

        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val base = 1024.0
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(base)).toInt().coerceAtMost(units.size - 1)

        return "%.${decimals}f ${units[digitGroups]}".format(bytes / Math.pow(base, digitGroups.toDouble()))
    }

    fun getUriFromPath(context: Context, path: String): Uri? {
        try {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                FileProvider.getUriForFile(
                    context,
                    context.applicationInfo.packageName + FILE_PROVIDER_AUTH,
                    File(path)
                )
            } else {
                Uri.fromFile(File(path))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    fun openFile(context: Context, path: String, fileName: String?) {
        val uri = getUriFromPath(context, path)
        if (uri == null) {
            Log.e("FileUtil", "openFile failed , uri is null")
            return
        }
        val fileExtension: String?
        if (fileName.isNullOrEmpty()) {
            fileExtension = getFileExtensionFromUrl(path)
        } else {
            fileExtension = getFileExtensionFromUrl(fileName)
        }
        val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(fileExtension)
        val intent = Intent(Intent.ACTION_VIEW)
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        intent.setDataAndType(uri, mimeType)
        try {
            val chooserIntent =
                Intent.createChooser(intent, context.getString(R.string.message_list_open_file_tips))
            chooserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooserIntent)
        } catch (e: java.lang.Exception) {
            Log.e("FileUtil", "openFile failed , " + e.message)
        }
    }

    fun getFileExtensionFromUrl(url: String): String {
        var url = url
        if (!TextUtils.isEmpty(url)) {
            val fragment = url.lastIndexOf('#')
            if (fragment > 0) {
                url = url.substring(0, fragment)
            }

            val query = url.lastIndexOf('?')
            if (query > 0) {
                url = url.substring(0, query)
            }

            val filenamePos = url.lastIndexOf('/')
            val filename = if (0 <= filenamePos) url.substring(filenamePos + 1) else url

            // if the filename contains special characters, we don't
            // consider it valid for our matching purposes:

            //          if (!filename.isEmpty() && Pattern.matches("[a-zA-Z_0-9\\.\\-\\(\\)\\%]+", filename))
            if (!filename.isEmpty()) {
                val dotPos = filename.lastIndexOf('.')
                if (0 <= dotPos) {
                    return filename.substring(dotPos + 1).lowercase(Locale.getDefault())
                }
            }
        }

        return ""
    }

}