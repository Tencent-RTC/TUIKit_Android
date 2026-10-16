package com.trtc.uikit.livekit.common.gift.player

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.util.AttributeSet
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.PathInterpolator
import android.widget.LinearLayout
import androidx.constraintlayout.widget.ConstraintLayout
import java.util.UUID

/**
 * Renders normal gifts in at most two independent channels. The merge key is
 * always senderID + giftID.
 */
internal class LiveGiftEntryView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : ConstraintLayout(context, attrs, defStyleAttr) {

    private class Entry(var gift: LiveGiftEvent, var count: Int, val view: LiveGiftEntryItemView) {
        val key: String = gift.comboKey
        var expiration: Runnable? = null
        var animator: Animator? = null
        var isLeaving = false
    }

    private class PendingEntry(val key: String, var gift: LiveGiftEvent, var count: Int)

    private val density = resources.displayMetrics.density
    private val channelContainer = LinearLayout(context)
    private val activeEntries = mutableListOf<Entry>()
    private val queue = mutableListOf<PendingEntry>()
    private var generation = UUID.randomUUID().toString()

    init {
        visibility = GONE
        isClickable = false
        isFocusable = false
        clipChildren = false
        clipToPadding = false

        channelContainer.orientation = LinearLayout.VERTICAL
        channelContainer.clipChildren = false
        channelContainer.clipToPadding = false
        channelContainer.dividerDrawable = null
        addView(channelContainer, GiftEntryLayoutParams.container())
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        reset()
    }

    fun enqueue(gift: LiveGiftEvent) {
        if (gift.count <= 0) return

        activeEntries.firstOrNull { it.key == gift.comboKey && !it.isLeaving }?.let { active ->
            active.gift = gift
            active.count = addingWithoutOverflow(active.count, gift.count)
            active.view.updateCount(active.count, true)
            scheduleExpiration(active)
            return
        }

        activeEntries.firstOrNull { it.key == gift.comboKey && it.isLeaving }?.let { finish(it) }

        queue.firstOrNull { it.key == gift.comboKey }?.let { pending ->
            pending.gift = gift
            pending.count = addingWithoutOverflow(pending.count, gift.count)
            return
        }

        val pending = PendingEntry(gift.comboKey, gift, gift.count)
        if (activeEntries.size < MAXIMUM_CHANNEL_COUNT) {
            activate(pending)
        } else {
            queue.add(pending)
        }
    }

    fun reset() {
        generation = UUID.randomUUID().toString()
        activeEntries.forEach { entry ->
            cancelScheduledWork(entry)
            entry.view.prepareForRemoval()
            channelContainer.removeView(entry.view)
        }
        activeEntries.clear()
        queue.clear()
        visibility = GONE
    }

    private fun activate(pending: PendingEntry) {
        val itemView = LiveGiftEntryItemView(context)
        val entry = Entry(pending.gift, pending.count, itemView)
        activeEntries.add(entry)
        channelContainer.addView(
            itemView,
            GiftEntryLayoutParams.item(dp(ITEM_HEIGHT_DP)),
        )
        refreshChannelSpacing()
        itemView.updateGift(pending.gift, pending.count)

        visibility = VISIBLE
        itemView.alpha = 0f
        itemView.translationX = -(width + dp(ENTER_OFFSET_EXTRA_DP)).toFloat()

        val fade = ObjectAnimator.ofFloat(itemView, ALPHA, 0f, 1f).apply {
            duration = FADE_IN_DURATION_MS
            interpolator = DecelerateInterpolator()
        }
        val slide = ObjectAnimator.ofFloat(itemView, TRANSLATION_X, itemView.translationX, 0f).apply {
            duration = ENTER_DURATION_MS
            interpolator = PathInterpolator(0.16f, 0.82f, 0.22f, 1f)
        }
        val animator = AnimatorSet().apply {
            playTogether(fade, slide)
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    entry.animator = null
                }
            })
        }
        entry.animator = animator
        animator.start()
        scheduleExpiration(entry)
    }

    private fun scheduleExpiration(entry: Entry) {
        entry.expiration?.let { removeCallbacks(it) }
        val currentGeneration = generation
        val work = Runnable {
            if (generation == currentGeneration) {
                dismiss(entry)
            }
        }
        entry.expiration = work
        postDelayed(work, HOLD_DURATION_MS)
    }

    private fun dismiss(entry: Entry) {
        if (!activeEntries.contains(entry) || entry.isLeaving) return
        entry.isLeaving = true
        entry.expiration = null
        entry.animator?.cancel()
        entry.animator = null

        val fade = ObjectAnimator.ofFloat(entry.view, ALPHA, entry.view.alpha, 0f)
        val slide = ObjectAnimator.ofFloat(
            entry.view,
            TRANSLATION_X,
            entry.view.translationX,
            -dp(EXIT_OFFSET_DP).toFloat(),
        )
        val animator = AnimatorSet().apply {
            duration = EXIT_DURATION_MS
            interpolator = AccelerateInterpolator()
            playTogether(fade, slide)
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    finish(entry)
                }
            })
        }
        entry.animator = animator
        animator.start()
    }

    private fun finish(entry: Entry) {
        if (!activeEntries.remove(entry)) return
        cancelScheduledWork(entry)
        entry.view.prepareForRemoval()
        channelContainer.removeView(entry.view)
        refreshChannelSpacing()

        if (queue.isNotEmpty()) {
            activate(queue.removeAt(0))
        } else if (activeEntries.isEmpty()) {
            visibility = GONE
        }
    }

    private fun refreshChannelSpacing() {
        val spacing = dp(CHANNEL_SPACING_DP)
        for (index in 0 until channelContainer.childCount) {
            val child = channelContainer.getChildAt(index)
            val params = child.layoutParams as LinearLayout.LayoutParams
            val topMargin = if (index == 0) 0 else spacing
            if (params.topMargin != topMargin) {
                params.topMargin = topMargin
                child.layoutParams = params
            }
        }
    }

    private fun cancelScheduledWork(entry: Entry) {
        entry.expiration?.let { removeCallbacks(it) }
        entry.expiration = null
        entry.animator?.cancel()
        entry.animator = null
    }

    private fun addingWithoutOverflow(lhs: Int, rhs: Int): Int {
        val sum = lhs.toLong() + rhs.toLong()
        return if (sum > Int.MAX_VALUE) Int.MAX_VALUE else sum.toInt()
    }

    private fun dp(value: Float): Int = (value * density).toInt()

    private object GiftEntryLayoutParams {
        fun container(): LayoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT).apply {
            startToStart = LayoutParams.PARENT_ID
            endToEnd = LayoutParams.PARENT_ID
            bottomToBottom = LayoutParams.PARENT_ID
        }

        fun item(height: Int): LinearLayout.LayoutParams =
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, height)
    }

    private companion object {
        const val MAXIMUM_CHANNEL_COUNT = 2
        const val ITEM_HEIGHT_DP = 44f
        const val CHANNEL_SPACING_DP = 4f
        const val ENTER_OFFSET_EXTRA_DP = 12f
        const val EXIT_OFFSET_DP = 24f
        const val HOLD_DURATION_MS = 2000L
        const val FADE_IN_DURATION_MS = 260L
        const val ENTER_DURATION_MS = 520L
        const val EXIT_DURATION_MS = 320L
    }
}
