package com.trtc.uikit.livekit.pusher.prepare

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import com.trtc.uikit.livekit.R
import com.trtc.uikit.livekit.pusher.settings.PusherSettingsActionHandling
import com.trtc.uikit.livekit.pusher.settings.PusherSettingsItemView
import com.trtc.uikit.livekit.pusher.settings.PusherSettingsState
import java.util.UUID
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.view.Gravity
import android.widget.LinearLayout
import androidx.core.content.ContextCompat

class PusherPrepareView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {
    private companion object {
        const val COUNTDOWN_START_VALUE = 3
        const val ONE_SECOND_MS = 1_000L
    }

    var onExit: (() -> Unit)? = null
    var onCountdownFinished: ((String) -> Unit)? = null
    var onSelectCover: ((String) -> Unit)? = null
    internal var onTitleChanged: ((String) -> Unit)? = null
    internal var onShowBeauty: (() -> Unit)? = null
    var onRefreshSettings: (() -> Unit)? = null
    var onFlipCamera: (() -> Unit)? = null
    internal var settingsActionHandler: PusherSettingsActionHandling?
        get() = moreSettingsView.actionHandler
        set(value) {
            moreSettingsView.actionHandler = value
        }

    private val liveInfoEditorView: PusherLiveInfoEditorView
    private val editingContentView: View
    private val countdownView: CountdownView
    private val exitView: TextView
    private val actionsView: View
    private val startButton: GradientButton
    private val coverPickerView = PusherCoverPickerView(context)
    private val moreSettingsView = PrepareMoreSettingsView(context)

    private val countdownHandler = Handler(Looper.getMainLooper())
    private var countdownAction: Runnable? = null
    private var countdownOperationID: UUID? = null
    private var countdownValue = 0
    private var countdownTitle: String? = null

    init {
        LayoutInflater.from(context).inflate(R.layout.livekit_pusher_prepare_view, this, true)
        clipChildren = false
        clipToPadding = false

        editingContentView = findViewById(R.id.livekit_pusher_prepare_editing_content)
        liveInfoEditorView = findViewById(R.id.livekit_pusher_prepare_setup)
        exitView = findViewById(R.id.livekit_pusher_prepare_exit)
        actionsView = findViewById(R.id.livekit_pusher_prepare_actions)
        startButton = findViewById(R.id.livekit_pusher_prepare_start)
        countdownView = findViewById(R.id.livekit_pusher_prepare_countdown)

        findViewById<PusherSettingsItemView>(R.id.livekit_pusher_prepare_beauty).apply {
            configure(
                R.string.livekit_prepareactionbarview_beauty,
                R.drawable.livekit_prepare_beauty,
                PusherSettingsItemView.Style.PREPARE,
            )
            setOnClickListener {
                onShowBeauty?.invoke()
            }
        }
        findViewById<PusherSettingsItemView>(R.id.livekit_pusher_prepare_flip).apply {
            configure(
                R.string.livekit_prepareactionbarview_flip,
                R.drawable.livekit_prepare_flip,
                PusherSettingsItemView.Style.PREPARE,
            )
            setOnClickListener { onFlipCamera?.invoke() }
        }
        findViewById<PusherSettingsItemView>(R.id.livekit_pusher_prepare_more).apply {
            configure(
                R.string.livekit_prepareactionbarview_more,
                R.drawable.livekit_prepare_more,
                PusherSettingsItemView.Style.PREPARE,
            )
            setOnClickListener {
                onRefreshSettings?.invoke()
                moreSettingsView.show()
            }
        }

        liveInfoEditorView.onChangeCover = { coverPickerView.show() }
        liveInfoEditorView.onTitleChanged = { onTitleChanged?.invoke(it) }
        coverPickerView.onSelect = { onSelectCover?.invoke(it) }

        exitView.setOnClickListener { onExit?.invoke() }
        startButton.setOnClickListener { startCountdown() }
        countdownView.onCancel = ::cancelCountdown
        applyInsets()
    }

    override fun onDetachedFromWindow() {
        deactivate()
        dismissTransientUi()
        super.onDetachedFromWindow()
    }

