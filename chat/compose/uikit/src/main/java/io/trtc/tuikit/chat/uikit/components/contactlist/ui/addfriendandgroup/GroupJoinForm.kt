package io.trtc.tuikit.chat.uikit.components.contactlist.ui.addfriendandgroup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.trtc.tuikit.chat.uikit.compose.R
import io.trtc.tuikit.chat.uikit.components.theme.LocalTheme
import io.trtc.tuikit.chat.uikit.components.contactlist.viewmodel.AddType
import io.trtc.tuikit.atomicxcore.api.contact.ContactInfo

@Composable
fun GroupJoinForm(
    result: ContactInfo,
    message: String,
    isJoining: Boolean,
    onMessageChange: (String) -> Unit,
    onSend: (message: String) -> Unit
) {
    val colors = LocalTheme.current.colors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bgColorDefault)
            .verticalScroll(rememberScrollState())
    ) {
        AddContactInfoCard(addType = AddType.GROUP, result = result)

        AddContactSectionTitle(text = stringResource(R.string.contact_list_fill_validation_message))

        AddContactMultilineInputCard(
            value = message,
            onValueChange = onMessageChange
        )

        AddContactSectionSpacer()

        AddContactActionCard(
            text = stringResource(R.string.contact_list_send),
            enabled = !isJoining,
            onClick = { onSend(message) }
        )
    }
}
