package io.trtc.tuikit.chat.uikit.components.messageinput.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.media.ExifInterface
import android.os.Build.VERSION.SDK_INT
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import coil3.ImageLoader
import coil3.gif.AnimatedImageDecoder
import coil3.gif.GifDecoder
import io.trtc.tuikit.chat.uikit.components.messageinput.utils.FileUtils.generateFileName
import io.trtc.tuikit.chat.uikit.components.messageinput.utils.FileUtils.getDocumentCacheDir
import io.trtc.tuikit.chat.uikit.components.messageinput.utils.FileUtils.getFileName
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

object ImageUtils {
    private var _imageLoader: ImageLoader? = null

    @Composable
    fun getImageLoader(): ImageLoader {
        val context = LocalContext.current
        return getImageLoader(context)
    }

    fun getImageLoader(context: Context): ImageLoader {
        return _imageLoader ?: ImageLoader.Builder(context)
            .components {
                if (SDK_INT >= 28) {
                    add(AnimatedImageDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
            }
            .build().also { _imageLoader = it }
    }
}

object ImageUtil {
    const val SP_IMAGE: String = "_conversation_group_face"

    /**
     * @param outFile
     * @param bitmap
     * @return
     */
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

    /**
     *
     * Read the rotation angle of the image
     */
    fun getBitmapDegree(fileName: String): Int {
        var degree = 0
        try {
            val exifInterface = ExifInterface(fileName)
            val orientation =
                exifInterface.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> degree = 90
                ExifInterface.ORIENTATION_ROTATE_180 -> degree = 180
                ExifInterface.ORIENTATION_ROTATE_270 -> degree = 270
                else -> {}
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return degree
    }

    /**
     *
     *
     * Rotate the image by an angle
     *
     * @param bm     image to be rotated
     * @param degree Rotation angle
     * @return rotated image
     */
    fun rotateBitmapByDegree(bm: Bitmap, degree: Int): Bitmap {
        var returnBm: Bitmap? = null

        val matrix = Matrix()
        matrix.postRotate(degree.toFloat())
        try {
            returnBm = Bitmap.createBitmap(bm, 0, 0, bm.width, bm.height, matrix, true)
        } catch (e: OutOfMemoryError) {
            e.printStackTrace()
        }
        if (returnBm == null) {
            returnBm = bm
        }
        if (bm != returnBm) {
            bm.recycle()
        }
        return returnBm
    }

    fun getImageSize(path: String): IntArray {
        val size = IntArray(2)
        try {
            val onlyBoundsOptions = BitmapFactory.Options()
            onlyBoundsOptions.inJustDecodeBounds = true
            BitmapFactory.decodeFile(path, onlyBoundsOptions)
            val originalWidth = onlyBoundsOptions.outWidth
            val originalHeight = onlyBoundsOptions.outHeight

            val degree = getBitmapDegree(path)
            if (degree == 0) {
                size[0] = originalWidth
                size[1] = originalHeight
            } else {
                var hh = 800f
                var ww = 480f
                if (degree == 90 || degree == 270) {
                    hh = 480f
                    ww = 800f
                }
                var be = 1
                if (originalWidth > originalHeight && originalWidth > ww) {
                    be = (originalWidth / ww).toInt()
                } else if (originalWidth < originalHeight && originalHeight > hh) {
                    be = (originalHeight / hh).toInt()
                }
                if (be <= 0) {
                    be = 1
                }
                val bitmapOptions = BitmapFactory.Options()
                bitmapOptions.inSampleSize = be
                bitmapOptions.inDither = true
                bitmapOptions.inPreferredConfig = Bitmap.Config.ARGB_8888
                var bitmap = BitmapFactory.decodeFile(path, bitmapOptions)
                bitmap = rotateBitmapByDegree(bitmap, degree)
                size[0] = bitmap.width
                size[1] = bitmap.height
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return size
    }

    // The image file is rotated locally, and the path of the image file after rotation is returned.
    fun getImagePathAfterRotate(context: Context, imagePath: String): String {
        try {
            val originBitmap = BitmapFactory.decodeFile(imagePath, null)
            val degree = getBitmapDegree(imagePath)
            if (degree == 0) {
                return imagePath
            } else {
                val newBitmap = rotateBitmapByDegree(originBitmap, degree)
                val oldName = getFileName(imagePath)
                val newImageFile = generateFileName(
                    oldName,
                    getDocumentCacheDir(context)
                )
                    ?: return imagePath
                storeBitmap(newImageFile, newBitmap)
                newBitmap.recycle()
                return newImageFile.absolutePath
            }
        } catch (e: Exception) {
            return imagePath
        }
    }

    /**
     *
     * Convert image to circle
     *
     * @param bitmap   Pass in a Bitmap object
     * @return
     */
    fun toRoundBitmap(bitmap: Bitmap): Bitmap {
        var width = bitmap.width
        var height = bitmap.height
        val roundPx: Float
        val left: Float
        val top: Float
        val right: Float
        val bottom: Float
        val dstLeft: Float
        val dstTop: Float
        val dstRight: Float
        val dstBottom: Float
        if (width <= height) {
            roundPx = (width / 2).toFloat()
            left = 0f
            top = 0f
            right = width.toFloat()
            bottom = width.toFloat()
            height = width
            dstLeft = 0f
            dstTop = 0f
            dstRight = width.toFloat()
            dstBottom = width.toFloat()
        } else {
            roundPx = (height / 2).toFloat()
            val clip = ((width - height) / 2).toFloat()
            left = clip
            right = width - clip
            top = 0f
            bottom = height.toFloat()
            width = height
            dstLeft = 0f
            dstTop = 0f
            dstRight = height.toFloat()
            dstBottom = height.toFloat()
        }

        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)

        val color = -0xbdbdbe
        val paint = Paint()
        val src = Rect(left.toInt(), top.toInt(), right.toInt(), bottom.toInt())
        val dst = Rect(dstLeft.toInt(), dstTop.toInt(), dstRight.toInt(), dstBottom.toInt())
        val rectF = RectF(dst)

        paint.isAntiAlias = true

        canvas.drawARGB(0, 0, 0, 0)
        paint.color = color

        // canvas.drawRoundRect(rectF, roundPx, roundPx, paint);
        canvas.drawCircle(roundPx, roundPx, roundPx, paint)

        paint.setXfermode(PorterDuffXfermode(PorterDuff.Mode.SRC_IN))
        canvas.drawBitmap(bitmap, src, dst, paint)

        return output
    }

    fun zoomImg(bm: Bitmap, targetWidth: Int, targetHeight: Int): Bitmap {
        val srcWidth = bm.width
        val srcHeight = bm.height
        val widthScale = targetWidth * 1.0f / srcWidth
        val heightScale = targetHeight * 1.0f / srcHeight
        val matrix = Matrix()
        matrix.postScale(widthScale, heightScale, 0f, 0f)
        val bmpRet = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.RGB_565)
        val canvas = Canvas(bmpRet)
        val paint = Paint()
        canvas.drawBitmap(bm, matrix, paint)
        return bmpRet
    }
}
