package com.tencent.uikit.app.chat

import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.tencent.uikit.app.R
import com.tencent.uikit.app.main.MainActivity
import com.trtc.uikit.roomkit.base.util.applyTopWindowInsets
import io.trtc.tuikit.chat.uikit.pages.ChatPageView
import io.trtc.tuikit.chat.uikit.components.messageinput.config.ChatMessageInputConfig
import io.trtc.tuikit.atomicxcore.api.message.MessageInfo

class HomeChatFragment : Fragment(R.layout.app_chat_host) {
    var locateMessage: MessageInfo? = null

    companion object {
        fun create(id: String, title: String?) = HomeChatFragment().apply {
            arguments = Bundle().apply { putString("id", id); putString("title", title) }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val id = requireArguments().getString("id").orEmpty()
        view.findViewById<TextView>(R.id.home_chat_title).text = arguments?.getString("title") ?: id
        view.findViewById<View>(R.id.home_chat_header).applyTopWindowInsets()
        view.findViewById<View>(R.id.home_chat_back)
            .setOnClickListener { requireActivity().onBackPressedDispatcher.onBackPressed() }
        view.findViewById<View>(R.id.home_chat_more).setOnClickListener {
            (requireActivity() as MainActivity).pushPage(HomeChatSettingsFragment.create(id), true)
        }
        val chat = ChatPageView(requireContext())
        chat.setup(
            id, locateMessage = locateMessage,
            messageInputConfig = ChatMessageInputConfig().apply { isShowAudioCall = false; isShowVideoCall = false },
            onUserClick = { user ->
                (requireActivity() as MainActivity).pushPage(
                    HomeChatSettingsFragment.create("c2c_$user", id), true
                )
            })
        view.findViewById<FrameLayout>(R.id.home_chat_content).addView(chat, FrameLayout.LayoutParams(-1, -1))
    }
}
