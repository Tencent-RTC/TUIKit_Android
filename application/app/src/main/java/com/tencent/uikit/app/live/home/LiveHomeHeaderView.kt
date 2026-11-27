package com.tencent.uikit.app.live.home

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.tencent.uikit.app.R
import io.trtc.tuikit.atomicx.common.event.EventManager

class LiveHomeHeaderView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {
    private var owner = ""
    private val grid: TextView
    private val preview: TextView
    private val start: View

    init {
        inflate(context, R.layout.app_live_home_header, this)
        grid = findViewById(R.id.home_mode_grid)
        preview = findViewById(R.id.home_mode_preview)
        start = findViewById(R.id.home_start)
        grid.setOnClickListener { send("grid") }
        preview.setOnClickListener { send("preview") }
        start.setOnClickListener { send("start") }
        updateMode(false)
    }

    fun bind(ownerID: String) {
        owner = ownerID
    }

    fun updateMode(isPreview: Boolean) {
        setBackgroundColor(
            ContextCompat.getColor(context, if (isPreview) android.R.color.transparent else R.color.home_dark)
        )
        val modeBackground = android.graphics.drawable.GradientDrawable().apply {
            cornerRadius = 11 * resources.displayMetrics.density
            setColor(
                ContextCompat.getColor(context, if (isPreview) R.color.home_preview_selector else R.color.home_selector)
            )
            if (isPreview) setStroke(dp(1), ContextCompat.getColor(context, R.color.home_preview_border))
        }
        findViewById<View>(R.id.home_modes).background =
            android.graphics.drawable.InsetDrawable(modeBackground, 0, dp(4), 0, dp(4))
        findViewById<View>(R.id.home_modes).setPadding(dp(3), 0, dp(3), 0)
        start.background = android.graphics.drawable.InsetDrawable(android.graphics.drawable.GradientDrawable().apply {
            cornerRadius = dp(10).toFloat()
            setColor(
                ContextCompat.getColor(context, if (isPreview) R.color.home_preview_start else R.color.home_start_fill)
            )
            setStroke(dp(1), ContextCompat.getColor(context, R.color.home_start_border))
        }, dp(4))
        listOf(grid, preview).forEach { item ->
            item.background = android.graphics.drawable.StateListDrawable().apply {
                addState(
                    intArrayOf(android.R.attr.state_selected),
                    android.graphics.drawable.InsetDrawable(android.graphics.drawable.GradientDrawable().apply {
                        cornerRadius = 8 * resources.displayMetrics.density
                        setColor(
                            ContextCompat.getColor(
                                context, if (isPreview) R.color.home_preview_selection else R.color.home_selection
                            )
                        )
                    }, 0, dp(7), 0, dp(7))
                )
            }
        }
        grid.isSelected = !isPreview
        preview.isSelected = isPreview
        listOf(grid, preview).forEach {
            it.setTypeface(
                null, if (it.isSelected) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL
            )
            it.setTextColor(
                ContextCompat.getColor(
                    context,
                    if (it.isSelected) R.color.home_white else if (isPreview) R.color.home_preview_inactive else R.color.home_header_inactive
                )
            )
        }
    }

    fun updateInteractionEnabled(enabled: Boolean) {
        listOf(grid, preview, start).forEach { it.isEnabled = enabled; it.alpha = if (enabled) 1f else .55f }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val d = resources.displayMetrics.density
        val label = findViewById<TextView>(R.id.home_start_label)
        val startWidth =
            (kotlin.math.ceil(label.paint.measureText(label.text.toString()) / d) + 52f).coerceIn(80f, 100f)
        if (start.layoutParams.width != (startWidth * d).toInt()) start.layoutParams =
            start.layoutParams.apply { width = (startWidth * d).toInt() }
        findViewById<View>(R.id.home_modes).let {
            it.layoutParams = it.layoutParams.apply { width = ((w / d - 248f).coerceIn(100f, 132f) * d).toInt() }
        }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun send(action: String) {
        EventManager.instance.notifyEvent("app.home.live", "header", mapOf("owner" to owner, "action" to action))
    }
}
