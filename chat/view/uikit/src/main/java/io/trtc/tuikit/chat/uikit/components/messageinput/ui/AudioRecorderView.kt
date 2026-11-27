package io.trtc.tuikit.chat.uikit.components.messageinput.ui
import android.animation.ValueAnimator
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
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
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ScrollView
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
import io.trtc.tuikit.chat.uikit.components.messageinput.asr.RealtimeAsrAudioCapture
import io.trtc.tuikit.chat.uikit.components.messageinput.asr.RealtimeAsrRecognizer
import io.trtc.tuikit.atomicx.theme.ThemeStore
import io.trtc.tuikit.atomicx.widget.basicwidget.toast.AtomicToast
import java.util.Locale

data class AudioRecorderViewConfig(
    val minDurationMs: Int = 1000,
    val maxDurationMs: Int = 60000,
    val cancelTouchSlopDp: Int = 60,
    val startOnLongPress: Boolean = true,
    /**
     * 滑到「转文字」时是否边说边出字。关掉的话行为退回「松手后上传整段音频再识别」。
     * 机型不支持实时采集时也会自动退回，不需要接入方关心。
     */
    val enableRealtimeVoiceToText: Boolean = true,
    /** 松手是否发送原语音。关掉后中间区域松手取消，提示改为滑到转文字。 */
    val enableReleaseToSendVoice: Boolean = true,
    /** 实时识别语言，ISO 639-1，如 "zh"、"en"；留空交给后台判断。 */
    val realtimeVoiceToTextLanguage: String = Locale.getDefault().language.orEmpty(),
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

    /** 本次长按的实时识别，随录音一起启停。 */
    private var realtimeRecognizer: RealtimeAsrRecognizer? = null

    /** 已松手在「转文字」上、等调用方来取结果的那个识别器。 */
    private var pendingRealtimeRecognizer: RealtimeAsrRecognizer? = null
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

    /**
     * 取走本次「转文字」松手对应的实时识别结果，只能在 [AudioRecorderViewResultListener] 回调里调用一次。
     *
     * 结果分两步给，因为 ASR 是流式的、松手时尾句往往还在路上：
     * - [onPartialText] 同步回调，带松手那一刻已经识别出的文本。已经识别出来的部分没有理由
     *   让用户再等一遍，调用方应当拿它直接把界面铺成最终形态。
     * - [onFinalText] 在收尾窗口结束后回调（数百毫秒量级），带补齐尾句的完整文本。
     *
     * @return 本次长按没有跑实时识别（关掉了、机型不支持、会话没建起来）时返回 false，
     *   此时两个回调都不会被调用，调用方应当回落到「上传整段音频再识别」
     */
    fun consumeRealtimeTranscription(
        onPartialText: (String) -> Unit,
        onFinalText: (String) -> Unit,
    ): Boolean {
        val recognizer = pendingRealtimeRecognizer ?: return false
        pendingRealtimeRecognizer = null
        // 必须排在 finish 之前：会话没建起来时 finish 会同步回调 onFinalText，
        // 顺序反了就成了「先更新一个还没铺出来的界面」
        onPartialText(recognizer.text)
        recognizer.finish(onFinalText)
        return true
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
        pendingReleaseAction = resolveReleaseAction(obtainOverlay().releaseActionFor(rawX, rawY))
        if (pendingReleaseAction == AudioRecorderReleaseAction.CANCEL) {
            AudioRecorder.cancelRecord()
        } else {
            AudioRecorder.stopRecord()
        }
    }

    override fun onDetachedFromWindow() {
        cancelRecording()
        pendingRealtimeRecognizer?.cancel()
        pendingRealtimeRecognizer = null
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
                    pendingReleaseAction = resolveReleaseAction(
                        obtainOverlay().releaseActionFor(event.rawX, event.rawY)
                    )
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
        // 必须排在 startRecord 之后：语音消息本身才是主角，先让它把麦克风占住，
        // 万一机型不允许同一应用并发采集，让步的是实时识别而不是这条语音。
        if (isRecording) {
            primeRealtimeRecognizer()
        }
    }

    /**
     * 录音一开始就把麦克风的实时通路和 ASR 会话都拉起来。
     *
     * 会话不能等到手指滑到「转文字」再建：startASR 是一个网络往返，那几百毫秒到几秒会
     * 原样变成用户滑过去之后盯着空气等的时间。
     */
    private fun primeRealtimeRecognizer() {
        realtimeRecognizer?.cancel()
        realtimeRecognizer = null
        obtainOverlay().setRealtimeRecognitionActive(false)
        if (!config.enableRealtimeVoiceToText || !RealtimeAsrAudioCapture.isSupported()) {
            return
        }
        val recognizer = RealtimeAsrRecognizer(
            language = config.realtimeVoiceToTextLanguage,
        ) { text ->
            if (isRecording) {
                obtainOverlay().updateRecognizedText(text)
            }
        }
        if (recognizer.prime()) {
            realtimeRecognizer = recognizer
            obtainOverlay().setRealtimeRecognitionActive(true)
        }
    }

    private fun handleRecordResult(result: AudioRecorderResult) {
        pendingReleaseAction = resolveReleaseAction(pendingReleaseAction)
        val recognizer = realtimeRecognizer
        realtimeRecognizer = null
        if (recognizer != null) {
            // 只有「在转文字上松手且录音本身成功」才值得把识别结果留给调用方，其余一律丢弃
            if (pendingReleaseAction == AudioRecorderReleaseAction.TRANSCRIBE &&
                result.isSuccess &&
                recognizer.isRecognizing
            ) {
                pendingRealtimeRecognizer = recognizer
            } else {
                recognizer.cancel()
            }
        }
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
        // 调用方没来取就说明它不打算用，别让识别器和麦克风一直挂着
        pendingRealtimeRecognizer?.cancel()
        pendingRealtimeRecognizer = null
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

    private fun resolveReleaseAction(action: AudioRecorderReleaseAction): AudioRecorderReleaseAction {
        if (!config.enableReleaseToSendVoice && action == AudioRecorderReleaseAction.SEND_AUDIO) {
            return AudioRecorderReleaseAction.CANCEL
        }
        return action
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
        // 会话按下时就建好了，这里只是打开上行；滑走再滑回来不会重开，识别一直连着
        if (nextState == RecordUiState.READY_TO_TRANSCRIBE) {
            realtimeRecognizer?.beginRecognize()
        }
        obtainOverlay().update(
            currentUiState,
            if (hasRecorderStarted) AudioRecorder.currentTimeMs.value else null,
            AudioRecorder.currentPower.value,
            config.maxDurationMs,
        )
    }

    private fun resetToIdleState() {
        realtimeRecognizer?.cancel()
        realtimeRecognizer = null
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
        val current = overlay ?: AudioRecorderOverlay(anchorView).also {
            overlay = it
            overlayAnchorView = anchorView
        }
        current.allowReleaseToSendVoice = config.enableReleaseToSendVoice
        return current
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
    var allowReleaseToSendVoice: Boolean = true
    private val context = anchorView.context
    private val density = context.resources.displayMetrics.density

    private val themeColors
        get() = ThemeStore.shared(context).themeState.value.currentTheme.tokens.color

    private val rootView: FrameLayout = FrameLayout(context)
    private val backgroundView = OverlayGradientBackgroundView(context)

    private val animationSpec = AudioRecorderOverlayAnimationPolicy.createSpec()
    private val animationInterpolator = DecelerateInterpolator()
    private var isDismissed = false
    private val bubbleView = RecognitionBubbleView(context).apply {
        val colors = themeColors
        setBubbleColor(colors.buttonColorPrimaryDefault)
        setContentColor(colors.textColorButton)
    }
    private var lastReleaseAction = AudioRecorderReleaseAction.SEND_AUDIO
    private var realtimeActive = false
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

        // 识别气泡浮在屏幕中部、按钮行上方，底边固定、内容多了往上长
        val bubbleLp = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM or Gravity.START,
        )
        bubbleLp.bottomMargin = (BUBBLE_BOTTOM_MARGIN_DP * density).toInt()
        rootView.addView(bubbleView, bubbleLp)
        bubbleView.setMaxContentHeight((BUBBLE_MAX_TEXT_HEIGHT_DP * density).toInt())
        updateBubbleGeometry()
    }

    fun show() {
        if (popupWindow.isShowing || !anchorView.isAttachedToWindow) {
            return
        }
        isDismissed = false
        rootView.animate().cancel()
        rootView.alpha = animationSpec.enterStartAlpha
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
                updateBubbleGeometry()
            }
        }
    }

    /** 实时识别每吐一次字就刷新一次气泡。 */
    fun updateRecognizedText(text: String) {
        bubbleView.setRecognizedText(text)
    }

    /**
     * 本次长按有没有跑实时识别。没跑（开关关了、机型不支持、采集起不来）时气泡不铺开，
     * 否则用户会对着一个永远停在「识别中」的空框等下去。
     */
    fun setRealtimeRecognitionActive(value: Boolean) {
        if (realtimeActive == value) {
            return
        }
        realtimeActive = value
        bubbleView.setRecognizedText("")
        updateBubbleGeometry()
    }

    /**
     * 按当前释放动作摆放气泡：默认居中、滑到「转文字」时铺满并指向该按钮、
     * 滑到「取消」时缩小左移并指向取消按钮。
     *
     * 位置一律用 marginStart + Gravity.START 表达，RTL 由系统镜像；只有画在 canvas 上的
     * 箭头要自己换算成物理坐标。
     */
    private fun updateBubbleGeometry() {
        val rootWidth = rootView.width.takeIf { it > 0 }
            ?: context.resources.displayMetrics.widthPixels
        val actionCenterInset = (ACTION_CENTER_INSET_DP * density).toInt()
        // 手指一到「转文字」就铺开，不等第一个识别结果——首字还要等一个后台往返，
        // 拿它当铺开的触发条件，用户看到的就是「滑过来了但什么都没发生」。等字期间显示占位文案。
        val expanded = realtimeActive && lastReleaseAction == AudioRecorderReleaseAction.TRANSCRIBE
        val edgeMargin = (BUBBLE_EDGE_MARGIN_DP * density).toInt()

        val width: Int
        val marginStart: Int
        val arrowFromStart: Float
        when {
            expanded -> {
                width = (rootWidth - edgeMargin * 2).coerceAtLeast(1)
                marginStart = edgeMargin
                arrowFromStart = (rootWidth - actionCenterInset - edgeMargin).toFloat()
            }
            lastReleaseAction == AudioRecorderReleaseAction.CANCEL -> {
                width = (COMPACT_BUBBLE_WIDTH_DP * density).toInt()
                    .coerceAtMost((rootWidth - edgeMargin * 2).coerceAtLeast(1))
                val maxStart = (rootWidth - width - edgeMargin).coerceAtLeast(edgeMargin)
                marginStart = (actionCenterInset - width / 2).coerceIn(edgeMargin, maxStart)
                arrowFromStart = (actionCenterInset - marginStart).toFloat()
            }
            else -> {
                width = (COMPACT_BUBBLE_WIDTH_DP * density).toInt()
                    .coerceAtMost((rootWidth - edgeMargin * 2).coerceAtLeast(1))
                marginStart = ((rootWidth - width) / 2).coerceAtLeast(edgeMargin)
                arrowFromStart = width / 2f
            }
        }

        val layoutParams = bubbleView.layoutParams as FrameLayout.LayoutParams
        if (layoutParams.width != width || layoutParams.marginStart != marginStart) {
            layoutParams.width = width
            layoutParams.marginStart = marginStart
            bubbleView.layoutParams = layoutParams
        }
        val isRtl = rootView.layoutDirection == View.LAYOUT_DIRECTION_RTL
        bubbleView.setArrowCenterX(if (isRtl) width - arrowFromStart else arrowFromStart)
        bubbleView.setExpanded(expanded)
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
        lastReleaseAction = releaseAction
        val statusText = statusTextFor(releaseAction, durationMs, maxDurationMs)
        statusTextView.text = statusText
        statusTextView.visibility = if (statusText.isEmpty()) View.GONE else View.VISIBLE
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
        updateBubbleGeometry()
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
                // 气泡已经在出字，按钮上也写着「转文字」，这行提示是重复的。
                // 实时识别没起来时仍要告诉用户松手才会转文字。
                if (realtimeActive) {
                    ""
                } else {
                    context.getString(R.string.message_input_release_transcribe_hint)
                }
            }
            countdown != null -> {
                context.getString(R.string.message_input_auto_stop_countdown, countdown)
            }
            !allowReleaseToSendVoice -> {
                context.getString(R.string.message_input_slide_to_transcribe_hint)
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

    private companion object {
        /** 气泡底边（箭头尖）离屏幕底部的距离，压在提示文案上方。 */
        const val BUBBLE_BOTTOM_MARGIN_DP = 231

        /** 只有波形时的气泡宽度。 */
        const val COMPACT_BUBBLE_WIDTH_DP = 200

        const val BUBBLE_EDGE_MARGIN_DP = 16

        /** 取消 / 转文字两个圆形按钮的中心离各自那一侧屏幕边缘的距离：72 外边距 + 80 直径的一半。 */
        const val ACTION_CENTER_INSET_DP = 112

        /** 实时文本最多占这么高，再多就内部滚动，免得气泡顶出屏幕。 */
        const val BUBBLE_MAX_TEXT_HEIGHT_DP = 220
    }
}

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

