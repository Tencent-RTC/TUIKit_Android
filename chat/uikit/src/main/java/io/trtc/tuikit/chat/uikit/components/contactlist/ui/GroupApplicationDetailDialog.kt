package io.trtc.tuikit.chat.uikit.components.contactlist.ui
import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import io.trtc.tuikit.chat.uikit.R
import io.trtc.tuikit.atomicx.common.util.ScreenUtil.dp2px
import io.trtc.tuikit.chat.uikit.components.common.ChatDateTimeUtils
import io.trtc.tuikit.chat.uikit.components.contactlist.utils.canHandle
import io.trtc.tuikit.chat.uikit.components.contactlist.utils.fromUserDisplayName
import io.trtc.tuikit.chat.uikit.components.contactlist.utils.getStatusText
import io.trtc.tuikit.chat.uikit.components.contactlist.utils.groupDisplayName
import io.trtc.tuikit.chat.uikit.components.contactlist.utils.isJoinRequest
import io.trtc.tuikit.chat.uikit.components.contactlist.utils.toUserDisplayName
import io.trtc.tuikit.atomicx.theme.ThemeStore
import io.trtc.tuikit.atomicx.theme.tokens.ColorTokens
import io.trtc.tuikit.chat.uikit.components.widgets.Avatar
import io.trtc.tuikit.chat.uikit.components.widgets.Avatar.AvatarContent
import io.trtc.tuikit.atomicxcore.api.group.GroupApplicationInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

