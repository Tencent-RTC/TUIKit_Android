package io.trtc.tuikit.chat.uikit.components.messagelist.ui.widgets

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.trtc.tuikit.chat.uikit.compose.R
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.popups.LongPressActionGrid
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.popups.LongPressBubblePopup
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.popups.LongPressDimens
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.popups.LongPressPopupAction

@Composable
fun AuxiliaryTextPopupMenu(
    anchorBounds: Rect?,
    onDismiss: () -> Unit,
    onForward: () -> Unit,
    onCopy: () -> Unit
) {
    val copyTitle = stringResource(R.string.message_list_menu_copy)
    val forwardTitle = stringResource(R.string.message_list_menu_forward)
    val actions = remember(copyTitle, forwardTitle, onCopy, onForward) {
        listOf(
            LongPressPopupAction(
                title = copyTitle,
                iconResId = R.drawable.message_list_menu_copy_icon,
                onClick = onCopy
            ),
            LongPressPopupAction(
                title = forwardTitle,
                iconResId = R.drawable.message_list_menu_forward_icon,
                onClick = onForward
            )
        )
    }
    LongPressBubblePopup(
        anchorBounds = anchorBounds,
        contentWidth = (LongPressDimens.CELL_WIDTH_DP * 2 + 8).dp,
        menuContentHeight = (8 + LongPressDimens.CELL_HEIGHT_DP).dp,
        onDismiss = onDismiss
    ) { animatedDismiss ->
        LongPressActionGrid(
            actions = actions,
            columnCount = 2,
            onDismiss = animatedDismiss,
            onReactionEntry = {}
        )
    }
}
