package io.trtc.tuikit.chat.settings

import android.view.WindowManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import io.trtc.tuikit.chat.R
import io.trtc.tuikit.chat.uikit.components.theme.LocalTheme
import kotlin.math.max
import kotlin.math.min

private const val DEFAULT_PRIMARY_COLOR = "#1C66E5"
private val ContentPadding = 16.dp
private val CornerRadiusDp = 12.dp
private val HorizontalMargin = 36.dp
private val PreviewSize = 48.dp
private val SpectrumHeight = 24.dp
private val SliderShadowPadding = 4.dp
private val ButtonMinWidth = 72.dp
private val ButtonMinHeight = 44.dp
private const val DIALOG_DIM_AMOUNT = 0.45f

fun normalizeHex(hex: String): String {
    val trimmed = hex.trim()
    val withHash = if (trimmed.startsWith("#")) trimmed else "#$trimmed"
    return withHash.uppercase()
}

private fun toHexColor(color: Int): String {
    return String.format("#%06X", 0xFFFFFF and color)
}

private fun parseSafeColor(hex: String): Int {
    return try {
        android.graphics.Color.parseColor(normalizeHex(hex))
    } catch (_: IllegalArgumentException) {
        android.graphics.Color.parseColor(DEFAULT_PRIMARY_COLOR)
    }
}