/**
 * 长按录音时浮在屏幕中部的识别气泡：圆角矩形 + 朝下的箭头。
 *
 * 两种形态共用一个 View——没有识别文本时只画波形（箭头居中，气泡居中或在取消按钮上方），
 * 有实时文本时文本在上、波形缩到右下角，箭头指向「转文字」按钮。形态切换由外部通过
 * [setExpanded] / [setArrowCenterX] 与 layoutParams 驱动，这里只负责画和排内容。
 */
private class RecognitionBubbleView(context: Context) : FrameLayout(context) {
    private val density = context.resources.displayMetrics.density
    private val bubblePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val path = Path()
    private val cornerRadius = 12f * density
    private val arrowHeight = 8f * density
    private val arrowHalfWidth = 8f * density

    private var bubbleColor = Color.TRANSPARENT
    private var arrowCenterX: Float? = null
    private var expanded = false
    private var recognizedText = ""
    private val placeholderText = context.getString(R.string.message_input_realtime_transcribing)

    // 显隐由外层的 textScrollView 统一控制，这里不要再设 GONE——设了就再也没人打开它
    private val textView = TextView(context).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        includeFontPadding = false
        setLineSpacing(4f * density, 1f)
    }
    private val textScrollView = MaxHeightScrollView(context).apply {
        isVerticalScrollBarEnabled = false
        overScrollMode = View.OVER_SCROLL_NEVER
        visibility = View.GONE
        addView(
            textView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
            ),
        )
    }
    private val waveformView = RecorderWaveformView(context)
    private val durationView = TextView(context).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        includeFontPadding = false
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        text = AudioRecorderDurationTextPolicy.formatDuration(null)
    }
    private val bottomRow = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }
    private val contentColumn = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
    }

    init {
        setWillNotDraw(false)
        applyTextContent()
        bottomRow.addView(
            waveformView,
            LinearLayout.LayoutParams((96 * density).toInt(), (20 * density).toInt()),
        )
        bottomRow.addView(
            durationView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { marginStart = (8 * density).toInt() },
        )
        contentColumn.addView(
            textScrollView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ),
        )
        contentColumn.addView(
            bottomRow,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { gravity = Gravity.CENTER_HORIZONTAL },
        )
        addView(
            contentColumn,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT),
        )
        applyContentPadding()
    }

    fun setBubbleColor(color: Int) {
        if (bubbleColor != color) {
            bubbleColor = color
            bubblePaint.color = color
            invalidate()
        }
    }

    fun setContentColor(color: Int) {
        textView.setTextColor(color)
        durationView.setTextColor(color)
        waveformView.setBarColor(color)
    }

    fun setPowerLevel(level: Int) {
        waveformView.setPowerLevel(level)
    }

    fun setDuration(durationMs: Int?) {
        durationView.text = AudioRecorderDurationTextPolicy.formatDuration(durationMs)
    }

    fun setMaxContentHeight(maxHeightPx: Int) {
        textScrollView.maxHeightPx = maxHeightPx
    }

    fun setRecognizedText(text: String) {
        if (recognizedText == text) {
            return
        }
        recognizedText = text
        applyTextContent()
        if (text.isNotEmpty() && textScrollView.visibility == View.VISIBLE) {
            // 新识别出的字追加在末尾，让视口跟着走
            textScrollView.post { textScrollView.fullScroll(View.FOCUS_DOWN) }
        }
    }

    /** 还没出字时先占个位，免得铺开的气泡是一片空白。 */
    private fun applyTextContent() {
        val waiting = recognizedText.isEmpty()
        textView.text = if (waiting) placeholderText else recognizedText
        textView.alpha = if (waiting) PLACEHOLDER_ALPHA else 1f
    }

    fun setArrowCenterX(x: Float) {
        if (arrowCenterX != x) {
            arrowCenterX = x
            invalidate()
        }
    }

    fun setExpanded(value: Boolean) {
        if (expanded == value) {
            return
        }
        expanded = value
        // 铺开之后文本是主角，波形缩到行尾当状态指示，时长让位
        textScrollView.visibility = if (value) View.VISIBLE else View.GONE
        durationView.visibility = if (value) View.GONE else View.VISIBLE
        (bottomRow.layoutParams as LinearLayout.LayoutParams).gravity =
            if (value) Gravity.END else Gravity.CENTER_HORIZONTAL
        waveformView.layoutParams = waveformView.layoutParams.apply {
            width = ((if (value) 40 else 96) * density).toInt()
        }
        requestLayout()
    }

    private companion object {
        const val PLACEHOLDER_ALPHA = 0.6f
    }

    override fun onDraw(canvas: Canvas) {
        val bodyBottom = height - arrowHeight
        if (bodyBottom <= 0f) {
            return
        }
        path.reset()
        path.addRoundRect(
            RectF(0f, 0f, width.toFloat(), bodyBottom),
            cornerRadius,
            cornerRadius,
            Path.Direction.CW,
        )
        val centerX = (arrowCenterX ?: (width / 2f))
            .coerceIn(cornerRadius + arrowHalfWidth, (width - cornerRadius - arrowHalfWidth).coerceAtLeast(0f))
        path.moveTo(centerX - arrowHalfWidth, bodyBottom)
        path.lineTo(centerX, height.toFloat())
        path.lineTo(centerX + arrowHalfWidth, bodyBottom)
        path.close()
        canvas.drawPath(path, bubblePaint)
        super.onDraw(canvas)
    }

    private fun applyContentPadding() {
        val horizontal = (16 * density).toInt()
        val vertical = (12 * density).toInt()
        setPaddingRelative(horizontal, vertical, horizontal, vertical + arrowHeight.toInt())
    }
}

