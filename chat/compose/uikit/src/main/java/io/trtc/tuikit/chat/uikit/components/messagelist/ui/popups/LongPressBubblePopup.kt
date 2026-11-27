package io.trtc.tuikit.chat.uikit.components.messagelist.ui.popups

import android.graphics.Bitmap
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.LocalMessageListWindowBounds
import io.trtc.tuikit.chat.uikit.components.theme.LocalTheme
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

internal data class LongPressPopupAction(
    val title: String,
    val iconResId: Int,
    val onClick: () -> Unit,
    val isReactionEntry: Boolean = false
)

private val DecelerateQuintEasing = Easing { x -> 1f - (1f - x).pow(5f) }
private val DecelerateCubicEasing = Easing { x -> 1f - (1f - x).pow(3f) }
private val AccelerateDecelerateEasing = Easing { x -> (1f - cos(Math.PI.toFloat() * x)) / 2f }

private fun renderBubbleShadowBitmap(
    width: Int,
    height: Int,
    bubblePath: android.graphics.Path,
    shadowBlurPx: Float,
    shadowOffsetYPx: Float,
    shadowColor: Int
): ImageBitmap {
    val scale = 0.5f
    val bitmapWidth = (width * scale).roundToInt().coerceAtLeast(1)
    val bitmapHeight = (height * scale).roundToInt().coerceAtLeast(1)
    val bitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    canvas.scale(scale, scale)
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        style = android.graphics.Paint.Style.FILL
        color = android.graphics.Color.TRANSPARENT
        setShadowLayer(shadowBlurPx, 0f, shadowOffsetYPx, shadowColor)
    }
    canvas.drawPath(bubblePath, paint)
    return bitmap.asImageBitmap()
}

internal class LongPressPopupAnimator(
    val scale: Animatable<Float, AnimationVector1D>,
    val alpha: Animatable<Float, AnimationVector1D>,
    val requestDismiss: () -> Unit
)

@Composable
internal fun rememberLongPressPopupAnimator(onDismiss: () -> Unit): LongPressPopupAnimator {
    val scale = remember { Animatable(LongPressDimens.POPUP_INITIAL_SCALE) }
    val alpha = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val dismissStarted = remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        launch {
            scale.animateTo(
                1f,
                tween(LongPressDimens.POPUP_SCALE_DURATION_MS, easing = DecelerateQuintEasing)
            )
        }
        launch {
            alpha.animateTo(
                1f,
                tween(LongPressDimens.POPUP_ALPHA_DURATION_MS, easing = DecelerateCubicEasing)
            )
        }
    }
    val requestDismiss: () -> Unit = remember(scope, onDismiss) {
        {
            if (!dismissStarted.value) {
                dismissStarted.value = true
                scope.launch {
                    val scaleJob = launch {
                        scale.animateTo(
                            LongPressDimens.POPUP_INITIAL_SCALE,
                            tween(LongPressDimens.POPUP_SCALE_DURATION_MS, easing = DecelerateQuintEasing)
                        )
                    }
                    launch {
                        alpha.animateTo(
                            0f,
                            tween(LongPressDimens.POPUP_ALPHA_DURATION_MS, easing = DecelerateCubicEasing)
                        )
                    }
                    scaleJob.join()
                    onDismiss()
                }
            }
        }
    }
    return remember(scale, alpha, requestDismiss) {
        LongPressPopupAnimator(scale, alpha, requestDismiss)
    }
}

