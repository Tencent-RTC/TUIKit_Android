package io.trtc.tuikit.chat.uikit.components.messagelist.ui.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.trtc.tuikit.chat.uikit.components.theme.LocalTheme
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.LocalBubbleMaxWidth

@Composable
fun AuxiliaryTextBubble(
    modifier: Modifier = Modifier,
    isSelf: Boolean,
    isLoading: Boolean,
    showMenu: Boolean = false,
    topSpacing: Dp = 6.dp,
    contentPadding: PaddingValues = PaddingValues(horizontal = 9.dp, vertical = 6.dp),
    onLongPress: () -> Unit = {},
    onDismissMenu: () -> Unit = {},
    onHide: () -> Unit = {},
    onForward: () -> Unit = {},
    onCopy: () -> Unit = {},
    content: @Composable () -> Unit
) {
    val colors = LocalTheme.current.colors
    val bubbleColor = if (isSelf) colors.bgColorBubbleOwn else colors.bgColorBubbleReciprocal
    val maxWidth = LocalBubbleMaxWidth.current
    var bubbleBounds by remember { mutableStateOf<Rect?>(null) }

    Spacer(modifier = Modifier.height(topSpacing))

    Box(
        modifier = modifier
            .widthIn(max = maxWidth)
            .onGloballyPositioned { coords ->
                val pos = coords.positionInWindow()
                val size = coords.size
                bubbleBounds = Rect(pos.x, pos.y, pos.x + size.width, pos.y + size.height)
            }
            .clip(RoundedCornerShape(9.dp))
            .background(bubbleColor)
            .then(
                if (!isLoading) {
                    Modifier.pointerInput(Unit) {
                        detectTapGestures(onLongPress = { onLongPress() })
                    }
                } else {
                    Modifier
                }
            )
            .padding(contentPadding),
        contentAlignment = Alignment.Center
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = colors.textColorSecondary
            )
        } else {
            content()
        }
    }

    if (showMenu) {
        AuxiliaryTextPopupMenu(
            anchorBounds = bubbleBounds,
            onDismiss = onDismissMenu,
            onForward = onForward,
            onCopy = onCopy
        )
    }
}
