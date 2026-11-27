package io.trtc.tuikit.chat.uikit.components.contactlist.ui.addchat
import android.content.Context
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import io.trtc.tuikit.chat.uikit.R
import io.trtc.tuikit.atomicx.common.util.ScreenUtil.dp2px
import io.trtc.tuikit.chat.uikit.components.common.displayName
import io.trtc.tuikit.atomicx.theme.ThemeStore
import io.trtc.tuikit.atomicx.theme.tokens.ColorTokens
import io.trtc.tuikit.chat.uikit.components.widgets.Avatar
import io.trtc.tuikit.atomicxcore.api.contact.ContactInfo

internal class SelectedMembersPreviewView(
    context: Context,
    selectedContacts: List<ContactInfo>
) : LinearLayout(context) {

    private val avatarsRow: LinearLayout

    init {
        val colors = getColors()
        val dm = context.resources.displayMetrics

        orientation = VERTICAL
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        setBackgroundColor(colors.bgColorTopBar)

        addView(TextView(context).apply {
            text = context.getString(R.string.contact_list_selected_group_member)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTextColor(colors.textColorPrimary)
            setTypeface(typeface, Typeface.BOLD)
            setPadding(
                dp2px(16f, dm).toInt(),
                dp2px(12f, dm).toInt(),
                dp2px(16f, dm).toInt(),
                dp2px(8f, dm).toInt()
            )
        })

        val scrollView = HorizontalScrollView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp2px(16f, dm).toInt()
            }
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            clipToPadding = false
            setPadding(
                dp2px(16f, dm).toInt(),
                0,
                dp2px(16f, dm).toInt(),
                0
            )
        }
        avatarsRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        scrollView.addView(avatarsRow)
        addView(scrollView)

        update(selectedContacts)
    }

    fun update(selectedContacts: List<ContactInfo>) {
        val dm = context.resources.displayMetrics
        avatarsRow.removeAllViews()

        val avatarSize = dp2px(Avatar.AvatarSize.M.sizeDp, dm).toInt()
        val avatarSpacing = dp2px(12f, dm).toInt()

        selectedContacts.forEachIndexed { index, contact ->
            val avatarView = Avatar(context).apply {
                layoutParams = LayoutParams(avatarSize, avatarSize).apply {
                    if (index > 0) marginStart = avatarSpacing
                }
                setSize(Avatar.AvatarSize.M)
                val avatarUrl = contact.avatarURL
                if (avatarUrl.isNullOrEmpty()) {
                    setContent(Avatar.AvatarContent.Text(contact.displayName))
                } else {
                    setContent(Avatar.AvatarContent.Image(avatarUrl, contact.displayName))
                }
            }
            avatarsRow.addView(avatarView)
        }
    }

    private fun getColors(): ColorTokens {
        return ThemeStore.shared(context).themeState.value.currentTheme.tokens.color
    }
}
