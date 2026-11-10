package com.tencent.uikit.app.common.widget

import android.app.Dialog
import android.os.Bundle
import android.text.TextUtils
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import com.tencent.uikit.app.R

class ConfirmDialogFragment : DialogFragment() {
    private var positiveClickListener: PositiveClickListener? =
        null
    private var negativeClickListener: NegativeClickListener? =
        null

    private var messageText: String? = null
    private var positiveText: String? = null
    private var negativeText: String? = null
    private var buttonNegative: Button? = null
    private var buttonPositive: Button? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = Dialog(requireContext(), R.style.TRTCLiveConfirmDialogFragment)
        dialog.setContentView(R.layout.app_dialog_confirm)
        buttonPositive = dialog.findViewById<View?>(R.id.btn_positive) as Button
        buttonNegative = dialog.findViewById<View?>(R.id.btn_negative) as Button
        dialog.setCancelable(false)
        initTextMessage(dialog)
        initButtonPositive()
        initButtonNegative()
        return dialog
    }

    private fun initTextMessage(dialog: Dialog) {
        val textMessage = dialog.findViewById<View?>(R.id.tv_message) as TextView
        textMessage.setText(messageText)
    }

    private fun initButtonPositive() {
        if (positiveClickListener == null) {
            buttonPositive?.visibility = View.GONE
            return
        }
        if (!TextUtils.isEmpty(positiveText)) {
            buttonPositive?.text = positiveText
        }
        buttonPositive?.visibility = View.VISIBLE
        buttonPositive?.setOnClickListener { positiveClickListener?.onClick() }
    }

    private fun initButtonNegative() {
        if (negativeClickListener == null) {
            buttonNegative?.visibility = View.GONE
            return
        }
        if (!TextUtils.isEmpty(negativeText)) {
            buttonNegative?.text = negativeText
        }
        buttonNegative?.visibility = View.VISIBLE
        buttonNegative?.setOnClickListener { negativeClickListener?.onClick() }
    }

    fun setMessage(message: String?) {
        messageText = message
    }

    fun setPositiveText(text: String?) {
        positiveText = text
        buttonPositive?.visibility = View.VISIBLE
    }

    fun setNegativeText(text: String?) {
        negativeText = text
    }

    fun setPositiveClickListener(listener: PositiveClickListener?) {
        this.positiveClickListener = listener
    }

    fun setNegativeClickListener(listener: NegativeClickListener?) {
        this.negativeClickListener = listener
    }

    interface PositiveClickListener {
        fun onClick()
    }

    interface NegativeClickListener {
        fun onClick()
    }
}