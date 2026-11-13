package com.tencent.uikit.app.call.common

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout

internal class CallAccessTapRow @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    internal var onTap: (() -> Unit)? = null

    init {
        setOnClickListener { onTap?.invoke() }
    }
}
