package com.trtc.uikit.roomkit.inroom.views.meeting

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ProgressBar
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.content.ContextCompat
import com.trtc.uikit.roomkit.R

class RoomEnteringView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    private val spinner: ProgressBar
    private val textLabel: AppCompatTextView

    init {
        LayoutInflater.from(context).inflate(R.layout.roomkit_entering_view, this)
        spinner = findViewById(R.id.entering_spinner)
        textLabel = findViewById(R.id.entering_text)
        setBackgroundColor(ContextCompat.getColor(context, R.color.roomkit_bg_canvas))
        isClickable = true
        visibility = GONE
        alpha = 0f
    }

    fun setShown(shown: Boolean) {
        if (shown) {
            visibility = VISIBLE
            animate().alpha(1f).setDuration(FADE_MS).start()
        } else {
            animate().alpha(0f).setDuration(FADE_MS).withEndAction {
                visibility = GONE
                spinner.isIndeterminate = false
            }.start()
        }
    }

    private companion object {
        const val FADE_MS = 200L
    }
}
