package com.trtc.uikit.livekit.player.living

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PointF
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ImageView
import androidx.constraintlayout.widget.ConstraintLayout
import com.trtc.uikit.livekit.R
import com.trtc.uikit.livekit.common.barrage.BarrageUIStore
import com.trtc.uikit.livekit.common.barrage.BarrageInputView
import com.trtc.uikit.livekit.common.barrage.BarrageStreamView
import com.trtc.uikit.livekit.common.basic.LiveKitLogger
import com.trtc.uikit.livekit.common.basic.setDebounceClickListener
import com.trtc.uikit.livekit.common.gift.GiftListView
import com.trtc.uikit.livekit.common.gift.player.LiveGiftComment
import com.trtc.uikit.livekit.common.gift.player.LiveGiftEvent
import com.trtc.uikit.livekit.common.gift.GiftPlayView
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
import androidx.core.view.isVisible
import com.trtc.uikit.livekit.common.theme.toast.LiveToast

class LivingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : ConstraintLayout(context, attrs, defStyleAttr) {

    var onCoGuestUiStateChanged: ((CoGuestStatus) -> Unit)? = null

    companion object {
        private const val CLEAN_ANIMATION_DURATION_MS = 320L
        private const val CLEAN_RESTORE_BUTTON_ENTER_DELAY_MS = 120L
        private const val CLEAN_RESTORE_BUTTON_ENTER_DURATION_MS = 260L
        private const val GIFT_COMMENT_COMBO_INTERVAL_MS = 2_000L
        private const val MESSAGE_LIST_NORMAL_HEIGHT_DP = 152
        private const val MESSAGE_LIST_COMPOSER_HEIGHT_DP = 132
        private const val MESSAGE_LIST_NORMAL_BOTTOM_DP = 88
        private const val GIFT_OVERLAY_BASE_BOTTOM_DP = 92
    }

    private val logger = LiveKitLogger.getComponentLogger("OverlayView")
    private var liveInfo: LiveInfo? = null
    private var activatedLiveID = ""
    private var isJoinedChannel = false
    private var isCleanMode = false
    private val audienceWelcomeBaseBottom: Int

    private val giftPlayView: GiftPlayView
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
    private val giftCommentHandler = Handler(Looper.getMainLooper())
    private val pendingGiftComments = mutableMapOf<String, PendingGiftComment>()
    private var messageHeightAnimator: ValueAnimator? = null
    private val cleanableViews: List<View>
    private val cleanTranslationX: Float =
        resources.displayMetrics.widthPixels.toFloat()
    private val touchSlop: Int = ViewConfiguration.get(context).scaledTouchSlop
    private val swipeThreshold: Float =
        minOf(
            54f * resources.displayMetrics.density,
            resources.displayMetrics.widthPixels * 0.14f
        )

