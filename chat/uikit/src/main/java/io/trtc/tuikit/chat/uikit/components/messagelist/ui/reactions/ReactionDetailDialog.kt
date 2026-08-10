package io.trtc.tuikit.chat.uikit.components.messagelist.ui.reactions
import android.app.Dialog
import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import io.trtc.tuikit.chat.uikit.R
import io.trtc.tuikit.chat.uikit.components.emojipicker.EmojiManager
import io.trtc.tuikit.atomicx.theme.ThemeStore
import io.trtc.tuikit.chat.uikit.components.widgets.Avatar
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.login.LoginStore
import io.trtc.tuikit.atomicxcore.api.login.UserProfile
import io.trtc.tuikit.atomicxcore.api.message.MessageActionStore
import io.trtc.tuikit.atomicxcore.api.message.MessageInfo
import io.trtc.tuikit.atomicxcore.api.message.MessageReaction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private const val REACTION_USERS_PAGE_SIZE = 20
private const val REACTION_LIST_PREFETCH_THRESHOLD = 5

internal object ReactionDetailFetchResultPolicy {
    fun usersForFetchCompletion(
        selectedReactionId: String?,
        completedReactionId: String,
        users: List<UserProfile>
    ): List<UserProfile>? {
        if (selectedReactionId != completedReactionId) {
            return null
        }
        return users
    }
}

