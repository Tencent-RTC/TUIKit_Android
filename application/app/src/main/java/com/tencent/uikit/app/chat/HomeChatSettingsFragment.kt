package com.tencent.uikit.app.chat

import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.tencent.uikit.app.R
import com.tencent.uikit.app.main.MainActivity
import com.trtc.uikit.roomkit.base.util.applyTopWindowInsets
import io.trtc.tuikit.chat.uikit.components.chatsetting.ui.C2CChatSettingView
import io.trtc.tuikit.chat.uikit.components.chatsetting.ui.GroupChatSettingView
import io.trtc.tuikit.chat.uikit.components.chatsetting.config.C2CChatSettingConfig
import io.trtc.tuikit.chat.uikit.components.common.ConversationIDUtil

class HomeChatSettingsFragment : Fragment(R.layout.app_chat_host) {
    companion object {
        fun create(id: String, origin: String = id) = HomeChatSettingsFragment().apply {
            arguments = Bundle().apply { putString("id", id); putString("origin", origin) }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val host = requireActivity() as MainActivity
        val id = requireArguments().getString("id").orEmpty()
        view.findViewById<TextView>(R.id.home_chat_title).setText(R.string.app_settings)
        view.findViewById<View>(R.id.home_chat_header).applyTopWindowInsets()
        view.findViewById<View>(R.id.home_chat_back).setOnClickListener { host.onBackPressedDispatcher.onBackPressed() }
        view.findViewById<View>(R.id.home_chat_more).visibility = View.GONE
        val content = if (ConversationIDUtil.isGroup(id)) GroupChatSettingView(host).apply {
            setup(
                ConversationIDUtil.groupId(id),
                onGroupMemberClick = { host.pushPage(create(ConversationIDUtil.fromUser(it.userID), id), true) },
                onGroupDeleted = { host.resetBranch() })
        } else C2CChatSettingView(host).apply {
            setup(
                ConversationIDUtil.userId(id), onSendMessageClick = {
                host.onBackPressedDispatcher.onBackPressed()
                if (id != requireArguments().getString("origin")) host.pushPage(
                    HomeChatFragment.create(id, null), true
                )
            }, onContactDeleted = { host.resetBranch() },
                config = C2CChatSettingConfig(isShowVoiceCall = false, isShowVideoCall = false)
            )
        }
        view.findViewById<FrameLayout>(R.id.home_chat_content).addView(content, FrameLayout.LayoutParams(-1, -1))
    }
}
