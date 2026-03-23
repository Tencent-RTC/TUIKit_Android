package com.example.atomicxcore.components

import android.content.Context
import android.content.res.ColorStateList
import android.util.AttributeSet
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import com.example.atomicxcore.R
import com.google.android.material.button.MaterialButton
import io.trtc.tuikit.atomicxcore.api.device.AudioChangerType
import io.trtc.tuikit.atomicxcore.api.device.AudioEffectState
import io.trtc.tuikit.atomicxcore.api.device.AudioEffectStore
import io.trtc.tuikit.atomicxcore.api.device.AudioReverbType

/**
 * Audio effect settings panel component
 *
 * Related APIs:
 * - AudioEffectStore.shared - Get the audio effect manager singleton
 * - AudioEffectStore.setAudioChangerType(type) - Set voice changer effect
 * - AudioEffectStore.setAudioReverbType(type) - Set reverb effect
 * - AudioEffectStore.setVoiceEarMonitorEnable(enable) - Toggle ear monitoring
 * - AudioEffectStore.setVoiceEarMonitorVolume(volume) - Set ear monitoring volume
 * - AudioEffectStore.reset() - Reset all audio effect settings
 * - AudioEffectStore.state - Audio effect state (StateFlow)
 *
 * Features:
 * - Voice changer selection (horizontal scrolling tags)
 * - Reverb effect selection (horizontal scrolling tags)
 * - Ear monitoring toggle and volume control
 * - One-click reset
 */
class AudioEffectSettingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ScrollView(context, attrs, defStyleAttr) {

    private val changerTypes = listOf(
        Pair(AudioChangerType.NONE, context.getString(R.string.interactive_audioEffect_changer_none)),
        Pair(AudioChangerType.CHILD, context.getString(R.string.interactive_audioEffect_changer_child)),
        Pair(AudioChangerType.LITTLE_GIRL, context.getString(R.string.interactive_audioEffect_changer_littleGirl)),
        Pair(AudioChangerType.MAN, context.getString(R.string.interactive_audioEffect_changer_man)),
        Pair(AudioChangerType.ETHEREAL, context.getString(R.string.interactive_audioEffect_changer_ethereal))
    )

    private val reverbTypes = listOf(
        Pair(AudioReverbType.NONE, context.getString(R.string.interactive_audioEffect_reverb_none)),
        Pair(AudioReverbType.KTV, context.getString(R.string.interactive_audioEffect_reverb_ktv)),
        Pair(AudioReverbType.SMALL_ROOM, context.getString(R.string.interactive_audioEffect_reverb_smallRoom)),
        Pair(AudioReverbType.AUDITORIUM, context.getString(R.string.interactive_audioEffect_reverb_auditorium)),
        Pair(AudioReverbType.METALLIC, context.getString(R.string.interactive_audioEffect_reverb_metallic))
    )

    private val changerButtons = mutableListOf<MaterialButton>()
    private val reverbButtons = mutableListOf<MaterialButton>()
    private val earMonitorSwitch: Switch
    private val volumeSeekBar: SeekBar
    private val volumeValueText: TextView
    private var selectedChangerIndex = 0
    private var selectedReverbIndex = 0
    private var isUpdatingUI = false

    init {
        val dp16 = (16 * resources.displayMetrics.density).toInt()
        val dp12 = (12 * resources.displayMetrics.density).toInt()
        val dp8 = (8 * resources.displayMetrics.density).toInt()
        val dp52 = (52 * resources.displayMetrics.density).toInt()

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
            setPadding(0, dp8, 0, dp16)
        }

        // Voice changer effects
        createTagSection(
            container,
            context.getString(R.string.interactive_audioEffect_changer_title),
            changerTypes.map { it.second },
            changerButtons
        ) { index ->
            if (!isUpdatingUI) {
                selectedChangerIndex = index
                AudioEffectStore.shared().setAudioChangerType(changerTypes[index].first)
            }
        }
        addDivider(container)

