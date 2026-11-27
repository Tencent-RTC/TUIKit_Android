package io.trtc.tuikit.chat.uikit.components.imageviewer
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream

internal object ImageViewerGallerySaver {

    fun saveImageToGallery(context: Context, source: MediaSource): Boolean {
        return saveToGallery(
            context = context,
            source = source,
            collection = MediaCollection.Image
        )
    }

    fun saveVideoToGallery(context: Context, source: MediaSource): Boolean {
        return saveToGallery(
            context = context,
            source = source,
            collection = MediaCollection.Video
        )
    }

    private fun saveToGallery(
        context: Context,
        source: MediaSource,
        collection: MediaCollection
    ): Boolean {
        if (!source.canOpen(context)) {
            return false
        }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveByMediaStore(context, source, collection)
        } else {
            saveByFile(context, source, collection)
        }
    }

    private fun saveByMediaStore(
        context: Context,
        source: MediaSource,
        collection: MediaCollection
    ): Boolean {
        val resolver = context.contentResolver
        val mimeType = source.mimeType(context) ?: collection.defaultMimeType
        val values = ContentValues().apply {
            put(collection.displayNameColumn, source.displayName(context))
            put(collection.mimeTypeColumn, mimeType)
            put(collection.dateAddedColumn, System.currentTimeMillis() / 1000)
            put(collection.dateModifiedColumn, System.currentTimeMillis() / 1000)
            put(collection.relativePathColumn, collection.relativePath(context))
            put(collection.pendingColumn, 1)
        }
        val destinationUri = resolver.insert(collection.contentUri(), values) ?: return false
        val success = copyToUri(context, source, resolver, destinationUri)
        if (!success) {
            resolver.delete(destinationUri, null, null)
            return false
        }
        values.clear()
        values.put(collection.pendingColumn, 0)
        resolver.update(destinationUri, values, null, null)
        MediaScannerConnection.scanFile(context, arrayOf(destinationUri.toString()), arrayOf(mimeType), null)
        return true
    }

    private fun saveByFile(
        context: Context,
        source: MediaSource,
        collection: MediaCollection
    ): Boolean {
        val directory = File(
            Environment.getExternalStoragePublicDirectory(collection.publicDirectory),
            appName(context)
        )
        if (!directory.exists() && !directory.mkdirs()) {
            return false
        }
        val outputFile = resolveUniqueFile(directory, source.displayName(context))
        val success = copyToFile(context, source, outputFile)
        if (!success) {
            return false
        }
        MediaScannerConnection.scanFile(
            context,
            arrayOf(outputFile.absolutePath),
            arrayOf(source.mimeType(context) ?: collection.defaultMimeType),
            null
        )
        return true
    }

    private fun copyToUri(
        context: Context,
        source: MediaSource,
        resolver: ContentResolver,
        destinationUri: Uri
    ): Boolean {
        return runCatching {
            resolver.openOutputStream(destinationUri)?.use { output ->
                BufferedOutputStream(output).use { buffered ->
                    source.openInputStream(context).use { input ->
                        input.copyTo(buffered)
                    }
                }
            } ?: return false
        }.isSuccess
    }

    private fun copyToFile(context: Context, source: MediaSource, destination: File): Boolean {
        return runCatching {
            BufferedOutputStream(FileOutputStream(destination)).use { output ->
                source.openInputStream(context).use { input ->
                    input.copyTo(output)
                }
            }
        }.isSuccess
    }

    private fun resolveUniqueFile(directory: File, displayName: String): File {
        val candidate = File(directory, displayName)
        if (!candidate.exists()) {
            return candidate
        }
        val dotIndex = displayName.lastIndexOf('.')
        val baseName = if (dotIndex > 0) displayName.substring(0, dotIndex) else displayName
        val extension = if (dotIndex > 0) displayName.substring(dotIndex) else ""
        var index = 1
        while (true) {
            val next = File(directory, "$baseName($index)$extension")
            if (!next.exists()) {
                return next
            }
            index++
        }
    }

    private fun appName(context: Context): String {
        val label = context.applicationInfo.loadLabel(context.packageManager)?.toString()
        return label?.takeIf { it.isNotBlank() } ?: DEFAULT_APP_NAME
    }

    sealed class MediaSource {
        abstract fun openInputStream(context: Context): InputStream
        abstract fun displayName(context: Context): String
        abstract fun mimeType(context: Context): String?
        abstract fun canOpen(context: Context): Boolean

        data class FilePath(val path: String) : MediaSource() {
            private val file: File
                get() = File(path.removePrefix(FILE_URI_PREFIX))

            override fun openInputStream(context: Context): InputStream = FileInputStream(file)

            override fun displayName(context: Context): String = file.name.takeIf { it.isNotBlank() }
                ?: DEFAULT_FILE_NAME

            override fun mimeType(context: Context): String? = mimeTypeForName(file.name)

            override fun canOpen(context: Context): Boolean = file.exists() && file.isFile
        }

        data class ContentUri(val uri: Uri) : MediaSource() {
            override fun openInputStream(context: Context): InputStream {
                return context.contentResolver.openInputStream(uri)
                    ?: throw IllegalArgumentException("Unable to open source uri")
            }

            override fun displayName(context: Context): String = uri.lastPathSegment
                ?.substringAfterLast('/')
                ?.takeIf { it.isNotBlank() }
                ?: DEFAULT_FILE_NAME

            override fun mimeType(context: Context): String? = context.contentResolver.getType(uri)
                ?: mimeTypeForName(displayName(context))

            override fun canOpen(context: Context): Boolean {
                return runCatching {
                    context.contentResolver.openInputStream(uri)?.use { true } == true
                }.getOrDefault(false)
            }
        }

        companion object {
            fun from(data: Any?): MediaSource? {
                return when (data) {
                    is File -> FilePath(data.absolutePath)
                    is Uri -> ContentUri(data)
                    is String -> fromString(data)
                    else -> null
                }
            }

            private fun fromString(value: String): MediaSource? {
                val source = value.takeIf { it.isNotBlank() } ?: return null
                return when {
                    source.startsWith(CONTENT_URI_PREFIX) -> ContentUri(Uri.parse(source))
                    source.startsWith(FILE_URI_PREFIX) -> FilePath(source)
                    source.startsWith(HTTP_URI_PREFIX) || source.startsWith(HTTPS_URI_PREFIX) -> null
                    else -> FilePath(source)
                }
            }
        }
    }

    private sealed class MediaCollection(
        val publicDirectory: String,
        val defaultMimeType: String,
        val displayNameColumn: String,
        val mimeTypeColumn: String,
        val dateAddedColumn: String,
        val dateModifiedColumn: String,
        val relativePathColumn: String,
        val pendingColumn: String
    ) {
        abstract fun contentUri(): Uri
        fun relativePath(context: Context): String = "$publicDirectory/${appName(context)}/"

        object Image : MediaCollection(
            publicDirectory = Environment.DIRECTORY_PICTURES,
            defaultMimeType = "image/jpeg",
            displayNameColumn = MediaStore.Images.Media.DISPLAY_NAME,
            mimeTypeColumn = MediaStore.Images.Media.MIME_TYPE,
            dateAddedColumn = MediaStore.Images.Media.DATE_ADDED,
            dateModifiedColumn = MediaStore.Images.Media.DATE_MODIFIED,
            relativePathColumn = MediaStore.Images.Media.RELATIVE_PATH,
            pendingColumn = MediaStore.Images.Media.IS_PENDING
        ) {
            override fun contentUri(): Uri = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        }

        object Video : MediaCollection(
            publicDirectory = Environment.DIRECTORY_MOVIES,
            defaultMimeType = "video/mp4",
            displayNameColumn = MediaStore.Video.Media.DISPLAY_NAME,
            mimeTypeColumn = MediaStore.Video.Media.MIME_TYPE,
            dateAddedColumn = MediaStore.Video.Media.DATE_ADDED,
            dateModifiedColumn = MediaStore.Video.Media.DATE_MODIFIED,
            relativePathColumn = MediaStore.Video.Media.RELATIVE_PATH,
            pendingColumn = MediaStore.Video.Media.IS_PENDING
        ) {
            override fun contentUri(): Uri = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        }
    }

    private fun mimeTypeForName(name: String): String? {
        val extension = MimeTypeMap.getFileExtensionFromUrl(name)
            .takeIf { it.isNotBlank() }
            ?: name.substringAfterLast('.', missingDelimiterValue = "").takeIf { it.isNotBlank() }
        return extension?.lowercase()?.let { MimeTypeMap.getSingleton().getMimeTypeFromExtension(it) }
    }

    private const val DEFAULT_APP_NAME = "App"
    private const val DEFAULT_FILE_NAME = "media"
    private const val FILE_URI_PREFIX = "file://"
    private const val CONTENT_URI_PREFIX = "content://"
    private const val HTTP_URI_PREFIX = "http://"
    private const val HTTPS_URI_PREFIX = "https://"
}
