package io.trtc.tuikit.chat.uikit.components.conversationlist.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import io.trtc.tuikit.chat.uikit.components.theme.LocalTheme
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min

private val AccelerateDecelerateEasing = Easing { x -> (1f - cos(Math.PI.toFloat() * x)) / 2f }

internal class ConversationMenuItemData(
    val title: String,
    val dangerous: Boolean,
    val action: () -> Unit,
)

internal class ConversationMenuState(
    val conversationID: String,
    val anchorBoundsInWindow: IntRect,
    val items: List<ConversationMenuItemData>,
)

@Composable
internal fun ConversationPopupMenu(
    menuState: ConversationMenuState,
    containerBoundsInWindow: IntRect,
    onDismissed: (pendingAction: (() -> Unit)?) -> Unit,
) {
    val colors = LocalTheme.current.colors
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val textMeasurer = rememberTextMeasurer()

    val items = menuState.items
    val menuTextStyle = TextStyle(fontSize = 16.sp)

    val menuMetrics = remember(items, density) {
        var maxTextWidthPx = 0
        var maxTextHeightPx = 0
        items.forEach { item ->
            val result = textMeasurer.measure(AnnotatedString(item.title), menuTextStyle)
            maxTextWidthPx = max(maxTextWidthPx, result.size.width)
            maxTextHeightPx = max(maxTextHeightPx, result.size.height)
        }
        with(density) {
            val widthPx = maxTextWidthPx + 32.dp.roundToPx()
            val dividerPx = 0.5.dp.toPx().coerceAtLeast(1f)
            val itemHeightPx = maxTextHeightPx + 24.dp.roundToPx()
            val heightPx = itemHeightPx * items.size +
                (dividerPx * (items.size - 1).coerceAtLeast(0)).toInt()
            Triple(widthPx, heightPx, dividerPx)
        }
    }
    val menuWidthPx = menuMetrics.first
    val menuHeightPx = menuMetrics.second
    val dividerHeightPx = menuMetrics.third

    val anchor = menuState.anchorBoundsInWindow
    val anchorX = anchor.left - containerBoundsInWindow.left
    val anchorY = anchor.top - containerBoundsInWindow.top
    val anchorWidth = anchor.width
    val anchorHeight = anchor.height
    val hostWidth = containerBoundsInWindow.width
    val hostHeight = containerBoundsInWindow.height

    val position = remember(menuState, menuWidthPx, menuHeightPx, containerBoundsInWindow, layoutDirection) {
        with(density) {
            val margin = 4.dp.roundToPx()
            val horizontalInset = 16.dp.roundToPx()
            val aboveOverlap = 8.dp.roundToPx()
            val belowOverlap = max(anchorHeight * 3 / 5, 24.dp.roundToPx())
            val isRtl = layoutDirection == LayoutDirection.Rtl
            val preferredX = if (isRtl) {
                anchorX + horizontalInset
            } else {
                anchorX + anchorWidth - menuWidthPx - horizontalInset
            }
            val maxX = max(hostWidth - menuWidthPx - margin, margin)
            val x = preferredX.coerceIn(margin, maxX)
            val belowY = anchorY + anchorHeight - belowOverlap
            val aboveY = anchorY - menuHeightPx + aboveOverlap
            val showBelow = belowY + menuHeightPx <= hostHeight - margin || aboveY < margin
            val y = if (showBelow) {
                min(belowY, hostHeight - menuHeightPx - margin)
            } else {
                max(aboveY, margin)
            }
            Triple(x, y, showBelow)
        }
    }
    val showBelow = position.third

    var dismissing by remember { mutableStateOf(false) }
    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    val offsetDirection = if (showBelow) -1f else 1f
    val enterOffsetPx = with(density) { 8.dp.toPx() }
    val exitOffsetPx = with(density) { 6.dp.toPx() }
    val alphaAnim = remember { Animatable(0f) }
    val scaleAnim = remember { Animatable(0.92f) }
    val offsetYAnim = remember { Animatable(enterOffsetPx * offsetDirection) }

    LaunchedEffect(Unit) {
        launch {
            alphaAnim.animateTo(1f, tween(durationMillis = 180, easing = AccelerateDecelerateEasing))
        }
        launch {
            scaleAnim.animateTo(1f, tween(durationMillis = 180, easing = AccelerateDecelerateEasing))
        }
        launch {
            offsetYAnim.animateTo(0f, tween(durationMillis = 180, easing = AccelerateDecelerateEasing))
        }
    }

    LaunchedEffect(dismissing) {
        if (dismissing) {
            coroutineScope {
                launch {
                    alphaAnim.animateTo(0f, tween(durationMillis = 160, easing = AccelerateDecelerateEasing))
                }
                launch {
                    scaleAnim.animateTo(0.92f, tween(durationMillis = 160, easing = AccelerateDecelerateEasing))
                }
                launch {
                    offsetYAnim.animateTo(
                        exitOffsetPx * offsetDirection,
                        tween(durationMillis = 160, easing = AccelerateDecelerateEasing)
                    )
                }
            }
            onDismissed(pendingAction)
        }
    }

    fun startDismiss(action: (() -> Unit)?) {
        if (dismissing) return
        pendingAction = action
        dismissing = true
    }

    val pivotXFraction = with(density) {
        if (layoutDirection == LayoutDirection.Rtl) {
            16.dp.toPx() / menuWidthPx
        } else {
            1f - 16.dp.toPx() / menuWidthPx
        }
    }

    Popup(
        alignment = Alignment.TopStart,
        offset = IntOffset(position.first, position.second),
        onDismissRequest = { startDismiss(null) },
        properties = PopupProperties(focusable = true)
    ) {
        Surface(
            modifier = Modifier
                .graphicsLayer {
                    alpha = alphaAnim.value
                    scaleX = scaleAnim.value
                    scaleY = scaleAnim.value
                    translationY = offsetYAnim.value
                    transformOrigin = TransformOrigin(pivotXFraction, if (showBelow) 0f else 1f)
                },
            shape = RoundedCornerShape(8.dp),
            color = colors.bgColorOperate,
            shadowElevation = 8.dp
        ) {
            Column(modifier = Modifier.width(with(density) { menuWidthPx.toDp() })) {
                items.forEachIndexed { index, item ->
                    val itemShape = RoundedCornerShape(
                        topStart = if (index == 0) 8.dp else 0.dp,
                        topEnd = if (index == 0) 8.dp else 0.dp,
                        bottomEnd = if (index == items.lastIndex) 8.dp else 0.dp,
                        bottomStart = if (index == items.lastIndex) 8.dp else 0.dp,
                    )
                    Text(
                        text = item.title,
                        style = menuTextStyle,
                        color = if (item.dangerous) colors.textColorError else colors.textColorPrimary,
                        maxLines = 1,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(itemShape)
                            .clickable { startDismiss(item.action) }
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                    if (index < items.size - 1) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .height(with(density) { dividerHeightPx.toDp() })
                                .background(colors.strokeColorSecondary)
                        )
                    }
                }
            }
        }
    }
}
