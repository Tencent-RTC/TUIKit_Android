package io.trtc.tuikit.chat.uikit.components.chatsetting.ui
import android.app.Dialog
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.trtc.tuikit.chat.uikit.R
import io.trtc.tuikit.chat.uikit.components.chatsetting.permission.GroupMemberActionPolicy
import io.trtc.tuikit.chat.uikit.components.common.findViewModelStoreOwner
import io.trtc.tuikit.chat.uikit.components.chatsetting.viewmodel.GroupMemberListViewModel
import io.trtc.tuikit.chat.uikit.components.chatsetting.viewmodel.GroupMemberListViewModelFactory
import io.trtc.tuikit.chat.uikit.components.common.displayName
import io.trtc.tuikit.atomicx.common.util.ScreenUtil.dp2px
import io.trtc.tuikit.chat.uikit.components.common.WindowThemeUtil
import io.trtc.tuikit.atomicx.theme.ThemeStore
import io.trtc.tuikit.chat.uikit.components.widgets.ActionItem
import io.trtc.tuikit.chat.uikit.components.widgets.ActionSheet
import io.trtc.tuikit.atomicx.theme.tokens.ColorTokens
import io.trtc.tuikit.chat.uikit.components.widgets.Avatar
import io.trtc.tuikit.chat.uikit.components.widgets.DialogNavBar
import io.trtc.tuikit.atomicxcore.api.group.GroupMember
import io.trtc.tuikit.atomicxcore.api.group.GroupMemberRole
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class GroupMemberListView(
    private val context: android.content.Context,
    private val groupID: String,
    private val currentUserRole: GroupMemberRole = GroupMemberRole.MEMBER,
    private val title: String? = null,
    private val isSelectionMode: Boolean = false,
    private val isSingleSelect: Boolean = false,
    private val preSelectedMembers: List<String> = emptyList(),
    private val onDismiss: () -> Unit = {},
    private val onConfirm: (List<GroupMember>) -> Unit = {},
    private val onMemberClick: (GroupMember) -> Unit = {},
    private val onSetAsAdmin: (GroupMember) -> Unit = {},
    private val onRemoveAdmin: (GroupMember) -> Unit = {},
    private val onRemoveMember: (GroupMember) -> Unit = {},
    private val onViewInfo: (GroupMember) -> Unit = {}
) {

    private var dialog: Dialog? = null
    private var viewModel: GroupMemberListViewModel? = null
    private var viewScope: CoroutineScope? = null
    private var adapter: MemberAdapter? = null
    private lateinit var navBar: DialogNavBar

    private var currentMembers: List<GroupMember> = emptyList()
    private val selectedMemberIds = linkedSetOf<String>()

    fun show() {
        val owner = context.findViewModelStoreOwner() ?: return
        viewModel = ViewModelProvider(owner, GroupMemberListViewModelFactory(groupID))
            .get("GroupMemberList_$groupID", GroupMemberListViewModel::class.java)

        selectedMemberIds.clear()
        selectedMemberIds.addAll(preSelectedMembers)

        val colors = ThemeStore.shared(context).themeState.value.currentTheme.tokens.color
        val dm = context.resources.displayMetrics

        dialog = Dialog(context, android.R.style.Theme_NoTitleBar).apply {
            window?.apply {
                setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
                addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
                WindowThemeUtil.applyDialogSystemBarStyle(this, colors)
            }
        }

        val rootLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_LOCALE
            setBackgroundColor(colors.bgColorOperate)
            fitsSystemWindows = true
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        navBar = DialogNavBar.create(
            context,
            DialogNavBar.Config(
                mode = DialogNavBar.Mode.BackTitle,
                title = title ?: context.getString(R.string.chat_setting_group_members),
                colors = colors,
                onLeadingClick = { dismiss() },
                onConfirmClick = {
                    val selectedMembers = currentMembers.filter { selectedMemberIds.contains(it.userID) }
                    if (selectedMembers.isNotEmpty()) {
                        onConfirm(selectedMembers)
                        dismiss()
                    }
                },
                showConfirm = isSelectionMode,
                leadingContentDescription = context.getString(R.string.chat_setting_back)
            )
        )
        rootLayout.addView(navBar)

        rootLayout.addView(
            View(context).apply {
                setBackgroundColor(colors.strokeColorSecondary)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp2px(0.5f, dm).toInt().coerceAtLeast(1)
                )
            }
        )

        val recyclerView = RecyclerView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
            layoutManager = LinearLayoutManager(context)
        }

        adapter = MemberAdapter(
            context = context,
            colors = colors,
            isSelectionMode = isSelectionMode,
            selectedMemberIds = selectedMemberIds
        ) { member ->
            handleMemberClick(member, colors)
        }

        recyclerView.adapter = adapter
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                val layoutManager = recyclerView.layoutManager as? LinearLayoutManager ?: return
                val totalItemCount = layoutManager.itemCount
                val lastVisibleItem = layoutManager.findLastVisibleItemPosition()
                if (totalItemCount > 0 && lastVisibleItem >= totalItemCount - 1) {
                    viewModel?.loadMoreMembers()
                }
            }
        })
        rootLayout.addView(recyclerView)

        dialog?.setContentView(rootLayout)
        dialog?.setOnDismissListener {
            viewScope?.cancel()
            viewScope = null
            onDismiss()
        }
        dialog?.show()
        updateConfirmState(colors)

        viewScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
        viewScope?.launch {
            viewModel?.members?.collectLatest { members ->
                currentMembers = members
                adapter?.submitList(members)
                updateConfirmState(colors)
            }
        }
    }

    fun dismiss() {
        dialog?.dismiss()
    }

    private fun handleMemberClick(member: GroupMember, colors: ColorTokens) {
        if (isSelectionMode) {
            toggleSelection(member)
            updateConfirmState(colors)
            return
        }
        if (GroupMemberActionPolicy.hasActionPermission(currentUserRole, member.role)) {
            showMemberActionSheet(member)
        } else {
            onMemberClick(member)
        }
    }

    private fun toggleSelection(member: GroupMember) {
        if (isSingleSelect) {
            if (selectedMemberIds.contains(member.userID)) {
                selectedMemberIds.clear()
            } else {
                selectedMemberIds.clear()
                selectedMemberIds.add(member.userID)
            }
        } else if (selectedMemberIds.contains(member.userID)) {
            selectedMemberIds.remove(member.userID)
        } else {
            selectedMemberIds.add(member.userID)
        }
        adapter?.notifyDataSetChanged()
    }

    private fun updateConfirmState(colors: ColorTokens) {
        if (!isSelectionMode) return
        navBar.setConfirmEnabled(selectedMemberIds.isNotEmpty(), colors)
    }

    private fun showMemberActionSheet(member: GroupMember) {
        val options = mutableListOf<ActionItem>()
        options.add(ActionItem(text = context.getString(R.string.chat_setting_member_info), value = "info"))
        if (GroupMemberActionPolicy.canRemoveMember(currentUserRole, member.role)) {
            options.add(ActionItem(text = context.getString(R.string.chat_setting_delete_member), value = "delete"))
        }
        if (GroupMemberActionPolicy.canSetAdmin(currentUserRole, member.role)) {
            options.add(ActionItem(text = context.getString(R.string.chat_setting_set_admin), value = "setAdmin"))
        }
        if (GroupMemberActionPolicy.canRemoveAdmin(currentUserRole, member.role)) {
            options.add(ActionItem(text = context.getString(R.string.chat_setting_remove_admin), value = "removeAdmin"))
        }

        ActionSheet.show(
            context = context,
            options = options,
            onActionSelected = { item ->
                when (item.value) {
                    "info" -> onViewInfo(member)
                    "delete" -> onRemoveMember(member)
                    "setAdmin" -> onSetAsAdmin(member)
                    "removeAdmin" -> onRemoveAdmin(member)
                }
            }
        )
    }

    private class MemberAdapter(
        private val context: android.content.Context,
        private val colors: ColorTokens,
        private val isSelectionMode: Boolean,
        private val selectedMemberIds: Set<String>,
        private val onItemClick: (GroupMember) -> Unit
    ) : RecyclerView.Adapter<MemberAdapter.MemberViewHolder>() {

        private var members: List<GroupMember> = emptyList()

        fun submitList(newMembers: List<GroupMember>) {
            members = newMembers
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MemberViewHolder {
            val dm = context.resources.displayMetrics
            val itemLayout = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutDirection = View.LAYOUT_DIRECTION_LOCALE
                setBackgroundColor(colors.bgColorOperate)
                setPadding(
                    dp2px(16f, dm).toInt(),
                    dp2px(12f, dm).toInt(),
                    dp2px(16f, dm).toInt(),
                    dp2px(12f, dm).toInt()
                )
                layoutParams = RecyclerView.LayoutParams(
                    RecyclerView.LayoutParams.MATCH_PARENT,
                    RecyclerView.LayoutParams.WRAP_CONTENT
                )
            }

            val avatar = Avatar(context).apply {
                val size = dp2px(40f, dm).toInt()
                layoutParams = LinearLayout.LayoutParams(size, size)
            }
            itemLayout.addView(avatar)

            val textContainer = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutDirection = View.LAYOUT_DIRECTION_LOCALE
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                ).apply {
                    marginStart = dp2px(12f, dm).toInt()
                }
            }
            itemLayout.addView(textContainer)

            val nameText = TextView(context).apply {
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                setTextColor(colors.textColorPrimary)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                textAlignment = View.TEXT_ALIGNMENT_VIEW_START
                textDirection = View.TEXT_DIRECTION_LOCALE
            }
            textContainer.addView(
                nameText,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            )

            val roleTag = TextView(context).apply {
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
                setTextColor(colors.textColorLink)
                setPadding(
                    dp2px(8f, dm).toInt(),
                    dp2px(2f, dm).toInt(),
                    dp2px(8f, dm).toInt(),
                    dp2px(2f, dm).toInt()
                )
                background = GradientDrawable().apply {
                    setColor(this@MemberAdapter.colors.buttonColorPrimaryDisabled)
                    cornerRadius = dp2px(3f, dm)
                }
                visibility = View.GONE
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    marginStart = dp2px(8f, dm).toInt()
                }
            }
            textContainer.addView(roleTag)

            val checkBox = CheckBox(context).apply {
                visibility = if (isSelectionMode) View.VISIBLE else View.GONE
                isClickable = false
                isFocusable = false
                buttonTintList = android.content.res.ColorStateList.valueOf(colors.textColorLink)
            }
            itemLayout.addView(checkBox)

            return MemberViewHolder(itemLayout, avatar, nameText, roleTag, checkBox)
        }

        override fun onBindViewHolder(holder: MemberViewHolder, position: Int) {
            val member = members[position]
            val displayName = member.displayName
            holder.nameText.text = displayName

            holder.avatar.setContent(
                Avatar.AvatarContent.Image(url = member.avatarURL, fallbackName = displayName)
            )

            when (member.role) {
                GroupMemberRole.OWNER -> {
                    holder.roleTag.text = context.getString(R.string.chat_setting_member_type_owner)
                    holder.roleTag.visibility = View.VISIBLE
                }

                GroupMemberRole.ADMIN -> {
                    holder.roleTag.text = context.getString(R.string.chat_setting_member_type_administrator)
                    holder.roleTag.visibility = View.VISIBLE
                }

                else -> holder.roleTag.visibility = View.GONE
            }

            holder.checkBox.visibility = if (isSelectionMode) View.VISIBLE else View.GONE
            holder.checkBox.isChecked = selectedMemberIds.contains(member.userID)
            holder.itemView.setOnClickListener { onItemClick(member) }
        }

        override fun getItemCount(): Int = members.size

        class MemberViewHolder(
            itemView: View,
            val avatar: Avatar,
            val nameText: TextView,
            val roleTag: TextView,
            val checkBox: CheckBox
        ) : RecyclerView.ViewHolder(itemView)
    }
}
