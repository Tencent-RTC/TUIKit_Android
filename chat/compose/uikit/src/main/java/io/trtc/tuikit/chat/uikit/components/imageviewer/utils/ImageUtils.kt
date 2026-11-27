package io.trtc.tuikit.chat.uikit.components.imageviewer.utils

import android.os.Build.VERSION.SDK_INT
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.gif.AnimatedImageDecoder
import coil3.gif.GifDecoder


object ImageUtils {
    private var _imageLoader: ImageLoader? = null

    @Composable
    fun getImageLoader(): ImageLoader {
        val context = LocalContext.current
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

    fun calculateOptimalSize(
        originalWidth: Int,
        originalHeight: Int,
        maxSize: Dp = 190.dp,
        minSize: Dp = 80.dp,
        minAspectRatio: Float = 0.5f,
        maxAspectRatio: Float = 2.0f
    ): DpSize {
        val widthRatio = maxSize.value / originalWidth.toFloat()
        val heightRatio = maxSize.value / originalHeight.toFloat()
        val scale = minOf(widthRatio, heightRatio).coerceAtMost(1f)

        var displayWidth = (originalWidth * scale).dp.coerceAtMost(maxSize)
        var displayHeight = (originalHeight * scale).dp.coerceAtMost(maxSize)

        if (displayWidth / displayHeight < minAspectRatio) {
            displayWidth = (displayHeight * minAspectRatio).coerceAtMost(maxSize)
        } else if (displayWidth / displayHeight > maxAspectRatio) {
            displayHeight = (displayWidth / maxAspectRatio).coerceAtMost(maxSize)
        }

        displayWidth = displayWidth.coerceAtLeast(minSize)
        displayHeight = displayHeight.coerceAtLeast(minSize)

        return DpSize(displayWidth, displayHeight)
    }
}