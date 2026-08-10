package io.trtc.tuikit.chat.uikit.components.messageinput.ui
import android.animation.ValueAnimator
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.PopupWindow
import android.widget.TextView
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import kotlin.math.abs
import kotlin.math.sin
import io.trtc.tuikit.chat.uikit.R
import io.trtc.tuikit.chat.uikit.components.audiorecorder.AudioRecorder
import io.trtc.tuikit.chat.uikit.components.audiorecorder.AudioRecorderListener
import io.trtc.tuikit.chat.uikit.components.audiorecorder.AudioRecorderResult
import io.trtc.tuikit.chat.uikit.components.audiorecorder.AudioRecorderResultCode
import io.trtc.tuikit.atomicx.common.permission.PermissionCallback
import io.trtc.tuikit.chat.uikit.components.common.ChatDateTimeUtils
import io.trtc.tuikit.chat.uikit.components.common.ChatPermissionHelper
import io.trtc.tuikit.atomicx.theme.ThemeStore
import io.trtc.tuikit.atomicx.widget.basicwidget.toast.AtomicToast

data class AudioRecorderViewConfig(
    val minDurationMs: Int = 1000,
    val maxDurationMs: Int = 60000,
    val cancelTouchSlopDp: Int = 60,
    val startOnLongPress: Boolean = true,
)

fun interface AudioRecorderViewResultListener {
    fun onCompleted(result: AudioRecorderResult, releaseAction: AudioRecorderReleaseAction)
}

class AudioRecorderView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : AppCompatTextView(context, attrs, defStyleAttr) {
    var config: AudioRecorderViewConfig = AudioRecorderViewConfig()

    private var resultListener: AudioRecorderViewResultListener? = null
    private var isRecording = false
    private var currentUiState = RecordUiState.IDLE
    private var overlay: AudioRecorderOverlay? = null
    private var overlayAnchorView: View? = null
    private var pendingReleaseAction = AudioRecorderReleaseAction.SEND_AUDIO
    private var isWaitingForLongPress = false
    private var hasRecorderStarted = false
    private val longPressRunnable = Runnable {
        isWaitingForLongPress = false
        beginRecordIfPossible()
    }

    init {
        gravity = Gravity.CENTER
        ellipsize = TextUtils.TruncateAt.END
        maxLines = 1
        setText(R.string.message_input_press_to_talk)
        isClickable = true
    }

    fun setOnResultListener(listener: AudioRecorderViewResultListener?) {
        resultListener = listener
    }

    fun cancelRecording() {
        if (isRecording) {
            AudioRecorder.cancelRecord()
        }
        resetToIdleState()
    }

    fun resetState() {
        if (!isRecording) {
            resetToIdleState()
        }
    }

    fun startRecordingFromLongPress(overlayAnchorView: View = this): Boolean {
        removeCallbacks(longPressRunnable)
        isWaitingForLongPress = false
        if (!isRecording) {
            beginRecordIfPossible(overlayAnchorView)
        }
        return isRecording
    }

    fun updateRecordingTouch(rawX: Float, rawY: Float) {
        if (isRecording) {
            updateReleaseAction(obtainOverlay().releaseActionFor(rawX, rawY))
        }
    }

    fun finishRecordingTouch(rawX: Float, rawY: Float) {
        if (!isRecording) {
            return
        }
        pendingReleaseAction = obtainOverlay().releaseActionFor(rawX, rawY)
        if (pendingReleaseAction == AudioRecorderReleaseAction.CANCEL) {
            AudioRecorder.cancelRecord()
        } else {
            AudioRecorder.stopRecord()
        }
    }

