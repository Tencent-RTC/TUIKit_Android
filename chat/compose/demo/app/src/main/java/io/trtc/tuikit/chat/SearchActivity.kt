package io.trtc.tuikit.chat.search

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.Modifier
import io.trtc.tuikit.chat.uikit.components.theme.LocalTheme
import io.trtc.tuikit.chat.uikit.components.common.SetActivitySystemBarAppearance
import io.trtc.tuikit.chat.uikit.components.search.ui.SearchScreen
import io.trtc.tuikit.atomicxcore.api.conversation.ConversationType
import io.trtc.tuikit.atomicxcore.api.message.MessageInfo
import io.trtc.tuikit.chat.BaseActivity
import io.trtc.tuikit.chat.chat.ChatActivity

class SearchActivity : BaseActivity() {

    companion object {

        fun start(context: Context) {
            val intent = Intent(context, SearchActivity::class.java)
            context.startActivity(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                    WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
        )

        setContent {
            val colors = LocalTheme.current.colors
            SetActivitySystemBarAppearance()

            SearchScreen(
                modifier = Modifier.Companion
                    .background(colors.bgColorOperate)
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding(),
                onBack = { finish() },

                onContactSelect = {
                    ChatActivity.start(this, "c2c_${it.userID}")
                },
                onGroupSelect = {
                    ChatActivity.start(this, "group_${it.groupID}")
                },
                onConversationSelect = {
                    ChatActivity.start(this, it.conversationID)
                },
                onMessageSelect = {
                    ChatActivity.start(this, it.conversationID, it)
                }
            )
        }
    }
}

val MessageInfo.conversationID: String
    get() = if (conversationType == ConversationType.GROUP) "group_${to}" else {
        if (isSentBySelf) "c2c_${to}" else "c2c_${from.userID}"
    }