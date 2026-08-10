package io.trtc.tuikit.chat.uikit.components.messageinput.ui
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentActivity
import io.trtc.tuikit.chat.uikit.R
import io.trtc.tuikit.chat.uikit.components.common.displayName
import io.trtc.tuikit.chat.uikit.components.common.expandTouchTarget
import io.trtc.tuikit.atomicx.common.util.ScreenUtil.dp2px
import io.trtc.tuikit.chat.uikit.components.common.WindowThemeUtil
import io.trtc.tuikit.chat.uikit.components.messageinput.model.MentionInfo
import io.trtc.tuikit.atomicx.theme.ThemeStore
import io.trtc.tuikit.atomicx.theme.tokens.ColorTokens
import io.trtc.tuikit.chat.uikit.components.userpicker.adapter.SelectionCheckBoxView
import io.trtc.tuikit.chat.uikit.components.userpicker.model.UserPickerData
import io.trtc.tuikit.chat.uikit.components.userpicker.ui.UserPickerView
import io.trtc.tuikit.chat.uikit.components.widgets.Avatar
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.group.GroupMember
import io.trtc.tuikit.atomicxcore.api.group.GroupMemberFilterRole
import io.trtc.tuikit.atomicxcore.api.group.GroupMemberStore

private fun GroupMember.toUserPickerData(): UserPickerData<GroupMember> {
    return UserPickerData(
        key = userID,
        label = displayName,
        avatarUrl = avatarURL,
        extraData = this
    )
}

class MentionMemberDialogFragment : DialogFragment() {

    private var onConfirm: ((List<MentionInfo>) -> Unit)? = null
    private var onDismissCallback: (() -> Unit)? = null

    private lateinit var rootLayout: LinearLayout
    private lateinit var cancelButton: TextView
    private lateinit var titleText: TextView
    private lateinit var confirmButton: TextView
    private lateinit var atAllRow: LinearLayout
    private lateinit var atAllCheckbox: SelectionCheckBoxView
    private lateinit var userPickerView: UserPickerView

    private var groupMemberStore: GroupMemberStore? = null
    private val groupMembers = mutableListOf<GroupMember>()
    private var isLoadingMore = false
    private var atAll = false
    private val selectedMembers = mutableListOf<GroupMember>()

