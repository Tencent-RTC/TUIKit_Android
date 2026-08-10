package io.trtc.tuikit.chat.demo.chat

import CustomLinkMessage
import CustomLinkMessageRenderer
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.ForegroundColorSpan
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.google.gson.Gson
import io.trtc.tuikit.chat.uikit.components.common.EventBus
import io.trtc.tuikit.chat.uikit.components.common.observeOn
import io.trtc.tuikit.chat.uikit.components.common.expandTouchTarget
import io.trtc.tuikit.chat.uikit.components.contactlist.ui.ContactFlowLauncher
import io.trtc.tuikit.atomicx.theme.ThemeStore
import io.trtc.tuikit.atomicx.theme.tokens.ColorTokens
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.contact.ContactInfo
import io.trtc.tuikit.atomicxcore.api.contact.ContactStore
import io.trtc.tuikit.atomicxcore.api.contact.GetContactInfoCompletionHandler
import io.trtc.tuikit.atomicxcore.api.conversation.ConversationInfo
import io.trtc.tuikit.atomicxcore.api.conversation.ConversationListStore
import io.trtc.tuikit.atomicxcore.api.conversation.GetConversationInfoCompletionHandler
import io.trtc.tuikit.atomicxcore.api.group.GroupEvent
import io.trtc.tuikit.atomicxcore.api.group.GroupStore
import io.trtc.tuikit.atomicxcore.api.message.CustomMessagePayload
import io.trtc.tuikit.atomicxcore.api.message.MessageInfo
import io.trtc.tuikit.atomicxcore.api.message.MessageInputStore
import io.trtc.tuikit.atomicxcore.api.message.SendMessagePayload
import io.trtc.tuikit.chat.demo.common.AppConstants
import io.trtc.tuikit.chat.demo.common.BaseActivity
import io.trtc.tuikit.chat.demo.common.Event
import io.trtc.tuikit.chat.app.R
import io.trtc.tuikit.chat.uikit.components.messageinput.config.ChatMessageInputConfig
import io.trtc.tuikit.chat.uikit.components.messageinput.data.MessageInputActionIDs
import io.trtc.tuikit.chat.uikit.components.messageinput.data.MessageInputMenuAction
import io.trtc.tuikit.chat.uikit.components.messagelist.config.ChatMessageListConfig
import io.trtc.tuikit.chat.uikit.pages.ChatPageView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ChatActivity : BaseActivity() {

    private var activityScope: CoroutineScope? = null
    private val themeStore by lazy { ThemeStore.shared(this) }
    private val contactStore by lazy { ContactStore.shared }
    private val groupStore by lazy { GroupStore.shared }

    private lateinit var rootContainer: LinearLayout
    private lateinit var headerContainer: LinearLayout
    private lateinit var tvChatTitle: TextView
    private lateinit var btnBack: ImageView
    private lateinit var btnMore: ImageView
    private lateinit var headerDivider: View
    private lateinit var badgeContainer: FrameLayout
    private lateinit var tvUnreadBadge: TextView
    private lateinit var leftContainer: LinearLayout
    private lateinit var btnMultiSelectCancel: TextView
    private lateinit var chatPageView: ChatPageView
    private lateinit var chatSecurityBar: LinearLayout
    private lateinit var securityWarningText: TextView
    private lateinit var securityWarningClose: ImageView

    private var isPeerTyping = false
    private var latestChatTitle: String = ""

    companion object {
        private const val EXTRA_CONVERSATION_ID = "conversationID"
        private const val EXTRA_LOCATE_MESSAGE = "locateMessage"
        private const val C2C_CONVERSATION_ID_PREFIX = "c2c_"
        private const val GROUP_CONVERSATION_ID_PREFIX = "group_"
        private const val UNREAD_BADGE_DEBOUNCE_MS = 300L
        private const val DEMO_MESSAGE_INPUT_CUSTOM_LINK_ACTION_ID = "demo.messageInput.customLink"
        private val SECURITY_REPORT_LINK_COLOR = 0xFF147AFF.toInt()

        fun start(context: Context, conversationID: String) {
            start(context, conversationID, null)
        }

        fun start(context: Context, conversationID: String, locateMessage: MessageInfo?) {
            context.startActivity(Intent(context, ChatActivity::class.java).apply {
                putExtra(EXTRA_CONVERSATION_ID, conversationID)
                if (locateMessage != null) {
                    putExtra(EXTRA_LOCATE_MESSAGE, locateMessage)
                }
            })
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (isFinishing) {
            return
        }
        setContentView(R.layout.demo_activity_chat)

        val conversationID = intent?.getStringExtra(EXTRA_CONVERSATION_ID) ?: run {
            finish()
            return
        }
        @Suppress("DEPRECATION")
        val locateMessage = intent?.getParcelableExtra<MessageInfo>(EXTRA_LOCATE_MESSAGE)

        rootContainer = findViewById(R.id.demo_chatRootContainer)
        headerContainer = findViewById(R.id.demo_chatHeaderContainer)
        tvChatTitle = findViewById(R.id.demo_tvChatTitle)
        btnBack = findViewById(R.id.demo_btnBack)
        btnMore = findViewById(R.id.demo_btnMore)
        headerDivider = findViewById(R.id.demo_headerDivider)
        badgeContainer = findViewById(R.id.demo_badgeContainer)
        tvUnreadBadge = findViewById(R.id.demo_tvUnreadBadge)
        leftContainer = findViewById(R.id.demo_leftContainer)
        btnMultiSelectCancel = findViewById(R.id.demo_btnMultiSelectCancel)
        btnMultiSelectCancel.text = getString(R.string.demo_chat_header_cancel)
        updateChatTitle(ChatTitleResolver.resolve(conversationID = conversationID))
        val chatPageContainer = findViewById<FrameLayout>(R.id.demo_chatPageContainer)
        chatSecurityBar = findViewById(R.id.demo_chatSecurityBar)
        securityWarningText = findViewById(R.id.demo_securityWarningText)
        securityWarningClose = findViewById(R.id.demo_securityWarningClose)
        setupSecurityWarning()

        ViewCompat.setOnApplyWindowInsetsListener(rootContainer) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(top = systemBars.top)
            chatPageContainer.updatePadding(bottom = systemBars.bottom)
            insets
        }

        leftContainer.contentDescription = btnBack.contentDescription
        leftContainer.setOnClickListener { finish() }
        btnMore.setOnClickListener {
            handleChatSettingNavigation(
                userID = getUserID(conversationID),
                groupID = getGroupID(conversationID)
            )
        }
        btnMore.expandTouchTarget()

        val messageListConfig = ChatMessageListConfig().setCustomMessageRenderer(
            businessID = CustomLinkMessage.BUSINESS_ID,
            renderer = CustomLinkMessageRenderer(),
            summaryProvider = { summaryContext ->
                val payload = summaryContext.message.messagePayload as? CustomMessagePayload
                CustomLinkMessage.from(payload?.customData)?.text
            }
        )

        val messageInputConfig = ChatMessageInputConfig().customizeActions {
            val androidContext = editorContext.androidContext
            val conversationID = editorContext.conversationID
            add(
                MessageInputMenuAction(
                    ID = DEMO_MESSAGE_INPUT_CUSTOM_LINK_ACTION_ID,
                    title = androidContext.getString(R.string.demo_chat_custom_message_menu_title),
                    iconResID = android.R.drawable.ic_menu_send,
                    onClick = {
                        val customLinkMessage = CustomLinkMessage(
                            businessID = CustomLinkMessage.BUSINESS_ID,
                            text = androidContext.getString(R.string.demo_chat_custom_message_content),
                            link = androidContext.getString(R.string.demo_chat_custom_message_link)
                        )
                        val payload = SendMessagePayload.CustomSendMessagePayload(
                            customData = Gson().toJson(customLinkMessage),
                            description = customLinkMessage.text
                        )
                        MessageInputStore.create(conversationID).sendMessage(
                            payload = payload,
                            completion = object : CompletionHandler {
                                override fun onSuccess() {}
                                override fun onFailure(code: Int, desc: String) {}
                            }
                        )
                    }
                )
            )
        }

        chatPageView = ChatPageView(this)
        chatPageContainer.addView(chatPageView)
        chatPageView.setup(
            conversationID = conversationID,
            messageListConfig = messageListConfig,
            messageInputConfig = messageInputConfig,
            locateMessage = locateMessage,
            onUserClick = { userID ->
                handleChatSettingNavigation(userID = userID)
            },
            onMultiSelectStateChanged = { isMultiSelect ->
                updateHeaderForMultiSelect(isMultiSelect)
            },
            onTypingStatusChanged = { isTyping ->
                isPeerTyping = isTyping
                tvChatTitle.text = if (isTyping) {
                    getString(R.string.demo_chat_typing_indicator)
                } else {
                    latestChatTitle
                }
            }
        )
        btnMultiSelectCancel.setOnClickListener {
            chatPageView.exitMultiSelectMode()
        }
        applyColors(themeStore.themeState.value.currentTheme.tokens.color)

        activityScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

        activityScope?.launch {
            themeStore.themeState.collectLatest { state ->
                applyColors(state.currentTheme.tokens.color)
            }
        }

        activityScope?.let { scope ->
            EventBus.observeOn<Event.ContactDeleted>(scope) { event ->
                if ("c2c_${event.contactID}" == conversationID) {
                    finish()
                }
            }
            EventBus.observeOn<Event.GroupDeleted>(scope) { event ->
                if ("group_${event.groupID}" == conversationID) {
                    finish()
                }
            }
            scope.launch {
                groupStore.groupEventFlow.collectLatest { event ->
                    when (event) {
                        is GroupEvent.OnKickedFromGroup -> {
                            if ("group_${event.groupID}" == conversationID) {
                                finish()
                            }
                        }
                        is GroupEvent.OnGroupDismissed -> {
                            if ("group_${event.groupID}" == conversationID) {
                                finish()
                            }
                        }
                        else -> Unit
                    }
                }
            }
        }

        val conversationListStore = ConversationListStore.create()
        conversationListStore.getConversationInfo(conversationID, object : GetConversationInfoCompletionHandler {
            override fun onSuccess(conversationInfo: ConversationInfo) {
                updateChatTitle(
                    ChatTitleResolver.resolve(
                        conversationTitle = conversationInfo.title,
                        conversationID = conversationID
                    )
                )
            }

            override fun onFailure(code: Int, desc: String) {}
        })

        val currentConversationFlow = conversationListStore.state.conversationList
            .map { list -> list.firstOrNull { it.conversationID == conversationID } }
            .stateIn(activityScope!!, SharingStarted.WhileSubscribed(5000), null)

        activityScope?.launch {
            currentConversationFlow.collect { info ->
                if (info != null) {
                    updateChatTitle(
                        ChatTitleResolver.resolve(
                            conversationTitle = info.title,
                            conversationID = conversationID
                        )
                    )
                }
            }
        }

        activityScope?.launch {
            observeTotalUnreadCount(conversationListStore)
        }
    }

    @OptIn(FlowPreview::class)
    private suspend fun observeTotalUnreadCount(store: ConversationListStore) {
        store.state.totalUnreadCount
            .debounce(UNREAD_BADGE_DEBOUNCE_MS)
            .distinctUntilChanged()
            .collect { total ->
                if (total > 0L) {
                    badgeContainer.visibility = View.VISIBLE
                    tvUnreadBadge.text = if (total > 99L) "99+" else total.toString()
                } else {
                    badgeContainer.visibility = View.GONE
                }
            }
    }

    private fun handleChatSettingNavigation(userID: String? = null, groupID: String? = null) {
        if (!userID.isNullOrEmpty()) {
            contactStore.getContactInfo(
                userIDList = listOf(userID),
                completion = object : GetContactInfoCompletionHandler {
                    override fun onSuccess(contactInfoList: List<ContactInfo>) {
                        val info = contactInfoList.firstOrNull() ?: return
                        if (info.isFriend == true) {
                            ChatSettingActivity.startC2C(this@ChatActivity, userID)
                        } else {
                            ContactFlowLauncher.showAddFriendForContact(
                                context = this@ChatActivity,
                                contactInfo = info
                            )
                        }
                    }

                    override fun onFailure(code: Int, desc: String) {
                    }
                }
            )
        } else if (!groupID.isNullOrEmpty()) {
            ChatSettingActivity.startGroup(this, groupID)
        }
    }

    private fun getUserID(conversationID: String): String? {
        return if (conversationID.startsWith(C2C_CONVERSATION_ID_PREFIX)) {
            conversationID.removePrefix(C2C_CONVERSATION_ID_PREFIX)
        } else {
            null
        }
    }

    private fun getGroupID(conversationID: String): String? {
        return if (conversationID.startsWith(GROUP_CONVERSATION_ID_PREFIX)) {
            conversationID.removePrefix(GROUP_CONVERSATION_ID_PREFIX)
        } else {
            null
        }
    }

    private fun updateHeaderForMultiSelect(isMultiSelect: Boolean) {
        leftContainer.visibility = if (isMultiSelect) View.GONE else View.VISIBLE
        btnMultiSelectCancel.visibility = if (isMultiSelect) View.VISIBLE else View.GONE
        btnMore.visibility = if (isMultiSelect) View.INVISIBLE else View.VISIBLE
        if (isMultiSelect) {
            btnMultiSelectCancel.expandTouchTarget()
        }
    }

    private fun updateChatTitle(title: String) {
        latestChatTitle = title
        if (!isPeerTyping) {
            tvChatTitle.text = title
        }
    }

    override fun onBackPressed() {
        if (::chatPageView.isInitialized && chatPageView.isInMultiSelectMode()) {
            chatPageView.exitMultiSelectMode()
            return
        }
        @Suppress("DEPRECATION")
        super.onBackPressed()
    }

    private fun setupSecurityWarning() {
        val warning = getString(R.string.demo_chat_security_warning)
        val report = getString(R.string.demo_chat_security_warning_click_to_report)
        val builder = SpannableStringBuilder(warning).append(" ")
        val reportStart = builder.length
        builder.append(report)
        builder.setSpan(
            ForegroundColorSpan(SECURITY_REPORT_LINK_COLOR),
            reportStart,
            builder.length,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        builder.setSpan(
            object : ClickableSpan() {
                override fun onClick(widget: View) {
                    openSecurityReport()
                }

                override fun updateDrawState(ds: TextPaint) {
                    ds.isUnderlineText = false
                }
            },
            reportStart,
            builder.length,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        securityWarningText.text = builder
        securityWarningText.movementMethod = LinkMovementMethod.getInstance()
        securityWarningClose.setOnClickListener {
            chatSecurityBar.visibility = View.GONE
        }
    }

    private fun openSecurityReport() {
        try {
            startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(AppConstants.REPORT_URL))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: Exception) {
            Toast.makeText(this, getString(R.string.demo_open_browser_failed), Toast.LENGTH_SHORT).show()
        }
    }

    private fun applyColors(colors: ColorTokens) {
        rootContainer.setBackgroundColor(colors.bgColorOperate)
        headerContainer.setBackgroundColor(colors.bgColorOperate)
        tvChatTitle.setTextColor(colors.textColorPrimary)
        btnBack.imageTintList = ColorStateList.valueOf(colors.textColorSecondary)
        btnMore.imageTintList = ColorStateList.valueOf(colors.textColorSecondary)
        btnMultiSelectCancel.setTextColor(colors.textColorLink)
        headerDivider.setBackgroundColor(colors.strokeColorPrimary)

        val badgeBg = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(colors.buttonColorOff)
        }
        badgeContainer.background = badgeBg
        tvUnreadBadge.setTextColor(colors.textColorButton)
    }

    override fun onDestroy() {
        super.onDestroy()
        activityScope?.cancel()
        activityScope = null
    }
}
