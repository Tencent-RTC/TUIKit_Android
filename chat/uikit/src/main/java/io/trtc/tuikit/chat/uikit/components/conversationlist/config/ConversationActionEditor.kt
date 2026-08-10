package io.trtc.tuikit.chat.uikit.components.conversationlist.config

import io.trtc.tuikit.chat.uikit.components.common.uicustom.Customizer
import io.trtc.tuikit.chat.uikit.components.common.uicustom.CustomEditor
import io.trtc.tuikit.chat.uikit.components.conversationlist.model.ConversationCustomAction
import io.trtc.tuikit.chat.uikit.components.conversationlist.model.ConversationCustomActionContext

typealias ConversationActionEditor =
    CustomEditor<ConversationCustomActionContext, ConversationCustomAction>

typealias ConversationActionCustomizer =
    Customizer<ConversationCustomActionContext, ConversationCustomAction>
