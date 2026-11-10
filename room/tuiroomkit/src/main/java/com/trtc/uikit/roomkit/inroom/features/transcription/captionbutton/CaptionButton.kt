package com.trtc.uikit.roomkit.inroom.features.transcription.captionbutton

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.view.LayoutInflater
import android.widget.LinearLayout
import com.trtc.uikit.roomkit.R
import com.trtc.uikit.roomkit.base.toast.RoomToast
import com.trtc.uikit.roomkit.base.util.dp

class CaptionButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr) {

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        setPadding(context.dp(2), context.dp(10), context.dp(2), context.dp(10))
        LayoutInflater.from(context).inflate(R.layout.roomkit_caption_button, this, true)
        setOnClickListener {
            RoomToast.show(context, context.getString(R.string.roomkit_main_feature_coming))
        }
    }

    override fun setPressed(pressed: Boolean) {
        super.setPressed(pressed)
        val scale = if (pressed) PRESSED_SCALE else 1f
        animate().setDuration(PRESSED_DURATION.toLong()).scaleX(scale).scaleY(scale)
    }

    private companion object {
        const val PRESSED_SCALE = 0.96f
        const val PRESSED_DURATION = 80
    }
}
