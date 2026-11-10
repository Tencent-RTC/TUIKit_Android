package com.tencent.uikit.app.common.widget.gatherimage

import android.graphics.Bitmap
import android.graphics.Canvas

interface Synthesizer {
    fun synthesizeImageList(imageData: MultiImageData): Bitmap
    fun asyncLoadImageList(imageData: MultiImageData): Boolean
    fun drawDrawable(canvas: Canvas, imageData: MultiImageData)
}