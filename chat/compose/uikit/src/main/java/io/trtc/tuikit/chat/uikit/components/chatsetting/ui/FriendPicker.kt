package io.trtc.tuikit.chat.uikit.components.chatsetting.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import io.trtc.tuikit.chat.uikit.components.chatsetting.viewmodel.FriendPickerViewModel
import io.trtc.tuikit.atomicxcore.api.contact.ContactInfo

@Composable
fun FriendPickerDialog(
    isVisible: Boolean,
    title: String = "",
    preSelectedFriends: List<String> = emptyList(),
    maxSelection: Int = 100,
    onDismiss: () -> Unit,
    onConfirm: (List<ContactInfo>) -> Unit,
    viewModel: FriendPickerViewModel = viewModel()
) {
    if (!isVisible) return

    val friends by viewModel.friends.collectAsState()

    LaunchedEffect(isVisible) {
        if (isVisible) {
            viewModel.loadFriends()
        }
    }

    ContactPickerDialog(
        isVisible = true,
        title = title,
        contacts = friends,
        maxSelection = maxSelection,
        preSelectedUserIds = preSelectedFriends,
        onDismiss = onDismiss,
        onConfirm = onConfirm
    )
}
