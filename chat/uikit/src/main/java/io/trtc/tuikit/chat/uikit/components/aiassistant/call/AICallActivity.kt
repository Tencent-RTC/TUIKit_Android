package io.trtc.tuikit.chat.uikit.components.aiassistant.call

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.TypedValue
import android.view.Gravity
import android.widget.Space
import android.transition.AutoTransition
import android.transition.TransitionManager
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.updatePadding
import io.trtc.tuikit.atomicx.theme.ThemeStore
import io.trtc.tuikit.atomicx.widget.basicwidget.alertdialog.AtomicAlertDialog
import io.trtc.tuikit.atomicx.widget.basicwidget.alertdialog.confirmButton
import io.trtc.tuikit.chat.uikit.BuildConfig
import io.trtc.tuikit.chat.uikit.R
import io.trtc.tuikit.chat.uikit.components.aiassistant.call.ui.DebugOverlayView
import io.trtc.tuikit.chat.uikit.components.aiassistant.call.ui.TranscriptView
import io.trtc.tuikit.chat.uikit.components.aiassistant.call.ui.VoiceOrbView

/**
 * AI 语音通话页。
 *
 * 聆听、播放、文本三种形态是同一套控件的三种状态，不是三个页面。
 */
class AICallActivity : AppCompatActivity() {

    private val themeStore by lazy { ThemeStore.shared(this) }
    private lateinit var orb: VoiceOrbView
    private lateinit var statusText: TextView
    private lateinit var btnMic: ImageView
    private lateinit var btnSubtitle: ImageView
    private lateinit var btnHangup: ImageView
    private lateinit var btnDebug: TextView
    private lateinit var windowRoot: FrameLayout
    private lateinit var root: LinearLayout
    private lateinit var orbContainer: FrameLayout
    private lateinit var transcriptContainer: FrameLayout
    private lateinit var debugOverlay: DebugOverlayView

    /** 第一条非空文本到达、且字幕开着时才创建，空列表不必为它付开销 */
    private var transcriptView: TranscriptView? = null
    private var subtitleVisible = true
    private var debugVisible = false

    /** 字幕面板当前是否占着光球下方。避免每条 ASR 增量都重跑一遍布局动画。 */
    private var showingTranscriptPanel = false

    /**
     * 最近一次 transcript 快照。
     *
     * [TranscriptView] 是懒创建的，而 controller 只在内容变化时才回调；不留这份快照的话，
     * 展开字幕后会一直空着，直到下一条 ASR 分片或流式增量到达才突然填上。
     */
    private var latestEntries: List<TranscriptEntry> = emptyList()

    /** 最近一次渲染过的状态。静音开关要在状态没变的情况下重刷一次文案，见 [renderState]。 */
    private var currentState = AICallState.IDLE

    private var isMuted = false

    /** 外观与引导选项，见 [AICallUiOptions]。默认值即原样。 */
    private var uiOptions = AICallUiOptions()
    private lateinit var modePill: TextView
    private var hintText: TextView? = null
    private var micLabel: TextView? = null
    private var hintIndex = 0
    private var callStartedAt = 0L

    private val callTimerTicker = object : Runnable {
        override fun run() {
            if (callStartedAt == 0L) {
                return
            }
            val seconds = (SystemClock.elapsedRealtime() - callStartedAt) / 1000
            modePill.text = getString(
                R.string.uikit_ai_call_duration,
                String.format(java.util.Locale.US, "%02d:%02d", seconds / 60, seconds % 60),
            )
            modePill.postDelayed(this, 1000L)
        }
    }

    private val hintRotator = object : Runnable {
        override fun run() {
            val hint = hintText ?: return
            val hints = uiOptions.sampleHints
            if (hints.size > 1) {
                hint.animate().alpha(0f).setDuration(200L).withEndAction {
                    hintIndex = (hintIndex + 1) % hints.size
                    hint.text = getString(R.string.uikit_ai_call_try_saying, hints[hintIndex])
                    hint.animate().alpha(1f).setDuration(240L).start()
                }.start()
            }
            hint.postDelayed(this, HINT_INTERVAL_MS)
        }
    }

    /**
     * ASR 会话起不来，通话停在 IDLE 等用户手动重试。
     *
     * 这个状态下麦克风按钮是「重试」而不是「静音」：原先要连点两下（第一下静音、第二下
     * 解除静音才顺带重开会话）才能恢复，没人猜得到。
     */
    private var listeningFailed = false