@Composable
internal fun LongPressBubblePopup(
    anchorBounds: Rect?,
    contentWidth: Dp,
    menuContentHeight: Dp,
    emojiContentHeight: Dp? = null,
    emojiExpanded: Boolean = false,
    onDismiss: () -> Unit,
    content: @Composable (animatedDismiss: () -> Unit) -> Unit
) {
    val colors = LocalTheme.current.colors
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val listBounds = LocalMessageListWindowBounds.current
    val animator = rememberLongPressPopupAnimator(onDismiss)

    val targetContentHeight =
        if (emojiExpanded && emojiContentHeight != null) emojiContentHeight else menuContentHeight
    val animatedContentHeight by animateDpAsState(
        targetValue = targetContentHeight,
        animationSpec = tween(
            LongPressDimens.SWITCH_ANIMATION_DURATION_MS,
            easing = AccelerateDecelerateEasing
        ),
        label = "longPressBubbleHeight"
    )
    val maxContentHeight =
        if (emojiContentHeight != null) maxOf(menuContentHeight, emojiContentHeight) else menuContentHeight

    val shadowPadH = with(density) { LongPressDimens.SHADOW_PAD_H_DP.dp.roundToPx() }
    val shadowPadTop = with(density) { LongPressDimens.SHADOW_PAD_TOP_DP.dp.roundToPx() }
    val shadowPadBottom = with(density) { LongPressDimens.SHADOW_PAD_BOTTOM_DP.dp.roundToPx() }
    val arrowHeightPx = with(density) { LongPressDimens.ARROW_HEIGHT_DP.dp.roundToPx() }
    val contentWidthPx = with(density) { contentWidth.roundToPx() }
    val screenWidth = with(density) { configuration.screenWidthDp.dp.roundToPx() }
    val maxBubbleHeightPx =
        with(density) { maxContentHeight.roundToPx() } + shadowPadTop + shadowPadBottom + arrowHeightPx

    val position = remember(anchorBounds, listBounds, contentWidthPx, maxBubbleHeightPx) {
        val anchor = if (anchorBounds != null && anchorBounds.width > 0f) {
            LongPressPositionCalculator.AnchorBounds(
                screenX = anchorBounds.left.roundToInt(),
                screenY = anchorBounds.top.roundToInt(),
                width = anchorBounds.width.roundToInt(),
                height = anchorBounds.height.roundToInt()
            )
        } else {
            LongPressPositionCalculator.AnchorBounds(0, 0, contentWidthPx, 1)
        }
        LongPressPositionCalculator.calculate(
            anchor = anchor,
            list = LongPressPositionCalculator.ListBounds(
                top = listBounds.top.roundToInt(),
                bottom = listBounds.bottom.roundToInt().coerceAtLeast(listBounds.top.roundToInt() + 1)
            ),
            screenWidth = screenWidth,
            contentWidth = contentWidthPx,
            maxBubbleHeight = maxBubbleHeightPx,
            screenMargin = with(density) { LongPressDimens.SCREEN_MARGIN_DP.dp.roundToPx() },
            visualGap = with(density) { LongPressDimens.VISUAL_GAP_DP.dp.roundToPx() },
            chrome = LongPressPositionCalculator.Chrome(
                shadowPadH = shadowPadH,
                shadowPadTop = shadowPadTop,
                shadowPadBottom = shadowPadBottom
            )
        )
    }

    val isArrowOnTop = !position.showAbove
    val arrowHeightDp = LongPressDimens.ARROW_HEIGHT_DP.dp
    val arrowHeightPxF = with(density) { arrowHeightDp.toPx() }
    val arrowWidthPxF = with(density) { LongPressDimens.ARROW_WIDTH_DP.dp.toPx() }
    val cornerRadiusPxF = with(density) { LongPressDimens.BUBBLE_CORNER_RADIUS_DP.dp.toPx() }
    val bubbleShape = remember(isArrowOnTop, position.arrowCenterX) {
        GenericShape { size, _ ->
            val top = if (isArrowOnTop) arrowHeightPxF else 0f
            val bottom = if (isArrowOnTop) size.height else size.height - arrowHeightPxF
            addPath(
                createBubblePath(
                    left = 0f,
                    top = top,
                    right = size.width,
                    bottom = bottom,
                    radius = cornerRadiusPxF,
                    arrowWidth = arrowWidthPxF,
                    arrowHeight = arrowHeightPxF,
                    isArrowOnTop = isArrowOnTop,
                    arrowCenterX = position.arrowCenterX
                )
            )
        }
    }

    Popup(
        popupPositionProvider = object : PopupPositionProvider {
            override fun calculatePosition(
                anchorBounds: IntRect,
                windowSize: IntSize,
                layoutDirection: LayoutDirection,
                popupContentSize: IntSize
            ): IntOffset {
                return IntOffset(position.popupX, position.popupY)
            }
        },
        onDismissRequest = { animator.requestDismiss() },
        properties = PopupProperties(focusable = true, clippingEnabled = false)
    ) {
        Box(
            modifier = Modifier
                .size(
                    width = with(density) { position.popupWidth.toDp() },
                    height = with(density) { maxBubbleHeightPx.toDp() }
                )
                .graphicsLayer {
                    scaleX = animator.scale.value
                    scaleY = animator.scale.value
                    alpha = animator.alpha.value
                    transformOrigin = TransformOrigin.Center
                }
                .pointerInput(Unit) {
                    detectTapGestures { animator.requestDismiss() }
                }
        ) {
            Box(
                modifier = Modifier
                    .align(if (position.showAbove) Alignment.BottomCenter else Alignment.TopCenter)
                    .pointerInput(Unit) {
                        detectTapGestures { }
                    }
                    .drawWithCache {
                        val shadowPadHPx = LongPressDimens.SHADOW_PAD_H_DP.dp.toPx()
                        val shadowPadTopPx = LongPressDimens.SHADOW_PAD_TOP_DP.dp.toPx()
                        val shadowPadBottomPx = LongPressDimens.SHADOW_PAD_BOTTOM_DP.dp.toPx()
                        val arrowHPx = LongPressDimens.ARROW_HEIGHT_DP.dp.toPx()
                        val bubbleTop = if (isArrowOnTop) shadowPadTopPx + arrowHPx else shadowPadTopPx
                        val bubbleBottom = if (isArrowOnTop) {
                            size.height - shadowPadBottomPx
                        } else {
                            size.height - shadowPadBottomPx - arrowHPx
                        }
                        val shadowPath = createBubblePath(
                            left = shadowPadHPx,
                            top = bubbleTop,
                            right = size.width - shadowPadHPx,
                            bottom = bubbleBottom,
                            radius = LongPressDimens.BUBBLE_CORNER_RADIUS_DP.dp.toPx(),
                            arrowWidth = LongPressDimens.ARROW_WIDTH_DP.dp.toPx(),
                            arrowHeight = arrowHPx,
                            isArrowOnTop = isArrowOnTop,
                            arrowCenterX = position.arrowCenterX + shadowPadHPx
                        ).asAndroidPath()
                        val shadowBitmap = renderBubbleShadowBitmap(
                            width = size.width.roundToInt(),
                            height = size.height.roundToInt(),
                            bubblePath = shadowPath,
                            shadowBlurPx = LongPressDimens.SHADOW_BLUR_DP.dp.toPx(),
                            shadowOffsetYPx = LongPressDimens.SHADOW_OFFSET_Y_DP.dp.toPx(),
                            shadowColor = LongPressDimens.SHADOW_COLOR
                        )
                        onDrawBehind {
                            drawImage(
                                image = shadowBitmap,
                                dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                                filterQuality = FilterQuality.Low
                            )
                        }
                    }
                    .padding(
                        start = LongPressDimens.SHADOW_PAD_H_DP.dp,
                        end = LongPressDimens.SHADOW_PAD_H_DP.dp,
                        top = LongPressDimens.SHADOW_PAD_TOP_DP.dp,
                        bottom = LongPressDimens.SHADOW_PAD_BOTTOM_DP.dp
                    )
                    .width(contentWidth)
                    .height(animatedContentHeight + arrowHeightDp)
                    .background(colors.dropdownColorDefault, bubbleShape)
                    .clipToBounds()
                    .padding(
                        top = if (isArrowOnTop) arrowHeightDp else 0.dp,
                        bottom = if (isArrowOnTop) 0.dp else arrowHeightDp
                    )
            ) {
                content(animator.requestDismiss)
            }
        }
    }
}

