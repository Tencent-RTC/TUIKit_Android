package com.trtc.uikit.livekit.common.beauty

import android.content.Context
import android.graphics.Canvas
import android.graphics.drawable.ColorDrawable
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.findViewTreeLifecycleOwner
import com.tencent.qcloud.tuicore.TUICore
import com.tencent.qcloud.tuicore.interfaces.ITUINotification
import com.tencent.trtc.TRTCCloud
import com.tencent.trtc.TRTCCloudDef
import com.tencent.trtc.TRTCCloudListener
import io.trtc.tuikit.atomicxcore.api.device.DeviceStore
import io.trtc.tuikit.atomicxcore.api.device.DeviceStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.WeakHashMap
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.trtc.uikit.livekit.common.theme.LiveToast
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
                val userID =
                    LoginStore.shared.loginState.loginUserInfo.value?.userID?.takeIf { it.isNotEmpty() } ?: return null
                return "v1.${LoginStore.shared.sdkAppID}.$userID"
            }

        fun load(context: Context, key: String?): BeautyPreferences {
            if (key == null) return BeautyPreferences()
            return runCatching {
                val data = preferences(context).getString(key, null) ?: return BeautyPreferences()
                val json = JSONObject(data)
                BeautyPreferences(
                    isEnabled = json.getBoolean("isEnabled"),
                    smooth = validated(json.getDouble("smooth").toFloat()),
                    whiteness = validated(json.getDouble("whiteness").toFloat()),
                    ruddy = validated(json.getDouble("ruddy").toFloat()),
                )
            }.getOrElse { BeautyPreferences() }
        }

        fun restore(context: Context) {
            AdvancedBeautySession.refresh()
            if (!AdvancedBeautySession.isProcessing) load(context, currentKey).applyToSdk()
        }

        private fun preferences(context: Context) =
            context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        private fun validated(value: Float) = if (value.isFinite()) value.coerceIn(0f, 9f) else 0f
    }

    fun save(context: Context, key: String?) {
        if (key == null) return
        val data = JSONObject().put("isEnabled", isEnabled).put("smooth", smooth.toDouble())
            .put("whiteness", whiteness.toDouble()).put("ruddy", ruddy.toDouble()).toString()
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
            R.drawable.livekit_pusher_beauty_ic_smooth, R.string.common_beauty_item_smooth
        ),
        WHITENESS(
            R.drawable.livekit_pusher_beauty_ic_whiteness, R.string.common_beauty_item_whiteness
        ),
        RUDDY(
            R.drawable.livekit_pusher_beauty_ic_ruddy, R.string.common_beauty_item_ruddy
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
    private var panelScope: CoroutineScope? = null
    private var advancedPanel: View? = null
    private var navigationInset = 0
    private val safeAreaBackground = BeautySafeAreaBackground(
        ContextCompat.getColor(context, R.color.livekit_beauty_panel_background),
    )
    internal var onDidDismiss: (() -> Unit)? = null

    override val dimsBackground = false

    override val sheetHeight: Int
        get() = if (advancedPanel != null) ViewGroup.LayoutParams.WRAP_CONTENT
        else context.resources.getDimensionPixelSize(R.dimen.livekit_pusher_settings_page_beauty_height)

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
        ViewCompat.setOnApplyWindowInsetsListener(contentView) { _, insets ->
            navigationInset = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            updatePanelInsets()
            insets
        }
        ViewCompat.requestApplyInsets(contentView)
        panelScope?.cancel()
        panelScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).also { current ->
            current.launch { AdvancedBeautySession.changes.collect { if (isActive) updateBeautyContent() } }
        }
    }

    override fun onDetachedFromWindow() {
        AdvancedBeautySession.save()
        isActive = false
        panelScope?.cancel()
        panelScope = null
        ViewCompat.setOnApplyWindowInsetsListener(contentView, null)
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
        if (!AdvancedBeautySession.isProcessing) settings.applyToSdk()
        select(Kind.SMOOTH)
        updateEnabledAppearance()
        isActive = true
        AdvancedBeautySession.refresh()
        updateBeautyContent()
        if (advancedPanel == null && TUICore.getService("TEBeautyExtension") != null) {
            LiveToast.show(context, context.getString(R.string.livekit_beauty_advanced_unavailable))
        }
    }

    private fun updateBeautyContent() {
        val panel = AdvancedBeautySession.getPanel(context)
        if (advancedPanel !== panel) {
            advancedPanel?.let { (it.parent as? ViewGroup)?.removeView(it) }
            advancedPanel = panel
            if (panel != null) {
                (panel.parent as? ViewGroup)?.removeView(panel)
                contentView.addView(panel, BeautyLayoutParams.sdkPanel())
            }
        }
        contentView.findViewById<View>(R.id.livekit_beauty_basic_content).isVisible = panel == null
        contentView.showsDecoration = panel == null
        sheetTitle = if (panel == null) context.getString(R.string.common_video_settings_item_beauty) else null
        updatePanelInsets()
        updateSheetHeight(animated = false)
    }

    private fun updatePanelInsets() {
        val bottomInset = if (advancedPanel != null) navigationInset else 0
        safeAreaBackground.bottomInset = bottomInset
        contentView.background = if (advancedPanel != null) safeAreaBackground else null
        contentView.setPadding(0, 0, 0, bottomInset)
    }

    private object BeautyLayoutParams {
        fun sdkPanel() =
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    override fun sheetDidDismiss() {
        onDidDismiss?.invoke()
    }

    override fun sheetWillDismiss() {
        AdvancedBeautySession.save()
        isActive = false
    }

    private val canEdit: Boolean
        get() = isActive && advancedPanel == null && contentView.isAttachedToWindow && preferencesKey == BeautyPreferences.currentKey

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
    private val selectedColor = ContextCompat.getColor(context, R.color.common_design_standard_flowkit_white)

    init {
        LayoutInflater.from(context).inflate(R.layout.livekit_beauty_option_view, this, true)
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
            widthMeasureSpec, MeasureSpec.makeMeasureSpec(
                resources.getDimensionPixelSize(R.dimen.livekit_pusher_beauty_options_height), MeasureSpec.EXACTLY
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
        plateView.animate().scaleX(scale).scaleY(scale).setDuration(PRESS_DURATION_MS).start()
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


/** Camera capture remains owned by DeviceStore; this session only owns preprocessing. */
internal object AdvancedBeautySession {
    private const val SERVICE = "TEBeautyExtension"
    private val mainHandler = Handler(Looper.getMainLooper())
    private val owners = WeakHashMap<View, Pair<Lifecycle, LifecycleEventObserver>?>()
    private var scope: CoroutineScope? = null
    private var appContext: Context? = null
    private var activation: Activation? = null
    private var failedKey: String? = null
    private val revision = MutableStateFlow(0L)
    val changes = revision.asStateFlow()
    var isProcessing = false
        private set

    private val notification = ITUINotification { _, event, params ->
        mainHandler.post {
            if (event == "failure" && params?.get("session") === activation?.token) {
                failedKey = activation?.key
                stopProcessing()
                appContext?.let { BeautyPreferences.load(it, BeautyPreferences.currentKey).applyToSdk() }
            }
            if (event == "availability") failedKey = null
            refresh()
            revision.value++
        }
    }

    fun acquire(owner: View) {
        if (owners.containsKey(owner)) return
        appContext = owner.context.applicationContext
        val lifecycle = owner.findViewTreeLifecycleOwner()?.lifecycle
        val observer = LifecycleEventObserver { _, _ -> refresh() }
        owners[owner] = lifecycle?.let { it to observer }
        lifecycle?.addObserver(observer)
        if (scope == null) {
            TUICore.registerEvent(SERVICE, "changed", notification)
            TUICore.registerEvent(SERVICE, "availability", notification)
            TUICore.registerEvent(SERVICE, "failure", notification)
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).also { current ->
                current.launch { DeviceStore.shared().deviceState.cameraStatus.collect { refresh() } }
                current.launch { LoginStore.shared.loginState.loginUserInfo.collect { refresh() } }
            }
        }
        refresh()
    }

    fun release(owner: View) {
        owners.remove(owner)?.let { (lifecycle, observer) -> lifecycle.removeObserver(observer) }
        if (owners.isNotEmpty()) {
            refresh()
            return
        }
        scope?.cancel()
        scope = null
        TUICore.unRegisterEvent(notification)
        stopProcessing()
        failedKey = null
        appContext = null
    }

    fun refresh() {
        val context = appContext ?: return
        val key = BeautyPreferences.currentKey?.plus(".advanced")
        val available = TUICore.callService(SERVICE, "isAvailable", null) == true
        val foreground = owners.any { (view, entry) ->
            view.isAttachedToWindow && (entry == null || entry.first.currentState.isAtLeast(Lifecycle.State.STARTED))
        }
        val cameraOn = DeviceStore.shared().deviceState.cameraStatus.value == DeviceStatus.ON
        if (!available || key == null || !foreground || !cameraOn) {
            stopProcessing()
            failedKey = null
            BeautyPreferences.load(context, BeautyPreferences.currentKey).applyToSdk()
            return
        }
        if (activation?.key == key || failedKey == key) return
        stopProcessing()
        BeautyPreferences.load(context, BeautyPreferences.currentKey).applyToSdk()
        val token = TUICore.callService(SERVICE, "createSession", mapOf("context" to context, "preferencesKey" to key))
        if (token == null) {
            failedKey = key
            return
        }
        val next = Activation(key, token)
        activation = next
        val result = TRTCCloud.sharedInstance(context).setLocalVideoProcessListener(
            TRTCCloudDef.TRTC_VIDEO_PIXEL_FORMAT_Texture_2D,
            TRTCCloudDef.TRTC_VIDEO_BUFFER_TYPE_TEXTURE,
            next,
        )
        if (result != 0) {
            failedKey = key
            stopProcessing()
            BeautyPreferences.load(context, BeautyPreferences.currentKey).applyToSdk()
        }
    }

    fun getPanel(context: Context): View? {
        val current = activation ?: return null
        if (!isProcessing) return null
        return call(current.token, "getPanel", mapOf("context" to context)) as? View
    }

    fun save() {
        activation?.let { call(it.token, "save") }
    }

    private fun stopProcessing() {
        val previous = activation ?: return
        activation = null
        previous.acceptsFrames = false
        call(previous.token, "stop")
        appContext?.let {
            TRTCCloud.sharedInstance(it).setLocalVideoProcessListener(
                TRTCCloudDef.TRTC_VIDEO_PIXEL_FORMAT_Texture_2D,
                TRTCCloudDef.TRTC_VIDEO_BUFFER_TYPE_TEXTURE,
                null,
            )
        }
        isProcessing = false
        revision.value++
    }

    private fun call(token: Any, method: String, params: Map<String, Any> = emptyMap()): Any? =
        TUICore.callService(SERVICE, method, params + ("session" to token))

    private class Activation(val key: String, val token: Any) : TRTCCloudListener.TRTCVideoFrameListener {
        @Volatile
        var acceptsFrames = true

        override fun onGLContextCreated() {
            if (!acceptsFrames) return
            val ready = call(token, "createOnGLThread") == true
            mainHandler.post {
                if (activation !== this) return@post
                if (ready) {
                    isProcessing = true
                    BeautyPreferences(isEnabled = false).applyToSdk()
                    revision.value++
                } else {
                    failedKey = key
                    stopProcessing()
                    appContext?.let { BeautyPreferences.load(it, BeautyPreferences.currentKey).applyToSdk() }
                }
            }
        }

        override fun onProcessVideoFrame(src: TRTCCloudDef.TRTCVideoFrame, dst: TRTCCloudDef.TRTCVideoFrame): Int {
            dst.texture.textureId = src.texture.textureId
            if (acceptsFrames) {
                dst.texture.textureId = call(
                    token, "process", mapOf(
                        "texture" to src.texture.textureId, "width" to src.width, "height" to src.height,
                    )
                ) as? Int ?: src.texture.textureId
            }
            return 0
        }

        override fun onGLContextDestory() {
            call(token, "destroyOnGLThread")
            mainHandler.post {
                if (activation !== this) return@post
                isProcessing = false
                revision.value++
            }
        }
    }
}


/** Extend the SDK surface only beneath the controls, leaving its floating slider transparent. */
private class BeautySafeAreaBackground(color: Int) : ColorDrawable(color) {
    var bottomInset = 0
        set(value) {
            if (field == value) return
            field = value
            invalidateSelf()
        }

    override fun draw(canvas: Canvas) {
        val checkpoint = canvas.save()
        canvas.clipRect(bounds.left, maxOf(bounds.top, bounds.bottom - bottomInset), bounds.right, bounds.bottom)
        super.draw(canvas)
        canvas.restoreToCount(checkpoint)
    }
}
