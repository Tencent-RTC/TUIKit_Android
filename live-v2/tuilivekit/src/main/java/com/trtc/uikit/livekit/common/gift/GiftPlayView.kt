package com.trtc.uikit.livekit.common.gift

import android.content.Context
import android.util.AttributeSet
import androidx.constraintlayout.widget.ConstraintLayout
import com.trtc.uikit.livekit.common.gift.player.GiftLanguage
import com.trtc.uikit.livekit.common.gift.player.LiveGiftEffectView
import com.trtc.uikit.livekit.common.gift.player.LiveGiftEntryView
import com.trtc.uikit.livekit.common.gift.player.LiveGiftEvent
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
internal class GiftPlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : ConstraintLayout(context, attrs, defStyleAttr) {

    /**
     * Observation hook for received real gifts. Rendering never depends on it; it only exists so
     * hosts can mirror real gifts into diagnostics.
     */
    var onGiftReceived: ((LiveGiftEvent) -> Unit)? = null

    private val density = resources.displayMetrics.density
    private val entryView = LiveGiftEntryView(context)
    private val effectView = LiveGiftEffectView(context)
    private var baseBottomInset = dp(DEFAULT_BASE_BOTTOM_INSET_DP)
    private var messageContentHeight = 0
    private var isAudienceEntranceVisible = false
    private var liveID = ""
    private var activatedLiveID: String? = null
    private var isSuspended = false
    private var isActive = true
    private var sdkStore: GiftStore? = null
    private var listener: GiftListener? = null
    private var scope: CoroutineScope? = null

    init {
        isClickable = false
        isFocusable = false
        clipChildren = false
        clipToPadding = false
        addView(
            entryView,
            GiftOverlayLayoutParams.entry(
                dp(HORIZONTAL_INSET_DP),
                dp(CHANNEL_HEIGHT_DP * 2 + CHANNEL_SPACING_DP),
                baseBottomInset,
            ),
        )
        // Draw effects above overlapping capsules, including newly added entries.
        addView(effectView, GiftOverlayLayoutParams.effect())
    }

    /** Injects the broadcast this overlay renders. Subscribing follows the attach state. */
    fun bind(liveID: String) {
        if (this.liveID == liveID) return
        this.liveID = liveID
        deactivate()
        if (isAttachedToWindow) activate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        activate()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility != VISIBLE) {
            reset()
        }
    }

    override fun onDetachedFromWindow() {
        deactivate()
        super.onDetachedFromWindow()
    }

    fun setActive(active: Boolean) {
        isActive = active
        if (active && isAttachedToWindow) {
            activate()
        } else {
            deactivate()
        }
    }

    /**
     * Suppresses rendering while the live is paused. Late events arriving in this window are
     * dropped instead of being replayed afterwards.
     */
    fun setSuspended(suspended: Boolean) {
        isSuspended = suspended
    }

    fun show(gift: LiveGiftEvent) {
        if (!isActive || isSuspended) return
        entryView.enqueue(gift)
        if (gift.isAdvanced) {
            effectView.enqueue(gift.resourceURL)
        }
    }

    /**
     * Sets the capsule baseline, measured from the container bottom.
     *
     * iOS anchors the capsule to `safeArea.bottom - 86`, which equals the audience entrance
     * baseline. Android margins are measured from the container bottom instead, so the host passes
     * its own entrance baseline here rather than reusing the iOS literal.
     */
    fun updateBaseBottomInset(inset: Int) {
        val safeInset = inset.coerceAtLeast(0)
        if (baseBottomInset == safeInset) return
        baseBottomInset = safeInset
        updateEntryBottomMargin()
    }

    fun updateMessageContentHeight(height: Int) {
        messageContentHeight = height.coerceAtLeast(0)
        updateEntryBottomMargin()
    }

    fun updateAudienceEntranceVisible(isVisible: Boolean) {
        if (isAudienceEntranceVisible == isVisible) return
        isAudienceEntranceVisible = isVisible
        updateEntryBottomMargin()
    }

    fun updatePreviewBottomInset(inset: Int) {
        effectView.updatePreviewBottomInset(inset)
    }

    fun preloadEffects(resourceURLs: List<String>) {
        effectView.preload(resourceURLs)
    }

    fun reset() {
        entryView.reset()
        effectView.reset()
    }

    private fun activate() {
        if (!isActive || liveID.isEmpty() || activatedLiveID == liveID) return
        deactivate()
        val activeScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        scope = activeScope
        activatedLiveID = liveID
        val sdkStore = GiftStore.create(liveID)
        this.sdkStore = sdkStore
        sdkStore.setLanguage(GiftLanguage.currentCode())
        val listener = object : GiftListener() {
            override fun onReceiveGift(
                liveID: String,
                gift: Gift,
                count: Int,
                sender: LiveUserInfo,
            ) {
                activeScope.launch { receive(liveID, gift, count, sender) }
            }
        }
        this.listener = listener
        sdkStore.addGiftListener(listener)
    }

    private fun deactivate() {
        scope?.cancel()
        scope = null
        listener?.let { sdkStore?.removeGiftListener(it) }
        listener = null
        sdkStore = null
        activatedLiveID = null
        reset()
    }

    private fun receive(eventLiveID: String, gift: Gift, count: Int, sender: LiveUserInfo) {
        if (eventLiveID != activatedLiveID || count <= 0) return
        val receivedGift = LiveGiftEvent(
            giftID = gift.giftID,
            name = gift.name,
            iconURL = gift.iconURL,
            resourceURL = gift.resourceURL,
            coins = gift.coins,
            count = count,
            senderID = sender.userID,
            senderName = sender.userName,
            senderAvatarURL = sender.avatarURL,
            senderLevel = sender.level
        )
        onGiftReceived?.invoke(receivedGift)
        show(receivedGift)
    }

    private fun updateEntryBottomMargin() {
        val entranceOffset = if (isAudienceEntranceVisible) dp(ENTRANCE_RESERVED_HEIGHT_DP) else 0
        val params = entryView.layoutParams as LayoutParams
        val bottomMargin = baseBottomInset + messageContentHeight + entranceOffset
        if (params.bottomMargin == bottomMargin) return
        params.bottomMargin = bottomMargin
        entryView.layoutParams = params
    }

    private fun dp(value: Float): Int = (value * density).toInt()

    private object GiftOverlayLayoutParams {
        fun effect(): LayoutParams = LayoutParams(0, 0).apply {
            startToStart = LayoutParams.PARENT_ID
            endToEnd = LayoutParams.PARENT_ID
            topToTop = LayoutParams.PARENT_ID
            bottomToBottom = LayoutParams.PARENT_ID
        }

        fun entry(horizontalInset: Int, height: Int, bottomOffset: Int): LayoutParams =
            LayoutParams(0, height).apply {
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
        const val ENTRANCE_RESERVED_HEIGHT_DP = 32f

        /** Fallback baseline used before the host reports its audience entrance baseline. */
        const val DEFAULT_BASE_BOTTOM_INSET_DP = 130f
    }
}
