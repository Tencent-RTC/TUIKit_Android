package io.trtc.tuikit.chat.uikit.components.imageviewer.utils

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.text.TextUtils
import android.util.Log
import android.webkit.MimeTypeMap
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.Locale


object FileUtils {
    private const val TAG = "FileUtils"

    fun saveVideoToGallery(context: Context, videoPath: String): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveVideoToGalleryByMediaStore(context, videoPath)
        } else {
            saveVideoToGalleryByFile(context, videoPath)
        }
    }

    fun saveImageToGallery(context: Context, imagePath: String): Boolean {
        val processedImagePath: String = detectImageTypeAndGetNewPath(imagePath)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveImageToGalleryByMediaStore(context, processedImagePath)
        } else {
            saveImageToGalleryByFile(context, processedImagePath)
        }
    }

    fun saveVideoToGalleryByMediaStore(context: Context?, videoPath: String): Boolean {
        if (TextUtils.isEmpty(videoPath) || context == null) {
            Log.e(TAG, "param invalid")
            return false
        }

        val videoFileName = getFileName(videoPath)
        val videoMimeType = getMimeType(videoPath)
        val now = System.currentTimeMillis()
        val videoImageValues = ContentValues()
        videoImageValues.put(MediaStore.Video.Media.DATE_ADDED, now / 1000)
        videoImageValues.put(MediaStore.Video.Media.DATE_MODIFIED, now / 1000)
        videoImageValues.put(MediaStore.Video.Media.DISPLAY_NAME, videoFileName)
        videoImageValues.put(MediaStore.Video.Media.MIME_TYPE, videoMimeType)
        // insert to gallery
        val videoExternalContentUri = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        videoImageValues.put(MediaStore.Video.Media.IS_PENDING, 1)
        videoImageValues.put(
            MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/" + getAppName(context) + "/"
        )

        val resolver = context.contentResolver
        val uri: Uri?
        try {
            uri = resolver.insert(videoExternalContentUri, videoImageValues)
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "saveVideoToGalleryByMediaStore failed, " + e.message)
            return false
        }
        if (uri == null) {
            return false
        }
        // got permission, write file to public media dir
        val saveSuccess = saveFileToUri(resolver, uri, videoPath)
        if (!saveSuccess) {
            resolver.delete(uri, null, null)
            return false
        }
        videoImageValues.clear()
        videoImageValues.put(MediaStore.Images.Media.IS_PENDING, 0)
        resolver.update(uri, videoImageValues, null, null)

        // update system gallery
        MediaScannerConnection.scanFile(context, arrayOf(uri.toString()), arrayOf(videoMimeType), null)
        return true
    }

    fun saveVideoToGalleryByFile(context: Context, videoPath: String): Boolean {
        val videoDirPath = (Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES).toString() + "/"
                + getAppName(context) + "/")
        val dir = File(videoDirPath)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        val videoName = getFileName(videoPath)
        val outputPath = videoDirPath + videoName
        val outputFile = File(outputPath)
        if (outputFile.exists()) {
            outputFile.delete()
        }
        val isSuccess = saveFileFromPath(outputFile, videoPath)
        if (!isSuccess) {
            return false
        }
        val videoMimeType = getMimeType(videoPath)
        MediaScannerConnection.scanFile(context, arrayOf(outputPath), arrayOf(videoMimeType), null)
        return true
    }

    fun saveImageToGalleryByMediaStore(context: Context?, imagePath: String): Boolean {
        if (TextUtils.isEmpty(imagePath) || context == null) {
            Log.e(TAG, "param invalid")
            return false
        }

        val imageFileName = getFileName(imagePath)
        val imageMimeType = getMimeType(imagePath)
        val now = System.currentTimeMillis()

        val newImageValues = ContentValues()
        newImageValues.put(MediaStore.Images.Media.DATE_ADDED, now / 1000)
        newImageValues.put(MediaStore.Images.Media.DATE_MODIFIED, now / 1000)
        newImageValues.put(MediaStore.Images.Media.DISPLAY_NAME, imageFileName)
        newImageValues.put(MediaStore.Images.Media.MIME_TYPE, imageMimeType)
        // insert to gallery
        val imageExternalContentUri = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        newImageValues.put(MediaStore.Images.Media.IS_PENDING, 1)
        newImageValues.put(
            MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/" + getAppName(context) + "/"
        )

        val resolver = context.contentResolver
        val uri: Uri?
        try {
            uri = resolver.insert(imageExternalContentUri, newImageValues)
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "saveImageToGalleryByMediaStore failed, " + e.message)
            return false
        }
        if (uri == null) {
            return false
        }
        // got permission, write file to public media dir
        val saveSuccess = saveFileToUri(resolver, uri, imagePath)
        if (!saveSuccess) {
            resolver.delete(uri, null, null)
            return false
        }
        newImageValues.clear()
        newImageValues.put(MediaStore.Images.Media.IS_PENDING, 0)
        resolver.update(uri, newImageValues, null, null)

        // update system gallery
        MediaScannerConnection.scanFile(context, arrayOf(uri.toString()), arrayOf(imageMimeType), null)
        return true
    }

    fun saveImageToGalleryByFile(context: Context, imagePath: String): Boolean {
        val imageDirPath =
            (Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).toString() + "/"
                    + getAppName(context) + "/")
        val dir = File(imageDirPath)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        val imageName = getFileName(imagePath)
        val outputPath = imageDirPath + imageName
        val outputFile = File(outputPath)
        if (outputFile.exists()) {
            outputFile.delete()
        }
        val isSuccess = saveFileFromPath(outputFile, imagePath)
        if (!isSuccess) {
            return false
        }
        val imageMimeType = getMimeType(imagePath)
        MediaScannerConnection.scanFile(context, arrayOf(outputPath), arrayOf(imageMimeType), null)
        return true
    }

    fun getFileName(path: String): String {
        val filenamePos = path.lastIndexOf('/')
        return if (0 <= filenamePos) path.substring(filenamePos + 1) else path
    }

    fun saveFileToUri(contentResolver: ContentResolver, destinationUri: Uri, srcPath: String?): Boolean {
        var `is`: InputStream? = null
        var bos: BufferedOutputStream? = null
        try {
            bos = BufferedOutputStream(contentResolver.openOutputStream(destinationUri))
            `is` = FileInputStream(srcPath)
            val buf = ByteArray(1024)

            var actualBytes: Int
            while ((`is`.read(buf).also { actualBytes = it }) != -1) {
                bos.write(buf, 0, actualBytes)
            }
        } catch (e: IOException) {
            return false
        } finally {
            try {
                `is`?.close()
                bos?.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
        return true
    }

    fun saveFileFromPath(desFile: File?, srcFilePath: String?): Boolean {
        var `is`: InputStream? = null
        var bos: BufferedOutputStream? = null
        try {
            bos = BufferedOutputStream(FileOutputStream(desFile))
            `is` = FileInputStream(srcFilePath)
            val buf = ByteArray(1024)

            var actualBytes: Int
            while ((`is`.read(buf).also { actualBytes = it }) != -1) {
                bos.write(buf, 0, actualBytes)
            }
        } catch (e: IOException) {
            return false
        } finally {
            try {
                `is`?.close()
                bos?.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
        return true
    }

    private fun getAppName(context: Context): String {
        var appName = ""
        val packageManager = context.packageManager
        try {
            val applicationInfo =
                packageManager.getApplicationInfo(context.packageName, PackageManager.GET_META_DATA)
            packageManager.getApplicationLabel(applicationInfo)
            val labelCharSequence = applicationInfo.loadLabel(packageManager)
            if (!TextUtils.isEmpty(labelCharSequence)) {
                appName = labelCharSequence.toString()
            }
        } catch (e: PackageManager.NameNotFoundException) {
            Log.e(TAG, "getAppName exception:" + e.message)
        }

        return appName
    }

    fun isFileExists(path: String): Boolean {
        try {
            val file = File(path)
            return file.exists() && file.isFile
        } catch (e: java.lang.Exception) {
            return false
        }
    }


    fun detectImageTypeAndGetNewPath(imagePath: String): String {
        val mimeType = getMimeType(imagePath)
        var processedImagePath = imagePath

        if (TextUtils.isEmpty(mimeType) || !mimeType!!.startsWith("image")) {
            val file = File(imagePath)
            var fileInputStream: FileInputStream? = null
            try {
                fileInputStream = FileInputStream(file)


                
                val header = ByteArray(12)
                val read = fileInputStream.read(header, 0, 12)
                if (read > 0) {
                    val imageType = detectImageType(header)

                    if (imageType == ImageType.UNKNOWN) {
                        // Not one of the following image formats : GIF/JPEG/RAW/PNG/WEBP
                    } else if (imageType == ImageType.GIF) {
                        processedImagePath = "$imagePath.gif"
                        val processedImageFile = File(processedImagePath)
                        saveFileFromPath(processedImageFile, imagePath)
                    } else {
                        
                        val bitmap = BitmapFactory.decodeFile(imagePath)
                        if (bitmap != null) {
                            processedImagePath = "$imagePath.png"
                            val processedImageFile = File(processedImagePath)
                            storeBitmap(processedImageFile, bitmap)
                            bitmap.recycle()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, e.message ?: "detectImageTypeAndGetNewPath failed")
                return imagePath
            } finally {
                try {
                    fileInputStream?.close()
                } catch (e: IOException) {
                    Log.e(TAG, e.message ?: "detectImageTypeAndGetNewPath failed")
                }
            }
        }
        return processedImagePath
    }

    
    private enum class ImageType {
        GIF, JPEG, PNG, WEBP, UNKNOWN
    }

    
    private fun detectImageType(header: ByteArray): ImageType {
        // GIF 87a
        if (header[0] == 'G'.code.toByte() && header[1] == 'I'.code.toByte() && header[2] == 'F'.code.toByte() && header[3] == '8'.code.toByte() &&
            (header[4] == '7'.code.toByte() || header[4] == '9'.code.toByte()) && header[5] == 'a'.code.toByte()
        ) {
            return ImageType.GIF
        }


        // JPEG
        if (header[0] == 0xFF.toByte() && header[1] == 0xD8.toByte()) {
            return ImageType.JPEG
        }


        // PNG
        if (header[0] == 0x89.toByte() && header[1] == 'P'.code.toByte() && header[2] == 'N'.code.toByte() && header[3] == 'G'.code.toByte()) {
            return ImageType.PNG
        }

        // WEBP
        if (header[0] == 'R'.code.toByte() && header[1] == 'I'.code.toByte() && header[2] == 'F'.code.toByte() && header[3] == 'F'.code.toByte() && header[8] == 'W'.code.toByte() && header[9] == 'E'.code.toByte() && header[10] == 'B'.code.toByte() && header[11] == 'P'.code.toByte()) {
            return ImageType.WEBP
        }

        return ImageType.UNKNOWN
    }

    
    private fun getMimeType(filePath: String): String? {
        val extension: String = getFileExtensionFromUrl(filePath)
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.lowercase(Locale.getDefault()))
    }

    fun storeBitmap(outFile: File, bitmap: Bitmap): File {
        if (!outFile.exists() || outFile.isDirectory) {
            outFile.parentFile.mkdirs()
        }
        var fOut: FileOutputStream? = null
        try {
            outFile.deleteOnExit()
            outFile.createNewFile()
            fOut = FileOutputStream(outFile)
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fOut)
            fOut.flush()
        } catch (e1: IOException) {
            outFile.deleteOnExit()
        } finally {
            if (null != fOut) {
                try {
                    fOut.close()
                } catch (e: IOException) {
                    e.printStackTrace()
                    outFile.deleteOnExit()
                }
            }
        }
        return outFile
    }


    // fix the problem that getFileExtensionFromUrl does not support Chinese
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