package io.trtc.tuikit.chat.uikit.components.chatsetting.ui
import android.app.Dialog
import android.content.Context
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.trtc.tuikit.chat.uikit.R
import io.trtc.tuikit.chat.uikit.components.chatsetting.permission.GroupPermission
import io.trtc.tuikit.chat.uikit.components.chatsetting.permission.GroupPermissionManager
import io.trtc.tuikit.chat.uikit.components.chatsetting.viewmodel.GroupChatSettingViewModel
import io.trtc.tuikit.chat.uikit.components.common.displayName
import io.trtc.tuikit.atomicx.common.util.ScreenUtil.dp2px
import io.trtc.tuikit.chat.uikit.components.common.WindowThemeUtil
import io.trtc.tuikit.atomicx.theme.ThemeStore
import io.trtc.tuikit.chat.uikit.components.widgets.ActionItem
import io.trtc.tuikit.chat.uikit.components.widgets.ActionSheet
import io.trtc.tuikit.atomicx.theme.tokens.ColorTokens
import android.graphics.drawable.GradientDrawable
import android.widget.HorizontalScrollView
import io.trtc.tuikit.chat.uikit.components.widgets.Avatar
import io.trtc.tuikit.chat.uikit.components.widgets.DialogNavBar
import io.trtc.tuikit.chat.uikit.components.widgets.Switch
import io.trtc.tuikit.chat.uikit.components.widgets.SwitchSize
import io.trtc.tuikit.atomicx.widget.basicwidget.toast.AtomicToast
import io.trtc.tuikit.atomicxcore.api.group.GroupMember
import io.trtc.tuikit.atomicxcore.api.group.GroupMemberRole
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class GroupManagementView(
    private val context: Context,
    private val groupID: String,
    private val viewModel: GroupChatSettingViewModel,
    private val onDismiss: () -> Unit = {}
) {

    private var dialog: Dialog? = null
    private var viewScope: CoroutineScope? = null
    private var muteAllSwitch: Switch? = null
    private var silencedAdapter: SilencedMemberAdapter? = null
    private var addMuteSection: LinearLayout? = null
    private var silencedRecyclerView: RecyclerView? = null
    private var adminPreviewRow: LinearLayout? = null
    private var adminSection: LinearLayout? = null
    private var adminSectionSpacer: View? = null
    private var canManageAdmins: Boolean = false

    fun show() {
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
            setBackgroundColor(colors.bgColorTopBar)
            fitsSystemWindows = true
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        rootLayout.addView(buildTopBar(colors, dm))
        adminSectionSpacer = buildSpacer(6f, colors.bgColorTopBar, dm)
        rootLayout.addView(adminSectionSpacer)
        adminSection = buildAdminSection(colors, dm)
        rootLayout.addView(adminSection)
        addSpacer(rootLayout, 6f, colors.bgColorTopBar, dm)
        rootLayout.addView(buildMuteAllSection(colors, dm))
        rootLayout.addView(buildMuteExplanation(colors, dm))

        addMuteSection = buildAddMuteSection(colors, dm)
        rootLayout.addView(addMuteSection)

        silencedRecyclerView = RecyclerView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
            layoutManager = LinearLayoutManager(context)
        }

        silencedAdapter = SilencedMemberAdapter(context, colors) { member ->
            showUnmuteActionSheet(member)
        }
        silencedRecyclerView?.adapter = silencedAdapter
        rootLayout.addView(silencedRecyclerView)

        dialog?.setContentView(rootLayout)
        dialog?.setOnDismissListener {
            viewScope?.cancel()
            viewScope = null
            onDismiss()
        }
        dialog?.show()
        viewModel.loadAllGroupMembers()

        viewScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
        viewScope?.launch {
            viewModel.isAllMuted.collectLatest { isMuted ->
                muteAllSwitch?.setChecked(isMuted, animate = false)
                updateMuteSectionVisibility(isMuted)
            }
        }
        viewScope?.launch {
            viewModel.silencedMembers.collectLatest { members ->
                silencedAdapter?.submitList(members)
            }
        }
        viewScope?.launch {
            combine(viewModel.groupType, viewModel.selfRole) { type, role ->
                GroupPermissionManager.canPerformAction(
                    type, role, GroupPermission.SET_GROUP_MEMBER_ROLE
                )
            }.collectLatest { allowed ->
                canManageAdmins = allowed
                updateAdminSectionVisibility(allowed)
                renderAdminPreviewItems(viewModel.adminMembers.value, colors)
            }
        }
        viewScope?.launch {
            viewModel.adminMembers.collectLatest { admins ->
                renderAdminPreviewItems(admins, colors)
            }
        }
    }

    private fun updateAdminSectionVisibility(visible: Boolean) {
        val visibility = if (visible) View.VISIBLE else View.GONE
        adminSection?.visibility = visibility
        adminSectionSpacer?.visibility = visibility
    }

    fun dismiss() {
        dialog?.dismiss()
    }

    private fun updateMuteSectionVisibility(isAllMuted: Boolean) {
        val visibility = if (isAllMuted) View.GONE else View.VISIBLE
        addMuteSection?.visibility = visibility
        silencedRecyclerView?.visibility = visibility
    }

    private fun buildTopBar(colors: ColorTokens, dm: android.util.DisplayMetrics): LinearLayout {
        val wrapper = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        wrapper.addView(
            DialogNavBar.create(
                context,
                DialogNavBar.Config(
                    mode = DialogNavBar.Mode.BackTitle,
                    title = context.getString(R.string.chat_setting_group_management),
                    colors = colors,
                    onLeadingClick = { dismiss() },
                    leadingContentDescription = context.getString(R.string.uikit_back)
                )
            )
        )

        wrapper.addView(
            View(context).apply {
                setBackgroundColor(colors.strokeColorSecondary)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp2px(0.5f, dm).toInt().coerceAtLeast(1)
                )
            }
        )

        return wrapper
    }

    private fun buildAdminSection(colors: ColorTokens, dm: android.util.DisplayMetrics): LinearLayout {
        val sectionLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_LOCALE
            setBackgroundColor(colors.bgColorOperate)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        sectionLayout.addView(
            TextView(context).apply {
                text = context.getString(R.string.chat_setting_group_admins)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                setTextColor(colors.textColorSecondary)
                setPadding(
                    dp2px(16f, dm).toInt(),
                    dp2px(16f, dm).toInt(),
                    dp2px(16f, dm).toInt(),
                    dp2px(8f, dm).toInt()
                )
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }
        )

        val scrollView = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            layoutDirection = View.LAYOUT_DIRECTION_LOCALE
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        adminPreviewRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_LOCALE
            val horizontalPadding = dp2px(16f, dm).toInt()
            val verticalPadding = dp2px(12f, dm).toInt()
            setPadding(horizontalPadding, verticalPadding, horizontalPadding, verticalPadding)
        }
        scrollView.addView(
            adminPreviewRow,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )
        sectionLayout.addView(scrollView)

        return sectionLayout
    }

    private fun renderAdminPreviewItems(admins: List<GroupMember>, colors: ColorTokens) {
        val dm = context.resources.displayMetrics
        adminPreviewRow?.removeAllViews()

        admins.forEach { member ->
            adminPreviewRow?.addView(createAdminMemberItem(member, colors, dm))
        }

        if (!canManageAdmins) return

        adminPreviewRow?.addView(createAdminActionItem(isAdd = true, colors, dm))

        if (admins.isNotEmpty()) {
            adminPreviewRow?.addView(createAdminActionItem(isAdd = false, colors, dm))
        }
    }

    private fun createAdminMemberItem(
        member: GroupMember,
        colors: ColorTokens,
        dm: android.util.DisplayMetrics
    ): View {
        val itemWidth = dp2px(56f, dm).toInt()
        val avatarSize = dp2px(40f, dm).toInt()
        val itemSpacing = dp2px(8f, dm).toInt()
        val displayName = member.displayName

        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_LOCALE
            layoutParams = LinearLayout.LayoutParams(itemWidth, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                marginEnd = itemSpacing
            }
            if (canManageAdmins) {
                isClickable = true
                isFocusable = true
                setOnClickListener { showRemoveAdminActionSheet(member) }
            }

            addView(
                Avatar(context).apply {
                    layoutParams = LinearLayout.LayoutParams(avatarSize, avatarSize)
                    setContent(
                        Avatar.AvatarContent.Image(
                            url = member.avatarURL,
                            fallbackName = displayName
                        )
                    )
                }
            )

            addView(
                TextView(context).apply {
                    text = displayName
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                    setTextColor(colors.textColorPrimary)
                    gravity = Gravity.CENTER
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        topMargin = dp2px(6f, dm).toInt()
                    }
                }
            )
        }
    }

    private fun createAdminActionItem(
        isAdd: Boolean,
        colors: ColorTokens,
        dm: android.util.DisplayMetrics
    ): View {
        val itemWidth = dp2px(56f, dm).toInt()
        val itemSpacing = dp2px(8f, dm).toInt()
        val iconSize = dp2px(40f, dm).toInt()
        val iconCornerRadius = dp2px(8f, dm)
        val symbol = if (isAdd) "+" else "−"

        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_LOCALE
            layoutParams = LinearLayout.LayoutParams(itemWidth, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                marginEnd = itemSpacing
            }
            isClickable = true
            isFocusable = true
            setOnClickListener {
                if (isAdd) {
                    showAdminMemberSelector()
                } else {
                    showAdminRemoveSelector()
                }
            }

            addView(
                FrameLayout(context).apply {
                    layoutParams = LinearLayout.LayoutParams(iconSize, iconSize)
                    background = GradientDrawable().apply {
                        setColor(colors.bgColorInput)
                        cornerRadius = iconCornerRadius
                    }
                    addView(
                        TextView(context).apply {
                            text = symbol
                            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
                            setTextColor(colors.textColorSecondary)
                            gravity = Gravity.CENTER
                            layoutParams = FrameLayout.LayoutParams(
                                FrameLayout.LayoutParams.MATCH_PARENT,
                                FrameLayout.LayoutParams.MATCH_PARENT
                            )
                        }
                    )
                }
            )

            addView(
                View(context).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dp2px(20f, dm).toInt()
                    )
                }
            )
        }
    }

    private fun showAdminMemberSelector() {
        viewModel.loadAllGroupMembers {
            val candidates = viewModel.memberList.value
                .filter { it.role == GroupMemberRole.MEMBER }
            GroupMemberPickerDialog(
                context = context,
                title = context.getString(R.string.chat_setting_select_admin_member),
                candidates = candidates,
                onConfirm = { members ->
                    members.forEach { member ->
                        viewModel.setMemberRole(
                            member.userID,
                            GroupMemberRole.ADMIN,
                            onFailure = { _, desc -> showAdminOperationFailed(desc) }
                        )
                    }
                }
            ).show()
        }
    }

    private fun showAdminRemoveSelector() {
        viewModel.loadAllGroupMembers {
            val candidates = viewModel.adminMembers.value
            GroupMemberPickerDialog(
                context = context,
                title = context.getString(R.string.chat_setting_remove_admin),
                candidates = candidates,
                onConfirm = { members ->
                    members.forEach { member ->
                        viewModel.setMemberRole(
                            member.userID,
                            GroupMemberRole.MEMBER,
                            onFailure = { _, desc -> showAdminOperationFailed(desc) }
                        )
                    }
                }
            ).show()
        }
    }

    private fun showRemoveAdminActionSheet(member: GroupMember) {
        ActionSheet.show(
            context = context,
            options = listOf(
                ActionItem(
                    text = context.getString(R.string.chat_setting_remove_admin),
                    value = "removeAdmin"
                )
            ),
            onActionSelected = {
                viewModel.setMemberRole(
                    member.userID,
                    GroupMemberRole.MEMBER,
                    onFailure = { _, desc -> showAdminOperationFailed(desc) }
                )
            }
        )
    }

    private fun showAdminOperationFailed(desc: String) {
        AtomicToast.show(
            context,
            context.getString(R.string.chat_setting_admin_operation_failed, desc),
            style = AtomicToast.Style.ERROR
        )
    }

    private fun showMuteOperationFailed(desc: String) {
        AtomicToast.show(
            context,
            context.getString(R.string.chat_setting_mute_operation_failed, desc),
            style = AtomicToast.Style.ERROR
        )
    }

    private fun buildMuteAllSection(colors: ColorTokens, dm: android.util.DisplayMetrics): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_LOCALE
            setBackgroundColor(colors.bgColorOperate)
            setPadding(
                dp2px(16f, dm).toInt(),
                dp2px(14f, dm).toInt(),
                dp2px(16f, dm).toInt(),
                dp2px(14f, dm).toInt()
            )
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            addView(
                TextView(context).apply {
                    text = context.getString(R.string.chat_setting_mute_all)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                    setTextColor(colors.textColorPrimary)
                    layoutParams = LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1f
                    )
                }
            )
            muteAllSwitch = Switch(context).apply {
                setSize(SwitchSize.L)
                setOnCheckedChangeListener {
                    viewModel.toggleGroupAllMute()
                }
            }
            addView(muteAllSwitch)
        }
    }

    private fun buildMuteExplanation(colors: ColorTokens, dm: android.util.DisplayMetrics): TextView {
        return TextView(context).apply {
            text = context.getString(R.string.chat_setting_mute_all_tips)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(colors.textColorPrimary)
            setPadding(
                dp2px(16f, dm).toInt(),
                dp2px(16f, dm).toInt(),
                dp2px(16f, dm).toInt(),
                dp2px(16f, dm).toInt()
            )
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
    }

    private fun buildAddMuteSection(colors: ColorTokens, dm: android.util.DisplayMetrics): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_LOCALE
            setBackgroundColor(colors.bgColorOperate)
            setPadding(
                dp2px(16f, dm).toInt(),
                dp2px(16f, dm).toInt(),
                dp2px(16f, dm).toInt(),
                dp2px(16f, dm).toInt()
            )
            isClickable = true
            isFocusable = true
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            addView(
                TextView(context).apply {
                    text = "+"
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
                    setTextColor(colors.textColorLink)
                }
            )
            addView(
                TextView(context).apply {
                    text = context.getString(R.string.chat_setting_select_muted_member)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                    setTextColor(colors.textColorLink)
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        marginStart = dp2px(8f, dm).toInt()
                    }
                }
            )
            setOnClickListener { showMemberSelector() }
        }
    }

    private fun showMemberSelector() {
        viewModel.loadAllGroupMembers {
            val candidates = viewModel.memberList.value
                .filter { it.role != GroupMemberRole.OWNER && !it.isMuted }
            GroupMemberPickerDialog(
                context = context,
                title = context.getString(R.string.chat_setting_select_muted_member),
                candidates = candidates,
                onConfirm = { members ->
                    members.forEach { member ->
                        viewModel.muteGroupMember(
                            member.userID,
                            7 * 24 * 60 * 60,
                            onFailure = { _, desc -> showMuteOperationFailed(desc) }
                        )
                    }
                }
            ).show()
        }
    }

    private fun showUnmuteActionSheet(member: GroupMember) {
        ActionSheet.show(
            context = context,
            options = listOf(
                ActionItem(text = context.getString(R.string.chat_setting_cancel_mute), value = "unmute")
            ),
            onActionSelected = {
                viewModel.muteGroupMember(
                    member.userID,
                    0,
                    onFailure = { _, desc -> showMuteOperationFailed(desc) }
                )
            }
        )
    }

    private fun addSpacer(
        parent: LinearLayout,
        heightDp: Float,
        color: Int,
        dm: android.util.DisplayMetrics
    ) {
        parent.addView(buildSpacer(heightDp, color, dm))
    }

    private fun buildSpacer(
        heightDp: Float,
        color: Int,
        dm: android.util.DisplayMetrics
    ): View {
        return View(context).apply {
            setBackgroundColor(color)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp2px(heightDp, dm).toInt()
            )
        }
    }

    private class SilencedMemberAdapter(
        private val context: Context,
        private val colors: ColorTokens,
        private val onLongClick: (GroupMember) -> Unit
    ) : RecyclerView.Adapter<SilencedMemberAdapter.ViewHolder>() {

        private var members: List<GroupMember> = emptyList()

        fun submitList(newMembers: List<GroupMember>) {
            members = newMembers
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val dm = context.resources.displayMetrics
            val itemLayout = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutDirection = View.LAYOUT_DIRECTION_LOCALE
                setBackgroundColor(colors.bgColorOperate)
                setPadding(
                    dp2px(16f, dm).toInt(),
                    dp2px(8f, dm).toInt(),
                    dp2px(16f, dm).toInt(),
                    dp2px(8f, dm).toInt()
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

            val nameText = TextView(context).apply {
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                setTextColor(colors.textColorPrimary)
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                textAlignment = View.TEXT_ALIGNMENT_VIEW_START
                textDirection = View.TEXT_DIRECTION_LOCALE
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                ).apply {
                    marginStart = dp2px(13f, dm).toInt()
                }
            }
            itemLayout.addView(nameText)

            return ViewHolder(itemLayout, avatar, nameText)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val member = members[position]
            val displayName = member.displayName
            holder.nameText.text = displayName
            val avatarUrl = member.avatarURL
            if (!avatarUrl.isNullOrEmpty()) {
                holder.avatar.setContent(
                    Avatar.AvatarContent.Image(url = member.avatarURL, fallbackName = displayName)
                )
            } else {
                holder.avatar.setContent(Avatar.AvatarContent.Text(displayName))
            }
            holder.itemView.setOnLongClickListener {
                onLongClick(member)
                true
            }
        }

        override fun getItemCount(): Int = members.size

        class ViewHolder(
            itemView: View,
            val avatar: Avatar,
            val nameText: TextView
        ) : RecyclerView.ViewHolder(itemView)
    }
}