    override fun onDetachedFromWindow() {
        cancelRecording()
        super.onDetachedFromWindow()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                disallowAncestorsIntercept(true)
                pendingReleaseAction = AudioRecorderReleaseAction.SEND_AUDIO
                if (config.startOnLongPress) {
                    isWaitingForLongPress = true
                    postDelayed(longPressRunnable, ViewConfiguration.getLongPressTimeout().toLong())
                } else {
                    beginRecordIfPossible()
                }
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (isRecording) {
                    updateReleaseAction(obtainOverlay().releaseActionFor(event.rawX, event.rawY))
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                disallowAncestorsIntercept(false)
                removeCallbacks(longPressRunnable)
                if (isWaitingForLongPress) {
                    isWaitingForLongPress = false
                    performClick()
                    return true
                }
                if (isRecording) {
                    pendingReleaseAction = obtainOverlay().releaseActionFor(event.rawX, event.rawY)
                    if (pendingReleaseAction == AudioRecorderReleaseAction.CANCEL) {
                        AudioRecorder.cancelRecord()
                    } else {
                        AudioRecorder.stopRecord()
                    }
                }
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                disallowAncestorsIntercept(false)
                removeCallbacks(longPressRunnable)
                isWaitingForLongPress = false
                if (isRecording) {
                    AudioRecorder.cancelRecord()
                }
                return true
            }

            else -> return super.onTouchEvent(event)
        }
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun disallowAncestorsIntercept(disallow: Boolean) {
        var p = parent
        while (p != null) {
            p.requestDisallowInterceptTouchEvent(disallow)
            p = p.parent
        }
    }

    private fun beginRecordIfPossible(overlayAnchorView: View = this) {
        if (!hasRecordPermission()) {
            requestRecordPermission()
            return
        }
        isRecording = true
        hasRecorderStarted = false
        currentUiState = RecordUiState.RECORDING
        pendingReleaseAction = AudioRecorderReleaseAction.SEND_AUDIO
        val overlay = obtainOverlay(overlayAnchorView)
        overlay.show()
        overlay.update(currentUiState, null, 0, config.maxDurationMs)
        AudioRecorder.startRecord(
            minDurationMs = config.minDurationMs,
            maxDurationMs = config.maxDurationMs,
            listener = object : AudioRecorderListener {
                override fun onStart() {
                    isRecording = true
                    currentUiState = RecordUiState.RECORDING
                    obtainOverlay().show()
                }

                override fun onProgress(durationMs: Int, powerLevel: Int) {
                    hasRecorderStarted = true
                    obtainOverlay().update(currentUiState, durationMs, powerLevel, config.maxDurationMs)
                }

                override fun onCompleted(result: AudioRecorderResult) {
                    handleRecordResult(result)
                }
            },
        )
    }

    private fun handleRecordResult(result: AudioRecorderResult) {
        when (result.resultCode) {
            AudioRecorderResultCode.ERROR_LESS_THAN_MIN_DURATION -> {
                AtomicToast.show(
                    context,
                    context.getString(R.string.message_input_audio_too_short),
                    style = AtomicToast.Style.WARNING,
                )
            }

            AudioRecorderResultCode.SUCCESS_EXCEED_MAX_DURATION -> {
                AtomicToast.show(
                    context,
                    context.getString(R.string.message_input_audio_time_limit_reached),
                    style = AtomicToast.Style.WARNING,
                )
            }

            AudioRecorderResultCode.ERROR_RECORD_PERMISSION_DENIED -> {
                AtomicToast.show(
                    context,
                    context.getString(R.string.message_input_record_audio_permission_settings_tip),
                    style = AtomicToast.Style.WARNING,
                )
            }

            AudioRecorderResultCode.ERROR_RECORD_INNER_FAIL,
            AudioRecorderResultCode.ERROR_STORAGE_UNAVAILABLE,
            AudioRecorderResultCode.ERROR_RECORDING -> {
                AtomicToast.show(
                    context,
                    context.getString(R.string.message_input_send_failed),
                    style = AtomicToast.Style.ERROR
                )
            }

            AudioRecorderResultCode.ERROR_CANCEL,
            AudioRecorderResultCode.SUCCESS -> Unit

            else -> Unit
        }
        resultListener?.onCompleted(result, pendingReleaseAction)
        resetToIdleState()
    }

    private fun requestRecordPermission() {
        ChatPermissionHelper.requestPermission(
            ChatPermissionHelper.PERMISSION_MICROPHONE,
            object : PermissionCallback() {
                override fun onGranted() {
                    AtomicToast.show(
                        context,
                        context.getString(R.string.message_input_press_to_talk),
                        style = AtomicToast.Style.SUCCESS,
                    )
                }

                override fun onDenied() {
                    AtomicToast.show(
                        context,
                        context.getString(R.string.message_input_record_audio_permission_settings_tip),
                        style = AtomicToast.Style.WARNING,
                    )
                }
            }
        )
    }

