package io.trtc.tuikit.chat.uikit.components.chatsetting.ui.groupchatsetting
import android.content.Context
import android.util.TypedValue
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import io.trtc.tuikit.chat.uikit.R
import io.trtc.tuikit.chat.uikit.components.chatsetting.ui.TextInputDialog
import io.trtc.tuikit.atomicx.common.util.ScreenUtil.dp2px
import io.trtc.tuikit.atomicx.theme.ThemeStore
import io.trtc.tuikit.atomicx.theme.tokens.ColorTokens
import io.trtc.tuikit.chat.uikit.components.widgets.Avatar

internal class GroupChatSettingHeaderSection(
    private val context: Context
) {
    val rootView: LinearLayout

    private val avatarView: Avatar
    private val nameView: TextView
    private val idView: TextView

    init {
        val dm = context.resources.displayMetrics
        rootView = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutDirection = android.view.View.LAYOUT_DIRECTION_LOCALE
            gravity = Gravity.CENTER_VERTICAL
            val horizontalPadding = dp2px(16f, dm).toInt()
            val verticalPadding = dp2px(12f, dm).toInt()
            setPadding(horizontalPadding, verticalPadding, horizontalPadding, verticalPadding)
            setBackgroundColor(getColors().bgColorOperate)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        avatarView = Avatar(context).apply {
            setSize(Avatar.AvatarSize.L)
        }
        rootView.addView(avatarView)

        val textInfoLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val leftMargin = dp2px(16f, dm).toInt()
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            ).apply {
                marginStart = leftMargin
            }
        }

        nameView = TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            maxLines = 1
        }
        textInfoLayout.addView(nameView)

        idView = TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            val topMargin = dp2px(4f, dm).toInt()
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                this.topMargin = topMargin
            }
        }
        textInfoLayout.addView(idView)

        rootView.addView(textInfoLayout)
    }

    fun update(
        state: GroupChatSettingUiState,
        onAvatarClick: () -> Unit,
        onGroupNameConfirmed: (String) -> Unit,
        onGroupIdClick: () -> Unit
    ) {
        val permissions = state.permissions
        val headerDisplayName = state.headerDisplayName
        nameView.text = headerDisplayName
        idView.text = "${context.getString(R.string.chat_setting_group_id)}: ${state.groupID}"
        avatarView.setContent(
            Avatar.AvatarContent.Image(
                url = state.avatarURL.ifEmpty { null },
                fallbackName = headerDisplayName
            )
        )

        if (permissions.canEditGroupAvatar) {
            avatarView.setOnAvatarClickListener { onAvatarClick() }
        } else {
            avatarView.setOnAvatarClickListener(null)
        }

        if (permissions.canEditGroupName) {
            nameView.isClickable = true
            nameView.setOnClickListener {
                TextInputDialog(
                    context = context,
                    title = context.getString(R.string.chat_setting_modify_group_name),
                    initialText = state.groupName,
                    onConfirm = { value ->
                        if (value.isNotBlank()) {
                            onGroupNameConfirmed(value)
                        }
                    }
                ).show()
            }
        } else {
            nameView.isClickable = false
            nameView.setOnClickListener(null)
        }

        idView.isClickable = true
        idView.setOnClickListener { onGroupIdClick() }
    }

    fun applyThemeColors(colors: ColorTokens) {
        rootView.setBackgroundColor(colors.bgColorOperate)
        nameView.setTextColor(colors.textColorPrimary)
        idView.setTextColor(colors.textColorTertiary)
    }

    private fun getColors(): ColorTokens {
        return ThemeStore.shared(context).themeState.value.currentTheme.tokens.color
    }
}