internal class GroupApplicationDetailDialog(
    context: Context,
    private val application: GroupApplicationInfo,
    private val onAccept: (GroupApplicationInfo, onSuccess: () -> Unit) -> Unit,
    private val onRefuse: (GroupApplicationInfo, onSuccess: () -> Unit) -> Unit
) : ContactSubPageDialog(context) {

    private var scope: CoroutineScope? = null

    private lateinit var avatar: Avatar
    private lateinit var roleText: TextView
    private lateinit var nameText: TextView
    private lateinit var idText: TextView
    private lateinit var groupNameLabelText: TextView
    private lateinit var groupNameValueText: TextView
    private lateinit var validationLabelText: TextView
    private lateinit var validationValueText: TextView
    private lateinit var timeLabelText: TextView
    private lateinit var timeValueText: TextView
    private lateinit var statusLabelText: TextView
    private lateinit var statusValueText: TextView
    private lateinit var agreeBtn: TextView
    private lateinit var refuseBtn: TextView
    private lateinit var actionRow: LinearLayout
    private var buttonCornerRadius: Float = 0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTitle(context.getString(R.string.contact_list_group_application_info))

        val colors = getColors()
        val dm = context.resources.displayMetrics

        val scrollView = ScrollView(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        val contentColumn = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_LOCALE
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setPadding(0, dp2px(20f, dm).toInt(), 0, 0)
        }

        buildUserInfoSection(contentColumn, colors, dm)
        buildDetailSection(contentColumn, colors, dm)
        buildActionSection(contentColumn, colors, dm)

        scrollView.addView(contentColumn)
        contentContainer.addView(scrollView)

        bindData(colors)
    }

    override fun onStart() {
        super.onStart()
        val newScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        scope = newScope

        newScope.launch {
            ThemeStore.shared(context).themeState.collectLatest {
                val colors = it.currentTheme.tokens.color
                refreshNavBarColors(colors)
                refreshDetailColors(colors)
            }
        }
    }

    override fun onStop() {
        super.onStop()
        scope?.cancel()
        scope = null
    }

    private fun buildUserInfoSection(
        parent: LinearLayout,
        colors: ColorTokens,
        dm: android.util.DisplayMetrics
    ) {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_LOCALE
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setPadding(
                dp2px(16f, dm).toInt(),
                dp2px(10f, dm).toInt(),
                dp2px(16f, dm).toInt(),
                dp2px(10f, dm).toInt()
            )
        }

        avatar = Avatar(context).apply {
            val avatarSize = dp2px(64f, dm).toInt()
            layoutParams = LinearLayout.LayoutParams(avatarSize, avatarSize)
            setSize(Avatar.AvatarSize.XL)
        }
        row.addView(avatar)

        row.addView(View(context).apply {
            layoutParams = LinearLayout.LayoutParams(dp2px(18f, dm).toInt(), 1)
        })

        val textColumn = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        roleText = TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(colors.textColorSecondary)
        }
        textColumn.addView(roleText)

        textColumn.addView(View(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp2px(2f, dm).toInt()
            )
        })

        nameText = TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTextColor(colors.textColorPrimary)
            setTypeface(typeface, Typeface.BOLD)
            maxLines = 1
        }
        textColumn.addView(nameText)

        textColumn.addView(View(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp2px(4f, dm).toInt()
            )
        })

        idText = TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(colors.textColorSecondary)
            maxLines = 1
        }
        textColumn.addView(idText)

        row.addView(textColumn)
        parent.addView(row)
    }

    private fun buildDetailSection(
        parent: LinearLayout,
        colors: ColorTokens,
        dm: android.util.DisplayMetrics
    ) {
        val detailColumn = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_LOCALE
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setPadding(
                dp2px(16f, dm).toInt(),
                dp2px(12f, dm).toInt(),
                dp2px(16f, dm).toInt(),
                0
            )
        }

        createInfoRow(
            parent = detailColumn,
            label = context.getString(R.string.contact_list_group_name),
            colors = colors,
            dm = dm
        ).also {
            groupNameLabelText = it.first
            groupNameValueText = it.second
        }
        createInfoRow(
            parent = detailColumn,
            label = context.getString(R.string.contact_list_friend_application_validation_message),
            colors = colors,
            dm = dm
        ).also {
            validationLabelText = it.first
            validationValueText = it.second
        }
        createInfoRow(
            parent = detailColumn,
            label = context.getString(R.string.contact_list_application_time),
            colors = colors,
            dm = dm
        ).also {
            timeLabelText = it.first
            timeValueText = it.second
        }
        createInfoRow(
            parent = detailColumn,
            label = context.getString(R.string.contact_list_handle_status),
            colors = colors,
            dm = dm,
            showDivider = false
        ).also {
            statusLabelText = it.first
            statusValueText = it.second
        }

        parent.addView(detailColumn)
    }

    private fun createInfoRow(
        parent: LinearLayout,
        label: String,
        colors: ColorTokens,
        dm: android.util.DisplayMetrics,
        showDivider: Boolean = true
    ): Pair<TextView, TextView> {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_LOCALE
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setPadding(0, dp2px(12f, dm).toInt(), 0, dp2px(12f, dm).toInt())
        }

        val labelTextView = TextView(context).apply {
            text = label
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTextColor(colors.textColorSecondary)
            layoutParams = LinearLayout.LayoutParams(dp2px(92f, dm).toInt(), LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        row.addView(labelTextView)

        val valueTextView = TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTextColor(colors.textColorPrimary)
            gravity = Gravity.END
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        row.addView(valueTextView)
        parent.addView(row)

        if (showDivider) {
            parent.addView(View(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp2px(0.5f, dm).toInt().coerceAtLeast(1)
                )
                setBackgroundColor(colors.strokeColorSecondary)
            })
        }

        return labelTextView to valueTextView
    }

    private fun buildActionSection(
        parent: LinearLayout,
        colors: ColorTokens,
        dm: android.util.DisplayMetrics
    ) {
        actionRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutDirection = View.LAYOUT_DIRECTION_LOCALE
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setPadding(
                dp2px(16f, dm).toInt(),
                dp2px(40f, dm).toInt(),
                dp2px(16f, dm).toInt(),
                dp2px(20f, dm).toInt()
            )
        }

        val buttonHeight = dp2px(42f, dm).toInt()
        val buttonSpacing = dp2px(20f, dm).toInt()
        buttonCornerRadius = dp2px(10f, dm)

        agreeBtn = TextView(context).apply {
            text = context.getString(R.string.contact_list_agree)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            gravity = Gravity.CENTER
            maxLines = 1
            layoutParams = LinearLayout.LayoutParams(0, buttonHeight, 1f).apply {
                marginEnd = buttonSpacing
            }
            setOnClickListener {
                onAccept(application) {
                    dismiss()
                }
            }
        }
        actionRow.addView(agreeBtn)

        refuseBtn = TextView(context).apply {
            text = context.getString(R.string.contact_list_refuse)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            gravity = Gravity.CENTER
            maxLines = 1
            layoutParams = LinearLayout.LayoutParams(0, buttonHeight, 1f)
            setOnClickListener {
                onRefuse(application) {
                    dismiss()
                }
            }
        }
        actionRow.addView(refuseBtn)

        parent.addView(actionRow)
        refreshButtonColors(colors)
    }

    private fun bindData(colors: ColorTokens) {
        val primaryName = primaryDisplayName()
        val primaryId = primaryUserId().ifBlank { application.applicationID }
        val avatarUrl = if (application.isJoinRequest) application.fromUserAvatarURL else null
        if (!avatarUrl.isNullOrEmpty()) {
            avatar.setContent(AvatarContent.Image(avatarUrl, primaryName))
        } else {
            avatar.setContent(AvatarContent.Text(primaryName))
        }

        roleText.text = context.getString(
            if (application.isJoinRequest) R.string.contact_list_applicant else R.string.contact_list_invitee
        )
        nameText.text = primaryName
        idText.text = context.getString(
            R.string.contact_list_label_value_format,
            context.getString(R.string.contact_list_user_id),
            primaryId
        )
        groupNameValueText.text = application.groupDisplayName
        validationValueText.text = application.requestMsg
            ?.takeIf { it.isNotBlank() }
            ?: context.getString(R.string.contact_list_no_content)
        timeValueText.text = formatAddTime(application.addTime)
        statusValueText.text = application.getStatusText(context)
        actionRow.visibility = if (application.canHandle) View.VISIBLE else View.GONE

        refreshDetailColors(colors)
    }

    private fun refreshDetailColors(colors: ColorTokens) {
        roleText.setTextColor(colors.textColorSecondary)
        nameText.setTextColor(colors.textColorPrimary)
        idText.setTextColor(colors.textColorSecondary)

        val labelViews = listOf(
            groupNameLabelText,
            validationLabelText,
            timeLabelText,
            statusLabelText
        )
        labelViews.forEach { it.setTextColor(colors.textColorSecondary) }

        val valueViews = listOf(
            groupNameValueText,
            validationValueText,
            timeValueText,
            statusValueText
        )
        valueViews.forEach { it.setTextColor(colors.textColorPrimary) }

        refreshButtonColors(colors)
    }

    private fun refreshButtonColors(colors: ColorTokens) {
        val agreeDrawable = GradientDrawable().apply {
            cornerRadius = buttonCornerRadius
            setColor(colors.buttonColorPrimaryDefault)
        }
        agreeBtn.background = agreeDrawable
        agreeBtn.setTextColor(colors.textColorButton)

        val refuseDrawable = GradientDrawable().apply {
            cornerRadius = buttonCornerRadius
            setColor(colors.bgColorInput)
        }
        refuseBtn.background = refuseDrawable
        refuseBtn.setTextColor(colors.textColorError)
    }

    private fun primaryDisplayName(): String {
        return if (application.isJoinRequest) {
            application.fromUserDisplayName
        } else {
            application.toUserDisplayName.ifBlank { application.fromUserDisplayName }
        }
    }

    private fun primaryUserId(): String {
        return if (application.isJoinRequest) {
            application.fromUser ?: ""
        } else {
            application.toUser ?: ""
        }
    }

    private fun formatAddTime(addTime: Number?): String {
        val timestamp = addTime?.toLong() ?: return context.getString(R.string.contact_list_no_content)
        if (timestamp <= 0L) {
            return context.getString(R.string.contact_list_no_content)
        }
        val timeInMillis = if (timestamp > 1_000_000_000_000L) timestamp else timestamp * 1000L
        return ChatDateTimeUtils.formatFullDateTime(timeInMillis)
    }
}
