package io.trtc.tuikit.chat

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.trtc.tuikit.chat.uikit.components.theme.LocalTheme
import io.trtc.tuikit.chat.uikit.components.widgets.UIKitPopupMenu
import io.trtc.tuikit.chat.uikit.components.widgets.UIKitPopupMenuItem
import io.trtc.tuikit.chat.uikit.compose.R as UiKitR

data class MenuItem(
    val enabled: Boolean = true,
    val text: String,
    val onClick: () -> Unit,
    @DrawableRes val iconResId: Int = 0,
)

@Composable
fun AddMoreButton(
    modifier: Modifier = Modifier,
    iconResId: Int = UiKitR.drawable.uikit_ic_add_circle,
    menuItems: List<MenuItem> = emptyList()
) {
    val colors = LocalTheme.current.colors
    var showMenu by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }

    Box {
        Image(
            painter = painterResource(iconResId),
            contentDescription = "More",
            colorFilter = ColorFilter.tint(colors.textColorPrimary),
            modifier = modifier
                .size(24.dp)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = {
                        if (menuItems.isNotEmpty()) {
                            showMenu = true
                        }
                    }
                )
        )
        UIKitPopupMenu(
            expanded = showMenu,
            items = menuItems.map { item ->
                UIKitPopupMenuItem(
                    title = item.text,
                    onClick = item.onClick,
                    enabled = item.enabled,
                    iconResId = item.iconResId
                )
            },
            onDismissRequest = { showMenu = false }
        )
    }
}

@Composable
fun createAddContactMenuItems(
    onAddFriendClick: () -> Unit,
    onAddGroupClick: () -> Unit
): List<MenuItem> {
    return listOf(
        MenuItem(
            text = stringResource(R.string.compose_demo_add_contact),
            onClick = onAddFriendClick,
            iconResId = UiKitR.drawable.uikit_ic_user_add
        ),
        MenuItem(
            text = stringResource(R.string.compose_demo_join_group),
            onClick = onAddGroupClick,
            iconResId = UiKitR.drawable.uikit_ic_chat_add
        )
    )
}

@Composable
fun createChatMenuItems(
    onAddC2CChatClick: () -> Unit,
    onAddGroupChatClick: () -> Unit,
): List<MenuItem> {
    return listOf(
        MenuItem(
            text = stringResource(R.string.compose_demo_start_c2c_chat),
            onClick = onAddC2CChatClick,
            iconResId = UiKitR.drawable.uikit_ic_user_add
        ),
        MenuItem(
            text = stringResource(R.string.compose_demo_create_group_chat),
            onClick = onAddGroupChatClick,
            iconResId = UiKitR.drawable.uikit_ic_chat_add
        )
    )
}
