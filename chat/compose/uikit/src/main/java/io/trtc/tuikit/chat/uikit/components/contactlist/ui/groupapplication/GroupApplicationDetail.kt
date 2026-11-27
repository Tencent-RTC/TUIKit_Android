package io.trtc.tuikit.chat.uikit.components.contactlist.ui.groupapplication

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.trtc.tuikit.atomicxcore.api.group.GroupApplicationInfo
import io.trtc.tuikit.chat.uikit.compose.R
import io.trtc.tuikit.chat.uikit.components.contactlist.utils.canHandle
import io.trtc.tuikit.chat.uikit.components.contactlist.utils.fromUserDisplayName
import io.trtc.tuikit.chat.uikit.components.contactlist.utils.groupDisplayName
import io.trtc.tuikit.chat.uikit.components.contactlist.utils.isJoinRequest
import io.trtc.tuikit.chat.uikit.components.contactlist.utils.statusText
import io.trtc.tuikit.chat.uikit.components.contactlist.utils.toUserDisplayName
import io.trtc.tuikit.chat.uikit.components.messagelist.utils.DateTimeUtils
import io.trtc.tuikit.chat.uikit.components.theme.LocalTheme
import io.trtc.tuikit.chat.uikit.components.widgets.Avatar
import io.trtc.tuikit.chat.uikit.components.widgets.AvatarSize
import io.trtc.tuikit.chat.uikit.components.widgets.DialogNavBar
import io.trtc.tuikit.chat.uikit.components.widgets.DialogNavBarMode

private const val MILLIS_TIMESTAMP_THRESHOLD = 1_000_000_000_000L

@Composable
fun GroupApplicationDetail(
    application: GroupApplicationInfo,
    onBackClick: () -> Unit = {},
    onAccept: () -> Unit = {},
    onRefuse: () -> Unit = {}
) {
    val colors = LocalTheme.current.colors

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(color = colors.bgColorOperate)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        DialogNavBar(
            mode = DialogNavBarMode.BackTitle,
            title = stringResource(R.string.contact_list_group_application_info),
            onLeadingClick = onBackClick
        )

        HorizontalDivider(
            thickness = 0.5.dp,
            color = colors.strokeColorSecondary
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = 20.dp)
        ) {
            GroupApplicationUserInfoSection(application = application)

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 12.dp)
            ) {
                DetailInfoRow(
                    label = stringResource(R.string.contact_list_group_name),
                    value = application.groupDisplayName
                )
                DetailInfoRow(
                    label = stringResource(R.string.contact_list_friend_application_validation_message),
                    value = application.requestMsg?.takeIf { it.isNotBlank() }
                        ?: stringResource(R.string.contact_list_no_content)
                )
                DetailInfoRow(
                    label = stringResource(R.string.contact_list_application_time),
                    value = formatAddTime(application.addTime)
                )
                DetailInfoRow(
                    label = stringResource(R.string.contact_list_handle_status),
                    value = application.statusText,
                    showDivider = false
                )
            }

            if (application.canHandle) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 40.dp, bottom = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onAccept() }
                            .background(color = colors.buttonColorPrimaryDefault),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.contact_list_agree),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.W400,
                            color = colors.textColorButton,
                            maxLines = 1
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onRefuse() }
                            .background(color = colors.bgColorInput),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.contact_list_refuse),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.W400,
                            color = colors.textColorError,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupApplicationUserInfoSection(application: GroupApplicationInfo) {
    val colors = LocalTheme.current.colors
    val primaryName = primaryDisplayName(application)
    val primaryId = primaryUserId(application).ifBlank { application.applicationID }
    val avatarUrl = if (application.isJoinRequest) application.fromUserAvatarURL else null

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Avatar(
            url = avatarUrl,
            name = primaryName,
            size = AvatarSize.XL,
        )

        Column {
            Text(
                text = stringResource(
                    if (application.isJoinRequest) {
                        R.string.contact_list_applicant
                    } else {
                        R.string.contact_list_invitee
                    }
                ),
                fontSize = 12.sp,
                fontWeight = FontWeight.W400,
                color = colors.textColorSecondary
            )

            Box(modifier = Modifier.height(2.dp))

            Text(
                text = primaryName,
                fontSize = 16.sp,
                fontWeight = FontWeight.W600,
                color = colors.textColorPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Box(modifier = Modifier.height(4.dp))

            Text(
                text = stringResource(
                    R.string.contact_list_label_value_format,
                    stringResource(R.string.contact_list_user_id),
                    primaryId
                ),
                fontSize = 12.sp,
                fontWeight = FontWeight.W400,
                color = colors.textColorSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun DetailInfoRow(
    label: String,
    value: String,
    showDivider: Boolean = true
) {
    val colors = LocalTheme.current.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 16.sp,
            fontWeight = FontWeight.W400,
            color = colors.textColorSecondary,
            modifier = Modifier.width(92.dp)
        )

        Text(
            text = value,
            fontSize = 16.sp,
            fontWeight = FontWeight.W400,
            color = colors.textColorPrimary,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f)
        )
    }

    if (showDivider) {
        HorizontalDivider(
            thickness = 0.5.dp,
            color = colors.strokeColorSecondary
        )
    }
}

private fun primaryDisplayName(application: GroupApplicationInfo): String {
    return if (application.isJoinRequest) {
        application.fromUserDisplayName
    } else {
        application.toUserDisplayName.ifBlank { application.fromUserDisplayName }
    }
}

private fun primaryUserId(application: GroupApplicationInfo): String {
    return if (application.isJoinRequest) {
        application.fromUser ?: ""
    } else {
        application.toUser ?: ""
    }
}

@Composable
private fun formatAddTime(addTime: Long): String {
    if (addTime <= 0L) {
        return stringResource(R.string.contact_list_no_content)
    }
    val timeInMillis = if (addTime > MILLIS_TIMESTAMP_THRESHOLD) addTime else addTime * 1000L
    return DateTimeUtils.formatFullDateTime(timeInMillis)
}
