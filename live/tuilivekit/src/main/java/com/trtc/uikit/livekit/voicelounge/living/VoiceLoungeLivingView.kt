package com.trtc.uikit.livekit.voicelounge.living

import android.content.Context
import android.graphics.PointF
import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.findViewTreeLifecycleOwner
import com.trtc.uikit.livekit.common.audience.AudienceWelcome
import com.trtc.uikit.livekit.common.audience.AudienceWelcomeView
import com.trtc.uikit.livekit.common.barrage.BarrageInputView
import com.trtc.uikit.livekit.common.barrage.BarrageStreamView
import com.trtc.uikit.livekit.common.barrage.BarrageUIStore
import com.trtc.uikit.livekit.common.gift.GiftBarrageStore
import com.trtc.uikit.livekit.common.gift.display.GiftDisplayView
import com.trtc.uikit.livekit.common.gift.send.GiftSendPanelView
import com.trtc.uikit.livekit.common.like.LikeAnimationView
import com.trtc.uikit.livekit.common.like.LikeUIStore
import com.trtc.uikit.livekit.common.theme.LiveToast
import com.trtc.uikit.livekit.voicelounge.MATCH
import com.trtc.uikit.livekit.voicelounge.VoiceLoungeBag
import com.trtc.uikit.livekit.voicelounge.WRAP
import com.trtc.uikit.livekit.voicelounge.addTo
import com.trtc.uikit.livekit.voicelounge.dp
import com.trtc.uikit.livekit.voicelounge.frameParams
import com.trtc.uikit.livekit.voicelounge.living.bottombar.VoiceLoungeBottomBar
import com.trtc.uikit.livekit.voicelounge.living.aitranscriber.VoiceLoungeAITranscriberStore
import com.trtc.uikit.livekit.voicelounge.living.barrage.VoiceLoungeNoticeStore
import com.trtc.uikit.livekit.voicelounge.living.coguest.VoiceLoungeCoGuestBannerView
import com.trtc.uikit.livekit.voicelounge.living.giftrank.VoiceLoungeGiftRankStore
import com.trtc.uikit.livekit.voicelounge.living.like.VoiceLoungeLikeButton
import com.trtc.uikit.livekit.voicelounge.living.seat.VoiceLoungeSeatAreaView
import com.trtc.uikit.livekit.voicelounge.living.seat.VoiceLoungeSeatStore
import com.trtc.uikit.livekit.voicelounge.living.topbar.VoiceLoungeTopBar
import com.trtc.uikit.livekit.voicelounge.rgb
import com.trtc.uikit.livekit.voicelounge.settings.VoiceLoungeSeatLayout
import com.trtc.uikit.livekit.voicelounge.settings.VoiceLoungeSettings
import com.trtc.uikit.livekit.voicelounge.settings.audioeffect.VoiceLoungeAudioEffectPanel
import com.trtc.uikit.livekit.voicelounge.settings.audioeffect.VoiceLoungeVoiceTuner
import com.trtc.uikit.livekit.voicelounge.settings.theme.VoiceLoungeBackgroundView
import io.trtc.tuikit.atomicxcore.api.device.AudioEffectStore
import io.trtc.tuikit.atomicxcore.api.device.DeviceStatus
import io.trtc.tuikit.atomicxcore.api.device.DeviceStore
import io.trtc.tuikit.atomicxcore.api.live.LiveAudienceListener
import io.trtc.tuikit.atomicxcore.api.live.LiveAudienceStore
import io.trtc.tuikit.atomicxcore.api.live.LiveUserInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

