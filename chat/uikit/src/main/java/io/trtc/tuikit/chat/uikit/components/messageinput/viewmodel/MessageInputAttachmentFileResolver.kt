package io.trtc.tuikit.chat.uikit.components.messageinput.viewmodel

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import io.trtc.tuikit.chat.uikit.components.common.FileUtil
import java.io.File
import java.io.IOException

internal class MessageInputAttachmentFileResolver {
    sealed class ResolvedFile {
        data class Success(
            val filePath: String,
            val fileName: String,
            val fileSize: Long
        ) : ResolvedFile()

        object FileTooLarge : ResolvedFile()

        object Failure : ResolvedFile()
    }

    fun getPathFromUri(context: Context, uri: Uri): String {
        val path = try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
                getPathByCopyFile(context, uri)
            } else {
                getRealFilePath(context, uri)
            }
        } catch (exception: Exception) {
            Log.e(TAG, "getPathFromUri error: ${exception.message}")
            null
        }
        return path.orEmpty()
    }

    fun resolveFileForSend(
        context: Context,
        uri: Uri,
        maxFileSizeBytes: Long
    ): ResolvedFile {
        val declaredSize = getDeclaredFileSize(context, uri)
        if (declaredSize != null && declaredSize > maxFileSizeBytes) {
            return ResolvedFile.FileTooLarge
        }
        return when (val result = copyUriToCache(context, uri, maxFileSizeBytes)) {
            is CopyUriResult.Success -> {
                val fileSize = result.file.length()
                if (fileSize > maxFileSizeBytes) {
                    result.file.delete()
                    ResolvedFile.FileTooLarge
                } else {
                    ResolvedFile.Success(
                        filePath = result.file.absolutePath,
                        fileName = result.file.name,
                        fileSize = fileSize
                    )
                }
            }
            CopyUriResult.FileTooLarge -> ResolvedFile.FileTooLarge
            CopyUriResult.Failure -> ResolvedFile.Failure
        }
    }

    fun getFileName(context: Context, uri: Uri): String? = FileUtil.getFileName(context, uri)

    fun getFileName(filePath: String?): String? {
        if (filePath == null) {
            return null
        }
        val index = filePath.lastIndexOf('/')
        return filePath.substring(index + 1)
    }

    fun getFileSize(path: String): Long {
        val file = File(path)
        return if (file.exists()) file.length() else 0
    }

    fun getFileExtensionFromUrl(url: String): String = FileUtil.getFileExtensionFromUrl(url)

    private fun getRealFilePath(context: Context, uri: Uri?): String? {
        if (uri == null) {
            return null
        }
        return when (uri.scheme) {
            null -> uri.path
            ContentResolver.SCHEME_FILE -> uri.path
            ContentResolver.SCHEME_CONTENT -> {
                val cursor = context.contentResolver.query(
                    uri,
                    arrayOf(android.provider.MediaStore.Images.ImageColumns.DATA),
                    null,
                    null,
                    null
                )
                cursor?.use {
                    if (it.moveToFirst()) {
                        val index = it.getColumnIndex(
                            android.provider.MediaStore.Images.ImageColumns.DATA
                        )
                        if (index > -1) {
                            return it.getString(index)
                        }
                    }
                }
                null
            }

            else -> null
        }
    }

    private fun getPathByCopyFile(context: Context, uri: Uri): String? {
        return when (val result = copyUriToCache(context, uri, Long.MAX_VALUE)) {
            is CopyUriResult.Success -> result.file.absolutePath
            CopyUriResult.FileTooLarge, CopyUriResult.Failure -> null
        }
    }

    private fun copyUriToCache(
        context: Context,
        uri: Uri,
        maxFileSizeBytes: Long
    ): CopyUriResult {
        val rawFileName = getFileName(context, uri) ?: return CopyUriResult.Failure
        val fileName = MessageInputFileCopyGuard.sanitizeFileName(rawFileName) ?: return CopyUriResult.Failure
        val cacheDir = getDocumentCacheDir(context)
        val file = generateFileName(fileName, cacheDir) ?: return CopyUriResult.Failure
        val inputStream = try {
            context.contentResolver.openInputStream(uri)
        } catch (_: IOException) {
            null
        } catch (_: SecurityException) {
            null
        }
        if (inputStream == null) {
            file.delete()
            return CopyUriResult.Failure
        }
        return when (MessageInputFileCopyGuard.copyWithLimit(inputStream, file, maxFileSizeBytes)) {
            MessageInputFileCopyResult.SUCCESS -> CopyUriResult.Success(file)
            MessageInputFileCopyResult.TOO_LARGE -> CopyUriResult.FileTooLarge
            MessageInputFileCopyResult.FAILED -> CopyUriResult.Failure
            else -> CopyUriResult.Failure
        }
    }

    fun getDeclaredFileSize(context: Context, uri: Uri): Long? {
        if (uri.scheme == ContentResolver.SCHEME_FILE) {
            return uri.path?.let { path -> File(path).takeIf { it.exists() }?.length() }
        }
        if (uri.scheme != ContentResolver.SCHEME_CONTENT) {
            return null
        }
        val openableColumnSize = try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeIndex >= 0) {
                        cursor.getLong(sizeIndex).takeIf { it >= 0L }
                    } else {
                        null
                    }
                } else {
                    null
                }
            }
        } catch (_: Exception) {
            null
        }
        if (openableColumnSize != null) {
            return openableColumnSize
        }
        return try {
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { descriptor ->
                descriptor.length.takeIf { it >= 0L }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun getDocumentCacheDir(context: Context): File {
        val directory = File(context.cacheDir, DOCUMENTS_DIR)
        if (!directory.exists()) {
            directory.mkdirs()
        }
        return directory
    }

    private fun generateFileName(name: String, directory: File): File? {
        var actualName = name
        var file = File(directory, actualName)
        if (file.exists()) {
            val dotIndex = actualName.lastIndexOf('.')
            val fileName = if (dotIndex > 0) {
                actualName.substring(0, dotIndex)
            } else {
                actualName
            }
            val extension = if (dotIndex > 0) {
                actualName.substring(dotIndex)
            } else {
                ""
            }
            var index = 0
            while (file.exists()) {
                index++
                actualName = "$fileName($index)$extension"
                file = File(directory, actualName)
            }
        }
        return try {
            if (!file.createNewFile()) {
                null
            } else {
                file
            }
        } catch (_: IOException) {
            null
        }
    }

    private companion object {
        const val TAG = "MsgInput.FileResolver"
        const val DOCUMENTS_DIR = "documents"
    }

    private sealed class CopyUriResult {
        data class Success(val file: File) : CopyUriResult()
        object FileTooLarge : CopyUriResult()
        object Failure : CopyUriResult()
    }
}
