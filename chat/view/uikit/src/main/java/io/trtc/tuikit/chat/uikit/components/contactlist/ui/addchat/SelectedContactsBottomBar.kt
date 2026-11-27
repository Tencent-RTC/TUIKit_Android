package io.trtc.tuikit.chat.uikit.components.contactlist.ui.addchat
import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
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

internal class SelectedContactsBottomBar(
    context: Context,
    selectedContacts: List<ContactInfo>,
    private val onConfirmClick: () -> Unit
) : LinearLayout(context) {

    private val selectedAvatarsScrollView: HorizontalScrollView
    private val selectedAvatarsContainer: LinearLayout
    private val confirmButton: TextView

    init {
        val colors = getColors()
        val dm = context.resources.displayMetrics

        orientation = VERTICAL
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )

        addView(View(context).apply {
            setBackgroundColor(colors.strokeColorSecondary)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp2px(0.5f, dm).toInt().coerceAtLeast(1)
            )
        })

        val container = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
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
            setBackgroundColor(colors.bgColorOperate)
        }

        selectedAvatarsScrollView = HorizontalScrollView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
            isHorizontalScrollBarEnabled = false
        }

        selectedAvatarsContainer = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        selectedAvatarsScrollView.addView(selectedAvatarsContainer)
        container.addView(selectedAvatarsScrollView)

        confirmButton = TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(colors.textColorButton)
            gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
            val btnHeight = dp2px(36f, dm).toInt()
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                btnHeight
            ).apply {
                marginStart = dp2px(12f, dm).toInt()
            }
            setPadding(dp2px(16f, dm).toInt(), 0, dp2px(16f, dm).toInt(), 0)
            setOnClickListener { onConfirmClick() }
        }
        container.addView(confirmButton)
        addView(container)

        update(selectedContacts)
    }

    fun update(selectedContacts: List<ContactInfo>) {
        val colors = getColors()
        val dm = context.resources.displayMetrics
        confirmButton.apply {
            text = context.getString(R.string.contact_list_confirm_selection, selectedContacts.size)
            background = GradientDrawable().apply {
                setColor(
                    if (selectedContacts.isNotEmpty()) colors.textColorLink
                    else colors.textColorDisable
                )
                cornerRadius = dp2px(4f, dm)
            }
        }
        populateSelectedAvatars(selectedContacts)
    }

    private fun populateSelectedAvatars(selectedContacts: List<ContactInfo>) {
        val dm = context.resources.displayMetrics
        selectedAvatarsContainer.removeAllViews()

        val avatarSize = dp2px(40f, dm).toInt()
        val avatarSpacing = dp2px(8f, dm).toInt()

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
            selectedAvatarsContainer.addView(avatarView)
        }

        selectedAvatarsScrollView.post {
            selectedAvatarsScrollView.fullScroll(View.FOCUS_RIGHT)
        }
    }

    private fun getColors(): ColorTokens {
        return ThemeStore.shared(context).themeState.value.currentTheme.tokens.color
    }
}
