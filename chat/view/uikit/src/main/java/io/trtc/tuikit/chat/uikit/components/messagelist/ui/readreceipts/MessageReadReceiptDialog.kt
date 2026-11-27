package io.trtc.tuikit.chat.uikit.components.messagelist.ui.readreceipts
import android.app.Dialog
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.trtc.tuikit.chat.uikit.R
import io.trtc.tuikit.chat.uikit.components.common.WindowThemeUtil
import io.trtc.tuikit.atomicx.theme.ThemeStore
import io.trtc.tuikit.chat.uikit.components.widgets.Avatar
import io.trtc.tuikit.chat.uikit.components.widgets.DialogNavBar
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.group.GroupMember
import io.trtc.tuikit.atomicxcore.api.message.MessageActionStore
import io.trtc.tuikit.atomicxcore.api.message.MessageInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class MessageReadReceiptDialog(
    context: Context,
    private val message: MessageInfo,
    private val onUserClick: (String) -> Unit = {}
) : Dialog(context, android.R.style.Theme_NoTitleBar) {

    private val density = context.resources.displayMetrics.density
    private val themeStore = ThemeStore.shared(context)
    private val messageActionStore = MessageActionStore.create(message)
    private val dialogScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private lateinit var readTabButton: TextView
    private lateinit var unreadTabButton: TextView
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: MemberAdapter

    private var readMembers: List<GroupMember> = emptyList()
    private var unreadMembers: List<GroupMember> = emptyList()
    private var hasMoreReadMembers: Boolean = false
    private var hasMoreUnreadMembers: Boolean = false
    private var isLoadingMoreRead: Boolean = false
    private var isLoadingMoreUnread: Boolean = false
    private var selectedReadTab = (message.readReceiptInfo?.readCount ?: 0) > 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(
            buildContentView(),
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        val colors = themeStore.themeState.value.currentTheme.tokens.color
        window?.apply {
            setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
            addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            WindowThemeUtil.applyDialogSystemBarStyle(this, colors)
        }
        collectState()
        loadInitialData()
        refreshTabState()
    }

    override fun dismiss() {
        dialogScope.cancel()
        super.dismiss()
    }

    private fun buildContentView(): LinearLayout {
        val colors = themeStore.themeState.value.currentTheme.tokens.color
        adapter = MemberAdapter(onUserClick)
        recyclerView = RecyclerView(context).apply {
            layoutManager = LinearLayoutManager(context)
            adapter = this@MessageReadReceiptDialog.adapter
            overScrollMode = RecyclerView.OVER_SCROLL_NEVER
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                    if (dy <= 0) return
                    maybeLoadMore()
                }
            })
        }
        readTabButton = createTabButton(context.getString(R.string.message_list_read_receipt_read_by)) {
            selectedReadTab = true
            refreshTabState()
        }
        unreadTabButton = createTabButton(context.getString(R.string.message_list_read_receipt_delivered_to)) {
            selectedReadTab = false
            refreshTabState()
        }

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_LOCALE
            setBackgroundColor(colors.bgColorOperate)
            fitsSystemWindows = true
        }

        root.addView(
            DialogNavBar.create(
                context,
                DialogNavBar.Config(
                    mode = DialogNavBar.Mode.BackTitle,
                    title = context.getString(R.string.message_list_read_receipt_detail),
                    colors = colors,
                    onLeadingClick = { dismiss() }
                )
            ),
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                56.dp
            )
        )

        root.addView(View(context).apply {
            setBackgroundColor(colors.strokeColorSecondary)
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            1
        ))

        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20.dp, 16.dp, 20.dp, 16.dp)
        }
        content.addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            addView(readTabButton, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = 6.dp
            })
            addView(unreadTabButton, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = 6.dp
            })
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        content.addView(recyclerView, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            0,
            1f
        ).apply {
            topMargin = 12.dp
        })

        root.addView(content, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            0,
            1f
        ))

        return root
    }

    private fun createTabButton(
        text: String,
        onClick: () -> Unit
    ): TextView {
        return TextView(context).apply {
            this.text = text
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = true
            isAllCaps = false
            setPadding(12.dp, 8.dp, 12.dp, 8.dp)
            setOnClickListener { onClick() }
        }
    }

    private fun collectState() {
        dialogScope.launch {
            messageActionStore.state.readMemberList.collectLatest { members ->
                readMembers = members.filter { it.userID.isNotBlank() }.distinctBy { it.userID }
                refreshTabState()
            }
        }
        dialogScope.launch {
            messageActionStore.state.unreadMemberList.collectLatest { members ->
                unreadMembers = members.filter { it.userID.isNotBlank() }.distinctBy { it.userID }
                refreshTabState()
            }
        }
        dialogScope.launch {
            messageActionStore.state.hasMoreReadMembers.collectLatest { value ->
                hasMoreReadMembers = value
            }
        }
        dialogScope.launch {
            messageActionStore.state.hasMoreUnreadMembers.collectLatest { value ->
                hasMoreUnreadMembers = value
            }
        }
    }

    private fun loadInitialData() {
        messageActionStore.loadReadMembers(PAGE_SIZE, object : CompletionHandler {
            override fun onSuccess() {}
            override fun onFailure(code: Int, desc: String) {}
        })
        messageActionStore.loadUnreadMembers(PAGE_SIZE, object : CompletionHandler {
            override fun onSuccess() {}
            override fun onFailure(code: Int, desc: String) {}
        })
    }

    private fun maybeLoadMore() {
        if (!::recyclerView.isInitialized) return
        val layoutManager = recyclerView.layoutManager as? LinearLayoutManager ?: return
        val totalItemCount = layoutManager.itemCount
        if (totalItemCount <= 0) return
        val lastVisible = layoutManager.findLastVisibleItemPosition()
        if (lastVisible < totalItemCount - LOAD_MORE_THRESHOLD) return

        if (selectedReadTab) {
            if (hasMoreReadMembers && !isLoadingMoreRead) {
                isLoadingMoreRead = true
                messageActionStore.loadMoreMembers(true, object : CompletionHandler {
                    override fun onSuccess() {
                        isLoadingMoreRead = false
                    }

                    override fun onFailure(code: Int, desc: String) {
                        isLoadingMoreRead = false
                    }
                })
            }
        } else {
            if (hasMoreUnreadMembers && !isLoadingMoreUnread) {
                isLoadingMoreUnread = true
                messageActionStore.loadMoreMembers(false, object : CompletionHandler {
                    override fun onSuccess() {
                        isLoadingMoreUnread = false
                    }

                    override fun onFailure(code: Int, desc: String) {
                        isLoadingMoreUnread = false
                    }
                })
            }
        }
    }

    private fun refreshTabState() {
        if (!::readTabButton.isInitialized || !::unreadTabButton.isInitialized) {
            return
        }
        val colors = themeStore.themeState.value.currentTheme.tokens.color
        val selectedBackground = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 20.dp.toFloat()
            setColor(colors.tabColorSelected)
        }
        val normalBackground = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 20.dp.toFloat()
            setColor(colors.tabColorUnselected)
        }
        readTabButton.background = if (selectedReadTab) selectedBackground else normalBackground
        unreadTabButton.background = if (selectedReadTab) normalBackground else selectedBackground
        readTabButton.text = context.getString(
            R.string.message_list_read_receipt_read_by,
        ) + " (${readMembers.size})"
        unreadTabButton.text = context.getString(
            R.string.message_list_read_receipt_delivered_to,
        ) + " (${unreadMembers.size})"
        readTabButton.setTextColor(if (selectedReadTab) colors.textColorPrimary else colors.textColorSecondary)
        unreadTabButton.setTextColor(if (selectedReadTab) colors.textColorSecondary else colors.textColorPrimary)
        adapter.submitList(if (selectedReadTab) readMembers else unreadMembers)
        recyclerView.post { maybeLoadMore() }
    }

    private inner class MemberAdapter(
        private val onUserClick: (String) -> Unit
    ) : RecyclerView.Adapter<MemberViewHolder>() {

        private var items: List<GroupMember> = emptyList()

        fun submitList(data: List<GroupMember>) {
            items = data
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MemberViewHolder {
            val row = LinearLayout(parent.context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(4.dp, 8.dp, 4.dp, 8.dp)
            }
            val avatarView = Avatar(parent.context).apply {
                setSize(Avatar.AvatarSize.S)
            }
            val nameView = TextView(parent.context).apply {
                textSize = 14f
            }
            row.addView(avatarView, LinearLayout.LayoutParams(36.dp, 36.dp))
            row.addView(nameView, LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            ).apply {
                marginStart = 12.dp
            })
            return MemberViewHolder(row, avatarView, nameView)
        }

        override fun onBindViewHolder(holder: MemberViewHolder, position: Int) {
            val member = items[position]
            val colors = themeStore.themeState.value.currentTheme.tokens.color
            val displayName = member.nameCard
                ?.takeIf { it.isNotBlank() }
                ?: member.nickname?.takeIf { it.isNotBlank() }
                ?: member.userID
            holder.nameView.text = displayName
            holder.nameView.setTextColor(colors.textColorPrimary)
            holder.avatarView.setContent(
                Avatar.AvatarContent.Image(
                    url = member.avatarURL.orEmpty(),
                    fallbackName = displayName
                )
            )
            holder.itemView.setOnClickListener {
                onUserClick(member.userID)
            }
        }

        override fun getItemCount(): Int = items.size
    }

    private class MemberViewHolder(
        itemView: View,
        val avatarView: Avatar,
        val nameView: TextView
    ) : RecyclerView.ViewHolder(itemView)

    private val Int.dp: Int
        get() = (this * density).roundToInt()

    companion object {
        private const val PAGE_SIZE = 20
        private const val LOAD_MORE_THRESHOLD = 5
    }
}
