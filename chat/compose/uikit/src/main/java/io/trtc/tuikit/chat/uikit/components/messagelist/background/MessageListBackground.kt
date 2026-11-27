package io.trtc.tuikit.chat.uikit.components.messagelist.background

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import io.trtc.tuikit.chat.uikit.components.theme.LocalTheme
import io.trtc.tuikit.chat.uikit.components.messagelist.config.MessageListBackground

@Composable
fun MessageListBackgroundLayer(
    background: MessageListBackground?,
    modifier: Modifier = Modifier
) {
    val colors = LocalTheme.current.colors
    Box(modifier = modifier.fillMaxSize()) {
        when (background) {
            is MessageListBackground.Image -> {
                AsyncImage(
                    model = background.uri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            is MessageListBackground.Color -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(background.color))
                )
            }

            is MessageListBackground.Gradient -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                colors = background.colors.map { Color(it) },
                                start = gradientStart(background.direction),
                                end = gradientEnd(background.direction)
                            )
                        )
                )
            }

            null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(colors.bgColorTopBar)
                )
            }
        }
    }
}

private fun gradientStart(direction: MessageListBackground.GradientDirection): Offset {
    return when (direction) {
        MessageListBackground.GradientDirection.TOP_BOTTOM,
        MessageListBackground.GradientDirection.LEFT_RIGHT -> Offset.Zero

        MessageListBackground.GradientDirection.BOTTOM_TOP -> Offset(0f, Float.POSITIVE_INFINITY)
        MessageListBackground.GradientDirection.RIGHT_LEFT -> Offset(Float.POSITIVE_INFINITY, 0f)
        MessageListBackground.GradientDirection.TOP_LEFT_BOTTOM_RIGHT -> Offset.Zero
        MessageListBackground.GradientDirection.BOTTOM_RIGHT_TOP_LEFT ->
            Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
    }
}

private fun gradientEnd(direction: MessageListBackground.GradientDirection): Offset {
    return when (direction) {
        MessageListBackground.GradientDirection.TOP_BOTTOM -> Offset(0f, Float.POSITIVE_INFINITY)
        MessageListBackground.GradientDirection.BOTTOM_TOP -> Offset.Zero
        MessageListBackground.GradientDirection.LEFT_RIGHT -> Offset(Float.POSITIVE_INFINITY, 0f)
        MessageListBackground.GradientDirection.RIGHT_LEFT -> Offset.Zero
        MessageListBackground.GradientDirection.TOP_LEFT_BOTTOM_RIGHT ->
            Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
        MessageListBackground.GradientDirection.BOTTOM_RIGHT_TOP_LEFT -> Offset.Zero
    }
}
