package io.trtc.tuikit.chat.uikit.components.contactlist.ui.groupapplication

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.trtc.tuikit.atomicxcore.api.group.GroupApplicationInfo
import io.trtc.tuikit.chat.uikit.compose.R
import io.trtc.tuikit.chat.uikit.components.contactlist.utils.applicationTypeText
import io.trtc.tuikit.chat.uikit.components.contactlist.utils.canHandle
import io.trtc.tuikit.chat.uikit.components.contactlist.utils.fromUserDisplayName
import io.trtc.tuikit.chat.uikit.components.contactlist.utils.groupDisplayName
import io.trtc.tuikit.chat.uikit.components.contactlist.utils.isJoinRequest
import io.trtc.tuikit.chat.uikit.components.contactlist.utils.statusText
import io.trtc.tuikit.chat.uikit.components.contactlist.utils.toUserDisplayName
import io.trtc.tuikit.chat.uikit.components.contactlist.viewmodel.GroupApplicationViewModel
import io.trtc.tuikit.chat.uikit.components.contactlist.viewmodel.GroupApplicationViewModelFactory
import io.trtc.tuikit.chat.uikit.components.theme.LocalTheme
import io.trtc.tuikit.chat.uikit.components.widgets.Avatar
import io.trtc.tuikit.chat.uikit.components.widgets.DialogNavBar
import io.trtc.tuikit.chat.uikit.components.widgets.DialogNavBarMode

@Composable
fun GroupApplication(
    onBackClick: () -> Unit = {},
    onApplicationClick: (GroupApplicationInfo) -> Unit = {},
    groupApplicationViewModelFactory: GroupApplicationViewModelFactory = GroupApplicationViewModelFactory()
) {
    val colors = LocalTheme.current.colors
    val context = LocalContext.current
    val groupApplicationViewModel = viewModel(
        GroupApplicationViewModel::class,
        factory = groupApplicationViewModelFactory
    )

    DisposableEffect(Unit) {
        groupApplicationViewModel.fetchGroupApplicationList()
        groupApplicationViewModel.clearGroupApplicationUnreadCount()
        onDispose {
            groupApplicationViewModel.clearGroupApplicationUnreadCount()
        }
    }

    val groupApplications by groupApplicationViewModel.groupApplications.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(color = colors.bgColorOperate)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        DialogNavBar(
            mode = DialogNavBarMode.BackTitle,
            title = stringResource(R.string.contact_list_group_application),
            onLeadingClick = onBackClick
        )

        HorizontalDivider(
            thickness = 0.5.dp,
            color = colors.strokeColorSecondary
        )

        if (groupApplications.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.contact_list_no_group_application),
                    fontSize = 17.sp,
                    color = colors.textColorSecondary
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 20.dp)
            ) {
                items(groupApplications, key = { it.applicationID }) { application ->
                    GroupApplicationItem(
                        application = application,
                        onItemClick = { onApplicationClick(application) },
                        onAccept = {
                            groupApplicationViewModel.acceptGroupApplication(
                                application,
                                onFailure = { message -> showFailureToast(context, message) }
                            )
                        },
                        onRefuse = {
                            groupApplicationViewModel.refuseGroupApplication(
                                application,
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

@Composable
fun GroupApplicationItem(
    application: GroupApplicationInfo,
    onItemClick: () -> Unit = {},
    onAccept: () -> Unit = {},
    onRefuse: () -> Unit = {}
) {
    val colors = LocalTheme.current.colors

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onItemClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Avatar(
                url = application.fromUserAvatarURL,
                name = application.fromUserDisplayName,
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = application.applicationTypeText,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.W400,
                        color = colors.textColorPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Text(
                    text = if (application.isJoinRequest) {
                        "${stringResource(R.string.contact_list_applicant)}：${application.fromUserDisplayName}"
                    } else {
                        "${stringResource(R.string.contact_list_invitee)}：${application.toUserDisplayName}"
                    },
                    fontSize = 13.sp,
                    fontWeight = FontWeight.W400,
                    color = colors.textColorSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = "${stringResource(R.string.contact_list_group_name)}：${application.groupDisplayName}",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.W400,
                    color = colors.textColorSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (!application.requestMsg.isNullOrEmpty()) {
                    Text(
                        text = application.requestMsg ?: "",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.W400,
                        color = colors.textColorSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (application.canHandle) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .width(60.dp)
                            .height(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onAccept() }
                            .background(color = colors.buttonColorPrimaryDefault),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.contact_list_agree),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.W400,
                            color = colors.textColorButton
                        )
                    }

                    Box(
                        modifier = Modifier
                            .width(60.dp)
                            .height(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onRefuse() }
                            .border(
                                width = 1.dp,
                                color = colors.strokeColorPrimary,
                                shape = RoundedCornerShape(8.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.contact_list_refuse),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.W400,
                            color = colors.textColorError
                        )
                    }
                }
            } else {
                Text(
                    text = application.statusText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.W400,
                    color = colors.textColorSecondary
                )
            }
        }
    }
}