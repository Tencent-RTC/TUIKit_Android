package io.trtc.tuikit.chat.uikit.components.chatsetting.config

import io.trtc.tuikit.chat.uikit.components.common.uicustom.CustomEditor
import io.trtc.tuikit.chat.uikit.components.common.uicustom.Customizer
import io.trtc.tuikit.chat.uikit.components.chatsetting.model.C2CChatSettingItemContext
import io.trtc.tuikit.chat.uikit.components.chatsetting.model.ChatSettingCustomItem
import io.trtc.tuikit.chat.uikit.components.chatsetting.model.GroupChatSettingItemContext

typealias C2CChatSettingItemEditor =
    CustomEditor<C2CChatSettingItemContext, ChatSettingCustomItem<C2CChatSettingItemContext>>

typealias C2CChatSettingItemCustomizer =
    Customizer<C2CChatSettingItemContext, ChatSettingCustomItem<C2CChatSettingItemContext>>

typealias GroupChatSettingItemEditor =
    CustomEditor<GroupChatSettingItemContext, ChatSettingCustomItem<GroupChatSettingItemContext>>

typealias GroupChatSettingItemCustomizer =
    Customizer<GroupChatSettingItemContext, ChatSettingCustomItem<GroupChatSettingItemContext>>
