package io.trtc.tuikit.chat.uikit.components.contactlist.config

import io.trtc.tuikit.chat.uikit.components.common.uicustom.Customizer
import io.trtc.tuikit.chat.uikit.components.common.uicustom.CustomEditor
import io.trtc.tuikit.chat.uikit.components.contactlist.model.ContactCustomItem
import io.trtc.tuikit.chat.uikit.components.contactlist.model.ContactCustomItemContext

typealias ContactListItemEditor =
    CustomEditor<ContactCustomItemContext, ContactCustomItem>

typealias ContactListItemCustomizer =
    Customizer<ContactCustomItemContext, ContactCustomItem>
