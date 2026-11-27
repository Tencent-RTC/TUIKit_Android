package io.trtc.tuikit.chat.uikit.components.widgets

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.TextPaint
import androidx.annotation.DrawableRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import io.trtc.tuikit.chat.uikit.components.theme.LocalTheme
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class UIKitPopupMenuItem(
    val title: String,
    val onClick: () -> Unit,
    val enabled: Boolean = true,
    @DrawableRes val iconResId: Int = 0,
)

@Composable
fun UIKitPopupMenu(
    expanded: Boolean,
    items: List<UIKitPopupMenuItem>,
    onDismissRequest: () -> Unit,
) {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val colors = LocalTheme.current.colors
    val scope = rememberCoroutineScope()
    val latestItems = rememberUpdatedState(items)
    val latestDismiss = rememberUpdatedState(onDismissRequest)

    val configuration = LocalConfiguration.current
    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.roundToPx() }
    val metrics = remember(
        density.density,
        density.fontScale,
        screenWidthPx,
        configuration.screenWidthDp,
        items.map { it.title to it.iconResId }
    ) {
        PopupMenuMetrics.measure(
            items = items,
            density = density.density,
            fontScale = density.fontScale,
            screenWidthPx = screenWidthPx
        )
    }
    val chrome = remember(density) { PopupMenuChrome.fromDensity(density.density) }

    val estimatedTotalHeight = metrics.contentHeightPx + chrome.arrowHeightInt +
        chrome.shadowPadTop + chrome.shadowPadBottom
    var actualPlacement by remember(metrics.contentWidthPx, chrome) {
        mutableStateOf(
            PopupPlacement(
                offset = IntOffset.Zero,
                showAbove = false,
                arrowCenterXInBubble = chrome.shadowPadH + metrics.contentWidthPx / 2f
            )
        )
    }

    val wantShow = expanded && items.isNotEmpty()
    var popupVisible by remember { mutableStateOf(false) }
    var isExiting by remember { mutableStateOf(false) }
    var armedIndex by remember { mutableIntStateOf(-1) }
    val progress = remember { Animatable(0f) }
    var clickJob by remember { mutableStateOf<Job?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            clickJob?.cancel()
            clickJob = null
        }
    }

    LaunchedEffect(wantShow) {
        if (wantShow) {
            clickJob?.cancel()
            clickJob = null
            armedIndex = -1
            popupVisible = true
            isExiting = false
            progress.animateTo(1f, DialogEnterAnimationSpec)
        } else if (popupVisible) {
            isExiting = true
            progress.animateTo(0f, DialogExitAnimationSpec)
            popupVisible = false
            isExiting = false
        }
    }

    if (!wantShow && !popupVisible) {
        return
    }

    val animationProgress = progress.asState()
    val positionProvider = remember(
        metrics.contentWidthPx,
        estimatedTotalHeight,
        layoutDirection,
        chrome
    ) {
        object : PopupPositionProvider {
            override fun calculatePosition(
                anchorBounds: IntRect,
                windowSize: IntSize,
                layoutDirection: LayoutDirection,
                popupContentSize: IntSize
            ): IntOffset {
                val popupHeight = if (popupContentSize.height > 0) {
                    popupContentSize.height
                } else {
                    estimatedTotalHeight
                }
                val contentWidth = if (popupContentSize.width > 0) {
                    (popupContentSize.width - chrome.shadowPadH * 2).coerceAtLeast(0)
                } else {
                    metrics.contentWidthPx
                }
                val placement = computePopupPlacement(
                    anchor = anchorBounds,
                    windowSize = windowSize,
                    contentWidth = contentWidth,
                    popupHeight = popupHeight,
                    layoutDirection = layoutDirection,
                    chrome = chrome
                )
                if (actualPlacement != placement) {
                    actualPlacement = placement
                }
                return placement.offset
            }
        }
    }

    Popup(
        popupPositionProvider = positionProvider,
        onDismissRequest = {
            if (!isExiting) {
                latestDismiss.value()
            }
        },
        properties = PopupProperties(
            focusable = true,
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            clippingEnabled = false
        )
    ) {
        UIKitPopupBubble(
            contentWidthPx = metrics.contentWidthPx,
            chrome = chrome,
            isArrowOnTop = !actualPlacement.showAbove,
            arrowCenterXInBubble = actualPlacement.arrowCenterXInBubble,
            bubbleColor = colors.bgColorDialog,
            progress = animationProgress
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                val hasAnyIcon = metrics.hasAnyIcon
                latestItems.value.forEachIndexed { index, item ->
                    UIKitPopupMenuRow(
                        item = item,
                        hasAnyIcon = hasAnyIcon,
                        isFirst = index == 0,
                        isLast = index == latestItems.value.lastIndex,
                        armed = armedIndex == index,
                        clickEnabled = item.enabled && armedIndex < 0 && !isExiting,
                        onClick = {
                            val clickedItem = item
                            armedIndex = index
                            clickJob?.cancel()
                            clickJob = scope.launch {
                                delay(ITEM_CLICK_FEEDBACK_DELAY_MS)
                                clickedItem.onClick()
                                latestDismiss.value()
                            }
                        }
                    )
                    if (index < latestItems.value.lastIndex) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp)
                                .height(1.dp)
                                .background(colors.strokeColorSecondary)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun UIKitPopupBubble(
    contentWidthPx: Int,
    chrome: PopupMenuChrome,
    isArrowOnTop: Boolean,
    arrowCenterXInBubble: Float,
    bubbleColor: Color,
    progress: State<Float>,
    content: @Composable () -> Unit,
) {
    val bubbleColorArgb = bubbleColor.toArgb()
    Layout(
        content = content,
        modifier = Modifier
            .graphicsLayer {
                val animationProgress = progress.value
                alpha = animationProgress
                val scale = 0.9f + 0.1f * animationProgress
                scaleX = scale
                scaleY = scale
                transformOrigin = TransformOrigin.Center
                clip = false
            }
            .drawWithCache {
                val path = createBubbleWithArrowPath(
                    width = size.width,
                    height = size.height,
                    chrome = chrome,
                    isArrowOnTop = isArrowOnTop,
                    arrowCenterXInBubble = arrowCenterXInBubble
                )
                val bitmapWidth = size.width.roundToInt().coerceAtLeast(1)
                val bitmapHeight = size.height.roundToInt().coerceAtLeast(1)
                val bitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888)
                val softwareCanvas = AndroidCanvas(bitmap)
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.FILL
                    color = bubbleColorArgb
                    setShadowLayer(
                        chrome.shadowBlur,
                        0f,
                        chrome.shadowOffsetY,
                        SHADOW_COLOR
                    )
                }
                softwareCanvas.drawPath(path, paint)
                paint.clearShadowLayer()
                softwareCanvas.drawPath(path, paint)
                val image = bitmap.asImageBitmap()
                onDrawBehind {
                    drawImage(image)
                }
            }
    ) { measurables, _ ->
        val placeable = measurables.first().measure(Constraints.fixedWidth(contentWidthPx))
        val width = contentWidthPx + chrome.shadowPadH * 2
        val height = placeable.height + chrome.shadowPadTop + chrome.shadowPadBottom +
            chrome.arrowHeightInt
        layout(width, height) {
            val y = chrome.shadowPadTop + if (isArrowOnTop) chrome.arrowHeightInt else 0
            placeable.place(chrome.shadowPadH, y)
        }
    }
}