@Composable
fun PrimaryColorPickerDialog(
    selectedHex: String,
    onDismiss: () -> Unit,
    onColorSelected: (String) -> Unit
) {
    val colors = LocalTheme.current.colors
    val density = LocalDensity.current
    val screenWidthPx = LocalContext.current.resources.displayMetrics.widthPixels
    val dialogWidth = with(density) {
        val marginPx = HorizontalMargin.roundToPx()
        (screenWidthPx - marginPx * 2).coerceAtLeast(0).toDp()
    }
    val hsv = remember {
        FloatArray(3).apply {
            android.graphics.Color.colorToHSV(parseSafeColor(selectedHex), this)
        }
    }
    var currentColor by remember { mutableStateOf(android.graphics.Color.HSVToColor(hsv)) }

    fun updateColor() {
        currentColor = android.graphics.Color.HSVToColor(hsv)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        val view = LocalView.current
        SideEffect {
            val window = (view.parent as? DialogWindowProvider)?.window ?: return@SideEffect
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            window.attributes = window.attributes.apply {
                dimAmount = DIALOG_DIM_AMOUNT
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .width(dialogWidth)
                    .clip(RoundedCornerShape(CornerRadiusDp))
                    .background(colors.bgColorDialog)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    )
                    .padding(ContentPadding),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.compose_demo_primary_color_title),
                    style = TextStyle(
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textColorPrimary,
                        textAlign = TextAlign.Center
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                Box(
                    modifier = Modifier
                        .size(PreviewSize)
                        .clip(CircleShape)
                        .background(Color(currentColor))
                        .border(1.5.dp, colors.strokeColorPrimary, CircleShape)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = toHexColor(currentColor),
                    style = TextStyle(
                        fontSize = 13.sp,
                        color = colors.textColorSecondary,
                        textAlign = TextAlign.Center
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                ColorSliderLabel(stringResource(R.string.compose_demo_primary_color_hue))
                GradientSlider(
                    progress = hsv[0] / 360f,
                    gradientColors = (0..6).map {
                        Color(android.graphics.Color.HSVToColor(floatArrayOf(it * 60f, 1f, 1f)))
                    },
                    thumbColor = Color(currentColor),
                    onProgressChanged = { progress ->
                        hsv[0] = progress * 360f
                        updateColor()
                    }
                )
                Spacer(modifier = Modifier.height(12.dp))

                ColorSliderLabel(stringResource(R.string.compose_demo_primary_color_saturation))
                GradientSlider(
                    progress = hsv[1],
                    gradientColors = listOf(
                        Color(android.graphics.Color.HSVToColor(floatArrayOf(hsv[0], 0f, hsv[2]))),
                        Color(android.graphics.Color.HSVToColor(floatArrayOf(hsv[0], 1f, hsv[2])))
                    ),
                    thumbColor = Color(currentColor),
                    onProgressChanged = { progress ->
                        hsv[1] = progress
                        updateColor()
                    }
                )
                Spacer(modifier = Modifier.height(12.dp))

                ColorSliderLabel(stringResource(R.string.compose_demo_primary_color_brightness))
                GradientSlider(
                    progress = hsv[2],
                    gradientColors = listOf(
                        Color.Black,
                        Color(android.graphics.Color.HSVToColor(floatArrayOf(hsv[0], hsv[1], 1f)))
                    ),
                    thumbColor = Color(currentColor),
                    onProgressChanged = { progress ->
                        hsv[2] = progress
                        updateColor()
                    }
                )
                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DialogTextButton(
                        text = stringResource(R.string.compose_demo_primary_color_reset),
                        color = colors.textColorSecondary,
                        paddingStart = 8.dp,
                        paddingEnd = 16.dp,
                        onClick = {
                            android.graphics.Color.colorToHSV(
                                parseSafeColor(DEFAULT_PRIMARY_COLOR),
                                hsv
                            )
                            updateColor()
                        }
                    )

                    Spacer(modifier = Modifier.weight(1f))

                    DialogTextButton(
                        text = stringResource(R.string.compose_demo_about_cancel),
                        color = colors.textColorSecondary,
                        onClick = onDismiss
                    )

                    DialogTextButton(
                        text = stringResource(R.string.compose_demo_about_confirm),
                        color = colors.textColorLink,
                        fontWeight = FontWeight.Bold,
                        onClick = {
                            onColorSelected(toHexColor(currentColor))
                            onDismiss()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun DialogTextButton(
    text: String,
    color: Color,
    fontWeight: FontWeight = FontWeight.Normal,
    paddingStart: Dp = 16.dp,
    paddingEnd: Dp = 16.dp,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .defaultMinSize(minWidth = ButtonMinWidth, minHeight = ButtonMinHeight)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(start = paddingStart, top = 10.dp, end = paddingEnd, bottom = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = TextStyle(
                fontSize = 16.sp,
                fontWeight = fontWeight,
                color = color,
                textAlign = TextAlign.Center
            )
        )
    }
}

@Composable
private fun ColorSliderLabel(label: String) {
    val colors = LocalTheme.current.colors
    Text(
        text = label,
        style = TextStyle(
            fontSize = 11.sp,
            color = colors.textColorSecondary
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp)
    )
}

@Composable
private fun GradientSlider(
    progress: Float,
    gradientColors: List<Color>,
    thumbColor: Color,
    onProgressChanged: (Float) -> Unit
) {
    var sliderWidth by remember { mutableStateOf(0) }
    val thumbRadiusPx = with(LocalDensity.current) { (SpectrumHeight / 2).toPx() }

    fun updateFromX(x: Float) {
        if (sliderWidth <= 0) return
        val usableWidth = max(1f, sliderWidth - thumbRadiusPx * 2)
        val clampedX = max(thumbRadiusPx, min(sliderWidth - thumbRadiusPx, x))
        onProgressChanged(((clampedX - thumbRadiusPx) / usableWidth).coerceIn(0f, 1f))
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(SpectrumHeight + SliderShadowPadding * 2)
                .onSizeChanged { sliderWidth = it.width }
                .pointerInput(Unit) {
                    detectTapGestures { offset -> updateFromX(offset.x) }
                }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset -> updateFromX(offset.x) },
                        onDrag = { change, _ ->
                            change.consume()
                            updateFromX(change.position.x)
                        }
                    )
                }
        ) {
            val trackHeight = SpectrumHeight.toPx()
            val trackTop = (size.height - trackHeight) / 2f
            val trackRadius = trackHeight / 2f

            drawRoundRect(
                brush = Brush.horizontalGradient(gradientColors),
                topLeft = Offset(0f, trackTop),
                size = Size(size.width, trackHeight),
                cornerRadius = CornerRadius(trackRadius, trackRadius)
            )

            val thumbRadius = trackRadius
            val usableWidth = max(0f, size.width - thumbRadius * 2f)
            val thumbX = thumbRadius + progress.coerceIn(0f, 1f) * usableWidth
            val thumbY = size.height / 2f
            val bezel = 3.dp.toPx()

            drawCircle(
                color = Color(0x29000000),
                radius = thumbRadius,
                center = Offset(thumbX, thumbY + 1.dp.toPx())
            )
            drawCircle(
                color = Color.White,
                radius = thumbRadius,
                center = Offset(thumbX, thumbY)
            )
            drawCircle(
                color = thumbColor,
                radius = max(0f, thumbRadius - bezel),
                center = Offset(thumbX, thumbY)
            )
            drawCircle(
                color = Color(0x29000000),
                radius = thumbRadius - 0.5.dp.toPx(),
                center = Offset(thumbX, thumbY),
                style = Stroke(width = 1.dp.toPx())
            )
        }
    }
}
