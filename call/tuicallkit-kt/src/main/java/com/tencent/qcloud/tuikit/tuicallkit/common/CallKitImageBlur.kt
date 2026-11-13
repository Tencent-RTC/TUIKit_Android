package com.tencent.qcloud.tuikit.tuicallkit.common

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Rect
import kotlin.math.max
import kotlin.math.min

internal object CallKitImageBlur {

    private const val DEFAULT_RADIUS = 26
    private const val SATURATION = 1.05f

    fun blurred(source: Bitmap, targetWidth: Int, targetHeight: Int, radius: Int = DEFAULT_RADIUS): Bitmap {
        val scale = max(targetWidth / source.width.toFloat(), targetHeight / source.height.toFloat())
        val canvasWidth = (source.width * scale).toInt().coerceAtLeast(1)
        val canvasHeight = (source.height * scale).toInt().coerceAtLeast(1)
        val filled = Bitmap.createBitmap(canvasWidth, canvasHeight, Bitmap.Config.ARGB_8888)
        Canvas(filled).drawBitmap(
            source,
            Rect(0, 0, source.width, source.height),
            Rect(0, 0, canvasWidth, canvasHeight),
            Paint(Paint.ANTI_ALIAS_FLAG)
        )
        stackBlur(filled, radius.coerceIn(1, 100))
        val saturation = ColorMatrix().apply { setSaturation(SATURATION) }
        val saturatePaint = Paint(Paint.ANTI_ALIAS_FLAG)
        saturatePaint.colorFilter = ColorMatrixColorFilter(saturation)
        val output = Bitmap.createBitmap(canvasWidth, canvasHeight, Bitmap.Config.ARGB_8888)
        Canvas(output).drawBitmap(filled, 0f, 0f, saturatePaint)
        filled.recycle()
        return output
    }