@Composable
internal fun LongPressActionGrid(
    actions: List<LongPressPopupAction>,
    columnCount: Int,
    onDismiss: () -> Unit,
    onReactionEntry: () -> Unit
) {
    val colors = LocalTheme.current.colors
    val density = LocalDensity.current
    val pages = actions.chunked(LongPressDimens.PAGE_SIZE)
    val pagerState = rememberPagerState { pages.size }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (pages.size <= 1) {
            ActionPage(
                items = pages.firstOrNull().orEmpty(),
                columnCount = columnCount,
                onDismiss = onDismiss,
                onReactionEntry = onReactionEntry
            )
        } else {
            val hairline = with(density) { 1f.toDp() }
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .width((LongPressDimens.CELL_WIDTH_DP * LongPressDimens.COLUMNS + 8).dp)
                    .height((8 + LongPressDimens.CELL_HEIGHT_DP * LongPressDimens.MAX_ROWS).dp + hairline)
            ) { page ->
                ActionPage(
                    items = pages[page],
                    columnCount = LongPressDimens.COLUMNS,
                    onDismiss = onDismiss,
                    onReactionEntry = onReactionEntry
                )
            }
            Row(
                modifier = Modifier.padding(vertical = LongPressDimens.PAGE_INDICATOR_VERTICAL_PADDING_DP.dp),
                horizontalArrangement = Arrangement.spacedBy(LongPressDimens.PAGE_INDICATOR_DOT_SPACING_DP.dp)
            ) {
                repeat(pages.size) { index ->
                    Box(
                        modifier = Modifier
                            .size(LongPressDimens.PAGE_INDICATOR_DOT_SIZE_DP.dp)
                            .background(
                                color = if (index == pagerState.currentPage) {
                                    colors.textColorPrimary
                                } else {
                                    colors.textColorPrimary.copy(alpha = LongPressDimens.PAGE_INDICATOR_INACTIVE_ALPHA / 255f)
                                },
                                shape = CircleShape
                            )
                    )
                }
            }
        }
    }
}

