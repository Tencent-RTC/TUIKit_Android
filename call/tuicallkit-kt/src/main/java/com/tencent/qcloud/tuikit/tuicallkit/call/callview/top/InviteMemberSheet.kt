package com.tencent.qcloud.tuikit.tuicallkit.call.callview.top

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.tencent.qcloud.tuikit.tuicallkit.R
import com.tencent.qcloud.tuikit.tuicallkit.common.CallKitContactLoader
import com.tencent.qcloud.tuikit.tuicallkit.common.CallKitLogger
import com.tencent.qcloud.tuikit.tuicallkit.common.CallKitToast
import com.tencent.qcloud.tuikit.tuicallkit.common.LightSheetHeader
import com.tencent.qcloud.tuikit.tuicallkit.common.dp
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.call.CallParticipantStatus
import io.trtc.tuikit.atomicxcore.api.call.CallStore
import io.trtc.tuikit.atomicxcore.api.contact.ContactStore
import io.trtc.tuikit.atomicxcore.api.group.GroupMemberStore
import io.trtc.tuikit.atomicxcore.api.login.LoginStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

internal class InviteMemberSheet @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr) {

    data class Pick(val userID: String, val displayName: String)

    var onInvite: ((List<Pick>) -> Unit)? = null
    var onClose: (() -> Unit)? = null

    private data class Row(
        val userID: String,
        val displayName: String,
        val avatarURL: String?,
        val showsFriendTag: Boolean,
        val busyStatus: CallParticipantStatus?,
    )

    private enum class Source { Friends, GroupMembers }

    private val header = LightSheetHeader(context)
    private val listView: RecyclerView
    private val emptyLabel: TextView
    private val adapter = InviteAdapter()
    private var rows: List<Row> = emptyList()
    private val picked = mutableSetOf<String>()
    private var isSubmitting = false
    private val source: Source
    private var subscribeJob: Job? = null
    private var memberStore: GroupMemberStore? = null
    private val logger = CallKitLogger.getLogger("InviteMemberSheet")

