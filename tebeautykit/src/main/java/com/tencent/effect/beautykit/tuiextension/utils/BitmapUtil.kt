package com.tencent.effect.beautykit.tuiextension.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Bitmap.CompressFormat
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.text.TextUtils
import com.tencent.effect.beautykit.utils.FileUtil
import com.tencent.effect.beautykit.utils.LogUtils
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException

object BitmapUtil {

    private val TAG = BitmapUtil::class.java.name

    @JvmStatic
    fun saveBitmap(bitmap: Bitmap, filePath: String, compress: Int): Boolean {
        return saveBitmap(bitmap, CompressFormat.JPEG, filePath, compress)
    }

    @JvmStatic
    fun saveBitmap(bitmap: Bitmap, format: CompressFormat, filePath: String, compress: Int): Boolean {
        val f = File(filePath)
        if (f.exists()) {
            f.delete()
        }
        val parent = f.parentFile
        if (parent != null && !parent.exists()) {
            parent.mkdirs()
        }
        return try {
            f.createNewFile()
            val out = FileOutputStream(f)
            bitmap.compress(format, compress, out)
            out.flush()
            out.close()
            true
        } catch (e: FileNotFoundException) {
            LogUtils.e(TAG, "saveBitmap FileNotFoundException " + e.message)
            false
        } catch (e: IOException) {
            LogUtils.e(TAG, "saveBitmap IOException " + e.message)
            false
        }
    }

    @JvmStatic
    fun scaleBitmap(source: Bitmap?, scale: Float, needRecycle: Boolean): Bitmap? {
        var resizedBmp: Bitmap? = null
        if (source != null && !source.isRecycled) {
            val matrix = Matrix()
            matrix.postScale(scale, scale)
            try {
                resizedBmp = Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
                if (resizedBmp !== source && needRecycle) {
                    source.recycle()
                }
            } catch (e: OutOfMemoryError) {
                e.printStackTrace()
                resizedBmp = source
            }
        }
        return resizedBmp
    }

    @JvmStatic
    fun getCompressFormat(imagePath: String?): CompressFormat? {
        if (TextUtils.isEmpty(imagePath)) {
            return null
        }
        val path = imagePath!!.lowercase()
        return when {
            path.endsWith(".png") -> CompressFormat.PNG
            path.endsWith(".jpeg") || path.endsWith(".jpg") -> CompressFormat.JPEG
            path.endsWith(".webp") -> CompressFormat.WEBP
            else -> null
        }
    }

    @JvmStatic
    fun getNameByCompressFormat(compressFormat: CompressFormat?): String? {
        return when (compressFormat) {
            CompressFormat.JPEG -> ".jpg"
            CompressFormat.PNG -> ".png"
            CompressFormat.WEBP -> ".webp"
            else -> null
        }
    }

    @JvmStatic
    fun rotateBitmap(origin: Bitmap?, degrees: Float): Bitmap? {
        if (origin == null) {
            return null
        }
        if (degrees == 0f) {
            return origin
        }
        val width = origin.width
        val height = origin.height
        val matrix = Matrix()
        matrix.setRotate(degrees)
        val newBM = Bitmap.createBitmap(origin, 0, 0, width, height, matrix, false)
        if (newBM == origin) {
            return newBM
        }
        origin.recycle()
        return newBM
    }

    private fun getInSampleSize(options: BitmapFactory.Options, reqWidth: Double, reqHeight: Double): Int {
        return try {
            val height = options.outHeight.toDouble()
            val width = options.outWidth.toDouble()
            if (height > reqHeight || width > reqWidth) {
                val heightRatio = Math.ceil(height / reqHeight).toInt()
                val widthRatio = Math.ceil(width / reqWidth).toInt()
                maxOf(heightRatio, widthRatio)
            } else {
                1
            }
        } catch (e: Exception) {
            1
        }
    }

    private fun compressImage(context: Context, imgPath: String): String {
        val options = BitmapFactory.Options()
        options.inJustDecodeBounds = true
        BitmapFactory.decodeFile(imgPath, options)
        var maxHeight = 3840
        var maxWidth = 2160
        if (options.outWidth > options.outHeight) {
            maxHeight = 2160
            maxWidth = 3840
        }
        val sampleSize = getInSampleSize(options, maxWidth.toDouble(), maxHeight.toDouble())
        val degree = com.tencent.xmagic.util.FileUtil.readImgAngle(imgPath)
        LogUtils.d(TAG, "compressImage ,sampleSize = $sampleSize , degree = $degree  imgPath= $imgPath")
        return if (sampleSize == 1) {
            if (degree == 0) {
                imgPath
            } else {
                saveImageToFile(context, imgPath, rotateBitmap(BitmapFactory.decodeFile(imgPath), degree.toFloat()))
            }
        } else {
            options.inJustDecodeBounds = false
            options.inSampleSize = sampleSize
            val bitmap = BitmapFactory.decodeFile(imgPath, options)
            saveImageToFile(context, imgPath, rotateBitmap(bitmap, degree.toFloat()))
        }
    }

    private fun saveImageToFile(context: Context, imgPath: String, bitmap: Bitmap?): String {
        val directory = context.filesDir.absolutePath + File.separator + "capture_avatar"
        val folder = File(directory)
        if (!folder.exists()) {
            folder.mkdirs()
        }
        val compressFormat = getCompressFormat(imgPath)
        val filePath = directory + File.separator + FileUtil.getMD5(imgPath) + getNameByCompressFormat(compressFormat)
        LogUtils.d(TAG, "roteImgAndSave , newPath $filePath")
        val result = saveBitmap(bitmap!!, compressFormat!!, filePath, 100)
        LogUtils.d(TAG, "roteImgAndSave , save bitmap result  $result")
        return if (result) filePath else imgPath
    }

    @JvmStatic
    fun compressImage(context: Context, imgPath: String, callBack: CompressImageCallBack) {
        if (!File(imgPath).exists()) {
            return
        }
        Thread {
            val compressPath = compressImage(context, imgPath)
            callBack.onCompressed(compressPath)
        }.start()
    }

    fun interface CompressImageCallBack {
        fun onCompressed(imgPath: String)
    }
}
