package io.trtc.tuikit.chat.uikit.components.widgets

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import io.trtc.tuikit.chat.uikit.components.theme.LocalTheme
import kotlin.math.roundToInt

@Immutable
private data class SliderSize(
    val thumbRadius: Dp = 7.dp,
    val thumbBorderWidth: Dp = 2.dp,
    val trackThickness: Dp = 4.dp,
)

@Immutable
private data class SliderColors(
    val trackColor: Color,
    val activeTrackColor: Color,
    val thumbColor: Color,
    val thumbBorderColor: Color
)

enum class SliderOrientation {
    Horizontal,
    Vertical
}

@Composable
fun Slider(
    value: Float,
    modifier: Modifier = Modifier,
    orientation: SliderOrientation = SliderOrientation.Horizontal,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..100f,
    showTooltip: Boolean = false,
    onValueChange: (Float) -> Unit,
) {
    val colors = LocalTheme.current.colors

    val slideSize = SliderSize()
    val slideColors = SliderColors(
        trackColor = colors.sliderColorEmpty,
        thumbColor = colors.sliderColorButton,
        activeTrackColor = colors.sliderColorFilled,
        thumbBorderColor = colors.sliderColorFilled,
    )

    when (orientation) {
        SliderOrientation.Horizontal -> {
            HorizontalSliderImpl(
                value = value,
                modifier = modifier,
                colors = slideColors,
                sliderSize = slideSize,
                enabled = enabled,
                valueRange = valueRange,
                showTooltip = showTooltip,
                onValueChange = onValueChange
            )
        }

        SliderOrientation.Vertical -> {
            VerticalSliderImpl(
                value = value,
                modifier = modifier,
                colors = slideColors,
                slideSize = slideSize,
                enabled = enabled,
                valueRange = valueRange,
                showTooltip = showTooltip,
                onValueChange = onValueChange
            )
        }
    }
}

@Composable
private fun HorizontalSliderImpl(
    value: Float,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..100f,
    colors: SliderColors,
    sliderSize: SliderSize,
    showTooltip: Boolean = false,
    onValueChange: (Float) -> Unit,
) {
    val density = LocalDensity.current

    var isDragging by remember { mutableStateOf(false) }
    var isHovering by remember { mutableStateOf(false) }
    var dragStartX by remember { mutableStateOf(0f) }

    val clampedValue = value.coerceIn(valueRange)
    val progress = (clampedValue - valueRange.start) / (valueRange.endInclusive - valueRange.start)
    val thumbRadius = with(density) { sliderSize.thumbRadius.toPx() }
    val thumbBorderWidth = with(density) { sliderSize.thumbBorderWidth.toPx() }
    val trackHeight = with(density) { sliderSize.trackThickness.toPx() }

    fun calculateNewValue(offset: Float, sliderWidth: Float): Float {
        val newProgress = ((offset - thumbRadius) / (sliderWidth - 2 * thumbRadius)).coerceIn(0f, 1f)
        return valueRange.start + newProgress * (valueRange.endInclusive - valueRange.start)
    }

    Box(
        modifier = modifier
            .height(48.dp),
        contentAlignment = Alignment.Center
    ) {
        if (showTooltip && (isDragging || isHovering) && enabled) {
            SliderTooltip(
                value = value.roundToInt(),
                progress = progress,
                modifier = Modifier
                    .zIndex(1f)
                    .offset(y = (-32).dp)
            )
        }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(enabled) {
                    if (enabled) {
                        detectTapGestures(
                            onTap = { offset ->
                                val newValue = calculateNewValue(offset.x, size.width.toFloat())
                                onValueChange(newValue)
                            }
                        )
                    }
                }
                .pointerInput(enabled) {
                    if (enabled) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                isDragging = true
                                isHovering = true
                                dragStartX = offset.x
                                val newValue = calculateNewValue(offset.x, size.width.toFloat())
                                onValueChange(newValue)
                            },
                            onDragEnd = {
                                isDragging = false
                                isHovering = false
                            },
                            onDrag = { change, offset ->
                                dragStartX += offset.x
                                val newValue = calculateNewValue(dragStartX, size.width.toFloat())
                                onValueChange(newValue)
                            }
                        )
                    }
                }
        ) {
            drawHorizontalSlider(
                progress = progress,
                trackColor = colors.trackColor,
                activeTrackColor = colors.activeTrackColor,
                thumbColor = colors.thumbColor,
                thumbBorderColor = colors.thumbBorderColor,
                enabled = enabled,
                sliderWidth = size.width,
                thumbRadius = thumbRadius,
                thumbBorderWidth = thumbBorderWidth,
                trackHeight = trackHeight,
                isDragging = isDragging
            )
        }
    }
}

