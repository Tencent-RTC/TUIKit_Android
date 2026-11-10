package com.trtc.uikit.roomkit.inroom.views.widgets

import android.app.Dialog
import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.AppCompatButton
import androidx.core.content.ContextCompat
import com.trtc.uikit.roomkit.R
import com.trtc.uikit.roomkit.base.util.dp
import com.trtc.uikit.roomkit.base.util.dpF
import kotlin.math.min

internal class RoomPasswordDialog(
    context: Context,
    private val wrongPassword: Boolean,
    private val onSubmit: (String) -> Unit,
    private val onCancel: () -> Unit,
) : Dialog(context, R.style.RoomKitDialog) {

    private val card: LinearLayout
    private val fieldRow: FrameLayout
    private val input: EditText
    private val eyeButton: ImageButton
    private val errorText: TextView
    private val fieldBg: GradientDrawable
    private var plainText = false

    init {
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setCanceledOnTouchOutside(false)
        setCancelable(false)

        val root = LayoutInflater.from(context).inflate(R.layout.roomkit_password_dialog, null) as FrameLayout
        card = root.findViewById(R.id.password_card)
        fieldRow = root.findViewById(R.id.password_field_row)
        input = root.findViewById(R.id.password_input)
        eyeButton = root.findViewById(R.id.password_eye)
        errorText = root.findViewById(R.id.password_error)
        card.clipToOutline = true
        fieldRow.clipToOutline = true
        fieldBg = fieldRow.background.mutate() as GradientDrawable

        val displayWidth = context.resources.displayMetrics.widthPixels
        val maxCardWidth = context.dp(CARD_MAX_WIDTH)
        if (displayWidth - context.dp(MASK_INSET) * 2 > maxCardWidth) {
            card.layoutParams.width = maxCardWidth
        }

        root.findViewById<AppCompatButton>(R.id.password_cancel).setOnClickListener {
            dismiss()
            onCancel()
        }
        root.findViewById<AppCompatButton>(R.id.password_confirm).setOnClickListener {
            if (password.isEmpty()) {
                showError(context.getString(R.string.roomkit_join_password_placeholder))
                return@setOnClickListener
            }
            if (password.length != PASSWORD_LENGTH) {
                showError(context.getString(R.string.roomkit_join_password_placeholder))
                return@setOnClickListener
            }
            dismiss()
            onSubmit(password)
        }

        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                errorText.visibility = View.GONE
                refreshStroke()
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })
        input.setOnFocusChangeListener { _, _ -> refreshStroke() }
        eyeButton.setOnClickListener { toggleEye() }

        setContentView(root)
    }

    override fun show() {
        window?.apply {
            setBackgroundDrawable(ColorDrawable(ContextCompat.getColor(context, R.color.roomkit_scrim)))
            setDimAmount(0f)
            navigationBarColor = ContextCompat.getColor(context, R.color.roomkit_dialog_nav_bar)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                isNavigationBarContrastEnforced = false
            }
            setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                    WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE,
            )
        }
        super.show()
        window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )
        input.requestFocus()
        if (wrongPassword) showError(context.getString(R.string.roomkit_room_entry_wrong_password))
    }

    val password: String get() = input.text?.toString()?.trim().orEmpty()

    fun showError(message: String) {
        errorText.text = message
        errorText.visibility = View.VISIBLE
        refreshStroke()
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
            errorText.visibility == View.VISIBLE -> R.color.roomkit_danger
            input.hasFocus() -> R.color.roomkit_accent
            else -> R.color.roomkit_border_strong
        }
        fieldBg.setStroke(context.dpF(STROKE_WIDTH).toInt(), ContextCompat.getColor(context, color))
    }

    private companion object {
        const val CARD_MAX_WIDTH = 420
        const val MASK_INSET = 20
        const val STROKE_WIDTH = 1.5f
        const val PASSWORD_LENGTH = 6
    }
}
