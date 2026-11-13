package com.tencent.qcloud.tuikit.tuicallkit.common

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat

internal object CallPanelHeader {

    fun create(context: Context, title: String): View {
        val grabber = View(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 2f.dp(context).toFloat()
                setColor(0x47FFFFFF)
            }
        }
        val titleLabel = TextView(context).apply {
            text = title
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
            setTextColor(ContextCompat.getColor(context, android.R.color.white))
        }
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val grabberParams = LinearLayout.LayoutParams(36.dp(context), 4.dp(context))
            grabberParams.topMargin = 12.dp(context)
            grabberParams.gravity = Gravity.CENTER_HORIZONTAL
            addView(grabber, grabberParams)
            val titleParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            titleParams.topMargin = 18.dp(context)
            titleParams.marginStart = 20.dp(context)
            addView(titleLabel, titleParams)
        }
    }
}