    fun bind(title: String, coverURL: String) {
        if (countdownOperationID != null) return
        liveInfoEditorView.bindTitle(title)
        liveInfoEditorView.bindCover(coverURL)
        coverPickerView.bind(coverURL)
    }

    /** Lets the host close the cover picker before it leaves the prepare stage. */
    fun dismissTransientUi() {
        if (coverPickerView.isShowing) coverPickerView.dismiss()
        if (moreSettingsView.isShowing) moreSettingsView.dismiss()
    }

    internal fun bindSettings(settings: PusherSettingsState) {
        moreSettingsView.bind(settings)
    }

    internal fun deactivate() {
        stopCountdown()
        setCountdownPresented(false)
    }

    /** True when a Prepare-owned overlay consumed the back gesture. */
    fun handleBackPressed(): Boolean {
        if (countdownOperationID != null) {
            cancelCountdown()
            return true
        }
        if (moreSettingsView.handleBackPressed()) return true
        if (!coverPickerView.isShowing) return false
        coverPickerView.dismiss()
        return true
    }

    private fun startCountdown() {
        if (countdownOperationID != null) return
        liveInfoEditorView.clearFocus()
        (context as? Activity)?.window?.let { window ->
            WindowInsetsControllerCompat(window, window.decorView)
                .hide(WindowInsetsCompat.Type.ime())
        }
        val title = liveInfoEditorView.draftTitle
        countdownTitle = title
        countdownValue = COUNTDOWN_START_VALUE
        val operationID = UUID.randomUUID()
        countdownOperationID = operationID
        setCountdownPresented(true)
        countdownView.show(countdownValue)
        scheduleCountdownAdvance(operationID)
    }

    private fun scheduleCountdownAdvance(operationID: UUID) {
        val action = Runnable { advanceCountdown(operationID) }
        countdownAction = action
        countdownHandler.postDelayed(action, ONE_SECOND_MS)
    }

    private fun advanceCountdown(operationID: UUID) {
        if (countdownOperationID != operationID) return
        countdownAction = null
        if (countdownValue > 1) {
            countdownValue -= 1
            countdownView.show(countdownValue)
            scheduleCountdownAdvance(operationID)
            return
        }
        val title = countdownTitle ?: liveInfoEditorView.draftTitle
        stopCountdown()
        setCountdownPresented(false)
        onCountdownFinished?.invoke(title)
    }

    private fun cancelCountdown() {
        stopCountdown()
        setCountdownPresented(false)
    }

    private fun stopCountdown() {
        countdownAction?.let(countdownHandler::removeCallbacks)
        countdownAction = null
        countdownOperationID = null
        countdownValue = 0
        countdownTitle = null
        countdownView.stopAnimation()
    }

    private fun setCountdownPresented(isPresented: Boolean) {
        editingContentView.isVisible = !isPresented
        countdownView.isVisible = isPresented
    }

    /** Anchors the start button and settings above the navigation safe area. */
    private fun applyInsets() {
        val exitTop = resources.getDimensionPixelSize(
            R.dimen.livekit_pusher_prepare_exit_margin_top
        )
        val headerTop =
            resources.getDimensionPixelSize(R.dimen.livekit_pusher_prepare_header_top)
        val actionsGap =
            resources.getDimensionPixelSize(R.dimen.livekit_pusher_prepare_actions_margin_bottom)
        val startHeight =
            resources.getDimensionPixelSize(R.dimen.livekit_pusher_prepare_start_height)
        val startGap =
            resources.getDimensionPixelSize(R.dimen.livekit_pusher_prepare_start_margin_bottom)

        ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val navigationBottom =
                insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            val startBottom = navigationBottom + startGap
            exitView.updateLayoutParams<MarginLayoutParams> {
                topMargin = exitTop + systemBars.top
            }
            liveInfoEditorView.updateLayoutParams<MarginLayoutParams> {
                topMargin = headerTop + systemBars.top
            }
            startButton.updateLayoutParams<MarginLayoutParams> {
                bottomMargin = startBottom
            }
            actionsView.updateLayoutParams<MarginLayoutParams> {
                bottomMargin = startBottom + startHeight + actionsGap
            }
            insets
        }
    }

}
/**
 * Start-live button of the prepare stage, from the Ardot 开始直播按钮 node: a diagonal
 * gradient pill with a brighter gradient hairline border and a blue drop shadow, holding
 * the live indicator and the caption.
 *
 * The shadow reaches outside the button box, so the hosting layout keeps clipChildren off.
 */
class GradientButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr) {

    private val radius = resources.getDimension(R.dimen.livekit_pusher_prepare_start_radius)
    private val borderWidth =
        resources.getDimension(R.dimen.livekit_pusher_prepare_start_border_width)

    private val fillStartColor =
        ContextCompat.getColor(context, R.color.livekit_pusher_prepare_start_fill_start)
    private val fillEndColor =
        ContextCompat.getColor(context, R.color.livekit_pusher_prepare_start_fill_end)
    private val borderColors = intArrayOf(
        ContextCompat.getColor(context, R.color.livekit_pusher_prepare_start_border_start),
        ContextCompat.getColor(context, R.color.livekit_pusher_prepare_start_border_middle),
        ContextCompat.getColor(context, R.color.livekit_pusher_prepare_start_border_end)
    )

    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        val shadowColor =
            ContextCompat.getColor(context, R.color.livekit_pusher_prepare_start_shadow)
        color = shadowColor
        setShadowLayer(
            resources.getDimension(R.dimen.livekit_pusher_prepare_start_shadow_radius),
            0f,
            resources.getDimension(R.dimen.livekit_pusher_prepare_start_shadow_offset_y),
            shadowColor
        )
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = borderWidth
    }

    private val fillBounds = RectF()
    private val borderBounds = RectF()
    private var borderRadius = radius

    init {
        LayoutInflater.from(context).inflate(R.layout.livekit_pusher_start_live_button, this, true)
        orientation = HORIZONTAL
        gravity = Gravity.CENTER
        clipChildren = false
        clipToPadding = false
        isClickable = true
        isFocusable = true
        setWillNotDraw(false)
        // setShadowLayer is ignored by the hardware pipeline.
        setLayerType(LAYER_TYPE_SOFTWARE, null)
        contentDescription = context.getString(R.string.livekit_pusher_prepare_start_live)
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)

        fillBounds.set(0f, 0f, width.toFloat(), height.toFloat())
        val inset = borderWidth / 2f
        borderBounds.set(inset, inset, width - inset, height - inset)
        borderRadius = (radius - inset).coerceAtLeast(0f)

        // The Ardot gradient runs from the bottom-left corner to the top-right corner.
        fillPaint.shader = LinearGradient(
            0f,
            height.toFloat(),
            width.toFloat(),
            0f,
            fillStartColor,
            fillEndColor,
            Shader.TileMode.CLAMP
        )
        borderPaint.shader = LinearGradient(
            0f,
            height.toFloat(),
            width.toFloat(),
            0f,
            borderColors,
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawRoundRect(fillBounds, radius, radius, shadowPaint)
        canvas.drawRoundRect(fillBounds, radius, radius, fillPaint)
        canvas.drawRoundRect(borderBounds, borderRadius, borderRadius, borderPaint)
        super.onDraw(canvas)
    }
}

/**
 * White live indicator with an outward glow, from the Ardot 直播指示点 node. The view is
 * larger than the dot by the glow radius on every side so the glow is never clipped.
 */
class PusherLiveDotView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    private val dotRadius =
        resources.getDimension(R.dimen.livekit_pusher_prepare_start_dot_size) / 2f

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, android.R.color.white)
        setShadowLayer(
            resources.getDimension(R.dimen.livekit_pusher_prepare_start_dot_glow_radius),
            0f,
            0f,
            ContextCompat.getColor(context, R.color.livekit_pusher_prepare_start_dot_glow)
        )
    }

    init {
        // setShadowLayer is ignored by the hardware pipeline.
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawCircle(width / 2f, height / 2f, dotRadius, paint)
    }
}
