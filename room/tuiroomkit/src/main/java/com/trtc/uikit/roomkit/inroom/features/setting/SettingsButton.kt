package com.trtc.uikit.roomkit.inroom.features.setting

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.view.isVisible
import com.trtc.uikit.roomkit.R
import com.trtc.uikit.roomkit.inroom.views.TitleDisplaying

class SettingsButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr), TitleDisplaying {

    var onRequestDismiss: (() -> Unit)? = null

    override var showsTitle: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            labelView.isVisible = value
        }

    private val labelView: AppCompatTextView

    init {
        LayoutInflater.from(context).inflate(R.layout.roomkit_settings_button, this, true)
        labelView = findViewById(R.id.label)
        setOnClickListener {
            onRequestDismiss?.invoke()
            RoomSettingsDialog(context).show()
        }
    }

    override fun setPressed(pressed: Boolean) {
        super.setPressed(pressed)
        val scale = if (pressed) PRESSED_SCALE else 1f
        animate().setDuration(PRESSED_DURATION.toLong()).scaleX(scale).scaleY(scale)
    }

    private companion object {
        const val PRESSED_SCALE = 0.92f
        const val PRESSED_DURATION = 80
    }
}
