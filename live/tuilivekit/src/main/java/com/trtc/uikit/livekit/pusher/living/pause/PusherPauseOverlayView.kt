package com.trtc.uikit.livekit.pusher.living.pause

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import com.trtc.uikit.livekit.R
import java.util.Locale

/** Full-screen paused state of the host Living stage. */
class PusherPauseOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {
    var onResume: (() -> Unit)? = null

    private val content: FrameLayout
    private val timer: TextView
    private val resume: TextView
    private val resumeBottom = dp(42)
    private var renderedSeconds: Int? = null

    init {
        LayoutInflater.from(context).inflate(R.layout.livekit_pause_overlay_view, this, true)
        isVisible = false
        isClickable = true
        isFocusable = true
        content = findViewById(R.id.livekit_pause_overlay_content)
        timer = findViewById(R.id.livekit_pause_overlay_timer)
        resume = findViewById(R.id.livekit_pause_overlay_resume)
        resume.setOnClickListener { onResume?.invoke() }
        ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
            val navigation = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            resume.updateLayoutParams<MarginLayoutParams> {
                bottomMargin = resumeBottom + navigation.bottom
            }
            insets
        }
    }

    fun bind(isPaused: Boolean, seconds: Int) {
        isVisible = isPaused
        if (!isPaused || renderedSeconds == seconds) return
        renderedSeconds = seconds
        timer.text = String.format(Locale.US, "%02d:%02d", seconds / 60, seconds % 60)
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        content.updateLayoutParams<MarginLayoutParams> {
            topMargin = height * 273 / 844
        }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
