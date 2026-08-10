package io.trtc.tuikit.chat.uikit.components.contactlist.ui.addcontact
import android.content.Context
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import io.trtc.tuikit.chat.uikit.R
import io.trtc.tuikit.atomicx.common.util.ScreenUtil.dp2px
import io.trtc.tuikit.chat.uikit.components.common.displayName
import io.trtc.tuikit.chat.uikit.components.contactlist.viewmodel.AddType
import io.trtc.tuikit.atomicx.theme.tokens.ColorTokens
import io.trtc.tuikit.chat.uikit.components.widgets.Avatar
import io.trtc.tuikit.atomicxcore.api.contact.ContactInfo

internal fun buildAddContactCardRow(
    context: Context,
    colors: ColorTokens,
    label: String,
    defaultValue: String,
    editable: Boolean = false
): Pair<View, EditText> {
    val dm = context.resources.displayMetrics
    val cardHeight = dp2px(52f, dm).toInt()
    val cardPaddingH = dp2px(16f, dm).toInt()

    val card = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        layoutDirection = View.LAYOUT_DIRECTION_LOCALE
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            cardHeight
        )
        setPadding(cardPaddingH, 0, cardPaddingH, 0)
        setBackgroundColor(colors.bgColorOperate)
    }

    card.addView(TextView(context).apply {
        text = label
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        setTextColor(colors.textColorPrimary)
    })

    card.addView(View(context).apply {
        layoutParams = LinearLayout.LayoutParams(dp2px(16f, dm).toInt(), 1)
    })

    val valueInput = EditText(context).apply {
        setText(defaultValue)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        setTextColor(colors.textColorPrimary)
        background = null
        setSingleLine()
        gravity = Gravity.END or Gravity.CENTER_VERTICAL
        textAlignment = View.TEXT_ALIGNMENT_VIEW_END
        textDirection = View.TEXT_DIRECTION_LOCALE
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
        isEnabled = editable
    }
    card.addView(valueInput)

    return Pair(card, valueInput)
}

internal fun buildAddContactInfoCard(
    context: Context,
    colors: ColorTokens,
    addType: AddType,
    result: ContactInfo
): View {
    val dm = context.resources.displayMetrics
    val hPadding = dp2px(16f, dm).toInt()

    val card = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        layoutDirection = View.LAYOUT_DIRECTION_LOCALE
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        setPadding(hPadding, dp2px(12f, dm).toInt(), hPadding, dp2px(12f, dm).toInt())
        setBackgroundColor(colors.bgColorOperate)
    }

    val avatar = Avatar(context).apply {
        val url = result.avatarURL
        if (!url.isNullOrEmpty()) {
            setContent(Avatar.AvatarContent.Image(url, result.displayName))
        } else {
            setContent(Avatar.AvatarContent.Text(result.displayName))
        }
        setSize(Avatar.AvatarSize.XL)
        layoutParams = LinearLayout.LayoutParams(
            dp2px(48f, dm).toInt(), dp2px(48f, dm).toInt()
        )
    }
    card.addView(avatar)

    card.addView(View(context).apply {
        layoutParams = LinearLayout.LayoutParams(dp2px(12f, dm).toInt(), 1)
    })

    val nameColumn = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
    }

    nameColumn.addView(TextView(context).apply {
        text = result.displayName
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        setTextColor(colors.textColorPrimary)
        setTypeface(typeface, Typeface.BOLD)
        maxLines = 1
    })

    nameColumn.addView(View(context).apply {
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp2px(2f, dm).toInt())
    })

    nameColumn.addView(TextView(context).apply {
        val idLabel = if (addType == AddType.GROUP) {
            context.getString(R.string.contact_list_group_id)
        } else {
            context.getString(R.string.contact_list_user_id)
        }
        text = context.getString(
            R.string.contact_list_label_value_format,
            idLabel,
            result.userID
        )
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        setTextColor(colors.textColorSecondary)
        maxLines = 1
    })

    if (addType == AddType.CONTACT) {
        val signatureText = result.aboutMe
            ?.takeIf { it.isNotBlank() }
            ?: context.getString(R.string.contact_list_no_content)
        nameColumn.addView(View(context).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp2px(2f, dm).toInt())
        })
        nameColumn.addView(TextView(context).apply {
            text = context.getString(
                R.string.contact_list_label_value_format,
                context.getString(R.string.contact_list_signature),
                signatureText
            )
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(colors.textColorSecondary)
            maxLines = 2
        })
    }

    card.addView(nameColumn)
    return card
}

internal fun buildAddContactSectionTitle(
    context: Context,
    colors: ColorTokens,
    text: String
): View {
    val dm = context.resources.displayMetrics
    return TextView(context).apply {
        this.text = text
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        setTextColor(colors.textColorSecondary)
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        setPadding(
            dp2px(16f, dm).toInt(),
            dp2px(16f, dm).toInt(),
            dp2px(16f, dm).toInt(),
            dp2px(8f, dm).toInt()
        )
    }
}

internal fun buildAddContactSectionSpacer(
    context: Context,
    colors: ColorTokens
): View {
    val dm = context.resources.displayMetrics
    return View(context).apply {
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dp2px(10f, dm).toInt()
        )
        setBackgroundColor(colors.bgColorDefault)
    }
}

internal fun buildAddContactActionCard(
    context: Context,
    colors: ColorTokens,
    text: String,
    textColor: Int,
    onClick: () -> Unit
): View {
    val dm = context.resources.displayMetrics
    val height = dp2px(52f, dm).toInt()

    return TextView(context).apply {
        this.text = text
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        setTextColor(textColor)
        gravity = Gravity.CENTER
        isClickable = true
        isFocusable = true
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            height
        )
        setBackgroundColor(colors.bgColorOperate)
        setOnClickListener { onClick() }
    }
}

internal fun buildAddContactMultilineInputCard(
    context: Context,
    colors: ColorTokens,
    defaultValue: String,
    minHeightDp: Float
): Pair<View, EditText> {
    val dm = context.resources.displayMetrics
    val padding = dp2px(14f, dm).toInt()

    val card = FrameLayout(context).apply {
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        setBackgroundColor(colors.bgColorOperate)
        minimumHeight = dp2px(minHeightDp, dm).toInt()
        setPadding(padding, padding, padding, padding)
    }

    val input = EditText(context).apply {
        setText(defaultValue)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        setTextColor(colors.textColorPrimary)
        background = null
        gravity = Gravity.TOP or Gravity.START
        isSingleLine = false
        setHorizontallyScrolling(false)
        maxLines = 8
        layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }
    card.addView(input)
    return Pair(card, input)
}
