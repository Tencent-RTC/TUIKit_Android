package com.trtc.uikit.livekit.common.gift.display

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Rect
import android.view.View
import android.util.AttributeSet
import androidx.constraintlayout.widget.ConstraintLayout
import com.trtc.uikit.livekit.common.LiveKitResources
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.findViewTreeLifecycleOwner
import io.trtc.tuikit.atomicxcore.api.gift.Gift
import io.trtc.tuikit.atomicxcore.api.gift.GiftListener
import io.trtc.tuikit.atomicxcore.api.gift.GiftStore
import io.trtc.tuikit.atomicxcore.api.live.LiveUserInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Owns the whole "gift received -> gift rendered" loop: it listens to [GiftStore] itself and
 * renders the result, so hosting views only inject the live ID instead of forwarding gift events.
 */
internal class GiftDisplayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : ConstraintLayout(context, attrs, defStyleAttr) {

    var onNoticeContentHeightChanged: ((Int) -> Unit)? = null
    val noticeContentHeight: Int get() = noticeView.contentHeight

    private var ready = false
    private val density = resources.displayMetrics.density
    private val noticeViewport = ConstraintLayout(context)
    private val noticeClipBounds = Rect()
    private val noticeView = GiftNoticeView(context)
    private val animationView = GiftAnimationView(context)
    private var noticeBottom: Int? = null
    private var noticeTopBoundary: Int? = null
    private var liveID = ""
    private var activatedLiveID: String? = null
    private var isSuspended = false
    private var isActive = true
    private var sdkStore: GiftStore? = null
    private var listener: GiftListener? = null
    private var scope: CoroutineScope? = null
    private var generation = 0L
    private var lifecycleOwner: LifecycleOwner? = null
    private var hostStarted = true
    private val lifecycleObserver = LifecycleEventObserver { _, event ->
        when (event) {
            Lifecycle.Event.ON_START -> hostStarted = true
            Lifecycle.Event.ON_STOP, Lifecycle.Event.ON_DESTROY -> hostStarted = false
            else -> return@LifecycleEventObserver
        }
        updateSubscription()
    }

    private val canPresent: Boolean
        get() = isActive && !isSuspended && liveID.isNotEmpty() && isAttachedToWindow && isShown && windowVisibility == VISIBLE && hostStarted

    init {
        isClickable = false
        isFocusable = false
        clipChildren = false
        clipToPadding = false
        noticeViewport.isClickable = false
        noticeViewport.isFocusable = false
        noticeViewport.clipChildren = false
        noticeViewport.clipToPadding = false
        addView(noticeViewport, GiftDisplayLayoutParams.effect())
        noticeViewport.addView(
            noticeView,
            GiftDisplayLayoutParams.entry(
                dp(HORIZONTAL_INSET_DP),
                dp(CHANNEL_HEIGHT_DP * 2 + CHANNEL_SPACING_DP),
                dp(DEFAULT_BASE_BOTTOM_INSET_DP),
            ),
        )
        noticeView.onContentHeightChanged = { onNoticeContentHeightChanged?.invoke(it) }
        // Draw effects above overlapping capsules, including newly added entries.
        addView(animationView, GiftDisplayLayoutParams.effect())
        ready = true
    }