    private fun hasRecordPermission(): Boolean {
        return ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun updateReleaseAction(releaseAction: AudioRecorderReleaseAction) {
        pendingReleaseAction = releaseAction
        val nextState = when (releaseAction) {
            AudioRecorderReleaseAction.CANCEL -> RecordUiState.READY_TO_CANCEL
            AudioRecorderReleaseAction.TRANSCRIBE -> RecordUiState.READY_TO_TRANSCRIBE
            AudioRecorderReleaseAction.SEND_AUDIO -> RecordUiState.RECORDING
            else -> RecordUiState.RECORDING
        }
        if (currentUiState == nextState) {
            return
        }
        currentUiState = nextState
        obtainOverlay().update(
            currentUiState,
            if (hasRecorderStarted) AudioRecorder.currentTimeMs.value else null,
            AudioRecorder.currentPower.value,
            config.maxDurationMs,
        )
    }

    private fun resetToIdleState() {
        isRecording = false
        hasRecorderStarted = false
        isWaitingForLongPress = false
        removeCallbacks(longPressRunnable)
        currentUiState = RecordUiState.IDLE
        pendingReleaseAction = AudioRecorderReleaseAction.SEND_AUDIO
        overlay?.dismiss()
        overlay = null
        overlayAnchorView = null
        isPressed = false
    }

    private fun obtainOverlay(anchorView: View = overlayAnchorView ?: this): AudioRecorderOverlay {
        if (overlay != null && overlayAnchorView !== anchorView) {
            overlay?.dismiss()
            overlay = null
        }
        return overlay ?: AudioRecorderOverlay(anchorView).also {
            overlay = it
            overlayAnchorView = anchorView
        }
    }

    internal enum class RecordUiState {
        IDLE,
        RECORDING,
        READY_TO_CANCEL,
        READY_TO_TRANSCRIBE,
    }
}

private class AudioRecorderOverlay(
    private val anchorView: View,
) {
    private val context = anchorView.context
    private val density = context.resources.displayMetrics.density

    private val themeColors
        get() = ThemeStore.shared(context).themeState.value.currentTheme.tokens.color

    private val rootView: FrameLayout = FrameLayout(context)
    private val backgroundView = OverlayGradientBackgroundView(context)

    private val bubbleHeight = (40 * density).toInt()
    private val bubbleDefaultHorizontalMargin = (8 * density).toInt()
    private val bubbleDefaultBottomMargin = (43 * density).toInt()
    private val animationSpec = AudioRecorderOverlayAnimationPolicy.createSpec()
    private val animationInterpolator = DecelerateInterpolator()
    private var isDismissed = false
    private val bubbleView = BubbleView(context).apply {
        val colors = themeColors
        setBubbleColor(colors.buttonColorPrimaryDefault)
        setContentColor(colors.textColorButton)
    }
    private val statusTextView = TextView(context).apply {
        gravity = Gravity.CENTER
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        setTextColor(themeColors.textColorSecondary)
    }
    private val cancelButtonView = TextView(context).apply {
        gravity = Gravity.CENTER
        setText(R.string.message_input_cancel)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        val colors = themeColors
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(colors.buttonColorSecondaryDefault)
        }
        setTextColor(colors.textColorPrimary)
    }
    private val transcribeButtonView = TextView(context).apply {
        gravity = Gravity.CENTER
        setText(R.string.message_input_transcribe_to_text)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        val colors = themeColors
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(colors.buttonColorSecondaryDefault)
        }
        setTextColor(colors.textColorPrimary)
    }
    private val popupWindow = PopupWindow(
        rootView,
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        false,
    ).apply {
        isClippingEnabled = false
        isTouchable = false
        isFocusable = false
        isOutsideTouchable = false
    }

    init {
        rootView.layoutDirection = anchorView.layoutDirection
        buildLayout()
        applyThemedBackground()
    }

    private fun buildLayout() {
        rootView.addView(
            backgroundView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )

        val actionSize = (80 * density).toInt()
        val actionHorizontalMargin = (72 * density).toInt()
        val cancelLp = FrameLayout.LayoutParams(actionSize, actionSize, Gravity.BOTTOM or Gravity.START)
        cancelLp.marginStart = actionHorizontalMargin
        cancelLp.bottomMargin = (107 * density).toInt()
        rootView.addView(cancelButtonView, cancelLp)

        val transcribeLp = FrameLayout.LayoutParams(actionSize, actionSize, Gravity.BOTTOM or Gravity.END)
        transcribeLp.marginEnd = actionHorizontalMargin
        transcribeLp.bottomMargin = (107 * density).toInt()
        rootView.addView(transcribeButtonView, transcribeLp)

        val statusLp = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL,
        )
        statusLp.bottomMargin = (199 * density).toInt()
        rootView.addView(statusTextView, statusLp)

        val bubbleLp = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            bubbleHeight,
            Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL,
        )
        bubbleLp.marginStart = bubbleDefaultHorizontalMargin
        bubbleLp.marginEnd = bubbleDefaultHorizontalMargin
        bubbleLp.bottomMargin = bubbleDefaultBottomMargin
        rootView.addView(bubbleView, bubbleLp)
    }

    fun show() {
        if (popupWindow.isShowing || !anchorView.isAttachedToWindow) {
            return
        }
        isDismissed = false
        rootView.animate().cancel()
        rootView.alpha = animationSpec.enterStartAlpha
        updateBubbleLayoutForAnchorBeforeShow()
        rootView.visibility = View.VISIBLE
        popupWindow.showAtLocation(anchorView, Gravity.NO_GRAVITY, 0, 0)
        rootView.animate()
            .alpha(animationSpec.enterEndAlpha)
            .setDuration(animationSpec.enterDurationMs)
            .setInterpolator(animationInterpolator)
            .setListener(null)
            .start()
        rootView.post {
            if (popupWindow.isShowing && !isDismissed) {
                updateBubbleLayoutForAnchorAfterShow()
            }
        }
    }

    private fun updateBubbleLayoutForAnchorBeforeShow() {
        val root = anchorView.rootView
        val rootLocation = IntArray(2)
        val anchorLocation = IntArray(2)
        root.getLocationOnScreen(rootLocation)
        anchorView.getLocationOnScreen(anchorLocation)
        updateBubbleLayout(
            rootWidth = root.width,
            rootHeight = root.height,
            anchorLeft = anchorLocation[0] - rootLocation[0],
            anchorTop = anchorLocation[1] - rootLocation[1],
        )
    }

    private fun updateBubbleLayoutForAnchorAfterShow() {
        val rootLocation = IntArray(2)
        val anchorLocation = IntArray(2)
        rootView.getLocationOnScreen(rootLocation)
        anchorView.getLocationOnScreen(anchorLocation)
        updateBubbleLayout(
            rootWidth = rootView.width,
            rootHeight = rootView.height,
            anchorLeft = anchorLocation[0] - rootLocation[0],
            anchorTop = anchorLocation[1] - rootLocation[1],
        )
    }

    private fun updateBubbleLayout(
        rootWidth: Int,
        rootHeight: Int,
        anchorLeft: Int,
        anchorTop: Int,
    ) {
        val layout = AudioRecorderOverlayBubbleLayoutPolicy.calculate(
            rootWidth = rootWidth,
            rootHeight = rootHeight,
            anchorLeft = anchorLeft,
            anchorTop = anchorTop,
            anchorWidth = anchorView.width,
            anchorHeight = anchorView.height,
            defaultHorizontalMargin = bubbleDefaultHorizontalMargin,
            defaultBottomMargin = bubbleDefaultBottomMargin,
            bubbleHeight = bubbleHeight,
        )
        val layoutParams = bubbleView.layoutParams as FrameLayout.LayoutParams
        layoutParams.marginStart = layout.leftMargin
        layoutParams.marginEnd = layout.rightMargin
        layoutParams.bottomMargin = layout.bottomMargin
        layoutParams.height = layout.height
        bubbleView.layoutParams = layoutParams
    }

    fun releaseActionFor(rawX: Float, rawY: Float): AudioRecorderReleaseAction {
        val rootLocation = IntArray(2)
        rootView.getLocationOnScreen(rootLocation)
        val localX = rawX - rootLocation[0]
        val localY = rawY - rootLocation[1]
        return AudioRecorderGesturePolicy.decideReleaseAction(
            fingerX = localX,
            fingerY = localY,
            cancelTarget = cancelButtonView.toGestureTarget(),
            transcribeTarget = transcribeButtonView.toGestureTarget(),
        )
    }

    fun update(state: Enum<*>, durationMs: Int?, powerLevel: Int, maxDurationMs: Int) {
        val releaseAction = when (state.name) {
            "READY_TO_CANCEL" -> AudioRecorderReleaseAction.CANCEL
            "READY_TO_TRANSCRIBE" -> AudioRecorderReleaseAction.TRANSCRIBE
            else -> AudioRecorderReleaseAction.SEND_AUDIO
        }
        statusTextView.text = statusTextFor(releaseAction, durationMs, maxDurationMs)
        val colors = themeColors
        applyThemedBackground(colors.bgColorOperate)
        statusTextView.setTextColor(colors.textColorSecondary)

        val cancelActive = releaseAction == AudioRecorderReleaseAction.CANCEL
        val transcribeActive = releaseAction == AudioRecorderReleaseAction.TRANSCRIBE
        setCircleBackground(
            cancelButtonView,
            if (cancelActive) colors.buttonColorHangupDefault else colors.buttonColorSecondaryDefault,
        )
        cancelButtonView.setTextColor(if (cancelActive) colors.textColorButton else colors.textColorPrimary)
        setCircleBackground(
            transcribeButtonView,
            if (transcribeActive) colors.buttonColorPrimaryDefault else colors.buttonColorSecondaryDefault,
        )
        transcribeButtonView.setTextColor(if (transcribeActive) colors.textColorButton else colors.textColorPrimary)

        val bubbleColor = when (releaseAction) {
            AudioRecorderReleaseAction.CANCEL -> colors.buttonColorHangupDefault
            AudioRecorderReleaseAction.TRANSCRIBE,
            AudioRecorderReleaseAction.SEND_AUDIO -> colors.buttonColorPrimaryDefault
            else -> colors.buttonColorPrimaryDefault
        }
        bubbleView.setBubbleColor(bubbleColor)
        bubbleView.setContentColor(colors.textColorButton)
        bubbleView.setPowerLevel(powerLevel)
        bubbleView.setDuration(durationMs)

    }

    private fun statusTextFor(
        releaseAction: AudioRecorderReleaseAction,
        durationMs: Int?,
        maxDurationMs: Int,
    ): String {
        val countdown = durationMs?.let {
            AudioRecorderGesturePolicy.remainingSecondsBeforeAutoStop(it, maxDurationMs)
        }
        return when {
            releaseAction == AudioRecorderReleaseAction.CANCEL -> {
                context.getString(R.string.message_input_release_cancel_hint)
            }
            releaseAction == AudioRecorderReleaseAction.TRANSCRIBE -> {
                context.getString(R.string.message_input_release_transcribe_hint)
            }
            countdown != null -> {
                context.getString(R.string.message_input_auto_stop_countdown, countdown)
            }
            else -> context.getString(R.string.message_input_release_send_hint)
        }
    }

    private fun View.toGestureTarget(): AudioRecorderGestureTarget {
        return AudioRecorderGestureTarget(
            left = left.toFloat(),
            top = top.toFloat(),
            width = width.toFloat(),
            height = height.toFloat(),
        )
    }

    private fun setCircleBackground(view: View, color: Int) {
        view.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
        }
    }

    private fun applyThemedBackground(backgroundColor: Int = themeColors.bgColorOperate) {
        backgroundView.setGradientSpec(AudioRecorderOverlayBackgroundPolicy.createGradientSpec(backgroundColor))
    }

    fun dismiss() {
        if (!popupWindow.isShowing) {
            return
        }
        isDismissed = true
        rootView.animate().cancel()
        rootView.alpha = animationSpec.exitStartAlpha.coerceAtMost(rootView.alpha.coerceAtLeast(0f))
        rootView.animate()
            .alpha(animationSpec.exitEndAlpha)
            .setDuration(animationSpec.exitDurationMs)
            .setInterpolator(animationInterpolator)
            .withEndAction {
                if (popupWindow.isShowing) {
                    popupWindow.dismiss()
                }
            }
            .start()
    }
}

