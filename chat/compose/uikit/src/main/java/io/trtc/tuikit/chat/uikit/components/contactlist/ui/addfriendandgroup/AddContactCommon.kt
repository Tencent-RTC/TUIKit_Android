package io.trtc.tuikit.chat.uikit.components.contactlist.ui.addfriendandgroup

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.trtc.tuikit.chat.uikit.compose.R
import io.trtc.tuikit.chat.uikit.components.widgets.Avatar
import io.trtc.tuikit.chat.uikit.components.widgets.AvatarSize
import io.trtc.tuikit.chat.uikit.components.theme.LocalTheme
import io.trtc.tuikit.chat.uikit.components.contactlist.utils.displayName
import io.trtc.tuikit.chat.uikit.components.contactlist.viewmodel.AddType
import io.trtc.tuikit.atomicxcore.api.contact.ContactInfo
import io.trtc.tuikit.atomicxcore.api.login.LoginStore

@Composable
internal fun defaultSelfWording(): String {
    val loginUserInfo by LoginStore.shared.loginState.loginUserInfo.collectAsState()
    return stringResource(R.string.contact_list_add_wording_i_am, loginUserInfo?.displayName ?: "")
}

@Composable
internal fun AddContactInfoCard(
    addType: AddType,
    result: ContactInfo,
    modifier: Modifier = Modifier
) {
    val colors = LocalTheme.current.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.bgColorOperate)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Avatar(
            modifier = Modifier.size(48.dp),
            url = result.avatarURL,
            name = result.displayName,
            size = AvatarSize.XL
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = result.displayName,
                color = colors.textColorPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            val idLabel = if (addType == AddType.GROUP) {
                stringResource(R.string.contact_list_group_id)
            } else {
                stringResource(R.string.contact_list_user_id)
            }
            Text(
                text = stringResource(R.string.contact_list_label_value_format, idLabel, result.userID),
                color = colors.textColorSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.W400,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (addType == AddType.CONTACT) {
                Spacer(modifier = Modifier.height(2.dp))
                val signature = result.aboutMe
                    ?.takeIf { it.isNotBlank() }
                    ?: stringResource(R.string.contact_list_no_content)
                Text(
                    text = stringResource(
                        R.string.contact_list_label_value_format,
                        stringResource(R.string.contact_list_signature),
                        signature
                    ),
                    color = colors.textColorSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.W400,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
internal fun AddContactSectionTitle(text: String, modifier: Modifier = Modifier) {
    val colors = LocalTheme.current.colors
    Text(
        text = text,
        color = colors.textColorSecondary,
        fontSize = 14.sp,
        fontWeight = FontWeight.W400,
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 8.dp)
    )
}

@Composable
internal fun AddContactSectionSpacer(modifier: Modifier = Modifier) {
    val colors = LocalTheme.current.colors
    Spacer(
        modifier = modifier
            .fillMaxWidth()
            .height(10.dp)
            .background(colors.bgColorDefault)
    )
}

@Composable
internal fun AddContactActionCard(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val colors = LocalTheme.current.colors
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .background(colors.bgColorOperate)
            .clickable(
                enabled = enabled,
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (enabled) colors.textColorLink else colors.textColorSecondary,
            fontSize = 16.sp,
            fontWeight = FontWeight.W400
        )
    }
}

@Composable
internal fun AddContactMultilineInputCard(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    minHeight: Dp = 120.dp
) {
    val colors = LocalTheme.current.colors
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = minHeight)
            .background(colors.bgColorOperate)
            .padding(14.dp)
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            textStyle = TextStyle(
                color = colors.textColorPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.W400
            ),
            cursorBrush = SolidColor(colors.textColorLink),
            maxLines = 8
        )
    }
}
