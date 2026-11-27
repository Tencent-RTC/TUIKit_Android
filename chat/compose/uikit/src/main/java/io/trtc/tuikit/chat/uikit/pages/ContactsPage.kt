package io.trtc.tuikit.chat.uikit.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.trtc.tuikit.chat.uikit.components.contactlist.config.ChatContactListConfig
import io.trtc.tuikit.chat.uikit.components.contactlist.config.ContactListConfigProtocol
import io.trtc.tuikit.chat.uikit.components.theme.Colors
import io.trtc.tuikit.chat.uikit.components.theme.LocalTheme
import io.trtc.tuikit.chat.uikit.components.contactlist.ui.ContactList
import io.trtc.tuikit.atomicxcore.api.contact.ContactInfo
import io.trtc.tuikit.chat.uikit.compose.R
import io.trtc.tuikit.chat.uikit.components.widgets.PageHeader

@Composable
fun ContactsPage(
    onContactClick: (ContactInfo) -> Unit = {},
    onGroupClick: (ContactInfo) -> Unit = {},
    headerTitle: String? = null,
    config: ContactListConfigProtocol = ChatContactListConfig(),
    editContent: @Composable () -> Unit = {},
) {

    val colors = LocalTheme.current.colors
    val title = headerTitle ?: stringResource(R.string.chat_uikit_contacts)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Colors.Transparent)
    ) {
        PageHeader(title) {
            editContent()
        }

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            ContactList(
                config = config,
                onGroupClick = onGroupClick,
                onContactClick = onContactClick
            )
        }
    }
}