    private fun stackBlur(bitmap: Bitmap, radius: Int) {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val radiusPlus1 = radius + 1
        val div = radius * 2 + 1
        val sumFactor = (div * (div + 1)) / 2

        val redStack = IntArray(div)
        val greenStack = IntArray(div)
        val blueStack = IntArray(div)
        val alphaStack = IntArray(div)

        val mulTable = IntArray(256 * sumFactor)
        for (i in 0 until 256 * sumFactor) {
            mulTable[i] = i / sumFactor
        }

        var x: Int
        var y: Int
        var i: Int
        var stackPointer: Int
        var stackStart: Int
        var pixelIndex: Int
        var redSum: Int
        var greenSum: Int
        var blueSum: Int
        var alphaSum: Int
        var redSumIn: Int
        var greenSumIn: Int
        var blueSumIn: Int
        var alphaSumIn: Int
        var redSumOut: Int
        var greenSumOut: Int
        var blueSumOut: Int
        var alphaSumOut: Int
        var sourcePixel: Int

        y = 0
        while (y < height) {
            redSum = 0; greenSum = 0; blueSum = 0; alphaSum = 0
            redSumIn = 0; greenSumIn = 0; blueSumIn = 0; alphaSumIn = 0
            redSumOut = 0; greenSumOut = 0; blueSumOut = 0; alphaSumOut = 0

            sourcePixel = pixels[y * width]
            redSumOut = radiusPlus1 * (sourcePixel shr 16 and 0xFF)
            greenSumOut = radiusPlus1 * (sourcePixel shr 8 and 0xFF)
            blueSumOut = radiusPlus1 * (sourcePixel and 0xFF)
            alphaSumOut = radiusPlus1 * (sourcePixel ushr 24)
            i = 0
            while (i <= radius) {
                redStack[i] = sourcePixel shr 16 and 0xFF
                greenStack[i] = sourcePixel shr 8 and 0xFF
                blueStack[i] = sourcePixel and 0xFF
                alphaStack[i] = sourcePixel ushr 24
                redSum += redStack[i] * (i + 1)
                greenSum += greenStack[i] * (i + 1)
                blueSum += blueStack[i] * (i + 1)
                alphaSum += alphaStack[i] * (i + 1)
                i++
            }
            i = 1
            while (i <= radius) {
                pixelIndex = y * width + min(width - 1, i)
                sourcePixel = pixels[pixelIndex]
                redStack[i + radius] = sourcePixel shr 16 and 0xFF
                greenStack[i + radius] = sourcePixel shr 8 and 0xFF
                blueStack[i + radius] = sourcePixel and 0xFF
                alphaStack[i + radius] = sourcePixel ushr 24
                redSum += redStack[i + radius] * (radiusPlus1 - i)
                greenSum += greenStack[i + radius] * (radiusPlus1 - i)
                blueSum += blueStack[i + radius] * (radiusPlus1 - i)
                alphaSum += alphaStack[i + radius] * (radiusPlus1 - i)
                redSumIn += redStack[i + radius]
                greenSumIn += greenStack[i + radius]
                blueSumIn += blueStack[i + radius]
                alphaSumIn += alphaStack[i + radius]
                i++
            }

            stackPointer = radius
            x = 0
            while (x < width) {
                pixelIndex = y * width + x
                pixels[pixelIndex] = (mulTable[alphaSum] shl 24) or
                    (mulTable[redSum] shl 16) or
                    (mulTable[greenSum] shl 8) or
                    mulTable[blueSum]
                redSum -= redSumOut
                greenSum -= greenSumOut
                blueSum -= blueSumOut
                alphaSum -= alphaSumOut

                stackStart = stackPointer + div - radius
                if (stackStart >= div) stackStart -= div
                redSumOut -= redStack[stackStart]
                greenSumOut -= greenStack[stackStart]
                blueSumOut -= blueStack[stackStart]
                alphaSumOut -= alphaStack[stackStart]

                pixelIndex = y * width + min(x + radiusPlus1, width - 1)
                sourcePixel = pixels[pixelIndex]
                redStack[stackStart] = sourcePixel shr 16 and 0xFF
                greenStack[stackStart] = sourcePixel shr 8 and 0xFF
                blueStack[stackStart] = sourcePixel and 0xFF
                alphaStack[stackStart] = sourcePixel ushr 24

                redSumIn += redStack[stackStart]
                greenSumIn += greenStack[stackStart]
                blueSumIn += blueStack[stackStart]
                alphaSumIn += alphaStack[stackStart]

                redSum += redSumIn
                greenSum += greenSumIn
                blueSum += blueSumIn
                alphaSum += alphaSumIn

                stackPointer++
                if (stackPointer >= div) stackPointer = 0
                redSumOut += redStack[stackPointer]
                greenSumOut += greenStack[stackPointer]
                blueSumOut += blueStack[stackPointer]
                alphaSumOut += alphaStack[stackPointer]
                redSumIn -= redStack[stackPointer]
                greenSumIn -= greenStack[stackPointer]
                blueSumIn -= blueStack[stackPointer]
                alphaSumIn -= alphaStack[stackPointer]
                x++
            }
            y++
        }

        x = 0
        while (x < width) {
            redSum = 0; greenSum = 0; blueSum = 0; alphaSum = 0
            redSumIn = 0; greenSumIn = 0; blueSumIn = 0; alphaSumIn = 0
            redSumOut = 0; greenSumOut = 0; blueSumOut = 0; alphaSumOut = 0

            sourcePixel = pixels[x]
            redSumOut = radiusPlus1 * (sourcePixel shr 16 and 0xFF)
            greenSumOut = radiusPlus1 * (sourcePixel shr 8 and 0xFF)
            blueSumOut = radiusPlus1 * (sourcePixel and 0xFF)
            alphaSumOut = radiusPlus1 * (sourcePixel ushr 24)
            i = 0
            while (i <= radius) {
                redStack[i] = sourcePixel shr 16 and 0xFF
                greenStack[i] = sourcePixel shr 8 and 0xFF
                blueStack[i] = sourcePixel and 0xFF
                alphaStack[i] = sourcePixel ushr 24
                redSum += redStack[i] * (i + 1)
                greenSum += greenStack[i] * (i + 1)
                blueSum += blueStack[i] * (i + 1)
                alphaSum += alphaStack[i] * (i + 1)
                i++
            }
            i = 1
            while (i <= radius) {
                pixelIndex = min(height - 1, i) * width + x
                sourcePixel = pixels[pixelIndex]
                redStack[i + radius] = sourcePixel shr 16 and 0xFF
                greenStack[i + radius] = sourcePixel shr 8 and 0xFF
                blueStack[i + radius] = sourcePixel and 0xFF
                alphaStack[i + radius] = sourcePixel ushr 24
                redSum += redStack[i + radius] * (radiusPlus1 - i)
                greenSum += greenStack[i + radius] * (radiusPlus1 - i)
                blueSum += blueStack[i + radius] * (radiusPlus1 - i)
                alphaSum += alphaStack[i + radius] * (radiusPlus1 - i)
                redSumIn += redStack[i + radius]
                greenSumIn += greenStack[i + radius]
                blueSumIn += blueStack[i + radius]
                alphaSumIn += alphaStack[i + radius]
                i++
            }

            stackPointer = radius
            y = 0
            while (y < height) {
                pixelIndex = y * width + x
                pixels[pixelIndex] = (mulTable[alphaSum] shl 24) or
                    (mulTable[redSum] shl 16) or
                    (mulTable[greenSum] shl 8) or
                    mulTable[blueSum]
                redSum -= redSumOut
                greenSum -= greenSumOut
                blueSum -= blueSumOut
                alphaSum -= alphaSumOut

                stackStart = stackPointer + div - radius
                if (stackStart >= div) stackStart -= div
                redSumOut -= redStack[stackStart]
                greenSumOut -= greenStack[stackStart]
                blueSumOut -= blueStack[stackStart]
                alphaSumOut -= alphaStack[stackStart]

                pixelIndex = min(y + radiusPlus1, height - 1) * width + x
                sourcePixel = pixels[pixelIndex]
                redStack[stackStart] = sourcePixel shr 16 and 0xFF
                greenStack[stackStart] = sourcePixel shr 8 and 0xFF
                blueStack[stackStart] = sourcePixel and 0xFF
                alphaStack[stackStart] = sourcePixel ushr 24

                redSumIn += redStack[stackStart]
                greenSumIn += greenStack[stackStart]
                blueSumIn += blueStack[stackStart]
                alphaSumIn += alphaStack[stackStart]

                redSum += redSumIn
                greenSum += greenSumIn
                blueSum += blueSumIn
                alphaSum += alphaSumIn

                stackPointer++
                if (stackPointer >= div) stackPointer = 0
                redSumOut += redStack[stackPointer]
                greenSumOut += greenStack[stackPointer]
                blueSumOut += blueStack[stackPointer]
                alphaSumOut += alphaStack[stackPointer]
                redSumIn -= redStack[stackPointer]
                greenSumIn -= greenStack[stackPointer]
                blueSumIn -= blueStack[stackPointer]
                alphaSumIn -= alphaStack[stackPointer]
                y++
            }
            x++
        }

        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
    }
}
