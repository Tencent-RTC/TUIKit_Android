package io.trtc.tuikit.chat.uikit.components.contactlist.ui
import android.content.Context
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
import io.trtc.tuikit.chat.uikit.components.common.displayName
import io.trtc.tuikit.atomicx.theme.ThemeStore
import io.trtc.tuikit.atomicx.theme.tokens.ColorTokens
import io.trtc.tuikit.chat.uikit.components.widgets.Avatar
import io.trtc.tuikit.chat.uikit.components.widgets.Avatar.AvatarContent
import io.trtc.tuikit.atomicxcore.api.contact.FriendApplicationInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

internal class FriendApplicationDetailDialog(
    context: Context,
    private val application: FriendApplicationInfo,
    private val onAccept: (FriendApplicationInfo, onSuccess: () -> Unit) -> Unit,
    private val onRefuse: (FriendApplicationInfo, onSuccess: () -> Unit) -> Unit
) : ContactSubPageDialog(context) {

    private var scope: CoroutineScope? = null

    private lateinit var avatar: Avatar
    private lateinit var nameText: TextView
    private lateinit var idText: TextView
    private lateinit var validationLabelText: TextView
    private lateinit var validationValueText: TextView
    private lateinit var validationSection: LinearLayout
    private lateinit var agreeBtn: TextView
    private lateinit var refuseBtn: TextView
    private var agreeBtnCornerRadius: Float = 0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTitle(context.getString(R.string.contact_list_friend_application_info))

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
            val topPad = dp2px(20f, dm).toInt()
            setPadding(0, topPad, 0, 0)
        }

        buildUserInfoSection(contentColumn, colors, dm)
        buildValidationSection(contentColumn, colors, dm)
        buildActionButtons(contentColumn, colors, dm)

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
        val hPad = dp2px(16f, dm).toInt()
        val vPad = dp2px(10f, dm).toInt()
        val avatarTextSpacing = dp2px(18f, dm).toInt()
        val avatarSize = dp2px(64f, dm).toInt()

        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_LOCALE
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setPadding(hPad, vPad, hPad, vPad)
        }

        avatar = Avatar(context).apply {
            layoutParams = LinearLayout.LayoutParams(avatarSize, avatarSize)
            setSize(Avatar.AvatarSize.XL)
        }
        row.addView(avatar)

        val spacer = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(avatarTextSpacing, 1)
        }
        row.addView(spacer)

        val textColumn = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        nameText = TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTextColor(colors.textColorPrimary)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            maxLines = 1
            textDirection = View.TEXT_DIRECTION_LOCALE
            textAlignment = View.TEXT_ALIGNMENT_VIEW_START
        }
        textColumn.addView(nameText)

        val nameIdSpacing = dp2px(4f, dm).toInt()
        val nameIdSpacer = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                nameIdSpacing
            )
        }
        textColumn.addView(nameIdSpacer)

        idText = TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(colors.textColorSecondary)
            maxLines = 1
            textDirection = View.TEXT_DIRECTION_LOCALE
            textAlignment = View.TEXT_ALIGNMENT_VIEW_START
        }
        textColumn.addView(idText)

        row.addView(textColumn)
        parent.addView(row)
    }

    private fun buildValidationSection(
        parent: LinearLayout,
        colors: ColorTokens,
        dm: android.util.DisplayMetrics
    ) {
        val hPad = dp2px(16f, dm).toInt()
        val vPad = dp2px(12f, dm).toInt()
        val labelValueSpacing = dp2px(16f, dm).toInt()

        validationSection = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_LOCALE
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setPadding(hPad, vPad, hPad, vPad)
            visibility = View.GONE
        }

        validationLabelText = TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTextColor(colors.textColorSecondary)
            text = context.getString(R.string.contact_list_friend_application_validation_message)
            textDirection = View.TEXT_DIRECTION_LOCALE
            textAlignment = View.TEXT_ALIGNMENT_VIEW_START
        }
        validationSection.addView(validationLabelText)

        val labelSpacer = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(labelValueSpacing, 1)
        }
        validationSection.addView(labelSpacer)

        validationValueText = TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTextColor(colors.textColorPrimary)
            textDirection = View.TEXT_DIRECTION_LOCALE
            textAlignment = View.TEXT_ALIGNMENT_VIEW_START
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        validationSection.addView(validationValueText)

        parent.addView(validationSection)
    }

    private fun buildActionButtons(
        parent: LinearLayout,
        colors: ColorTokens,
        dm: android.util.DisplayMetrics
    ) {
        val topSpacer = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp2px(40f, dm).toInt()
            )
        }
        parent.addView(topSpacer)

        val buttonRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutDirection = View.LAYOUT_DIRECTION_LOCALE
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            val hPad = dp2px(16f, dm).toInt()
            val vPad = dp2px(20f, dm).toInt()
            setPadding(hPad, vPad, hPad, vPad)
        }

        val buttonHeight = dp2px(42f, dm).toInt()
        agreeBtnCornerRadius = dp2px(10f, dm)
        val buttonSpacing = dp2px(20f, dm).toInt()

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
        buttonRow.addView(agreeBtn)

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
        buttonRow.addView(refuseBtn)

        parent.addView(buttonRow)
    }

    private fun bindData(colors: ColorTokens) {
        val avatarUrl = application.avatarURL
        if (!avatarUrl.isNullOrEmpty()) {
            avatar.setContent(AvatarContent.Image(avatarUrl, application.displayName))
        } else {
            avatar.setContent(AvatarContent.Text(application.displayName))
        }

        nameText.text = application.displayName
        idText.text = context.getString(
            R.string.contact_list_label_value_format,
            context.getString(R.string.contact_list_user_id),
            application.userID
        )

        if (!application.addWording.isNullOrEmpty()) {
            validationSection.visibility = View.VISIBLE
            validationValueText.text = application.addWording
        } else {
            validationSection.visibility = View.GONE
        }

        refreshButtonColors(colors)
    }

    private fun refreshDetailColors(colors: ColorTokens) {
        nameText.setTextColor(colors.textColorPrimary)
        idText.setTextColor(colors.textColorSecondary)
        validationLabelText.setTextColor(colors.textColorSecondary)
        validationValueText.setTextColor(colors.textColorPrimary)
        refreshButtonColors(colors)
    }

    private fun refreshButtonColors(colors: ColorTokens) {
        val agreeDrawable = GradientDrawable().apply {
            cornerRadius = agreeBtnCornerRadius
            setColor(colors.buttonColorPrimaryDefault)
        }
        agreeBtn.background = agreeDrawable
        agreeBtn.setTextColor(colors.textColorButton)

        val refuseDrawable = GradientDrawable().apply {
            cornerRadius = agreeBtnCornerRadius
            setColor(colors.bgColorInput)
        }
        refuseBtn.background = refuseDrawable
        refuseBtn.setTextColor(colors.textColorError)
    }
}
