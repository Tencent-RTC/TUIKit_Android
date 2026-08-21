package io.trtc.tuikit.chat.demo.settings

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import io.trtc.tuikit.atomicx.theme.ThemeStore
import io.trtc.tuikit.atomicx.theme.tokens.ColorTokens
import io.trtc.tuikit.chat.app.R
import kotlin.apply
import kotlin.math.max
import kotlin.math.min
import kotlin.ranges.coerceIn
import kotlin.text.format
import kotlin.text.startsWith
import kotlin.text.trim
import kotlin.text.uppercase

class PrimaryColorPickerDialog private constructor(
    context: Context,
    private val selectedHex: String,
    private val onColorSelected: (String) -> Unit
) {

    companion object {
        private const val CONTENT_PADDING_DP = 16f
        private const val CORNER_RADIUS_DP = 12f
        private const val HORIZONTAL_MARGIN_DP = 36f
        private const val PREVIEW_SIZE_DP = 48f
        private const val SPECTRUM_HEIGHT_DP = 24f
        private const val SLIDER_SHADOW_PADDING_DP = 4f
        private const val BUTTON_MIN_WIDTH_DP = 72f
        private const val BUTTON_MIN_HEIGHT_DP = 44f
        private const val DEFAULT_PRIMARY_COLOR = "#1C66E5"

        fun show(
            context: Context,
            selectedHex: String,
            onColorSelected: (String) -> Unit
        ) {
            PrimaryColorPickerDialog(context, selectedHex, onColorSelected).showInternal()
        }

        fun normalizeHex(hex: String): String {
            val trimmed = hex.trim()
            val withHash = if (trimmed.startsWith("#")) trimmed else "#$trimmed"
            return withHash.uppercase()
        }

        fun toHexColor(color: Int): String {
            return String.format("#%06X", 0xFFFFFF and color)
        }
    }

    private val dialog = Dialog(context)
    private val themeColors: ColorTokens
        get() = ThemeStore.shared(dialog.context).themeState.value.currentTheme.tokens.color

    private val hsv = FloatArray(3)
    private lateinit var previewView: View
    private lateinit var hexLabel: TextView
    private lateinit var hueBar: GradientSliderBar
    private lateinit var saturationBar: GradientSliderBar
    private lateinit var brightnessBar: GradientSliderBar

    private fun showInternal() {
        val activity = dialog.context as? Activity
        if (activity?.isFinishing == true || activity?.isDestroyed == true) {
            return
        }

        val context = dialog.context
        val density = context.resources.displayMetrics.density
        val screenWidth = context.resources.displayMetrics.widthPixels
        fun dp(value: Float): Int = (value * density + 0.5f).toInt()

        Color.colorToHSV(parseSafeColor(selectedHex), hsv)

        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setCanceledOnTouchOutside(true)

        val colors = themeColors
        val contentPadding = dp(CONTENT_PADDING_DP)
        val cornerRadius = CORNER_RADIUS_DP * density
        val previewSize = dp(PREVIEW_SIZE_DP)
        val horizontalMargin = dp(HORIZONTAL_MARGIN_DP)
        val dialogWidth = screenWidth - horizontalMargin * 2

        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(contentPadding, contentPadding, contentPadding, contentPadding)
            layoutParams = FrameLayout.LayoutParams(
                dialogWidth,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
            background = GradientDrawable().apply {
                setColor(colors.bgColorDialog)
                this.cornerRadius = cornerRadius
            }
        }

        content.addView(
            TextView(context).apply {
                text = context.getString(R.string.demo_settings_primary_color_title)
                setTextColor(colors.textColorPrimary)
                textSize = 16f
                setTypeface(typeface, Typeface.BOLD)
                gravity = Gravity.CENTER
            },
            matchWidthParams(bottom = dp(12f))
        )

        previewView = View(context)
        updatePreviewBackground()
        content.addView(
            previewView,
            LinearLayout.LayoutParams(previewSize, previewSize).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = dp(8f)
            }
        )

        hexLabel = TextView(context).apply {
            text = toHexColor(currentColor())
            setTextColor(colors.textColorSecondary)
            textSize = 13f
            gravity = Gravity.CENTER
        }
        content.addView(hexLabel, matchWidthParams(bottom = dp(12f)))

        hueBar = GradientSliderBar(context).apply {
            progress = hsv[0] / 360f
            setGradientColors(hueGradientColors())
            setOnProgressChangedListener { progress ->
                hsv[0] = progress * 360f
                refreshDependentBars()
                notifyPreviewChanged()
            }
        }
        addLabeledSlider(
            content,
            context.getString(R.string.demo_settings_primary_color_hue),
            hueBar,
            colors,
            ::dp
        )

        saturationBar = GradientSliderBar(context).apply {
            progress = hsv[1]
            setGradientColors(saturationGradientColors())
            setOnProgressChangedListener { progress ->
                hsv[1] = progress
                brightnessBar.setGradientColors(brightnessGradientColors())
                notifyPreviewChanged()
            }
        }
        addLabeledSlider(
            content,
            context.getString(R.string.demo_settings_primary_color_saturation),
            saturationBar,
            colors,
            ::dp
        )

        brightnessBar = GradientSliderBar(context).apply {
            progress = hsv[2]
            setGradientColors(brightnessGradientColors())
            setOnProgressChangedListener { progress ->
                hsv[2] = progress
                saturationBar.setGradientColors(saturationGradientColors())
                notifyPreviewChanged()
            }
        }
        addLabeledSlider(
            content,
            context.getString(R.string.demo_settings_primary_color_brightness),
            brightnessBar,
            colors,
            ::dp,
            last = true
        )

        val buttonRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val resetBtn = TextView(context).apply {
            text = context.getString(R.string.demo_settings_primary_color_reset)
            setTextColor(colors.textColorSecondary)
            textSize = 16f
            gravity = Gravity.CENTER
            minWidth = dp(BUTTON_MIN_WIDTH_DP)
            minHeight = dp(BUTTON_MIN_HEIGHT_DP)
            setPadding(dp(8f), dp(10f), dp(16f), dp(10f))
            setOnClickListener { resetSelectionToDefault() }
        }
        val cancelBtn = TextView(context).apply {
            text = context.getString(android.R.string.cancel)
            setTextColor(colors.textColorSecondary)
            textSize = 16f
            gravity = Gravity.CENTER
            minWidth = dp(BUTTON_MIN_WIDTH_DP)
            minHeight = dp(BUTTON_MIN_HEIGHT_DP)
            setPadding(dp(16f), dp(10f), dp(16f), dp(10f))
            setOnClickListener { dialog.dismiss() }
        }
        val confirmBtn = TextView(context).apply {
            text = context.getString(android.R.string.ok)
            setTextColor(colors.textColorLink)
            textSize = 16f
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            minWidth = dp(BUTTON_MIN_WIDTH_DP)
            minHeight = dp(BUTTON_MIN_HEIGHT_DP)
            setPadding(dp(16f), dp(10f), dp(16f), dp(10f))
            setOnClickListener {
                onColorSelected(toHexColor(currentColor()))
                dialog.dismiss()
            }
        }
        buttonRow.addView(resetBtn)
        buttonRow.addView(
            View(context),
            LinearLayout.LayoutParams(0, 1, 1f)
        )
        buttonRow.addView(cancelBtn)
        buttonRow.addView(confirmBtn)
        content.addView(buttonRow, matchWidthParams())
        updateThumbColors()

        val root = FrameLayout(context).apply {
            addView(content)
        }
        dialog.setContentView(
            root,
            ViewGroup.LayoutParams(dialogWidth, ViewGroup.LayoutParams.WRAP_CONTENT)
        )
        dialog.show()
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(dialogWidth, WindowManager.LayoutParams.WRAP_CONTENT)
            setGravity(Gravity.CENTER)
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            attributes = attributes.apply {
                width = dialogWidth
                dimAmount = 0.45f
            }
        }
    }

    private fun addLabeledSlider(
        content: LinearLayout,
        label: String,
        bar: GradientSliderBar,
        colors: ColorTokens,
        dp: (Float) -> Int,
        last: Boolean = false
    ) {
        content.addView(
            TextView(content.context).apply {
                text = label
                setTextColor(colors.textColorSecondary)
                textSize = 11f
            },
            matchWidthParams(bottom = dp(4f))
        )
        content.addView(
            bar,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(SPECTRUM_HEIGHT_DP + SLIDER_SHADOW_PADDING_DP * 2f)
            ).apply { bottomMargin = dp(if (last) 16f else 12f) }
        )
    }

    private fun matchWidthParams(top: Int = 0, bottom: Int = 0): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = top
            bottomMargin = bottom
        }
    }

    private fun resetSelectionToDefault() {
        Color.colorToHSV(parseSafeColor(DEFAULT_PRIMARY_COLOR), hsv)
        hueBar.progress = hsv[0] / 360f
        saturationBar.progress = hsv[1]
        brightnessBar.progress = hsv[2]
        refreshDependentBars()
        notifyPreviewChanged()
    }

    private fun refreshDependentBars() {
        saturationBar.setGradientColors(saturationGradientColors())
        brightnessBar.setGradientColors(brightnessGradientColors())
    }

    private fun notifyPreviewChanged() {
        updatePreviewBackground()
        hexLabel.text = toHexColor(currentColor())
        updateThumbColors()
    }

    private fun updateThumbColors() {
        val color = currentColor()
        hueBar.thumbColor = color
        saturationBar.thumbColor = color
        brightnessBar.thumbColor = color
    }

    private fun updatePreviewBackground() {
        val density = dialog.context.resources.displayMetrics.density
        previewView.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(currentColor())
            setStroke((1.5f * density).toInt(), themeColors.strokeColorPrimary)
        }
    }

    private fun currentColor(): Int = Color.HSVToColor(hsv)

    private fun hueGradientColors(): IntArray {
        return IntArray(7) { index ->
            Color.HSVToColor(floatArrayOf(index * 60f, 1f, 1f))
        }
    }

    private fun saturationGradientColors(): IntArray {
        return intArrayOf(
            Color.HSVToColor(floatArrayOf(hsv[0], 0f, hsv[2])),
            Color.HSVToColor(floatArrayOf(hsv[0], 1f, hsv[2]))
        )
    }

    private fun brightnessGradientColors(): IntArray {
        return intArrayOf(
            Color.BLACK,
            Color.HSVToColor(floatArrayOf(hsv[0], hsv[1], 1f))
        )
    }

    private fun parseSafeColor(hex: String): Int {
        return try {
            Color.parseColor(normalizeHex(hex))
        } catch (_: IllegalArgumentException) {
            Color.parseColor(DEFAULT_PRIMARY_COLOR)
        }
    }
}

class GradientSliderBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val density = resources.displayMetrics.density
    private val shadowPadding = 4f * density
    private val thumbBezel = 3f * density
    private val shadowOffsetY = 1f * density

    private val spectrumPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val thumbShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0x29000000
    }
    private val thumbFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
    }
    private val thumbInnerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val thumbRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f * density
        color = 0x29000000
    }

    private var spectrumShader: LinearGradient? = null
    private var gradientColors: IntArray = intArrayOf(Color.BLACK, Color.WHITE)
    private var onProgressChanged: ((Float) -> Unit)? = null

    var progress: Float = 0f
        set(value) {
            field = value.coerceIn(0f, 1f)
            invalidate()
        }

    var thumbColor: Int = Color.WHITE
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }

    init {
        layoutDirection = LAYOUT_DIRECTION_LTR
    }

    fun setGradientColors(colors: IntArray) {
        if (colors.size < 2) return
        gradientColors = colors
        rebuildShader()
        invalidate()
    }

    fun setOnProgressChangedListener(listener: (Float) -> Unit) {
        onProgressChanged = listener
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        rebuildShader()
    }

    override fun onDraw(canvas: Canvas) {
        val trackTop = shadowPadding
        val trackBottom = height - shadowPadding
        val trackHeight = max(0f, trackBottom - trackTop)
        if (trackHeight <= 0f || width <= 0) return

        val trackRadius = trackHeight / 2f
        canvas.drawRoundRect(
            0f,
            trackTop,
            width.toFloat(),
            trackBottom,
            trackRadius,
            trackRadius,
            spectrumPaint
        )

        val thumbRadius = trackRadius
        val usableWidth = max(0f, width - thumbRadius * 2f)
        val thumbX = thumbRadius + progress * usableWidth
        val thumbY = (trackTop + trackBottom) / 2f
        val innerRadius = max(0f, thumbRadius - thumbBezel)

        canvas.drawCircle(thumbX, thumbY + shadowOffsetY, thumbRadius, thumbShadowPaint)
        canvas.drawCircle(thumbX, thumbY, thumbRadius, thumbFillPaint)
        thumbInnerPaint.color = thumbColor
        canvas.drawCircle(thumbX, thumbY, innerRadius, thumbInnerPaint)
        canvas.drawCircle(thumbX, thumbY, thumbRadius - thumbRingPaint.strokeWidth / 2f, thumbRingPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN,
            MotionEvent.ACTION_MOVE -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                updateProgressFromX(event.x)
                return true
            }
            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                updateProgressFromX(event.x)
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun rebuildShader() {
        if (width <= 0) return
        spectrumShader = LinearGradient(
            0f,
            0f,
            width.toFloat(),
            0f,
            gradientColors,
            null,
            Shader.TileMode.CLAMP
        )
        spectrumPaint.shader = spectrumShader
    }

    private fun updateProgressFromX(x: Float) {
        if (width <= 0) return
        val thumbRadius = max(0f, height - shadowPadding * 2f) / 2f
        val usableWidth = max(1f, width - thumbRadius * 2f)
        val clampedX = max(thumbRadius, min(width - thumbRadius, x))
        progress = (clampedX - thumbRadius) / usableWidth
        onProgressChanged?.invoke(progress)
    }
}