internal data class AudioRecorderOverlayBubbleLayout(
    val leftMargin: Int,
    val rightMargin: Int,
    val bottomMargin: Int,
    val height: Int,
)

internal data class AudioRecorderOverlayAnimationSpec(
    val enterStartAlpha: Float,
    val enterEndAlpha: Float,
    val exitStartAlpha: Float,
    val exitEndAlpha: Float,
    val enterDurationMs: Long,
    val exitDurationMs: Long,
)

internal object AudioRecorderOverlayAnimationPolicy {
    fun createSpec(): AudioRecorderOverlayAnimationSpec {
        return AudioRecorderOverlayAnimationSpec(
            enterStartAlpha = 0.92f,
            enterEndAlpha = 1f,
            exitStartAlpha = 1f,
            exitEndAlpha = 0f,
            enterDurationMs = 120L,
            exitDurationMs = 100L,
        )
    }
}

internal object AudioRecorderOverlayBubbleLayoutPolicy {
    fun calculate(
        rootWidth: Int,
        rootHeight: Int,
        anchorLeft: Int,
        anchorTop: Int,
        anchorWidth: Int,
        anchorHeight: Int,
        defaultHorizontalMargin: Int,
        defaultBottomMargin: Int,
        bubbleHeight: Int,
    ): AudioRecorderOverlayBubbleLayout {
        if (rootWidth <= 0 || rootHeight <= 0 || anchorWidth <= 0 || anchorHeight <= 0) {
            return AudioRecorderOverlayBubbleLayout(
                leftMargin = defaultHorizontalMargin,
                rightMargin = defaultHorizontalMargin,
                bottomMargin = defaultBottomMargin,
                height = bubbleHeight,
            )
        }
        val anchorBottom = anchorTop + anchorHeight
        return AudioRecorderOverlayBubbleLayout(
            leftMargin = defaultHorizontalMargin,
            rightMargin = defaultHorizontalMargin,
            bottomMargin = (rootHeight - anchorBottom).coerceAtLeast(0),
            height = anchorHeight,
        )
    }
}

