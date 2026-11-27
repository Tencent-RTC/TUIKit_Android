package io.trtc.tuikit.chat.uikit.components.contactlist.ui.groupapplication

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import io.trtc.tuikit.chat.uikit.components.widgets.FullScreenDialog
import io.trtc.tuikit.chat.uikit.components.contactlist.viewmodel.GroupApplicationViewModel
import io.trtc.tuikit.chat.uikit.components.contactlist.viewmodel.GroupApplicationViewModelFactory
import io.trtc.tuikit.atomicxcore.api.group.GroupApplicationInfo
import io.trtc.tuikit.atomicxcore.api.group.GroupStore

@Composable
fun GroupApplicationDialog(isVisible: Boolean, groupStore: GroupStore, onDismiss: () -> Unit) {
    if (isVisible) {
        FullScreenDialog(
            onDismissRequest = onDismiss,
        ) {
            val context = LocalContext.current
            val groupApplicationViewModel = viewModel(
                GroupApplicationViewModel::class,
                factory = GroupApplicationViewModelFactory(groupStore)
            )

            var currentApplication by remember { mutableStateOf<GroupApplicationInfo?>(null) }

            GroupApplication(
                onBackClick = { onDismiss() },
                onApplicationClick = { application ->
                    currentApplication = application
                },
                groupApplicationViewModelFactory = GroupApplicationViewModelFactory(groupStore)
            )
            currentApplication?.let { application ->
                FullScreenDialog(
                    onDismissRequest = { currentApplication = null },
                ) {
                    GroupApplicationDetail(
                        application = application,
                        onBackClick = { currentApplication = null },
                        onAccept = {
                            groupApplicationViewModel.acceptGroupApplication(
                                application,
                                onSuccess = { currentApplication = null },
                                onFailure = { message -> showFailureToast(context, message) }
                            )
                        },
                        onRefuse = {
                            groupApplicationViewModel.refuseGroupApplication(
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
