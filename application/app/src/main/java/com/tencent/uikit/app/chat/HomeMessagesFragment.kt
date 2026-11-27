package com.tencent.uikit.app.chat

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.PopupMenu
import io.trtc.tuikit.chat.uikit.components.contactlist.ui.ContactFlowLauncher
import io.trtc.tuikit.chat.uikit.components.config.AppBuilderConfig
import androidx.fragment.app.Fragment
import com.tencent.uikit.app.main.MainActivity
import com.trtc.uikit.roomkit.base.util.applyTopWindowInsets
import io.trtc.tuikit.chat.uikit.pages.ConversationsPageView
import io.trtc.tuikit.atomicx.theme.Theme
import io.trtc.tuikit.atomicx.theme.ThemeStore

class HomeMessagesFragment : Fragment() {
    private var menu: PopupMenu? = null
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        ThemeStore.shared(requireContext()).setTheme(Theme.lightTheme(requireContext()))
        val host = requireActivity() as MainActivity
        val action = inflater.inflate(com.tencent.uikit.app.R.layout.app_messages_action, container, false)
        action.setOnClickListener {
            menu?.dismiss()
            menu = PopupMenu(requireContext(), action).apply {
                menu.add(0, 1, 0, io.trtc.tuikit.chat.uikit.R.string.uikit_start_chat)
                    .setIcon(io.trtc.tuikit.chat.uikit.R.drawable.uikit_ic_user_add)
                menu.add(0, 2, 1, io.trtc.tuikit.chat.uikit.R.string.uikit_create_group)
                    .setIcon(io.trtc.tuikit.chat.uikit.R.drawable.uikit_ic_chat_add)
                setForceShowIcon(true)
                setOnMenuItemClickListener { item ->
                    val open: (String) -> Unit = { id ->
                        if (isResumed) host.pushPage(HomeChatFragment.create(id, null), true)
                    }
                    if (item.itemId == 1) ContactFlowLauncher.showStartSingleChatPage(
                        requireContext(), onCreateChat = open
                    )
                    else ContactFlowLauncher.showCreateGroupChatPage(requireContext(), onCreateChat = open)
                    true
                }
                show()
            }
        }
        return ConversationsPageView(requireContext()).apply {
            applyTopWindowInsets()
            setup(
                headerTitle = getString(io.trtc.tuikit.chat.uikit.R.string.uikit_conversations_title),
                headerRightAction = action.takeIf { AppBuilderConfig.enableCreateConversation },
                onConversationClick = { info ->
                    (requireActivity() as MainActivity).pushPage(
                        HomeChatFragment.create(info.conversationID, info.title), true
                    )
                }, onSearchClick = {
                    (requireActivity() as MainActivity).pushPage(HomeSearchFragment(), true)
                })
        }
    }

    override fun onPause() {
        menu?.dismiss(); menu = null; super.onPause()
    }
}
