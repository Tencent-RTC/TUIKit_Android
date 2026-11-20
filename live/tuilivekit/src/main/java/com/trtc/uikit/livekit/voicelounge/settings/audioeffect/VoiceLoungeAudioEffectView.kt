package com.trtc.uikit.livekit.voicelounge.settings.audioeffect

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.util.TypedValue
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.trtc.uikit.livekit.R
import com.trtc.uikit.livekit.common.theme.LiveSlider
import com.trtc.uikit.livekit.common.theme.LiveSwitch
import com.trtc.uikit.livekit.voicelounge.MATCH
import com.trtc.uikit.livekit.voicelounge.VoiceLoungeBag
import com.trtc.uikit.livekit.voicelounge.VoiceLoungeMain
import com.trtc.uikit.livekit.voicelounge.WRAP
import com.trtc.uikit.livekit.voicelounge.blackAlpha
import com.trtc.uikit.livekit.voicelounge.dp
import com.trtc.uikit.livekit.voicelounge.frameParams
import com.trtc.uikit.livekit.voicelounge.linearParams
import com.trtc.uikit.livekit.voicelounge.rgb
import com.trtc.uikit.livekit.voicelounge.roundedDrawable
import com.trtc.uikit.livekit.voicelounge.settings.theme.VoiceLoungeSkin
import com.trtc.uikit.livekit.voicelounge.whiteAlpha
import com.trtc.uikit.livekit.voicelounge.withAlpha
import io.trtc.tuikit.atomicxcore.api.device.AudioChangerType
import io.trtc.tuikit.atomicxcore.api.device.AudioEffectStore
import io.trtc.tuikit.atomicxcore.api.device.AudioReverbType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max

internal class VoiceLoungeAudioEffectView(context: Context) : FrameLayout(context) {
    var onSetCaptureVolume: ((Int) -> Unit)? = null

