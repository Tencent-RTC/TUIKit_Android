package io.trtc.tuikit.chat.demo.search

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import io.trtc.tuikit.chat.uikit.components.search.ui.SearchView
import io.trtc.tuikit.atomicx.theme.ThemeStore
import io.trtc.tuikit.atomicx.theme.tokens.ColorTokens
import io.trtc.tuikit.atomicxcore.api.conversation.ConversationType
import io.trtc.tuikit.atomicxcore.api.message.MessageInfo
import io.trtc.tuikit.chat.demo.common.BaseActivity
import io.trtc.tuikit.chat.demo.chat.ChatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class SearchActivity : BaseActivity() {

    private lateinit var rootContainer: FrameLayout
    private lateinit var searchView: SearchView

    private val themeStore by lazy { ThemeStore.shared(this) }
    private var activityScope: CoroutineScope? = null

    companion object {

        fun start(context: Context) {
            val intent = Intent(context, SearchActivity::class.java)
            context.startActivity(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (isFinishing) {
            return
        }

        window.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
        )

        rootContainer = FrameLayout(this)
        setContentView(rootContainer)

        ViewCompat.setOnApplyWindowInsetsListener(rootContainer) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(
                top = systemBars.top,
                bottom = systemBars.bottom
            )
            insets
        }

        searchView = SearchView(this)
        rootContainer.addView(
            searchView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        searchView.setup(
            onContactSelect = { contact ->
                ChatActivity.start(this, "c2c_${contact.userID}")
            },
            onGroupSelect = { group ->
                ChatActivity.start(this, "group_${group.groupID}")
            },
            onConversationSelect = { conversation ->
                val conversationID = conversation.conversationID
                if (!conversationID.isNullOrEmpty()) {
                    ChatActivity.start(this, conversationID)
                }
            },
            onMessageSelect = { message ->
                val conversationID = message.conversationID
                ChatActivity.start(this, conversationID, message)
            },
            onBack = { finish() }
        )
        applyColors(themeStore.themeState.value.currentTheme.tokens.color)

        activityScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
        activityScope?.launch {
            themeStore.themeState.collectLatest { state ->
                applyColors(state.currentTheme.tokens.color)
            }
        }
    }

    private fun applyColors(colors: ColorTokens) {
        rootContainer.setBackgroundColor(colors.bgColorOperate)
    }

    override fun onDestroy() {
        super.onDestroy()
        activityScope?.cancel()
        activityScope = null
    }
}

val MessageInfo.conversationID: String
    get() = if (conversationType == ConversationType.GROUP) {
        "group_$to"
    } else {
        if (isSentBySelf) "c2c_$to" else "c2c_${from.userID}"
    }