internal enum class GradientDirection {
    TOP_TO_BOTTOM,
}

internal data class OverlayGradientSpec(
    val colors: IntArray,
    val positions: FloatArray,
    val direction: GradientDirection,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is OverlayGradientSpec) return false
        return colors.contentEquals(other.colors) &&
            positions.contentEquals(other.positions) &&
            direction == other.direction
    }

    override fun hashCode(): Int {
        var result = colors.contentHashCode()
        result = 31 * result + positions.contentHashCode()
        result = 31 * result + direction.hashCode()
        return result
    }
}

internal object AudioRecorderOverlayBackgroundPolicy {
    fun createGradientSpec(backgroundColor: Int): OverlayGradientSpec {
        return OverlayGradientSpec(
            colors = intArrayOf(
                ColorUtils.setAlphaComponent(backgroundColor, 0x00),
                ColorUtils.setAlphaComponent(backgroundColor, 0xB3),
                backgroundColor,
                backgroundColor,
            ),
            positions = floatArrayOf(0f, 0.42f, 0.67f, 1f),
            direction = GradientDirection.TOP_TO_BOTTOM,
        )
    }
}

internal object AudioRecorderCancelBoundaryPolicy {
    fun calculateBoundaryRawY(touchDownRawY: Float, cancelTouchSlopDp: Int, density: Float): Float {
        return touchDownRawY - cancelTouchSlopDp * density
    }