@Composable
private fun UIKitPopupMenuRow(
    item: UIKitPopupMenuItem,
    hasAnyIcon: Boolean,
    isFirst: Boolean,
    isLast: Boolean,
    armed: Boolean,
    clickEnabled: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalTheme.current.colors
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val highlight = item.enabled && (isPressed || armed)
    val contentColor = if (item.enabled) colors.textColorPrimary else colors.textColorDisable
    val itemShape = RoundedCornerShape(
        topStart = if (isFirst) 8.dp else 0.dp,
        topEnd = if (isFirst) 8.dp else 0.dp,
        bottomStart = if (isLast) 8.dp else 0.dp,
        bottomEnd = if (isLast) 8.dp else 0.dp
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(itemShape)
            .background(
                if (highlight) colors.textColorPrimary.copy(alpha = PRESS_ALPHA) else Color.Transparent
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = clickEnabled,
                onClick = onClick
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (hasAnyIcon) {
            if (item.iconResId != 0) {
                Image(
                    painter = painterResource(item.iconResId),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    colorFilter = ColorFilter.tint(contentColor)
                )
            } else {
                Spacer(modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.width(8.dp))
        }
        BasicText(
            text = item.title,
            modifier = Modifier.weight(1f),
            style = TextStyle(
                color = contentColor,
                fontSize = 16.sp,
                fontWeight = FontWeight.Normal,
                platformStyle = PlatformTextStyle(includeFontPadding = false)
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private data class PopupMenuMetrics(
    val contentWidthPx: Int,
    val contentHeightPx: Int,
    val hasAnyIcon: Boolean,
) {
    companion object {
        fun measure(
            items: List<UIKitPopupMenuItem>,
            density: Float,
            fontScale: Float,
            screenWidthPx: Int
        ): PopupMenuMetrics {
            if (items.isEmpty()) {
                return PopupMenuMetrics(0, 0, false)
            }
            val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = 16f * fontScale * density
                typeface = Typeface.DEFAULT
            }
            val maxTextWidth = items.maxOf { item ->
                ceil(textPaint.measureText(item.title).toDouble()).toInt()
            }
            val hasAnyIcon = items.any { it.iconResId != 0 }
            val iconSpace = if (hasAnyIcon) roundDp(20, density) + roundDp(8, density) else 0
            val contentPadding = roundDp(16, density) * 2
            val minWidth = roundDp(72, density)
            val maxWidth = screenWidthPx - roundDp(32, density)
            val contentWidth = (maxTextWidth + iconSpace + contentPadding).coerceIn(minWidth, maxWidth)

            val textHeight = ceil((textPaint.fontMetrics.descent - textPaint.fontMetrics.ascent).toDouble()).toInt()
            val iconSize = roundDp(20, density)
            val verticalPadding = roundDp(10, density) * 2
            val itemHeight = max(iconSize, textHeight) + verticalPadding
            val dividerHeight = max(roundDp(1, density), 1)
            val contentHeight = itemHeight * items.size + dividerHeight * (items.size - 1).coerceAtLeast(0)
            return PopupMenuMetrics(contentWidth, contentHeight, hasAnyIcon)
        }
    }
}

private data class PopupMenuChrome(
    val shadowPadH: Int,
    val shadowPadTop: Int,
    val shadowPadBottom: Int,
    val arrowWidth: Float,
    val arrowHeight: Float,
    val arrowHeightInt: Int,
    val cornerRadius: Float,
    val shadowBlur: Float,
    val shadowOffsetY: Float,
    val screenMargin: Int,
    val visualGap: Int,
) {
    companion object {
        fun fromDensity(density: Float): PopupMenuChrome {
            val arrowHeight = 6f * density
            return PopupMenuChrome(
                shadowPadH = roundDp(8, density),
                shadowPadTop = roundDp(6, density),
                shadowPadBottom = roundDp(10, density),
                arrowWidth = 12f * density,
                arrowHeight = arrowHeight,
                arrowHeightInt = arrowHeight.toInt(),
                cornerRadius = 8f * density,
                shadowBlur = 8f * density,
                shadowOffsetY = 2f * density,
                screenMargin = roundDp(8, density),
                visualGap = roundDp(4, density)
            )
        }
    }
}

private data class PopupPlacement(
    val offset: IntOffset,
    val showAbove: Boolean,
    val arrowCenterXInBubble: Float,
)

private fun computePopupPlacement(
    anchor: IntRect,
    windowSize: IntSize,
    contentWidth: Int,
    popupHeight: Int,
    layoutDirection: LayoutDirection,
    chrome: PopupMenuChrome,
): PopupPlacement {
    val popupW = contentWidth + chrome.shadowPadH * 2
    val preferredX = if (layoutDirection == LayoutDirection.Rtl) {
        chrome.screenMargin - chrome.shadowPadH
    } else {
        windowSize.width - contentWidth - chrome.shadowPadH - chrome.screenMargin
    }
    val maxX = (windowSize.width - popupW - chrome.screenMargin).coerceAtLeast(chrome.screenMargin)
    val popupX = preferredX.coerceIn(chrome.screenMargin, maxX)

    val belowPopupY = anchor.top + anchor.height + chrome.visualGap - chrome.shadowPadTop
    val abovePopupY = anchor.top - popupHeight - chrome.visualGap + chrome.shadowPadBottom
    val showAbove = belowPopupY + popupHeight > windowSize.height && abovePopupY >= chrome.screenMargin
    val popupY = if (showAbove) {
        abovePopupY.coerceAtLeast(chrome.screenMargin)
    } else {
        belowPopupY.coerceAtMost(windowSize.height - popupHeight - chrome.screenMargin)
    }

    val anchorCenterX = anchor.left + anchor.width / 2f
    return PopupPlacement(
        offset = IntOffset(popupX, popupY),
        showAbove = showAbove,
        arrowCenterXInBubble = anchorCenterX - popupX
    )
}

private fun createBubbleWithArrowPath(
    width: Float,
    height: Float,
    chrome: PopupMenuChrome,
    isArrowOnTop: Boolean,
    arrowCenterXInBubble: Float,
): android.graphics.Path {
    val left = chrome.shadowPadH.toFloat()
    val top = chrome.shadowPadTop.toFloat() + if (isArrowOnTop) chrome.arrowHeight else 0f
    val right = width - chrome.shadowPadH.toFloat()
    val bottom = height - chrome.shadowPadBottom.toFloat() - if (isArrowOnTop) 0f else chrome.arrowHeight
    val cr = chrome.cornerRadius
    val arrowHalfWidth = chrome.arrowWidth / 2f
    val minCenter = left + cr
    val maxCenter = right - cr
    val centerX = if (minCenter <= maxCenter) {
        arrowCenterXInBubble.coerceIn(minCenter, maxCenter)
    } else {
        (left + right) / 2f
    }
    val tipY = if (isArrowOnTop) top - chrome.arrowHeight else bottom + chrome.arrowHeight
    val path = android.graphics.Path()
    val oval = RectF()
    if (isArrowOnTop) {
        path.moveTo(left + cr, top)
        path.lineTo(centerX - arrowHalfWidth, top)
        path.lineTo(centerX, tipY)
        path.lineTo(centerX + arrowHalfWidth, top)
        path.lineTo(right - cr, top)
        oval.set(right - 2 * cr, top, right, top + 2 * cr)
        path.arcTo(oval, 270f, 90f, false)
        path.lineTo(right, bottom - cr)
        oval.set(right - 2 * cr, bottom - 2 * cr, right, bottom)
        path.arcTo(oval, 0f, 90f, false)
        path.lineTo(left + cr, bottom)
        oval.set(left, bottom - 2 * cr, left + 2 * cr, bottom)
        path.arcTo(oval, 90f, 90f, false)
        path.lineTo(left, top + cr)
        oval.set(left, top, left + 2 * cr, top + 2 * cr)
        path.arcTo(oval, 180f, 90f, false)
    } else {
        path.moveTo(left + cr, top)
        path.lineTo(right - cr, top)
        oval.set(right - 2 * cr, top, right, top + 2 * cr)
        path.arcTo(oval, 270f, 90f, false)
        path.lineTo(right, bottom - cr)
        oval.set(right - 2 * cr, bottom - 2 * cr, right, bottom)
        path.arcTo(oval, 0f, 90f, false)
        path.lineTo(centerX + arrowHalfWidth, bottom)
        path.lineTo(centerX, tipY)
        path.lineTo(centerX - arrowHalfWidth, bottom)
        path.lineTo(left + cr, bottom)
        oval.set(left, bottom - 2 * cr, left + 2 * cr, bottom)
        path.arcTo(oval, 90f, 90f, false)
        path.lineTo(left, top + cr)
        oval.set(left, top, left + 2 * cr, top + 2 * cr)
        path.arcTo(oval, 180f, 90f, false)
    }
    path.close()
    return path
}

private fun roundDp(value: Int, density: Float): Int = (value * density + 0.5f).toInt()

private val DialogDecelerateEasing = Easing { fraction -> 1f - (1f - fraction) * (1f - fraction) }
private val DialogAccelerateEasing = Easing { fraction -> fraction * fraction }
private val DialogEnterAnimationSpec = tween<Float>(durationMillis = 200, easing = DialogDecelerateEasing)
private val DialogExitAnimationSpec = tween<Float>(durationMillis = 200, easing = DialogAccelerateEasing)

private const val ITEM_CLICK_FEEDBACK_DELAY_MS = 120L
private const val PRESS_ALPHA = 30f / 255f
private const val SHADOW_COLOR: Int = 0x33000000