    private var swipeAxis: SwipeAxis = SwipeAxis.NONE
    private var swipeStartX = 0f
    private var swipeStartY = 0f
    private var swipeConsumed = false
    private var messageListGesture = false
    private var audienceEnteredJob: Job? = null
    private val gestureDetector = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapUp(e: MotionEvent): Boolean = false
        }
    )

    init {
        LayoutInflater.from(context).inflate(R.layout.livekit_player_overlay_view, this, true)
        giftPlayView = findViewById(R.id.livekit_overlay_gift_overlay)
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
            giftPlayView,
            likeAnimationView,
            hostInfoView,
            audienceListView,
            messageListView,
            commentEntryView,
            likeView,
            giftView,
            coGuestView
        )

        commentEntryView.setDebounceClickListener {
            if (isSelfMessageBanned()) {
                showCommentError(BarrageUIStore.sendErrorResource(10017))
                return@setDebounceClickListener
            }
            commentComposerView.show()
        }
        commentComposerView.onSend = ::sendComment
        commentComposerView.onPresentationChanged = { isPresented, animated, messageListBottom ->
            updateMessageListLayout(isPresented, messageListBottom, animated)
        }
        messageListView.onVisibleContentHeightChanged = { height ->
            giftPlayView.updateMessageContentHeight(height)
        }
        audienceWelcomeBaseBottom = audienceWelcomeView.layoutParams.let {
            (it as MarginLayoutParams).bottomMargin
        }
        giftPlayView.onGiftReceived = ::receiveGiftComment
        giftPlayView.updateBaseBottomInset(dp(GIFT_OVERLAY_BASE_BOTTOM_DP))

        giftView.setDebounceClickListener {
            liveInfo?.let { GiftListView.show(context, it) }
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
        giftPlayView.visibility = businessVisibility
        giftPlayView.setSuspended(businessVisibility != VISIBLE)
        likeAnimationView.visibility = baseVisibility
        hostInfoView.visibility = businessVisibility
        audienceListView.visibility = businessVisibility
        messageListView.visibility = businessVisibility
        commentEntryView.visibility = businessVisibility
        likeView.visibility = businessVisibility
        giftView.visibility = businessVisibility
        coGuestView.visibility = businessVisibility

        controlsRestoreView.visibility =
            if (isJoinedChannel && isCleanMode) VISIBLE else GONE
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (isJoinedChannel) {
            liveInfo?.liveID?.let(::activateRoomFeatures)
        }
    }

    override fun onDetachedFromWindow() {
        deactivateRoomFeatures()
        messageHeightAnimator?.cancel()
        messageHeightAnimator = null
        likeUIStore.release()
        super.onDetachedFromWindow()
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        updateAudienceWelcomeLayout()
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
                    messageListView.bind(state.comments.map { comment ->
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
            }
        }
        scope.launch { commentStore.sendErrors.collectLatest(::showCommentError) }
        commentComposerView.bind(liveID)
        giftPlayView.bind(liveID)
        giftPlayView.setSuspended(isCleanMode)
        likeUIStore.setLiveID(liveID)
    }

    fun deactivateCoGuest() {
        coGuestView.deactivate()
    }

    fun invalidateCoGuestCameraRequests(allowRestore: Boolean): Boolean =
        coGuestView.invalidateCameraRequests(allowRestore)

    fun startCoGuestAudioServiceIfNeeded() = coGuestView.startAudioServiceIfNeeded()

    fun deactivateRoomFeatures() {
        if (activatedLiveID.isEmpty()) return
        activatedLiveID = ""
        hostInfoView.deactivateLikeCount()
        likeUIStore.release()
        commentComposerView.bind("")
        audienceWelcomeView.reset()
        commentScope?.cancel()
        commentScope = null
        commentStore.deactivate()
        commentToast?.cancel()
        commentToast = null
        messageListView.bind(emptyList())
        giftPlayView.bind("")
        giftPlayView.setSuspended(true)
        giftPlayView.reset()
        clearPendingGiftComments()
        updateMessageListLayout(
            isPresented = false,
            composerBottom = MESSAGE_LIST_NORMAL_BOTTOM_DP,
            animated = false,
        )
    }

    private fun sendComment(text: String) {
        commentStore.sendComment(text)
    }

    private fun showCommentError(messageRes: Int) {
        if (!isJoinedChannel || activatedLiveID.isEmpty() ||
            activatedLiveID != liveInfo?.liveID || !isAttachedToWindow || !isShown ||
            windowVisibility != VISIBLE) return
        commentToast?.cancel()
        commentToast = LiveToast.showScoped(context, context.getString(messageRes))
    }

    private fun receiveGiftComment(event: LiveGiftEvent) {
        if (activatedLiveID.isEmpty() || isCleanMode || event.count <= 0) return
        val key = event.comboKey
        val pending = pendingGiftComments[key] ?: PendingGiftComment(event).also {
            pendingGiftComments[key] = it
        }
        pending.event = event
        pending.count = (pending.count.toLong() + event.count.toLong())
            .coerceAtMost(Int.MAX_VALUE.toLong())
            .toInt()
        pending.task?.let(giftCommentHandler::removeCallbacks)
        val task = Runnable { flushGiftComment(key, pending) }
        pending.task = task
        giftCommentHandler.postDelayed(task, GIFT_COMMENT_COMBO_INTERVAL_MS)
    }

    private fun flushGiftComment(key: String, pending: PendingGiftComment) {
        if (pendingGiftComments[key] !== pending || activatedLiveID.isEmpty() || isCleanMode) return
        pendingGiftComments.remove(key)
        val event = pending.event
        commentStore.appendGiftComment(
            LiveGiftComment(
                giftID = event.giftID,
                giftName = event.name,
                giftIconURL = event.iconURL,
                count = pending.count,
                senderID = event.senderID,
                senderName = event.senderName,
                senderLevel = event.senderLevel,
            )
        )
    }

    private fun clearPendingGiftComments() {
        pendingGiftComments.values.forEach { pending ->
            pending.task?.let(giftCommentHandler::removeCallbacks)
        }
        pendingGiftComments.clear()
    }

    private fun updateMessageListLayout(
        isPresented: Boolean,
        composerBottom: Int,
        animated: Boolean,
    ) {
        val targetHeight = dp(
            if (isPresented) MESSAGE_LIST_COMPOSER_HEIGHT_DP else MESSAGE_LIST_NORMAL_HEIGHT_DP
        )
        val targetBottom = if (isPresented) composerBottom else dp(MESSAGE_LIST_NORMAL_BOTTOM_DP)
        setMessageListBottom(targetBottom)
        messageHeightAnimator?.cancel()
        messageHeightAnimator = null
        if (!animated || messageListView.layoutParams.height == targetHeight) {
            setMessageListHeight(targetHeight)
            return
        }
        messageHeightAnimator = ValueAnimator.ofInt(messageListView.layoutParams.height, targetHeight).apply {
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

    internal fun showAudienceWelcome(welcome: AudienceWelcome) {
        if (visibility != VISIBLE) return
        audienceWelcomeView.enqueue(welcome)
    }

    private fun updateAudienceWelcomeLayout() {
        val messageContentHeight = messageListView.visibleContentHeight()
        val targetBottom = audienceWelcomeBaseBottom + messageContentHeight
        giftPlayView.updateBaseBottomInset(audienceWelcomeBaseBottom)
        giftPlayView.updateMessageContentHeight(messageContentHeight)
        giftPlayView.updateAudienceEntranceVisible(audienceWelcomeView.isVisible)
        (audienceWelcomeView.layoutParams as MarginLayoutParams).apply {
            if (bottomMargin == targetBottom) return
            bottomMargin = targetBottom
            audienceWelcomeView.layoutParams = this
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                messageListGesture = messageListView.visibility == VISIBLE &&
                    ev.x >= messageListView.x && ev.x <= messageListView.x + messageListView.width &&
                    ev.y >= messageListView.y && ev.y <= messageListView.y + messageListView.height
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

            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {
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
        giftPlayView.setSuspended(clean || !isJoinedChannel)
        if (clean) {
            commentComposerView.dismiss()
            clearPendingGiftComments()
        }

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
                view.animate()
                    .translationX(targetTranslation)
                    .alpha(targetAlpha)
                    .setDuration(CLEAN_ANIMATION_DURATION_MS)
                    .withEndAction { view.visibility = GONE }
                    .start()
            } else {
                view.translationX = cleanTranslationX
                view.alpha = 0f
                view.visibility = VISIBLE
                view.animate()
                    .translationX(0f)
                    .alpha(1f)
                    .setDuration(CLEAN_ANIMATION_DURATION_MS)
                    .start()
            }
        }

        controlsRestoreView.animate().cancel()
        if (clean) {
            controlsRestoreView.alpha = 0f
            controlsRestoreView.translationX = cleanTranslationX * 0.05f
            controlsRestoreView.visibility = VISIBLE
            if (animate) {
                controlsRestoreView.animate()
                    .alpha(1f)
                    .translationX(0f)
                    .setStartDelay(CLEAN_RESTORE_BUTTON_ENTER_DELAY_MS)
                    .setDuration(CLEAN_RESTORE_BUTTON_ENTER_DURATION_MS)
                    .start()
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

    private data class PendingGiftComment(
        var event: LiveGiftEvent,
        var count: Int = 0,
        var task: Runnable? = null,
    )

    private enum class SwipeAxis {
        NONE,
        HORIZONTAL,
        VERTICAL
    }
}
