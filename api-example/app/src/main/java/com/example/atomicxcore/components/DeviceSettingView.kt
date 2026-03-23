package com.example.atomicxcore.components

import android.content.Context
import android.content.res.ColorStateList
import android.util.AttributeSet
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import com.example.atomicxcore.R
import com.google.android.material.button.MaterialButton
import io.trtc.tuikit.atomicxcore.api.device.DeviceState
import io.trtc.tuikit.atomicxcore.api.device.DeviceStatus
import io.trtc.tuikit.atomicxcore.api.device.DeviceStore
import io.trtc.tuikit.atomicxcore.api.device.MirrorType
import io.trtc.tuikit.atomicxcore.api.device.VideoQuality

/**
 * Device management panel content - reusable component
 *
 * Related APIs:
 * - DeviceStore.shared().openLocalCamera(isFront, completion) - Open camera
 * - DeviceStore.shared().closeLocalCamera() - Close camera
 * - DeviceStore.shared().openLocalMicrophone(completion) - Open microphone
 * - DeviceStore.shared().closeLocalMicrophone() - Close microphone
 * - DeviceStore.shared().switchCamera(isFront) - Switch front/rear camera
 * - DeviceStore.shared().switchMirror(mirrorType) - Set mirror mode
 * - DeviceStore.shared().updateVideoQuality(quality) - Set video quality
 * - DeviceStore.shared().state - Device state (StateFlow)
 *
 * A pure UI component that only depends on the DeviceStore public API, not coupled to any specific business scenario.
 */
class DeviceSettingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ScrollView(context, attrs, defStyleAttr) {

    private val cameraSwitch: Switch
    private val microphoneSwitch: Switch
    private val frontCameraSwitch: Switch
    private val mirrorSwitch: Switch
    private val qualityLabels = arrayOf("360P", "540P", "720P", "1080P")
    private val qualities = arrayOf(
        VideoQuality.QUALITY_360P,
        VideoQuality.QUALITY_540P,
        VideoQuality.QUALITY_720P,
        VideoQuality.QUALITY_1080P
    )
    private var qualityIndex = 2 // 默认 720P
    private val qualityButtons = mutableListOf<MaterialButton>()
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

        // Camera toggle
        cameraSwitch = createSwitchRow(container, context.getString(R.string.deviceSetting_camera))
        addDivider(container)

        // Microphone toggle
        microphoneSwitch = createSwitchRow(container, context.getString(R.string.deviceSetting_microphone))
        addDivider(container)

        // Front camera toggle
        frontCameraSwitch = createSwitchRow(container, context.getString(R.string.deviceSetting_frontCamera))
        addDivider(container)

        // Mirror mode toggle
        mirrorSwitch = createSwitchRow(container, context.getString(R.string.deviceSetting_mirror))
        addDivider(container)

        // Video quality - segmented buttons
        val qualityRow = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setPadding(dp16, dp12, dp16, dp12)
        }
        val qualityTitle = TextView(context).apply {
            text = context.getString(R.string.deviceSetting_videoQuality)
            textSize = 16f
        }
        qualityRow.addView(qualityTitle)

        val buttonRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp8 }
        }
        val dp36 = (36 * resources.displayMetrics.density).toInt()
        qualityLabels.forEachIndexed { index, label ->
            val btn = MaterialButton(context, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                text = label
                textSize = 13f
                cornerRadius = (18 * resources.displayMetrics.density).toInt()
                insetTop = 0
                insetBottom = 0
                minHeight = dp36
                minimumHeight = dp36
                setPadding(0, 0, 0, 0)
                layoutParams = LinearLayout.LayoutParams(0, dp36, 1f).apply {
                    if (index > 0) marginStart = dp8
                }
                setOnClickListener {
                    if (!isUpdatingUI) {
                        qualityIndex = index
                        updateQualitySelection()
                        DeviceStore.shared().updateVideoQuality(qualities[index])
                    }
                }
            }
            qualityButtons.add(btn)
            buttonRow.addView(btn)
        }
        qualityRow.addView(buttonRow)
        updateQualitySelection()
        container.addView(qualityRow)

        addView(container)

        setupActions()
    }

    private fun createSwitchRow(parent: LinearLayout, title: String): Switch {
        val dp16 = (16 * resources.displayMetrics.density).toInt()
        val dp52 = (52 * resources.displayMetrics.density).toInt()
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp52
            )
            setPadding(dp16, 0, dp16, 0)
        }
        val label = TextView(context).apply {
            text = title
            textSize = 16f
        }
        val switch = Switch(context)
        row.addView(label, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(switch, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        parent.addView(row)
        return switch
    }

    private fun addDivider(parent: LinearLayout) {
        val dp56 = (56 * resources.displayMetrics.density).toInt()
        val dp16 = (16 * resources.displayMetrics.density).toInt()
        val divider = android.view.View(context).apply {
            setBackgroundColor(android.graphics.Color.parseColor("#E0E0E0"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1
            ).apply {
                marginStart = dp56
                marginEnd = dp16
            }
        }
        parent.addView(divider)
    }

    private fun setupActions() {
        cameraSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isUpdatingUI) return@setOnCheckedChangeListener
            if (isChecked) {
                DeviceStore.shared().openLocalCamera(
                    DeviceStore.shared().deviceState.isFrontCamera.value, null
                )
            } else {
                DeviceStore.shared().closeLocalCamera()
            }
        }

        microphoneSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isUpdatingUI) return@setOnCheckedChangeListener
            if (isChecked) {
                DeviceStore.shared().openLocalMicrophone(null)
            } else {
                DeviceStore.shared().closeLocalMicrophone()
            }
        }

        frontCameraSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isUpdatingUI) return@setOnCheckedChangeListener
            DeviceStore.shared().switchCamera(isChecked)
        }

        mirrorSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isUpdatingUI) return@setOnCheckedChangeListener
            val mirrorType = if (isChecked) MirrorType.ENABLE else MirrorType.DISABLE
            DeviceStore.shared().switchMirror(mirrorType)
        }
    }

    /**
     * Update UI state from DeviceStore.state
     * Called in Activity via lifecycleScope + StateFlow.collect
     */
    fun updateFromState(state: DeviceState) {
        isUpdatingUI = true
        cameraSwitch.isChecked = state.cameraStatus.value == DeviceStatus.ON
        microphoneSwitch.isChecked = state.microphoneStatus.value == DeviceStatus.ON
        frontCameraSwitch.isChecked = state.isFrontCamera.value
        mirrorSwitch.isChecked = state.localMirrorType.value == MirrorType.ENABLE
        isUpdatingUI = false
    }

    private fun updateQualitySelection() {
        qualityButtons.forEachIndexed { index, btn ->
            if (index == qualityIndex) {
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
                btn.strokeWidth = (1 * resources.displayMetrics.density).toInt()
            }
        }
    }
}