    fun bind(liveID: String) {
        if (this.liveID == liveID) return
        this.liveID = liveID
        deactivate()
        updateSubscription()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        lifecycleOwner = findViewTreeLifecycleOwner() ?: findLifecycleOwner(context)
        hostStarted = lifecycleOwner?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.STARTED) ?: true
        lifecycleOwner?.lifecycle?.addObserver(lifecycleObserver)
        updateSubscription()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (ready) updateSubscription()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (ready) updateSubscription()
    }

    override fun onDetachedFromWindow() {
        lifecycleOwner?.lifecycle?.removeObserver(lifecycleObserver)
        lifecycleOwner = null
        deactivate()
        super.onDetachedFromWindow()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        if (ready && MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.EXACTLY) {
            updateNoticeBottomMargin(MeasureSpec.getSize(heightMeasureSpec))
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        if (ready) updateNoticeBottomMargin()
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        updateNoticeClip()
    }

    /** Final local pixel coordinates. Only capsules are clipped; the effect keeps its full canvas. */
    fun updateNoticeLayout(bottom: Int, topBoundary: Int?) {
        noticeBottom = bottom
        noticeTopBoundary = topBoundary
        updateNoticeBottomMargin()
        updateNoticeClip()
    }

    private fun updateNoticeClip() {
        val top = noticeTopBoundary
        if (top == null) {
            if (noticeViewport.clipBounds != null) noticeViewport.clipBounds = null
            return
        }
        noticeClipBounds.set(0, top.coerceIn(0, noticeViewport.height), noticeViewport.width, noticeViewport.height)
        if (noticeViewport.clipBounds != noticeClipBounds) noticeViewport.clipBounds = noticeClipBounds
    }

    fun setActive(active: Boolean) {
        isActive = active
        updateSubscription()
    }

    fun setSuspended(suspended: Boolean) {
        if (isSuspended == suspended) return
        isSuspended = suspended
        updateSubscription()
    }

    private fun show(gift: GiftDisplayItem) {
        noticeView.enqueue(gift)
        if (gift.isAdvanced) animationView.enqueue(gift.resourceURL)
    }

    fun updatePreviewBottomInset(inset: Int) {
        animationView.updatePreviewBottomInset(inset)
    }

    fun reset() {
        deactivate()
        updateSubscription()
    }

    private fun updateSubscription() {
        if (!canPresent) {
            deactivate()
            return
        }
        if (activatedLiveID == liveID) return
        deactivate()
        val activeScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        scope = activeScope
        activatedLiveID = liveID
        val activation = generation
        val store = GiftStore.create(liveID)
        sdkStore = store
        store.setLanguage(LiveKitResources.giftLanguageCode)
        val listener = object : GiftListener() {
            override fun onReceiveGift(liveID: String, gift: Gift, count: Int, sender: LiveUserInfo) {
                if (count <= 0) return
                // Snapshot SDK values before handing the callback to the UI thread.
                val item = GiftDisplayItem(
                    giftID = gift.giftID,
                    name = gift.name,
                    iconURL = gift.iconURL,
                    resourceURL = gift.resourceURL,
                    count = count,
                    senderID = sender.userID,
                    senderName = sender.userName,
                    senderAvatarURL = sender.avatarURL,
                )
                activeScope.launch {
                    if (activation == generation && liveID == activatedLiveID && canPresent) show(item)
                }
            }
        }
        this.listener = listener
        store.addGiftListener(listener)
    }

    private fun deactivate() {
        generation++
        scope?.cancel()
        scope = null
        listener?.let { sdkStore?.removeGiftListener(it) }
        listener = null
        sdkStore = null
        activatedLiveID = null
        noticeView.reset()
        animationView.reset()
    }

    private fun findLifecycleOwner(context: Context): LifecycleOwner? {
        var current = context
        while (current is ContextWrapper) {
            if (current is LifecycleOwner) return current
            current = current.baseContext
        }
        return current as? LifecycleOwner
    }

    private fun updateNoticeBottomMargin(containerHeight: Int = height) {
        val bottom = noticeBottom ?: return
        if (containerHeight <= 0) return
        val params = noticeView.layoutParams as LayoutParams
        val bottomMargin = containerHeight - bottom
        if (params.bottomMargin == bottomMargin) return
        params.bottomMargin = bottomMargin
        noticeView.layoutParams = params
    }

    private fun dp(value: Float): Int = (value * density).toInt()

    private object GiftDisplayLayoutParams {
        fun effect(): LayoutParams = LayoutParams(0, 0).apply {
            startToStart = LayoutParams.PARENT_ID
            endToEnd = LayoutParams.PARENT_ID
            topToTop = LayoutParams.PARENT_ID
            bottomToBottom = LayoutParams.PARENT_ID
        }

        fun entry(horizontalInset: Int, height: Int, bottomOffset: Int): LayoutParams = LayoutParams(0, height).apply {
            startToStart = LayoutParams.PARENT_ID
            endToEnd = LayoutParams.PARENT_ID
            bottomToBottom = LayoutParams.PARENT_ID
            marginStart = horizontalInset
            marginEnd = horizontalInset
            bottomMargin = bottomOffset
        }
    }

    private companion object {
        const val HORIZONTAL_INSET_DP = 12f
        const val CHANNEL_HEIGHT_DP = 44f
        const val CHANNEL_SPACING_DP = 4f

        /** Fallback baseline used before the host reports final local coordinates. */
        const val DEFAULT_BASE_BOTTOM_INSET_DP = 130f
    }
}

internal data class GiftDisplayItem(
    val giftID: String,
    val name: String,
    val iconURL: String,
    val resourceURL: String,
    val count: Int,
    val senderID: String,
    val senderName: String,
    val senderAvatarURL: String,
) {
    val comboKey: Pair<String, String> get() = senderID to giftID
    val isAdvanced: Boolean get() = resourceURL.isNotEmpty()
}
