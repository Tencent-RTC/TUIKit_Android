package com.tencent.uikit.app.common.widget.gatherimage

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Rect
import android.text.TextUtils
import android.widget.ImageView
import com.tencent.uikit.app.R
import com.tencent.uikit.app.common.utils.ImageUtil
import com.tencent.uikit.app.common.utils.GlideEngine
import com.tencent.uikit.app.common.utils.ThreadUtils
import io.trtc.tuikit.atomicxcore.impl.common.ContextProvider
import java.io.File
import java.util.*
import java.util.concurrent.ExecutionException

class TeamHeadSynthesizer(private val mContext: Context, private val imageView: ImageView) :
    Synthesizer {
    private lateinit var multiImageData: MultiImageData

    // It is safe to set and get only in the main thread
    private var currentImageId = ""

    private val callback = object : Callback {
        override fun onCall(bitmap: Bitmap, targetID: String) {
            if (!TextUtils.equals(getImageId(), targetID)) {
                return
            }
            GlideEngine.loadUserIcon(imageView, bitmap)
        }
    }

    init {
        init()
    }

    private fun init() {
        multiImageData = MultiImageData()
    }

    fun setMaxWidthHeight(maxWidth: Int, maxHeight: Int) {
        multiImageData.maxWidth = maxWidth
        multiImageData.maxHeight = maxHeight
    }

    fun getMultiImageData(): MultiImageData {
        return multiImageData
    }

    fun getDefaultImage(): Int {
        return multiImageData.defaultImageResId
    }

    fun setDefaultImage(defaultImageResId: Int) {
        multiImageData.defaultImageResId = defaultImageResId
    }

    fun setBgColor(bgColor: Int) {
        multiImageData.bgColor = bgColor
    }

    fun setGap(gap: Int) {
        multiImageData.gap = gap
    }

    /**
     * Set Grid params
     *
     * @param imagesSize   Number of pictures
     * @return gridParam[0] Rows gridParam[1] columns
     */
    protected fun calculateGridParam(imagesSize: Int): IntArray {
        val gridParam = IntArray(2)
        if (imagesSize < 3) {
            gridParam[0] = 1
            gridParam[1] = imagesSize
        } else if (imagesSize <= 4) {
            gridParam[0] = 2
            gridParam[1] = 2
        } else {
            gridParam[0] = imagesSize / 3 + if (imagesSize % 3 == 0) 0 else 1
            gridParam[1] = 3
        }
        return gridParam
    }

    override fun synthesizeImageList(imageData: MultiImageData): Bitmap {
        val mergeBitmap =
            Bitmap.createBitmap(imageData.maxWidth, imageData.maxHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(mergeBitmap)
        drawDrawable(canvas, imageData)
        canvas.save()
        canvas.restore()
        return mergeBitmap
    }

    override fun asyncLoadImageList(imageData: MultiImageData): Boolean {
        val loadSuccess = true
        val imageUrls = imageData.imageUrls
        if (imageUrls != null) {
            for (i in imageUrls.indices) {
                val defaultIcon =
                    BitmapFactory.decodeResource(
                        mContext.resources,
                        R.drawable.app_default_user_icon_light
                    )
                try {
                    val bitmap = asyncLoadImage(imageUrls[i], imageData.targetImageSize)
                    imageData.putBitmap(bitmap, i)
                } catch (e: InterruptedException) {
                    e.printStackTrace()
                    imageData.putBitmap(defaultIcon, i)
                } catch (e: ExecutionException) {
                    e.printStackTrace()
                    imageData.putBitmap(defaultIcon, i)
                }
            }
        }
        return loadSuccess
    }

    override fun drawDrawable(canvas: Canvas, imageData: MultiImageData) {
        canvas.drawColor(imageData.bgColor)
        val size = imageData.size()
        val tCenter = (imageData.maxHeight + imageData.gap) / 2
        val bCenter = (imageData.maxHeight - imageData.gap) / 2
        val lCenter = (imageData.maxWidth + imageData.gap) / 2
        val rCenter = (imageData.maxWidth - imageData.gap) / 2
        val center = (imageData.maxHeight - imageData.targetImageSize) / 2

        for (i in 0 until size) {
            val rowNum = i / imageData.columnCount
            val columnNum = i % imageData.columnCount

            val left =
                (imageData.targetImageSize * (if (imageData.columnCount == 1) columnNum + 0.5 else columnNum.toDouble()) + imageData.gap * (columnNum + 1)).toInt()
            val top =
                (imageData.targetImageSize * (if (imageData.columnCount == 1) rowNum + 0.5 else rowNum.toDouble()) + imageData.gap * (rowNum + 1)).toInt()
            val right = left + imageData.targetImageSize
            val bottom = top + imageData.targetImageSize

            val bitmap = imageData.getBitmap(i)
            when (size) {
                1 -> {
                    drawBitmapAtPosition(canvas, left, top, right, bottom, bitmap)
                }

                2 -> {
                    drawBitmapAtPosition(
                        canvas,
                        left,
                        center,
                        right,
                        center + imageData.targetImageSize,
                        bitmap
                    )
                }

                3 -> {
                    if (i == 0) {
                        drawBitmapAtPosition(
                            canvas,
                            center,
                            top,
                            center + imageData.targetImageSize,
                            bottom,
                            bitmap
                        )
                    } else {
                        drawBitmapAtPosition(
                            canvas,
                            imageData.gap * i + imageData.targetImageSize * (i - 1),
                            tCenter,
                            imageData.gap * i + imageData.targetImageSize * i,
                            tCenter + imageData.targetImageSize,
                            bitmap
                        )
                    }
                }

                4 -> {
                    drawBitmapAtPosition(canvas, left, top, right, bottom, bitmap)
                }

                5 -> {
                    when (i) {
                        0 -> {
                            drawBitmapAtPosition(
                                canvas,
                                rCenter - imageData.targetImageSize,
                                rCenter - imageData.targetImageSize,
                                rCenter,
                                rCenter,
                                bitmap
                            )
                        }

                        1 -> {
                            drawBitmapAtPosition(
                                canvas,
                                lCenter,
                                rCenter - imageData.targetImageSize,
                                lCenter + imageData.targetImageSize,
                                rCenter,
                                bitmap
                            )
                        }

                        else -> {
                            drawBitmapAtPosition(
                                canvas,
                                imageData.gap * (i - 1) + imageData.targetImageSize * (i - 2),
                                tCenter,
                                imageData.gap * (i - 1) + imageData.targetImageSize * (i - 1),
                                tCenter + imageData.targetImageSize,
                                bitmap
                            )
                        }
                    }
                }

                6 -> {
                    if (i < 3) {
                        drawBitmapAtPosition(
                            canvas,
                            imageData.gap * (i + 1) + imageData.targetImageSize * i,
                            bCenter - imageData.targetImageSize,
                            imageData.gap * (i + 1) + imageData.targetImageSize * (i + 1),
                            bCenter,
                            bitmap
                        )
                    } else {
                        drawBitmapAtPosition(
                            canvas,
                            imageData.gap * (i - 2) + imageData.targetImageSize * (i - 3),
                            tCenter,
                            imageData.gap * (i - 2) + imageData.targetImageSize * (i - 2),
                            tCenter + imageData.targetImageSize,
                            bitmap
                        )
                    }
                }

                7 -> {
                    when {
                        i == 0 -> {
                            drawBitmapAtPosition(
                                canvas,
                                center,
                                imageData.gap,
                                center + imageData.targetImageSize,
                                imageData.gap + imageData.targetImageSize,
                                bitmap
                            )
                        }

                        i in 1..3 -> {
                            drawBitmapAtPosition(
                                canvas,
                                imageData.gap * i + imageData.targetImageSize * (i - 1),
                                center,
                                imageData.gap * i + imageData.targetImageSize * i,
                                center + imageData.targetImageSize,
                                bitmap
                            )
                        }

                        else -> {
                            drawBitmapAtPosition(
                                canvas,
                                imageData.gap * (i - 3) + imageData.targetImageSize * (i - 4),
                                tCenter + imageData.targetImageSize / 2,
                                imageData.gap * (i - 3) + imageData.targetImageSize * (i - 3),
                                tCenter + imageData.targetImageSize / 2 + imageData.targetImageSize,
                                bitmap
                            )
                        }
                    }
                }

                8 -> {
                    when {
                        i == 0 -> {
                            drawBitmapAtPosition(
                                canvas,
                                rCenter - imageData.targetImageSize,
                                imageData.gap,
                                rCenter,
                                imageData.gap + imageData.targetImageSize,
                                bitmap
                            )
                        }

                        i == 1 -> {
                            drawBitmapAtPosition(
                                canvas,
                                lCenter,
                                imageData.gap,
                                lCenter + imageData.targetImageSize,
                                imageData.gap + imageData.targetImageSize,
                                bitmap
                            )
                        }

                        i in 2..4 -> {
                            drawBitmapAtPosition(
                                canvas,
                                imageData.gap * (i - 1) + imageData.targetImageSize * (i - 2),
                                center,
                                imageData.gap * (i - 1) + imageData.targetImageSize * (i - 1),
                                center + imageData.targetImageSize,
                                bitmap
                            )
                        }

                        else -> {
                            drawBitmapAtPosition(
                                canvas,
                                imageData.gap * (i - 4) + imageData.targetImageSize * (i - 5),
                                tCenter + imageData.targetImageSize / 2,
                                imageData.gap * (i - 4) + imageData.targetImageSize * (i - 4),
                                tCenter + imageData.targetImageSize / 2 + imageData.targetImageSize,
                                bitmap
                            )
                        }
                    }
                }

                9 -> {
                    drawBitmapAtPosition(canvas, left, top, right, bottom, bitmap)
                }
            }
        }
    }

    /**
     * DrawBitmap
     *
     * @param canvas
     * @param left
     * @param top
     * @param right
     * @param bottom
     * @param bitmap
     */
    fun drawBitmapAtPosition(
        canvas: Canvas,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        bitmap: Bitmap?
    ) {
        var drawBitmap = bitmap
        if (drawBitmap == null) {
            if (multiImageData.defaultImageResId > 0) {
                drawBitmap =
                    BitmapFactory.decodeResource(
                        mContext.resources,
                        multiImageData.defaultImageResId
                    )
            }
        }
        if (drawBitmap != null) {
            val rect = Rect(left, top, right, bottom)
            canvas.drawBitmap(drawBitmap, null, rect, null)
        }
    }

    @Throws(ExecutionException::class, InterruptedException::class)
    private fun asyncLoadImage(imageUrl: Any, targetImageSize: Int): Bitmap {
        return GlideEngine.loadBitmap(imageUrl, targetImageSize)
    }

    fun setImageId(id: String) {
        currentImageId = id
    }

    fun getImageId(): String {
        return currentImageId
    }

    fun load(imageId: String?) {
        if (multiImageData.size() == 0) {
            // The image id when the request is initiated is inconsistent with the current image id,
            // indicating that multiplexing has occurred, and the image should not be set at this time.
            if (imageId != null && !TextUtils.equals(imageId, currentImageId)) {
                return
            }
            GlideEngine.loadUserIcon(imageView, getDefaultImage())
            return
        }

        if (multiImageData.size() == 1) {
            // The image id when the request is initiated is inconsistent with the current image id,
            // indicating that multiplexing has occurred, and the image should not be set at this time.
            if (imageId != null && !TextUtils.equals(imageId, currentImageId)) {
                return
            }
            GlideEngine.loadUserIcon(imageView, multiImageData.imageUrls!![0])
            return
        }

        // Clear the content before loading images asynchronously to avoid flickering
        clearImage()

        // Initialize the image information. Since it is asynchronous loading and synthesizing the avatar,
        // a local object needs to be passed to the synthesis thread, which is only used in the asynchronous
        // loading thread, so that when the image is reused, the external thread will not overwrite the local
        // object by setting the url again.
        var copyMultiImageData: MultiImageData
        try {
            copyMultiImageData = multiImageData.clone()
        } catch (e: CloneNotSupportedException) {
            e.printStackTrace()
            val urlList = ArrayList<Any>()
            if (multiImageData.imageUrls != null) {
                urlList.addAll(multiImageData.imageUrls!!)
            }
            copyMultiImageData = MultiImageData(urlList, multiImageData.defaultImageResId)
        }

        val gridParam = calculateGridParam(multiImageData.size())
        copyMultiImageData.rowCount = gridParam[0]
        copyMultiImageData.columnCount = gridParam[1]
        copyMultiImageData.targetImageSize =
            (copyMultiImageData.maxWidth - (copyMultiImageData.columnCount + 1) * copyMultiImageData.gap) /
            if (copyMultiImageData.columnCount == 1) 2 else copyMultiImageData.columnCount

        val finalImageId = imageId
        val finalCopyMultiImageData = copyMultiImageData

        ThreadUtils.execute {
            val context = ContextProvider.getApplicationContext() ?: return@execute
            val imageBaseDir = context.filesDir.absolutePath + "/image/"
            val file = File(imageBaseDir + finalImageId)
            var cacheBitmapExists = false
            var existsBitmap: Bitmap? = null
            if (file.exists() && file.isFile) {
                val options = BitmapFactory.Options()
                existsBitmap = BitmapFactory.decodeFile(file.path, options)
                if (options.outWidth > 0 && options.outHeight > 0) {
                    cacheBitmapExists = true
                }
            }
            if (!cacheBitmapExists) {
                asyncLoadImageList(finalCopyMultiImageData)
                val bitmap = synthesizeImageList(finalCopyMultiImageData)
                ImageUtil.storeBitmap(file, bitmap)
                ImageUtil.setGroupConversationAvatar(finalImageId!!, file.absolutePath)
                ThreadUtils.postOnUiThread {
                    callback.onCall(bitmap, finalImageId)
                }
            } else {
                val finalExistsBitmap = existsBitmap
                ThreadUtils.postOnUiThread {
                    callback.onCall(finalExistsBitmap!!, finalImageId!!)
                }
            }
        }
    }

    fun clearImage() {
        GlideEngine.clear(imageView)
    }

    internal interface Callback {
        fun onCall(bitmap: Bitmap, targetID: String)
    }
}