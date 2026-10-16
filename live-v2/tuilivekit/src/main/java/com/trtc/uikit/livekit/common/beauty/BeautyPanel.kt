package com.trtc.uikit.livekit.common.beauty

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.trtc.uikit.livekit.R
import com.trtc.uikit.livekit.common.theme.BottomSheetView
import com.trtc.uikit.livekit.common.theme.LiveSlider
import com.trtc.uikit.livekit.common.theme.LiveSwitch
import com.trtc.uikit.livekit.common.theme.LiveText
import com.trtc.uikit.livekit.common.theme.LiveTheme
import io.trtc.tuikit.atomicxcore.api.device.BaseBeautyStore
import androidx.core.content.edit
import io.trtc.tuikit.atomicxcore.api.login.LoginStore
import org.json.JSONObject
import kotlin.math.roundToInt

// Saved user choices are independent of SDK readback and shared by both camera entry points.
internal data class BeautyPreferences(
    val isEnabled: Boolean = true,
    val smooth: Float = 0f,
    val whiteness: Float = 0f,
    val ruddy: Float = 0f,
) {
    companion object {
        private const val PREFS_NAME = "livekit_beauty_preferences"

        val currentKey: String?
            get() {
                val userID = LoginStore.shared.loginState.loginUserInfo.value?.userID
                    ?.takeIf { it.isNotEmpty() } ?: return null
                return "v1.${LoginStore.shared.sdkAppID}.$userID"
            }

        fun load(context: Context, key: String?): BeautyPreferences {
            if (key == null) return BeautyPreferences()
            return runCatching {
                val data = preferences(context).getString(key, null)
                    ?: return BeautyPreferences()
                val json = JSONObject(data)
                BeautyPreferences(
                    isEnabled = json.getBoolean("isEnabled"),
                    smooth = validated(json.getDouble("smooth").toFloat()),
                    whiteness = validated(json.getDouble("whiteness").toFloat()),
                    ruddy = validated(json.getDouble("ruddy").toFloat()),
                )
            }.getOrElse { BeautyPreferences() }
        }

        fun restore(context: Context) = load(context, currentKey).applyToSdk()

        private fun preferences(context: Context) = context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        private fun validated(value: Float) = if (value.isFinite()) value.coerceIn(0f, 9f) else 0f
    }

    fun save(context: Context, key: String?) {
        if (key == null) return
        val data = JSONObject()
            .put("isEnabled", isEnabled)
            .put("smooth", smooth.toDouble())
            .put("whiteness", whiteness.toDouble())
            .put("ruddy", ruddy.toDouble())
            .toString()
        preferences(context).edit { putString(key, data) }
    }

    fun applyToSdk() {
        BaseBeautyStore.shared().setSmoothLevel(if (isEnabled) smooth else 0f)
        BaseBeautyStore.shared().setWhitenessLevel(if (isEnabled) whiteness else 0f)
        BaseBeautyStore.shared().setRuddyLevel(if (isEnabled) ruddy else 0f)
    }
}

internal class BeautyPanel(context: Context) : BottomSheetView(context) {
    enum class Kind(
        @DrawableRes val iconRes: Int,
        @StringRes val titleRes: Int,
    ) {
        SMOOTH(
            R.drawable.livekit_pusher_beauty_ic_smooth,
            R.string.common_beauty_item_smooth
        ),
        WHITENESS(
            R.drawable.livekit_pusher_beauty_ic_whiteness,
            R.string.common_beauty_item_whiteness
        ),
        RUDDY(
            R.drawable.livekit_pusher_beauty_ic_ruddy,
            R.string.common_beauty_item_ruddy
        ),
    }

    private val beautySwitch: LiveSwitch
    private val typeView: TextView
    private val valueView: TextView
    private val slider: LiveSlider
    private val options: Map<Kind, BeautyOptionControl>
    private var optionTitles: Map<Kind, Int> = emptyMap()
    private var selectedKind = Kind.SMOOTH
    private var settings = BeautyPreferences()
    private var preferencesKey: String? = null
    private var isActive = false

    override val sheetHeight: Int
        get() = context.resources.getDimensionPixelSize(R.dimen.livekit_pusher_settings_page_beauty_height)

    init {
        LayoutInflater.from(context).inflate(R.layout.livekit_beauty_panel, contentView, true)
        typeView = contentView.findViewById(R.id.livekit_pusher_beauty_type)
        valueView = contentView.findViewById(R.id.livekit_pusher_beauty_value)
        slider = contentView.findViewById(R.id.livekit_pusher_beauty_slider)
        beautySwitch = contentView.findViewById(R.id.livekit_beauty_switch)
        options = mapOf(
            Kind.SMOOTH to contentView.findViewById(R.id.livekit_pusher_beauty_option_smooth),
            Kind.WHITENESS to contentView.findViewById(R.id.livekit_pusher_beauty_option_whiteness),
            Kind.RUDDY to contentView.findViewById(R.id.livekit_pusher_beauty_option_ruddy),
        )
        options.forEach { (kind, view) -> view.bind(kind.iconRes, kind.titleRes) }
        sheetTitle = context.getString(R.string.common_video_settings_item_beauty)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        beautySwitch.onCheckedChange = ::setBeautyEnabled
        slider.onValueChanged = ::applySliderValue
        options.forEach { (kind, view) -> view.setOnClickListener { select(kind) } }
        updateEnabledAppearance()
    }

