package io.trtc.tuikit.chat.uikit.components.messagelist.listen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.trtc.tuikit.chat.uikit.compose.R
import io.trtc.tuikit.chat.uikit.components.theme.LocalTheme

@Composable
fun ListenPlaybackBar(
    state: ListenPlaybackState,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    collapsed: Boolean = false,
    onCollapsedChange: (Boolean) -> Unit = {}
) {
    if (!state.isActive) {
        return
    }
    val colors = LocalTheme.current.colors
    var expanded by remember { mutableStateOf(false) }
    val isExpanded = !collapsed && expanded
    val shape = RoundedCornerShape(100.dp)

    Box(
        modifier = modifier
            .shadow(4.dp, shape)
            .clip(shape)
            .background(colors.bgColorOperate, shape)
            .border(0.5.dp, colors.strokeColorPrimary, shape)
            .widthIn(max = 320.dp)
            .height(36.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                if (!isExpanded) {
                    expanded = true
                    onCollapsedChange(false)
                }
            }
            .padding(horizontal = 10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxHeight()
                .align(Alignment.CenterStart),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(20.dp),
                contentAlignment = Alignment.Center
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = colors.textColorLink,
                        strokeWidth = 1.5.dp
                    )
                } else {
                    Icon(
                        painter = painterResource(R.drawable.message_list_menu_listen_icon),
                        contentDescription = null,
                        tint = colors.textColorLink,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            if (isExpanded) {
                Text(
                    text = state.currentText,
                    fontSize = 13.sp,
                    color = colors.textColorSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .weight(1f, fill = false)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            expanded = false
                            onCollapsedChange(true)
                        }
                )
                Icon(
                    painter = painterResource(R.drawable.message_list_listen_close_icon),
                    contentDescription = null,
                    tint = colors.textColorPrimary,
                    modifier = Modifier
                        .padding(start = 4.dp)
                        .size(36.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onClose
                        )
                        .padding(10.dp)
                )
            }
        }
    }
}
