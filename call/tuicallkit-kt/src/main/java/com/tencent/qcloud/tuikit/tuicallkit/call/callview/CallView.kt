package com.tencent.qcloud.tuikit.tuicallkit.call.callview

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Outline
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import com.tencent.qcloud.tuikit.tuicallkit.R
import com.tencent.qcloud.tuikit.tuicallkit.call.CallKitAssets
import com.tencent.qcloud.tuikit.tuicallkit.call.CallKitConfig
import com.tencent.qcloud.tuikit.tuicallkit.call.CallLauncher
import com.tencent.qcloud.tuikit.tuicallkit.call.aitranscriber.LanguagePickerSheet
import com.tencent.qcloud.tuikit.tuicallkit.call.aitranscriber.TapHintPill
import com.tencent.qcloud.tuikit.tuicallkit.call.aitranscriber.TranscriptView
import com.tencent.qcloud.tuikit.tuicallkit.call.aitranscriber.TranscriptionSettingsSheet
import com.tencent.qcloud.tuikit.tuicallkit.call.callview.bottom.AudioRouteBubble
import com.tencent.qcloud.tuikit.tuicallkit.call.callview.bottom.BackgroundPanel
import com.tencent.qcloud.tuikit.tuicallkit.call.callview.bottom.BeautyPanel
import com.tencent.qcloud.tuikit.tuicallkit.call.callview.bottom.CallControlPanel
import com.tencent.qcloud.tuikit.tuicallkit.call.callview.bottom.MoreMenuBubble
import com.tencent.qcloud.tuikit.tuicallkit.call.callview.overlays.VideoCallingOverlay
import com.tencent.qcloud.tuikit.tuicallkit.call.callview.overlays.WaitingParticipantsView
import com.tencent.qcloud.tuikit.tuicallkit.call.callview.top.InviteMemberSheet
import com.tencent.qcloud.tuikit.tuicallkit.call.callview.top.TopBarView
import com.tencent.qcloud.tuikit.tuicallkit.common.CallKitBottomSheet
import com.tencent.qcloud.tuikit.tuicallkit.common.CallKitContactLoader
import com.tencent.qcloud.tuikit.tuicallkit.common.CallKitImageBlur
import com.tencent.qcloud.tuikit.tuicallkit.common.CallKitToast
import com.tencent.qcloud.tuikit.tuicallkit.common.CallKitLogger
import com.tencent.qcloud.tuikit.tuicallkit.common.dp
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.call.CallMediaType
import io.trtc.tuikit.atomicxcore.api.call.CallParticipantStatus
import io.trtc.tuikit.atomicxcore.api.call.CallStore
import io.trtc.tuikit.atomicxcore.api.device.DeviceStatus
import io.trtc.tuikit.atomicxcore.api.device.DeviceStore
import io.trtc.tuikit.atomicxcore.api.login.LoginStore
import io.trtc.tuikit.atomicxcore.api.view.CallCoreView
import io.trtc.tuikit.atomicxcore.api.view.CallLayoutTemplate
import io.trtc.tuikit.atomicxcore.impl.view.call.grid.CallGridLayout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal class CallView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    private val backdropImage: ImageView
    private val callCoreView: CallCoreView
    private val avatarImage: ImageView
    private val avatarRing: View
    private val nameLabel: TextView
    private val statusLabel: TextView
    private val topBar: TopBarView
    private val controlPanel: CallControlPanel

    private val routeBubble = AudioRouteBubble(context)
    private val moreBubble = MoreMenuBubble(context)
    private val bubbleDismissOverlay: View
    private val transcriptView = TranscriptView(context)
    private val tapHintPill = TapHintPill(context)

    private var waitingParticipantsView: WaitingParticipantsView? = null
    private var previewShadeView: View? = null
    private var videoCallingOverlay: VideoCallingOverlay? = null

    private var subscribeJob: Job? = null
    private var blurJob: Job? = null
    private var hintJob: Job? = null
    private var ringPulseAnimator: ObjectAnimator? = null
    private var hasConnected = false
    private var wasIncomingRinging = false
    private var didEnterMultiMode = false
    private var backdropToken = 0
    private var lastParticipantStatuses: Map<String, CallParticipantStatus> = emptyMap()
    private var isImmersive = false
    private var openSheetCount = 0
    private var lastTranscriptionEnabled = false
    private var barBackgroundLatched = false
    private var statusBarInset = 0
    private var navBarInset = 0
    private var observedGridLayout: CallGridLayout? = null
    private val logger = CallKitLogger.getLogger("CallView")

    private var tapDownX = 0f
    private var tapDownY = 0f
    private var tapMoved = false
    private var routeTouchToGrid = false
    private val touchSlop = android.view.ViewConfiguration.get(context).scaledTouchSlop

    private val isVideo: Boolean
        get() = CallStore.shared.observerState.activeCall.value.mediaType == CallMediaType.Video

    private val isIncomingRinging: Boolean
        get() {
            val state = CallStore.shared.observerState
            val selfID = LoginStore.shared.loginState.loginUserInfo.value?.userID.orEmpty()
            return state.selfInfo.value.status == CallParticipantStatus.Waiting
                && state.activeCall.value.inviterId.isNotEmpty()
                && state.activeCall.value.inviterId != selfID
        }

    private val isMulti: Boolean
        get() {
            val state = CallStore.shared.observerState
            val call = state.activeCall.value
            return call.chatGroupId.isNotEmpty()
                || call.inviteeIds.size > 1
                || state.allParticipants.value.size > 2
        }

    init {
        clipChildren = false
        clipToPadding = false
        CallCoreView.defaultAvatarImage = defaultAvatarDrawable()
        LayoutInflater.from(context).inflate(R.layout.tuicallkit_call_view, this, true)
        backdropImage = findViewById(R.id.backdrop_image)
        callCoreView = findViewById(R.id.call_core)
        avatarImage = findViewById(R.id.avatar_image)
        nameLabel = findViewById(R.id.name_label)
        statusLabel = findViewById(R.id.status_label)
        topBar = findViewById(R.id.top_bar)
        controlPanel = findViewById(R.id.control_panel)
        installInsetsDispatch()
        injectEngineAssets()
        avatarImage.setImageDrawable(defaultAvatarDrawable())
        avatarImage.translationY = -AVATAR_CENTER_OFFSET_DP.dp(context).toFloat()
        avatarImage.outlineProvider = RoundedOutlineProvider(AVATAR_CORNER_RADIUS_DP)

        avatarRing = View(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0x0AFFFFFF)
            }
            alpha = 0f
            translationY = -AVATAR_CENTER_OFFSET_DP.dp(context).toFloat()
        }
        addView(avatarRing, indexOfChild(avatarImage), LayoutParams(AVATAR_RING_SIZE_DP.dp(context), AVATAR_RING_SIZE_DP.dp(context)).apply {
            gravity = Gravity.CENTER
        })

        transcriptView.isVisible = false
        addView(transcriptView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.BOTTOM
            marginStart = 24.dp(context)
            marginEnd = 24.dp(context)
            bottomMargin = 220.dp(context)
        })
        addView(tapHintPill, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.CENTER_HORIZONTAL))

        // 不再使用独立的 immersiveOverlay View：跨 XML inflate 层级无法稳定排到 topBar/controlPanel 下方。
        // 退出沉浸的逻辑统一在 CallView.dispatchTouchEvent → handleImmersiveTap 内判断。

        bubbleDismissOverlay = View(context).apply {
            isVisible = false
            setOnClickListener { dismissBubbles() }
        }
        addView(bubbleDismissOverlay, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        routeBubble.isVisible = false
        moreBubble.isVisible = false
        addView(routeBubble, LayoutParams(264.dp(context), LayoutParams.WRAP_CONTENT))
        addView(moreBubble, LayoutParams(212.dp(context), LayoutParams.WRAP_CONTENT))

        controlPanel.onSpeakerBubbleToggle = { toggleRouteBubble() }
        controlPanel.onMoreBubbleToggle = { toggleMoreBubble() }
        controlPanel.onExpand = { setImmersive(false) }
        transcriptView.onPickSettings = { presentTranscriptionSettings() }
        topBar.onInviteTap = { presentInviteSheet() }
        routeBubble.onSelect = { device ->
            DeviceStore.shared().setCurrentSpeaker(device)
            dismissBubbles()
        }
        moreBubble.onSelect = { item ->
            dismissBubbles()
            when (item) {
                MoreMenuBubble.Item.BlurBackground -> presentPanel(BackgroundPanel(context))
                MoreMenuBubble.Item.Beauty -> presentPanel(BeautyPanel(context))
            }
        }

        lastTranscriptionEnabled = CallKitConfig.shared.enableAITranscriber
        loadPeerProfile()
        blurBackdrop(defaultAvatarBitmap())
        if (isIncomingRinging) {
            enterRingingMode()
        } else if (isMulti) {
            enterMultiMode()
        } else if (isVideo) {
            enterVideoCallingMode()
        }
    }

    fun setPermissionHandlers(onMicrophone: ((() -> Unit) -> Unit)?, onCamera: ((() -> Unit) -> Unit)?) {
        controlPanel.onMicPermissionRequest = onMicrophone
        controlPanel.onCameraPermissionRequest = onCamera
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        // 主动请求一次 insets，避免底部控制栏因 inset 未下发而贴导航栏
        ViewCompat.requestApplyInsets(this)
        if (didEnterMultiMode) {
            bindGridLayout()
        }
        subscribeCallState()
    }

    override fun onDetachedFromWindow() {
        subscribeJob?.cancel()
        subscribeJob = null
        blurJob?.cancel()
        blurJob = null
        hintJob?.cancel()
        hintJob = null
        observedGridLayout?.onGeometryChanged = null
        observedGridLayout = null
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (wasIncomingRinging) {
            syncAvatarToRingingSlot()
        }
        (tapHintPill.layoutParams as? LayoutParams)?.let {
            it.topMargin = (h * 462f / 852f).toInt()
            tapHintPill.layoutParams = it
        }
        applyTranscriptConstraints()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                tapDownX = ev.rawX
                tapDownY = ev.rawY
                tapMoved = false
            }
            MotionEvent.ACTION_MOVE -> {
                if (!tapMoved && (kotlin.math.abs(ev.rawX - tapDownX) > touchSlop
                        || kotlin.math.abs(ev.rawY - tapDownY) > touchSlop)
                ) {
                    tapMoved = true
                }
            }
            MotionEvent.ACTION_UP -> {
                if (!tapMoved) {
                    handleImmersiveTap(ev)
                }
            }
            MotionEvent.ACTION_CANCEL -> tapMoved = true
        }
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) {
            routeTouchToGrid = shouldRouteTouchToGrid(ev)
        }
        if (routeTouchToGrid) {
            val handled = dispatchToGrid(ev)
            if (ev.actionMasked == MotionEvent.ACTION_UP || ev.actionMasked == MotionEvent.ACTION_CANCEL) {
                routeTouchToGrid = false
            }
            return handled
        }
        return super.dispatchTouchEvent(ev)
    }

    /**
     * 子视图不消费空白点击时，默认 onTouchEvent 返回 false，父布局会提前结束手势序列，
     * 导致 ACTION_UP 不再回到 dispatchTouchEvent，简洁模式永远不会触发。
     * 这里统一声明本视图接管未被子视图处理的手势，确保能收到 UP 以切换沉浸态。
     */
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        return true
    }

    /** 字幕/提示层位于宫格之上；与格子重叠时，格子点击优先（对齐 iOS hitTest 规则）。 */
    private fun shouldRouteTouchToGrid(ev: MotionEvent): Boolean {
        if (!didEnterMultiMode || openSheetCount > 0) {
            return false
        }
        if (routeBubble.isVisible || moreBubble.isVisible || bubbleDismissOverlay.isVisible) {
            return false
        }
        val coveredByCaption = isTouchOn(transcriptView, ev) || isTouchOn(tapHintPill, ev)
        if (!coveredByCaption) return false
        // 字幕头行的齿轮等真实按钮点击优先响应，不再路由给宫格（避免字幕区覆盖格子时吞掉设置入口）。
        val x = ev.rawX.toInt()
        val y = ev.rawY.toInt()
        if (hitsInteractiveChild(transcriptView, x, y)) return false
        return hitsParticipantView(ev)
    }

    private fun isTouchOn(view: View, ev: MotionEvent): Boolean {
        if (!view.isVisible || view.alpha == 0f) {
            return false
        }
        val rect = Rect()
        return view.getGlobalVisibleRect(rect) && rect.contains(ev.rawX.toInt(), ev.rawY.toInt())
    }

    private fun dispatchToGrid(ev: MotionEvent): Boolean {
        val target = findGridLayout(callCoreView) ?: return super.dispatchTouchEvent(ev)
        val location = IntArray(2)
        target.getLocationOnScreen(location)
        val copy = MotionEvent.obtain(ev)
        copy.setLocation(ev.rawX - location[0], ev.rawY - location[1])
        val handled = target.dispatchTouchEvent(copy)
        copy.recycle()
        return handled
    }

    private fun handleImmersiveTap(e: MotionEvent) {
        if ((!isVideo && !didEnterMultiMode) || !hasConnected) {
            return
        }
        if (openSheetCount > 0) {
            return
        }
        if (routeBubble.isVisible || moreBubble.isVisible) {
            return
        }
        val x = e.rawX.toInt()
        val y = e.rawY.toInt()
        if (topBar.isVisible && hitsInteractiveChild(topBar, x, y)) {
            return
        }
        if (controlPanel.isVisible && hitsInteractiveChild(controlPanel, x, y)) {
            return
        }
        val hit = hitsParticipantView(e)
        val grid = findGridLayout(callCoreView)
        if (hit) {
            return
        }
        setImmersive(!isImmersive)
    }

    private fun hitsInteractiveChild(view: View, x: Int, y: Int): Boolean {
        if (!view.isVisible) {
            return false
        }
        val rect = Rect()
        if (!view.getGlobalVisibleRect(rect) || !rect.contains(x, y)) {
            return false
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                if (hitsInteractiveChild(view.getChildAt(i), x, y)) {
                    return true
                }
            }
            return view.hasOnClickListeners() || view.isClickable
        }
        return view.isClickable || view.hasOnClickListeners()
    }

    private fun hitsParticipantView(e: MotionEvent): Boolean {
        if (!callCoreView.isVisible) {
            return false
        }
        val coreRect = Rect()
        if (!callCoreView.getGlobalVisibleRect(coreRect) || !coreRect.contains(e.rawX.toInt(), e.rawY.toInt())) {
            return false
        }
        val grid = findGridLayout(callCoreView)
        if (grid != null) {
            val location = IntArray(2)
            grid.getLocationOnScreen(location)
            return grid.containsParticipantAt(e.rawX - location[0], e.rawY - location[1])
        }
        val limit = 200.dp(context)
        return findParticipantHit(callCoreView, e.rawX.toInt(), e.rawY.toInt(), limit)
    }

    private fun findParticipantHit(view: View, x: Int, y: Int, smallLimit: Int): Boolean {
        if (view !is android.view.ViewGroup) {
            return false
        }
        val rect = Rect()
        for (i in 0 until view.childCount) {
            val child = view.getChildAt(i)
            if (!child.isVisible || !child.getGlobalVisibleRect(rect) || !rect.contains(x, y)) {
                continue
            }
            if (child.javaClass.simpleName == "CallParticipantView" && (didEnterMultiMode || child.width < smallLimit)) {
                return true
            }
            if (findParticipantHit(child, x, y, smallLimit)) {
                return true
            }
        }
        return false
    }

    private fun installInsetsDispatch() {
        ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            statusBarInset = bars.top
            navBarInset = bars.bottom
            (topBar.layoutParams as? androidx.constraintlayout.widget.ConstraintLayout.LayoutParams)?.let {
                it.topMargin = bars.top + 8.dp(context)
                topBar.layoutParams = it
            }
            applyControlPanelBarMode(barBackgroundLatched)
            insets
        }
    }

    private fun injectEngineAssets() {
        val waitingPath = CallKitAssets.waitingAnimationPath()
        if (waitingPath.isNotEmpty()) {
            callCoreView.setWaitingAnimation(waitingPath)
        }
        val volumeIcons = CallKitAssets.volumeIconPaths()
        if (volumeIcons.isNotEmpty()) {
            callCoreView.setVolumeLevelIcons(volumeIcons)
        }
    }

    private fun setImmersive(immersive: Boolean) {
        isImmersive = immersive
        dismissBubbles()
        topBar.setImmersive(immersive)
        transcriptView.isImmersiveExpanded = immersive
        applyTranscriptConstraints()
        if (didEnterMultiMode) {
            if (immersive) {
                barBackgroundLatched = true
            }
            controlPanel.isVisible = true
            controlPanel.setCollapsed(immersive)
            controlPanel.setBarBackground(barBackgroundLatched)
            applyControlPanelBarMode(barBackgroundLatched)
        } else {
            controlPanel.isVisible = !immersive
        }
    }

    private fun applyControlPanelBarMode(barMode: Boolean) {
        val layout = findViewById<View>(R.id.control_panel)
        val params = layout.layoutParams as? androidx.constraintlayout.widget.ConstraintLayout.LayoutParams ?: return
        if (barMode) {
            params.marginStart = 0
            params.marginEnd = 0
            params.bottomMargin = 0
            controlPanel.setBarContentBottomInset(navBarInset)
        } else {
            params.marginStart = 16.dp(context)
            params.marginEnd = 16.dp(context)
            params.bottomMargin = navBarInset
        }
        layout.layoutParams = params
    }

    private fun subscribeCallState() {
        subscribeJob?.cancel()
        subscribeJob = CoroutineScope(Dispatchers.Main + SupervisorJob()).launch {
            launch {
                CallStore.shared.observerState.selfInfo.collect { self ->
                    if (self.status == CallParticipantStatus.Accept && !hasConnected) {
                        handleConnected()
                    }
                }
            }
            launch {
                CallStore.shared.observerState.allParticipants.collect { participants ->
                    if (isIncomingRinging) {
                        waitingParticipantsView?.reload()
                    }
                    if (!didEnterMultiMode && !isIncomingRinging && isMulti) {
                        enterMultiMode()
                    }
                    refreshTranscriptLayoutMode()
                    diffParticipantChanges(participants.map { it.id to it.status })
                }
            }
            launch {
                CallStore.shared.observerState.activeCall.collect {
                    if (isIncomingRinging) {
                        waitingParticipantsView?.reload()
                    }
                }
            }
            launch {
                DeviceStore.shared().deviceState.cameraStatus.collect { status ->
                    if (isVideo) {
                        videoCallingOverlay?.setCameraOff(status != DeviceStatus.ON)
                    }
                }
            }
            launch {
                DeviceStore.shared().deviceState.currentSpeaker.collect {
                    refreshRouteBubbleIfVisible()
                }
            }
            launch {
                CallKitConfig.shared.transcriptionChanged.collect {
                    refreshTranscriptVisibility()
                    val enabled = CallKitConfig.shared.enableAITranscriber
                    if (enabled && !lastTranscriptionEnabled && hasConnected && isVideo) {
                        tapHintPill.showTemporarily()
                    }
                    lastTranscriptionEnabled = enabled
                }
            }
        }
    }

    private fun handleConnected() {
        hasConnected = true
        refreshTranscriptVisibility()
        topBar.isVisible = true
        controlPanel.isVisible = true
        if (isMulti) {
            enterMultiMode()
        }
        topBar.handleConnected()
        transitionToConnected()
        if (isVideo) {
            hintJob?.cancel()
            hintJob = CoroutineScope(Dispatchers.Main + SupervisorJob()).launch {
                delay(2000)
                tapHintPill.showTemporarily()
            }
        }
    }

    private fun transitionToConnected() {
        val overlays = listOfNotNull(waitingParticipantsView, videoCallingOverlay, previewShadeView).filter { it.isVisible }
        val isAudioOneToOne = !isVideo && !isMulti

        ringPulseAnimator?.cancel()
        ringPulseAnimator = null
        avatarImage.scaleX = 1f
        avatarImage.scaleY = 1f

        val fadingLayers = if (isAudioOneToOne) {
            emptyList()
        } else {
            listOf(avatarImage, nameLabel).filter { it.isVisible }
        }

        callCoreView.isVisible = !isAudioOneToOne
        if (!isAudioOneToOne || wasIncomingRinging) {
            statusLabel.isVisible = false
        }

        if (wasIncomingRinging) {
            topBar.alpha = 0f
            controlPanel.alpha = 0f
        }

        overlays.forEach { overlay ->
            overlay.animate().alpha(0f).setDuration(250).withEndAction {
                overlay.isVisible = false
                overlay.alpha = 1f
            }.start()
        }
        fadingLayers.forEach { layer ->
            layer.animate().alpha(0f).setDuration(250).withEndAction {
                layer.isVisible = false
                layer.alpha = 1f
            }.start()
        }
        topBar.animate().alpha(1f).setDuration(250).start()
        controlPanel.animate().alpha(1f).setDuration(250).start()

        if (isAudioOneToOne) {
            statusLabel.animate().alpha(0f).setDuration(250).withEndAction {
                statusLabel.isVisible = false
                statusLabel.alpha = 1f
            }.start()
            avatarRing.animate().alpha(1f).setDuration(250).start()

            val targetY = -AVATAR_CENTER_OFFSET_DP.dp(context).toFloat()
            avatarImage.animate().translationY(targetY).setDuration(250).start()
            avatarRing.animate().translationY(targetY).setDuration(250).start()
            nameLabel.animate().translationY(targetY).setDuration(250).start()

            ValueAnimator.ofFloat(AVATAR_CORNER_RADIUS_DP, AVATAR_CONNECTED_RADIUS_DP).apply {
                duration = 250
                addUpdateListener {
                    avatarImage.outlineProvider = RoundedOutlineProvider(it.animatedValue as Float)
                    avatarImage.invalidateOutline()
                }
                start()
            }

            val targetSize = AVATAR_CONNECTED_SIZE_DP.dp(context)
            if (avatarImage.layoutParams.width != targetSize) {
                ValueAnimator.ofInt(avatarImage.layoutParams.width, targetSize).apply {
                    duration = 250
                    addUpdateListener {
                        val size = it.animatedValue as Int
                        avatarImage.layoutParams = avatarImage.layoutParams.apply {
                            width = size
                            height = size
                        }
                    }
                    start()
                }
            }
        }
    }

    private fun refreshTranscriptVisibility() {
        val enabled = CallKitConfig.shared.enableAITranscriber
        transcriptView.isVisible = enabled && hasConnected
    }

    private fun refreshTranscriptLayoutMode() {
        if (!didEnterMultiMode) {
            transcriptView.layoutMode = TranscriptView.LayoutMode.Single
            return
        }
        val gridRows = observedGridLayout?.rows?.takeIf { it > 0 }
        val rows = gridRows ?: run {
            val count = CallStore.shared.observerState.allParticipants.value.size
            val columns = when (count) {
                1 -> 1
                2, 4 -> 2
                else -> 3
            }
            kotlin.math.ceil(count.toDouble() / columns).toInt()
        }
        transcriptView.layoutMode = TranscriptView.LayoutMode.Multi(rows)
    }

    private fun bindGridLayout() {
        val grid = findGridLayout(callCoreView)
        val changed = observedGridLayout !== grid
        observedGridLayout?.onGeometryChanged = null
        observedGridLayout = grid
        grid?.onGeometryChanged = {
            refreshTranscriptLayoutMode()
            applyTranscriptConstraints()
        }
        if (changed) {
            refreshTranscriptLayoutMode()
            applyTranscriptConstraints()
        }
    }

    private fun findGridLayout(view: View): CallGridLayout? {
        if (view is CallGridLayout) {
            return view
        }
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                findGridLayout(view.getChildAt(index))?.let { return it }
            }
        }
        return null
    }

    private fun applyTranscriptConstraints() {
        // 上沿一次确定，不随 immersive 变化：
        //   · multi = HTML --cap-top（引擎宫格下沿 + 12；兜底 468pt）
        //   · single = HTML 清屏块上沿 469（= 852 − 383；常态 flex-end 浮动顶部统一锁到此处，
        //              让展开/折叠切换时字幕上沿不动，向下延伸）
        // 高度 availableMaxHeight 按 HTML 规则限制：
        //   · multi immersive = 底距屏底 110（cap-bottom 34 + 76 让位折叠条）
        //   · single immersive = 底距屏底 34（device-safe-bottom）
        //   · single 非 immersive = 底距屏底 220（.g-body bottom:236 − cap-drop:16）
        //   · multi 非 immersive = 不限（允许重叠按钮区，高度由 LayoutMode 70/139 决定）
        val params = transcriptView.layoutParams as? LayoutParams ?: return
        params.gravity = Gravity.TOP
        params.bottomMargin = 0

        val captionTop: Int = if (didEnterMultiMode) {
            val treeGrid = findGridLayout(callCoreView)
            if (treeGrid !== observedGridLayout) {
                bindGridLayout()
                return
            }
            treeGrid?.captionTopPx?.takeIf { it > 0 } ?: (height * 468f / 852f).toInt()
        } else {
            (height * 469f / 852f).toInt()
        }
        params.topMargin = captionTop

        val bottomInset: Int? = when {
            didEnterMultiMode && isImmersive -> navBarInset + 76.dp(context)
            didEnterMultiMode -> null
            isImmersive -> 34.dp(context)
            else -> 220.dp(context)
        }
        transcriptView.availableMaxHeight =
            bottomInset?.let { (height - it - captionTop).coerceAtLeast(0) }
        transcriptView.layoutParams = params
    }

    private fun toggleRouteBubble() {
        if (routeBubble.isVisible) {
            dismissBubbles()
        } else {
            dismissBubbles()
            showRouteBubble()
        }
    }

    private fun showRouteBubble() {
        val anchor = controlPanel.speakerAnchor ?: return
        val anchorRect = Rect()
        if (!anchor.getGlobalVisibleRect(anchorRect)) {
            return
        }
        val selfRect = Rect()
        getGlobalVisibleRect(selfRect)
        val state = DeviceStore.shared().deviceState
        routeBubble.reload(state.speakerList.value, state.currentSpeaker.value)
        bubbleDismissOverlay.isVisible = true
        routeBubble.isVisible = true
        val bubbleWidth = 264.dp(context)
        val bubbleX = (anchorRect.centerX() - selfRect.left - bubbleWidth / 2)
            .coerceIn(12.dp(context), width - bubbleWidth - 12.dp(context))
        routeBubble.post {
            val params = routeBubble.layoutParams as LayoutParams
            params.marginStart = bubbleX
            params.gravity = Gravity.BOTTOM
            params.bottomMargin = (selfRect.bottom - anchorRect.top) + 12.dp(context)
            routeBubble.layoutParams = params
            routeBubble.setArrowCenterX((anchorRect.centerX() - selfRect.left - bubbleX).toFloat())
        }
    }

    private fun toggleMoreBubble() {
        if (moreBubble.isVisible) {
            dismissBubbles()
        } else {
            dismissBubbles()
            showMoreBubble()
        }
    }

    private fun showMoreBubble() {
        val anchor = controlPanel.moreAnchor ?: return
        val anchorRect = Rect()
        if (!anchor.getGlobalVisibleRect(anchorRect)) {
            return
        }
        val selfRect = Rect()
        getGlobalVisibleRect(selfRect)
        bubbleDismissOverlay.isVisible = true
        moreBubble.isVisible = true
        val bubbleWidth = 212.dp(context)
        val bubbleX = (anchorRect.centerX() - selfRect.left - bubbleWidth / 2)
            .coerceIn(12.dp(context), width - bubbleWidth - 12.dp(context))
        moreBubble.post {
            val params = moreBubble.layoutParams as LayoutParams
            params.marginStart = bubbleX
            params.gravity = Gravity.BOTTOM
            params.bottomMargin = (selfRect.bottom - anchorRect.top) + 12.dp(context)
            moreBubble.layoutParams = params
            moreBubble.setArrowCenterX((anchorRect.centerX() - selfRect.left - bubbleX).toFloat())
        }
    }

    private fun refreshRouteBubbleIfVisible() {
        if (!routeBubble.isVisible) {
            return
        }
        val state = DeviceStore.shared().deviceState
        routeBubble.reload(state.speakerList.value, state.currentSpeaker.value)
    }

    private fun dismissBubbles() {
        routeBubble.isVisible = false
        moreBubble.isVisible = false
        bubbleDismissOverlay.isVisible = false
    }

    private fun presentTranscriptionSettings() {
        val content = TranscriptionSettingsSheet(context)
        content.onPickLanguage = { presentLanguagePicker() }
        val sheet = presentSheet(content, CallKitBottomSheet.Style.GENERIC)
        content.onClose = { sheet.dismiss() }
    }

    private fun presentLanguagePicker() {
        val picker = LanguagePickerSheet(context)
        val sheet = presentSheet(picker, CallKitBottomSheet.Style.GENERIC)
        picker.onFinish = { sheet.dismiss() }
    }

    private fun presentSheet(content: View, style: CallKitBottomSheet.Style = CallKitBottomSheet.Style.CALL_PANEL): CallKitBottomSheet {
        val sheet = CallKitBottomSheet(context)
        sheet.onDismiss = { openSheetCount -= 1 }
        openSheetCount += 1
        sheet.show(this, content, style)
        return sheet
    }

    private fun presentPanel(content: View) {
        presentSheet(content)
    }

    private fun presentInviteSheet() {
        val content = InviteMemberSheet(context)
        var sheetRef: CallKitBottomSheet? = null
        content.onClose = { sheetRef?.dismiss() }
        content.onInvite = { picks ->
            sheetRef?.dismiss()
            if (picks.isNotEmpty()) {
                invite(picks)
            }
        }
        val sheet = presentSheet(content, CallKitBottomSheet.Style.GENERIC)
        sheetRef = sheet
    }

    private fun invite(picks: List<InviteMemberSheet.Pick>) {
        CallStore.shared.invite(picks.map { it.userID }, null, object : CompletionHandler {
            override fun onSuccess() {
                post {
                    val names = picks.joinToString("、") { it.displayName }
                    CallKitToast.show(context.getString(R.string.tuicallkit_invite_sent, names))
                }
            }

            override fun onFailure(code: Int, desc: String) {
                post {
                    CallKitToast.show(context.getString(R.string.tuicallkit_invite_failed))
                }
            }
        })
    }

    private fun enterRingingMode() {
        wasIncomingRinging = true
        topBar.isVisible = false
        controlPanel.isVisible = false
        statusLabel.isVisible = false
        avatarImage.isVisible = true
        nameLabel.isVisible = true
        val ringSize = AVATAR_CONNECTED_SIZE_DP.dp(context)
        avatarImage.layoutParams = avatarImage.layoutParams.apply {
            width = ringSize
            height = ringSize
        }
        avatarImage.outlineProvider = RoundedOutlineProvider(AVATAR_CORNER_RADIUS_DP)
        startRingingPulse()

        if (isVideo) {
            callCoreView.setLayoutTemplate(CallLayoutTemplate.Float)
            callCoreView.isVisible = true
            val shade = PreviewShadeView(context)
            addView(shade, 1, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
            previewShadeView = shade
        }

        val waiting = WaitingParticipantsView(context)
        waiting.reload()
        waiting.onAccept = { CallLauncher.acceptIncoming() }
        waiting.onReject = { CallLauncher.rejectIncoming() }
        waiting.onContentLayout = { syncAvatarToRingingSlot() }
        addView(waiting, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        waitingParticipantsView = waiting
        waiting.post { syncAvatarToRingingSlot() }
    }

    private fun syncAvatarToRingingSlot() {
        val waiting = waitingParticipantsView ?: return
        if (height <= 0) return
        val slotCenterY = waiting.avatarSlotCenterYIn(this)
        avatarImage.translationY = slotCenterY - height / 2f
        avatarRing.translationY = slotCenterY - height / 2f
        nameLabel.translationY = avatarImage.translationY
    }

    private fun startRingingPulse() {
        ringPulseAnimator?.cancel()
        val animator = ObjectAnimator.ofPropertyValuesHolder(
            avatarImage,
            PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.05f),
            PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.05f)
        )
        animator.duration = 900
        animator.repeatCount = ValueAnimator.INFINITE
        animator.repeatMode = ValueAnimator.REVERSE
        animator.start()
        ringPulseAnimator = animator
    }

    private fun enterVideoCallingMode() {
        avatarImage.isVisible = false
        nameLabel.isVisible = false
        statusLabel.isVisible = false
        backdropImage.isVisible = false
        callCoreView.isVisible = true
        val overlay = VideoCallingOverlay(context)
        addView(overlay, 1, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        overlay.reload()
        overlay.setCameraOff(DeviceStore.shared().deviceState.cameraStatus.value != DeviceStatus.ON)
        videoCallingOverlay = overlay
    }

    private fun enterMultiMode() {
        if (didEnterMultiMode) {
            return
        }
        didEnterMultiMode = true
        blurBackdrop(defaultAvatarBitmap(), MULTI_BACKDROP_BLUR_RADIUS)
        backdropImage.isVisible = true
        callCoreView.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        avatarImage.isVisible = false
        nameLabel.isVisible = false
        statusLabel.isVisible = false
        callCoreView.isVisible = true
        callCoreView.setLayoutTemplate(CallLayoutTemplate.Grid)
        bindGridLayout()
        applyTranscriptConstraints()
        refreshTranscriptLayoutMode()
        loadAllParticipantAvatars()
    }

    private fun diffParticipantChanges(currentPairs: List<Pair<String, CallParticipantStatus>>) {
        val selfID = CallStore.shared.observerState.selfInfo.value.id
        val current = currentPairs.filter { it.first != selfID }.toMap()
        val previous = lastParticipantStatuses
        lastParticipantStatuses = current

        if (previous.isEmpty() || CallStore.shared.observerState.activeCall.value.callId.isEmpty()) {
            return
        }
        if (!isMulti && !didEnterMultiMode) {
            return
        }
        for ((id, status) in current) {
            if (status == CallParticipantStatus.Accept && previous[id] != CallParticipantStatus.Accept) {
                showMemberToast(R.string.tuicallkit_call_member_joined, id)
            }
        }
        for ((id, previousStatus) in previous) {
            if (current[id] != null) {
                continue
            }
            if (previousStatus == CallParticipantStatus.Accept) {
                showMemberToast(R.string.tuicallkit_call_member_left, id)
            }
        }
    }

    private fun showMemberToast(stringRes: Int, userID: String) {
        CallKitContactLoader.load(listOf(userID)) { _, nickname, _, _ ->
            val name = if (nickname.isNullOrEmpty()) userID else nickname
            CallKitToast.show(context.getString(stringRes, name))
        }
    }

    private fun loadAllParticipantAvatars() {
        val state = CallStore.shared.observerState
        val loginInfo = LoginStore.shared.loginState.loginUserInfo.value
        val selfId = loginInfo?.userID.orEmpty().ifEmpty { state.selfInfo.value.id }
        val ids = state.allParticipants.value.map { it.id }.toMutableList()
        for (id in state.activeCall.value.inviteeIds + listOf(state.activeCall.value.inviterId, selfId)) {
            if (id.isNotEmpty() && !ids.contains(id)) {
                ids.add(id)
            }
        }
        if (ids.isEmpty()) {
            return
        }
        CallKitContactLoader.loadAvatarPaths(ids) { avatars ->
            callCoreView.setParticipantAvatars(avatars)
            val loginAvatarURL = LoginStore.shared.loginState.loginUserInfo.value?.avatarURL
            if (!loginAvatarURL.isNullOrEmpty()) {
                CallKitContactLoader.loadAvatarBitmap(loginAvatarURL) { bitmap ->
                    if (bitmap != null) {
                        applyMultiBackdrop(bitmap)
                    }
                }
                return@loadAvatarPaths
            }
            val selfPath = avatars[selfId]
            if (!selfPath.isNullOrEmpty()) {
                val bitmap = android.graphics.BitmapFactory.decodeFile(selfPath)
                if (bitmap != null) {
                    applyMultiBackdrop(bitmap)
                }
            }
        }
    }

    private fun applyMultiBackdrop(bitmap: android.graphics.Bitmap) {
        if (!didEnterMultiMode) {
            return
        }
        blurBackdrop(bitmap, MULTI_BACKDROP_BLUR_RADIUS)
    }

    private fun loadPeerProfile() {
        val state = CallStore.shared.observerState
        val peerID = state.allParticipants.value.firstOrNull { it.id != state.selfInfo.value.id }?.id
            ?: state.activeCall.value.inviteeIds.firstOrNull()
            ?: return
        nameLabel.text = peerID
        CallKitContactLoader.load(listOf(peerID)) { _, nickname, avatarURL, bitmap ->
            if (!nickname.isNullOrEmpty()) {
                nameLabel.text = nickname
            }
            if (!avatarURL.isNullOrEmpty()) {
                CallKitContactLoader.loadAvatarPaths(listOf(peerID)) { paths ->
                    paths[peerID]?.let { callCoreView.setParticipantAvatars(mapOf(peerID to it)) }
                }
            }
            if (bitmap != null && !didEnterMultiMode) {
                avatarImage.setImageBitmap(bitmap)
                avatarImage.isVisible = !isVideo || isIncomingRinging
                if (!isVideo) {
                    blurBackdrop(bitmap)
                }
            }
        }
    }

    private fun blurBackdrop(source: Bitmap?, radius: Int = DEFAULT_BACKDROP_BLUR_RADIUS) {
        val base = source ?: return
        blurJob?.cancel()
        backdropToken += 1
        val token = backdropToken
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            blurJob = null
            backdropImage.post {
                if (backdropToken == token) {
                    backdropImage.setImageBitmap(base)
                    backdropImage.setRenderEffect(android.graphics.RenderEffect.createBlurEffect(
                        radius.dp(context).toFloat(),
                        radius.dp(context).toFloat(),
                        android.graphics.Shader.TileMode.CLAMP
                    ))
                }
            }
            return
        }
        val width = (resources.displayMetrics.widthPixels / 4).coerceAtLeast(1)
        val height = (resources.displayMetrics.heightPixels / 4).coerceAtLeast(1)
        val scaledRadius = (radius / 4).coerceAtLeast(1)
        blurJob = CoroutineScope(Dispatchers.Default).launch {
            val blurred = CallKitImageBlur.blurred(base, width, height, scaledRadius)
            launch(Dispatchers.Main) {
                if (backdropToken == token) {
                    backdropImage.setImageBitmap(blurred)
                }
            }
        }
    }

    private fun defaultAvatarBitmap(): Bitmap? {
        val drawable = defaultAvatarDrawable() ?: return null
        val bitmap = Bitmap.createBitmap(
            drawable.intrinsicWidth.coerceAtLeast(1),
            drawable.intrinsicHeight.coerceAtLeast(1),
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, bitmap.width, bitmap.height)
        drawable.draw(canvas)
        return bitmap
    }

    private fun defaultAvatarDrawable(): Drawable? {
        return ContextCompat.getDrawable(context, R.drawable.tuicallkit_default_avatar)
    }

    private class RoundedOutlineProvider(private val radiusDp: Float) : ViewOutlineProvider() {
        override fun getOutline(view: View, outline: Outline) {
            val radius = radiusDp * view.resources.displayMetrics.density
            outline.setRoundRect(0, 0, view.width, view.height, radius)
        }
    }

    private class PreviewShadeView(context: Context) : View(context) {
        private val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)

        override fun onDraw(canvas: Canvas) {
            paint.shader = android.graphics.LinearGradient(
                0f, 0f, 0f, height.toFloat(),
                intArrayOf(0x6B000000, 0x2E000000, 0xCC000000.toInt()),
                floatArrayOf(0f, 0.42f, 1f),
                android.graphics.Shader.TileMode.CLAMP
            )
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        }
    }

    private companion object {
        const val AVATAR_CENTER_OFFSET_DP = 100
        const val AVATAR_CORNER_RADIUS_DP = 16f
        const val AVATAR_CONNECTED_SIZE_DP = 96
        const val AVATAR_CONNECTED_RADIUS_DP = 48f
        const val AVATAR_RING_SIZE_DP = 116
        const val DEFAULT_BACKDROP_BLUR_RADIUS = 26
        const val MULTI_BACKDROP_BLUR_RADIUS = 30
    }
}
