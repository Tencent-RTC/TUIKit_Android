package io.trtc.tuikit.chat.uikit.components.chatsetting.ui
import android.content.Context
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.lifecycle.ViewModelProvider
import io.trtc.tuikit.chat.uikit.R
import io.trtc.tuikit.chat.uikit.components.chatsetting.config.C2CChatSettingConfig
import io.trtc.tuikit.chat.uikit.components.chatsetting.config.C2CChatSettingConfigProtocol
import io.trtc.tuikit.chat.uikit.components.chatsetting.model.C2CChatSettingItemContext
import io.trtc.tuikit.chat.uikit.components.chatsetting.model.ChatSettingCustomItem
import io.trtc.tuikit.chat.uikit.components.chatsetting.model.ChatSettingItemIDs
import io.trtc.tuikit.chat.uikit.components.chatsetting.model.ChatSettingSectionIDs
import io.trtc.tuikit.chat.uikit.components.chatsetting.model.buildChatSettingItems
import io.trtc.tuikit.chat.uikit.components.common.findViewModelStoreOwner
import io.trtc.tuikit.chat.uikit.components.chatsetting.viewmodel.C2CChatSettingViewModel
import io.trtc.tuikit.chat.uikit.components.chatsetting.viewmodel.C2CChatSettingViewModelFactory
import io.trtc.tuikit.atomicx.common.util.ScreenUtil.dp2px
import io.trtc.tuikit.chat.uikit.components.common.AtomicCallEventPublisher
import io.trtc.tuikit.atomicx.theme.ThemeStore
import io.trtc.tuikit.atomicx.theme.tokens.ColorTokens
import io.trtc.tuikit.atomicx.widget.basicwidget.alertdialog.AtomicAlertDialog
import io.trtc.tuikit.atomicx.widget.basicwidget.alertdialog.cancelButton
import io.trtc.tuikit.atomicx.widget.basicwidget.alertdialog.confirmButton
import io.trtc.tuikit.chat.uikit.components.widgets.Avatar
import io.trtc.tuikit.atomicx.widget.basicwidget.toast.AtomicToast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class C2CChatSettingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private var onSendMessageClick: (() -> Unit)? = null
    private var onVoiceCallClick: (() -> Unit)? = null
    private var onVideoCallClick: (() -> Unit)? = null
    private var onContactDeleted: (() -> Unit)? = null

    private var viewModel: C2CChatSettingViewModel? = null
    private var viewScope: CoroutineScope? = null

    private lateinit var scrollView: ScrollView
    private lateinit var contentLayout: LinearLayout

    private lateinit var userInfoLayout: LinearLayout
    private lateinit var avatarView: Avatar
    private lateinit var nicknameTextView: TextView
    private lateinit var idTextView: TextView
    private lateinit var signatureTextView: TextView

    private lateinit var remarkRow: SettingRowNavigate

    private lateinit var doNotDisturbRow: SettingRowToggle
    private lateinit var pinRow: SettingRowToggle
    private lateinit var blacklistRow: SettingRowToggle
    private lateinit var chatBackgroundRow: SettingRowNavigate

    private var config: C2CChatSettingConfigProtocol = C2CChatSettingConfig()
    private var currentUserID: String? = null
    private lateinit var itemRenderer: ChatSettingItemLayoutRenderer<C2CChatSettingItemContext>

    fun setup(
        userID: String,
        onSendMessageClick: (() -> Unit)? = null,
        onVoiceCallClick: (() -> Unit)? = null,
        onVideoCallClick: (() -> Unit)? = null,
        onContactDeleted: (() -> Unit)? = null,
        config: C2CChatSettingConfigProtocol = C2CChatSettingConfig(),
    ) {
        this.onSendMessageClick = onSendMessageClick
        this.onVoiceCallClick = onVoiceCallClick
        this.onVideoCallClick = onVideoCallClick
        this.onContactDeleted = onContactDeleted
        this.config = config

        val owner = context.findViewModelStoreOwner() ?: return

        cleanupBinding()
        currentUserID = userID
        val viewModelKey = "${C2CChatSettingViewModel::class.java.name}:$userID"
        viewModel = ViewModelProvider(owner, C2CChatSettingViewModelFactory(userID, context))
            .get(viewModelKey, C2CChatSettingViewModel::class.java)

        buildUI()

        if (isAttachedToWindow) {
            bindViewModel()
        }
    }

    private fun buildUI() {
        val userID = currentUserID ?: return
        layoutDirection = LAYOUT_DIRECTION_LOCALE
        removeAllViews()
        val dm = resources.displayMetrics
        val colors = ThemeStore.shared(context).themeState.value.currentTheme.tokens.color

        scrollView = ScrollView(context).apply {
            layoutDirection = LAYOUT_DIRECTION_LOCALE
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            setBackgroundColor(colors.bgColorInput)
        }

        contentLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = LAYOUT_DIRECTION_LOCALE
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
            setBackgroundColor(colors.bgColorInput)
        }

        userInfoLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val horizontalPadding = dp2px(16f, dm).toInt()
            val verticalPadding = dp2px(12f, dm).toInt()
            setPadding(horizontalPadding, verticalPadding, horizontalPadding, verticalPadding)
        }

        avatarView = Avatar(context).apply {
            setSize(Avatar.AvatarSize.L)
        }
        userInfoLayout.addView(avatarView)

        val textInfoLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val leftMargin = dp2px(16f, dm).toInt()
            layoutParams = LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
            ).apply { marginStart = leftMargin }
        }

        nicknameTextView = TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            maxLines = 1
        }
        textInfoLayout.addView(nicknameTextView)

        idTextView = TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            val topMargin = dp2px(4f, dm).toInt()
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { this.topMargin = topMargin }
        }
        textInfoLayout.addView(idTextView)

        signatureTextView = TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            textAlignment = View.TEXT_ALIGNMENT_VIEW_START
            textDirection = View.TEXT_DIRECTION_LOCALE
            maxLines = 1
            val topMargin = dp2px(2f, dm).toInt()
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { this.topMargin = topMargin }
        }
        textInfoLayout.addView(signatureTextView)

        userInfoLayout.addView(textInfoLayout)

        remarkRow = SettingRowNavigate(context).apply {
            setShowArrow(false)
            setCustomAccessory(R.drawable.chat_setting_group_name_edit_icon)
        }
        remarkRow.setOnClickListener {
            val vm = viewModel ?: return@setOnClickListener
            TextInputDialog(
                context = context,
                title = context.getString(R.string.chat_setting_modify_contact_remark),
                initialText = vm.friendRemark.value,
                onConfirm = { vm.setFriendRemark(it) }
            ).show()
        }

        doNotDisturbRow = SettingRowToggle(context).apply {
            setTitle(context.getString(R.string.chat_setting_do_not_disturb))
            onToggleChanged = { checked -> viewModel?.setDoNotDisturb(checked) }
        }

        pinRow = SettingRowToggle(context).apply {
            setTitle(context.getString(R.string.chat_setting_pin))
            onToggleChanged = { checked -> viewModel?.setPinChat(checked) }
        }

        chatBackgroundRow = SettingRowNavigate(context).apply {
            setTitle(context.getString(R.string.chat_setting_chat_background))
            setShowArrow(true)
            setOnClickListener {
                viewModel?.let { vm -> showChatBackgroundPicker(vm) }
            }
        }

        blacklistRow = SettingRowToggle(context).apply {
            setTitle(context.getString(R.string.chat_setting_add_blacklist))
            onToggleChanged = { viewModel?.toggleBlacklist() }
        }

        val sendMessageButton = createSendMessageButton()
        val voiceCallButton = createVoiceCallButton()
        val videoCallButton = createVideoCallButton()
        val clearHistoryButton = createClearHistoryButton()
        val deleteFriendButton = createDeleteFriendButton()

        val itemContext = C2CChatSettingItemContext(
            androidContext = context,
            userID = userID,
        )
        val items = buildChatSettingItems(
            itemContext = itemContext,
            defaults = buildList<ChatSettingCustomItem<C2CChatSettingItemContext>> {
                if (config.isShowHeader) {
                    add(ChatSettingCustomItem(ChatSettingItemIDs.C2C_HEADER) { userInfoLayout })
                }
                if (config.isShowRemark) {
                    add(
                        ChatSettingCustomItem(
                            ChatSettingItemIDs.C2C_REMARK,
                            ChatSettingSectionIDs.C2C_REMARK,
                        ) { remarkRow }
                    )
                }
                if (config.isShowDoNotDisturb) {
                    add(
                        ChatSettingCustomItem(
                            ChatSettingItemIDs.C2C_DO_NOT_DISTURB,
                            ChatSettingSectionIDs.C2C_SWITCHES,
                        ) { doNotDisturbRow }
                    )
                }
                if (config.isShowPin) {
                    add(
                        ChatSettingCustomItem(
                            ChatSettingItemIDs.C2C_PIN,
                            ChatSettingSectionIDs.C2C_SWITCHES,
                        ) { pinRow }
                    )
                }
                if (config.isShowChatBackground) {
                    add(
                        ChatSettingCustomItem(
                            ChatSettingItemIDs.C2C_CHAT_BACKGROUND,
                            ChatSettingSectionIDs.C2C_CHAT_BACKGROUND,
                        ) { chatBackgroundRow }
                    )
                }
                if (config.isShowBlacklist) {
                    add(
                        ChatSettingCustomItem(
                            ChatSettingItemIDs.C2C_BLACKLIST,
                            ChatSettingSectionIDs.C2C_BLACKLIST,
                        ) { blacklistRow }
                    )
                }
                if (config.isShowSendMessage) {
                    add(
                        ChatSettingCustomItem(
                            ChatSettingItemIDs.C2C_SEND_MESSAGE,
                            ChatSettingSectionIDs.C2C_ACTIONS,
                        ) { sendMessageButton }
                    )
                }
                if (config.isShowVoiceCall) {
                    add(
                        ChatSettingCustomItem(
                            ChatSettingItemIDs.C2C_VOICE_CALL,
                            ChatSettingSectionIDs.C2C_ACTIONS,
                        ) { voiceCallButton }
                    )
                }
                if (config.isShowVideoCall) {
                    add(
                        ChatSettingCustomItem(
                            ChatSettingItemIDs.C2C_VIDEO_CALL,
                            ChatSettingSectionIDs.C2C_ACTIONS,
                        ) { videoCallButton }
                    )
                }
                if (config.isShowClearHistory) {
                    add(
                        ChatSettingCustomItem(
                            ChatSettingItemIDs.C2C_CLEAR_HISTORY,
                            ChatSettingSectionIDs.C2C_ACTIONS,
                        ) { clearHistoryButton }
                    )
                }
                if (config.isShowDeleteFriend) {
                    add(
                        ChatSettingCustomItem(
                            ChatSettingItemIDs.C2C_DELETE_FRIEND,
                            ChatSettingSectionIDs.C2C_ACTIONS,
                        ) { deleteFriendButton }
                    )
                }
            },
            customizer = config.itemCustomizer,
        )
        itemRenderer = ChatSettingItemLayoutRenderer(context, contentLayout)
        itemRenderer.setItems(itemContext, items)

        scrollView.addView(contentLayout)
        addView(scrollView)
        applyThemeColors(colors)
    }

    private fun createSendMessageButton(): SettingRowButton {
        return SettingRowButton(context).apply {
            setTitle(context.getString(R.string.chat_setting_send_messages))
            setButtonStyle(SettingRowButton.Style.LINK)
            setOnClickListener { onSendMessageClick?.invoke() }
        }
    }

    private fun createVoiceCallButton(): SettingRowButton {
        return SettingRowButton(context).apply {
            setTitle(context.getString(R.string.chat_setting_voice_call))
            setButtonStyle(SettingRowButton.Style.LINK)
            setOnClickListener {
                onVoiceCallClick?.invoke() ?: currentUserID?.let { userID ->
                    AtomicCallEventPublisher.publishStartCall(
                        participantIds = listOf(userID),
                        mediaType = AtomicCallEventPublisher.MEDIA_TYPE_AUDIO
                    )
                }
            }
        }
    }

    private fun createVideoCallButton(): SettingRowButton {
        return SettingRowButton(context).apply {
            setTitle(context.getString(R.string.chat_setting_video_call))
            setButtonStyle(SettingRowButton.Style.LINK)
            setOnClickListener {
                onVideoCallClick?.invoke() ?: currentUserID?.let { userID ->
                    AtomicCallEventPublisher.publishStartCall(
                        participantIds = listOf(userID),
                        mediaType = AtomicCallEventPublisher.MEDIA_TYPE_VIDEO
                    )
                }
            }
        }
    }

    private fun createClearHistoryButton(): SettingRowButton {
        return SettingRowButton(context).apply {
            setTitle(context.getString(R.string.chat_setting_clear_history_messages))
            setDangerStyle(true)
            setOnClickListener {
                AtomicAlertDialog(context).apply {
                    init {
                        content = context.getString(R.string.chat_setting_clear_contact_history_messages_tips)
                        confirmButton(context.getString(R.string.uikit_confirm)) { _ ->
                            viewModel?.clearChatHistory()
                        }
                        cancelButton(context.getString(R.string.uikit_cancel))
                    }
                    show()
                }
            }
        }
    }

    private fun createDeleteFriendButton(): SettingRowButton {
        return SettingRowButton(context).apply {
            setTitle(context.getString(R.string.chat_setting_delete_friend))
            setButtonStyle(SettingRowButton.Style.DANGER)
            setOnClickListener {
                AtomicAlertDialog(context).apply {
                    init {
                        content = context.getString(R.string.chat_setting_delete_friend_tips)
                        confirmButton(
                            context.getString(R.string.uikit_confirm),
                            type = AtomicAlertDialog.TextColorPreset.RED
                        ) { _ ->
                            viewModel?.deleteFriend(
                                onSuccess = { onContactDeleted?.invoke() },
                                onFailure = { _, desc ->
                                    AtomicToast.show(context, desc, style = AtomicToast.Style.ERROR)
                                }
                            )
                        }
                        cancelButton(context.getString(R.string.uikit_cancel))
                    }
                    show()
                }
            }
        }
    }

    private fun bindViewModel() {
        val vm = viewModel ?: return
        if (viewScope != null) return
        val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
        viewScope = scope

        scope.launch {
            ThemeStore.shared(context).themeState.collectLatest {
                applyThemeColors(it.currentTheme.tokens.color)
            }
        }

        scope.launch {
            combine(vm.nickname, vm.avatar, vm.friendRemark, vm.aboutMe) { nickname, avatar, remark, signature ->
                arrayOf(nickname, avatar, remark, signature)
            }.collectLatest { values ->
                val nickname = values[0]
                val avatar = values[1]
                val remark = values[2]
                val signature = values[3]
                val displayName = nickname.ifEmpty { vm.userID }
                nicknameTextView.text = displayName
                idTextView.text = "${context.getString(R.string.chat_setting_user_id)}: ${vm.userID}"
                avatarView.setContent(
                    Avatar.AvatarContent.Image(url = avatar, fallbackName = displayName)
                )
                if (signature.isNotEmpty()) {
                    signatureTextView.visibility = View.VISIBLE
                    signatureTextView.text = context.getString(R.string.chat_setting_signature_prefix) + signature
                } else {
                    signatureTextView.visibility = View.GONE
                }
                remarkRow.setTitle(context.getString(R.string.chat_setting_remark_name))
                remarkRow.setValue(remark)
            }
        }
        scope.launch {
            vm.isNotDisturb.collectLatest { doNotDisturbRow.setChecked(it) }
        }
        scope.launch {
            vm.isPinned.collectLatest { pinRow.setChecked(it) }
        }
        scope.launch {
            vm.chatBackgroundImageUri.collectLatest { imageUri ->
                updateChatBackgroundRow(imageUri)
            }
        }
        scope.launch {
            vm.isInBlacklist.collectLatest { blacklistRow.setChecked(it) }
        }
    }

    private fun updateChatBackgroundRow(imageUri: String?) {
        chatBackgroundRow.setTitle(context.getString(R.string.chat_setting_chat_background))
        chatBackgroundRow.setValue(
            if (imageUri.isNullOrBlank()) {
                context.getString(R.string.chat_setting_chat_background_default)
            } else {
                context.getString(R.string.chat_setting_chat_background_custom)
            }
        )
    }

    private fun showChatBackgroundPicker(viewModel: C2CChatSettingViewModel) {
        ChatBackgroundPickerDialog(
            context = context,
            selectedImageUri = viewModel.chatBackgroundImageUri.value,
            onBackgroundSelected = { imageUri ->
                if (imageUri.isNullOrBlank()) {
                    viewModel.clearChatBackground()
                } else {
                    viewModel.setChatBackground(imageUri)
                }
            }
        ).show()
    }

    private fun cleanupBinding() {
        viewScope?.cancel()
        viewScope = null
    }

    private fun applyThemeColors(colors: ColorTokens) {
        setBackgroundColor(colors.bgColorInput)
        scrollView.setBackgroundColor(colors.bgColorInput)
        contentLayout.setBackgroundColor(colors.bgColorInput)
        userInfoLayout.setBackgroundColor(colors.bgColorOperate)
        nicknameTextView.setTextColor(colors.textColorPrimary)
        idTextView.setTextColor(colors.textColorTertiary)
        signatureTextView.setTextColor(colors.textColorTertiary)
        if (::itemRenderer.isInitialized) {
            itemRenderer.applyThemeColors(colors)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (viewModel == null) return
        bindViewModel()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        cleanupBinding()
    }
}
