package io.trtc.tuikit.chat.uikit.components.messagelist.ui.merged
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import io.trtc.tuikit.chat.uikit.R
import io.trtc.tuikit.chat.uikit.components.config.MessageAlignment
import io.trtc.tuikit.chat.uikit.components.messagelist.adapter.MessageListAdapter
import io.trtc.tuikit.chat.uikit.components.messagelist.config.ChatMessageListConfig
import io.trtc.tuikit.chat.uikit.components.messagelist.config.MessageListConfigProtocol
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.MessageQuoteLocatePolicy
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.MessageRendererResolver
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.layout.MessageListLocateCoordinator
import io.trtc.tuikit.chat.uikit.components.messagelist.utils.CallMessageParser
import io.trtc.tuikit.chat.uikit.components.common.expandTouchTarget
import io.trtc.tuikit.chat.uikit.components.messagelist.viewmodel.MessageListDisplayMapper
import io.trtc.tuikit.chat.uikit.components.messagelist.viewmodel.MessageListViewModel
import io.trtc.tuikit.chat.uikit.components.messagelist.viewmodel.MessageListViewModelFactory
import io.trtc.tuikit.atomicx.theme.ThemeStore
import io.trtc.tuikit.atomicx.theme.tokens.ColorTokens
import io.trtc.tuikit.atomicx.widget.basicwidget.toast.AtomicToast
import io.trtc.tuikit.atomicxcore.api.message.MergedMessageListCompletionHandler
import io.trtc.tuikit.atomicxcore.api.message.MergedMessagePayload
import io.trtc.tuikit.atomicxcore.api.message.MessageActionStore
import io.trtc.tuikit.atomicxcore.api.message.MessageInfo
import io.trtc.tuikit.atomicxcore.api.message.MessageListStore
import io.trtc.tuikit.atomicxcore.api.message.MessageQuoteInfo
import io.trtc.tuikit.atomicxcore.api.message.MessageStatus
import io.trtc.tuikit.atomicxcore.api.message.MessageType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MergedMessageDetailActivity : AppCompatActivity() {

    companion object {
        private const val EXTRA_MSG = "extra_msg"

        fun start(context: Context, mergedMessage: MessageInfo) {
            val intent = Intent(context, MergedMessageDetailActivity::class.java).apply {
                putExtra(EXTRA_MSG, mergedMessage)
            }
            if (context !is Activity) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    private lateinit var recyclerView: RecyclerView
    private lateinit var loadingView: ProgressBar
    private lateinit var titleView: TextView
    private lateinit var backButton: ImageView
    private lateinit var dividerView: View
    private lateinit var topBar: View
    private lateinit var rootContainer: ViewGroup
    private lateinit var adapter: MessageListAdapter
    private lateinit var viewModel: MessageListViewModel
    private lateinit var messageListStore: MessageListStore
    private var scope: CoroutineScope? = null
    private var locateCoordinator: MessageListLocateCoordinator? = null
    private val mergedMessageSource = MutableStateFlow<List<MessageInfo>>(emptyList())

    private val config: MessageListConfigProtocol by lazy {
        ChatMessageListConfig(
            alignment = resolveMergedDetailAlignment()
        )
    }
    private val rendererResolver by lazy {
        MessageRendererResolver(
            customRules = (config as? ChatMessageListConfig)?.customRenderRules.orEmpty()
        )
    }

    private fun resolveMergedDetailAlignment(): MessageAlignment {
        return if (resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL) {
            MessageAlignment.RIGHT
        } else {
            MessageAlignment.LEFT
        }
    }

    private val themeStore by lazy { ThemeStore.shared(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        setContentView(R.layout.message_list_merged_detail_activity)

        @Suppress("DEPRECATION")
        val message = intent.getParcelableExtra<MessageInfo>(EXTRA_MSG)
        if (message == null) {
            finish()
            return
        }

        initViews()
        setupTitle(message)
        setupMessageList(message)
        applyTheme()
    }

    private fun initViews() {
        rootContainer = findViewById(R.id.message_list_merged_root)
        recyclerView = findViewById(R.id.message_list_merged_recycler_view)
        loadingView = findViewById(R.id.message_list_merged_loading)
        titleView = findViewById(R.id.message_list_merged_title)
        backButton = findViewById(R.id.message_list_merged_back_button)
        dividerView = findViewById(R.id.message_list_merged_divider)
        topBar = findViewById(R.id.message_list_merged_top_bar)

        ViewCompat.setOnApplyWindowInsetsListener(rootContainer) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(top = systemBars.top, bottom = systemBars.bottom)
            insets
        }

        backButton.setOnClickListener { finish() }
        backButton.expandTouchTarget()
    }

    private fun setupTitle(message: MessageInfo) {
        val title = (message.messagePayload as? MergedMessagePayload)?.title
        titleView.text = if (title.isNullOrBlank()) {
            getString(R.string.message_list_merge_message)
        } else {
            title
        }
    }

    private fun setupMessageList(message: MessageInfo) {
        messageListStore = MessageListStore.create("")

        viewModel = ViewModelProvider(
            this,
            MessageListViewModelFactory(
                messageListStore = messageListStore,
                conversationID = "",
                locateMessage = null,
                messageListConfig = config,
                enableMediaPreviewBoundaryLoading = false,
                reverseMediaPreviewMessageOrder = false
            )
        )[MessageListViewModel::class.java]

        adapter = MessageListAdapter(
            context = this,
            viewModel = viewModel,
            config = config,
            onItemLongClick = { _, _ -> },
            onUserClick = { },
            onQuoteClick = { _, quoteInfo -> locateQuotedMessage(quoteInfo) },
            enableMessageInteraction = false,
            enableQuoteNavigation = true,
            showMessageReadReceipt = false,
            showInlineMessageTime = true,
            resolver = rendererResolver
        )

        recyclerView.layoutManager = LinearLayoutManager(this).apply {
            reverseLayout = false
        }
        recyclerView.adapter = adapter
        (recyclerView.itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false

        val coordinator = MessageListLocateCoordinator(
            recyclerView = recyclerView,
            adapterProvider = { if (::adapter.isInitialized) adapter else null }
        )
        locateCoordinator = coordinator
        recyclerView.addOnChildAttachStateChangeListener(coordinator.childAttachStateChangeListener)

        val activityScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        scope = activityScope

        val displayMapper = MessageListDisplayMapper(
            reverseSource = false,
            shouldDisplayMessage = ::shouldDisplayMessage
        )
        val mergedMessageList = mergedMessageSource.map { list ->
            displayMapper.map(list)
        }.stateIn(
            scope = activityScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        viewModel.initializeAudioPlayer()

        scope?.launch {
            mergedMessageList.collectLatest { list ->
                viewModel.setMediaPreviewMessages(list)
                loadingView.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
                adapter.submitList(list)
            }
        }

        scope?.launch {
            viewModel.audioPlayingState.collectLatest {
                adapter.notifyDataSetChanged()
            }
        }

        scope?.launch {
            themeStore.themeState.collectLatest { themeState ->
                applyColors(themeState.currentTheme.tokens.color)
                adapter.notifyDataSetChanged()
            }
        }

        MessageActionStore.create(message).downloadMergedMessageList(
            object : MergedMessageListCompletionHandler {
                override fun onSuccess(messageList: List<MessageInfo>) {
                    mergedMessageSource.value = messageList
                }

                override fun onFailure(code: Int, desc: String) {
                    mergedMessageSource.value = emptyList()
                }
            }
        )
    }

    private fun applyTheme() {
        applyColors(themeStore.themeState.value.currentTheme.tokens.color)
    }

    private fun applyColors(colors: ColorTokens) {
        rootContainer.setBackgroundColor(colors.bgColorOperate)
        topBar.setBackgroundColor(colors.bgColorOperate)
        titleView.setTextColor(colors.textColorPrimary)
        backButton.imageTintList = ColorStateList.valueOf(colors.textColorSecondary)
        dividerView.setBackgroundColor(colors.strokeColorSecondary)
        recyclerView.setBackgroundColor(colors.bgColorOperate)
        window.setBackgroundDrawable(ColorDrawable(colors.bgColorOperate))

        val controller = WindowInsetsControllerCompat(window, window.decorView)
        val isLight = isColorLight(colors.bgColorOperate)
        controller.isAppearanceLightStatusBars = isLight
        controller.isAppearanceLightNavigationBars = isLight
    }

    private fun isColorLight(color: Int): Boolean {
        val r = (color shr 16) and 0xFF
        val g = (color shr 8) and 0xFF
        val b = color and 0xFF
        val luminance = (0.299 * r + 0.587 * g + 0.114 * b) / 255
        return luminance > 0.5
    }

    private fun locateQuotedMessage(quoteInfo: MessageQuoteInfo) {
        if (MessageQuoteLocatePolicy.isOriginalMessageUnreachable(quoteInfo)) {
            showQuotedOriginalUnreachableToast()
            return
        }
        val messages = if (::adapter.isInitialized) adapter.currentList else emptyList()
        val targetMessageId = MessageQuoteLocatePolicy.findLoadedTargetMessageId(quoteInfo, messages)
        if (targetMessageId == null) {
            if (isFilteredQuoteTarget(quoteInfo)) {
                showQuotedOriginalFilteredToast()
            } else {
                showQuotedOriginalUnreachableToast()
            }
            return
        }
        locateCoordinator?.requestLocateMessage(targetMessageId)
    }

    private fun isFilteredQuoteTarget(quoteInfo: MessageQuoteInfo): Boolean {
        val rawMessages = mergedMessageSource.value
        val target = rawMessages.firstOrNull { quoteInfo.msgID.isNotBlank() && it.msgID == quoteInfo.msgID }
            ?: rawMessages.firstOrNull { quoteInfo.sequence > 0 && it.sequence == quoteInfo.sequence }
            ?: return false
        return !shouldDisplayMessage(target)
    }

    private fun showQuotedOriginalUnreachableToast() {
        AtomicToast.show(
            this,
            getString(R.string.message_list_quote_original_unreachable),
            style = AtomicToast.Style.INFO
        )
    }

    private fun showQuotedOriginalFilteredToast() {
        AtomicToast.show(
            this,
            getString(R.string.message_list_quote_original_filtered),
            style = AtomicToast.Style.INFO
        )
    }

    private fun shouldDisplayMessage(message: MessageInfo): Boolean {
        if (!config.isShowSystemMessage &&
            (message.messageType == MessageType.TIPS || message.status == MessageStatus.REVOKED)
        ) {
            return false
        }
        if (!config.isShowUnsupportMessage &&
            rendererResolver.isDefaultBuiltInRenderer(message)
        ) {
            return false
        }
        if (message.messageType == MessageType.CUSTOM) {
            val callModel = CallMessageParser.parse(message)
            if (callModel?.isExcludeFromHistory == true) {
                return false
            }
        }
        if (config.messageExclusionMatchers.any { it.matches(message) }) {
            return false
        }
        return true
    }

    override fun onDestroy() {
        locateCoordinator?.let { coordinator ->
            recyclerView.removeOnChildAttachStateChangeListener(coordinator.childAttachStateChangeListener)
            coordinator.cancel()
        }
        locateCoordinator = null
        scope?.cancel()
        scope = null
        super.onDestroy()
    }
}
