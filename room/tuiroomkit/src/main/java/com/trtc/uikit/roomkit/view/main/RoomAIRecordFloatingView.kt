package com.trtc.uikit.roomkit.view.main

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import com.trtc.uikit.roomkit.R

class RoomAIRecordFloatingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    var onTapListener: ((RoomAIRecordFloatingView) -> Unit)? = null

    init {
        LayoutInflater.from(context).inflate(R.layout.roomkit_view_ai_record_floating, this)
        initView()
    }

    private fun initView() {
        setOnClickListener { onTapListener?.invoke(this) }
    }
}
