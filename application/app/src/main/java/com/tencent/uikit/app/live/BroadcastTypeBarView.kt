package com.tencent.uikit.app.live

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.tencent.uikit.app.R
import io.trtc.tuikit.atomicx.common.event.EventManager

class BroadcastTypeBarView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {
    private var owner = ""
    private var voice = false
    private val row: LinearLayout
    private val buttons: List<TextView>

    init {
        inflate(context, R.layout.app_broadcast_type_bar, this)
        row = findViewById(R.id.home_broadcast_row)
        buttons = listOf(R.id.home_broadcast_video, R.id.home_broadcast_voice, R.id.home_broadcast_game).map {
            findViewById(
                it
            )
        }
        buttons.forEachIndexed { index, button ->
            button.setOnClickListener {
                EventManager.instance.notifyEvent(
                    "app.home.live", "broadcast", mapOf("owner" to owner, "type" to index)
                )
            }
        }
        updateSelection(false)
    }

    fun bind(ownerID: String) {
        owner = ownerID
    }

    fun updateSelection(isVoice: Boolean) {
        voice = isVoice
        buttons.forEachIndexed { index, button ->
            button.isSelected = index == if (voice) 1 else 0
            button.setTextColor(
                ContextCompat.getColor(
                    context, if (button.isSelected) R.color.home_white else R.color.home_broadcast_inactive
                )
            )
            button.setTypeface(
                null, if (button.isSelected) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL
            )
        }
        requestLayout()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        row.layoutParams = row.layoutParams.apply { width = minOf(w, dp(232)) }
        listOf(R.id.home_broadcast_gap1, R.id.home_broadcast_gap2).forEach {
            findViewById<View>(it).layoutParams =
                findViewById<View>(it).layoutParams.apply { width = if (w >= dp(232)) dp(20) else dp(8) }
        }
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)
        val button = buttons[if (voice) 1 else 0]
        findViewById<View>(R.id.home_broadcast_indicator).translationX =
            row.left + button.left + button.width / 2f - width / 2f
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
