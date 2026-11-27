package io.trtc.tuikit.chat.uikit.components.contactlist.ui.addnewchat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.trtc.tuikit.chat.uikit.compose.R
import io.trtc.tuikit.chat.uikit.components.widgets.Avatar
import io.trtc.tuikit.chat.uikit.components.widgets.AvatarSize
import io.trtc.tuikit.chat.uikit.components.theme.LocalTheme
import io.trtc.tuikit.chat.uikit.components.contactlist.utils.displayName
import io.trtc.tuikit.atomicxcore.api.contact.ContactInfo

@Composable
internal fun SelectedMembersPreview(
    selectedContacts: List<ContactInfo>,
    modifier: Modifier = Modifier
) {
    val colors = LocalTheme.current.colors

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.bgColorTopBar)
    ) {
        Text(
            text = stringResource(R.string.contact_list_selected_group_member),
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = colors.textColorPrimary,
            modifier = Modifier.padding(
                start = 16.dp,
                top = 12.dp,
                end = 16.dp,
                bottom = 8.dp
            )
        )

        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(selectedContacts, key = { it.userID }) { contact ->
                Avatar(
                    url = contact.avatarURL,
                    name = contact.displayName,
                    size = AvatarSize.M
                )
            }
        }
    }
}
