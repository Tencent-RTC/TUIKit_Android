package io.trtc.tuikit.chat.uikit.components.messagelist.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.lerp
import io.trtc.tuikit.chat.uikit.components.theme.LocalTheme
import io.trtc.tuikit.chat.uikit.components.messagelist.viewmodel.HighlightManager
import kotlinx.coroutines.delay

private const val HIGHLIGHT_ALPHA = 72 / 255f
private const val HIGHLIGHT_ALPHA_DARK_BUBBLE = 128 / 255f
private const val HIGHLIGHT_DARK_BUBBLE_LIGHTEN_RATIO = 0.4f
private const val HIGHLIGHT_DARK_BUBBLE_LUMINANCE_THRESHOLD = 0.5f

@Composable
fun Modifier.highlightBackground(
    color: Color,
    highlightKey: String,
    shape: Shape,
    highlightManager: HighlightManager,
): Modifier {
    val highlights by highlightManager.highlights.collectAsState()
    val highlightState = highlights[highlightKey]
    val colors = LocalTheme.current.colors
    val isDarkBubble = color.luminance() < HIGHLIGHT_DARK_BUBBLE_LUMINANCE_THRESHOLD
    val highlightColor = if (isDarkBubble) {
        lerp(colors.textColorWarning, Color.White, HIGHLIGHT_DARK_BUBBLE_LIGHTEN_RATIO)
    } else {
        colors.textColorWarning
    }
    val highlightMaxAlpha = if (isDarkBubble) HIGHLIGHT_ALPHA_DARK_BUBBLE else HIGHLIGHT_ALPHA

    if (highlightState == null) {
        return this
    }

    val config = highlightState.config
    var animationPhase by remember(highlightState.startTime, highlightKey) { mutableIntStateOf(0) }

    LaunchedEffect(highlightState.startTime, highlightKey) {
        for (i in 1..config.flashCount) {
            animationPhase = i * 2 - 1
            delay(config.flashDuration)
            animationPhase = i * 2
            delay(config.flashDuration)
        }
        animationPhase = 0
    }

    val targetAlpha = when {
        animationPhase == 0 -> 0f
        animationPhase % 2 == 1 -> highlightMaxAlpha
        else -> 0f
    }
    val alpha by animateFloatAsState(
        targetValue = targetAlpha,
        animationSpec = tween(durationMillis = config.flashDuration.toInt()),
        label = "HighlightOverlayAlpha"
    )
    return this.drawWithContent {
        drawContent()
        if (alpha > 0f) {
            val outline = shape.createOutline(this.size, layoutDirection, this)
            drawOutline(outline, highlightColor.copy(alpha = alpha))
        }
    }
}