    fun calculateBoundaryLocalY(boundaryRawY: Float, overlayTopRawY: Int): Float {
        return boundaryRawY - overlayTopRawY
    }
}

internal class OverlayGradientBackgroundView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var spec = AudioRecorderOverlayBackgroundPolicy.createGradientSpec(Color.TRANSPARENT)
    private var shaderHeight = -1

    fun setGradientSpec(gradientSpec: OverlayGradientSpec) {
        if (spec == gradientSpec) {
            return
        }
        spec = gradientSpec
        shaderHeight = -1
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        if (height <= 0 || width <= 0) {
            return
        }
        if (shaderHeight != height) {
            paint.shader = LinearGradient(
                0f,
                0f,
                0f,
                height.toFloat(),
                spec.colors,
                spec.positions,
                Shader.TileMode.CLAMP,
            )
            shaderHeight = height
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
    }
}

internal data class AudioRecorderWaveformLayout(
    val startX: Float,
    val totalWidth: Float,
) {
    val waveformCenterX: Float = startX + totalWidth / 2f
}

internal object AudioRecorderWaveformLayoutPolicy {
    fun calculate(
        containerWidth: Float,
        barCount: Int,
        barWidth: Float,
        gap: Float,
    ): AudioRecorderWaveformLayout {
        val totalWidth = barCount * barWidth + (barCount - 1).coerceAtLeast(0) * gap
        return AudioRecorderWaveformLayout(
            startX = (containerWidth - totalWidth) / 2f,
            totalWidth = totalWidth,
        )
    }
}