    private val scrollView = ScrollView(context)
    private val contentStack = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }

    private val earMonitorSwitch = LiveSwitch(context)
    private val earMonitorVolumeRow = EffectVolumeRow(context, R.string.livekit_pusher_settings_ear_monitor_volume)
    private val voiceVolumeRow = EffectVolumeRow(context, R.string.livekit_voiceroom_audio_voice_volume)
    private val reverbStrip = EffectCardStrip(context, reverbOptions())
    private val voiceStrip = EffectCardStrip(context, voiceOptions())
    private val formantPad = FormantPadView(context)
    private val tuner = VoiceLoungeVoiceTuner

    private var skin = VoiceLoungeSkin.defaultSkin
    private var isActive = false
    private var scope: CoroutineScope? = null
    private val bag = VoiceLoungeBag()

    init {
        setupViews()
        setupBindings()
        applySkin(skin)
    }

    fun bindCaptureVolume(value: Int) {
        voiceVolumeRow.setValue(value)
    }

    fun setActive(active: Boolean) {
        isActive = active
        updateSubscription()
        if (active) scrollView.scrollTo(0, 0)
    }

    fun applySkin(skin: VoiceLoungeSkin) {
        this.skin = skin
        earMonitorSwitch.onTrackColor = skin.mid
        earMonitorSwitch.invalidate()
        earMonitorVolumeRow.applySkin(skin)
        voiceVolumeRow.applySkin(skin)
        reverbStrip.applySkin(skin)
        voiceStrip.applySkin(skin)
        formantPad.applySkin(skin)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        val navigationBottom = rootWindowInsets?.let {
            androidx.core.view.WindowInsetsCompat.toWindowInsetsCompat(it)
                .getInsets(androidx.core.view.WindowInsetsCompat.Type.navigationBars()).bottom
        } ?: 0
        contentStack.setPadding(0, 0, 0, context.dp(12) + navigationBottom)
        updateSubscription()
    }

    override fun onDetachedFromWindow() {
        isActive = false
        updateSubscription()
        super.onDetachedFromWindow()
    }

    private fun updateSubscription() {
        if (!isActive || !isAttachedToWindow) {
            scope?.cancel()
            scope = null
            return
        }
        if (scope != null) return
        val current = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        scope = current
        val state = AudioEffectStore.shared().audioEffectState
        state.isEarMonitorOpened.onEach { if (scope === current) earMonitorSwitch.setChecked(it, animate = false) }
            .launchIn(current)
        state.earMonitorVolume.onEach { if (scope === current) earMonitorVolumeRow.setValue(it) }.launchIn(current)
        state.audioReverbType.onEach { if (scope === current) reverbStrip.setSelected(it) }.launchIn(current)
        state.audioChangerType.onEach { if (scope === current) renderVoice(it) }.launchIn(current)
        renderVoice(state.audioChangerType.value)
    }

    private fun renderVoice(changer: AudioChangerType) {
        val voice: VoiceChoice = if (tuner.isCustom) VoiceChoice.Custom else VoiceChoice.Preset(changer)
        voiceStrip.setSelected(voice)
        val point = voice.formantPoint(tuner.customX, tuner.customY)
        formantPad.place(point.x, point.y, isIdle = voice is VoiceChoice.Preset && voice.type == AudioChangerType.NONE)
    }

    private fun setupViews() {
        addView(scrollView, frameParams(MATCH, MATCH))
        scrollView.isVerticalScrollBarEnabled = false
        scrollView.overScrollMode = OVER_SCROLL_NEVER
        scrollView.clipChildren = false
        scrollView.addView(contentStack, ViewGroup.LayoutParams(MATCH, WRAP))
        contentStack.clipChildren = false
        contentStack.setPadding(0, 0, 0, context.dp(12))

        val card = buildSettingsCard()
        contentStack.addView(inset(card), linearParams(MATCH, WRAP))
        space(12)

        val reverbTitle = inset(sectionTitle(R.string.common_reverb), fillHeight = true)
        contentStack.addView(reverbTitle, linearParams(MATCH, context.dp(22)))
        contentStack.addView(reverbStrip, linearParams(MATCH, context.dp(EffectStyle.CARD_SIZE + 8)))
        space(4)

        val voiceTitle = inset(sectionTitle(R.string.common_change_voice), fillHeight = true)
        contentStack.addView(voiceTitle, linearParams(MATCH, context.dp(22)))
        contentStack.addView(voiceStrip, linearParams(MATCH, context.dp(EffectStyle.CARD_SIZE + 8)))
        space(4)

        contentStack.addView(inset(formantPad, fillHeight = true), linearParams(MATCH, context.dp(150)))
        space(6)

        val hint = TextView(context).apply {
            text = context.getString(R.string.livekit_voiceroom_audio_formant_hint)
            setTextColor(whiteAlpha(0.45f))
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 8f)
            includeFontPadding = false
        }
        contentStack.addView(inset(hint), linearParams(MATCH, WRAP))
    }

    private fun setupBindings() {
        earMonitorSwitch.onCheckedChange = { enabled ->
            if (scope != null) AudioEffectStore.shared().setVoiceEarMonitorEnable(enabled)
        }
        earMonitorVolumeRow.onValueChanged = { value ->
            if (scope != null) AudioEffectStore.shared().setVoiceEarMonitorVolume(value.coerceIn(0, 100))
        }
        voiceVolumeRow.onValueChanged = { value -> onSetCaptureVolume?.invoke(value) }
        reverbStrip.onSelect = { reverb ->
            if (scope != null) AudioEffectStore.shared().setAudioReverbType(reverb)
        }
        voiceStrip.onSelect = { voice -> selectVoice(voice) }
        formantPad.onDrag = { x, y -> dragFormant(x, y) }
        formantPad.onReset = { selectVoice(VoiceChoice.Preset(AudioChangerType.NONE)) }
    }

    private fun selectVoice(voice: VoiceChoice) {
        if (scope == null) return
        when (voice) {
            is VoiceChoice.Preset -> {
                tuner.clearCustom()
                AudioEffectStore.shared().setAudioChangerType(voice.type)
            }

            VoiceChoice.Custom -> {
                AudioEffectStore.shared().setAudioChangerType(AudioChangerType.NONE)
                tuner.applyCustom(tuner.customX, tuner.customY)
            }
        }
        renderVoice(AudioEffectStore.shared().audioEffectState.audioChangerType.value)
    }

    private fun dragFormant(x: Float, y: Float) {
        if (scope == null) return
        if (!tuner.isCustom) {
            AudioEffectStore.shared().setAudioChangerType(AudioChangerType.NONE)
            voiceStrip.setSelected(VoiceChoice.Custom)
        }
        tuner.applyCustom(x, y)
        formantPad.place(x, y, isIdle = false, animated = false)
    }

    private fun inset(view: View, fillHeight: Boolean = false): View {
        val wrap = FrameLayout(context)
        val inset = context.dp(EffectStyle.HORIZONTAL_INSET)
        wrap.addView(view, frameParams(MATCH, if (fillHeight) MATCH else WRAP, Gravity.TOP, start = inset, end = inset))
        return wrap
    }

    private fun sectionTitle(@StringRes res: Int): View {
        val container = FrameLayout(context)
        val label = TextView(context).apply {
            text = context.getString(res)
            setTextColor(whiteAlpha(0.45f))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 8f)
            includeFontPadding = false
        }
        container.addView(
            label,
            frameParams(WRAP, WRAP, Gravity.BOTTOM or Gravity.START, start = context.dp(2), bottom = context.dp(5)),
        )
        return container
    }

    private fun space(dp: Int) {
        contentStack.addView(View(context), linearParams(MATCH, context.dp(dp)))
    }

    private fun buildSettingsCard(): View {
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedDrawable(
                rgb(22f / 255, 23f / 255, 31f / 255), context.dp(12).toFloat(), context.dp(1), whiteAlpha(0.08f),
            )
            setPadding(context.dp(12), context.dp(2), context.dp(12), context.dp(2))
        }
        val earRow = FrameLayout(context)
        val earLabel = TextView(context).apply {
            text = context.getString(R.string.livekit_pusher_settings_ear_monitor)
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 9.5f)
            includeFontPadding = false
        }
        earRow.addView(earLabel, frameParams(WRAP, WRAP, Gravity.CENTER_VERTICAL or Gravity.START))
        earRow.addView(
            earMonitorSwitch,
            frameParams(context.dp(42), context.dp(26), Gravity.CENTER_VERTICAL or Gravity.END),
        )
        card.addView(earRow, linearParams(MATCH, context.dp(EffectStyle.ROW_HEIGHT)))
        card.addView(withSeparator(earMonitorVolumeRow))
        card.addView(withSeparator(voiceVolumeRow))
        return card
    }

    private fun withSeparator(row: View): View {
        val wrap = FrameLayout(context)
        val sep = View(context).apply { setBackgroundColor(whiteAlpha(0.08f)) }
        wrap.addView(sep, frameParams(MATCH, context.dp(1), Gravity.TOP))
        wrap.addView(row, frameParams(MATCH, context.dp(EffectStyle.ROW_HEIGHT)))
        wrap.layoutParams = LinearLayout.LayoutParams(MATCH, context.dp(EffectStyle.ROW_HEIGHT))
        return wrap
    }

    private fun reverbOptions(): List<EffectOption<AudioReverbType>> = listOf(
        EffectOption(
            AudioReverbType.NONE, R.string.livekit_pusher_settings_reverb_none, R.drawable.livekit_voicefx_r_none,
        ),
        EffectOption(
            AudioReverbType.KTV, R.string.livekit_pusher_settings_reverb_ktv, R.drawable.livekit_voicefx_r_ktv
        ),
        EffectOption(
            AudioReverbType.METALLIC, R.string.common_reverb_metallic_sound, R.drawable.livekit_voicefx_r_metal
        ),
        EffectOption(
            AudioReverbType.DEEP, R.string.livekit_pusher_settings_reverb_deep, R.drawable.livekit_voicefx_r_deep,
        ),
        EffectOption(
            AudioReverbType.LOUD, R.string.livekit_pusher_settings_reverb_bright, R.drawable.livekit_voicefx_r_loud,
        ),
    )

    private fun voiceOptions(): List<EffectOption<VoiceChoice>> = listOf(
        EffectOption(
            VoiceChoice.Preset(AudioChangerType.NONE),
            R.string.livekit_pusher_settings_voice_original, R.drawable.livekit_voicefx_v_none,
        ),
        EffectOption(
            VoiceChoice.Custom, R.string.livekit_voiceroom_audio_voice_custom, R.drawable.livekit_voicefx_v_custom,
        ),
        EffectOption(
            VoiceChoice.Preset(AudioChangerType.CHILD),
            R.string.livekit_pusher_settings_voice_child, R.drawable.livekit_voicefx_v_kid,
        ),
        EffectOption(
            VoiceChoice.Preset(AudioChangerType.LITTLE_GIRL),
            R.string.common_change_voice_girl, R.drawable.livekit_voicefx_v_loli,
        ),
        EffectOption(
            VoiceChoice.Preset(AudioChangerType.MAN),
            R.string.livekit_pusher_settings_voice_uncle, R.drawable.livekit_voicefx_v_uncle,
        ),
        EffectOption(
            VoiceChoice.Preset(AudioChangerType.ETHEREAL),
            R.string.common_change_voice_ethereal, R.drawable.livekit_voicefx_v_ethereal,
        ),
    )

    internal sealed class VoiceChoice {
        data class Preset(val type: AudioChangerType) : VoiceChoice()
        object Custom : VoiceChoice()

        fun formantPoint(customX: Float, customY: Float): PointF = when (this) {
            is Custom -> PointF(customX, customY)
            is Preset -> when (type) {
                AudioChangerType.CHILD -> PointF(0.55f, 0.35f)
                AudioChangerType.LITTLE_GIRL -> PointF(0.75f, 0.65f)
                AudioChangerType.MAN -> PointF(-0.55f, -0.5f)
                AudioChangerType.ETHEREAL -> PointF(0.25f, 0.8f)
                else -> PointF(0f, 0f)
            }
        }
    }

    private data class EffectOption<T>(val value: T, @StringRes val titleRes: Int, @DrawableRes val iconRes: Int)

    private class EffectCardStrip<T>(context: Context, options: List<EffectOption<T>>) : HorizontalScrollView(context) {
        var onSelect: ((T) -> Unit)? = null
        private val stack = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(
                context.dp(EffectStyle.HORIZONTAL_INSET), context.dp(3), context.dp(EffectStyle.HORIZONTAL_INSET), 0
            )
        }
        private val cards: List<Pair<T, EffectCard>>

        init {
            isHorizontalScrollBarEnabled = false
            overScrollMode = OVER_SCROLL_NEVER
            clipToPadding = false
            clipChildren = false
            addView(stack, ViewGroup.LayoutParams(WRAP, MATCH))
            cards = options.mapIndexed { index, option ->
                val card = EffectCard(context, context.getString(option.titleRes), option.iconRes)
                card.setOnClickListener {
                    setSelected(option.value)
                    onSelect?.invoke(option.value)
                }
                val lp = LinearLayout.LayoutParams(context.dp(EffectStyle.CARD_SIZE), context.dp(EffectStyle.CARD_SIZE))
                    .apply {
                        if (index > 0) marginStart = context.dp(7)
                    }
                stack.addView(card, lp)
                option.value to card
            }
        }

        fun setSelected(value: T) = cards.forEach { it.second.markSelected(it.first == value) }
        fun applySkin(skin: VoiceLoungeSkin) = cards.forEach { it.second.applySkin(skin) }
    }

    private class EffectCard(context: Context, title: String, @DrawableRes iconRes: Int) : FrameLayout(context) {
        private val imageView = ImageView(context).apply {
            setImageResource(iconRes)
            scaleType = ImageView.ScaleType.CENTER_CROP
        }
        private val gradient = View(context).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(blackAlpha(0f), blackAlpha(0.55f), blackAlpha(0.88f)),
            )
        }
        private val titleLabel = TextView(context).apply {
            text = title
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 7f)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            maxLines = 1
            gravity = Gravity.CENTER
            includeFontPadding = false
            setShadowLayer(1.5f, 0f, 1f, blackAlpha(0.8f))
        }
        private val checkView = CheckBadgeView(context)
        private val border = GradientDrawable().apply {
            cornerRadius = context.dp(10).toFloat()
            setStroke(context.dp(1), whiteAlpha(0.08f))
            setColor(whiteAlpha(0.06f))
        }
        private var skin = VoiceLoungeSkin.defaultSkin
        private var selected = false

        init {
            background = border
            clipChildren = true
            clipToOutline = true
            outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
            addView(imageView, frameParams(MATCH, MATCH))
            addView(gradient, frameParams(MATCH, context.dp(26), Gravity.BOTTOM))
            addView(
                titleLabel, frameParams(
                    MATCH, WRAP, Gravity.BOTTOM, start = context.dp(2), end = context.dp(2), bottom = context.dp(4)
                )
            )
            addView(
                checkView, frameParams(
                    context.dp(12), context.dp(12), Gravity.TOP or Gravity.END, top = context.dp(4), end = context.dp(4)
                )
            )
            setOnTouchListener { view, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> view.animate().scaleX(0.95f).scaleY(0.95f).setDuration(80).start()
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> view.animate().scaleX(1f).scaleY(1f)
                        .setDuration(80).start()
                }
                false
            }
            update()
        }

        fun markSelected(value: Boolean) {
            if (selected == value) return
            selected = value
            update()
        }

        fun applySkin(skin: VoiceLoungeSkin) {
            this.skin = skin
            update()
        }

        private fun update() {
            border.setStroke(context.dp(if (selected) 2 else 1), if (selected) skin.mid else whiteAlpha(0.08f))
            checkView.visibility = if (selected) View.VISIBLE else View.GONE
            checkView.setBadgeColor(skin.mid)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                outlineSpotShadowColor = skin.mid
                outlineAmbientShadowColor = skin.mid
            }
            elevation = if (selected) context.dp(4).toFloat() else 0f
        }
    }

    private class CheckBadgeView(context: Context) : View(context) {
        private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        private val path = Path()

        init {
            bgPaint.color = Color.BLUE
        }

        fun setBadgeColor(color: Int) {
            bgPaint.color = color
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val size = max(width, height).toFloat()
            canvas.drawCircle(size / 2f, size / 2f, size / 2f, bgPaint)
            strokePaint.strokeWidth = size / 10f
            val density = size / 12f
            path.reset()
            path.moveTo(3.7f * density, 6f * density)
            path.lineTo(5.3f * density, 7.7f * density)
            path.lineTo(8.3f * density, 4.3f * density)
            canvas.drawPath(path, strokePaint)
        }
    }

    private class FormantPadView(context: Context) : FrameLayout(context) {
        var onDrag: ((Float, Float) -> Unit)? = null
        var onReset: (() -> Unit)? = null

        private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = whiteAlpha(0.18f) }
        private val axisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = whiteAlpha(0.12f) }
        private val knobView = KnobView(context)
        private val glowView = View(context)
        private val readoutView = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            background = roundedDrawable(whiteAlpha(0.07f), context.dp(8).toFloat())
            setPadding(context.dp(8), context.dp(4), context.dp(8), context.dp(4))
        }
        private val pitchValue = TextView(context)
        private val formantValue = TextView(context)
        private val tagLow = tagLabel(R.string.livekit_voiceroom_audio_pad_low)
        private val tagHigh = tagLabel(R.string.livekit_voiceroom_audio_pad_high)
        private val tagBright = tagLabel(R.string.livekit_voiceroom_audio_pad_bright)
        private val tagThick = tagLabel(R.string.livekit_voiceroom_audio_pad_thick)
        private var skin = VoiceLoungeSkin.defaultSkin
        private var pointX = 0f
        private var pointY = 0f
        private var isIdle = true
        private var knobCenter = PointF(0f, 0f)

        private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true

            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                onDrag?.invoke(valueXAt(e.x), valueYAt(e.y))
                return true
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                val r = context.dp(13 + 10).toFloat()
                val insideKnob = abs(e.x - knobCenter.x) < r && abs(e.y - knobCenter.y) < r
                if (insideKnob) onReset?.invoke() else onDrag?.invoke(valueXAt(e.x), valueYAt(e.y))
                return true
            }

            override fun onScroll(e1: MotionEvent?, e2: MotionEvent, dx: Float, dy: Float): Boolean {
                onDrag?.invoke(valueXAt(e2.x), valueYAt(e2.y))
                return true
            }
        })

        init {
            background = roundedDrawable(blackAlpha(0.25f), context.dp(14).toFloat(), context.dp(1), whiteAlpha(0.08f))
            clipToOutline = true
            outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
            setWillNotDraw(false)

            listOf(tagLow, tagHigh, tagBright, tagThick).forEach { addView(it, frameParams(WRAP, WRAP)) }

            glowView.background = RadialGlowDrawable(skin.mid)
            glowView.visibility = View.INVISIBLE
            addView(glowView, frameParams(context.dp(GLOW_SIZE), context.dp(GLOW_SIZE)))

            setupReadout()
            addView(knobView, frameParams(context.dp(KNOB_SIZE), context.dp(KNOB_SIZE)))
            place(0f, 0f, isIdle = true, animated = false)
        }

        fun applySkin(skin: VoiceLoungeSkin) {
            this.skin = skin
            glowView.background = RadialGlowDrawable(skin.mid)
            pitchValue.setTextColor(skin.textStrong)
            formantValue.setTextColor(skin.textStrong)
            updateKnobStyle()
        }

        fun place(x: Float, y: Float, isIdle: Boolean, animated: Boolean = true) {
            pointX = x
            pointY = y
            this.isIdle = isIdle
            pitchValue.text = format(x)
            formantValue.text = format(y)
            updateKnobStyle()
            layoutKnob(animated)
        }

        private fun updateKnobStyle() {
            if (isIdle) {
                knobView.setStyle(whiteAlpha(0.08f), IDLE_KNOB_START, IDLE_KNOB_END)
            } else {
                knobView.setStyle(skin.mid.withAlpha(0.25f), ACTIVE_KNOB_HIGHLIGHT, blendedForKnob(skin.mid))
            }
            glowView.visibility = if (isIdle) View.INVISIBLE else View.VISIBLE
        }

        private fun layoutKnob(animated: Boolean) {
            if (width <= 0 || height <= 0) return
            val span = 1f - EDGE_RATIO * 2f
            val x = (EDGE_RATIO + (pointX + 1f) / 2f * span) * width
            val y = (EDGE_RATIO + (1f - pointY) / 2f * span) * height
            knobCenter = PointF(x, y)
            val knobHalf = context.dp(KNOB_SIZE) / 2f
            val glowHalf = context.dp(GLOW_SIZE) / 2f
            knobView.animate().cancel()
            glowView.animate().cancel()
            if (animated && isAttachedToWindow) {
                knobView.animate().x(x - knobHalf).y(y - knobHalf).setDuration(250).start()
                glowView.animate().x(x - glowHalf).y(y - glowHalf).setDuration(250).start()
            } else {
                knobView.x = x - knobHalf
                knobView.y = y - knobHalf
                glowView.x = x - glowHalf
                glowView.y = y - glowHalf
            }
        }

        private fun valueXAt(sx: Float): Float {
            if (width <= 0) return 0f
            val span = 1f - EDGE_RATIO * 2f
            return snap((sx / width - EDGE_RATIO) / span * 2f - 1f)
        }

        private fun valueYAt(sy: Float): Float {
            if (height <= 0) return 0f
            val span = 1f - EDGE_RATIO * 2f
            return snap(1f - (sy / height - EDGE_RATIO) / span * 2f)
        }

        private fun snap(value: Float): Float {
            val clamped = value.coerceIn(-1f, 1f)
            return if (abs(clamped) < 0.06f) 0f else clamped
        }

        override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
            super.onLayout(changed, left, top, right, bottom)
            layoutTags()
            if (changed) layoutKnob(animated = false)
        }

        private fun layoutTags() {
            val midX = width / 2
            val midY = height / 2
            val gap = context.dp(4)
            val side = context.dp(8)
            val vertical = context.dp(6)
            val horizontal = context.dp(6)
            tagLow.layoutAt(side, midY - gap - tagLow.measuredHeight)
            tagHigh.layoutAt(width - side - tagHigh.measuredWidth, midY - gap - tagHigh.measuredHeight)
            tagBright.layoutAt(midX + horizontal, vertical)
            tagThick.layoutAt(midX + horizontal, height - vertical - tagThick.measuredHeight)
        }

        private fun View.layoutAt(x: Int, y: Int) = layout(x, y, x + measuredWidth, y + measuredHeight)

        override fun onInterceptTouchEvent(ev: MotionEvent): Boolean = true

        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (event.actionMasked == MotionEvent.ACTION_DOWN) parent?.requestDisallowInterceptTouchEvent(true)
            return gestureDetector.onTouchEvent(event)
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val axisX = context.dp(20).toFloat()
            val axisY = context.dp(16).toFloat()
            canvas.drawRect(axisX, height / 2f, width - axisX, height / 2f + context.dp(1), axisPaint)
            canvas.drawRect(width / 2f, axisY, width / 2f + context.dp(1), height - axisY, axisPaint)
            val rect = RectF(axisX, axisY, width - axisX, height - axisY)
            if (rect.width() <= 0 || rect.height() <= 0) return
            val radius = context.resources.displayMetrics.density * 1.2f
            for (column in 0..4) {
                for (row in 0..2) {
                    val cx = rect.left + rect.width() * column / 4f
                    val cy = rect.top + rect.height() * row / 2f
                    canvas.drawCircle(cx, cy, radius, dotPaint)
                }
            }
        }

        private fun setupReadout() {
            val titles = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
            val values = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.END
            }
            titles.addView(readoutTitle(R.string.livekit_voiceroom_audio_pitch))
            titles.addView(
                readoutTitle(R.string.livekit_voiceroom_audio_formant), linearParams(WRAP, WRAP, top = context.dp(1))
            )
            pitchValue.applyReadoutValue()
            formantValue.applyReadoutValue()
            values.addView(pitchValue)
            values.addView(formantValue, linearParams(WRAP, WRAP, top = context.dp(1)))
            readoutView.addView(titles)
            readoutView.addView(values, linearParams(WRAP, WRAP, start = context.dp(4)))
            addView(
                readoutView,
                frameParams(WRAP, WRAP, Gravity.TOP or Gravity.END, top = context.dp(8), end = context.dp(8)),
            )
        }

        private fun tagLabel(@StringRes res: Int): TextView = TextView(context).apply {
            text = context.getString(res)
            setTextColor(whiteAlpha(0.4f))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 7f)
            includeFontPadding = false
        }

        private fun readoutTitle(@StringRes res: Int): TextView = TextView(context).apply {
            text = context.getString(res)
            setTextColor(whiteAlpha(0.8f))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 7.5f)
            includeFontPadding = false
        }

        private fun TextView.applyReadoutValue() {
            setTextColor(skin.textStrong)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 7.5f)
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            gravity = Gravity.END
            includeFontPadding = false
            text = format(0f)
        }

        private fun format(value: Float): String = (if (value > 0f) "+" else "") + "%.2f".format(value.toDouble())

        private fun blendedForKnob(color: Int): Int {
            fun mix(channel: Int): Int = (channel * 0.8f + 255f * 0.2f).toInt().coerceIn(0, 255)
            return Color.rgb(mix(Color.red(color)), mix(Color.green(color)), mix(Color.blue(color)))
        }

        companion object {
            private const val EDGE_RATIO = 0.08f
            private const val KNOB_SIZE = 26
            private const val GLOW_SIZE = 120
            private val IDLE_KNOB_START = rgb(0.914f, 0.925f, 0.949f)
            private val IDLE_KNOB_END = rgb(0.604f, 0.631f, 0.682f)
            private val ACTIVE_KNOB_HIGHLIGHT = rgb(0.835f, 0.894f, 1f)
        }
    }

    private class KnobView(context: Context) : View(context) {
        private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
        private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private var startColor = Color.WHITE
        private var endColor = Color.GRAY

        init {
            outlineProvider = object : android.view.ViewOutlineProvider() {
                override fun getOutline(view: View, outline: android.graphics.Outline) {
                    outline.setOval(0, 0, view.width, view.height)
                }
            }
            elevation = context.dp(5).toFloat()
        }

        fun setStyle(ring: Int, start: Int, end: Int) {
            ringPaint.color = ring
            startColor = start
            endColor = end
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val size = width.toFloat()
            val border = context.dp(4).toFloat()
            ringPaint.strokeWidth = border
            canvas.drawCircle(size / 2f, size / 2f, size / 2f - border / 2f, ringPaint)
            val inner = size - border * 2f
            val cx = border + inner * 0.35f
            val cy = border + inner * 0.3f
            fillPaint.shader = RadialGradient(
                cx, cy, inner * 0.85f, intArrayOf(startColor, endColor), null, Shader.TileMode.CLAMP,
            )
            canvas.drawCircle(size / 2f, size / 2f, inner / 2f, fillPaint)
        }
    }

    private class RadialGlowDrawable(private val color: Int) : android.graphics.drawable.Drawable() {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        override fun draw(canvas: Canvas) {
            val r = bounds.width() / 2f
            paint.shader = RadialGradient(
                bounds.exactCenterX(), bounds.exactCenterY(), r * 0.65f,
                intArrayOf(color.withAlpha(0.28f), color.withAlpha(0f)), floatArrayOf(0f, 1f), Shader.TileMode.CLAMP,
            )
            canvas.drawCircle(bounds.exactCenterX(), bounds.exactCenterY(), r, paint)
        }

        override fun setAlpha(alpha: Int) = Unit
        override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) = Unit

        @Deprecated("", ReplaceWith("android.graphics.PixelFormat.TRANSLUCENT"))
        override fun getOpacity(): Int = android.graphics.PixelFormat.TRANSLUCENT
    }

    private class EffectVolumeRow(context: Context, @StringRes titleRes: Int) : FrameLayout(context) {
        var onValueChanged: ((Int) -> Unit)? = null
        private val slider = LiveSlider(context).apply {
            setRange(0, 100, 1)
        }
        private val valueLabel = TextView(context).apply {
            setTextColor(whiteAlpha(0.72f))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 9f)
            gravity = Gravity.END
            includeFontPadding = false
            text = "0"
        }
        private val titleLabel = TextView(context).apply {
            text = context.getString(titleRes)
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 9.5f)
            includeFontPadding = false
        }

        init {
            addView(titleLabel, frameParams(WRAP, WRAP, Gravity.CENTER_VERTICAL or Gravity.START))
            addView(
                valueLabel, frameParams(
                    context.dp(30), WRAP, Gravity.CENTER_VERTICAL or Gravity.END, end = context.dp(SLIDER_WIDTH_DP + 8)
                )
            )
            addView(
                slider, frameParams(context.dp(SLIDER_WIDTH_DP), context.dp(28), Gravity.CENTER_VERTICAL or Gravity.END)
            )
            slider.onValueChanged = { value ->
                valueLabel.text = value.toString()
                onValueChanged?.invoke(value)
            }
        }

        fun setValue(value: Int) {
            valueLabel.text = value.toString()
            slider.setValue(value)
        }

        fun applySkin(skin: VoiceLoungeSkin) {
            slider.minimumTrackColor = skin.mid
        }

        companion object {
            private const val SLIDER_WIDTH_DP = 180
        }
    }

    private object EffectStyle {
        const val HORIZONTAL_INSET = 16
        const val ROW_HEIGHT = 44
        const val CARD_SIZE = 56
    }
}
