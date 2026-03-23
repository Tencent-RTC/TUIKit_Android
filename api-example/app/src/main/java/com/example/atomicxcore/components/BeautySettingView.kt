package com.example.atomicxcore.components

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import com.example.atomicxcore.R
import io.trtc.tuikit.atomicxcore.api.device.BaseBeautyState
import io.trtc.tuikit.atomicxcore.api.device.BaseBeautyStore

/**
 * Beauty settings panel component
 *
 * Related APIs:
 * - BaseBeautyStore.shared - Get the beauty manager singleton
 * - BaseBeautyStore.setSmoothLevel(smoothLevel) - Set smooth level [0-9]
 * - BaseBeautyStore.setWhitenessLevel(whitenessLevel) - Set whiteness level [0-9]
 * - BaseBeautyStore.setRuddyLevel(ruddyLevel) - Set ruddy level [0-9]
 * - BaseBeautyStore.reset() - Reset all beauty parameters
 * - BaseBeautyStore.state - Beauty state (StateFlow)
 *
 * Features:
 * - Three sliders for smooth, whiteness, and ruddy adjustments
 * - Real-time beauty effect preview
 * - One-click reset for all beauty parameters
 */
class BeautySettingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ScrollView(context, attrs, defStyleAttr) {

    private val smoothSeekBar: SeekBar
    private val smoothValueText: TextView
    private val whitenessSeekBar: SeekBar
    private val whitenessValueText: TextView
    private val ruddySeekBar: SeekBar
    private val ruddyValueText: TextView
    private var isUpdatingUI = false

    init {
        val dp16 = (16 * resources.displayMetrics.density).toInt()
        val dp12 = (12 * resources.displayMetrics.density).toInt()
        val dp8 = (8 * resources.displayMetrics.density).toInt()

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
            setPadding(0, dp8, 0, dp16)
        }

        // Smooth
        val (smoothSb, smoothVal) = createSliderRow(
            container,
            context.getString(R.string.interactive_beauty_smooth),
            9
        ) { value ->
            if (!isUpdatingUI) BaseBeautyStore.shared().setSmoothLevel(value.toFloat())
        }
        smoothSeekBar = smoothSb
        smoothValueText = smoothVal
        addDivider(container)

        // Whiteness
        val (whitenessSb, whitenessVal) = createSliderRow(
            container,
            context.getString(R.string.interactive_beauty_whiteness),
            9
        ) { value ->
            if (!isUpdatingUI) BaseBeautyStore.shared().setWhitenessLevel(value.toFloat())
        }
        whitenessSeekBar = whitenessSb
        whitenessValueText = whitenessVal
        addDivider(container)

        // Ruddy
        val (ruddySb, ruddyVal) = createSliderRow(
            container,
            context.getString(R.string.interactive_beauty_ruddy),
            9
        ) { value ->
            if (!isUpdatingUI) BaseBeautyStore.shared().setRuddyLevel(value.toFloat())
        }
        ruddySeekBar = ruddySb
        ruddyValueText = ruddyVal
        addDivider(container)

        // Reset button
        val resetBtn = Button(context).apply {
            text = context.getString(R.string.interactive_beauty_reset)
            setTextColor(android.graphics.Color.parseColor("#F44336"))
            background = null
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = dp12
            }
            setOnClickListener { BaseBeautyStore.shared().reset() }
        }
        container.addView(resetBtn)

        addView(container)
    }

    private fun createSliderRow(
        parent: LinearLayout,
        title: String,
        max: Int,
        onChanged: (Int) -> Unit
    ): Pair<SeekBar, TextView> {
        val dp16 = (16 * resources.displayMetrics.density).toInt()
        val dp12 = (12 * resources.displayMetrics.density).toInt()
        val dp8 = (8 * resources.displayMetrics.density).toInt()

        val row = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setPadding(dp16, dp12, dp16, dp12)
        }

        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        val titleLabel = TextView(context).apply {
            text = title
            textSize = 16f
        }
        val valueLabel = TextView(context).apply {
            text = "0"
            textSize = 14f
            gravity = Gravity.END
        }
        header.addView(titleLabel, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(valueLabel, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        row.addView(header)

        val seekBar = SeekBar(context).apply {
            this.max = max
            progress = 0
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp8 }
        }
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                valueLabel.text = "$progress"
                if (fromUser) onChanged(progress)
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })
        row.addView(seekBar)
        parent.addView(row)

        return Pair(seekBar, valueLabel)
    }

    private fun addDivider(parent: LinearLayout) {
        val dp16 = (16 * resources.displayMetrics.density).toInt()
        val divider = android.view.View(context).apply {
            setBackgroundColor(android.graphics.Color.parseColor("#E0E0E0"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1
            ).apply {
                marginStart = dp16
                marginEnd = dp16
            }
        }
        parent.addView(divider)
    }

    /**
     * Update UI from BaseBeautyStore.state
     */
    fun updateFromState(state: BaseBeautyState) {
        isUpdatingUI = true
        smoothSeekBar.progress = state.smoothLevel.value.toInt()
        smoothValueText.text = "${state.smoothLevel.value.toInt()}"
        whitenessSeekBar.progress = state.whitenessLevel.value.toInt()
        whitenessValueText.text = "${state.whitenessLevel.value.toInt()}"
        ruddySeekBar.progress = state.ruddyLevel.value.toInt()
        ruddyValueText.text = "${state.ruddyLevel.value.toInt()}"
        isUpdatingUI = false
    }
}
