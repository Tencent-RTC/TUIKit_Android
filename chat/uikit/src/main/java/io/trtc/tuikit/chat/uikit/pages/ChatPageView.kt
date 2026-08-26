package io.trtc.tuikit.chat.uikit.pages
import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import io.trtc.tuikit.chat.uikit.components.common.ConversationIDUtil
import io.trtc.tuikit.chat.uikit.components.chatbot.ChatbotConversationController
import io.trtc.tuikit.chat.uikit.components.chatbot.ChatbotConversationControllers
import io.trtc.tuikit.chat.uikit.components.chatbot.ChatbotConversationPolicy
import io.trtc.tuikit.chat.uikit.components.chatbot.ChatbotMessageListConfig
import io.trtc.tuikit.chat.uikit.components.messagelist.typing.TypingIndicatorController
import io.trtc.tuikit.chat.uikit.components.messageinput.config.ChatMessageInputConfig
import io.trtc.tuikit.chat.uikit.components.messageinput.config.MessageInputConfigProtocol
import io.trtc.tuikit.chat.uikit.components.messageinput.ui.MessageInputView
import io.trtc.tuikit.chat.uikit.components.messagelist.config.ChatMessageListConfig
import io.trtc.tuikit.chat.uikit.components.messagelist.config.MessageListConfigProtocol
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.MessageListView
import io.trtc.tuikit.atomicx.theme.ThemeStore
import io.trtc.tuikit.atomicx.theme.tokens.ColorTokens
import io.trtc.tuikit.atomicxcore.api.message.MessageInfo
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.conversation.ConversationListStore
import io.trtc.tuikit.atomicx.widget.basicwidget.toast.AtomicToast
import io.trtc.tuikit.chat.uikit.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ChatPageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val messageListView: MessageListView
    private val messageInputView: MessageInputView

    private val themeStore = ThemeStore.shared(context)
    private var viewScope: CoroutineScope? = null

    private var typingController: TypingIndicatorController? = null
    private var typingCollectJob: Job? = null
    private var typingConversationID: String? = null
    private var typingEnabled = false
    private var chatbotController: ChatbotConversationController? = null
    private var chatbotConversationID: String? = null
    private var isChatbotControllerAcquired = false

    private var onTypingStatusChanged: (Boolean) -> Unit = {}

    init {
        LayoutInflater.from(context).inflate(R.layout.uikit_page_chat, this, true)
        messageListView = findViewById(R.id.uikit_message_list_view)
        messageInputView = findViewById(R.id.uikit_message_input_view)
        applyColors(themeStore.themeState.value.currentTheme.tokens.color)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        retainChatbotController()
        viewScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
        viewScope?.launch {
            themeStore.themeState.collectLatest { state ->
                applyColors(state.currentTheme.tokens.color)
            }
        }
        obtainTypingControllerIfNeeded()
        bindTypingController()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        releaseTypingController()
        releaseChatbotController(clearReference = false)
        viewScope?.cancel()
        viewScope = null
    }

    private fun applyColors(colors: ColorTokens) {
        setBackgroundColor(colors.bgColorOperate)
    }

    private fun obtainTypingControllerIfNeeded() {
        if (!isAttachedToWindow || !typingEnabled || typingController != null) return
        val conversationID = typingConversationID ?: return
        typingController = TypingIndicatorController.obtain(conversationID)
    }

    private fun releaseTypingController() {
        typingCollectJob?.cancel()
        typingCollectJob = null
        typingController?.let { TypingIndicatorController.release(it) }
        typingController = null
    }

    private fun bindTypingController() {
        typingCollectJob?.cancel()
        typingCollectJob = null
        val controller = typingController ?: return
        val scope = viewScope ?: return
        typingCollectJob = scope.launch {
            controller.typingState.collectLatest { isTyping ->
                onTypingStatusChanged(isTyping)
            }
        }
    }

    fun setup(
        conversationID: String,
        locateMessage: MessageInfo? = null,
        messageListConfig: MessageListConfigProtocol = ChatMessageListConfig(),
        messageInputConfig: MessageInputConfigProtocol = ChatMessageInputConfig(),
        onTypingStatusChanged: (Boolean) -> Unit = {},
        onMultiSelectStateChanged: (Boolean) -> Unit = {},
        onUserClick: (String) -> Unit = {}
    ) {
        this.onTypingStatusChanged = onTypingStatusChanged

        releaseTypingController()
        releaseChatbotController(clearReference = true)
        typingConversationID = conversationID
        val isChatbotConversation = isChatbotConversationID(conversationID)
        val effectiveMessageListConfig = if (isChatbotConversation) {
            ChatbotMessageListConfig(messageListConfig)
        } else {
            messageListConfig
        }
        val controller = if (isChatbotConversation) {
            ChatbotConversationControllers.acquire(conversationID).also {
                chatbotController = it
                chatbotConversationID = conversationID
                isChatbotControllerAcquired = true
            }
        } else {
            null
        }
        typingEnabled = effectiveMessageListConfig.enableTyping &&
            ConversationIDUtil.isC2C(conversationID)
        obtainTypingControllerIfNeeded()
        bindTypingController()

        val multiSelectCallback: (Boolean) -> Unit = { isMultiSelect ->
            messageInputView.visibility = if (isMultiSelect) GONE else VISIBLE
            onMultiSelectStateChanged(isMultiSelect)
        }
        val userClickCallback: (String) -> Unit = { userID ->
            if (!ChatbotConversationPolicy.isChatbotID(userID)) {
                onUserClick(userID)
            }
        }
        if (controller == null) {
            messageListView.setup(
                conversationID = conversationID,
                config = effectiveMessageListConfig,
                locateMessage = locateMessage,
                onMultiSelectStateChanged = multiSelectCallback,
                onUserClick = userClickCallback
            )
        } else {
            messageListView.setupWithChatbot(
                conversationID = conversationID,
                config = effectiveMessageListConfig,
                locateMessage = locateMessage,
                chatbotController = controller,
                onMultiSelectStateChanged = multiSelectCallback,
                onUserClick = userClickCallback
            )
        }
        val typingStatusSender: ((Boolean) -> Unit)? = if (typingEnabled) {
            { isTyping -> typingController?.sendTypingStatus(isTyping) }
        } else {
            null
        }
        if (controller == null) {
            messageInputView.setup(
                conversationID = conversationID,
                config = messageInputConfig,
                typingStatusSender = typingStatusSender
            )
        } else {
            messageInputView.setupWithChatbot(
                conversationID = conversationID,
                config = messageInputConfig,
                chatbotController = controller,
                typingStatusSender = null
            )
        }
    }

    fun exitMultiSelectMode() {
        messageListView.exitMultiSelectMode()
    }

    fun isInMultiSelectMode(): Boolean {
        return messageListView.isInMultiSelectMode()
    }

    @JvmOverloads
    fun clearChatHistory(completion: CompletionHandler? = null): Boolean {
        val conversationID = chatbotConversationID ?: return false
        val controller = chatbotController ?: return false
        if (controller.isActive) {
            AtomicToast.show(
                context,
                context.getString(R.string.message_input_chatbot_waiting_tips)
            )
            return false
        }
        ConversationListStore.create().clearConversationMessages(
            conversationID,
            object : CompletionHandler {
                override fun onSuccess() {
                    ChatbotConversationControllers.clear(conversationID)
                    completion?.onSuccess()
                }

                override fun onFailure(code: Int, desc: String) {
                    completion?.onFailure(code, desc)
                }
            }
        )
        return true
    }

    fun isChatbotConversation(): Boolean {
        return chatbotConversationID != null
    }

    private fun retainChatbotController() {
        if (isChatbotControllerAcquired) {
            return
        }
        val conversationID = chatbotConversationID ?: return
        val controller = chatbotController ?: return
        ChatbotConversationControllers.retain(conversationID, controller)
        isChatbotControllerAcquired = true
    }

    private fun releaseChatbotController(clearReference: Boolean) {
        val conversationID = chatbotConversationID
        val controller = chatbotController
        if (isChatbotControllerAcquired && conversationID != null && controller != null) {
            ChatbotConversationControllers.release(conversationID, controller)
        }
        isChatbotControllerAcquired = false
        if (clearReference) {
            chatbotConversationID = null
            chatbotController = null
        }
    }

    companion object {
        @JvmStatic
        fun isChatbotConversationID(conversationID: String?): Boolean {
            return ChatbotConversationPolicy.isChatbotConversation(conversationID)
        }
    }
}