private class BubbleView(context: Context) : View(context) {
    private val density = context.resources.displayMetrics.density
    private val bubblePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val waveformPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.TRANSPARENT
        style = Paint.Style.FILL
    }
    private val cornerRadius = 12f * density

    private var bubbleColor = Color.TRANSPARENT
    private var powerLevel: Int = 0
    private var durationText: String = AudioRecorderDurationTextPolicy.formatDuration(null)
    private val durationPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.TRANSPARENT
        textAlign = Paint.Align.RIGHT
        textSize = 12f * density
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private val totalBars = 24
    private val phases: FloatArray
    private val freqs: FloatArray

    private val startTimeNanos = System.nanoTime()
    private var animator: ValueAnimator? = null

    init {
        bubblePaint.color = bubbleColor
        bubblePaint.style = Paint.Style.FILL

        val rng = java.util.Random(20260421L)
        phases = FloatArray(totalBars) { rng.nextFloat() * (Math.PI * 2).toFloat() }
        freqs = FloatArray(totalBars) { 2.4f + rng.nextFloat() * 3.2f }
    }

    fun setBubbleColor(color: Int) {
        if (bubbleColor != color) {
            bubbleColor = color
            bubblePaint.color = color
            invalidate()
        }
    }

    fun setContentColor(color: Int) {
        waveformPaint.color = color
        durationPaint.color = color
        invalidate()
    }

    fun setPowerLevel(level: Int) {
        powerLevel = level.coerceIn(0, 100)
    }

    fun setDuration(durationMs: Int?) {
        durationText = AudioRecorderDurationTextPolicy.formatDuration(durationMs)
        invalidate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        animator?.cancel()
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1000L
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            interpolator = LinearInterpolator()
            addUpdateListener { invalidate() }
            start()
        }
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        animator = null
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        canvas.drawRoundRect(0f, 0f, w, h, cornerRadius, cornerRadius, bubblePaint)
        drawWaveform(canvas, w, h)
        drawDuration(canvas, w, h)
    }

    private fun drawWaveform(canvas: Canvas, w: Float, bodyBottom: Float) {
        val centerY = bodyBottom / 2f
        val barWidth = 2.5f * density
        val gap = 3f * density
        val layout = AudioRecorderWaveformLayoutPolicy.calculate(
            containerWidth = w,
            barCount = totalBars,
            barWidth = barWidth,
            gap = gap,
        )

        val t = (System.nanoTime() - startTimeNanos) / 1_000_000_000.0
        val amplitude = 13f * density * (0.45f + (powerLevel / 100f) * 0.55f)
        val minH = 3f * density

        for (i in 0 until totalBars) {
            val phase = phases[i].toDouble()
            val freq = freqs[i].toDouble()
            val wave = abs(sin(t * freq + phase)).toFloat()
            val barH = minH + amplitude * wave
            val x = layout.startX + i * (barWidth + gap)
            val top = centerY - barH / 2f
            val bottom = centerY + barH / 2f
            canvas.drawRoundRect(
                x,
                top,
                x + barWidth,
                bottom,
                barWidth / 2f,
                barWidth / 2f,
                waveformPaint,
            )
        }
    }

    private fun drawDuration(canvas: Canvas, w: Float, h: Float) {
        val centerY = h / 2f
        val textY = centerY - (durationPaint.descent() + durationPaint.ascent()) / 2f
        val padding = 16f * density
        if (layoutDirection == View.LAYOUT_DIRECTION_RTL) {
            durationPaint.textAlign = Paint.Align.LEFT
            canvas.drawText(durationText, padding, textY, durationPaint)
        } else {
            durationPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText(durationText, w - padding, textY, durationPaint)
        }
    }

}

