package com.trtc.uikit.livekit.player.living

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.RectF
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.ImageView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.findViewTreeLifecycleOwner
import com.trtc.uikit.livekit.R
import com.trtc.uikit.livekit.common.barrage.BarrageUIStore
import com.trtc.uikit.livekit.common.barrage.BarrageInputView
import com.trtc.uikit.livekit.common.barrage.BarrageStreamView
import com.trtc.uikit.livekit.common.barrage.LiveInteractionLayout
import com.trtc.uikit.livekit.common.log.LiveKitLog
import com.trtc.uikit.livekit.common.setDebounceClickListener
import com.trtc.uikit.livekit.common.gift.send.GiftSendPanelView
import com.trtc.uikit.livekit.common.gift.GiftBarrageStore
import com.trtc.uikit.livekit.common.gift.display.GiftDisplayView
import com.trtc.uikit.livekit.common.like.LikeAnimationView
import com.trtc.uikit.livekit.player.living.audience.AudienceView
import com.trtc.uikit.livekit.common.audience.AudienceWelcomeView
import com.trtc.uikit.livekit.common.audience.AudienceWelcome
import com.trtc.uikit.livekit.player.living.coguest.CoGuestIcon
import com.trtc.uikit.livekit.player.living.coguest.CoGuestStatus
import com.trtc.uikit.livekit.player.living.hostinfo.HostInfoView
import com.trtc.uikit.livekit.common.like.LikeUIStore
import io.trtc.tuikit.atomicxcore.api.live.LiveInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.math.abs
import com.trtc.uikit.livekit.common.theme.LiveToast

class LivingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : ConstraintLayout(context, attrs, defStyleAttr) {

    var onCoGuestUiStateChanged: ((CoGuestStatus) -> Unit)? = null
    internal var videoSeatRegions: (() -> List<RectF>)? = null

    companion object {
        private const val CLEAN_ANIMATION_DURATION_MS = 320L
        private const val CLEAN_RESTORE_BUTTON_ENTER_DELAY_MS = 120L
        private const val CLEAN_RESTORE_BUTTON_ENTER_DURATION_MS = 260L
        private const val MESSAGE_LIST_NORMAL_HEIGHT_DP = 184
    }

    private val logger = LiveKitLog.getComponentLogger("OverlayView")
    private var liveInfo: LiveInfo? = null
    private var activatedLiveID = ""
    private var isJoinedChannel = false
    private var isCleanMode = false
    private var navigationBottom = 0
    private var isComposerPresented = false
    private var composerBottom = 0
    private var composerCommentHeight = dp(MESSAGE_LIST_NORMAL_HEIGHT_DP)
    private var normalCommentContentHeight = 0
    private val welcomeClipBounds = Rect()

    private val giftDisplayView: GiftDisplayView
    private val likeAnimationView: LikeAnimationView
    private val hostInfoView: HostInfoView
    private val audienceListView: AudienceView
    private val audienceWelcomeView: AudienceWelcomeView
    private val messageListView: BarrageStreamView
    private val commentEntryView: View
    private val commentComposerView: BarrageInputView
    private val likeView: ImageView
    private val giftView: ImageView
    private val coGuestView: CoGuestIcon
    private val controlsRestoreView: ImageView

    private val commentStore = BarrageUIStore(context)
    private var commentScope: CoroutineScope? = null
    private var commentToast: android.widget.Toast? = null
    private val likeUIStore: LikeUIStore
    private val giftBarrageStore = GiftBarrageStore()
    private var giftPresentationGeneration = 0L
    private var giftPanelTopOnScreen: Int? = null
    private var lifecycleOwner: LifecycleOwner? = null
    private val lifecycleObserver = LifecycleEventObserver { _, event ->
        when (event) {
            Lifecycle.Event.ON_START -> giftBarrageStore.setApplicationActive(true)
            Lifecycle.Event.ON_STOP -> {
                giftBarrageStore.setApplicationActive(false)
                GiftSendPanelView.stopCombo()
            }

            Lifecycle.Event.ON_DESTROY -> {
                giftBarrageStore.setApplicationActive(false)
                dismissGiftPresentation()
            }

            else -> Unit
        }
    }
    private val cleanableViews: List<View>
    private val cleanTranslationX: Float = resources.displayMetrics.widthPixels.toFloat()
    private val touchSlop: Int = ViewConfiguration.get(context).scaledTouchSlop
    private val swipeThreshold: Float = minOf(
        54f * resources.displayMetrics.density, resources.displayMetrics.widthPixels * 0.14f
    )