@Composable
private fun ActionPage(
    items: List<LongPressPopupAction>,
    columnCount: Int,
    onDismiss: () -> Unit,
    onReactionEntry: () -> Unit
) {
    val colors = LocalTheme.current.colors
    val rows = items.chunked(columnCount)
    Column(modifier = Modifier.padding(4.dp)) {
        rows.forEachIndexed { rowIndex, rowItems ->
            Row {
                repeat(columnCount) { col ->
                    val action = rowItems.getOrNull(col)
                    if (action != null) {
                        ActionItemCell(
                            action = action,
                            onClick = {
                                if (action.isReactionEntry) {
                                    onReactionEntry()
                                } else {
                                    onDismiss()
                                    action.onClick()
                                }
                            }
                        )
                    } else {
                        Spacer(
                            modifier = Modifier.size(
                                width = LongPressDimens.CELL_WIDTH_DP.dp,
                                height = LongPressDimens.CELL_HEIGHT_DP.dp
                            )
                        )
                    }
                }
            }
            if (rowIndex < rows.lastIndex) {
                Box(
                    modifier = Modifier
                        .width((LongPressDimens.CELL_WIDTH_DP * columnCount).dp)
                        .padding(horizontal = 8.dp)
                        .height(Dp.Hairline)
                        .background(colors.strokeColorPrimary.copy(alpha = 140 / 255f))
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActionItemCell(
    action: LongPressPopupAction,
    onClick: () -> Unit
) {
    val colors = LocalTheme.current.colors
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    Column(
        modifier = Modifier
            .size(
                width = LongPressDimens.CELL_WIDTH_DP.dp,
                height = LongPressDimens.CELL_HEIGHT_DP.dp
            )
            .clip(RoundedCornerShape(LongPressDimens.ACTION_ITEM_CORNER_RADIUS_DP.dp))
            .background(if (isPressed) colors.dropdownColorHover else Color.Transparent)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(
                    color = colors.textColorPrimary.copy(alpha = LongPressDimens.ACTION_ITEM_RIPPLE_ALPHA / 255f),
                    bounded = true
                )
            ) { onClick() }
            .padding(top = LongPressDimens.ACTION_ITEM_TOP_PADDING_DP.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (action.iconResId != 0) {
            Icon(
                painter = painterResource(action.iconResId),
                contentDescription = action.title,
                modifier = Modifier.size(LongPressDimens.ACTION_ITEM_ICON_SIZE_DP.dp),
                tint = colors.textColorPrimary
            )
        }
        Text(
            text = action.title,
            fontSize = 10.sp,
            color = colors.textColorPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = LongPressDimens.ACTION_ITEM_TEXT_TOP_PADDING_DP.dp)
        )
    }
}

internal fun createBubblePath(
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    radius: Float,
    arrowWidth: Float,
    arrowHeight: Float,
    isArrowOnTop: Boolean,
    arrowCenterX: Float
): Path {
    val cr = min(radius, min(right - left, bottom - top) / 2f)
    val halfArrow = min(arrowWidth / 2f, ((right - left) / 2f - cr).coerceAtLeast(0f))
    val centerMin = left + cr + halfArrow
    val centerMax = right - cr - halfArrow
    val centerX = if (centerMin <= centerMax) arrowCenterX.coerceIn(centerMin, centerMax) else (left + right) / 2f
    val tipY = if (isArrowOnTop) top - arrowHeight else bottom + arrowHeight
    return Path().apply {
        if (isArrowOnTop) {
            moveTo(left + cr, top)
            lineTo(centerX - halfArrow, top)
            lineTo(centerX, tipY)
            lineTo(centerX + halfArrow, top)
            lineTo(right - cr, top)
            quadraticTo(right, top, right, top + cr)
            lineTo(right, bottom - cr)
            quadraticTo(right, bottom, right - cr, bottom)
            lineTo(left + cr, bottom)
            quadraticTo(left, bottom, left, bottom - cr)
            lineTo(left, top + cr)
            quadraticTo(left, top, left + cr, top)
        } else {
            moveTo(left + cr, top)
            lineTo(right - cr, top)
            quadraticTo(right, top, right, top + cr)
            lineTo(right, bottom - cr)
            quadraticTo(right, bottom, right - cr, bottom)
            lineTo(centerX + halfArrow, bottom)
            lineTo(centerX, tipY)
            lineTo(centerX - halfArrow, bottom)
            lineTo(left + cr, bottom)
            quadraticTo(left, bottom, left, bottom - cr)
            lineTo(left, top + cr)
            quadraticTo(left, top, left + cr, top)
        }
        close()
    }
}
