package io.trtc.tuikit.chat.uikit.components.contactlist.ui.mygroup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.trtc.tuikit.chat.uikit.components.contactlist.viewmodel.MyGroupViewModelFactory
import io.trtc.tuikit.chat.uikit.components.widgets.FullScreenDialog
import io.trtc.tuikit.chat.uikit.components.theme.LocalTheme
import io.trtc.tuikit.atomicxcore.api.contact.ContactInfo
import io.trtc.tuikit.atomicxcore.api.group.GroupStore

@Composable
fun MyGroupDialog(
    isVisible: Boolean,
    groupStore: GroupStore = GroupStore.shared,
    onDismiss: () -> Unit,
    onGroupClick: (ContactInfo) -> Unit
) {
    val colors = LocalTheme.current.colors
    if (isVisible) {
        FullScreenDialog(
            onDismissRequest = onDismiss
        ) {
            MyGroup(
                onBackClick = { onDismiss() },
                modifier = Modifier
                    .fillMaxSize()
                    .background(color = colors.bgColorOperate),
                myGroupViewModelFactory = MyGroupViewModelFactory(groupStore)
            ) {
                onGroupClick(it)
            }

        }
    }
}
