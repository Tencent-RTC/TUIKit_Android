package com.example.atomicxcore.components

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.Color
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.util.AttributeSet
import android.util.Log
import android.view.Gravity
import android.view.animation.AccelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import coil.load
import com.example.atomicxcore.R
import com.opensource.svgaplayer.SVGACallback
import com.opensource.svgaplayer.SVGAImageView
import com.opensource.svgaplayer.SVGAParser
import com.opensource.svgaplayer.SVGAVideoEntity
import io.trtc.tuikit.atomicxcore.api.gift.Gift
import io.trtc.tuikit.atomicxcore.api.live.LiveUserInfo
import java.net.URL

/**
 * Gift animation display component
 *
 * Supports two types of gift animations:
 * 1. Full-screen SVGA animation — Uses SVGAPlayer when Gift.resourceURL has a value
 * 2. Barrage slide animation — Slides in from the left and disappears after staying (similar to TikTok live gift barrages)
 *
 * Usage:
 * Overlay GiftAnimationView on top of the live video (full screen), then call playGiftAnimation.
 */
class GiftAnimationView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    companion object {
        private const val TAG = "GiftAnimationView"
    }

    /** Barrage animation queue */
    private data class GiftBarrageItem(
        val gift: Gift,
        val count: Int,
        val sender: LiveUserInfo
    )

    private val animationQueue = mutableListOf<GiftBarrageItem>()
    private val activeSlots = mutableMapOf<Int, android.view.View>()
    private val maxSlots = 3

    // SVGA full-screen player
    private val svgaParser = SVGAParser(context)
    private val svgaImageView: SVGAImageView

    init {
        isClickable = false
        isFocusable = false
        setBackgroundColor(Color.TRANSPARENT)

        // Initialize SVGA full-screen player (hidden by default)
        svgaImageView = SVGAImageView(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            visibility = GONE
            isClickable = false
            isFocusable = false
            setBackgroundColor(Color.TRANSPARENT)
            loops = 1
            fillMode = SVGAImageView.FillMode.Clear
            scaleType = ImageView.ScaleType.FIT_CENTER
            callback = object : SVGACallback {
                override fun onPause() {}
                override fun onRepeat() {}
                override fun onStep(frame: Int, percentage: Double) {}
                override fun onFinished() {
                    post {
                        stopAnimation()
                        visibility = GONE
                    }
                }
            }
        }
        addView(svgaImageView)
    }

    /**
     * Play gift animation
     * - If gift.resourceURL has a value, play full-screen SVGA animation
     * - A barrage slide animation is always shown regardless of full-screen animation
     */
    fun playGiftAnimation(gift: Gift, count: Int, sender: LiveUserInfo) {
        // Full-screen SVGA animation
        if (!gift.resourceURL.isNullOrEmpty()) {
            playSVGAAnimation(gift.resourceURL)
        }

        // Barrage slide animation
        val item = GiftBarrageItem(gift, count, sender)
        animationQueue.add(item)
        safeProcessQueue()
    }

    // MARK: - SVGA Full-screen Animation

    private fun playSVGAAnimation(resourceURL: String) {
        try {
            val url = URL(resourceURL)
            svgaImageView.visibility = VISIBLE
            // Ensure SVGA player is on top layer (above barrages)
            bringChildToFront(svgaImageView)

            svgaParser.decodeFromURL(url, object : SVGAParser.ParseCompletion {
                override fun onComplete(videoItem: SVGAVideoEntity) {
                    post {
                        if (!isAttachedToWindow) return@post
                        svgaImageView.setVideoItem(videoItem)
                        svgaImageView.startAnimation()
                    }
                }

                override fun onError() {
                    Log.w(TAG, "Failed to parse SVGA from: $resourceURL")
                    post {
                        svgaImageView.visibility = GONE
                    }
                }
            })
        } catch (e: Exception) {
            Log.w(TAG, "Invalid SVGA URL: $resourceURL", e)
            svgaImageView.visibility = GONE
        }
    }

    /** Safely process queue: ensure the View has completed layout */
    private fun safeProcessQueue() {
        if (animationQueue.isEmpty()) return
        if (!isAttachedToWindow) return

        if (height > 0) {
            processQueue()
        } else {
            // View hasn't completed layout yet; wait for the next frame
            post { safeProcessQueue() }
        }
    }

    private fun processQueue() {
        if (animationQueue.isEmpty()) return

        for (slot in 0 until maxSlots) {
            if (animationQueue.isEmpty()) break
            if (activeSlots[slot] == null) {
                val item = animationQueue.removeFirst()
                showBarrageItem(item, slot)
            }
        }
    }

    private fun showBarrageItem(item: GiftBarrageItem, slot: Int) {
        val dp = resources.displayMetrics.density
        val dp48 = (48 * dp).toInt()
        val dp32 = (32 * dp).toInt()
        val dp4 = (4 * dp).toInt()
        val dp6 = (6 * dp).toInt()
        val dp8 = (8 * dp).toInt()
        val dp280 = (280 * dp).toInt()

        val itemView = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(Color.parseColor("#80000000"))
                cornerRadius = 24 * dp
            }
            setPadding(dp4, dp4, dp8, dp4)
        }

        // Avatar
        val avatarView = ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams(dp32, dp32)
            scaleType = ImageView.ScaleType.CENTER_CROP
            clipToOutline = true
            outlineProvider = object : android.view.ViewOutlineProvider() {
                override fun getOutline(view: android.view.View, outline: android.graphics.Outline) {
                    outline.setOval(0, 0, view.width, view.height)
                }
            }
        }
        if (!item.sender.avatarURL.isNullOrEmpty()) {
            avatarView.load(item.sender.avatarURL)
        } else {
            avatarView.setImageResource(R.drawable.ic_person)
        }
        itemView.addView(avatarView)

        // Text
        val senderName = if (item.sender.userName.isNullOrEmpty()) item.sender.userID else item.sender.userName
        val ssb = SpannableStringBuilder()
        ssb.append("$senderName\n")
        ssb.setSpan(ForegroundColorSpan(Color.WHITE), 0, senderName.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        ssb.setSpan(StyleSpan(android.graphics.Typeface.BOLD), 0, senderName.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        val actionStart = ssb.length
        val actionText = "${context.getString(R.string.interactive_gift_sent)} ${item.gift.name}"
        ssb.append(actionText)
        ssb.setSpan(ForegroundColorSpan(Color.parseColor("#CCFFFFFF")), actionStart, ssb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        ssb.setSpan(RelativeSizeSpan(0.85f), actionStart, ssb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

        val textLabel = TextView(context).apply {
            text = ssb
            textSize = 12f
            maxLines = 2
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp6
            }
        }
        itemView.addView(textLabel)

        // Gift icon
        val giftIcon = ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams(dp32, dp32)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        if (!item.gift.iconURL.isNullOrEmpty()) {
            giftIcon.load(item.gift.iconURL)
        } else {
            giftIcon.setImageResource(R.drawable.ic_gift)
        }
        itemView.addView(giftIcon)

        // Count
        val countLabel = TextView(context).apply {
            text = "x${item.count}"
            textSize = 18f
            setTextColor(Color.parseColor("#FFFF00"))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginStart = dp4 }
        }
        itemView.addView(countLabel)

        addView(itemView)
        activeSlots[slot] = itemView

        val slotY = (height * 0.35f + slot * 56 * dp)
        itemView.layoutParams = LayoutParams(dp280, dp48).apply {
            topMargin = slotY.toInt()
        }
        itemView.translationX = -300 * dp
        itemView.alpha = 0f

        // Slide-in animation
        val slideIn = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(itemView, "translationX", -300 * dp, 12 * dp),
                ObjectAnimator.ofFloat(itemView, "alpha", 0f, 1f)
            )
            duration = 400
            interpolator = OvershootInterpolator(0.8f)
        }
        slideIn.start()

        // Count bounce animation
        countLabel.scaleX = 0.1f
        countLabel.scaleY = 0.1f
        AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(countLabel, "scaleX", 0.1f, 1f),
                ObjectAnimator.ofFloat(countLabel, "scaleY", 0.1f, 1f)
            )
            duration = 400
            startDelay = 300
            interpolator = OvershootInterpolator(2f)
            start()
        }

        // Fade out after delay
        postDelayed({
            if (!isAttachedToWindow) {
                activeSlots.remove(slot)
                return@postDelayed
            }
            val fadeOut = AnimatorSet().apply {
                playTogether(
                    ObjectAnimator.ofFloat(itemView, "alpha", 1f, 0f),
                    ObjectAnimator.ofFloat(itemView, "translationY", 0f, -20 * dp)
                )
                duration = 500
                interpolator = AccelerateInterpolator()
            }
            fadeOut.start()
            postDelayed({
                if (isAttachedToWindow) {
                    removeView(itemView)
                }
                activeSlots.remove(slot)
                safeProcessQueue()
            }, 500)
        }, 2400)
    }
}
