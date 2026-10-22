package com.trtc.uikit.roomkit.inroom.features.setting

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.AppCompatTextView
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import com.trtc.uikit.roomkit.R
import com.trtc.uikit.roomkit.base.util.dp
import io.trtc.tuikit.atomicxcore.api.device.DeviceStore
import io.trtc.tuikit.atomicxcore.api.device.MirrorType
import io.trtc.tuikit.atomicxcore.api.device.VideoQuality
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class RoomSettingsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    interface NavigationHost {
        fun pushOptions(mode: RoomSettingsOptionsView.Mode)
        fun finishCurrent()
    }

    var navigationHost: NavigationHost? = null

    private val navBar: RoomNavigationBar
    private val videoGroupLabel: AppCompatTextView
    private val generalGroupLabel: AppCompatTextView
    private val videoSection: RoomSettingsSection
    private val generalSection: RoomSettingsSection

    private val mirrorRow = RoomSettingsRow.toggle(context)
    private val resolutionRow = RoomSettingsRow.navigation(context)

    private val deviceStore = DeviceStore.shared()
    private var observeJob: Job? = null

    init {
        LayoutInflater.from(context).inflate(R.layout.roomkit_settings_view, this, true)
        navBar = findViewById(R.id.settings_nav_bar)
        videoGroupLabel = findViewById(R.id.video_group_label)
        generalGroupLabel = findViewById(R.id.general_group_label)
        videoSection = findViewById(R.id.video_section)
        generalSection = findViewById(R.id.general_section)

        applyTexts()
        setupViews()
        setupBindings()
    }

    private fun setupViews() {
        navBar.title = context.getString(R.string.roomkit_settings_title)
        navBar.setOnBackClickListener { navigationHost?.finishCurrent() }

        resolutionRow.showsArrow = true

        videoSection.addRow(mirrorRow)
        videoSection.addRow(resolutionRow)

        generalGroupLabel.visibility = View.GONE
        generalSection.visibility = View.GONE
    }

    private fun setupBindings() {
        mirrorRow.onToggle = { isOn ->
            deviceStore.switchMirror(if (isOn) MirrorType.ENABLE else MirrorType.DISABLE)
        }
        resolutionRow.onTap = { navigationHost?.pushOptions(RoomSettingsOptionsView.Mode.RESOLUTION) }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        observeDeviceState()
    }

    override fun onDetachedFromWindow() {
        observeJob?.cancel()
        observeJob = null
        super.onDetachedFromWindow()
    }

    private fun observeDeviceState() {
        observeJob?.cancel()
        observeJob = CoroutineScope(Dispatchers.Main).launch {
            launch {
                deviceStore.deviceState.isFrontCamera.collect { isFrontCamera ->
                    applyMirror(isFrontCamera, deviceStore.deviceState.localMirrorType.value)
                }
            }
            launch {
                deviceStore.deviceState.localMirrorType.collect { mirrorType ->
                    applyMirror(deviceStore.deviceState.isFrontCamera.value, mirrorType)
                }
            }
            launch {
                deviceStore.deviceState.localVideoQuality.collect { quality ->
                    resolutionRow.value = context.getString(qualityDisplayName(quality))
                }
            }
        }
    }

    private fun applyMirror(isFrontCamera: Boolean, mirrorType: MirrorType) {
        mirrorRow.setEnabledState(isFrontCamera)
        mirrorRow.isOn = isFrontCamera && mirrorType == MirrorType.ENABLE
    }

    private fun applyTexts() {
        videoGroupLabel.text = context.getString(R.string.roomkit_settings_group_video)
        generalGroupLabel.text = context.getString(R.string.roomkit_settings_group_general)
        mirrorRow.title = context.getString(R.string.roomkit_settings_mirror)
        mirrorRow.desc = context.getString(R.string.roomkit_settings_mirror_desc)
        resolutionRow.title = context.getString(R.string.roomkit_settings_resolution)
    }

    private companion object {
        fun qualityDisplayName(quality: VideoQuality): Int = when (quality) {
            VideoQuality.QUALITY_1080P -> R.string.roomkit_settings_quality1080_p
            VideoQuality.QUALITY_720P -> R.string.roomkit_settings_quality720_p
            VideoQuality.QUALITY_540P -> R.string.roomkit_settings_quality540_p
            else -> R.string.roomkit_settings_quality360_p
        }
    }
}

class RoomSettingsRow private constructor(
    context: Context,
    private val style: Style,
) : FrameLayout(context) {

    enum class Style { NAVIGATION, TOGGLE }

    companion object {
        fun navigation(context: Context): RoomSettingsRow = RoomSettingsRow(context, Style.NAVIGATION)
        fun toggle(context: Context): RoomSettingsRow = RoomSettingsRow(context, Style.TOGGLE)
    }

    var title: CharSequence?
        get() = titleView.text
        set(value) { titleView.text = value }

    var value: CharSequence?
        get() = valueView?.text
        set(value) { valueView?.text = value }

    var desc: CharSequence?
        get() = descView?.text
        set(value) { descView?.text = value }

    var isOn: Boolean
        get() = toggleView?.isChecked ?: false
        set(value) { toggleView?.isChecked = value }

    var isChecked: Boolean
        get() = checkView?.visibility == View.VISIBLE
        set(value) { checkView?.visibility = if (value) View.VISIBLE else View.GONE }

    var showsArrow: Boolean
        get() = arrowView?.visibility == View.VISIBLE
        set(value) { arrowView?.visibility = if (value) View.VISIBLE else View.GONE }

    var onTap: (() -> Unit)? = null
    var onToggle: ((Boolean) -> Unit)? = null

    private val titleView: AppCompatTextView
    private val valueView: AppCompatTextView?
    private val descView: AppCompatTextView?
    private val arrowView: AppCompatImageView?
    private val checkView: AppCompatImageView?
    private val toggleView: SwitchCompat?

    init {
        val layout = if (style == Style.NAVIGATION) {
            R.layout.roomkit_settings_row_navigation
        } else {
            R.layout.roomkit_settings_row_toggle
        }
        LayoutInflater.from(context).inflate(layout, this, true)
        titleView = findViewById(R.id.row_title)
        valueView = findViewById(R.id.row_value)
        descView = findViewById(R.id.row_desc)
        arrowView = findViewById(R.id.row_arrow)
        checkView = findViewById(R.id.row_check)
        toggleView = findViewById(R.id.row_switch)

        if (style == Style.NAVIGATION) {
            setOnClickListener { onTap?.invoke() }
        }
        toggleView?.setOnCheckedChangeListener { _, isChecked -> onToggle?.invoke(isChecked) }
    }

    fun setEnabledState(enabled: Boolean) {
        alpha = if (enabled) 1f else 0.45f
        isEnabled = enabled
        toggleView?.isEnabled = enabled
    }
}

class RoomSettingsSection @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr) {

    init {
        orientation = VERTICAL
        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = context.dp(12).toFloat()
            setColor(ContextCompat.getColor(context, R.color.roomkit_bg_surface))
        }
        clipToOutline = true
    }

    fun addRow(row: RoomSettingsRow) {
        if (childCount > 0) {
            addView(
                View(context).apply {
                    layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, context.dp(1))
                    setBackgroundResource(R.drawable.roomkit_settings_row_divider)
                },
            )
        }
        addView(row, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
    }
}