    init {
        val call = CallStore.shared.observerState.activeCall.value
        source = if (call.chatGroupId.isEmpty()) Source.Friends else Source.GroupMembers
        orientation = VERTICAL
        setBackgroundColor(Color.WHITE)

        header.setTitle(context.getString(R.string.tuicallkit_invite_title))
        header.onAction = { handleActionTap() }
        addView(header, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        listView = RecyclerView(context).apply {
            layoutManager = LinearLayoutManager(context)
            adapter = this@InviteMemberSheet.adapter
            itemAnimator = null
            // 行分割线：除首行外，每行顶部 1dp（对齐 iOS UITableView 分割线/html opt-row 顶线）
            addItemDecoration(object : RecyclerView.ItemDecoration() {
                private val paint = android.graphics.Paint().apply {
                    color = ContextCompat.getColor(context, R.color.tuicallkit_sheet_line)
                }
                private val lineHeight = 1.dp(context)

                override fun onDraw(canvas: android.graphics.Canvas, parent: RecyclerView, state: RecyclerView.State) {
                    for (i in 0 until parent.childCount) {
                        val child = parent.getChildAt(i)
                        if (parent.getChildAdapterPosition(child) <= 0) {
                            continue
                        }
                        canvas.drawRect(
                            child.left.toFloat(), child.top.toFloat(),
                            child.right.toFloat(), (child.top + lineHeight).toFloat(), paint
                        )
                    }
                }
            })
        }
        emptyLabel = TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(ContextCompat.getColor(context, R.color.tuicallkit_sheet_text3))
            gravity = Gravity.CENTER
            isVisible = false
            val padding = 18.dp(context)
            setPadding(0, padding, 0, padding)
        }
        addView(listView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        addView(emptyLabel, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        syncAction()
        loadRows()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        subscribeJob?.cancel()
        subscribeJob = CoroutineScope(Dispatchers.Main + SupervisorJob()).launch {
            launch {
                CallStore.shared.observerState.allParticipants.collect { reloadRows() }
            }
            when (source) {
                Source.Friends -> launch {
                    ContactStore.shared.state.friendList.collect { reloadRows() }
                }
                Source.GroupMembers -> memberStore?.let { store ->
                    launch {
                        store.state.memberList.collect { reloadRows() }
                    }
                }
            }
        }
    }

    override fun onDetachedFromWindow() {
        subscribeJob?.cancel()
        subscribeJob = null
        super.onDetachedFromWindow()
    }

    private fun loadRows() {
        when (source) {
            Source.Friends -> {
                emptyLabel.setText(R.string.tuicallkit_invite_empty_friends)
                ContactStore.shared.loadFriends(object : CompletionHandler {
                    override fun onSuccess() {}
                    override fun onFailure(code: Int, desc: String) {
                        logger.warn("loadFriends failed: code=$code desc=$desc")
                    }
                })
            }
            Source.GroupMembers -> {
                emptyLabel.setText(R.string.tuicallkit_invite_empty_members)
                val groupID = CallStore.shared.observerState.activeCall.value.chatGroupId
                val store = GroupMemberStore.create(groupID)
                memberStore = store
                store.loadMembers(completion = object : CompletionHandler {
                    override fun onSuccess() {}
                    override fun onFailure(code: Int, desc: String) {
                        logger.warn("loadMembers failed: code=$code desc=$desc")
                    }
                })
            }
        }
        reloadRows()
    }

    private fun busyMap(): Map<String, CallParticipantStatus> {
        return CallStore.shared.observerState.allParticipants.value.associate { it.id to it.status }
    }

    private fun reloadRows() {
        val busy = busyMap()
        rows = when (source) {
            Source.Friends -> ContactStore.shared.state.friendList.value.map { info ->
                Row(
                    userID = info.userID,
                    displayName = info.nickname?.takeIf { it.isNotEmpty() } ?: info.userID,
                    avatarURL = info.avatarURL,
                    showsFriendTag = true,
                    busyStatus = busy[info.userID]
                )
            }
            Source.GroupMembers -> {
                val store = memberStore ?: return
                val selfID = LoginStore.shared.loginState.loginUserInfo.value?.userID.orEmpty()
                store.state.memberList.value.filter { it.userID != selfID }.map { member ->
                    val name = listOfNotNull(member.nameCard, member.nickname).firstOrNull { it.isNotEmpty() } ?: member.userID
                    Row(
                        userID = member.userID,
                        displayName = name,
                        avatarURL = member.avatarURL,
                        showsFriendTag = false,
                        busyStatus = busy[member.userID]
                    )
                }
            }
        }
        finishLoad()
    }

    private fun finishLoad() {
        emptyLabel.isVisible = rows.isEmpty()
        listView.isVisible = rows.isNotEmpty()
        val maxHeight = 52.dp(context) * 6
        val contentHeight = 52.dp(context) * rows.size
        (listView.layoutParams as? LayoutParams)?.let {
            it.height = contentHeight.coerceAtMost(maxHeight)
            listView.layoutParams = it
        }
        adapter.submit(rows, picked)
    }

    private fun syncAction() {
        val hasPicks = picked.isNotEmpty()
        header.setActionTitle(
            context.getString(if (hasPicks) R.string.tuicallkit_invite_confirm else R.string.tuicallkit_invite_cancel),
            isCancel = !hasPicks
        )
    }

    private fun handleActionTap() {
        if (isSubmitting) {
            return
        }
        if (picked.isEmpty()) {
            onClose?.invoke()
            return
        }
        isSubmitting = true
        header.setActionEnabled(false)
        val total = CallStore.shared.observerState.allParticipants.value.size
        val available = (9 - total).coerceAtLeast(0)
        val invited = mutableListOf<Pick>()
        var skipped = false
        for (row in rows) {
            if (!picked.contains(row.userID)) {
                continue
            }
            if (invited.size < available) {
                invited.add(Pick(row.userID, row.displayName))
            } else {
                skipped = true
            }
        }
        if (skipped) {
            CallKitToast.show(context.getString(R.string.tuicallkit_invite_limit))
        }
        onInvite?.invoke(invited)
    }

    private fun handleRowTap(row: Row) {
        val status = row.busyStatus
        if (status != null) {
            val key = if (status == CallParticipantStatus.Accept) R.string.tuicallkit_invite_in_call else R.string.tuicallkit_invite_waiting
            CallKitToast.show(context.getString(key, row.displayName))
            return
        }
        if (!picked.remove(row.userID)) {
            picked.add(row.userID)
        }
        adapter.submit(rows, picked)
        syncAction()
    }

    private inner class InviteAdapter : RecyclerView.Adapter<InviteAdapter.RowHolder>() {

        private var data: List<Row> = emptyList()
        private var pickedSet: Set<String> = emptySet()

        fun submit(rows: List<Row>, picked: Set<String>) {
            data = rows
            pickedSet = picked
            notifyDataSetChanged()
        }

        override fun getItemCount(): Int = data.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RowHolder {
            val row = LinearLayout(parent.context).apply {
                orientation = HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = RecyclerView.LayoutParams(LayoutParams.MATCH_PARENT, 52.dp(parent.context))
                setPadding(16.dp(parent.context), 0, 16.dp(parent.context), 0)
            }
            return RowHolder(row)
        }

        override fun onBindViewHolder(holder: RowHolder, position: Int) {
            val row = data[position]
            holder.bind(row, pickedSet.contains(row.userID))
            holder.itemView.setOnClickListener { handleRowTap(row) }
        }

        private inner class RowHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val container = itemView as LinearLayout
            private var avatar: ImageView? = null
            private var nameLabel: TextView? = null
            private var tagLabel: TextView? = null
            private var tagContainer: FrameLayout? = null
            private var checkLabel: TextView? = null

            fun bind(row: Row, isPicked: Boolean) {
                if (avatar == null) {
                    buildRow(container)
                }
                val busy = row.busyStatus != null
                nameLabel?.text = row.displayName
                tagContainer?.isVisible = row.showsFriendTag
                checkLabel?.isVisible = busy || isPicked
                val context = container.context
                if (busy) {
                    nameLabel?.setTextColor(ContextCompat.getColor(context, R.color.tuicallkit_sheet_text3))
                    tagLabel?.setTextColor(ContextCompat.getColor(context, R.color.tuicallkit_sheet_text3))
                    (tagContainer?.background as? GradientDrawable)?.setColor(ContextCompat.getColor(context, R.color.tuicallkit_sheet_tag_background))
                    avatar?.alpha = 0.5f
                    checkLabel?.setTextColor(ContextCompat.getColor(context, R.color.tuicallkit_sheet_text3))
                } else {
                    nameLabel?.setTextColor(ContextCompat.getColor(context, R.color.tuicallkit_sheet_text))
                    tagLabel?.setTextColor(ContextCompat.getColor(context, R.color.tuicallkit_brand))
                    (tagContainer?.background as? GradientDrawable)?.setColor(ContextCompat.getColor(context, R.color.tuicallkit_brand_light))
                    avatar?.alpha = 1f
                    checkLabel?.setTextColor(ContextCompat.getColor(context, R.color.tuicallkit_brand))
                }
                avatar?.tag = row.userID
                val target = avatar ?: return
                // 清理复用行的旧 Glide 请求，避免旧 URL 回调覆盖新行头像
                com.bumptech.glide.Glide.with(target).clear(target)
                target.setImageResource(R.drawable.tuicallkit_default_avatar)
                if (!row.avatarURL.isNullOrEmpty()) {
                    // 优先使用行数据自带的头像 URL（对齐 iOS）
                    com.bumptech.glide.Glide.with(target).load(row.avatarURL).into(target)
                } else {
                    CallKitContactLoader.load(listOf(row.userID)) { _, _, _, bitmap ->
                        if (bitmap != null && target.tag == row.userID) {
                            target.setImageBitmap(bitmap)
                        }
                    }
                }
            }

            private fun buildRow(container: LinearLayout) {
                val context = container.context
                val avatarView = ImageView(context).apply {
                    layoutParams = LinearLayout.LayoutParams(34.dp(context), 34.dp(context))
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    clipToOutline = true
                    outlineProvider = object : android.view.ViewOutlineProvider() {
                        override fun getOutline(view: View, outline: android.graphics.Outline) {
                            outline.setRoundRect(0, 0, view.width, view.height, 17f.dp(context).toFloat())
                        }
                    }
                }
                container.addView(avatarView)
                avatar = avatarView

                val name = TextView(context).apply {
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    // 长昵称截断但不挤压右侧标签/勾选框（预留头像/标签/勾选/边距空间）
                    maxWidth = resources.displayMetrics.widthPixels - 190.dp(context)
                }
                val nameParams = LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
                nameParams.marginStart = 10.dp(context)
                container.addView(name, nameParams)
                nameLabel = name

                val tag = TextView(context).apply {
                    setText(R.string.tuicallkit_invite_friend_tag)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
                }
                val tagBg = FrameLayout(context).apply {
                    background = GradientDrawable().apply {
                        cornerRadius = 8f.dp(context).toFloat()
                        setColor(ContextCompat.getColor(context, R.color.tuicallkit_brand_light))
                    }
                    val padH = 7.dp(context)
                    val padV = 1.dp(context)
                    tag.setPadding(padH, padV, padH, padV)
                    addView(tag)
                }
                val tagParams = LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
                tagParams.marginStart = 8.dp(context)
                container.addView(tagBg, tagParams)
                tagContainer = tagBg
                tagLabel = tag

                val check = TextView(context).apply {
                    text = "✓"
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                }
                val spacer = android.widget.Space(context)
                container.addView(spacer, LinearLayout.LayoutParams(0, 0, 1f))

                val checkParams = LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
                checkParams.marginStart = 8.dp(context)
                container.addView(check, checkParams)
                checkLabel = check
            }
        }
    }
}