/** 内容超过 [maxHeightPx] 时改为内部滚动，避免气泡无限往上长。 */
private class MaxHeightScrollView(context: Context) : ScrollView(context) {
    var maxHeightPx = 0

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val spec = if (maxHeightPx > 0) {
            MeasureSpec.makeMeasureSpec(maxHeightPx, MeasureSpec.AT_MOST)
        } else {
            heightMeasureSpec
        }
        super.onMeasure(widthMeasureSpec, spec)
    }
}

/** 气泡里那排随音量起伏的竖条。 */
private class RecorderWaveformView(context: Context) : View(context) {
    private val density = context.resources.displayMetrics.density
    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.TRANSPARENT
    }
    private val totalBars = 20
    private val phases: FloatArray
    private val freqs: FloatArray
    private val startTimeNanos = System.nanoTime()
    private var powerLevel = 0
    private var animator: ValueAnimator? = null

    init {
        val rng = java.util.Random(20260421L)
        phases = FloatArray(totalBars) { rng.nextFloat() * (Math.PI * 2).toFloat() }
        freqs = FloatArray(totalBars) { 2.4f + rng.nextFloat() * 3.2f }
    }

    fun setBarColor(color: Int) {
        barPaint.color = color
        invalidate()
    }

    fun setPowerLevel(level: Int) {
        powerLevel = level.coerceIn(0, 100)
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
        val centerY = height / 2f
        val barWidth = 2.5f * density
        val gap = 2.5f * density
        val layout = AudioRecorderWaveformLayoutPolicy.calculate(
            containerWidth = w,
            barCount = totalBars,
            barWidth = barWidth,
            gap = gap,
        )
        val t = (System.nanoTime() - startTimeNanos) / 1_000_000_000.0
        val amplitude = (height / 2f) * 0.9f * (0.45f + (powerLevel / 100f) * 0.55f)
        val minH = 3f * density
        for (i in 0 until totalBars) {
            val wave = abs(sin(t * freqs[i].toDouble() + phases[i].toDouble())).toFloat()
            val barH = minH + amplitude * wave
            val x = layout.startX + i * (barWidth + gap)
            canvas.drawRoundRect(
                x,
                centerY - barH / 2f,
                x + barWidth,
                centerY + barH / 2f,
                barWidth / 2f,
                barWidth / 2f,
                barPaint,
            )
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