    private val groupID: String
        get() = arguments?.getString(ARG_GROUP_ID).orEmpty()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_FRAME, android.R.style.Theme_NoTitleBar)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        groupMemberStore = GroupMemberStore.create(groupID)
        buildLayout()
        return rootLayout
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupListeners()
        loadGroupMembers()
        applyTheme()
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.apply {
            setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT
            )
            addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN
                    or WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
            )
            WindowThemeUtil.applyDialogSystemBarStyle(this, getColors())
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        onDismissCallback?.invoke()
    }


    private fun buildLayout() {
        val ctx = requireContext()
        val dm = ctx.resources.displayMetrics
        val colors = getColors()

        rootLayout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_LOCALE
            setBackgroundColor(colors.bgColorInput)
            fitsSystemWindows = true
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val headerBar = FrameLayout(ctx).apply {
            layoutDirection = View.LAYOUT_DIRECTION_LOCALE
            setBackgroundColor(colors.bgColorOperate)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp2px(56f, dm).toInt()
            )
            setPadding(dp2px(10f, dm).toInt(), 0, dp2px(10f, dm).toInt(), 0)
        }

        cancelButton = TextView(ctx).apply {
            text = ctx.getString(R.string.uikit_cancel)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTextColor(colors.textColorLink)
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
                Gravity.START or Gravity.CENTER_VERTICAL
            )
            isClickable = true
            isFocusable = true
        }
        headerBar.addView(cancelButton)
        cancelButton.expandTouchTarget()

        titleText = TextView(ctx).apply {
            text = ctx.getString(R.string.message_input_mention_select_member)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTextColor(colors.textColorPrimary)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
                Gravity.CENTER
            )
        }
        headerBar.addView(titleText)

        confirmButton = TextView(ctx).apply {
            text = ctx.getString(R.string.uikit_confirm)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
                Gravity.END or Gravity.CENTER_VERTICAL
            )
            isClickable = true
            isFocusable = true
        }
        headerBar.addView(confirmButton)
        confirmButton.expandTouchTarget()

        rootLayout.addView(headerBar)

        val divider = View(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp2px(0.5f, dm).toInt()
            )
        }
        rootLayout.addView(divider)

        atAllRow = buildAtAllRow(dm)
        rootLayout.addView(atAllRow)

        userPickerView = UserPickerView(ctx)
        rootLayout.addView(
            userPickerView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )
    }

    private fun buildAtAllRow(dm: android.util.DisplayMetrics): LinearLayout {
        val ctx = requireContext()
        val colors = getColors()
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_LOCALE
            setBackgroundColor(colors.bgColorOperate)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp2px(56f, dm).toInt()
            )
            setPadding(dp2px(16f, dm).toInt(), 0, dp2px(16f, dm).toInt(), 0)
        }

        val atAllText = ctx.getString(R.string.message_input_mention_all)

        val checkboxSize = dp2px(16f, dm).toInt()
        atAllCheckbox = SelectionCheckBoxView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(checkboxSize, checkboxSize)
        }
        row.addView(atAllCheckbox)

        val checkboxSpacer = View(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(dp2px(10f, dm).toInt(), 1)
        }
        row.addView(checkboxSpacer)

        val avatar = Avatar(ctx).apply {
            setContent(Avatar.AvatarContent.Image(null, atAllText))
            setSize(Avatar.AvatarSize.M)
        }
        row.addView(
            avatar,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        val spacer = View(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(dp2px(13f, dm).toInt(), 1)
        }
        row.addView(spacer)

        val label = TextView(ctx).apply {
            text = "@$atAllText"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            textAlignment = View.TEXT_ALIGNMENT_VIEW_START
            textDirection = View.TEXT_DIRECTION_LOCALE
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        row.addView(label)

        val endSpacer = View(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(dp2px(26f, dm).toInt(), 1)
        }
        row.addView(endSpacer)

        return row
    }


    private fun setupListeners() {
        cancelButton.setOnClickListener {
            dismissAllowingStateLoss()
        }

        confirmButton.setOnClickListener {
            val ctx = context ?: return@setOnClickListener
            val mentionInfos = buildList {
                if (atAll) {
                    add(
                        MentionInfo(
                            userID = MentionInfo.AT_ALL_USER_ID,
                            displayName = ctx.getString(R.string.message_input_mention_all)
                        )
                    )
                }
                addAll(selectedMembers.map {
                    MentionInfo(
                        userID = it.userID,
                        displayName = it.displayName
                    )
                })
            }
            onConfirm?.invoke(mentionInfos)
            dismissAllowingStateLoss()
        }

        atAllRow.setOnClickListener {
            atAll = !atAll
            updateAtAllCheckbox()
        }

        userPickerView.setOnSelectedChangedListener<GroupMember> { selected ->
            selectedMembers.clear()
            selectedMembers.addAll(selected.map { it.extraData })
        }

        userPickerView.setOnReachEndListener {
            if (!isLoadingMore) {
                isLoadingMore = true
                groupMemberStore?.loadMoreMembers(object : CompletionHandler {
                    override fun onSuccess() {
                        groupMembers.clear()
                        groupMembers.addAll(
                            groupMemberStore?.state?.memberList?.value.orEmpty()
                        )
                        updateUserPickerData()
                        isLoadingMore = false
                    }

                    override fun onFailure(code: Int, desc: String) {
                        isLoadingMore = false
                    }
                })
            }
        }
    }


    private fun loadGroupMembers() {
        groupMemberStore?.loadMembers(
            roleList = listOf(GroupMemberFilterRole.ALL),
            completion = object : CompletionHandler {
                override fun onSuccess() {
                    groupMembers.clear()
                    groupMembers.addAll(
                        groupMemberStore?.state?.memberList?.value.orEmpty()
                    )
                    updateUserPickerData()
                }

                override fun onFailure(code: Int, desc: String) {}
            }
        )
    }

    private fun updateUserPickerData() {
        val dataSource = groupMembers.map { it.toUserPickerData() }
        userPickerView.setDataSource(dataSource)
    }


    private fun updateAtAllCheckbox() {
        if (!::atAllCheckbox.isInitialized) return
        atAllCheckbox.setCheckedState(atAll, locked = false, colors = getColors())
    }

    private fun applyTheme() {
        val colors = getColors()
        rootLayout.setBackgroundColor(colors.bgColorInput)

        val headerBar = rootLayout.getChildAt(0)
        (headerBar as? FrameLayout)?.setBackgroundColor(colors.bgColorOperate)

        atAllRow.setBackgroundColor(colors.bgColorOperate)

        cancelButton.setTextColor(colors.textColorLink)
        titleText.setTextColor(colors.textColorPrimary)
        confirmButton.setTextColor(colors.textColorLink)

        val label = (atAllRow.getChildAt(4) as? TextView)
        label?.setTextColor(colors.textColorPrimary)

        updateAtAllCheckbox()
    }

    private fun getColors(): ColorTokens {
        val ctx = context ?: throw IllegalStateException("Fragment not attached")
        return ThemeStore.shared(ctx).themeState.value.currentTheme.tokens.color
    }

    companion object {

        private const val ARG_GROUP_ID = "arg_group_id"
        private const val TAG = "MentionMemberDialogFragment"

        fun show(
            activity: FragmentActivity,
            groupID: String,
            onConfirm: (List<MentionInfo>) -> Unit,
            onDismiss: () -> Unit = {}
        ) {
            val existing = activity.supportFragmentManager.findFragmentByTag(TAG)
            if (existing != null) return

            val fragment = MentionMemberDialogFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_GROUP_ID, groupID)
                }
                this.onConfirm = onConfirm
                this.onDismissCallback = onDismiss
            }
            fragment.show(activity.supportFragmentManager, TAG)
        }
    }
}