internal class VoiceLoungeLivingView(
    context: Context,
    private val liveID: String,
    private val seatStore: VoiceLoungeSeatStore,
    giftRankStore: VoiceLoungeGiftRankStore,
    clock: VoiceLoungeLiveClock,
    livingActions: VoiceLoungeLivingActions,
    transcriberStore: VoiceLoungeAITranscriberStore,
) : FrameLayout(context) {
    var onClose: (() -> Unit)? = null

    val role: VoiceLoungeRole = seatStore.role

    private val backgroundView = VoiceLoungeBackgroundView(context)
    private val backgroundDimView = View(context)
    private val headerView = VoiceLoungeTopBar(context, role, liveID, seatStore.settings, giftRankStore, clock)
    private val seatStage = VoiceLoungeSeatAreaView(context, seatStore, transcriberStore, livingActions)
    private val messageListView = BarrageStreamView(context)
    private val audienceWelcomeView = AudienceWelcomeView(context)
    private val bottomBar = VoiceLoungeBottomBar(context, liveID, seatStore, livingActions)
    private val requestingView =
        VoiceLoungeCoGuestBannerView(context, VoiceLoungeCoGuestBannerView.Kind.REQUESTING, seatStore)
    private val invitationView =
        VoiceLoungeCoGuestBannerView(context, VoiceLoungeCoGuestBannerView.Kind.INVITATION, seatStore)
    private val leavingView =
        VoiceLoungeCoGuestBannerView(context, VoiceLoungeCoGuestBannerView.Kind.LEAVING, seatStore)
    private val likeAnimationView = LikeAnimationView(context)
    private val likeButton = VoiceLoungeLikeButton(context, interactive = role == VoiceLoungeRole.AUDIENCE)
    private var likeOffsetX = 0f
    private var likeOffsetY = 0f
    private var likePanStartX = 0f
    private var likePanStartY = 0f
    private var likePanStartOffsetX = 0f
    private var likePanStartOffsetY = 0f
    private var likePanActive = false
    private val giftDisplayView = GiftDisplayView(context)
    private val commentComposer = BarrageInputView(context)

    private val noticeStore = VoiceLoungeNoticeStore()
    private val commentStore = BarrageUIStore(context)
    private val likeStore = LikeUIStore { count -> showReceivedLikes(count) }
    private val giftBarrageStore = GiftBarrageStore()
    private var giftPresentationGeneration = 0L

    private val audioEffectPanel = VoiceLoungeAudioEffectPanel(context)
    private var captureVolume = 100
    private val musicView = com.trtc.uikit.livekit.voicelounge.living.music.VoiceLoungeMusicView(context)
    private var isMusicActive = false
    private var isInBackground = false

    private var activatedLiveID = ""
    private var hostUserID = ""
    private var interactionScope: CoroutineScope? = null
    private var audienceStore: LiveAudienceStore? = null
    private var commentToast: android.widget.Toast? = null
    private val bag = VoiceLoungeBag()

    private var statusTop = 0
    private var navigationBottom = 0
    private var barrageVisibleHeight = 0

    private var lifecycleOwner: LifecycleOwner? = null
    private val lifecycleObserver = LifecycleEventObserver { _, event ->
        when (event) {
            Lifecycle.Event.ON_START -> {
                giftBarrageStore.setApplicationActive(true)
                isInBackground = false
                updateMusicSuspension()
            }

            Lifecycle.Event.ON_STOP -> {
                giftBarrageStore.setApplicationActive(false)
                GiftSendPanelView.stopCombo()
                isInBackground = true
                updateMusicSuspension()
            }

            Lifecycle.Event.ON_DESTROY -> {
                giftBarrageStore.setApplicationActive(false)
                dismissGiftPanel()
            }

            else -> Unit
        }
    }

    private val audienceListener = object : LiveAudienceListener() {
        override fun onAudienceJoined(audience: LiveUserInfo) {
            post { showAudienceWelcome(audience) }
        }
    }

    private val settings: VoiceLoungeSettings get() = seatStore.settings

    init {
        setBackgroundColor(rgb(0.06f, 0.07f, 0.10f))
        clipChildren = false
        backgroundView.clipChildren = true
        backgroundDimView.setBackgroundColor(rgb(0.051f, 0.055f, 0.078f, 0.45f))

        addView(backgroundView, frameParams(MATCH, MATCH))
        addView(backgroundDimView, frameParams(MATCH, MATCH))
        addView(headerView, frameParams(MATCH, dp(82), Gravity.TOP))
        addView(seatStage, frameParams(MATCH, WRAP, Gravity.TOP, start = dp(16), end = dp(16)))
        addView(messageListView, frameParams(0, 0, Gravity.BOTTOM or Gravity.START, start = dp(14)))
        addView(audienceWelcomeView, frameParams(MATCH, dp(28), Gravity.BOTTOM))
        addView(bottomBar, frameParams(MATCH, dp(62), Gravity.BOTTOM))
        val likeSizePx = dp(VoiceLoungeLikeButton.SIZE_DP)
        addView(
            likeButton,
            frameParams(likeSizePx, likeSizePx, Gravity.BOTTOM or Gravity.END, end = dp(14), bottom = dp(14)),
        )
        if (role == VoiceLoungeRole.HOST) likeButton.visibility = View.INVISIBLE
        bindLikeDrag()
        addView(likeAnimationView, frameParams(MATCH, MATCH))
        addView(giftDisplayView, frameParams(MATCH, MATCH))
        addView(commentComposer, frameParams(MATCH, MATCH))
        val bannerHeight = dp(VoiceLoungeCoGuestBannerView.HEIGHT)
        addView(requestingView, frameParams(MATCH, bannerHeight, Gravity.BOTTOM, start = dp(14), end = dp(14)))
        addView(invitationView, frameParams(MATCH, bannerHeight, Gravity.BOTTOM, start = dp(14), end = dp(14)))
        addView(leavingView, frameParams(MATCH, bannerHeight, Gravity.BOTTOM, start = dp(14), end = dp(14)))

        giftDisplayView.setActive(false)
        likeAnimationView.setLikeStore(likeStore)
        setupBindings()
        applyInsetLayout()
        applySettings()

        ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.displayCutout())
            statusTop = bars.top
            navigationBottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            applyInsetLayout()
            insets
        }
    }

    fun activateInteractions(hostUserID: String) {
        if (liveID.isEmpty()) return
        deactivateInteractions()
        activatedLiveID = liveID
        this.hostUserID = hostUserID
        commentStore.activate(liveID, hostUserID)
        likeStore.setLiveID(liveID)
        headerView.activate()
        commentComposer.bind(liveID)
        giftDisplayView.bind(liveID)
        giftDisplayView.setActive(true)

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        interactionScope = scope
        scope.launch {
            commentStore.state.collectLatest { state ->
                if (state.liveID == activatedLiveID) renderBarrage(state.comments)
            }
        }
        scope.launch { commentStore.sendErrors.collectLatest(::showCommentError) }
        scope.launch {
            giftBarrageStore.giftBarrages.collectLatest { gift ->
                if (activatedLiveID.isNotEmpty()) commentStore.appendGiftComment(gift)
            }
        }
        giftBarrageStore.activate(liveID)
        giftBarrageStore.setApplicationActive(isHostStarted())
        noticeStore.activate(liveID, role, settings.topic)
        activateAudienceWelcome()
        activateAudioEffects(scope)
        if (role == VoiceLoungeRole.HOST) activateMusic()
    }

    private fun activateAudioEffects(scope: CoroutineScope) {
        if (role != VoiceLoungeRole.AUDIENCE) return
        scope.launch {
            DeviceStore.shared().deviceState.microphoneStatus.map { it == DeviceStatus.ON }.distinctUntilChanged()
                .collectLatest { isOn ->
                    if (!isOn) return@collectLatest
                    if (seatStore.coGuest.guestStatus != com.trtc.uikit.livekit.voicelounge.living.coguest.VoiceLoungeCoGuestStore.GuestStatus.ON_SEAT) return@collectLatest
                    reapplySavedAudioEffects()
                }
        }
    }

    private fun reapplySavedAudioEffects() {
        val effects = AudioEffectStore.shared().audioEffectState
        AudioEffectStore.shared().setAudioChangerType(effects.audioChangerType.value)
        AudioEffectStore.shared().setAudioReverbType(effects.audioReverbType.value)
        AudioEffectStore.shared().setVoiceEarMonitorVolume(effects.earMonitorVolume.value)
        AudioEffectStore.shared().setVoiceEarMonitorEnable(effects.isEarMonitorOpened.value)
        VoiceLoungeVoiceTuner.reapply()
        DeviceStore.shared().setCaptureVolume(captureVolume)
    }

    fun updateHostUserID(hostUserID: String) {
        if (activatedLiveID.isEmpty() || this.hostUserID == hostUserID) return
        this.hostUserID = hostUserID
        commentStore.activate(liveID, hostUserID)
    }

    fun showOpeningTopic() = noticeStore.showOpeningTopic()

    fun deactivateInteractions() {
        interactionScope?.cancel()
        interactionScope = null
        giftBarrageStore.deactivate()
        noticeStore.deactivate()
        audienceStore?.removeLiveAudienceListener(audienceListener)
        audienceStore = null
        audienceWelcomeView.reset()
        commentStore.deactivate()
        commentToast?.cancel()
        commentToast = null
        likeStore.release()
        headerView.deactivate()
        likeAnimationView.removeAllAnimations()
        giftDisplayView.setActive(false)
        giftDisplayView.bind("")
        giftDisplayView.reset()
        commentComposer.reset()
        commentComposer.bind("")
        messageListView.prepareForReuse()
        dismissGiftPanel()
        audioEffectPanel.dismiss()
        VoiceLoungeVoiceTuner.reset()
        deactivateMusic()
        activatedLiveID = ""
    }

    fun dismissOverlays() {
        commentComposer.dismiss()
        dismissGiftPanel()
        audioEffectPanel.takeIf { it.isShowing }?.dismiss()
        musicView.dismissIfShowing()
        bottomBar.dismissPanels()
        headerView.dismissPanels()
        seatStage.dismissPanels()
    }

    fun handleBackPressed(): Boolean = commentComposer.dismiss()

    fun release() {
        deactivateInteractions()
        headerView.release()
        bag.clear()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        ViewCompat.requestApplyInsets(this)
        if (bag.isEmpty) settings.changes.subscribe { applySettings() }.addTo(bag)
        applySettings()
        val owner = findViewTreeLifecycleOwner()
        lifecycleOwner = owner
        owner?.lifecycle?.addObserver(lifecycleObserver)
        isInBackground = !isHostStarted()
        giftBarrageStore.setApplicationActive(isHostStarted())
        updateMusicSuspension()
    }

    override fun onDetachedFromWindow() {
        lifecycleOwner?.lifecycle?.removeObserver(lifecycleObserver)
        lifecycleOwner = null
        giftBarrageStore.setApplicationActive(false)
        dismissGiftPanel()
        bag.clear()
        super.onDetachedFromWindow()
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        updateSeatColumnLimit()
        updateFlexibleLayout()
    }

    private fun isHostStarted(): Boolean =
        lifecycleOwner?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.STARTED) ?: true

    private fun setupBindings() {
        commentComposer.onSend = { text -> commentStore.sendComment(text) }
        audioEffectPanel.onSetCaptureVolume = { volume ->
            captureVolume = volume
            DeviceStore.shared().setCaptureVolume(volume)
        }

        headerView.onClose = { onClose?.invoke() }
        bottomBar.onComment = { presentCommentComposer() }
        likeButton.onLike = { sendLike() }
        bottomBar.onGift = { presentGiftPanel(null) }
        seatStage.onSendGift = { userID, _ -> presentGiftPanel(userID) }
        bottomBar.onAudioEffects = { audioEffectPanel.show(captureVolume, settings.skin) }
        bottomBar.onBackgroundMusic = { if (isMusicActive) musicView.show() }
        messageListView.onVisibleContentHeightChanged = { height ->
            if (barrageVisibleHeight != height) {
                barrageVisibleHeight = height
                updateWelcomeLayout()
            }
        }
        musicView.bind(liveID)
    }

    private fun applySettings() {
        backgroundView.show(settings.background)
        noticeStore.updateTopic(settings.topic)
        musicView.applySkin(settings.skin)
    }

    private fun bottomBarHeight(): Int = dp(62) + navigationBottom

    private fun applyInsetLayout() {
        (headerView.layoutParams as LayoutParams).topMargin = statusTop + dp(10)
        (seatStage.layoutParams as LayoutParams).topMargin = statusTop + dp(10) + dp(82) + dp(14)
        val barHeight = bottomBarHeight()
        (bottomBar.layoutParams as LayoutParams).height = barHeight
        bottomBar.setPadding(0, 0, 0, navigationBottom)
        (requestingView.layoutParams as LayoutParams).bottomMargin = barHeight + dp(10)
        (invitationView.layoutParams as LayoutParams).bottomMargin = barHeight + dp(10)
        (leavingView.layoutParams as LayoutParams).bottomMargin = barHeight + dp(10)
        (messageListView.layoutParams as LayoutParams).bottomMargin = barHeight + dp(8)
        (likeButton.layoutParams as LayoutParams).bottomMargin = barHeight + dp(14)
        updateWelcomeLayout()
        requestLayout()
    }

    private fun updateWelcomeLayout() {
        val params = audienceWelcomeView.layoutParams as LayoutParams
        val margin = bottomBarHeight() + dp(18) + barrageVisibleHeight
        if (params.bottomMargin != margin) {
            params.bottomMargin = margin
            audienceWelcomeView.layoutParams = params
        }
        updateGiftNoticeLayout()
    }

    private fun updateFlexibleLayout() {
        if (width <= 0 || height <= 0) return
        val params = messageListView.layoutParams as LayoutParams
        val targetWidth = (width * 0.78f).toInt()
        val listBottom = height - bottomBarHeight() - dp(8)
        val targetHeight = (listBottom - (seatContentBottom() + dp(12))).coerceAtLeast(0)
        if (params.width != targetWidth || params.height != targetHeight) {
            params.width = targetWidth
            params.height = targetHeight
            messageListView.layoutParams = params
        }
        updateGiftNoticeLayout()
    }

    private fun seatContentBottom(): Int = seatStage.top + seatStage.contentBottom

    private fun updateSeatColumnLimit() {
        if (height <= 0 || seatStage.top <= 0) return
        val likeHomeTop = height - bottomBarHeight() - dp(14) - dp(VoiceLoungeLikeButton.SIZE_DP)
        seatStage.updateColumnLimit(likeHomeTop - dp(12) - seatStage.top)
    }

    private fun updateGiftNoticeLayout() {
        if (height <= 0) return
        val welcomeTop = height - (audienceWelcomeView.layoutParams as LayoutParams).bottomMargin - dp(28)
        giftDisplayView.updateNoticeLayout(welcomeTop - dp(4), seatContentBottom().takeIf { seatStage.bottom > 0 })
    }

    private fun renderBarrage(comments: List<BarrageUIStore.Comment>) {
        messageListView.bind(comments.filter { it.gift != null || it.text.isNotEmpty() }.map {
            BarrageStreamView.Message(
                id = it.id,
                userName = it.senderName,
                content = it.text,
                level = it.level,
                isHost = it.isHost,
                gift = it.gift,
            )
        })
    }

    private fun showCommentError(messageRes: Int) {
        if (activatedLiveID.isEmpty() || !isAttachedToWindow || !isShown) return
        commentToast?.cancel()
        commentToast = LiveToast.showScoped(context, context.getString(messageRes))
    }

    private fun presentCommentComposer() {
        dismissGiftPanel()
        commentComposer.show()
    }

    private fun presentGiftPanel(receiverID: String?) {
        if (activatedLiveID.isEmpty()) return
        commentComposer.dismiss()
        dismissGiftPanel()
        giftPresentationGeneration++
        GiftSendPanelView.show(context, liveID, receiverID.orEmpty())
    }

    private fun dismissGiftPanel() {
        giftPresentationGeneration++
        GiftSendPanelView.dismiss()
    }

    private fun bindLikeDrag() {
        if (role != VoiceLoungeRole.AUDIENCE) return
        val touchSlop = android.view.ViewConfiguration.get(context).scaledTouchSlop
        likeButton.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    likePanStartX = event.rawX
                    likePanStartY = event.rawY
                    likePanStartOffsetX = likeOffsetX
                    likePanStartOffsetY = likeOffsetY
                    likePanActive = false
                    false
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - likePanStartX
                    val dy = event.rawY - likePanStartY
                    if (!likePanActive && kotlin.math.hypot(dx, dy) < touchSlop) {
                        false
                    } else {
                        likePanActive = true
                        likeButton.parent?.requestDisallowInterceptTouchEvent(true)
                        val target = clampLikeOffset(likePanStartOffsetX + dx, likePanStartOffsetY + dy)
                        likeOffsetX = target[0]
                        likeOffsetY = target[1]
                        likeButton.translationX = likeOffsetX
                        likeButton.translationY = likeOffsetY
                        true
                    }
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    val consumed = likePanActive
                    likePanActive = false
                    consumed
                }

                else -> false
            }
        }
    }

    private fun clampLikeOffset(rawX: Float, rawY: Float): FloatArray {
        val leftMarginPx = dp(14).toFloat()
        val topMarginPx = (statusTop + dp(10) + dp(82) + dp(14)).toFloat()
        val homeLeft = (width - leftMarginPx - likeButton.width).toFloat()
        val homeTop = (height - bottomBarHeight() - dp(14) - likeButton.height).toFloat()
        val minX = leftMarginPx - homeLeft
        val minY = topMarginPx - homeTop
        return floatArrayOf(rawX.coerceIn(minX, 0f), rawY.coerceIn(minY, 0f))
    }

    private fun sendLike() {
        val center = likeButton.heartCenter(likeAnimationView)
        likeAnimationView.showButtonLike(PointF(center[0], center[1]))
        likeStore.sendLike()
    }

    private fun showReceivedLikes(count: Int) {
        if (activatedLiveID.isEmpty()) return
        likeButton.pulse()
        val center = likeButton.heartCenter(likeAnimationView)
        val origin = PointF(center[0], center[1])
        likeAnimationView.showReceivedLikes(count, origin)
    }

    private fun activateAudienceWelcome() {
        audienceStore?.removeLiveAudienceListener(audienceListener)
        val store = LiveAudienceStore.create(liveID)
        store.addLiveAudienceListener(audienceListener)
        audienceStore = store
    }

    private fun showAudienceWelcome(user: LiveUserInfo) {
        if (activatedLiveID.isEmpty() || user.userID.isEmpty()) return
        audienceWelcomeView.enqueue(
            AudienceWelcome(
                userID = user.userID,
                userName = user.userName.ifEmpty { user.userID },
                level = maxOf(0, user.level),
            )
        )
    }

    private fun activateMusic() {
        isMusicActive = true
        isInBackground = !isHostStarted()
        musicView.activate()
        updateMusicSuspension()
    }

    private fun deactivateMusic() {
        if (!isMusicActive) return
        isMusicActive = false
        musicView.deactivate()
        musicView.dismissIfShowing()
    }

    private fun updateMusicSuspension() {
        if (isMusicActive) musicView.setSuspended(isInBackground)
    }

}

internal class VoiceLoungeLiveClock {
    var startTimeMillis: Long? = null
        private set

    private var startUptime: Long? = null

    val elapsedSeconds: Int
        get() {
            val start = startUptime ?: return 0
            return maxOf(0L, (SystemClock.elapsedRealtime() - start) / 1000).toInt()
        }

    fun start() {
        startTimeMillis = System.currentTimeMillis()
        startUptime = SystemClock.elapsedRealtime()
    }

    fun stop() {
        startTimeMillis = null
        startUptime = null
    }
}

internal class VoiceLoungeLivingActions {
    var isMuted: () -> Boolean = { false }
    var toggleMute: () -> Unit = {}
    var changeLayout: (VoiceLoungeSeatLayout) -> Unit = {}
    var changeTopic: (String) -> Unit = {}
    var updateTranscriber: (com.trtc.uikit.livekit.voicelounge.settings.aitranscriber.VoiceLoungeAITranscriberPanel.State) -> Unit =
        {}
    var showTranscriberPanel: () -> Unit = {}
    var confirm: (title: String, message: String, confirmTitle: String, action: () -> Unit) -> Unit = { _, _, _, _ -> }
}

internal enum class VoiceLoungeRole {
    HOST, AUDIENCE,
}
