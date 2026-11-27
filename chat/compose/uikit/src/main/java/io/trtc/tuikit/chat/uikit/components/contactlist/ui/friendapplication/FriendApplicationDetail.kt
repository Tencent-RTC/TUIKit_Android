package io.trtc.tuikit.chat.uikit.components.contactlist.ui.friendapplication

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.trtc.tuikit.atomicxcore.api.contact.FriendApplicationInfo
import io.trtc.tuikit.chat.uikit.compose.R
import io.trtc.tuikit.chat.uikit.components.contactlist.utils.displayName
import io.trtc.tuikit.chat.uikit.components.theme.LocalTheme
import io.trtc.tuikit.chat.uikit.components.widgets.Avatar
import io.trtc.tuikit.chat.uikit.components.widgets.AvatarSize
import io.trtc.tuikit.chat.uikit.components.widgets.DialogNavBar
import io.trtc.tuikit.chat.uikit.components.widgets.DialogNavBarMode

@Composable
fun FriendApplicationDetail(
    application: FriendApplicationInfo,
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
            title = stringResource(R.string.contact_list_friend_application_info),
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(18.dp)
            ) {

                Avatar(
                    url = application.avatarURL,
                    name = application.displayName,
                    size = AvatarSize.XL,
                )

                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = application.displayName,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.W600,
                        color = colors.textColorPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Text(
                        text = stringResource(
                            R.string.contact_list_label_value_format,
                            stringResource(R.string.contact_list_user_id),
                            application.userID
                        ),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.W400,
                        color = colors.textColorSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (!application.addWording.isNullOrEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = stringResource(R.string.contact_list_friend_application_validation_message),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.W400,
                        color = colors.textColorSecondary
                    )

                    Text(
                        text = application.addWording ?: "",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.W400,
                        color = colors.textColorPrimary,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(40.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable {
                            onAccept()
                        }
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
                        .clickable {
                            onRefuse()
                        }
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
