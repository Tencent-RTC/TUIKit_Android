package com.trtc.uikit.livekit.pusher.living

import com.trtc.uikit.livekit.common.audience.AudienceWelcome
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.RectF
import android.view.ViewTreeObserver
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.Shader
import android.os.SystemClock
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.findViewTreeLifecycleOwner
import com.trtc.uikit.livekit.R
import com.trtc.uikit.livekit.common.barrage.BarrageUIStore
import com.trtc.uikit.livekit.common.barrage.BarrageInputView
import com.trtc.uikit.livekit.common.barrage.BarrageStreamView
import com.trtc.uikit.livekit.common.barrage.BarrageStreamView.Message
import com.trtc.uikit.livekit.common.gift.GiftPlayView
import com.trtc.uikit.livekit.pusher.living.coguest.controlpanel.PusherCoGuestSettingsView
import com.trtc.uikit.livekit.pusher.living.coguest.controlpanel.PusherCoGuestAudienceManageView
import com.trtc.uikit.livekit.pusher.living.coguest.PusherCoGuestActionHandling
import com.trtc.uikit.livekit.pusher.living.coguest.PusherCoGuestStore
import com.trtc.uikit.livekit.common.audience.AudienceWelcomeView
import com.trtc.uikit.livekit.pusher.living.audience.PusherAudienceListView
import com.trtc.uikit.livekit.pusher.living.audience.PusherAudienceManageView
import com.trtc.uikit.livekit.pusher.living.audience.PusherAudienceStore
import com.trtc.uikit.livekit.common.like.LikeAnimationView
import com.trtc.uikit.livekit.common.theme.toast.LiveToast
import com.trtc.uikit.livekit.pusher.living.gift.PusherGiftStore
import com.trtc.uikit.livekit.pusher.living.music.PusherMusicView
import com.trtc.uikit.livekit.pusher.living.pause.PusherPauseOverlayView
import com.trtc.uikit.livekit.pusher.living.endconfirm.PusherEndConfirmationView
import com.trtc.uikit.livekit.pusher.living.status.PusherLiveStatusView
import com.trtc.uikit.livekit.pusher.living.status.PusherDeviceStatusState
import com.trtc.uikit.livekit.pusher.settings.PusherSettingsActionHandling
import com.trtc.uikit.livekit.pusher.settings.PusherSettingsState
import io.trtc.tuikit.atomicxcore.api.device.VideoQuality
import io.trtc.tuikit.atomicxcore.api.live.LikeListener
import io.trtc.tuikit.atomicxcore.api.live.LikeStore
import io.trtc.tuikit.atomicxcore.api.live.LiveUserInfo
import io.trtc.tuikit.atomicxcore.api.login.LoginStore
import io.trtc.tuikit.atomicxcore.api.device.NetworkQuality
import java.lang.ref.WeakReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/** Living stage of the host entry; owns audience, likes, comments and their presentation. */
class PusherLivingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {
    internal var settingsActionHandler: PusherSettingsActionHandling?
        get() = settingsActionHandlerReference?.get()
        set(value) {
            settingsActionHandlerReference = value?.let(::WeakReference)
            settingsView.settingsActionHandler = value
        }
    internal var coGuestActionHandler: PusherCoGuestActionHandling?
        get() = coGuestActionHandlerReference?.get()
        set(value) {
            coGuestActionHandlerReference = value?.let(::WeakReference)
            coGuestSettingsView.actionHandler = value
            coGuestAudienceManageView.actionHandler = value
        }
    internal var onShowBeauty: (() -> Unit)? = null
    internal var onRefreshSettings: (() -> Unit)? = null
    internal var onEnd: (() -> Unit)? = null
    internal var onToggleMute: (() -> Unit)? = null
    internal var onPause: (() -> Unit)? = null
    internal var onResume: (() -> Unit)? = null
    internal var onFlipCamera: (() -> Unit)? = null
    internal var coGuestSmallWindowRegions: (() -> List<RectF>)? = null
    private val geometryListener = ViewTreeObserver.OnPreDrawListener {
        updateCommentWidth()
        true
    }

