package io.trtc.tuikit.chat.uikit.components.contactlist.ui.addfriendandgroup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.trtc.tuikit.chat.uikit.compose.R
import io.trtc.tuikit.chat.uikit.components.theme.LocalTheme
import io.trtc.tuikit.chat.uikit.components.contactlist.viewmodel.AddType
import io.trtc.tuikit.atomicxcore.api.contact.ContactInfo

@Composable
fun ContactDetail(
    result: ContactInfo,
    addType: AddType,
    onAddContact: () -> Unit
) {
    val colors = LocalTheme.current.colors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bgColorDefault)
    ) {
        AddContactInfoCard(addType = addType, result = result)

        AddContactSectionSpacer()

        AddContactActionCard(
            text = stringResource(R.string.contact_list_add_contact),
            onClick = onAddContact
        )
    }
}
