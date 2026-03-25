package com.trtc.uikit.livekit.component.gift.view.animation

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.RelativeLayout
import com.trtc.uikit.livekit.component.gift.view.GiftBulletFrameLayout

class ImageAnimationView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    FrameLayout(context, attrs) {
    private val normalChannelCount = 2
    private val trackHeightDp = 56
    private val bulletHeightDp = 46
    private val childViews: Array<GiftBulletFrameLayout> = Array(normalChannelCount) { GiftBulletFrameLayout(context) }
    private var callback: Callback? = null
    
    private val activeBulletViews: MutableMap<String, GiftBulletFrameLayout> = mutableMapOf()
    private val channelOccupancy: MutableMap<Int, String> = mutableMapOf()

    private val density = context.resources.displayMetrics.density
    private val trackHeightPx = (trackHeightDp * density).toInt()
    private val bulletHeightPx = (bulletHeightDp * density).toInt()
    private var lastAppliedBaseBottomMargin = -1

    init {
        for (i in childViews.indices) {
            val bullet = childViews[i]
            bullet.setCallback(object : GiftBulletFrameLayout.Callback {
                override fun onFinished(error: Int, comboKey: String) {
                    handleBulletFinished(comboKey)
                }
            })
            val lp = LayoutParams(LayoutParams.MATCH_PARENT, bulletHeightPx)
            lp.gravity = Gravity.BOTTOM
            lp.bottomMargin = i * trackHeightPx
            bullet.layoutParams = lp
            addView(bullet)
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        post {
            adjustSelfLayoutParams()
            updateBulletBottomMargins()
        }
    }

    private fun adjustSelfLayoutParams() {
        parent ?: return
        val lp = layoutParams as? RelativeLayout.LayoutParams ?: return
        if (lp.height != RelativeLayout.LayoutParams.MATCH_PARENT) {
            lp.height = RelativeLayout.LayoutParams.MATCH_PARENT
            lp.removeRule(RelativeLayout.ALIGN_PARENT_BOTTOM)
            layoutParams = lp
        }
    }

    private fun updateBulletBottomMargins() {
        val viewHeight = height
        if (viewHeight <= 0) return
        val isLandscape = width > height
        val baseBottomMargin = if (isLandscape) {
            (viewHeight * BOTTOM_MARGIN_RATIO_LANDSCAPE).toInt()
        } else {
            (viewHeight * BOTTOM_MARGIN_RATIO_PORTRAIT).toInt()
        }
        if (baseBottomMargin == lastAppliedBaseBottomMargin) return
        lastAppliedBaseBottomMargin = baseBottomMargin
        for (i in childViews.indices) {
            val lp = childViews[i].layoutParams as LayoutParams
            lp.bottomMargin = baseBottomMargin + i * trackHeightPx
            childViews[i].layoutParams = lp
        }
    }

    fun playAnimation(model: GiftImageAnimationInfo) {
        if (!isAttachedToWindow) {
            postDelayed({ notifyFinished() }, 500)
            return
        }
        
        val comboKey = "${model.senderUserId}_${model.giftId}"
        
        val existingView = activeBulletViews[comboKey]
        if (existingView != null) {
            existingView.addGiftCount(model.giftCount)
            notifyFinished()
            return
        }
        
        val freeTrackIndex = getFreeTrackIndex()
        if (freeTrackIndex == null) {
            return
        }
        
        val bullet = childViews[freeTrackIndex]
        channelOccupancy[freeTrackIndex] = comboKey
        activeBulletViews[comboKey] = bullet
        
        bullet.setGiftInfo(model)
        bullet.startAnimation()
    }
    
    private fun getFreeTrackIndex(): Int? {
        for (i in 0 until normalChannelCount) {
            if (channelOccupancy[i] == null) {
                return i
            }
        }
        return null
    }
    
    private fun handleBulletFinished(comboKey: String) {
        activeBulletViews.remove(comboKey)
        
        val trackIndex = channelOccupancy.entries.find { it.value == comboKey }?.key
        trackIndex?.let { channelOccupancy.remove(it) }
        
        notifyFinished()
    }

    fun tryMergeGift(senderUserId: String?, giftId: String?, giftCount: Int): Boolean {
        val comboKey = "${senderUserId}_${giftId}"
        val existingView = activeBulletViews[comboKey]
        if (existingView != null) {
            existingView.addGiftCount(giftCount)
            return true
        }
        return false
    }

    fun stopPlay() {
        for (bullet in childViews) {
            bullet.stopPlay()
        }
        activeBulletViews.clear()
        channelOccupancy.clear()
    }

    private fun notifyFinished() {
        callback?.onFinished(0)
    }

    fun setCallback(callback: Callback?) {
        this@ImageAnimationView.callback = callback
    }

    interface Callback {
        fun onFinished(error: Int)
    }

    class GiftImageAnimationInfo {
        var senderAvatarUrl: String? = null
        var senderName: String? = null
        var senderUserId: String? = null
        var isFromSelf: Boolean = false
        var giftId: String? = null
        var giftName: String? = null
        var giftImageUrl: String? = null
        var giftCount: Int = 0

        init {
            reset()
        }

        fun reset() {
            senderAvatarUrl = ""
            senderName = ""
            senderUserId = ""
            isFromSelf = false
            giftId = ""
            giftName = ""
            giftImageUrl = ""
            giftCount = 0
        }
    }

    companion object {
        private const val BOTTOM_MARGIN_RATIO_LANDSCAPE = 0.53f
        private const val BOTTOM_MARGIN_RATIO_PORTRAIT = 0.45f
    }
}