        // Reverb effects
        createTagSection(
            container,
            context.getString(R.string.interactive_audioEffect_reverb_title),
            reverbTypes.map { it.second },
            reverbButtons
        ) { index ->
            if (!isUpdatingUI) {
                selectedReverbIndex = index
                AudioEffectStore.shared().setAudioReverbType(reverbTypes[index].first)
            }
        }
        addDivider(container)

        // Ear monitoring toggle
        val earMonitorRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp52
            )
            setPadding(dp16, 0, dp16, 0)
        }
        val earMonitorLabel = TextView(context).apply {
            text = context.getString(R.string.interactive_audioEffect_earMonitor)
            textSize = 16f
        }
        earMonitorSwitch = Switch(context)
        earMonitorRow.addView(earMonitorLabel, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        earMonitorRow.addView(earMonitorSwitch)
        container.addView(earMonitorRow)
        addDivider(container)

        // Ear monitoring volume
        val volumeRow = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setPadding(dp16, dp12, dp16, dp12)
        }
        val volumeHeader = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        val volumeTitle = TextView(context).apply {
            text = context.getString(R.string.interactive_audioEffect_earMonitorVolume)
            textSize = 16f
        }
        volumeValueText = TextView(context).apply {
            text = "0"
            textSize = 14f
            gravity = Gravity.END
        }
        volumeHeader.addView(volumeTitle, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        volumeHeader.addView(volumeValueText)
        volumeRow.addView(volumeHeader)

        volumeSeekBar = SeekBar(context).apply {
            max = 100
            progress = 0
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp8 }
        }
        volumeRow.addView(volumeSeekBar)
        container.addView(volumeRow)
        addDivider(container)

        // Reset button
        val resetBtn = Button(context).apply {
            text = context.getString(R.string.interactive_audioEffect_reset)
            setTextColor(android.graphics.Color.parseColor("#F44336"))
            background = null
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = dp12
            }
            setOnClickListener { AudioEffectStore.shared().reset() }
        }
        container.addView(resetBtn)

        addView(container)

        setupActions()
    }

    private fun createTagSection(
        parent: LinearLayout,
        title: String,
        tags: List<String>,
        buttonList: MutableList<MaterialButton>,
        onSelect: (Int) -> Unit
    ) {
        val dp16 = (16 * resources.displayMetrics.density).toInt()
        val dp12 = (12 * resources.displayMetrics.density).toInt()
        val dp8 = (8 * resources.displayMetrics.density).toInt()

        val section = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setPadding(dp16, dp12, dp16, dp12)
        }

        val titleLabel = TextView(context).apply {
            text = title
            textSize = 14f
            setTextColor(android.graphics.Color.GRAY)
        }
        section.addView(titleLabel)

        // Use custom FlowLayout for automatic line wrapping
        val flowLayout = FlowLayout(context, dp8, dp8).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp8 }
        }

        val dp36 = (36 * resources.displayMetrics.density).toInt()
        tags.forEachIndexed { index, tag ->
            val btn = MaterialButton(context, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                text = tag
                textSize = 13f
                cornerRadius = (18 * resources.displayMetrics.density).toInt()
                insetTop = 0
                insetBottom = 0
                minHeight = dp36
                minimumHeight = dp36
                minWidth = 0
                minimumWidth = 0
                setPadding(dp16, 0, dp16, 0)
                layoutParams = ViewGroup.MarginLayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, dp36
                )
                setOnClickListener {
                    val selected = buttonList.indexOf(this)
                    updateTagSelection(buttonList, selected)
                    onSelect(selected)
                }
            }
            buttonList.add(btn)
            flowLayout.addView(btn)
        }

        // Select the first item by default
        if (buttonList.isNotEmpty()) {
            updateTagSelection(buttonList, 0)
        }

        section.addView(flowLayout)
        parent.addView(section)
    }

    private fun updateTagSelection(buttons: List<MaterialButton>, selectedIndex: Int) {
        val dp1 = (1 * resources.displayMetrics.density).toInt()
        buttons.forEachIndexed { index, btn ->
            if (index == selectedIndex) {
                btn.backgroundTintList = ColorStateList.valueOf(
                    android.graphics.Color.parseColor("#2196F3")
                )
                btn.setTextColor(android.graphics.Color.WHITE)
                btn.strokeWidth = 0
            } else {
                btn.backgroundTintList = ColorStateList.valueOf(android.graphics.Color.TRANSPARENT)
                btn.setTextColor(android.graphics.Color.DKGRAY)
                btn.strokeColor = ColorStateList.valueOf(
                    android.graphics.Color.parseColor("#E0E0E0")
                )
                btn.strokeWidth = dp1
            }
        }
    }

    /**
     * Simple FlowLayout with automatic line wrapping for child Views
     */
    private class FlowLayout(
        context: Context,
        private val hSpacing: Int,
        private val vSpacing: Int
    ) : ViewGroup(context) {

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val maxWidth = MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight
            var lineWidth = 0
            var lineHeight = 0
            var totalHeight = 0

            for (i in 0 until childCount) {
                val child = getChildAt(i)
                if (child.visibility == GONE) continue
                measureChild(child, widthMeasureSpec, heightMeasureSpec)
                val childWidth = child.measuredWidth
                val childHeight = child.measuredHeight

                if (lineWidth + childWidth > maxWidth && lineWidth > 0) {
                    // Line break
                    totalHeight += lineHeight + vSpacing
                    lineWidth = childWidth + hSpacing
                    lineHeight = childHeight
                } else {
                    lineWidth += childWidth + hSpacing
                    lineHeight = maxOf(lineHeight, childHeight)
                }
            }
            totalHeight += lineHeight // 最后一行

            setMeasuredDimension(
                MeasureSpec.getSize(widthMeasureSpec),
                totalHeight + paddingTop + paddingBottom
            )
        }

        override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
            val maxWidth = r - l - paddingLeft - paddingRight
            var x = paddingLeft
            var y = paddingTop
            var lineHeight = 0

            for (i in 0 until childCount) {
                val child = getChildAt(i)
                if (child.visibility == GONE) continue
                val childWidth = child.measuredWidth
                val childHeight = child.measuredHeight

                if (x + childWidth > maxWidth + paddingLeft && x > paddingLeft) {
                    // Line break
                    y += lineHeight + vSpacing
                    x = paddingLeft
                    lineHeight = 0
                }

                child.layout(x, y, x + childWidth, y + childHeight)
                x += childWidth + hSpacing
                lineHeight = maxOf(lineHeight, childHeight)
            }
        }
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

    private fun setupActions() {
        earMonitorSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (!isUpdatingUI) {
                AudioEffectStore.shared().setVoiceEarMonitorEnable(isChecked)
            }
        }

        volumeSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                volumeValueText.text = "$progress"
                if (fromUser && !isUpdatingUI) {
                    AudioEffectStore.shared().setVoiceEarMonitorVolume(progress)
                }
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })
    }

    /**
     * Update UI from AudioEffectStore.state
     */
    fun updateFromState(state: AudioEffectState) {
        isUpdatingUI = true
        earMonitorSwitch.isChecked = state.isEarMonitorOpened.value
        volumeSeekBar.progress = state.earMonitorVolume.value
        volumeValueText.text = "${state.earMonitorVolume.value}"

        // Update voice changer selection
        val changerIndex = changerTypes.indexOfFirst { it.first == state.audioChangerType.value }
        if (changerIndex >= 0 && changerIndex != selectedChangerIndex) {
            selectedChangerIndex = changerIndex
            updateTagSelection(changerButtons, changerIndex)
        }

        // Update reverb selection
        val reverbIndex = reverbTypes.indexOfFirst { it.first == state.audioReverbType.value }
        if (reverbIndex >= 0 && reverbIndex != selectedReverbIndex) {
            selectedReverbIndex = reverbIndex
            updateTagSelection(reverbButtons, reverbIndex)
        }
        isUpdatingUI = false
    }
}