    /**
     * [onStart] 与 [onStop] 之间为 true。
     *
     * 后台期间不接受音频焦点恢复：通话结束时若用户还停在别的 App 上，AUDIOFOCUS_GAIN 照样
     * 会来，此时把麦克风重新拉起来采到的是全零帧，正是 [onStop] 要避免的那件事。
     */
    private var visible = false

    private val debugTicker = object : Runnable {
        override fun run() {
            if (!debugVisible) {
                return
            }
            debugOverlay.setStatus(controller?.debugSnapshot().orEmpty())
            debugOverlay.postDelayed(this, DEBUG_TICK_MS)
        }
    }

    private var controller: AICallController? = null

    private var asrUnavailableDialog: AtomicAlertDialog? = null

    private val audioManager: AudioManager by lazy {
        getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    /** API 26+ 的焦点句柄，归还焦点时要用同一个对象 */
    private var audioFocusRequest: AudioFocusRequest? = null

    /** 进入通话模式前的全局音频模式，退出时原样还回去 */
    private var previousAudioMode = AudioManager.MODE_NORMAL
    private var audioSessionConfigured = false

    /**
     * 音频焦点变化。
     *
     * **回调线程**：`AudioManager` 没拿到 Handler 时，会把回调绑到调用 `requestAudioFocus`
     * 那条线程的 Looper 上（那条线程没有 Looper 才退回主线程）。本页两条申请路径都在主
     * 线程上，所以回调本来就落在主线程；API 26+ 仍然显式传一个主线程 Handler，让这个
     * 结论不再依赖「申请点碰巧在主线程」。这件事不能含糊：[AICallController] 的公开方法
     * 全是主线程契约，`pause()` / `resume()` 里要碰 IMSDK 的 ASR / TTS 会话。
     *
     * 永久丢失（AUDIOFOCUS_LOSS）同样只暂停不退出：用户从来电切回来时通话页还在，
     * 自己挂断比替他做决定好。DUCK 之类的其余取值对语音通话没有意义，不处理。
     */
    private val audioFocusListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> controller?.pause()

            AudioManager.AUDIOFOCUS_GAIN -> if (visible) controller?.resume()

            else -> Unit
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            onMicrophoneReady()
        } else {
            Toast.makeText(this, R.string.uikit_ai_call_permission_denied, Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        val colors = themeStore.themeState.value.currentTheme.tokens.color
        window.setBackgroundDrawable(ColorDrawable(colors.bgColorDefault))
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = isColorLight(colors.bgColorOperate)
        }
        setContentView(R.layout.uikit_ai_activity_call)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        windowRoot = findViewById(R.id.uikit_ai_call_window)
        root = findViewById(R.id.uikit_ai_call_root)
        ViewCompat.setOnApplyWindowInsetsListener(windowRoot) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(top = bars.top, bottom = bars.bottom)
            insets
        }

