package com.trtc.uikit.livekit.component.gift.view.cell

import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.trtc.uikit.livekit.R
import io.trtc.tuikit.atomicx.common.imageloader.ImageLoader
import io.trtc.tuikit.atomicxcore.api.gift.Gift

class GiftBatchCell(itemView: View) : GiftBaseCell(itemView) {

    private val layoutGift: LinearLayout = itemView.findViewById(R.id.ll_gift)
    private val giftImageView: ImageView = itemView.findViewById(R.id.iv_gift_icon)
    private val giftNameLabel: TextView = itemView.findViewById(R.id.tv_gift_name)
    private val giftPriceLabel: TextView = itemView.findViewById(R.id.tv_gift_price)

    private var selectedCount: Int = 0
    private var isLongPressing = false
    private val longPressHandler = Handler(Looper.getMainLooper())
    private var longPressRunnable: Runnable? = null

    private var badgeLabel: TextView? = null
    private var badgeCreated = false

    private var cachedGiftName: String = ""

    override val isActionStyle: Boolean = true

    private fun ensureBadgeUI() {
        if (badgeCreated) return
        badgeCreated = true

        val density = itemView.resources.displayMetrics.density

        val imageIndex = layoutGift.indexOfChild(giftImageView)
        val imageLp = giftImageView.layoutParams as LinearLayout.LayoutParams
        layoutGift.removeView(giftImageView)

        val container = FrameLayout(itemView.context)
        container.clipChildren = false
        container.clipToPadding = false
        layoutGift.addView(container, imageIndex, LinearLayout.LayoutParams(imageLp.width, imageLp.height).apply {
            gravity = imageLp.gravity
            topMargin = imageLp.topMargin
            bottomMargin = imageLp.bottomMargin
            marginStart = imageLp.marginStart
            marginEnd = imageLp.marginEnd
        })

        giftImageView.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )
        container.addView(giftImageView)

        badgeLabel = TextView(itemView.context).apply {
            textSize = 16f
            setTextColor(Color.WHITE)
            gravity = Gravity.END
            visibility = View.GONE
        }

        val badgeParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            topMargin = (2 * density).toInt()
            marginEnd = (4 * density).toInt()
        }
        container.addView(badgeLabel, badgeParams)
    }

    override fun bindGift(gift: Gift) {
        ImageLoader.load(itemView.context, giftImageView, gift.iconURL, 0)
        cachedGiftName = gift.name
        giftNameLabel.text = gift.name
        giftPriceLabel.text = gift.coins.toString()
        resetState()
    }

    override fun setSelected(selected: Boolean) {
        super.setSelected(selected)
        val backgroundRes = if (selected) {
            R.drawable.gift_selected_bg
        } else {
            R.drawable.gift_normal_bg
        }
        layoutGift.setBackgroundResource(backgroundRes)

        if (selected) {
            ensureBadgeUI()
            if (selectedCount == 0) {
                selectedCount = 1
            }
            badgeLabel?.visibility = View.VISIBLE
            updateBadgeUI(animate = true)
            showSendButton()
        } else {
            selectedCount = 0
            stopLongPress()
            badgeLabel?.visibility = View.GONE
            hideSendButton()
        }
    }

    private fun showSendButton() {
        giftNameLabel.text = itemView.context.getString(R.string.common_gift_give_gift)
        giftNameLabel.setTextColor(ContextCompat.getColor(itemView.context, io.trtc.tuikit.atomicx.R.color.text_color_primary))
        giftNameLabel.isClickable = true
        giftNameLabel.setOnClickListener {
            performSendAction()
        }
    }

    private fun hideSendButton() {
        giftNameLabel.text = cachedGiftName
        giftNameLabel.setTextColor(
            ContextCompat.getColor(itemView.context, R.color.common_text_color_primary)
        )
        giftNameLabel.background = null
        giftNameLabel.isClickable = false
        giftNameLabel.setOnClickListener(null)
    }

    override fun handleHitWhenSelected() {
        increaseCount()
        performBadgeScaleAnimation(1.2f)
    }

    override fun performSendAction() {
        val g = gift ?: return
        if (selectedCount <= 0) return

        listener?.onSendGift(this, g, selectedCount)

        selectedCount = 1
        updateBadgeUI(animate = true)
    }

    private fun getStep(current: Int): Int {
        return when {
            current < 10 -> 1
            current < 100 -> 5
            else -> 50
        }
    }

    private fun increaseCount() {
        val step = getStep(selectedCount)
        val newCount = selectedCount + step
        selectedCount = if (newCount > 999) 1 else newCount
        updateBadgeUI(animate = true)
    }

    private fun updateBadgeUI(animate: Boolean) {
        badgeLabel?.text = "x$selectedCount"
        if (animate) {
            val scale = if (isLongPressing) 1.1f else 1.2f
            performBadgeScaleAnimation(scale)
        }
    }

    private fun performBadgeScaleAnimation(scale: Float = 1.2f) {
        badgeLabel?.apply {
            scaleX = scale
            scaleY = scale
            animate()
                .scaleX(1f).scaleY(1f)
                .setDuration(200)
                .setInterpolator(OvershootInterpolator(2f))
                .start()
        }
    }

    fun setupLongPressOnBind() {
        itemView.setOnLongClickListener {
            if (itemView.isSelected) {
                startLongPress()
                true
            } else {
                false
            }
        }
    }

    private fun startLongPress() {
        if (isLongPressing) return
        isLongPressing = true

        longPressRunnable = object : Runnable {
            override fun run() {
                if (isLongPressing) {
                    increaseCount()
                    longPressHandler.postDelayed(this, 100)
                }
            }
        }
        longPressHandler.post(longPressRunnable!!)

        itemView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL -> {
                    stopLongPress()
                    performBadgeScaleAnimation(1.3f)
                    itemView.setOnTouchListener(null)
                }
            }
            false
        }
    }

    private fun stopLongPress() {
        isLongPressing = false
        longPressRunnable?.let {
            longPressHandler.removeCallbacks(it)
            longPressRunnable = null
        }
    }

    private fun resetState() {
        selectedCount = 0
        stopLongPress()
        badgeLabel?.visibility = View.GONE
        hideSendButton()
    }

    override fun prepareForReuse() {
        super.prepareForReuse()
        resetState()
    }

    companion object {
        fun create(parent: ViewGroup): GiftBatchCell {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.gift_layout_panel_recycle_item, parent, false)
            return GiftBatchCell(view)
        }
    }
}