class ReactionDetailDialog(
    context: Context,
    private val message: MessageInfo,
    private val onReactionRemoved: () -> Unit = {}
) : Dialog(context, io.trtc.tuikit.atomicx.R.style.dialogStyleFromBottom) {

    private val density = context.resources.displayMetrics.density
    private val themeStore = ThemeStore.shared(context)
    private val messageActionStore = MessageActionStore.create(message)
    private val currentUserId: String? = LoginStore.shared.loginState.loginUserInfo.value?.userID

    private lateinit var reactionTabContainer: LinearLayout
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: ReactionUserAdapter
    private var selectedReactionId: String? = null
    private var isFetchingMore: Boolean = false
    private var dialogScope: CoroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var collectUsersJob: Job? = null
    private var collectHasMoreJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val halfScreenHeight = context.resources.displayMetrics.heightPixels / 2
        setContentView(buildContentView())
        window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setGravity(Gravity.BOTTOM)
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, halfScreenHeight)
        }
        observeReactionUsers()
        selectFirstReaction()
    }

    override fun dismiss() {
        dialogScope.cancel()
        super.dismiss()
    }

    private fun buildContentView(): View {
        val colors = themeStore.themeState.value.currentTheme.tokens.color
        adapter = ReactionUserAdapter { user ->
            val reactionId = selectedReactionId ?: return@ReactionUserAdapter
            val reaction = message.reactionList.firstOrNull { it.reactionID == reactionId }
            val isSelf = reaction?.reactedByMyself == true && user.userID == currentUserId
            if (!isSelf) {
                return@ReactionUserAdapter
            }
            messageActionStore.removeReaction(reactionId, object : CompletionHandler {
                override fun onSuccess() {
                    onReactionRemoved()
                    dismiss()
                }

                override fun onFailure(code: Int, desc: String) {}
            })
        }
        reactionTabContainer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        recyclerView = RecyclerView(context).apply {
            layoutManager = LinearLayoutManager(context)
            adapter = this@ReactionDetailDialog.adapter
            overScrollMode = RecyclerView.OVER_SCROLL_NEVER
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                    if (dy <= 0) return
                    maybeLoadMore()
                }
            })
        }
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadii = floatArrayOf(
                    20.dp.toFloat(), 20.dp.toFloat(),
                    20.dp.toFloat(), 20.dp.toFloat(),
                    0f, 0f,
                    0f, 0f
                )
                setColor(colors.bgColorDialog)
            }
            setPadding(16.dp, 12.dp, 16.dp, 24.dp)
            addView(View(context).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = 2.dp.toFloat()
                    setColor(colors.strokeColorPrimary)
                }
            }, LinearLayout.LayoutParams(36.dp, 4.dp).apply {
                gravity = Gravity.CENTER_HORIZONTAL
            })
            addView(TextView(context).apply {
                text = context.getString(R.string.message_list_reaction_detail_title)
                textSize = 16f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setTextColor(colors.textColorPrimary)
            }, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 12.dp
            })
            addView(reactionTabContainer, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 12.dp
            })
            addView(recyclerView, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            ).apply {
                topMargin = 12.dp
            })
        }
    }

    private fun selectFirstReaction() {
        val firstReactionId = message.reactionList.firstOrNull()?.reactionID
        selectedReactionId = firstReactionId
        rebuildTabs()
        if (firstReactionId != null) {
            fetchFirstPage(firstReactionId)
        } else {
            adapter.submitList(emptyList(), null)
        }
    }

    private fun fetchFirstPage(reactionId: String) {
        isFetchingMore = false
        messageActionStore.loadReactionUsers(
            reactionId,
            REACTION_USERS_PAGE_SIZE,
            object : CompletionHandler {
                override fun onSuccess() {
                    val users = ReactionDetailFetchResultPolicy.usersForFetchCompletion(
                        selectedReactionId = selectedReactionId,
                        completedReactionId = reactionId,
                        users = messageActionStore.state.reactionUserList.value
                    ) ?: return
                    val reaction = message.reactionList.firstOrNull { it.reactionID == reactionId }
                    adapter.submitList(sortUsers(users, reaction), reaction)
                }

                override fun onFailure(code: Int, desc: String) {}
            }
        )
    }

    private fun maybeLoadMore() {
        if (isFetchingMore) return
        if (!messageActionStore.state.hasMoreReactionUsers.value) return
        val layoutManager = recyclerView.layoutManager as? LinearLayoutManager ?: return
        val totalItemCount = layoutManager.itemCount
        val lastVisible = layoutManager.findLastVisibleItemPosition()
        if (totalItemCount <= 0 || lastVisible < 0) return
        if (lastVisible >= totalItemCount - REACTION_LIST_PREFETCH_THRESHOLD) {
            isFetchingMore = true
            messageActionStore.loadMoreReactionUsers(object : CompletionHandler {
                override fun onSuccess() {
                    isFetchingMore = false
                }

                override fun onFailure(code: Int, desc: String) {
                    isFetchingMore = false
                }
            })
        }
    }

    private fun observeReactionUsers() {
        collectUsersJob?.cancel()
        collectUsersJob = dialogScope.launch {
            messageActionStore.state.reactionUserList.collect { users ->
                val reaction = message.reactionList.firstOrNull { it.reactionID == selectedReactionId }
                adapter.submitList(sortUsers(users, reaction), reaction)
            }
        }
        collectHasMoreJob?.cancel()
        collectHasMoreJob = dialogScope.launch {
            messageActionStore.state.hasMoreReactionUsers.collect {
                maybeLoadMore()
            }
        }
    }

    private fun sortUsers(users: List<UserProfile>, reaction: MessageReaction?): List<UserProfile> {
        if (reaction?.reactedByMyself != true || currentUserId == null) {
            return users
        }
        val mutable = users.toMutableList()
        val selfIndex = mutable.indexOfFirst { it.userID == currentUserId }
        if (selfIndex > 0) {
            val self = mutable.removeAt(selfIndex)
            mutable.add(0, self)
        }
        return mutable
    }

    private fun rebuildTabs() {
        reactionTabContainer.removeAllViews()
        message.reactionList.forEachIndexed { index, reaction ->
            reactionTabContainer.addView(createReactionTab(reaction))
            if (index < message.reactionList.lastIndex) {
                reactionTabContainer.addView(View(context), LinearLayout.LayoutParams(8.dp, 1))
            }
        }
    }

    private fun createReactionTab(reaction: MessageReaction): View {
        val colors = themeStore.themeState.value.currentTheme.tokens.color
        val isSelected = reaction.reactionID == selectedReactionId
        val emoji = EmojiManager.findEmojiByKey(reaction.reactionID)
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 16.dp.toFloat()
                setColor(if (isSelected) colors.buttonColorPrimaryDefault else colors.bgColorInput)
                setStroke(1.dp, if (isSelected) colors.buttonColorPrimaryDefault else colors.bgColorInput)
            }
            setPadding(10.dp, 6.dp, 10.dp, 6.dp)
            if (emoji != null) {
                addView(ImageView(context).apply {
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    val drawable = EmojiManager.getCachedEmojiDrawable(reaction.reactionID)
                    if (drawable != null) {
                        setImageDrawable(drawable)
                    } else {
                        Glide.with(context)
                            .load(emoji.emojiUrl)
                            .into(this)
                    }
                }, LinearLayout.LayoutParams(18.dp, 18.dp))
            }
            addView(TextView(context).apply {
                text = reaction.totalUserCount.toString()
                textSize = 14f
                setTextColor(if (isSelected) colors.textColorButton else colors.textColorSecondary)
            }, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginStart = 4.dp
            })
            setOnClickListener {
                if (selectedReactionId == reaction.reactionID) return@setOnClickListener
                selectedReactionId = reaction.reactionID
                rebuildTabs()
                adapter.submitList(emptyList(), reaction)
                fetchFirstPage(reaction.reactionID)
            }
        }
    }

    private inner class ReactionUserAdapter(
        private val onItemClick: (UserProfile) -> Unit
    ) : RecyclerView.Adapter<ReactionUserViewHolder>() {

        private var items: List<UserProfile> = emptyList()
        private var currentReaction: MessageReaction? = null

        fun submitList(data: List<UserProfile>, reaction: MessageReaction?) {
            items = data
            currentReaction = reaction
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReactionUserViewHolder {
            val colors = themeStore.themeState.value.currentTheme.tokens.color
            val row = LinearLayout(parent.context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, 8.dp, 0, 8.dp)
            }
            val avatarView = Avatar(parent.context).apply {
                setSize(Avatar.AvatarSize.S)
            }
            val textColumn = LinearLayout(parent.context).apply {
                orientation = LinearLayout.VERTICAL
            }
            val nameView = TextView(parent.context).apply {
                textSize = 14f
                setTextColor(colors.textColorPrimary)
            }
            val hintView = TextView(parent.context).apply {
                text = context.getString(R.string.message_list_reaction_tap_to_delete)
                textSize = 12f
                setTextColor(colors.textColorTertiary)
                visibility = View.GONE
            }
            textColumn.addView(nameView, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ))
            textColumn.addView(hintView, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 2.dp
            })
            row.addView(avatarView, LinearLayout.LayoutParams(36.dp, 36.dp))
            row.addView(textColumn, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginStart = 12.dp
            })
            return ReactionUserViewHolder(row, avatarView, nameView, hintView)
        }

        override fun onBindViewHolder(holder: ReactionUserViewHolder, position: Int) {
            val user = items[position]
            val isSelf = currentReaction?.reactedByMyself == true && user.userID == currentUserId
            val displayName = user.nickname?.takeIf { it.isNotBlank() }
                ?: user.userID?.takeIf { it.isNotBlank() }
                ?: ""
            holder.nameView.text = displayName
            holder.hintView.visibility = if (isSelf) View.VISIBLE else View.GONE
            holder.avatarView.setContent(
                Avatar.AvatarContent.Image(
                    url = user.avatarURL.orEmpty(),
                    fallbackName = displayName
                )
            )
            holder.itemView.isClickable = isSelf
            if (isSelf) {
                holder.itemView.setOnClickListener { onItemClick(user) }
            } else {
                holder.itemView.setOnClickListener(null)
            }
        }

        override fun getItemCount(): Int = items.size
    }

    private class ReactionUserViewHolder(
        itemView: View,
        val avatarView: Avatar,
        val nameView: TextView,
        val hintView: TextView
    ) : RecyclerView.ViewHolder(itemView)

    private val Int.dp: Int
        get() = (this * density).roundToInt()
}