@Composable
private fun VerticalSliderImpl(
    value: Float,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..100f,
    colors: SliderColors,
    slideSize: SliderSize,
    showTooltip: Boolean = false,
    onValueChange: (Float) -> Unit,
) {
    val density = LocalDensity.current

    var isDragging by remember { mutableStateOf(false) }
    var isHovering by remember { mutableStateOf(false) }
    var dragStartY by remember { mutableStateOf(0f) }

    val clampedValue = value.coerceIn(valueRange)
    val progress = (clampedValue - valueRange.start) / (valueRange.endInclusive - valueRange.start)

    val thumbRadius = with(density) { slideSize.thumbRadius.toPx() }
    val thumbBorderWidth = with(density) { slideSize.thumbBorderWidth.toPx() }
    val trackWidth = with(density) { slideSize.trackThickness.toPx() }

    fun calculateNewValue(offset: Float, sliderHeight: Float): Float {
        val invertedOffset = sliderHeight - offset
        val newProgress = ((invertedOffset - thumbRadius) / (sliderHeight - 2 * thumbRadius)).coerceIn(0f, 1f)
        return valueRange.start + newProgress * (valueRange.endInclusive - valueRange.start)
    }

    Box(
        modifier = modifier
            .width(48.dp),
        contentAlignment = Alignment.Center
    ) {
        if (showTooltip && (isDragging || isHovering) && enabled) {
            VerticalSliderTooltip(
                value = value.roundToInt(),
                progress = progress,
                modifier = Modifier
                    .zIndex(1f)
                    .offset(x = 32.dp)
            )
        }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(enabled) {
                    if (enabled) {
                        detectTapGestures(
                            onTap = { offset ->
                                val newValue = calculateNewValue(offset.y, size.height.toFloat())
                                onValueChange(newValue)
                            }
                        )
                    }
                }
                .pointerInput(enabled) {
                    if (enabled) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                isDragging = true
                                isHovering = true
                                dragStartY = offset.y
                                val newValue = calculateNewValue(offset.y, size.height.toFloat())
                                onValueChange(newValue)
                            },
                            onDragEnd = {
                                isDragging = false
                                isHovering = false
                            },
                            onDrag = { change, offset ->
                                dragStartY += offset.y
                                val newValue = calculateNewValue(dragStartY, size.height.toFloat())
                                onValueChange(newValue)
                            }
                        )
                    }
                }
        ) {
            drawVerticalSlider(
                progress = progress,
                trackColor = colors.trackColor,
                activeTrackColor = colors.activeTrackColor,
                thumbColor = colors.thumbColor,
                thumbBorderColor = colors.thumbBorderColor,
                enabled = enabled,
                sliderHeight = size.height,
                thumbRadius = thumbRadius,
                thumbBorderWidth = thumbBorderWidth,
                trackWidth = trackWidth,
                isDragging = isDragging
            )
        }
    }
}

@Composable
private fun SliderTooltip(
    value: Int,
    progress: Float,
    modifier: Modifier = Modifier
) {
    val colors = LocalTheme.current.colors
    val density = LocalDensity.current
    val tooltipWidth = 34.dp
    val thumbRadius = 7.dp
}