    internal var onCoGuestStageTap: ((PointF) -> Boolean)? = null

    companion object {
        private const val CONTINUOUS_TAP_WINDOW_MS = 420L
        private const val LIKE_SEND_INTERVAL_MS = 6_000L
    }

    private var lastStageTapTimeMillis = 0L

    private val headerView: LiveHeaderView
    private val messageListView: BarrageStreamView
    private val audienceWelcomeView: AudienceWelcomeView
    private val audienceListView = PusherAudienceListView(context)
    private val audienceManageView = PusherAudienceManageView(context)
    private val bottomToolbarView: LiveBottomToolbarView
    private val likeAnimationView: LikeAnimationView
    private val giftPlayView: GiftPlayView
    private val settingsView = PusherLivingSettingsView(context)
    private val musicView = PusherMusicView(context)
    private val pauseOverlay: PusherPauseOverlayView
    private val endConfirmationView = PusherEndConfirmationView(context)
    private val liveStatusView = PusherLiveStatusView(context)
    private val coGuestSettingsView = PusherCoGuestSettingsView(context)
    private val coGuestAudienceManageView = PusherCoGuestAudienceManageView(context)
    private val commentComposerView: BarrageInputView
    private var headerSafeTop = 0
    private var messageListBottom: Int
    private val messageListHeight: Int
    private var audienceWelcomeBaseBottom: Int

    private var liveID = ""
    private var renderedAudienceState: PusherAudienceStore.State? = null
    private var settingsActionHandlerReference: WeakReference<PusherSettingsActionHandling>? = null
    private var coGuestActionHandlerReference: WeakReference<PusherCoGuestActionHandling>? = null
    private var coGuestState = PusherCoGuestStore.State()
    private var messageHeightAnimator: ValueAnimator? = null
    private var messageHeightTarget = 0
    private var isPaused = false
    private var viewerCount = 0
    private var audienceState = PusherAudienceStore.State()
    private var audienceStore: PusherAudienceStore? = null
    private var audienceScope: CoroutineScope? = null
    private var audienceStateJob: Job? = null
    private var audienceEnteredJob: Job? = null
    private var audienceActionJob: Job? = null
    private var likeStore: LikeStore? = null
    private var likeScope: CoroutineScope? = null
    private var likeStateJob: Job? = null
    private var likeFlushJob: Job? = null
    internal var likeCount = 0
        private set
    private var pendingLikeCount = 0
    private var lastLikeSendTime = 0L
    private var isLikeActive = false
    private val commentStore = BarrageUIStore(context)
    private var commentToast: android.widget.Toast? = null
    private var giftStore: PusherGiftStore? = null
    private var commentScope: CoroutineScope? = null
    private var isCommentActive = false
    private var isMusicInBackground = false
    private var lifecycleOwner: LifecycleOwner? = null
    private val lifecycleObserver = LifecycleEventObserver { _, event ->
        when (event) {
            Lifecycle.Event.ON_START -> giftStore?.setApplicationActive(true)
            Lifecycle.Event.ON_RESUME -> {
                isMusicInBackground = false
                updateMusicSuspension()
            }
            Lifecycle.Event.ON_STOP, Lifecycle.Event.ON_DESTROY -> {
                giftStore?.setApplicationActive(false)
                isMusicInBackground = true
                updateMusicSuspension()
            }
            else -> Unit
        }
    }
    private val likeListener = object : LikeListener() {
        override fun onReceiveLikesMessage(
            liveID: String,
            totalLikesReceived: Long,
            sender: LiveUserInfo,
        ) {
            val activeScope = likeScope ?: return
            activeScope.launch {
                if (!isLikeActive || liveID != this@PusherLivingView.liveID) return@launch
                showReceivedLikes(totalLikesReceived.coerceAtMost(6L).toInt())
            }
        }
    }

