package com.trtc.uikit.livekit.common.gift.player

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.util.AttributeSet
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import com.trtc.uikit.livekit.R
import com.trtc.uikit.livekit.common.basic.imageloader.ImageLoader
import com.trtc.uikit.livekit.common.theme.avatar.AtomicAvatar
import com.trtc.uikit.livekit.common.theme.label.AtomicLabel

internal class LiveGiftEntryItemView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : ConstraintLayout(context, attrs, defStyleAttr) {

    private val avatarView: AtomicAvatar
    private val senderView: AtomicLabel
    private val detailView: AtomicLabel
    private val iconView: LiveGiftIconView
    private val countView: AtomicLabel
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

    fun updateGift(gift: LiveGiftEvent, count: Int) {
        val senderName = gift.senderName.ifEmpty { gift.senderID }
        senderView.text = senderName
        detailView.text = context.getString(R.string.livekit_pusher_gift_sent, gift.name)
        avatarView.setContent(
            AtomicAvatar.AvatarContent.URL(
                gift.senderAvatarURL,
                R.drawable.livekit_livelist_default_avatar,
            )
        )
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
        countBumpAnimator?.cancel()
        countBumpAnimator = null
        animate().cancel()
        countView.scaleX = 1f
        countView.scaleY = 1f
    }

    private fun loadGiftIcon(iconURL: String) {
        iconView.setImageDrawable(null)
        if (iconURL.isEmpty()) return
        ImageLoader.load(context, iconView, iconURL, 0)
    }

    private companion object {
        const val COUNT_BUMP_DURATION_MS = 220L
        const val BUMP_PEAK_FRACTION = 0.45f
        const val BUMP_MAX_SCALE = 1.24f
    }
}