internal object AudioRecorderDurationTextPolicy {
    private const val PLACEHOLDER = "--:--"

    fun formatDuration(durationMs: Int?): String {
        if (durationMs == null) {
            return PLACEHOLDER
        }
        return ChatDateTimeUtils.formatDurationMillis(durationMs.toLong())
    }
}

private class CancelButtonView(context: Context) : View(context) {
    private val density = context.resources.displayMetrics.density
    private val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val xPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * density
        strokeCap = Paint.Cap.ROUND
    }

    private var idleColor: Int = Color.TRANSPARENT
    private var activeColor: Int = Color.TRANSPARENT

    private var active = false
    private var rotationAnimator: ValueAnimator? = null

    private val idleRotationDeg = -30f
    private val activeRotationDeg = 0f

    init {
        rotation = idleRotationDeg
        circlePaint.color = idleColor
    }

    fun setColors(idle: Int, active: Int, icon: Int) {
        if (idleColor == idle && activeColor == active && xPaint.color == icon) {
            return
        }
        idleColor = idle
        activeColor = active
        xPaint.color = icon
        circlePaint.color = if (this.active) activeColor else idleColor
        invalidate()
    }

    fun setActive(activeState: Boolean) {
        if (active == activeState) return
        active = activeState
        circlePaint.color = if (activeState) activeColor else idleColor
        animateRotationTo(if (activeState) activeRotationDeg else idleRotationDeg)
        invalidate()
    }

    private fun animateRotationTo(target: Float) {
        rotationAnimator?.cancel()
        rotationAnimator = ValueAnimator.ofFloat(rotation, target).apply {
            duration = 260L
            interpolator = OvershootInterpolator(1.6f)
            addUpdateListener { animator ->
                rotation = animator.animatedValue as Float
            }
            start()
        }
    }

    override fun onDetachedFromWindow() {
        rotationAnimator?.cancel()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val cx = w / 2f
        val cy = h / 2f
        val radius = minOf(w, h) / 2f - 1f
        canvas.drawCircle(cx, cy, radius, circlePaint)

        val armLen = radius * 0.42f
        canvas.drawLine(cx - armLen, cy - armLen, cx + armLen, cy + armLen, xPaint)
        canvas.drawLine(cx + armLen, cy - armLen, cx - armLen, cy + armLen, xPaint)
    }
}

private class ArcView(context: Context) : View(context) {
    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = context.resources.displayMetrics.density
    }
    private var arcColor = Color.TRANSPARENT
    private var strokeColor = Color.TRANSPARENT
    private var boundaryLocalY: Float? = null

    fun setColors(fillColor: Int, borderColor: Int) {
        if (arcColor == fillColor && strokeColor == borderColor) {
            return
        }
        arcColor = fillColor
        strokeColor = borderColor
        invalidate()
    }

    fun setBoundaryLocalY(boundaryY: Float) {
        if (boundaryLocalY == boundaryY) {
            return
        }
        boundaryLocalY = boundaryY
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val radius = w * 1.6f
        val cx = w / 2f
        val fallbackBoundaryY = h - 99f * resources.displayMetrics.density
        val cy = (boundaryLocalY ?: fallbackBoundaryY) + radius
        arcPaint.color = arcColor
        strokePaint.color = strokeColor
        canvas.drawCircle(cx, cy, radius, arcPaint)
        canvas.drawCircle(cx, cy, radius, strokePaint)
    }
}
