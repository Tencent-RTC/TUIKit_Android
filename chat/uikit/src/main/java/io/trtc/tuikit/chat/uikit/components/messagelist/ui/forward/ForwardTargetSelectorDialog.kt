package io.trtc.tuikit.chat.uikit.components.messagelist.ui.forward
import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.trtc.tuikit.chat.uikit.R
import io.trtc.tuikit.chat.uikit.components.common.ConversationIDUtil
import io.trtc.tuikit.chat.uikit.components.common.expandTouchTarget
import io.trtc.tuikit.atomicx.common.util.ScreenUtil.dp2px
import io.trtc.tuikit.chat.uikit.components.common.WindowThemeUtil
import io.trtc.tuikit.chat.uikit.components.common.displayName
import io.trtc.tuikit.atomicx.theme.ThemeStore
import io.trtc.tuikit.atomicx.theme.tokens.ColorTokens
import io.trtc.tuikit.chat.uikit.components.widgets.Avatar
import io.trtc.tuikit.chat.uikit.components.widgets.DialogNavBar
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.contact.ContactInfo
import io.trtc.tuikit.atomicxcore.api.contact.ContactStore
import io.trtc.tuikit.atomicxcore.api.conversation.ConversationInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class ForwardTargetSelectorDialog(
    context: Context,
    private val onConfirm: (List<String>) -> Unit
) : Dialog(context, android.R.style.Theme_NoTitleBar) {

    private val density = context.resources.displayMetrics.density
    private val dm = context.resources.displayMetrics
    private val themeStore = ThemeStore.shared(context)
    private val dialogScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val conversationStore = ForwardConversationStore(scope = dialogScope)
    private val contactStore: ContactStore = ContactStore.shared
    private var friendList: List<ContactInfo> = emptyList()

    private val selectedContacts = LinkedHashSet<ContactInfo>()

    private lateinit var rootLayout: LinearLayout
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: ConversationSelectorAdapter
    private lateinit var bottomBar: View
    private lateinit var avatarStrip: LinearLayout
    private lateinit var confirmButton: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)

        val colors = colors()
        window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT
            )
            addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            WindowThemeUtil.applyDialogSystemBarStyle(this, colors)
        }

        setContentView(buildContentView())
        collectState()
        loadFriendList()
    }

    override fun dismiss() {
        dialogScope.cancel()
        super.dismiss()
    }

    private fun buildContentView(): View {
        val colors = colors()
        rootLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_LOCALE
            setBackgroundColor(colors.bgColorOperate)
            fitsSystemWindows = true
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        rootLayout.addView(
            DialogNavBar.create(
                context,
                DialogNavBar.Config(
                    mode = DialogNavBar.Mode.CancelTitleConfirm,
                    title = context.getString(R.string.message_list_select_conversation),
                    colors = colors,
                    onLeadingClick = { dismiss() },
                    showConfirm = false,
                    horizontalPaddingDp = 16f
                )
            )
        )

        rootLayout.addView(
            buildContactEntry(colors),
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp2px(56f, dm).roundToInt()
            )
        )

        rootLayout.addView(
            buildSectionLabel(colors),
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        adapter = ConversationSelectorAdapter(
            onToggle = { conversation -> toggleConversation(conversation) }
        )
        recyclerView = RecyclerView(context).apply {
            layoutManager = LinearLayoutManager(context)
            adapter = this@ForwardTargetSelectorDialog.adapter
            overScrollMode = RecyclerView.OVER_SCROLL_NEVER
            setBackgroundColor(colors.bgColorOperate)
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    val lm = recyclerView.layoutManager as? LinearLayoutManager ?: return
                    val total = lm.itemCount
                    val lastVisible = lm.findLastVisibleItemPosition()
                    if (total > 0 && lastVisible >= total - 3) {
                        conversationStore.loadMoreConversation()
                    }
                }
            })
        }
        rootLayout.addView(
            recyclerView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        bottomBar = buildBottomBar(colors)
        rootLayout.addView(
            bottomBar,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        refreshBottomBar()
        return rootLayout
    }

    private fun buildContactEntry(colors: ColorTokens): View {
        val hPad = dp2px(16f, dm).roundToInt()
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(hPad, 0, hPad, 0)
            setBackgroundColor(colors.bgColorOperate)
            isClickable = true
            isFocusable = true
            setOnClickListener { openContactPicker() }

            val title = TextView(context).apply {
                text = context.getString(R.string.message_list_select_from_contacts)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                setTextColor(colors.textColorPrimary)
            }
            addView(
                title,
                LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                )
            )

            val arrow = ImageView(context).apply {
                setImageResource(R.drawable.uikit_ic_arrow_right)
                imageTintList = android.content.res.ColorStateList.valueOf(colors.textColorSecondary)
            }
            val arrowSize = dp2px(18f, dm).roundToInt()
            addView(
                arrow,
                LinearLayout.LayoutParams(arrowSize, arrowSize)
            )
        }
    }

    private fun buildSectionLabel(colors: ColorTokens): View {
        val hPad = dp2px(16f, dm).roundToInt()
        val vPad = dp2px(8f, dm).roundToInt()
        return TextView(context).apply {
            text = context.getString(R.string.message_list_recent_conversations)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(colors.textColorSecondary)
            setPadding(hPad, vPad, hPad, vPad)
            setBackgroundColor(colors.bgColorInput)
        }
    }

    private fun buildBottomBar(colors: ColorTokens): View {
        val hPad = dp2px(12f, dm).roundToInt()
        val vPad = dp2px(10f, dm).roundToInt()
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(hPad, vPad, hPad, vPad)
            setBackgroundColor(colors.bgColorOperate)
        }

        val scroll = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        avatarStrip = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        scroll.addView(
            avatarStrip,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        )
        container.addView(
            scroll,
            LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        )

        confirmButton = TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            val btnHPad = dp2px(16f, dm).roundToInt()
            val btnVPad = dp2px(8f, dm).roundToInt()
            setPadding(btnHPad, btnVPad, btnHPad, btnVPad)
            isClickable = true
            isFocusable = true
            setOnClickListener {
                val ids = selectedConversationIDs()
                if (ids.isNotEmpty()) {
                    onConfirm(ids)
                    dismiss()
                }
            }
        }
        container.addView(
            confirmButton,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginStart = dp2px(12f, dm).roundToInt()
            }
        )
        confirmButton.expandTouchTarget()
        return container
    }

    private fun openContactPicker() {
        if (friendList.isEmpty()) {
            return
        }
        val preSelectedUserIds = collectPreSelectedUserIds()
        ForwardContactPickerDialog(
            context = context,
            title = context.getString(R.string.message_list_select_from_contacts),
            contacts = friendList,
            maxSelection = 100,
            preSelectedUserIds = preSelectedUserIds,
            allowEmptyConfirm = preSelectedUserIds.isNotEmpty(),
            onConfirm = { returnedContacts ->
                applyContactPickerResult(
                    returnedContacts = returnedContacts,
                    preSelectedUserIds = preSelectedUserIds
                )
            }
        ).show()
    }

    private fun collectPreSelectedUserIds(): List<String> {
        val ids = LinkedHashSet<String>()
        selectedContacts.forEach { ids.add(it.userID) }
        conversationStore.selectedConversations.value.forEach { conversation ->
            ConversationIDUtil.userIdOrNull(conversation.conversationID)?.let { ids.add(it) }
        }
        return ids.toList()
    }

    private fun applyContactPickerResult(
        returnedContacts: List<ContactInfo>,
        preSelectedUserIds: List<String>
    ) {
        val returnedUserIds = returnedContacts.map { it.userID }.toSet()

        val deselectedUserIds = preSelectedUserIds.filter { it !in returnedUserIds }
        deselectedUserIds.forEach { userId ->
            val c2cId = ConversationIDUtil.fromUser(userId)
            conversationStore.selectedConversations.value
                .firstOrNull { it.conversationID == c2cId }
                ?.let { conversationStore.removeSelection(it) }
        }

        selectedContacts.clear()
        returnedContacts.forEach { contact ->
            val c2cId = ConversationIDUtil.fromUser(contact.userID)
            val existsAsConv = conversationStore.selectedConversations.value
                .any { it.conversationID == c2cId }
            if (!existsAsConv) {
                selectedContacts.add(contact)
            }
        }

        refreshBottomBar()
        adapter.notifyDataSetChanged()
    }

    private fun isConversationSelected(conversation: ConversationInfo): Boolean {
        if (conversationStore.isSelected(conversation)) {
            return true
        }
        ConversationIDUtil.userIdOrNull(conversation.conversationID)?.let { userId ->
            return selectedContacts.any { it.userID == userId }
        }
        return false
    }

    private fun toggleConversation(conversation: ConversationInfo) {
        if (conversationStore.isSelected(conversation)) {
            conversationStore.removeSelection(conversation)
            return
        }
        ConversationIDUtil.userIdOrNull(conversation.conversationID)?.let { userId ->
            val matchingContact = selectedContacts.firstOrNull { it.userID == userId }
            if (matchingContact != null) {
                selectedContacts.remove(matchingContact)
                refreshBottomBar()
                adapter.notifyDataSetChanged()
                return
            }
        }
        conversationStore.addSelection(conversation)
    }

    private fun selectedConversationIDs(): List<String> {
        val conversationIds = conversationStore.selectedConversations.value
            .map { it.conversationID }
        val contactIds = selectedContacts.map { ConversationIDUtil.fromUser(it.userID) }
        return (conversationIds + contactIds).distinct()
    }

    private fun collectState() {
        dialogScope.launch {
            conversationStore.conversationList.collectLatest { list ->
                adapter.submitList(list)
            }
        }
        dialogScope.launch {
            conversationStore.selectedConversations.collectLatest {
                adapter.notifyDataSetChanged()
                refreshBottomBar()
            }
        }
        dialogScope.launch {
            themeStore.themeState.collectLatest {
                val colors = it.currentTheme.tokens.color
                rootLayout.setBackgroundColor(colors.bgColorOperate)
                recyclerView.setBackgroundColor(colors.bgColorOperate)
                adapter.notifyDataSetChanged()
                refreshBottomBar()
            }
        }
    }

    private fun loadFriendList() {
        contactStore.loadFriends(object : CompletionHandler {
            override fun onSuccess() {}
            override fun onFailure(code: Int, desc: String) {}
        })
        dialogScope.launch {
            contactStore.state.friendList.collectLatest { list ->
                friendList = list
            }
        }
    }

    private fun refreshBottomBar() {
        if (!::avatarStrip.isInitialized || !::confirmButton.isInitialized) {
            return
        }
        val colors = colors()
        avatarStrip.removeAllViews()

        val conversationItems = conversationStore.selectedConversations.value.map {
            AvatarChipInfo(
                key = "conv_${it.conversationID}",
                avatarUrl = it.avatarURL.orEmpty(),
                fallback = it.title?.takeIf { title -> title.isNotBlank() } ?: it.conversationID
            )
        }
        val contactItems = selectedContacts.map {
            AvatarChipInfo(
                key = "contact_${it.userID}",
                avatarUrl = it.avatarURL.orEmpty(),
                fallback = it.displayName
            )
        }
        val allItems = conversationItems + contactItems

        val avatarSize = dp2px(32f, dm).roundToInt()
        allItems.forEach { info ->
            val avatar = Avatar(context).apply {
                setSize(Avatar.AvatarSize.XS)
                setContent(
                    Avatar.AvatarContent.Image(
                        url = info.avatarUrl,
                        fallbackName = info.fallback
                    )
                )
            }
            avatarStrip.addView(
                avatar,
                LinearLayout.LayoutParams(avatarSize, avatarSize).apply {
                    marginEnd = dp2px(6f, dm).roundToInt()
                }
            )
        }

        val count = allItems.size
        val enabled = count > 0
        confirmButton.text = context.getString(R.string.message_list_forward_button_text, count)
        confirmButton.isEnabled = enabled
        confirmButton.setTextColor(
            if (enabled) colors.textColorButton else colors.textColorButtonDisabled
        )
        confirmButton.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp2px(6f, dm)
            setColor(
                if (enabled) colors.buttonColorPrimaryDefault else colors.buttonColorPrimaryDisabled
            )
        }
    }

    private data class AvatarChipInfo(
        val key: String,
        val avatarUrl: String,
        val fallback: String
    )

    private inner class ConversationSelectorAdapter(
        private val onToggle: (ConversationInfo) -> Unit
    ) : RecyclerView.Adapter<ConversationViewHolder>() {

        private var items: List<ConversationInfo> = emptyList()

        fun submitList(data: List<ConversationInfo>) {
            items = data
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ConversationViewHolder {
            val hPad = dp2px(16f, dm).roundToInt()
            val vPad = dp2px(10f, dm).roundToInt()
            val itemView = LinearLayout(parent.context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(hPad, vPad, hPad, vPad)
            }
            val avatarView = Avatar(parent.context).apply {
                setSize(Avatar.AvatarSize.S)
            }
            val titleView = TextView(parent.context).apply {
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            }
            val checkBox = ImageView(parent.context)
            val checkSize = dp2px(22f, dm).roundToInt()
            val avatarSize = dp2px(40f, dm).roundToInt()

            itemView.addView(
                checkBox,
                LinearLayout.LayoutParams(checkSize, checkSize).apply {
                    marginEnd = dp2px(12f, dm).roundToInt()
                }
            )
            itemView.addView(avatarView, LinearLayout.LayoutParams(avatarSize, avatarSize))
            itemView.addView(
                titleView,
                LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                ).apply {
                    marginStart = dp2px(12f, dm).roundToInt()
                }
            )
            return ConversationViewHolder(itemView, avatarView, titleView, checkBox)
        }

        override fun onBindViewHolder(holder: ConversationViewHolder, position: Int) {
            val conversation = items[position]
            val colors = colors()
            val title = conversation.title?.takeIf { it.isNotBlank() } ?: conversation.conversationID
            holder.titleView.text = title
            holder.titleView.setTextColor(colors.textColorPrimary)
            holder.avatarView.setContent(
                Avatar.AvatarContent.Image(
                    url = conversation.avatarURL.orEmpty(),
                    fallbackName = title
                )
            )
            val isSelected = isConversationSelected(conversation)
            holder.checkBox.setImageResource(
                if (isSelected) {
                    R.drawable.message_list_multi_select_checkbox_checked
                } else {
                    R.drawable.message_list_multi_select_checkbox_unchecked
                }
            )
            holder.itemView.setBackgroundColor(colors.bgColorOperate)
            holder.itemView.setOnClickListener { onToggle(conversation) }
        }

        override fun getItemCount(): Int = items.size
    }

    private class ConversationViewHolder(
        itemView: View,
        val avatarView: Avatar,
        val titleView: TextView,
        val checkBox: ImageView
    ) : RecyclerView.ViewHolder(itemView)

    private fun colors(): ColorTokens {
        return themeStore.themeState.value.currentTheme.tokens.color
    }
}
