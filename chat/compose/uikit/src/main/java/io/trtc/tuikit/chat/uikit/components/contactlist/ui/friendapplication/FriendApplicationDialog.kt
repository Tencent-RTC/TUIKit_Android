package io.trtc.tuikit.chat.uikit.components.contactlist.ui.friendapplication

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import io.trtc.tuikit.chat.uikit.components.widgets.FullScreenDialog
import io.trtc.tuikit.chat.uikit.components.contactlist.viewmodel.FriendApplicationViewModel
import io.trtc.tuikit.chat.uikit.components.contactlist.viewmodel.FriendApplicationViewModelFactory
import io.trtc.tuikit.atomicxcore.api.contact.ContactStore
import io.trtc.tuikit.atomicxcore.api.contact.FriendApplicationInfo

@Composable
fun FriendApplicationDialog(
    isVisible: Boolean,
    contactStore: ContactStore = ContactStore.shared,
    onDismiss: () -> Unit
) {
    if (isVisible) {
        FullScreenDialog(
            onDismissRequest = onDismiss,
        ) {
            val context = LocalContext.current
            val friendApplicationViewModelFactory = remember(contactStore) {
                FriendApplicationViewModelFactory(contactStore)
            }
            val friendApplicationViewModel = viewModel(
                FriendApplicationViewModel::class,
                factory = friendApplicationViewModelFactory
            )

            var currentApplication by remember { mutableStateOf<FriendApplicationInfo?>(null) }

            FriendApplication(
                onBackClick = { onDismiss() },
                onApplicationClick = { application ->
                    currentApplication = application
                },
                friendApplicationViewModelFactory = friendApplicationViewModelFactory
            )
            currentApplication?.let { application ->
                FullScreenDialog(
                    onDismissRequest = { currentApplication = null },
                ) {
                    FriendApplicationDetail(
                        application = application,
                        onBackClick = { currentApplication = null },
                        onAccept = {
                            friendApplicationViewModel.acceptFriendApplication(
                                application,
                                onSuccess = { currentApplication = null },
                                onFailure = { message -> showFailureToast(context, message) }
                            )
                        },
                        onRefuse = {
                            friendApplicationViewModel.refuseFriendApplication(
                                application,
                                onSuccess = { currentApplication = null },
                                onFailure = { message -> showFailureToast(context, message) }
                            )
                        }
                    )
                }
            }
        }
    }
}

private fun showFailureToast(context: android.content.Context, message: String) {
    if (message.isNotBlank()) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }
}
