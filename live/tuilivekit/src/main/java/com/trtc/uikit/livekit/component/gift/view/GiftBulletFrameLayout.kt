package com.trtc.uikit.livekit.component.gift.view

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.util.AttributeSet
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.constraintlayout.utils.widget.ImageFilterView
import com.trtc.uikit.livekit.R
import com.trtc.uikit.livekit.component.gift.view.animation.ImageAnimationView.GiftImageAnimationInfo
import io.trtc.tuikit.atomicx.common.imageloader.ImageLoader
import io.trtc.tuikit.atomicx.widget.basicwidget.avatar.AtomicAvatar
import io.trtc.tuikit.atomicx.widget.basicwidget.avatar.AtomicAvatar.AvatarContent

class GiftBulletFrameLayout @JvmOverloads constructor(
    private val mContext: Context,
    attrs: AttributeSet? = null
) : FrameLayout(mContext, attrs) {
    private val handler = Handler(Looper.getMainLooper())
    private val layoutInflater: LayoutInflater = LayoutInflater.from(mContext)
    private val isRtl: Boolean =
        mContext.resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL
    private var giftEndAnimationRunnable: Runnable? = null
    private var giftGroup: RelativeLayout? = null
    private var imageGiftIcon: ImageFilterView? = null
    private var imageSendUserIcon: AtomicAvatar? = null
    private var textSendUserName: TextView? = null
    private var textGiftTitle: TextView? = null
    private var textGiftCount: TextView? = null
    private var callback: Callback? = null
    private val giftImageAnimationInfo = GiftImageAnimationInfo()
    private var comboKey: String = ""
    private var totalCount: Int = 0
    private var isPlaying: Boolean = false

    init {
        val rootView = layoutInflater.inflate(R.layout.gift_layout_bullet, this)
        giftGroup = rootView.findViewById(R.id.gift_group)
        imageGiftIcon = rootView.findViewById(R.id.iv_gift_icon)
        imageSendUserIcon = rootView.findViewById(R.id.iv_send_user_icon)
        textSendUserName = rootView.findViewById(R.id.tv_send_user_name)
        textGiftTitle = rootView.findViewById(R.id.tv_gift_title)
        textGiftCount = rootView.findViewById(R.id.tv_gift_count)
        visibility = INVISIBLE
    }

    fun setGiftInfo(info: GiftImageAnimationInfo) {
        giftImageAnimationInfo.senderAvatarUrl = info.senderAvatarUrl
        giftImageAnimationInfo.senderName = info.senderName
        giftImageAnimationInfo.senderUserId = info.senderUserId
        giftImageAnimationInfo.isFromSelf = info.isFromSelf
        giftImageAnimationInfo.giftCount = info.giftCount
        giftImageAnimationInfo.giftId = info.giftId
        giftImageAnimationInfo.giftName = info.giftName
        giftImageAnimationInfo.giftImageUrl = info.giftImageUrl
        comboKey = "${info.senderUserId}_${info.giftId}"
        totalCount = info.giftCount
        updateUI()
    }

    fun addGiftCount(count: Int) {
        totalCount += count
        giftImageAnimationInfo.giftCount = totalCount
        updateUI()
        resetDismissTimer()
        playCountUpdateAnimation()
    }

    private fun updateUI() {
        val displayName = if (giftImageAnimationInfo.isFromSelf) {
            mContext.getString(R.string.common_gift_me)
        } else if (giftImageAnimationInfo.senderName.isNullOrEmpty()) {
            giftImageAnimationInfo.senderUserId ?: ""
        } else {
            giftImageAnimationInfo.senderName
        }
        textSendUserName?.text = displayName
        textGiftTitle?.text = giftImageAnimationInfo.giftName
        if (totalCount >= 1) {
            textGiftCount?.visibility = View.VISIBLE
            textGiftCount?.text = "×$totalCount "
        } else {
            textGiftCount?.visibility = View.GONE
        }
    }

    private fun playCountUpdateAnimation() {
        textGiftCount?.let { countView ->
            countView.animate()
                .scaleX(1.5f)
                .scaleY(1.5f)
                .setDuration(200)
                .setInterpolator(OvershootInterpolator(0.6f))
                .withEndAction {
                    countView.animate()
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .setDuration(200)
                        .setInterpolator(OvershootInterpolator(0.6f))
                        .start()
                }
                .start()
        }
    }

    private fun resetDismissTimer() {
        giftEndAnimationRunnable?.let {
            handler.removeCallbacks(it)
        }
        giftEndAnimationRunnable = Runnable { this.endAnimation() }
        handler.postDelayed(giftEndAnimationRunnable!!, GIFT_DISMISS_TIME.toLong())
    }

    fun stopPlay() {
        visibility = INVISIBLE
        isPlaying = false
        if (giftEndAnimationRunnable != null) {
            handler.removeCallbacks(giftEndAnimationRunnable!!)
        }
        giftImageAnimationInfo.reset()
        totalCount = 0
        comboKey = ""
    }

    private fun initLayoutState() {
        if (!isAttachedToWindow) {
            Log.w("GiftBulletFrameLayout", "initLayoutState: isAttachedToWindow is false")
            return
        }
        this.visibility = VISIBLE
        if (!TextUtils.isEmpty(giftImageAnimationInfo.giftImageUrl)) {
            imageGiftIcon?.let {
                ImageLoader.load(
                    context,
                    imageGiftIcon,
                    giftImageAnimationInfo.giftImageUrl,
                    R.drawable.gift_default_avatar
                )
            }
        }
        imageSendUserIcon?.setContent(
            AvatarContent.URL(
                giftImageAnimationInfo.senderAvatarUrl ?: "",
                R.drawable.gift_default_avatar
            )
        )
    }

    fun startAnimation() {
        if (isPlaying) {
            return
        }
        visibility = VISIBLE
        isPlaying = true
        imageGiftIcon?.setVisibility(VISIBLE)

        val duration = 350L
        val startX = if (isRtl) getWidth().toFloat() else -getWidth().toFloat()

        val giftLayoutAnimator = AnimationUtils.createFadesInFromLtoR(
            giftGroup, startX, 0f, duration.toInt(), OvershootInterpolator(0.6f)
        )
        giftLayoutAnimator.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationStart(animation: Animator) {
                initLayoutState()
            }
        })

        val giftImageAnimator = AnimationUtils.createFadesInFromLtoR(
            imageGiftIcon, startX, 0f, duration.toInt(), DecelerateInterpolator()
        )
        giftImageAnimator.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationStart(animation: Animator) {
                imageGiftIcon?.setVisibility(VISIBLE)
            }
        })

        AnimationUtils.startAnimation(giftLayoutAnimator, giftImageAnimator)
        resetDismissTimer()
    }

    fun endAnimation() {
        val endY = -30f
        val fadeAnimator = AnimationUtils.createFadesOutAnimator(
            this, 0f, endY, 300, 0
        )
        val fadeAnimator2 = AnimationUtils.createFadesOutAnimator(
            this, 100f, 0f, 0, 0
        )
        fadeAnimator2.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                visibility = INVISIBLE
                isPlaying = false
                setAlpha(1f)
                translationY = 0f
                callback?.onFinished(0, comboKey)
            }
        })
        AnimationUtils.startAnimation(fadeAnimator, fadeAnimator2)
    }

    fun setCallback(callback: Callback?) {
        this.callback = callback
    }

    interface Callback {
        fun onFinished(error: Int, comboKey: String)
    }

    companion object {
        private const val GIFT_DISMISS_TIME = 5000
    }
}
