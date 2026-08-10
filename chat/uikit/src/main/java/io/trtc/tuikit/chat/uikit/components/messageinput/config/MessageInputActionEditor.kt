package io.trtc.tuikit.chat.uikit.components.messageinput.config

import io.trtc.tuikit.chat.uikit.components.common.uicustom.Customizer
import io.trtc.tuikit.chat.uikit.components.common.uicustom.CustomEditor
import io.trtc.tuikit.chat.uikit.components.messageinput.data.MessageInputMenuAction
import io.trtc.tuikit.chat.uikit.components.messageinput.data.MessageInputMenuActionContext

typealias MessageInputActionEditor =
    CustomEditor<MessageInputMenuActionContext, MessageInputMenuAction>

typealias MessageInputActionCustomizer =
    Customizer<MessageInputMenuActionContext, MessageInputMenuAction>