private fun DrawScope.drawHorizontalSlider(
    progress: Float,
    trackColor: Color,
    activeTrackColor: Color,
    thumbColor: Color,
    thumbBorderColor: Color,
    enabled: Boolean,
    sliderWidth: Float,
    thumbRadius: Float,
    thumbBorderWidth: Float,
    trackHeight: Float,
    isDragging: Boolean = false
) {
    val centerY = size.height / 2
    val trackY = centerY - trackHeight / 2
    val thumbX = progress * (sliderWidth - 2 * thumbRadius) + thumbRadius

    val alpha = if (enabled) 1f else 0.5f

    drawRoundRect(
        color = trackColor.copy(alpha = alpha),
        topLeft = Offset(thumbRadius, trackY),
        size = androidx.compose.ui.geometry.Size(sliderWidth - 2 * thumbRadius, trackHeight),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(trackHeight / 2)
    )

    if (progress > 0) {
        drawRoundRect(
            color = activeTrackColor.copy(alpha = alpha),
            topLeft = Offset(thumbRadius, trackY),
            size = androidx.compose.ui.geometry.Size(
                (sliderWidth - 2 * thumbRadius) * progress,
                trackHeight
            ),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(trackHeight / 2)
        )
    }

    if (isDragging && enabled) {
        drawCircle(
            color = Color.Black.copy(alpha = 0.12f),
            radius = thumbRadius + 2f,
            center = Offset(thumbX + 1f, centerY + 2f)
        )
    }

    drawCircle(
        color = thumbColor,
        radius = thumbRadius,
        center = Offset(thumbX, centerY)
    )

    drawCircle(
        color = thumbBorderColor.copy(alpha = alpha),
        radius = thumbRadius,
        center = Offset(thumbX, centerY),
        style = Stroke(width = thumbBorderWidth)
    )
}

@Composable
private fun VerticalSliderTooltip(
    value: Int,
    progress: Float,
    modifier: Modifier = Modifier
) {
    val colors = LocalTheme.current.colors
    val tooltipHeight = 28.dp
    val thumbRadius = 7.dp
}

private fun DrawScope.drawVerticalSlider(
    progress: Float,
    trackColor: Color,
    activeTrackColor: Color,
    thumbColor: Color,
    thumbBorderColor: Color,
    enabled: Boolean,
    sliderHeight: Float,
    thumbRadius: Float,
    thumbBorderWidth: Float,
    trackWidth: Float,
    isDragging: Boolean = false
) {
    val centerX = size.width / 2
    val trackX = centerX - trackWidth / 2
    val thumbY = (1f - progress) * (sliderHeight - 2 * thumbRadius) + thumbRadius

    val alpha = if (enabled) 1f else 0.5f

    drawRoundRect(
        color = trackColor.copy(alpha = alpha),
        topLeft = Offset(trackX, thumbRadius),
        size = androidx.compose.ui.geometry.Size(trackWidth, sliderHeight - 2 * thumbRadius),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(trackWidth / 2)
    )

    if (progress > 0) {
        val activeTrackHeight = (sliderHeight - 2 * thumbRadius) * progress
        drawRoundRect(
            color = activeTrackColor.copy(alpha = alpha),
            topLeft = Offset(trackX, sliderHeight - thumbRadius - activeTrackHeight),
            size = androidx.compose.ui.geometry.Size(trackWidth, activeTrackHeight),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(trackWidth / 2)
        )
    }

    if (isDragging && enabled) {
        drawCircle(
            color = Color.Black.copy(alpha = 0.12f),
            radius = thumbRadius + 2f,
            center = Offset(centerX + 1f, thumbY + 2f)
        )
    }

    drawCircle(
        color = thumbColor,
        radius = thumbRadius,
        center = Offset(centerX, thumbY)
    )

    drawCircle(
        color = thumbBorderColor.copy(alpha = alpha),
        radius = thumbRadius,
        center = Offset(centerX, thumbY),
        style = Stroke(width = thumbBorderWidth)
    )
}