        orb = findViewById(R.id.uikit_ai_call_orb)
        modePill = findViewById(R.id.uikit_ai_call_mode_pill)
        @Suppress("DEPRECATION")
        uiOptions = (intent.getSerializableExtra(EXTRA_UI_OPTIONS) as? AICallUiOptions) ?: AICallUiOptions()
        if (uiOptions.auroraStyle) {
            orb.auroraStyle = true
            orb.auroraOnDark = true
            windowRoot.background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(0xFF1B1540.toInt(), 0xFF0B0B18.toInt(), 0xFF08080F.toInt()),
            )
        }
        statusText = findViewById(R.id.uikit_ai_call_status)
        btnMic = findViewById(R.id.uikit_ai_call_mic)
        btnSubtitle = findViewById(R.id.uikit_ai_call_subtitle)
        btnHangup = findViewById(R.id.uikit_ai_call_hangup)
        btnDebug = findViewById(R.id.uikit_ai_call_debug)
        orbContainer = findViewById(R.id.uikit_ai_call_orb_container)
        transcriptContainer = findViewById(R.id.uikit_ai_transcript_container)
        debugOverlay = findViewById(R.id.uikit_ai_call_debug_overlay)

        applyUiOptions()
        btnHangup.setOnClickListener { finish() }
        btnSubtitle.setOnClickListener { toggleSubtitle() }
        btnMic.setOnClickListener { onMicClicked() }
        btnDebug.visibility = View.GONE
        btnDebug.setOnClickListener { toggleDebugOverlay() }
        applySubtitleVisibility()

        renderState(AICallState.IDLE)
        ensureMicrophonePermission()
    }

    private fun isColorLight(color: Int): Boolean {
        val red = (color shr 16) and 0xFF
        val green = (color shr 8) and 0xFF
        val blue = color and 0xFF
        return (0.299 * red + 0.587 * green + 0.114 * blue) / 255 > 0.5
    }

    private fun ensureMicrophonePermission() {
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            onMicrophoneReady()
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun onMicrophoneReady() {
        // 先要焦点再开麦克风：其他 App 的音乐要先暂停下来，采集才不会把它一起录进去
        requestAudioFocus()
        val robotUserId = intent.getStringExtra(EXTRA_ROBOT_USER_ID).orEmpty()
        if (robotUserId.isEmpty()) {
            finish()
            return
        }
        if (uiOptions.showCallTimer) {
            callStartedAt = SystemClock.elapsedRealtime()
            modePill.removeCallbacks(callTimerTicker)
            callTimerTicker.run()
        }
        val newController = AICallController(robotUserId, object : AICallController.Callbacks {
            override fun onStateChanged(state: AICallState) {
                if (state != AICallState.IDLE) {
                    listeningFailed = false
                }
                renderState(state)
            }

            override fun onTranscriptChanged(entries: List<TranscriptEntry>) {
                latestEntries = entries
                transcriptView?.submit(entries)
                updateHintVisibility()
                // 第一条非空文本到达时才把光球让到上方；空列表时保持居中
                applySubtitleVisibility()
            }

            override fun onAudioLevel(level: Float) {
                orb.setLevel(level)
            }

            override fun onError(@StringRes messageResId: Int) {
                Toast.makeText(this@AICallActivity, messageResId, Toast.LENGTH_SHORT).show()
            }

            override fun onListeningFailed() {
                listeningFailed = true
                // 状态往往本来就是 IDLE，这种情况下不会有 onStateChanged，文案得自己刷
                renderState(currentState)
            }

            override fun onAsrServiceUnavailable() {
                showAsrServiceUnavailableDialog()
            }

            override fun onDebugLog(line: String) {
                // 浮层在 release 打不开，这里再攒行就是纯浪费
                if (BuildConfig.DEBUG) {
                    debugOverlay.appendLog(line)
                }
            }
        })
        if (!newController.start()) {
            // 起不来就走不下去了，别把焦点攥在手里不放
            abandonAudioFocus()
            finish()
            return
        }
        controller = newController
    }

    /**
     * 麦克风按钮有两种含义，按当前是不是「启动失败」分派。
     *
     * 失败态下重试优先：那时通话已经停住了，用户要的是让它重新跑起来，不是静音一个
     * 本来就没在听的麦克风。静音中不走这一支——先解除静音，[AICallController.setMuted]
     * 自己会重开会话。
     *
     * [listeningFailed] 不在这里乐观清掉：重试成功会走到 THINKING，由 `onStateChanged`
     * 统一清；重试被拒（例如来电期间暂停中）则原样留着「点麦克风重试」的提示，
     * 比换成一句「正在听…」诚实。
     */
    private fun onMicClicked() {
        if (listeningFailed && !isMuted) {
            controller?.retryListening()
            return
        }
        toggleMute()
    }

    /**
     * 服务不可用时通话页没有任何可做的事，只给「退出通话」，点外部和返回键都关不掉。
     * 通话中途资源包耗尽也走这里：下一轮建会话时才会发现。
     */
    private fun showAsrServiceUnavailableDialog() {
        if (isFinishing || isDestroyed || asrUnavailableDialog?.isShowing() == true) {
            return
        }
        asrUnavailableDialog = AtomicAlertDialog(this).apply {
            init {
                title = getString(R.string.uikit_ai_call_asr_unavailable_title)
                content = getString(R.string.uikit_ai_call_asr_unavailable_content)
                confirmButton(getString(R.string.uikit_ai_call_asr_unavailable_exit)) { _ ->
                    finish()
                }
            }
            show()
        }
    }

    /**
     * 麦克风静音开关。
     *
     * [isMuted] 要在通知 controller **之前**翻转：`setMuted(true)` 会同步回调一次
     * `onStateChanged(IDLE)`，而 [renderState] 要靠这个标志才知道该显示「已静音」
     * 而不是 IDLE 对应的「正在听…」。后面再补一次 [renderState] 是为了覆盖状态没变化、
     * 因而不会有回调的那些情形（例如静音时本来就停在 IDLE）。
     */
    private fun toggleMute() {
        isMuted = !isMuted
        // 关闭态用带斜线的图标而不是调低透明度：变灰只有和开启态摆在一起才分得出来，
        // 而这两个按钮任何时刻都只呈现自己的一种状态，没有可比较的对象。
        btnMic.setImageResource(
            if (isMuted) R.drawable.uikit_ai_ic_call_mic_off else R.drawable.uikit_ai_ic_call_mic
        )
        controller?.setMuted(isMuted)
        micLabel?.setText(if (isMuted) R.string.uikit_ai_call_label_unmute else R.string.uikit_ai_call_label_mute)
        renderState(currentState)
    }

    /** 按 [uiOptions] 补上按钮文字、示例话术和通话计时。默认选项下什么都不做。 */
    private fun applyUiOptions() {
        if (uiOptions.showCallTimer) {
            modePill.setText(R.string.uikit_ai_call_connecting)
        }
        val buttonRow = btnMic.parent as? LinearLayout ?: return
        val column = buttonRow.parent as? LinearLayout ?: return
        if (uiOptions.showButtonLabels) {
            val labels = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                setPadding(buttonRow.paddingLeft, 0, buttonRow.paddingRight, 0)
            }
            fun label(@StringRes res: Int) = TextView(this).apply {
                setText(res)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setTextColor(LABEL_COLOR)
                gravity = Gravity.CENTER
                maxLines = 1
            }
            val width = dp(72)
            micLabel = label(R.string.uikit_ai_call_label_mute)
            labels.addView(micLabel, LinearLayout.LayoutParams(width, ViewGroup.LayoutParams.WRAP_CONTENT))
            labels.addView(label(R.string.uikit_ai_call_label_subtitle), LinearLayout.LayoutParams(width, ViewGroup.LayoutParams.WRAP_CONTENT))
            labels.addView(label(R.string.uikit_ai_call_label_hangup), LinearLayout.LayoutParams(width, ViewGroup.LayoutParams.WRAP_CONTENT))
            column.addView(
                labels,
                column.indexOfChild(buttonRow) + 1,
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = dp(8)
                },
            )
        }
        val hints = uiOptions.sampleHints
        if (hints.isNotEmpty()) {
            (statusText.layoutParams as? ViewGroup.MarginLayoutParams)?.let {
                it.bottomMargin = dp(6)
                statusText.layoutParams = it
            }
            hintText = TextView(this).apply {
                text = getString(R.string.uikit_ai_call_try_saying, hints.first())
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setTextColor(HINT_COLOR)
                gravity = Gravity.CENTER
                maxLines = 2
                setPadding(dp(32), 0, dp(32), 0)
            }
            column.addView(
                hintText,
                column.indexOfChild(statusText) + 1,
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = dp(26)
                },
            )
            hintText?.postDelayed(hintRotator, HINT_INTERVAL_MS)
        }
    }

    /** 用户开口之后、静音或启动失败时，不再提示「试试说」。 */
    private fun updateHintVisibility() {
        val hint = hintText ?: return
        val userSpoke = latestEntries.any { it.isFromUser && it.text.isNotBlank() }
        val show = !userSpoke && !isMuted && !listeningFailed
        // 用 INVISIBLE 而不是 GONE：占位不变，按钮不会跟着跳
        hint.visibility = if (show) View.VISIBLE else View.INVISIBLE
        if (userSpoke) {
            hint.removeCallbacks(hintRotator)
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    /**
     * 申请独占的通话音频焦点。
     *
     * USAGE_VOICE_COMMUNICATION 与 [io.trtc.tuikit.chat.uikit.components.aiassistant.audio.AudioPlaybackEngine]
     * 的播放属性保持一致，系统据此把这条流当成通话音频，才可能拿它做回声消除的参考信号。
     */
    private fun requestAudioFocus() {
        // 真机实测：不设通话模式时，外放期间麦克风被整条清零（探针读数 peak=0/1），
        // 机型侧按半双工处理，说话打断根本拿不到输入。MODE_IN_COMMUNICATION 是让系统
        // 按全双工通话对待这条链路的标准手段，也是硬件 AEC 真正生效的前提。
        previousAudioMode = audioManager.mode
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        audioSessionConfigured = true
        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setOnAudioFocusChangeListener(audioFocusListener, Handler(Looper.getMainLooper()))
                .build()
            audioFocusRequest = request
            audioManager.requestAudioFocus(request)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                audioFocusListener,
                AudioManager.STREAM_VOICE_CALL,
                AudioManager.AUDIOFOCUS_GAIN
            )
        }
        if (result != AudioManager.AUDIOFOCUS_REQUEST_GRANTED && BuildConfig.DEBUG) {
            // 拿不到焦点最常见的原因是系统通话正在进行。不中断本次通话：麦克风能不能用
            // 由 RealtimeAsrAudioCapture 说了算
            Log.w(TAG, "audio focus not granted: $result")
        }
    }

    private fun abandonAudioFocus() {
        if (!audioSessionConfigured) {
            return
        }
        audioSessionConfigured = false
        // 通话模式是全局的，不还回去会让退出后的普通媒体播放走听筒
        audioManager.mode = previousAudioMode
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
            audioFocusRequest = null
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(audioFocusListener)
        }
    }

    /**
     * 字幕按钮可以默认开着，但没内容时不挤光球。
     * 真正把光球缩到上方、展开列表，要等字幕开着且已经有至少一句非空文本。
     */
    private fun shouldShowTranscriptPanel(): Boolean {
        return subtitleVisible && latestEntries.any { it.text.isNotBlank() }
    }

    /**
     * 切换文本态。关掉时列表收起、光球回中间；打开时若还没有消息，只点亮按钮，
     * 光球仍居中，等第一句到来再让位。
     */
    private fun toggleSubtitle() {
        subtitleVisible = !subtitleVisible
        applySubtitleVisibility()
    }

    /** 右上角 LOG 开关：打开后覆盖状态快照和滚动日志。 */
    private fun toggleDebugOverlay() {
        debugVisible = !debugVisible
        debugOverlay.visibility = if (debugVisible) View.VISIBLE else View.GONE
        btnDebug.setTextColor(if (debugVisible) DEBUG_TOGGLE_ON else DEBUG_TOGGLE_OFF)
        debugOverlay.removeCallbacks(debugTicker)
        if (debugVisible) {
            debugTicker.run()
        }
    }

    /** 初始化、开关字幕、以及 transcript 从空变有内容，都走这里。 */
    private fun applySubtitleVisibility() {
        val showPanel = shouldShowTranscriptPanel()
        if (showPanel && transcriptView == null) {
            transcriptView = TranscriptView(this).also {
                it.plainText = uiOptions.plainTranscript
                transcriptContainer.addView(
                    it,
                    FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
                )
                it.submit(latestEntries)
            }
        }
        renderSubtitleButton()
        if (showPanel == showingTranscriptPanel) {
            return
        }
        showingTranscriptPanel = showPanel
        resizeOrb(compact = showPanel, animate = orb.isLaidOut)
    }

    /** 斜线图标的理由见 [toggleMute]。初始化与每次切换都走这里，避免两处各写一遍。 */
    private fun renderSubtitleButton() {
        btnSubtitle.setImageResource(
            if (subtitleVisible) {
                R.drawable.uikit_ai_ic_call_subtitle
            } else {
                R.drawable.uikit_ai_ic_call_subtitle_off
            }
        )
    }

    /**
     * 文本态且已有对话时，光球让位给列表：自身缩到 [ORB_SIZE_COMPACT_DP]，容器从
     * 「吃掉剩余高度」改成按内容包裹。没有消息或字幕关掉时，还原成布局里的居中大球。
     *
     * 已经完成过一次布局之后，用 [AutoTransition] 把光球上移缩小和字幕区展开做成一次
     * 过渡；进页第一次套布局时 [View.isLaidOut] 还是 false，那次直接落位，不要闪一下。
     */
    private fun resizeOrb(compact: Boolean, animate: Boolean) {
        if (animate) {
            TransitionManager.endTransitions(root)
            TransitionManager.beginDelayedTransition(
                root,
                AutoTransition().apply {
                    duration = ORB_LAYOUT_ANIM_MS
                    interpolator = PathInterpolator(0.4f, 0f, 0.2f, 1f)
                }
            )
        }
        transcriptContainer.visibility = if (compact) View.VISIBLE else View.GONE
        val sizeDp = if (compact) ORB_SIZE_COMPACT_DP else ORB_SIZE_FULL_DP
        val sizePx = (sizeDp * resources.displayMetrics.density).toInt()
        orb.layoutParams = orb.layoutParams.apply {
            width = sizePx
            height = sizePx
        }
        orbContainer.layoutParams = (orbContainer.layoutParams as LinearLayout.LayoutParams).apply {
            weight = if (compact) 0f else 1f
            height = if (compact) ViewGroup.LayoutParams.WRAP_CONTENT else 0
        }
    }

    private fun renderState(state: AICallState) {
        currentState = state
        // 静音时状态机停在 IDLE，光球若跟着切过去会变成一颗静止暗球。
        // 静音只关麦，视觉上继续用聆听态的呼吸闪烁；关掉的是麦克风按钮图标和文案。
        orb.setState(if (isMuted) AICallState.LISTENING else state)
        // 静音、启动失败时状态机都停在 IDLE，而 IDLE 的文案是「正在听…」，照搬就成了
        // 「都静音了还说在听」，以及弱网下一次 Toast 过后页面就再也看不出异常
        val textRes = if (isMuted) {
            R.string.uikit_ai_call_muted
        } else if (listeningFailed) {
            R.string.uikit_ai_call_start_failed_retry
        } else {
            when (state) {
                AICallState.LISTENING -> R.string.uikit_ai_call_listening
                AICallState.THINKING -> R.string.uikit_ai_call_thinking
                AICallState.SPEAKING -> R.string.uikit_ai_call_speaking
                AICallState.IDLE -> R.string.uikit_ai_call_listening
            }
        }
        statusText.setText(textRes)
        updateHintVisibility()
    }

    /**
     * 重新可见：把麦克风要回来。
     *
     * 首次进入不会和 [onMicrophoneReady] 的启动路径打架，不需要额外的「第一次」标志：
     * 权限已授予时 controller 在 `onCreate` 里就建好了，但它从没暂停过，
     * [AICallController.resume] 对「没暂停」是 no-op；权限要现场申请时 controller 还是
     * null，这里连调都调不到。
     */
    override fun onStart() {
        super.onStart()
        visible = true
        controller?.resume()
    }

    /**
     * 退到后台：暂停整次通话。
     *
     * 按 Home 键不触发 AUDIOFOCUS_LOSS，所以不能只靠音频焦点回调。targetSdk 35 下 Activity
     * 不可见之后系统就收走麦克风，`AudioRecord.read()` 开始返回全零，而全零帧会被 VAD 判成
     * 静音——用户在说了半句的情况下退到后台，那半句会在 800ms 后被自动发出去，AI 的回答
     * 接着在后台大声外放。设计文档的非目标里写明了不做后台播放。
     */
    override fun onStop() {
        super.onStop()
        visible = false
        controller?.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        // 先还焦点再拆 controller：焦点还在手上时来的回调会去碰一个正在释放的 controller，
        // 虽然 release() 之后每个入口都有 released 守卫，少一次无谓的往返总是好的
        TransitionManager.endTransitions(root)
        debugOverlay.removeCallbacks(debugTicker)
        modePill.removeCallbacks(callTimerTicker)
        hintText?.removeCallbacks(hintRotator)
        asrUnavailableDialog?.dismiss()
        asrUnavailableDialog = null
        abandonAudioFocus()
        controller?.release()
        controller = null
    }

    companion object {
        private const val TAG = "AICallActivity"

        /** 与 uikit_ai_activity_call.xml 里 uikit_ai_call_orb 的初始尺寸保持一致，收起字幕时要能还原 */
        private const val ORB_SIZE_FULL_DP = 280
        private const val ORB_SIZE_COMPACT_DP = 72
        private const val ORB_LAYOUT_ANIM_MS = 320L
        private const val DEBUG_TICK_MS = 200L
        private const val DEBUG_TOGGLE_ON = 0xFF7EE0D8.toInt()
        private const val DEBUG_TOGGLE_OFF = 0xFF8A9099.toInt()

        private const val EXTRA_ROBOT_USER_ID = "robot_user_id"
        private const val EXTRA_UI_OPTIONS = "ui_options"
        private const val HINT_INTERVAL_MS = 3200L
        private const val LABEL_COLOR = 0xFFB8BDC4.toInt()
        private const val HINT_COLOR = 0xFF9FA6FF.toInt()

        /** [uiOptions] 控制通话页的可选外观与引导，不传时保持原样。 */
        @JvmOverloads
        fun start(context: Context, robotUserId: String, uiOptions: AICallUiOptions? = null) {
            if (robotUserId.isBlank()) {
                return
            }
            val intent = Intent(context, AICallActivity::class.java)
                .putExtra(EXTRA_ROBOT_USER_ID, robotUserId)
            if (uiOptions != null) {
                intent.putExtra(EXTRA_UI_OPTIONS, uiOptions.forIntent())
            }
            context.startActivity(intent)
        }
    }
}
