package io.trtc.tuikit.chat.uikit.components.messagelist.ui.widgets

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.trtc.tuikit.chat.uikit.components.theme.LocalTheme


@Composable
fun AsrTextBubble(
    modifier: Modifier = Modifier,
    isSelf: Boolean,
    isLoading: Boolean,
    asrText: String?,
    showMenu: Boolean = false,
    onLongPress: () -> Unit = {},
    onDismissMenu: () -> Unit = {},
    onHide: () -> Unit = {},
    onForward: () -> Unit = {},
    onCopy: () -> Unit = {}
) {
    val colors = LocalTheme.current.colors

    AuxiliaryTextBubble(
        modifier = modifier,
        isSelf = isSelf,
        isLoading = isLoading,
        showMenu = showMenu,
        topSpacing = 10.dp,
        contentPadding = PaddingValues(horizontal = 9.dp, vertical = 6.dp),
        onLongPress = onLongPress,
        onDismissMenu = onDismissMenu,
        onHide = onHide,
        onForward = onForward,
        onCopy = onCopy
    ) {
        Text(
            modifier = Modifier.padding(top = 3.dp),
            text = asrText ?: "",
            fontSize = 14.sp,
            lineHeight = 18.2.sp,
            color = if (isSelf) colors.textColorAntiPrimary else colors.textColorPrimary
        )
    }
}
