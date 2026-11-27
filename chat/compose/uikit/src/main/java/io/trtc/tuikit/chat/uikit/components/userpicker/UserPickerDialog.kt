package io.trtc.tuikit.chat.uikit.components.userpicker

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import io.trtc.tuikit.chat.uikit.components.theme.LocalTheme
import io.trtc.tuikit.chat.uikit.components.widgets.DialogNavBar
import io.trtc.tuikit.chat.uikit.components.widgets.DialogNavBarMode
import io.trtc.tuikit.chat.uikit.components.widgets.FullScreenDialog

@Composable
fun <T> UserPickerDialog(
    visible: Boolean,
    title: String,
    dataSource: List<UserPickerData<T>>,
    modifier: Modifier = Modifier,
    maxCount: Int? = null,
    preSelectedKeys: List<String> = emptyList(),
    lockedItems: List<String>? = null,
    showCheckbox: Boolean = true,
    allowEmptyConfirm: Boolean = false,
    onMaxCountExceed: (List<UserPickerData<T>>) -> Unit = {},
    onDismiss: () -> Unit,
    onConfirm: (List<T>) -> Unit
) {
    if (!visible) return

    val colors = LocalTheme.current.colors
    val pickerState = remember { UserPickerState<T>() }
    val selectedItems by pickerState.selectedItems.collectAsState()
    val confirmEnabled = selectedItems.isNotEmpty() || allowEmptyConfirm

    FullScreenDialog(onDismissRequest = onDismiss) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(colors.bgColorTopBar)
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            DialogNavBar(
                mode = DialogNavBarMode.CancelTitleConfirm,
                title = title,
                onLeadingClick = onDismiss,
                onConfirmClick = {
                    onConfirm(selectedItems.map { it.extraData })
                    onDismiss()
                },
                confirmEnabled = confirmEnabled
            )

            UserPicker(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                dataSource = dataSource,
                state = pickerState,
                defaultSelectedItems = preSelectedKeys,
                lockedItems = lockedItems,
                maxCount = maxCount,
                showCheckbox = showCheckbox,
                onMaxCountExceed = onMaxCountExceed
            )
        }
    }
}
