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
import io.trtc.tuikit.chat.uikit.components.chatsetting.config.ChatSettingActionConfig
import io.trtc.tuikit.chat.uikit.components.chatsetting.config.ChatSettingActionContext
import io.trtc.tuikit.chat.uikit.components.chatsetting.config.ChatSettingActionStyle
import io.trtc.tuikit.chat.uikit.components.chatsetting.config.ChatSettingCustomAction
import io.trtc.tuikit.chat.uikit.components.chatsetting.config.ChatSettingScene
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

    private lateinit var sendMessageButton: SettingRowButton
    private lateinit var voiceCallButton: SettingRowButton
    private lateinit var videoCallButton: SettingRowButton
    private lateinit var clearHistoryButton: SettingRowButton
    private lateinit var deleteFriendButton: SettingRowButton

    private val spacers = mutableListOf<View>()
    private val dividers = mutableListOf<View>()

    private var currentUserID: String? = null
    private var isUiBuilt = false

    fun setup(
        userID: String,
        onSendMessageClick: (() -> Unit)? = null,
        onVoiceCallClick: (() -> Unit)? = null,
        onVideoCallClick: (() -> Unit)? = null,
        onContactDeleted: (() -> Unit)? = null
    ) {
        this.onSendMessageClick = onSendMessageClick
        this.onVoiceCallClick = onVoiceCallClick
        this.onVideoCallClick = onVideoCallClick
        this.onContactDeleted = onContactDeleted

        val owner = context.findViewModelStoreOwner() ?: return

        cleanupBinding()
        currentUserID = userID
        val viewModelKey = "${C2CChatSettingViewModel::class.java.name}:$userID"
        viewModel = ViewModelProvider(owner, C2CChatSettingViewModelFactory(userID, context))
            .get(viewModelKey, C2CChatSettingViewModel::class.java)

        if (!isUiBuilt) {
            buildUI()
            isUiBuilt = true
        }

        if (isAttachedToWindow) {
            bindViewModel()
        }
    }

    private fun buildUI() {
        layoutDirection = LAYOUT_DIRECTION_LOCALE
        removeAllViews()
        spacers.clear()
        dividers.clear()
        val dm = resources.displayMetrics
        val colors = ThemeStore.shared(context).themeState.value.currentTheme.tokens.color

        scrollView = ScrollView(context).apply {
            layoutDirection = LAYOUT_DIRECTION_LOCALE
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            setBackgroundColor(colors.bgColorTopBar)
        }

        contentLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = LAYOUT_DIRECTION_LOCALE
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
            setBackgroundColor(colors.bgColorTopBar)
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
        contentLayout.addView(userInfoLayout)

        addSpacer(contentLayout)

        remarkRow = SettingRowNavigate(context).apply {
            setShowArrow(true)
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
        contentLayout.addView(remarkRow)

        addSpacer(contentLayout)

        doNotDisturbRow = SettingRowToggle(context).apply {
            setTitle(context.getString(R.string.chat_setting_do_not_disturb))
            onToggleChanged = { checked -> viewModel?.setDoNotDisturb(checked) }
        }
        contentLayout.addView(doNotDisturbRow)

        addDivider(contentLayout)

        pinRow = SettingRowToggle(context).apply {
            setTitle(context.getString(R.string.chat_setting_pin))
            onToggleChanged = { checked -> viewModel?.setPinChat(checked) }
        }
        contentLayout.addView(pinRow)

        addSpacer(contentLayout)

        chatBackgroundRow = SettingRowNavigate(context).apply {
            setTitle(context.getString(R.string.chat_setting_chat_background))
            setShowArrow(true)
            setOnClickListener {
                viewModel?.let { vm -> showChatBackgroundPicker(vm) }
            }
        }
        contentLayout.addView(chatBackgroundRow)

        addSpacer(contentLayout)

        blacklistRow = SettingRowToggle(context).apply {
            setTitle(context.getString(R.string.chat_setting_add_blacklist))
            onToggleChanged = { viewModel?.toggleBlacklist() }
        }
        contentLayout.addView(blacklistRow)

        addSpacer(contentLayout)

        val actionRows = C2CChatSettingActionPolicy.actions().map { action ->
            when (action) {
                C2CChatSettingAction.SEND_MESSAGE -> createSendMessageButton().also { sendMessageButton = it }
                C2CChatSettingAction.VOICE_CALL -> createVoiceCallButton().also { voiceCallButton = it }
                C2CChatSettingAction.VIDEO_CALL -> createVideoCallButton().also { videoCallButton = it }
                C2CChatSettingAction.CLEAR_HISTORY -> createClearHistoryButton().also { clearHistoryButton = it }
                C2CChatSettingAction.DELETE_FRIEND -> createDeleteFriendButton().also { deleteFriendButton = it }
            }
        }
        actionRows.forEachIndexed { index, row ->
            contentLayout.addView(row)
            if (index != actionRows.lastIndex) {
                addDivider(contentLayout)
            }
        }

        appendCustomActions(contentLayout, actionRows.isNotEmpty())

        scrollView.addView(contentLayout)
        addView(scrollView)
    }

    private fun appendCustomActions(parent: LinearLayout, hasPrecedingActions: Boolean) {
        val provider = ChatSettingActionConfig.customActionProvider ?: return
        val actions = provider.getActions(
            ChatSettingActionContext(
                context = context,
                scene = ChatSettingScene.C2C,
                userID = currentUserID,
                groupID = null
            )
        )
        actions.forEachIndexed { index, action ->
            if (hasPrecedingActions || index > 0) {
                addDivider(parent)
            }
            parent.addView(createCustomActionRow(action))
        }
    }

    private fun createCustomActionRow(action: ChatSettingCustomAction): SettingRowButton {
        return SettingRowButton(context).apply {
            setTitle(action.title)
            setButtonStyle(
                when (action.style) {
                    ChatSettingActionStyle.LINK -> SettingRowButton.Style.LINK
                    ChatSettingActionStyle.DANGER -> SettingRowButton.Style.DANGER
                    ChatSettingActionStyle.NORMAL -> SettingRowButton.Style.NORMAL
                }
            )
            setOnClickListener { action.onClick(context) }
        }
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

    private fun addSpacer(parent: LinearLayout) {
        val spacer = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp2px(10f, resources.displayMetrics).toInt()
            )
        }
        spacers.add(spacer)
        parent.addView(spacer)
    }

    private fun addDivider(parent: LinearLayout) {
        val divider = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp2px(0.5f, resources.displayMetrics).toInt()
            )
            val colors = ThemeStore.shared(context).themeState.value.currentTheme.tokens.color
            setBackgroundColor(colors.strokeColorPrimary)
        }
        dividers.add(divider)
        parent.addView(divider)
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
                remarkRow.setValue(remark.ifEmpty { displayName })
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
        setBackgroundColor(colors.bgColorTopBar)
        scrollView.setBackgroundColor(colors.bgColorTopBar)
        contentLayout.setBackgroundColor(colors.bgColorTopBar)
        userInfoLayout.setBackgroundColor(colors.bgColorOperate)
        nicknameTextView.setTextColor(colors.textColorPrimary)
        idTextView.setTextColor(colors.textColorTertiary)
        signatureTextView.setTextColor(colors.textColorTertiary)
        spacers.forEach { it.setBackgroundColor(colors.bgColorTopBar) }
        dividers.forEach { it.setBackgroundColor(colors.strokeColorPrimary) }
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
