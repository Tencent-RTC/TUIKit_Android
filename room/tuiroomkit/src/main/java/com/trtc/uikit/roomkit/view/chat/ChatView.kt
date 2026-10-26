package com.trtc.uikit.roomkit.view.chat

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.view.inputmethod.InputMethodManager
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import com.trtc.uikit.roomkit.R
import com.trtc.uikit.roomkit.base.ui.BaseView
import com.trtc.uikit.roomkit.base.ui.RoomTopBar
import io.trtc.tuikit.atomicx.theme.Theme
import io.trtc.tuikit.atomicx.theme.ThemeStore
import io.trtc.tuikit.chat.uikit.components.messageinput.config.ChatMessageInputConfig
import io.trtc.tuikit.chat.uikit.components.messagelist.config.ChatMessageListConfig
import io.trtc.tuikit.chat.uikit.pages.ChatPageView

class ChatView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : BaseView(context, attrs, defStyleAttr) {

    private val topBar: RoomTopBar
    private val chatPageView: ChatPageView
    private var currentTheme: Theme? = null

    init {
        LayoutInflater.from(context).inflate(R.layout.roomkit_view_chat, this, true)
        topBar = findViewById(R.id.top_bar_chat)
        chatPageView = findViewById(R.id.chat_page_view)
        topBar.onBackClick = { hide() }
        visibility = GONE
        applyBottomInsets()
    }

    public override fun init(roomID: String) {
        super.init(roomID)
        val inputConfig = ChatMessageInputConfig(
            isShowAudioRecorder = false,
            isShowPhotoTaker = false,
            isShowAudioCall = false,
            isShowVideoCall = false,
            enableLongPressToTalk = false,
            enableMention = false
        )
        val messageListConfig = ChatMessageListConfig().apply {
            isSupportMultiSelect = false
            isSupportForward = false
            isSupportQuote = false
            isSupportConvertToText = false
            isSupportTranslate = false
            isSupportListenFromHere = false
        }
        chatPageView.setup(
            conversationID = "group_$roomID",
            messageInputConfig = inputConfig,
            messageListConfig = messageListConfig
        )
    }

    override fun initStore(roomID: String) {
    }

    override fun addObserver() {
    }

    override fun removeObserver() {
    }

    fun show() {
        if (isVisible) return
        currentTheme = ThemeStore.shared(context).themeState.value.currentTheme
        ThemeStore.shared(context).setTheme(Theme.lightTheme(context))
        visibility = VISIBLE
        startAnimation(AnimationUtils.loadAnimation(context, R.anim.roomkit_slide_in_bottom))
        applyChatStatusBarStyle()
    }

    fun hide() {
        if (visibility != VISIBLE) return
        currentTheme?.let { ThemeStore.shared(context).setTheme(it) }
        hideSoftKeyboard()
        val anim = AnimationUtils.loadAnimation(context, R.anim.roomkit_slide_out_bottom)
        anim.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationStart(animation: Animation?) {}
            override fun onAnimationRepeat(animation: Animation?) {}
            override fun onAnimationEnd(animation: Animation?) {
                visibility = GONE
                clearAnimation()
                restoreRoomStatusBarStyle()
            }
        })
        startAnimation(anim)
    }

    fun handleBackPressed(): Boolean {
        if (isVisible) {
            hide()
            return true
        }
        return false
    }

    private fun applyChatStatusBarStyle() {
        val activity = context as? Activity ?: return
        val window = activity.window
        window.statusBarColor = Color.WHITE
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = true
    }

    private fun restoreRoomStatusBarStyle() {
        val activity = context as? Activity ?: return
        val window = activity.window
        window.statusBarColor = Color.TRANSPARENT
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false
    }

    private fun hideSoftKeyboard() {
        val activity = context as? Activity ?: return
        val imm = activity.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager ?: return
        imm.hideSoftInputFromWindow(activity.currentFocus?.windowToken ?: windowToken, 0)
    }

    private fun applyBottomInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
            val navBarHeight = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            setPadding(paddingLeft, paddingTop, paddingRight, navBarHeight)
            insets
        }
        ViewCompat.requestApplyInsets(this)
    }
}
