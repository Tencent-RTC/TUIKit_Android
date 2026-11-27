package com.tencent.uikit.app.chat

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.tencent.uikit.app.main.MainActivity
import com.trtc.uikit.roomkit.base.util.applyTopWindowInsets
import io.trtc.tuikit.chat.uikit.components.search.ui.SearchView

class HomeSearchFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val host = requireActivity() as MainActivity
        fun open(id: String, title: String?) {
            host.pushPage(HomeChatFragment.create(id, title), true)
        }
        return SearchView(host).apply {
            applyTopWindowInsets()
            setup(
                onContactSelect = { open("c2c_${it.userID}", it.userInfo?.nickname ?: it.userID) },
                onGroupSelect = { open("group_${it.groupID}", it.groupName) },
                onConversationSelect = { open(it.conversationID, it.conversationShowName) },
                onMessageSelect = { message ->
                    host.pushPage(
                        HomeChatFragment.create(
                            if (message.conversationType == io.trtc.tuikit.atomicxcore.api.conversation.ConversationType.GROUP) "group_${message.to}" else "c2c_${if (message.isSentBySelf) message.to else message.from.userID}",
                            null
                        ).apply {
                            locateMessage = message
                        }, true
                    )
                }, onBack = { host.onBackPressedDispatcher.onBackPressed() })
        }
    }
}
