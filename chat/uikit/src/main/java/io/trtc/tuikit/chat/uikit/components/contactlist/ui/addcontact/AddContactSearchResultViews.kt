package io.trtc.tuikit.chat.uikit.components.contactlist.ui.addcontact
import android.content.Context
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.View
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

internal fun buildAddContactSearchResultView(
    context: Context,
    colors: ColorTokens,
    addType: AddType,
    result: ContactInfo,
    isJoinGroupAlready: Boolean,
    onClick: (ContactInfo) -> Unit
): View {
    val dm = context.resources.displayMetrics

    val row = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        setPadding(dp2px(16f, dm).toInt(), dp2px(12f, dm).toInt(), dp2px(16f, dm).toInt(), dp2px(12f, dm).toInt())
        isClickable = true
        isFocusable = true
        setOnClickListener { onClick(result) }
    }

    val avatar = Avatar(context).apply {
        val url = result.avatarURL
        if (!url.isNullOrEmpty()) {
            setContent(Avatar.AvatarContent.Image(url, result.displayName))
        } else {
            setContent(Avatar.AvatarContent.Text(result.displayName))
        }
        layoutParams = LinearLayout.LayoutParams(
            dp2px(48f, dm).toInt(), dp2px(48f, dm).toInt()
        )
    }
    row.addView(avatar)

    val spacer = View(context).apply {
        layoutParams = LinearLayout.LayoutParams(dp2px(12f, dm).toInt(), 1)
    }
    row.addView(spacer)

    val infoColumn = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
    }

    val nameText = TextView(context).apply {
        text = result.displayName
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        setTextColor(colors.textColorPrimary)
        maxLines = 1
    }
    infoColumn.addView(nameText)

    val idText = TextView(context).apply {
        val idLabel = if (addType == AddType.GROUP) {
            context.getString(R.string.contact_list_group_id)
        } else {
            context.getString(R.string.contact_list_user_id)
        }
        val full = context.getString(
            R.string.contact_list_label_value_format,
            idLabel,
            result.userID
        )
        val valueStart = full.indexOf(result.userID)
        val spannable = SpannableString(full)
        if (valueStart >= 0) {
            spannable.setSpan(
                ForegroundColorSpan(colors.textColorLink),
                valueStart,
                valueStart + result.userID.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        text = spannable
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        setTextColor(colors.textColorSecondary)
        maxLines = 1
    }
    infoColumn.addView(idText)

    row.addView(infoColumn)

    val alreadyTips = when (addType) {
        AddType.CONTACT -> if (result.isFriend == true) context.getString(R.string.contact_list_already_is_friend) else null
        AddType.GROUP -> if (isJoinGroupAlready) context.getString(R.string.contact_list_already_in_group) else null
        else -> null
    }
    if (alreadyTips != null) {
        val tipsText = TextView(context).apply {
            text = alreadyTips
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(colors.textColorSecondary)
        }
        row.addView(tipsText)
    }

    return row
}

internal fun buildAddContactSearchEmptyView(
    context: Context,
    colors: ColorTokens
): View {
    val dm = context.resources.displayMetrics
    return TextView(context).apply {
        text = context.getString(R.string.contact_list_no_information)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        setTextColor(colors.textColorSecondary)
        gravity = Gravity.CENTER
        layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = dp2px(32f, dm).toInt()
        }
    }
}
