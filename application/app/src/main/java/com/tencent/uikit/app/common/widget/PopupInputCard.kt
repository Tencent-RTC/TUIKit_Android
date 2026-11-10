package com.tencent.uikit.app.common.widget

import android.animation.ValueAnimator
import android.app.Activity
import android.graphics.drawable.ColorDrawable
import android.text.Editable
import android.text.InputFilter
import android.text.Spanned
import android.text.TextUtils
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.view.animation.LinearInterpolator
import android.widget.Button
import android.widget.EditText
import android.widget.PopupWindow
import android.widget.TextView
import android.widget.Toast
import com.tencent.uikit.app.R
import com.tencent.uikit.app.common.utils.SoftKeyBoardUtil
import java.util.regex.Pattern

class PopupInputCard(private val activity: Activity) {
    private var popupWindow: PopupWindow
    private lateinit var titleTv: TextView
    private lateinit var editText: EditText
    private lateinit var descriptionTv: TextView
    private lateinit var positiveBtn: Button
    private lateinit var closeBtn: View
    private var positiveOnClickListener: OnClickListener? = null
    private var textExceedListener: OnTextExceedListener? = null

    private var minLimit = 0
    private var maxLimit = Int.MAX_VALUE
    private var rule: String? = null
    private var notMachRuleTip: String? = null
    private val lengthFilter = ByteLengthFilter()

    init {
        val popupView = LayoutInflater.from(activity).inflate(R.layout.app_layout_popup_card, null)
        titleTv = popupView.findViewById(R.id.popup_card_title)
        editText = popupView.findViewById(R.id.popup_card_edit)
        descriptionTv = popupView.findViewById(R.id.popup_card_description)
        positiveBtn = popupView.findViewById(R.id.popup_card_positive_btn)
        closeBtn = popupView.findViewById(R.id.close_btn)

        popupWindow = object : PopupWindow(popupView, WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT, true) {
            override fun showAtLocation(anchor: View?, gravity: Int, x: Int, y: Int) {
                if (!activity.isFinishing) {
                    val dialogWindow = activity.window
                    startAnimation(dialogWindow, true)
                }
                editText.requestFocus()
                if (activity.window != null) {
                    SoftKeyBoardUtil.showKeyBoard(activity.window)
                }
                super.showAtLocation(anchor, gravity, x, y)
            }

            override fun dismiss() {
                if (!activity.isFinishing) {
                    val dialogWindow = activity.window
                    startAnimation(dialogWindow, false)
                }
                super.dismiss()
            }
        }
        
        popupWindow.setBackgroundDrawable(ColorDrawable())
        popupWindow.isTouchable = true
        popupWindow.isOutsideTouchable = false
        popupWindow.animationStyle = R.style.PopupInputCardAnim
        popupWindow.inputMethodMode = PopupWindow.INPUT_METHOD_NEEDED
        popupWindow.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        popupWindow.setOnDismissListener {
            if (activity.window != null) {
                SoftKeyBoardUtil.hideKeyBoard(activity.window)
            }
        }

        positiveBtn.setOnClickListener {
            val result = editText.text.toString()

            if (result.length !in minLimit..maxLimit) {
                Toast.makeText(activity, notMachRuleTip ?: "", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (!TextUtils.isEmpty(rule) && !Pattern.matches(rule, result)) {
                Toast.makeText(activity, notMachRuleTip ?: "", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            positiveOnClickListener?.onClick(editText.text.toString())
            popupWindow.dismiss()
        }

        closeBtn.setOnClickListener {
            popupWindow.dismiss()
        }
        
        editText.filters = arrayOf<InputFilter>(lengthFilter)
        editText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                if (!TextUtils.isEmpty(rule)) {
                    if (!Pattern.matches(rule, s.toString())) {
                        positiveBtn.isEnabled = false
                    } else {
                        positiveBtn.isEnabled = true
                    }
                }
            }
        })
    }

    private fun startAnimation(window: Window, isShow: Boolean) {
        val animator: ValueAnimator = if (isShow) {
            ValueAnimator.ofFloat(1.0f, 0.5f)
        } else {
            ValueAnimator.ofFloat(0.5f, 1.0f)
        }
        animator.addUpdateListener { animation ->
            val lp = window.attributes
            lp.alpha = animation.animatedValue as Float
            window.attributes = lp
        }
        val interpolator = LinearInterpolator()
        animator.duration = 200
        animator.interpolator = interpolator
        animator.start()
    }

    fun show(rootView: View?, gravity: Int) {
        popupWindow.showAtLocation(rootView, gravity, 0, 0)
    }

    fun setTitle(title: String?) {
        titleTv.text = title
    }

    fun setDescription(description: String?) {
        if (!TextUtils.isEmpty(description)) {
            descriptionTv.visibility = View.VISIBLE
            descriptionTv.text = description
        }
    }

    fun setContent(content: String?) {
        editText.setText(content)
    }

    fun setOnPositive(clickListener: OnClickListener?) {
        positiveOnClickListener = clickListener
    }

    fun setTextExceedListener(textExceedListener: OnTextExceedListener?) {
        this.textExceedListener = textExceedListener
    }

    fun setSingleLine(isSingleLine: Boolean) {
        editText.isSingleLine = isSingleLine
    }

    fun setMaxLimit(maxLimit: Int) {
        this.maxLimit = maxLimit
        lengthFilter.setLength(maxLimit)
    }

    fun setMinLimit(minLimit: Int) {
        this.minLimit = minLimit
    }

    fun setRule(rule: String?) {
        this.rule = if (TextUtils.isEmpty(rule)) {
            ""
        } else {
            rule
        }
    }

    fun setNotMachRuleTip(notMachRuleTip: String?) {
        this.notMachRuleTip = notMachRuleTip
    }

    inner class ByteLengthFilter : InputFilter {
        private var length = Int.MAX_VALUE

        fun setLength(length: Int) {
            this.length = length
        }

        override fun filter(source: CharSequence?, start: Int, end: Int, dest: Spanned?, dstart: Int, dend: Int): CharSequence? {
            var destLength = 0
            var destReplaceLength = 0
            var sourceLength = 0
            if (!TextUtils.isEmpty(dest)) {
                destLength = dest.toString().toByteArray().size
                destReplaceLength = dest!!.subSequence(dstart, dend).toString().toByteArray().size
            }
            if (!TextUtils.isEmpty(source)) {
                sourceLength = source!!.subSequence(start, end).toString().toByteArray().size
            }
            val keepBytesLength = length - (destLength - destReplaceLength)
            return if (keepBytesLength <= 0) {
                textExceedListener?.onTextExceedMax()
                ""
            } else if (keepBytesLength >= sourceLength) {
                null
            } else {
                textExceedListener?.onTextExceedMax()
                getSource(source!!, start, keepBytesLength)
            }
        }

        private fun getSource(sequence: CharSequence, start: Int, keepLength: Int): CharSequence {
            val sequenceLength = sequence.length
            var end = 0
            for (i in 1..sequenceLength) {
                if (sequence.subSequence(0, i).toString().toByteArray().size <= keepLength) {
                    end = i
                } else {
                    break
                }
            }
            if (end > 0 && Character.isHighSurrogate(sequence[end - 1])) {
                --end
                if (end == start) {
                    return ""
                }
            }
            return sequence.subSequence(start, end)
        }
    }

    fun interface OnClickListener {
        fun onClick(result: String)
    }

    interface OnTextExceedListener {
        fun onTextExceedMax()
    }
}