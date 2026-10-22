package com.trtc.uikit.roomkit.inroom.views.meeting

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.TextWatcher
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.appcompat.widget.AppCompatImageButton
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.content.ContextCompat
import androidx.core.widget.ImageViewCompat
import com.trtc.uikit.roomkit.R
import com.trtc.uikit.roomkit.base.util.dp
import com.trtc.uikit.roomkit.base.util.dpF

internal class RoomPasswordInputView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : LinearLayout(context, attrs) {

    val password: String get() = input.text?.toString()?.trim().orEmpty()

    private val fieldBg = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = context.dpF(RADIUS)
        setStroke(context.strokeWidth(), ContextCompat.getColor(context, R.color.roomkit_border_strong))
    }
    private val input: EditText
    private val eyeButton: AppCompatImageButton
    private val errorText: AppCompatTextView
    private var plainText = false

    init {
        orientation = VERTICAL

        input = EditText(context).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            filters = arrayOf(InputFilter.LengthFilter(PASSWORD_MAX_LENGTH))
            gravity = Gravity.CENTER
            background = null
            layoutParams = FrameLayout.LayoutParams(MATCH, MATCH)
        }
        eyeButton = AppCompatImageButton(context).apply {
            setImageResource(R.drawable.roomkit_icon_eye_off)
            scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
            setPadding(context.dp(7), context.dp(7), context.dp(7), context.dp(7))
            ImageViewCompat.setImageTintList(
                this,
                android.content.res.ColorStateList.valueOf(
                    ContextCompat.getColor(context, R.color.roomkit_fg_tertiary),
                ),
            )
            background = null
            contentDescription = context.getString(R.string.roomkit_schedule_show_password)
            layoutParams = FrameLayout.LayoutParams(context.dp(EYE_SIZE), context.dp(EYE_SIZE)).apply {
                gravity = Gravity.CENTER_VERTICAL or Gravity.END
                marginEnd = context.dp(EYE_MARGIN_END)
            }
            setOnClickListener { toggleEye() }
        }
        val fieldRow = FrameLayout(context).apply {
            background = fieldBg
            layoutParams = LayoutParams(MATCH, context.dp(FIELD_HEIGHT))
            addView(input)
            addView(eyeButton)
        }

        errorText = AppCompatTextView(context).apply {
            textSize = ERROR_TEXT_SIZE
            setTextColor(ContextCompat.getColor(context, R.color.roomkit_danger))
            gravity = Gravity.CENTER
            visibility = GONE
            layoutParams = LayoutParams(MATCH, WRAP).apply { topMargin = context.dp(8) }
        }

        addView(fieldRow)
        addView(errorText)

        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                errorText.visibility = GONE
                refreshStroke()
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })
        input.setOnFocusChangeListener { _, _ -> refreshStroke() }
    }

    fun showError(message: String) {
        errorText.text = message
        errorText.visibility = VISIBLE
        refreshStroke()
    }

    fun startEditing() {
        input.requestFocus()
    }

    private fun toggleEye() {
        plainText = !plainText
        eyeButton.setImageResource(
            if (plainText) R.drawable.roomkit_icon_eye else R.drawable.roomkit_icon_eye_off,
        )
        input.inputType = if (plainText) {
            InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_NORMAL
        } else {
            InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        }
        input.setSelection(input.text?.length ?: 0)
    }

    private fun refreshStroke() {
        val color = when {
            errorText.visibility == VISIBLE -> R.color.roomkit_danger
            input.hasFocus() -> R.color.roomkit_accent
            else -> R.color.roomkit_border_strong
        }
        fieldBg.setStroke(context.strokeWidth(), ContextCompat.getColor(context, color))
    }

    private fun Context.strokeWidth(): Int = dpF(STROKE_WIDTH).toInt()

    private companion object {
        const val FIELD_HEIGHT = 44
        const val RADIUS = 12f
        const val STROKE_WIDTH = 1.5f
        const val EYE_SIZE = 32
        const val EYE_MARGIN_END = 10
        const val ERROR_TEXT_SIZE = 11f
        const val PASSWORD_MAX_LENGTH = 6
        const val MATCH = LayoutParams.MATCH_PARENT
        const val WRAP = LayoutParams.WRAP_CONTENT
    }
}