    private var swipeAxis: SwipeAxis = SwipeAxis.NONE
    private var swipeStartX = 0f
    private var swipeStartY = 0f
    private var swipeConsumed = false
    private var messageListGesture = false
    private var audienceEnteredJob: Job? = null
    private val gestureDetector = GestureDetector(
        context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapUp(e: MotionEvent): Boolean = false
        })

    init {
        LayoutInflater.from(context).inflate(R.layout.livekit_player_overlay_view, this, true)
        giftDisplayView = findViewById(R.id.livekit_overlay_gift_overlay)
        likeAnimationView = findViewById(R.id.livekit_overlay_like_animation)
        likeUIStore = LikeUIStore { count -> likeAnimationView.emit(count) }
        hostInfoView = findViewById(R.id.livekit_overlay_host_info)
        audienceListView = findViewById(R.id.livekit_overlay_audience_list)
        audienceWelcomeView = findViewById(R.id.livekit_audience_welcome)
        messageListView = findViewById(R.id.livekit_overlay_message_list)
        commentEntryView = findViewById(R.id.livekit_overlay_comment_entry)
        commentComposerView = findViewById(R.id.livekit_overlay_comment_composer)
        likeView = findViewById(R.id.livekit_overlay_like_view)
        giftView = findViewById(R.id.livekit_overlay_gift_view)
        coGuestView = findViewById(R.id.livekit_overlay_coguest_view)
        coGuestView.onUiStateChanged = { onCoGuestUiStateChanged?.invoke(it) }
        controlsRestoreView = findViewById(R.id.livekit_overlay_controls_restore)

        cleanableViews = listOf(
            giftDisplayView, likeAnimationView, hostInfoView, audienceListView, audienceWelcomeView, messageListView,
            commentEntryView, likeView, giftView, coGuestView
        )

        commentEntryView.setDebounceClickListener {
            if (isSelfMessageBanned()) {
                showCommentError(BarrageUIStore.sendErrorResource(10017))
                return@setDebounceClickListener
            }
            commentComposerView.show()
        }
        commentComposerView.onSend = ::sendComment
        commentComposerView.onPresentationChanged = { isPresented, _, messageListBottom ->
            updateCommentComposerLayout(isPresented, messageListBottom)
        }
        messageListView.onContentHeightChanged = { requestLayout() }
        audienceWelcomeView.onOccupancyChanged = { updateInteractionLayout() }
        giftDisplayView.onNoticeContentHeightChanged = { updateInteractionLayout() }
        ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
            navigationBottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            updateInteractionLayout()
            insets
        }

        giftView.setDebounceClickListener {
            if (activatedLiveID.isNotEmpty()) {
                commentComposerView.dismiss()
                dismissGiftPresentation()
                val generation = giftPresentationGeneration
                val liveID = activatedLiveID
                GiftSendPanelView.show(context, liveID, liveInfo?.liveOwner?.userID.orEmpty()) { top ->
                    if (generation == giftPresentationGeneration && activatedLiveID == liveID && isJoinedChannel && !isCleanMode) {
                        giftPanelTopOnScreen = top
                        updateInteractionLayout()
                    }
                }
            }
        }
        likeView.setOnClickListener {
            likeAnimationView.showButtonLike(PointF(likeView.x + likeView.width / 2, likeView.y))
            likeUIStore.sendLike()
        }
        likeAnimationView.enableTouchInteraction { _, _ -> likeUIStore.sendLike() }
        controlsRestoreView.setDebounceClickListener {
            setCleanMode(false)
        }

        refresh()
    }

    private fun isSelfMessageBanned(): Boolean = commentStore.isSelfMessageBanned()

    fun bind(liveInfo: LiveInfo) {
        if (this.liveInfo?.liveID != liveInfo.liveID) {
            deactivateRoomFeatures()
        }
        this.liveInfo = liveInfo
        hostInfoView.bind(liveInfo)
        audienceListView.bind(liveInfo)
        coGuestView.bind(liveInfo)
        likeAnimationView.setLikeStore(likeUIStore)
        if (isJoinedChannel) {
            activateRoomFeatures(liveInfo.liveID)
        }
        audienceEnteredJob?.cancel()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        audienceEnteredJob = scope.launch {
            audienceListView.audienceEntered.collectLatest(::showAudienceWelcome)
        }
        refresh()
    }

    fun setJoinedChannel(isJoinedChannel: Boolean) {
        if (this.isJoinedChannel == isJoinedChannel) {
            refresh()
            return
        }
        this.isJoinedChannel = isJoinedChannel
        if (isJoinedChannel) {
            liveInfo?.liveID?.let(::activateRoomFeatures)
        } else {
            deactivateRoomFeatures()
            if (isCleanMode) {
                setCleanMode(clean = false, animate = false)
            }
        }
        refresh()
    }

    fun refresh() {
        logger.info("refresh: isJoinedChannel=$isJoinedChannel, isCleanMode=$isCleanMode")

        val baseVisibility = if (isJoinedChannel) VISIBLE else GONE
        val businessVisibility = if (isJoinedChannel && !isCleanMode) VISIBLE else GONE
        giftDisplayView.visibility = businessVisibility
        giftDisplayView.setSuspended(businessVisibility != VISIBLE)
        likeAnimationView.visibility = baseVisibility
        hostInfoView.visibility = businessVisibility
        audienceListView.visibility = businessVisibility
        messageListView.visibility = businessVisibility
        commentEntryView.visibility = businessVisibility
        likeView.visibility = businessVisibility
        giftView.visibility = businessVisibility
        coGuestView.visibility = businessVisibility

        controlsRestoreView.visibility = if (isJoinedChannel && isCleanMode) VISIBLE else GONE
        updateInteractionLayout()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        ViewCompat.requestApplyInsets(this)
        lifecycleOwner = findViewTreeLifecycleOwner()
        lifecycleOwner?.lifecycle?.addObserver(lifecycleObserver)
        if (isJoinedChannel) {
            liveInfo?.liveID?.let(::activateRoomFeatures)
        }
        giftBarrageStore.setApplicationActive(
            lifecycleOwner?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.STARTED) == true
        )
    }

    override fun onDetachedFromWindow() {
        lifecycleOwner?.lifecycle?.removeObserver(lifecycleObserver)
        lifecycleOwner = null
        deactivateRoomFeatures()
        likeUIStore.release()
        super.onDetachedFromWindow()
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        updateInteractionLayout()
        // Resume normal measurement only after its width, height and crop have actually been laid out.
        if (!isComposerPresented && !messageListView.isLayoutRequested) {
            val contentHeight = messageListView.contentHeight
            if (normalCommentContentHeight != contentHeight) {
                normalCommentContentHeight = contentHeight
                updateInteractionLayout()
            }
        }
    }

    private fun activateRoomFeatures(liveID: String) {
        if (liveID.isEmpty() || activatedLiveID == liveID) return
        deactivateRoomFeatures()
        activatedLiveID = liveID
        hostInfoView.activateLikeCount(liveID)
        commentStore.activate(liveID, liveInfo?.liveOwner?.userID.orEmpty())
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        commentScope = scope
        scope.launch {
            commentStore.state.collectLatest { state ->
                if (state.liveID == activatedLiveID) {
                    renderComments(state.comments)
                }
            }
        }
        scope.launch { commentStore.sendErrors.collectLatest(::showCommentError) }
        scope.launch {
            giftBarrageStore.giftBarrages.collectLatest { gift ->
                if (activatedLiveID == liveID) commentStore.appendGiftComment(gift)
            }
        }
        giftBarrageStore.activate(liveID)
        giftBarrageStore.setCollectionEnabled(!isCleanMode)
        giftBarrageStore.setApplicationActive(
            lifecycleOwner?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.STARTED) == true
        )
        commentComposerView.bind(liveID)
        giftDisplayView.bind(liveID)
        giftDisplayView.setSuspended(isCleanMode)
        likeUIStore.setLiveID(liveID)
        renderComments(commentStore.state.value.comments)
    }

    fun deactivateCoGuest() {
        coGuestView.deactivate()
    }

    fun invalidateCoGuestCameraRequests(allowRestore: Boolean): Boolean =
        coGuestView.invalidateCameraRequests(allowRestore)

    fun startCoGuestAudioServiceIfNeeded() = coGuestView.startAudioServiceIfNeeded()

    fun deactivateRoomFeatures() {
        dismissGiftPresentation()
        isComposerPresented = false
        composerBottom = 0
        composerCommentHeight = dp(MESSAGE_LIST_NORMAL_HEIGHT_DP)
        normalCommentContentHeight = 0
        messageListView.setVisibleHeight(null)
        audienceWelcomeView.clipBounds = null
        if (activatedLiveID.isEmpty()) return
        activatedLiveID = ""
        hostInfoView.deactivateLikeCount()
        likeUIStore.release()
        commentComposerView.bind("")
        audienceWelcomeView.reset()
        giftBarrageStore.deactivate()
        commentScope?.cancel()
        commentScope = null
        commentStore.deactivate()
        commentToast?.cancel()
        commentToast = null
        messageListView.prepareForReuse()
        giftDisplayView.bind("")
        giftDisplayView.setSuspended(true)
        giftDisplayView.reset()
        updateInteractionLayout()
    }

    private fun sendComment(text: String) {
        commentStore.sendComment(text)
    }

    private fun dismissGiftPresentation() {
        giftPresentationGeneration++
        giftPanelTopOnScreen = null
        GiftSendPanelView.dismiss()
        updateInteractionLayout()
    }

    private fun showCommentError(messageRes: Int) {
        if (!isJoinedChannel || activatedLiveID.isEmpty() || activatedLiveID != liveInfo?.liveID || !isAttachedToWindow || !isShown || windowVisibility != VISIBLE) return
        commentToast?.cancel()
        commentToast = LiveToast.showScoped(context, context.getString(messageRes))
    }

    private fun renderComments(comments: List<BarrageUIStore.Comment>) {
        val announcement = BarrageStreamView.Message(
            id = "player-system-announcement",
            userName = "",
            content = context.getString(R.string.livekit_player_system_announcement),
            level = 0,
            isSystemAnnouncement = true,
        )
        messageListView.bind(listOf(announcement) + comments.map { comment ->
            BarrageStreamView.Message(
                id = comment.id,
                userName = comment.senderName,
                content = comment.text,
                level = comment.level,
                isHost = comment.isHost,
                gift = comment.gift,
            )
        })
    }

    private fun updateCommentComposerLayout(isPresented: Boolean, composerBottom: Int) {
        if (isPresented && !isComposerPresented) {
            composerCommentHeight = if (ViewCompat.isLaidOut(messageListView)) {
                messageListView.visibleHeight
            } else dp(MESSAGE_LIST_NORMAL_HEIGHT_DP)
        }
        isComposerPresented = isPresented
        this.composerBottom = composerBottom
        updateInteractionLayout()
    }

    internal fun refreshVideoGeometry() {
        updateInteractionLayout()
    }

    private fun interactionTopBoundary(): Int? {
        if (!isJoinedChannel || width <= 0 || height <= 0) return null
        val bounds = RectF(0f, 0f, width.toFloat(), height.toFloat())
        val seatBottom = videoSeatRegions?.invoke()?.filter { !it.isEmpty && RectF.intersects(it, bounds) }
            ?.maxOfOrNull { it.bottom } ?: return null
        val top = (seatBottom.toInt() + dp(8)).coerceIn(0, height)
        // Notifications, the input method and the gift sheet must not change this normal-layout gate.
        val normalBottom = commentEntryView.top - dp(14)
        return top.takeIf { normalBottom - it >= dp(MESSAGE_LIST_NORMAL_HEIGHT_DP) }
    }

    private fun updateInteractionLayout() {
        if (width <= 0 || height <= 0 || commentEntryView.height <= 0) return
        val top = interactionTopBoundary()
        var normalBottom = commentEntryView.top - dp(14)
        giftPanelTopOnScreen?.let { panelTop ->
            val origin = IntArray(2)
            getLocationOnScreen(origin)
            normalBottom = minOf(normalBottom, panelTop - origin[1] - dp(4))
        }
        val layout = LiveInteractionLayout(
            topBoundary = top,
            bottom = normalBottom,
            defaultCommentHeight = dp(MESSAGE_LIST_NORMAL_HEIGHT_DP),
            contentHeight = normalCommentContentHeight,
            isWelcomeVisible = audienceWelcomeView.isOccupied,
            giftNoticeHeight = giftDisplayView.noticeContentHeight,
            safeBottom = height - navigationBottom,
            density = resources.displayMetrics.density,
        )
        val params = messageListView.layoutParams as LayoutParams
        val bottomMargin = if (isComposerPresented) composerBottom else height - normalBottom
        val commentHeight = if (isComposerPresented) composerCommentHeight else layout.commentHeight
        val anchorChanged = params.bottomToTop != LayoutParams.UNSET || params.bottomToBottom != LayoutParams.PARENT_ID
        if (params.height != commentHeight || params.bottomMargin != bottomMargin || anchorChanged) {
            params.height = commentHeight
            params.bottomMargin = bottomMargin
            params.bottomToTop = LayoutParams.UNSET
            params.bottomToBottom = LayoutParams.PARENT_ID
            messageListView.layoutParams = params
        }
        messageListView.setVisibleHeight(if (isComposerPresented) null else layout.visibleCommentHeight)
        val welcomeParams = audienceWelcomeView.layoutParams as LayoutParams
        val welcomeBottomMargin = height - normalBottom - layout.welcomeBottomOffset
        if (welcomeParams.bottomMargin != welcomeBottomMargin) {
            welcomeParams.bottomMargin = welcomeBottomMargin
            audienceWelcomeView.layoutParams = welcomeParams
        }
        if (top == null) {
            if (audienceWelcomeView.clipBounds != null) audienceWelcomeView.clipBounds = null
        } else {
            val welcomeTop = height - welcomeBottomMargin - welcomeParams.height
            welcomeClipBounds.set(0, (top - welcomeTop).coerceIn(0, welcomeParams.height), width, welcomeParams.height)
            if (audienceWelcomeView.clipBounds != welcomeClipBounds) audienceWelcomeView.clipBounds = welcomeClipBounds
        }
        giftDisplayView.updateNoticeLayout(layout.giftNoticeBottom, top)
    }

    internal fun showAudienceWelcome(welcome: AudienceWelcome) {
        if (!isJoinedChannel || isCleanMode || visibility != VISIBLE) return
        audienceWelcomeView.enqueue(welcome)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val comments = messageListView
                val visibleTop = comments.y + comments.height - comments.visibleHeight
                val insideX = ev.x >= comments.x && ev.x <= comments.x + comments.width
                val insideY = ev.y >= visibleTop && ev.y <= comments.y + comments.height
                messageListGesture = comments.visibility == VISIBLE && insideX && insideY
                if (messageListGesture) parent?.requestDisallowInterceptTouchEvent(true)
            }

            MotionEvent.ACTION_MOVE -> if (messageListGesture) {
                val dx = abs(ev.rawX - swipeStartX)
                val dy = abs(ev.rawY - swipeStartY)
                if (maxOf(dx, dy) >= touchSlop) {
                    when {
                        dx > dy * 1.15f -> parent?.requestDisallowInterceptTouchEvent(true)
                        dy > dx * 1.15f -> parent?.requestDisallowInterceptTouchEvent(
                            messageListView.canScrollMessages(if (ev.rawY > swipeStartY) -1 else 1)
                        )
                    }
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (messageListGesture) parent?.requestDisallowInterceptTouchEvent(false)
                messageListGesture = false
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (!isJoinedChannel) return super.onInterceptTouchEvent(ev)
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                swipeAxis = SwipeAxis.NONE
                swipeStartX = ev.rawX
                swipeStartY = ev.rawY
                swipeConsumed = false
            }

            MotionEvent.ACTION_MOVE -> {
                if (swipeAxis == SwipeAxis.NONE) {
                    val dx = abs(ev.rawX - swipeStartX)
                    val dy = abs(ev.rawY - swipeStartY)
                    if (maxOf(dx, dy) < touchSlop) return false
                    swipeAxis = when {
                        dx > dy * 1.15f -> SwipeAxis.HORIZONTAL
                        dy > dx * 1.15f -> SwipeAxis.VERTICAL
                        else -> SwipeAxis.NONE
                    }
                }
                if (swipeAxis == SwipeAxis.HORIZONTAL) {
                    parent?.requestDisallowInterceptTouchEvent(true)
                    return true
                }
            }
        }
        return super.onInterceptTouchEvent(ev)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isJoinedChannel) return super.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                swipeStartX = event.rawX
                swipeStartY = event.rawY
                swipeAxis = SwipeAxis.NONE
                swipeConsumed = false
                return if (isCleanMode) true else super.onTouchEvent(event)
            }

            MotionEvent.ACTION_MOVE -> {
                if (swipeConsumed) return true
                val dx = event.rawX - swipeStartX
                val dy = event.rawY - swipeStartY
                if (swipeAxis == SwipeAxis.NONE) {
                    val absDx = abs(dx)
                    val absDy = abs(dy)
                    if (maxOf(absDx, absDy) < touchSlop) return super.onTouchEvent(event)
                    swipeAxis = when {
                        absDx > absDy * 1.15f -> SwipeAxis.HORIZONTAL
                        absDy > absDx * 1.15f -> SwipeAxis.VERTICAL
                        else -> return super.onTouchEvent(event)
                    }
                }
                if (swipeAxis == SwipeAxis.HORIZONTAL) {
                    parent?.requestDisallowInterceptTouchEvent(true)
                    if (dx >= swipeThreshold && !isCleanMode) {
                        setCleanMode(true)
                        swipeConsumed = true
                    } else if (dx <= -swipeThreshold && isCleanMode) {
                        setCleanMode(false)
                        swipeConsumed = true
                    }
                    return true
                }
                return super.onTouchEvent(event)
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val consumed = swipeConsumed
                swipeAxis = SwipeAxis.NONE
                swipeConsumed = false
                return if (consumed) true else super.onTouchEvent(event)
            }
        }
        return super.onTouchEvent(event)
    }

    private fun setCleanMode(clean: Boolean, animate: Boolean = true) {
        if (isCleanMode == clean) return
        isCleanMode = clean
        giftDisplayView.setSuspended(clean || !isJoinedChannel)
        giftBarrageStore.setCollectionEnabled(!clean && isJoinedChannel)
        if (clean) {
            commentComposerView.dismiss()
            giftPresentationGeneration++
            giftPanelTopOnScreen = null
        }
        updateInteractionLayout()

        val targetTranslation = if (clean) cleanTranslationX else 0f
        val targetAlpha = if (clean) 0f else 1f

        cleanableViews.forEach { view ->
            view.animate().cancel()
            if (!animate) {
                view.translationX = targetTranslation
                view.alpha = targetAlpha
                view.visibility = if (clean) GONE else if (isJoinedChannel) VISIBLE else GONE
                return@forEach
            }
            if (clean) {
                view.animate().translationX(targetTranslation).alpha(targetAlpha)
                    .setDuration(CLEAN_ANIMATION_DURATION_MS).withEndAction { view.visibility = GONE }.start()
            } else {
                view.translationX = cleanTranslationX
                view.alpha = 0f
                view.visibility = VISIBLE
                view.animate().translationX(0f).alpha(1f).setDuration(CLEAN_ANIMATION_DURATION_MS).start()
            }
        }

        controlsRestoreView.animate().cancel()
        if (clean) {
            controlsRestoreView.alpha = 0f
            controlsRestoreView.translationX = cleanTranslationX * 0.05f
            controlsRestoreView.visibility = VISIBLE
            if (animate) {
                controlsRestoreView.animate().alpha(1f).translationX(0f)
                    .setStartDelay(CLEAN_RESTORE_BUTTON_ENTER_DELAY_MS)
                    .setDuration(CLEAN_RESTORE_BUTTON_ENTER_DURATION_MS).start()
            } else {
                controlsRestoreView.alpha = 1f
                controlsRestoreView.translationX = 0f
            }
        } else {
            controlsRestoreView.visibility = GONE
            controlsRestoreView.alpha = 1f
            controlsRestoreView.translationX = 0f
        }
    }

    private enum class SwipeAxis {
        NONE, HORIZONTAL, VERTICAL
    }
}
