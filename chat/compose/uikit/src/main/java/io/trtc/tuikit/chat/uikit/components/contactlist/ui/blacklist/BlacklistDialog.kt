package io.trtc.tuikit.chat.uikit.components.contactlist.ui.blacklist

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.DialogProperties
import io.trtc.tuikit.chat.uikit.components.contactlist.viewmodel.BlacklistViewModelFactory
import io.trtc.tuikit.chat.uikit.components.widgets.FullScreenDialog
import io.trtc.tuikit.chat.uikit.components.theme.LocalTheme
import io.trtc.tuikit.atomicxcore.api.contact.ContactInfo
import io.trtc.tuikit.atomicxcore.api.contact.ContactStore

@Composable
fun BlacklistDialog(
    isVisible: Boolean,
    contactStore: ContactStore = ContactStore.shared,
    onDismiss: () -> Unit,
    onContactClick: (ContactInfo) -> Unit
) {
    val colors = LocalTheme.current.colors
    if (isVisible) {
        FullScreenDialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(
                dismissOnBackPress = true,
                dismissOnClickOutside = false,
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false
            )
        ) {
            Blacklist(
                onBackClick = { onDismiss() },
                modifier = Modifier
                    .fillMaxSize()
                    .background(color = colors.bgColorOperate),
                blacklistViewModelFactory = BlacklistViewModelFactory(contactStore)
            ) {
                onContactClick(it)
            }
        }
    }
}
