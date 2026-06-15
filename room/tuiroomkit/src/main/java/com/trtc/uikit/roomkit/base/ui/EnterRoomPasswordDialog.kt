package com.trtc.uikit.roomkit.base.ui

import android.app.Dialog
import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import com.trtc.uikit.roomkit.R

class EnterRoomPasswordDialog(
    context: Context,
    private val onCancel: () -> Unit,
    private val onConfirm: (password: String) -> Unit
) : Dialog(context, R.style.RoomKitCenterDialog) {

    private lateinit var etPassword: EditText
    private lateinit var tvTitle: TextView
    private lateinit var tvCancel: TextView
    private lateinit var tvConfirm: TextView
    private lateinit var ivClear: ImageView

    init {
        setContentView(R.layout.roomkit_dialog_room_password)
        setupWindow()
        setupViews()
        setupListeners()
        setCancelable(false)
        setCanceledOnTouchOutside(false)
    }

    private fun setupWindow() {
        window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        }
    }

    private fun setupViews() {
        tvTitle = findViewById(R.id.tv_room_password_title)
        etPassword = findViewById(R.id.et_room_password)
        tvCancel = findViewById(R.id.tv_cancel_password)
        tvConfirm = findViewById(R.id.tv_confirm_password)
        ivClear = findViewById(R.id.iv_clear_password)
    }

    private fun setupListeners() {
        etPassword.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                val notEmpty = !s.isNullOrEmpty()
                ivClear.visibility = if (notEmpty) View.VISIBLE else View.GONE
            }
        })

        ivClear.setOnClickListener {
            etPassword.setText("")
        }

        tvCancel.setOnClickListener {
            dismiss()
            onCancel()
        }

        tvConfirm.setOnClickListener {
            onConfirm(etPassword.text.toString())
        }

        setOnKeyListener { _, keyCode, event ->
            keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_DOWN
        }
    }
}