    override fun onDetachedFromWindow() {
        isActive = false
        beautySwitch.onCheckedChange = null
        slider.onValueChanged = null
        options.values.forEach { it.setOnClickListener(null) }
        super.onDetachedFromWindow()
    }

    fun setOptionTitles(titles: Map<Kind, Int>) {
        optionTitles = titles.toMap()
        options.forEach { (kind, view) -> view.bind(kind.iconRes, titles[kind] ?: kind.titleRes) }
        typeView.minWidth = context.resources.getDimensionPixelSize(R.dimen.livekit_pusher_beauty_type_label_width)
        typeView.layoutParams = typeView.layoutParams.apply { width = android.view.ViewGroup.LayoutParams.WRAP_CONTENT }
        updateSlider()
    }

    override fun sheetWillPresent() {
        preferencesKey = BeautyPreferences.currentKey
        settings = BeautyPreferences.load(context, preferencesKey)
        settings.applyToSdk()
        select(Kind.SMOOTH)
        updateEnabledAppearance()
        isActive = true
    }

    override fun sheetWillDismiss() {
        isActive = false
    }

    private val canEdit: Boolean
        get() = isActive && contentView.isAttachedToWindow &&
            preferencesKey == BeautyPreferences.currentKey

    private fun setBeautyEnabled(value: Boolean) {
        if (!canEdit || settings.isEnabled == value) return
        settings = settings.copy(isEnabled = value)
        settings.save(context, preferencesKey)
        settings.applyToSdk()
        updateEnabledAppearance()
    }

    private fun updateEnabledAppearance() {
        beautySwitch.setChecked(settings.isEnabled, animate = false)
        slider.isEnabled = settings.isEnabled
        slider.alpha = if (settings.isEnabled) 1f else 0.4f
        updateSlider()
    }

    private fun select(kind: Kind) {
        selectedKind = kind
        options.forEach { (value, view) -> view.isSelected = value == kind }
        updateSlider()
    }

    private fun updateSlider() {
        val level = when (selectedKind) {
            Kind.SMOOTH -> settings.smooth
            Kind.WHITENESS -> settings.whiteness
            Kind.RUDDY -> settings.ruddy
        }.roundToInt()
        typeView.setText(optionTitles[selectedKind] ?: selectedKind.titleRes)
        valueView.text = level.toString()
        slider.isVisible = true
        slider.setValue(level)
    }

    private fun applySliderValue(level: Int) {
        if (!settings.isEnabled || !canEdit) return
        val value = level.toFloat().coerceIn(0f, 9f)
        settings = when (selectedKind) {
            Kind.SMOOTH -> settings.copy(smooth = value)
            Kind.WHITENESS -> settings.copy(whiteness = value)
            Kind.RUDDY -> settings.copy(ruddy = value)
        }
        settings.save(context, preferencesKey)
        settings.applyToSdk()
        updateSlider()
    }
}

class BeautyOptionControl @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : ConstraintLayout(context, attrs, defStyleAttr) {

    private companion object {
        const val PRESS_SCALE = 0.94f
        const val PRESS_DURATION_MS = 120L
    }

    private val plateView: View
    private val iconView: ImageView
    private val labelView: LiveText

    private val iconColor = ContextCompat.getColor(context, R.color.livekit_pusher_beauty_icon)
    private val selectedColor =
        ContextCompat.getColor(context, R.color.common_design_standard_flowkit_white)

    init {
        LayoutInflater.from(context)
            .inflate(R.layout.livekit_beauty_option_view, this, true)
        plateView = findViewById(R.id.livekit_pusher_beauty_option_plate)
        iconView = findViewById(R.id.livekit_pusher_beauty_option_icon)
        labelView = findViewById(R.id.livekit_pusher_beauty_option_label)
        isClickable = true
        isFocusable = true
        applySelectionState()
    }

    fun bind(@DrawableRes iconRes: Int, @StringRes titleRes: Int) {
        iconView.setImageResource(iconRes)
        labelView.setText(titleRes)
        contentDescription = context.getString(titleRes)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(
            widthMeasureSpec,
            MeasureSpec.makeMeasureSpec(
                resources.getDimensionPixelSize(R.dimen.livekit_pusher_beauty_options_height),
                MeasureSpec.EXACTLY
            )
        )
    }

    override fun setSelected(selected: Boolean) {
        if (isSelected == selected) return
        super.setSelected(selected)
        applySelectionState()
    }

    override fun setPressed(pressed: Boolean) {
        if (isPressed == pressed) return
        super.setPressed(pressed)
        val scale = if (pressed) PRESS_SCALE else 1f
        plateView.animate()
            .scaleX(scale)
            .scaleY(scale)
            .setDuration(PRESS_DURATION_MS)
            .start()
    }

    private fun applySelectionState() {
        iconView.setColorFilter(if (isSelected) selectedColor else iconColor)
        labelView.tone = if (isSelected) {
            LiveTheme.TextTone.PRIMARY
        } else {
            LiveTheme.TextTone.SECONDARY
        }
    }
}
