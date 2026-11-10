package com.trtc.uikit.livekit.common.gift.display

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.View
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.PathInterpolator
import android.widget.LinearLayout
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.AppCompatTextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import com.trtc.uikit.livekit.R
import com.trtc.uikit.livekit.common.theme.imageloader.ImageLoader

/**
 * Renders normal gifts in at most two independent channels. The merge key is
 * always senderID + giftID.
 */
internal class GiftNoticeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : ConstraintLayout(context, attrs, defStyleAttr) {

    var onContentHeightChanged: ((Int) -> Unit)? = null
    val contentHeight: Int
        get() = activeEntries.size * dp(ITEM_HEIGHT_DP) + (activeEntries.size - 1).coerceAtLeast(0) * dp(
            CHANNEL_SPACING_DP
        )

    private class Entry(var gift: GiftDisplayItem, var count: Int, val view: GiftNoticeItemView) {
        val key = gift.comboKey
        var expiration: Runnable? = null
        var animator: Animator? = null
        var isLeaving = false
    }

    private class PendingEntry(val key: Pair<String, String>, var gift: GiftDisplayItem, var count: Int)

    private val density = resources.displayMetrics.density
    private val channelContainer = LinearLayout(context)
    private val activeEntries = mutableListOf<Entry>()
    private val queue = mutableListOf<PendingEntry>()
    private var generation = 0L
    private var occupancyMutationDepth = 0
    private var reportedContentHeight = 0

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
        addView(channelContainer, GiftNoticeLayoutParams.container())
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        reset()
    }

    fun enqueue(gift: GiftDisplayItem) = updateOccupancy {
        if (gift.count <= 0) return@updateOccupancy

        activeEntries.firstOrNull { it.key == gift.comboKey && !it.isLeaving }?.let { active ->
            active.gift = gift
            active.count = addingWithoutOverflow(active.count, gift.count)
            active.view.updateCount(active.count, true)
            scheduleExpiration(active)
            return@updateOccupancy
        }

        activeEntries.firstOrNull { it.key == gift.comboKey && it.isLeaving }?.let { finish(it) }

        queue.firstOrNull { it.key == gift.comboKey }?.let { pending ->
            pending.gift = gift
            pending.count = addingWithoutOverflow(pending.count, gift.count)
            return@updateOccupancy
        }

        val pending = PendingEntry(gift.comboKey, gift, gift.count)
        if (activeEntries.size < MAXIMUM_CHANNEL_COUNT) {
            activate(pending)
        } else {
            queue.add(pending)
        }
    }

    fun reset() = updateOccupancy {
        generation++
        val entries = activeEntries.toList()
        activeEntries.clear()
        queue.clear()
        entries.forEach { entry ->
            cancelScheduledWork(entry)
            entry.view.prepareForRemoval()
            channelContainer.removeView(entry.view)
        }
        visibility = GONE
    }

    private fun activate(pending: PendingEntry) {
        val itemView = GiftNoticeItemView(context)
        val entry = Entry(pending.gift, pending.count, itemView)
        activeEntries.add(entry)
        channelContainer.addView(
            itemView,
            GiftNoticeLayoutParams.item(dp(ITEM_HEIGHT_DP)),
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
        entry.animator?.removeAllListeners()
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

    private fun finish(entry: Entry) = updateOccupancy {
        if (!activeEntries.remove(entry)) return@updateOccupancy
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

    // An enqueue can finish a leaving entry and refill it before the final occupied height is known.
    private inline fun updateOccupancy(change: () -> Unit) {
        occupancyMutationDepth++
        try {
            change()
        } finally {
            occupancyMutationDepth--
            if (occupancyMutationDepth == 0 && reportedContentHeight != contentHeight) {
                reportedContentHeight = contentHeight
                onContentHeightChanged?.invoke(reportedContentHeight)
            }
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
        entry.animator?.removeAllListeners()
        entry.animator?.cancel()
        entry.animator = null
    }

    private fun addingWithoutOverflow(lhs: Int, rhs: Int): Int {
        val sum = lhs.toLong() + rhs.toLong()
        return if (sum > Int.MAX_VALUE) Int.MAX_VALUE else sum.toInt()
    }

    private fun dp(value: Float): Int = (value * density).toInt()

    private object GiftNoticeLayoutParams {
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

private class GiftNoticeItemView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : ConstraintLayout(context, attrs, defStyleAttr) {

    private val avatarView: AppCompatImageView
    private val senderView: AppCompatTextView
    private val detailView: AppCompatTextView
    private val iconView: GiftNoticeIconView
    private val countView: AppCompatTextView
    private var countBumpAnimator: Animator? = null

    init {
        inflate(context, R.layout.livekit_pusher_gift_entry_item_view, this)
        avatarView = findViewById(R.id.livekit_pusher_gift_entry_avatar)
        senderView = findViewById(R.id.livekit_pusher_gift_entry_sender)
        detailView = findViewById(R.id.livekit_pusher_gift_entry_detail)
        iconView = findViewById(R.id.livekit_pusher_gift_entry_icon)
        countView = findViewById(R.id.livekit_pusher_gift_entry_count)
        iconView.attachFallbackView(findViewById(R.id.livekit_pusher_gift_entry_icon_fallback))
        isClickable = false
        isFocusable = false
        clipChildren = false
        clipToPadding = false
    }

    fun updateGift(gift: GiftDisplayItem, count: Int) {
        val senderName = gift.senderName.ifEmpty { gift.senderID }
        senderView.text = senderName
        detailView.text = context.getString(R.string.livekit_pusher_gift_sent, gift.name)
        ImageLoader.loadAvatar(avatarView, gift.senderAvatarURL, R.drawable.livekit_livelist_default_avatar)
        updateCount(count, false)
        loadGiftIcon(gift.iconURL)
    }

    fun updateCount(count: Int, animated: Boolean) {
        countView.text = count.toString()
        if (!animated) return
        countBumpAnimator?.cancel()
        val bumpColor = ContextCompat.getColor(context, R.color.livekit_pusher_gift_count_bump_text)
        val normalColor = ContextCompat.getColor(context, R.color.livekit_pusher_gift_count_text)
        val animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = COUNT_BUMP_DURATION_MS
            addUpdateListener { animation ->
                val fraction = animation.animatedFraction
                val progress = if (fraction <= BUMP_PEAK_FRACTION) {
                    fraction / BUMP_PEAK_FRACTION
                } else {
                    1f - (fraction - BUMP_PEAK_FRACTION) / (1f - BUMP_PEAK_FRACTION)
                }
                val scale = 1f + (BUMP_MAX_SCALE - 1f) * progress
                countView.scaleX = scale
                countView.scaleY = scale
                countView.setTextColor(if (fraction <= BUMP_PEAK_FRACTION) bumpColor else normalColor)
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    countView.scaleX = 1f
                    countView.scaleY = 1f
                    countView.setTextColor(normalColor)
                    countBumpAnimator = null
                }
            })
        }
        countBumpAnimator = animator
        animator.start()
    }

    fun prepareForRemoval() {
        ImageLoader.clear(context, iconView)
        countBumpAnimator?.cancel()
        countBumpAnimator = null
        animate().cancel()
        countView.scaleX = 1f
        countView.scaleY = 1f
    }

    private fun loadGiftIcon(iconURL: String) {
        ImageLoader.clear(context, iconView)
        iconView.setImageDrawable(null)
        val url = iconURL.trim()
        if (url.isNotEmpty()) ImageLoader.load(context, iconView, url, 0)
    }

    private companion object {
        const val COUNT_BUMP_DURATION_MS = 220L
        const val BUMP_PEAK_FRACTION = 0.45f
        const val BUMP_MAX_SCALE = 1.24f
    }
}

/** Keeps the fallback visible until the image loader supplies a drawable. */
internal class GiftNoticeIconView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : AppCompatImageView(context, attrs, defStyleAttr) {

    private var fallbackView: View? = null

    fun attachFallbackView(view: View) {
        fallbackView = view
        syncFallbackVisibility()
    }

    override fun setImageDrawable(drawable: Drawable?) {
        super.setImageDrawable(drawable)
        syncFallbackVisibility()
    }

    override fun setImageResource(resId: Int) {
        super.setImageResource(resId)
        syncFallbackVisibility()
    }

    private fun syncFallbackVisibility() {
        fallbackView?.visibility = if (drawable == null) VISIBLE else GONE
    }
}
