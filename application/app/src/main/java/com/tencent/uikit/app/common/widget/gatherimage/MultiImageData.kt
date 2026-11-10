package com.tencent.uikit.app.common.widget.gatherimage

import android.graphics.Bitmap
import android.graphics.Color
import java.util.*

/**
 * Multiple image data
 */
class MultiImageData : Cloneable {
    companion object {
        const val maxSize = 9
    }

    var imageUrls: MutableList<Any>? = null
    var defaultImageResId: Int = 0
    var bitmapMap: MutableMap<Int, Bitmap>? = null
    var bgColor = Color.parseColor("#cfd3d8")

    var targetImageSize: Int = 0
    var maxWidth: Int = 0
    var maxHeight: Int = 0
    var rowCount: Int = 0
    var columnCount: Int = 0
    var gap = 6

    constructor()

    constructor(defaultImageResId: Int) {
        this.defaultImageResId = defaultImageResId
    }

    constructor(imageUrls: MutableList<Any>?, defaultImageResId: Int) {
        this.imageUrls = imageUrls
        this.defaultImageResId = defaultImageResId
    }

//    fun getDefaultImageResId(): Int {
//        return defaultImageResId
//    }
//
//    fun setDefaultImageResId(defaultImageResId: Int) {
//        this.defaultImageResId = defaultImageResId
//    }

//    fun getImageUrls(): MutableList<Any>? {
//        return imageUrls
//    }
//
//    fun setImageUrls(imageUrls: MutableList<Any>?) {
//        this.imageUrls = imageUrls
//    }

    fun putBitmap(bitmap: Bitmap, position: Int) {
        if (bitmapMap != null) {
            synchronized(bitmapMap!!) {
                bitmapMap!![position] = bitmap
            }
        } else {
            bitmapMap = HashMap()
            synchronized(bitmapMap!!) {
                bitmapMap!![position] = bitmap
            }
        }
    }

    fun getBitmap(position: Int): Bitmap? {
        if (bitmapMap != null) {
            synchronized(bitmapMap!!) {
                return bitmapMap!![position]
            }
        }
        return null
    }

    fun size(): Int {
        return if (imageUrls != null) {
            if (imageUrls!!.size > maxSize) maxSize else imageUrls!!.size
        } else {
            0
        }
    }

    @Throws(CloneNotSupportedException::class)
    public override fun clone(): MultiImageData {
        val multiImageData = super.clone() as MultiImageData
        if (imageUrls != null) {
            multiImageData.imageUrls = ArrayList(imageUrls!!.size)
            multiImageData.imageUrls!!.addAll(imageUrls!!)
        }
        if (bitmapMap != null) {
            multiImageData.bitmapMap = HashMap()
            multiImageData.bitmapMap!!.putAll(bitmapMap!!)
        }
        return multiImageData
    }
}