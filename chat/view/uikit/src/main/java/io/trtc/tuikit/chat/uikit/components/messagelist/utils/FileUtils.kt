package io.trtc.tuikit.chat.uikit.components.messagelist.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import io.trtc.tuikit.chat.uikit.R
import io.trtc.tuikit.chat.uikit.components.common.FileUtil
import java.io.File

object FileUtils {
    private const val TAG = "MessageListFileUtils"
    private const val FILE_PROVIDER_AUTH = ".MessageList.FileProvider"

    fun openFile(context: Context, path: String, fileName: String?) {
        val uri = getUriFromPath(context, path) ?: run {
            Log.e(TAG, "openFile failed, uri is null")
            return
        }
        val fileExtension = if (fileName.isNullOrEmpty()) {
            FileUtil.getFileExtensionFromUrl(path)
        } else {
            FileUtil.getFileExtensionFromUrl(fileName)
        }
        val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(fileExtension)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            setDataAndType(uri, mimeType)
        }
        try {
            val chooserIntent = Intent.createChooser(
                intent,
                context.getString(R.string.message_list_open_file_tips)
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooserIntent)
        } catch (exception: Exception) {
            Log.e(TAG, "openFile failed, ${exception.message}", exception)
        }
    }

    private fun getUriFromPath(context: Context, path: String): Uri? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                FileProvider.getUriForFile(
                    context,
                    context.applicationInfo.packageName + FILE_PROVIDER_AUTH,
                    File(path)
                )
            } else {
                Uri.fromFile(File(path))
            }
        } catch (exception: Exception) {
            Log.e(TAG, "getUriFromPath failed, ${exception.message}", exception)
            null
        }
    }
}