    init {
        LayoutInflater.from(context).inflate(R.layout.livekit_pusher_living_view, this, true)
        headerView = findViewById(R.id.livekit_pusher_living_header)
        messageListView = findViewById(R.id.livekit_pusher_living_message_list)
        audienceWelcomeView = findViewById(R.id.livekit_pusher_living_audience_welcome)
        bottomToolbarView = findViewById(R.id.livekit_pusher_living_bottom_toolbar)
        likeAnimationView = findViewById(R.id.livekit_pusher_living_like_animation)
        giftPlayView = findViewById(R.id.livekit_pusher_living_gift_overlay)
        giftPlayView.setActive(false)
        pauseOverlay = findViewById(R.id.livekit_pusher_living_pause_overlay)
        commentComposerView = findViewById(R.id.livekit_pusher_living_comment_composer)
        headerView.onRequestEnd = ::showEndConfirmation
        headerView.onShowStatus = liveStatusView::show
        headerView.onShowAudience = audienceListView::show
        audienceListView.onRefresh = { audienceStore?.refresh() }
        audienceListView.onSelectAudience = { user ->
            audienceManageView.show(user, audienceState)
            audienceStore?.refreshFollowStatus(user.userID)
        }
        audienceManageView.onToggleFollow = { audienceStore?.toggleFollow(it) }
        audienceManageView.onToggleMessagePermission = {
            audienceStore?.toggleMessagePermission(it)
        }
        audienceManageView.onRemoveFromRoom = { audienceStore?.removeFromRoom(it) }
        bottomToolbarView.onShowCoGuest = {
            coGuestSettingsView.show()
            coGuestActionHandler?.markApplicantsSeen()
        }
        bottomToolbarView.onShowLivingSettings = {
            onRefreshSettings?.invoke()
            settingsView.show()
        }
        commentComposerView.onPresentationChanged = { isPresented, animated, messageListBottom ->
            updateMessageListLayout(isPresented, messageListBottom, animated)
        }
        commentComposerView.onSend = ::sendComment
        settingsView.onShowBeauty = { onShowBeauty?.invoke() }
        settingsView.onPause = { onPause?.invoke() }
        settingsView.onToggleMute = { onToggleMute?.invoke() }
        settingsView.onShowComment = commentComposerView::show
        settingsView.onShowMusic = musicView::show
        settingsView.onFlipCamera = { onFlipCamera?.invoke() }
        liveStatusView.onSetCaptureVolume = { settingsActionHandler?.setCaptureVolume(it) }
        pauseOverlay.onResume = { onResume?.invoke() }
        endConfirmationView.onConfirmEnd = { onEnd?.invoke() }
        messageListBottom = messageListView.layoutParams.let { (it as MarginLayoutParams).bottomMargin }
        messageListHeight = messageListView.layoutParams.height
        audienceWelcomeBaseBottom = audienceWelcomeView.layoutParams.let {
            (it as MarginLayoutParams).bottomMargin
        }
        messageHeightTarget = messageListHeight
        setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_UP) {
                handleStageTap(event)
            }
            true
        }
        ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
            val navigationBottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            messageListBottom = dp(82) + navigationBottom
            audienceWelcomeBaseBottom = dp(86) + navigationBottom
            findViewById<View>(R.id.livekit_pusher_living_bottom_scrim).apply {
                layoutParams = layoutParams.apply { height = dp(152) + navigationBottom }
            }
            updateAudienceWelcomeLayout()
            headerSafeTop = insets.getInsets(
                WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.displayCutout()
            ).top
            updateHeaderTop()
            if (commentComposerView.visibility != VISIBLE) {
                setMessageListLayout(
                    height = messageListHeight,
                    bottom = messageListBottom,
                )
            }
            insets
        }
    }

    private fun handleStageTap(event: MotionEvent) {
        if (
            !isVisible ||
            isPaused ||
            commentComposerView.visibility == VISIBLE ||
            settingsView.isShowing ||
            musicView.isShowing ||
            liveStatusView.isShowing ||
            endConfirmationView.isShowing ||
            audienceListView.isShowing ||
            audienceManageView.isShowing ||
            coGuestSettingsView.isShowing
            || coGuestAudienceManageView.isShowing
        ) {
            lastStageTapTimeMillis = 0L
            return
        }

        if (onCoGuestStageTap?.invoke(PointF(event.x, event.y)) == true) {
            lastStageTapTimeMillis = 0L
            return
        }
        val tapTimeMillis = event.eventTime
        val isContinuousTap = lastStageTapTimeMillis > 0L &&
            tapTimeMillis - lastStageTapTimeMillis <= CONTINUOUS_TAP_WINDOW_MS
        lastStageTapTimeMillis = tapTimeMillis
        if (!isContinuousTap) return

        likeAnimationView.showStageLike(
            PointF(event.x, event.y),
            bottomToolbarView.likeAnimationOriginIn(likeAnimationView),
        )
        registerLike()
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        updateCommentWidth()
        updateAudienceWelcomeLayout()
    }

    fun bind(liveID: String) {
        deactivateLikes(resetState = true)
        likeStore = null
        deactivateComments(resetState = true)
        giftStore = null
        audienceStore?.deactivate(true)
        audienceStateJob?.cancel()
        audienceEnteredJob?.cancel()
        audienceActionJob?.cancel()
        audienceScope?.cancel()
        audienceStore = null
        audienceScope = null
        audienceState = PusherAudienceStore.State()
        commentComposerView.dismiss()
        this.liveID = liveID
        bottomToolbarView.bind(liveID)
        commentComposerView.bind(liveID)
        giftPlayView.bind(liveID)
        musicView.bind(liveID)
        liveStatusView.bind(liveID)
        if (liveID.isNotEmpty()) {
            likeStore = LikeStore.create(liveID)
            giftStore = PusherGiftStore(liveID)
            val store = PusherAudienceStore(liveID)
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
            audienceStore = store
            audienceScope = scope
            audienceStateJob = scope.launch {
                store.state.collectLatest {
                    audienceState = it
                    renderAudience()
                }
            }
            audienceEnteredJob = scope.launch {
                store.audienceEntered.collectLatest(::showAudienceWelcome)
            }
            audienceActionJob = scope.launch {
                store.actionCompleted.collectLatest(::completeAudienceAction)
            }
        }
        renderAudience()
        renderComments(emptyList())
    }

    fun handleBackPressed(): Boolean =
        pauseOverlay.isVisible || coGuestAudienceManageView.dismissIfShowing() || coGuestSettingsView.dismissIfShowing() || liveStatusView.dismissIfShowing() || endConfirmationView.dismissIfShowing() || audienceManageView.dismissIfShowing() || audienceListView.dismissIfShowing() ||
            musicView.dismissIfShowing() || settingsView.dismissIfShowing() ||
            commentComposerView.dismiss()

    fun dismissTransientUi() {
        lastStageTapTimeMillis = 0L
        settingsView.cancelPendingAction()
        audienceListView.cancelPendingSelection()
        commentComposerView.dismiss()
        settingsView.dismiss()
        musicView.dismiss()
        likeAnimationView.removeAllAnimations()
        audienceWelcomeView.reset()
        giftPlayView.reset()
        audienceListView.dismissIfShowing()
        audienceManageView.dismissIfShowing()
        pauseOverlay.bind(false, 0)
        endConfirmationView.dismiss()
        liveStatusView.dismiss()
        coGuestSettingsView.dismiss()
        coGuestAudienceManageView.dismiss()
    }

    internal fun resetAfterStartFailure() {
        // Discard unsent likes before normal deactivation can flush them.
        pendingLikeCount = 0
        lastLikeSendTime = 0L
        likeFlushJob?.cancel()
        likeFlushJob = null
        deactivateAudience(resetState = true)
        deactivateLikes(resetState = true)
        deactivateComments(resetState = true)
        deactivateMusic()
        updatePause(isPaused = false, seconds = 0)
        updateElapsedSeconds(0)
        dismissTransientUi()
        messageHeightAnimator?.cancel()
        messageHeightAnimator = null
        messageHeightTarget = messageListHeight
        setMessageListLayout(messageListHeight, messageListBottom)
    }

    internal fun updateHost(name: String, avatarURL: String) {
        headerView.updateHost(name, avatarURL)
    }

    internal fun updateSettings(settings: PusherSettingsState) {
        settingsView.bind(settings)
    }

    internal fun updateDeviceStatus(
        status: PusherDeviceStatusState,
        videoQuality: VideoQuality,
        isMuted: Boolean,
        captureVolume: Int,
    ) {
        headerView.updateNetworkQuality(when (status.networkQuality) {
            1 -> NetworkQuality.EXCELLENT
            2 -> NetworkQuality.GOOD
            3 -> NetworkQuality.POOR
            4 -> NetworkQuality.BAD
            5 -> NetworkQuality.VERY_BAD
            6 -> NetworkQuality.DOWN
            else -> NetworkQuality.UNKNOWN
        })
        liveStatusView.updateDeviceStatus(status, videoQuality, isMuted, captureVolume)
    }

    internal fun updateElapsedSeconds(seconds: Int) {
        headerView.updateElapsedSeconds(seconds)
        liveStatusView.updateElapsedSeconds(seconds)
    }

    internal fun updatePause(isPaused: Boolean, seconds: Int) {
        val wasPaused = this.isPaused
        this.isPaused = isPaused
        pauseOverlay.bind(isPaused, seconds)
        giftPlayView.setSuspended(isPaused)
        updateMusicSuspension()
        if (isPaused && !wasPaused) {
            lastStageTapTimeMillis = 0L
            settingsView.dismiss()
            musicView.dismiss()
            commentComposerView.dismiss()
            audienceListView.dismissIfShowing()
            audienceManageView.dismissIfShowing()
            endConfirmationView.dismiss()
            liveStatusView.dismiss()
            coGuestSettingsView.dismiss()
            coGuestAudienceManageView.dismiss()
            likeAnimationView.removeAllAnimations()
        }
    }

    fun bindCoGuest(state: PusherCoGuestStore.State) = updateCoGuest(state)

    internal fun showCoGuestToast(message: String) {
        // Settings use a separate window, so page-level toasts would be obscured.
        val toastContainer = if (coGuestSettingsView.isShowing) {
            coGuestSettingsView.window?.decorView as? ViewGroup
        } else null
        LiveToast.show(context, message)
    }

    internal fun updateCoGuest(state: PusherCoGuestStore.State) {
        val needsLayoutUpdate = coGuestState.layout != state.layout ||
            coGuestState.connected.size != state.connected.size
        coGuestState = state
        if (needsLayoutUpdate) updateCommentWidth()
        bottomToolbarView.setCoGuestUnread(state.hasUnreadApplicant)
        if (state.hasUnreadApplicant && coGuestSettingsView.isShowing) {
            coGuestActionHandler?.markApplicantsSeen()
        }
        coGuestSettingsView.bind(state)
        coGuestAudienceManageView.bind(state)
    }

    fun showCoGuestActions(userID: String) {
        val user = coGuestState.connected.firstOrNull { it.userID == userID } ?: return
        coGuestAudienceManageView.show(user, userID in coGuestState.pendingUserIDs)
    }

    fun setCoGuestStageActive(isActive: Boolean) {
        bottomToolbarView.setCoGuestStageActive(isActive)
    }

    internal fun showAudienceWelcome(welcome: AudienceWelcome) {
        if (isPaused || visibility != VISIBLE) return
        audienceWelcomeView.enqueue(welcome)
    }

    private fun showReceivedLikes(count: Int) {
        if (isPaused || visibility != VISIBLE) return
        likeAnimationView.showReceivedLikes(
            count,
            bottomToolbarView.likeAnimationOriginIn(likeAnimationView),
        )
    }

    private fun updateHeaderTop() {
        // Ardot 77:405 在 844 高页面的 y=46；只适配顶部留白，不缩放控件。
        val top = maxOf(headerSafeTop, (height * 46f / 844f + 0.5f).toInt())
        val params = headerView.layoutParams as MarginLayoutParams
        if (params.topMargin != top) {
            params.topMargin = top
            headerView.layoutParams = params
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateHeaderTop()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        ViewCompat.requestApplyInsets(this)
        viewTreeObserver.addOnPreDrawListener(geometryListener)
        val owner = findViewTreeLifecycleOwner() ?: return
        lifecycleOwner = owner
        owner.lifecycle.addObserver(lifecycleObserver)
        giftStore?.setApplicationActive(
            owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        )
    }

    override fun onDetachedFromWindow() {
        lifecycleOwner?.lifecycle?.removeObserver(lifecycleObserver)
        lifecycleOwner = null
        giftStore?.setApplicationActive(false)
        audienceStore?.deactivate(true)
        audienceStore = null
        audienceStateJob?.cancel()
        audienceStateJob = null
        audienceEnteredJob?.cancel()
        audienceEnteredJob = null
        audienceActionJob?.cancel()
        audienceActionJob = null
        audienceScope?.cancel()
        audienceScope = null
        deactivateLikes(resetState = true)
        deactivateComments(resetState = true)
        deactivateMusic()
        coGuestSettingsView.dismiss()
        audienceListView.dismissIfShowing()
        audienceManageView.dismissIfShowing()
        messageHeightAnimator?.cancel()
        messageHeightAnimator = null
        viewTreeObserver.removeOnPreDrawListener(geometryListener)
        super.onDetachedFromWindow()
    }

    private fun showEndConfirmation() {
        endConfirmationView.show(viewerCount)
    }

    private fun completeAudienceAction(result: PusherAudienceStore.ActionResult) {
        audienceManageView.completeAction(result)
        if (!result.succeeded || visibility != VISIBLE) {
            return
        }
        val messageRes = when (result.action) {
            PusherAudienceStore.Action.MUTE -> R.string.livekit_pusher_audience_muted_toast
            PusherAudienceStore.Action.UNMUTE -> R.string.livekit_pusher_audience_unmuted_toast
            PusherAudienceStore.Action.REMOVE -> R.string.livekit_pusher_audience_removed_toast
            PusherAudienceStore.Action.FOLLOW,
            PusherAudienceStore.Action.UNFOLLOW,
            -> return
        }
        val user = audienceState.users.firstOrNull { it.userID == result.userID }
        val name = user?.userName?.ifEmpty { user.userID } ?: result.userID
        LiveToast.show(context, context.getString(messageRes, name))
    }

    internal fun activateAudience() {
        audienceStore?.activate()
    }

    internal fun refreshAudience() {
        audienceStore?.refresh()
    }

    internal fun deactivateAudience(resetState: Boolean) {
        audienceStore?.deactivate(resetState)
        audienceWelcomeView.reset()
    }

    internal fun activateMusic() {
        isMusicInBackground = lifecycleOwner?.lifecycle?.currentState
            ?.isAtLeast(Lifecycle.State.STARTED) != true
        musicView.activate()
        updateMusicSuspension()
    }

    private fun updateMusicSuspension() {
        musicView.setSuspended(isPaused || isMusicInBackground)
    }

    internal fun deactivateMusic() {
        musicView.deactivate()
    }

    internal fun activateLikes() {
        if (isLikeActive) return
        val store = likeStore ?: return
        isLikeActive = true
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        likeScope = scope
        updateLikeCount(store.likeState.totalLikeCount.value)
        store.addLikeListener(likeListener)
        likeStateJob = scope.launch {
            store.likeState.totalLikeCount.collectLatest { count ->
                if (isLikeActive) updateLikeCount(count)
            }
        }
    }

    internal fun deactivateLikes(resetState: Boolean) {
        if (isLikeActive) {
            isLikeActive = false
            flushPendingLikes()
        }
        likeStore?.removeLikeListener(likeListener)
        likeStateJob?.cancel()
        likeStateJob = null
        likeScope?.cancel()
        likeScope = null
        if (resetState) {
            likeCount = 0
            renderLikes()
        }
    }

    internal fun activateComments() {
        if (isCommentActive || liveID.isEmpty()) return
        val giftStore = giftStore ?: return
        isCommentActive = true
        giftPlayView.setActive(true)
        commentStore.activate(
            liveID,
            LoginStore.shared.loginState.loginUserInfo.value?.userID.orEmpty(),
        )
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        commentScope = scope
        scope.launch {
            commentStore.state.collectLatest { state ->
                if (isCommentActive && state.liveID == liveID) renderComments(state.comments)
            }
        }
        scope.launch {
            commentStore.sendErrors.collectLatest { messageRes ->
                if (isCommentActive && isAttachedToWindow && isShown && windowVisibility == VISIBLE) {
                    commentToast?.cancel()
                    commentToast = LiveToast.showScoped(context, context.getString(messageRes))
                }
            }
        }
        scope.launch {
            giftStore.giftComments.collectLatest { gift ->
                if (isCommentActive) commentStore.appendGiftComment(gift)
            }
        }
        giftStore.activate()
        giftStore.setApplicationActive(
            lifecycleOwner?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.STARTED) == true
        )
    }

    internal fun pauseComments() {
        if (!isCommentActive) return
        giftStore?.setGiftCommentCollectionEnabled(false)
    }

    internal fun resumeComments() {
        if (!isCommentActive) return
        giftStore?.setGiftCommentCollectionEnabled(true)
    }

    internal fun deactivateComments(resetState: Boolean) {
        isCommentActive = false
        giftPlayView.setActive(false)
        commentStore.deactivate()
        commentToast?.cancel()
        commentToast = null
        commentComposerView.reset()
        commentScope?.cancel()
        commentScope = null
        giftStore?.deactivate(resetState)
        if (!resetState) return
        renderComments(emptyList())
    }

    internal val audienceCount: Int
        get() = audienceState.count

    private fun renderAudience() {
        val state = audienceState
        val previous = renderedAudienceState
        if (previous == state) return
        renderedAudienceState = state
        viewerCount = state.count
        if (previous?.users != state.users) audienceListView.bind(state.users)
        audienceManageView.bind(state)
        if (previous?.users != state.users || previous.count != state.count) {
            headerView.updateAudience(state.users, state.count)
        }
        liveStatusView.updateAudienceCount(state.count)
    }

    private fun updateLikeCount(count: Long) {
        val value = count.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
        if (likeCount == value) return
        likeCount = value
        renderLikes()
    }

    private fun renderLikes() {
        val count = likeCount
        headerView.updateLikeCount(count)
        liveStatusView.updateLikeCount(count)
    }

    private fun registerLike() {
        if (!isLikeActive || isPaused) return
        pendingLikeCount += 1
        scheduleLikeFlush()
    }

    private fun scheduleLikeFlush() {
        val elapsed = SystemClock.elapsedRealtime() - lastLikeSendTime
        if (elapsed >= LIKE_SEND_INTERVAL_MS) {
            flushPendingLikes()
            return
        }
        if (likeFlushJob != null) return
        likeFlushJob = likeScope?.launch {
            delay(LIKE_SEND_INTERVAL_MS - elapsed)
            flushPendingLikes()
        }
    }

    private fun flushPendingLikes() {
        likeFlushJob?.cancel()
        likeFlushJob = null
        if (pendingLikeCount <= 0) return
        val count = pendingLikeCount
        pendingLikeCount = 0
        lastLikeSendTime = SystemClock.elapsedRealtime()
        likeStore?.sendLike(count, null)
    }

    private fun sendComment(text: String) {
        if (isCommentActive) commentStore.sendComment(text)
    }

    private fun renderComments(comments: List<BarrageUIStore.Comment>) {
        messageListView.bind(comments.map { comment ->
            Message(
                id = comment.id,
                userName = comment.senderName,
                content = comment.text,
                level = comment.level,
                isHost = comment.isHost,
                gift = comment.gift,
            )
        })
    }

    internal fun refreshCoGuestGeometry() {
        updateCommentWidth()
    }

    private fun updateCommentWidth() {
        if (width <= 0) return
        val defaultWidth = minOf((width * BarrageStreamView.DEFAULT_WIDTH_RATIO).toInt(), width - dp(24))
        val params = messageListView.layoutParams as MarginLayoutParams
        val viewportBottom = height - params.bottomMargin
        val viewportTop = viewportBottom - params.height
        val smallWindows = coGuestState.layout == PusherCoGuestStore.Layout.FLOATING ||
            coGuestState.layout == PusherCoGuestStore.Layout.FIXED
        val edge = if (smallWindows) coGuestSmallWindowRegions?.invoke()
            ?.filter { it.bottom > viewportTop && it.top < viewportBottom }
            ?.minOfOrNull { it.left.toInt() - dp(8) } else null
        val resolved = maxOf(dp(17), minOf(defaultWidth, edge?.minus(dp(12)) ?: defaultWidth))
        if (params.width == resolved) return
        params.width = resolved
        messageListView.layoutParams = params
    }

    private fun updateMessageListLayout(
        isComposerPresented: Boolean,
        composerBottom: Int?,
        animated: Boolean,
    ) {
        val targetHeight = if (isComposerPresented) dp(132) else messageListHeight
        val targetBottom = if (isComposerPresented) {
            composerBottom ?: messageListBottom
        } else {
            messageListBottom
        }
        setMessageListBottom(targetBottom)

        if (!animated) {
            if (messageHeightAnimator?.isRunning != true || messageHeightTarget != targetHeight) {
                messageHeightAnimator?.cancel()
                messageHeightAnimator = null
                messageHeightTarget = targetHeight
                setMessageListHeight(targetHeight)
            }
            return
        }
        messageHeightAnimator?.cancel()
        messageHeightTarget = targetHeight
        if (messageListView.layoutParams.height == targetHeight) return
        val startHeight = messageListView.layoutParams.height
        messageHeightAnimator = ValueAnimator.ofInt(startHeight, targetHeight).apply {
            duration = 250L
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { animator ->
                setMessageListHeight(animator.animatedValue as Int)
            }
            start()
        }
    }

    private fun setMessageListHeight(height: Int) {
        (messageListView.layoutParams as MarginLayoutParams).apply {
            this.height = height
            messageListView.layoutParams = this
        }
    }

    private fun setMessageListBottom(bottom: Int) {
        (messageListView.layoutParams as MarginLayoutParams).apply {
            bottomMargin = bottom
            messageListView.layoutParams = this
        }
    }

    private fun setMessageListLayout(height: Int, bottom: Int) {
        (messageListView.layoutParams as MarginLayoutParams).apply {
            this.height = height
            bottomMargin = bottom
            messageListView.layoutParams = this
        }
    }

    private fun updateAudienceWelcomeLayout() {
        val messageContentHeight = messageListView.visibleContentHeight()
        val targetBottom = audienceWelcomeBaseBottom + messageContentHeight
        giftPlayView.updateBaseBottomInset(audienceWelcomeBaseBottom)
        giftPlayView.updateMessageContentHeight(messageContentHeight)
        giftPlayView.updateAudienceEntranceVisible(audienceWelcomeView.visibility == VISIBLE)
        (audienceWelcomeView.layoutParams as MarginLayoutParams).apply {
            if (bottomMargin == targetBottom) return
            bottomMargin = targetBottom
            audienceWelcomeView.layoutParams = this
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

}

/**
 * Vertical dim above the video so the header and the bottom toolbar stay readable.
 * Inflated from the living layout, which selects the edge through `android:tag`.
 */
class LivingScrimView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    private val direction = attrs?.getAttributeValue(ANDROID_NAMESPACE, "tag") ?: "top"
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        isClickable = false
        isFocusable = false
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        val edgeColor = ContextCompat.getColor(
            context,
            if (direction == "bottom") {
                R.color.livekit_pusher_living_scrim_bottom
            } else {
                R.color.livekit_pusher_living_scrim_top
            },
        )
        val colors = if (direction == "bottom") {
            intArrayOf(Color.TRANSPARENT, edgeColor)
        } else {
            intArrayOf(edgeColor, Color.TRANSPARENT)
        }
        paint.shader = LinearGradient(
            0f,
            0f,
            0f,
            height.toFloat(),
            colors,
            null,
            Shader.TileMode.CLAMP,
        )
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
    }

    private companion object {
        const val ANDROID_NAMESPACE = "http://schemas.android.com/apk/res/android"
    }
}
