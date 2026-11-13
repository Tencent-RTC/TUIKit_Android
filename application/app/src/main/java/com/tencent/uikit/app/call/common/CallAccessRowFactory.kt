package com.tencent.uikit.app.call.common

import android.content.Context
import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.tencent.uikit.app.R

internal object CallAccessRowFactory {

    fun optionRow(
        context: Context,
        title: CharSequence,
        isSelected: Boolean,
        showsSeparator: Boolean = true,
        onTap: () -> Unit,
    ): View {
        val row = LayoutInflater.from(context).inflate(R.layout.callaccess_option_row, null)
        // inflate(null) 不会为根节点生成 LayoutParams，显式恢复 XML 声明的 52dp 行高
        row.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 52.dp(context))
        val titleView = row.findViewById<TextView>(R.id.option_title)
        titleView.text = title
        titleView.setTextColor(ContextCompat.getColor(context, if (isSelected) R.color.callaccess_brand else R.color.callaccess_text))
        titleView.setTypeface(null, if (isSelected) Typeface.BOLD else Typeface.NORMAL)
        row.findViewById<View>(R.id.option_check).isVisible = isSelected
        row.findViewById<View>(R.id.option_separator).isVisible = showsSeparator
        row.setOnClickListener { onTap() }
        return row
    }
}
