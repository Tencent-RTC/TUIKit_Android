package com.tencent.qcloud.tuikit.tuicallkit.common

import android.content.Context
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.tencent.qcloud.tuikit.tuicallkit.R

internal class LightSheetHeader @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    internal var onClose: (() -> Unit)? = null
        set(value) {
            field = value
            closeButton.isVisible = value != null
        }

    internal var onAction: (() -> Unit)? = null
        set(value) {
            field = value
            actionButton.isVisible = value != null
        }

    private val titleLabel: TextView
    private val closeButton: TextView
    private val actionButton: TextView

    init {
        LayoutInflater.from(context).inflate(R.layout.tuicallkit_light_sheet_header, this, true)
        titleLabel = findViewById(R.id.sheet_title)
        closeButton = findViewById(R.id.close_button)
        actionButton = findViewById(R.id.action_button)
        closeButton.setOnClickListener { onClose?.invoke() }
        actionButton.setOnClickListener { onAction?.invoke() }
    }

    fun setTitle(title: CharSequence) {
        titleLabel.text = title
    }

    fun setActionTitle(title: CharSequence, isCancel: Boolean) {
        actionButton.text = title
        val colorRes = if (isCancel) R.color.tuicallkit_call_red else R.color.tuicallkit_brand
        actionButton.setTextColor(ContextCompat.getColor(context, colorRes))
        actionButton.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL), Typeface.NORMAL)
    }

    fun setActionEnabled(enabled: Boolean) {
        actionButton.isEnabled = enabled
        actionButton.alpha = if (enabled) 1f else 0.4f
    }
}
