package io.trtc.tuikit.chat.uikit.components.messagelist.config

import io.trtc.tuikit.chat.uikit.components.common.uicustom.Customizer
import io.trtc.tuikit.chat.uikit.components.common.uicustom.CustomEditor
import io.trtc.tuikit.chat.uikit.components.messagelist.model.MessageCustomAction
import io.trtc.tuikit.chat.uikit.components.messagelist.model.MessageCustomActionContext

typealias MessageActionEditor =
    CustomEditor<MessageCustomActionContext, MessageCustomAction>

typealias MessageActionCustomizer =
    Customizer<MessageCustomActionContext, MessageCustomAction>
