package com.trtc.uikit.livekit.voicelounge.living.end

import android.content.Context
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageButton
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.trtc.uikit.livekit.R
import com.trtc.uikit.livekit.player.summary.SummaryView
import com.trtc.uikit.livekit.voicelounge.MATCH
import com.trtc.uikit.livekit.voicelounge.dp
import com.trtc.uikit.livekit.voicelounge.frameParams
import com.trtc.uikit.livekit.voicelounge.rgb
import io.trtc.tuikit.atomicxcore.api.live.VoiceLoungeInfo

internal class VoiceLoungeAudienceEndView(context: Context) : FrameLayout(context) {
    var onBack: (() -> Unit)? = null
    var onSwipeUp: (() -> Unit)? = null

    private val summaryView = SummaryView(context)
    private val backButton = ImageButton(context)
    private var statusTop = 0

    private val swipeDetector = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                if (velocityY < -SWIPE_VELOCITY_THRESHOLD) {
                    onSwipeUp?.invoke()
                    return true
                }
                return false
            }
        },
    )

    init {
        setBackgroundColor(rgb(0.06f, 0.07f, 0.10f))
        visibility = View.GONE
        isClickable = true
        isFocusable = true

        addView(summaryView, frameParams(MATCH, MATCH))

        backButton.setImageResource(R.drawable.livekit_summary_back)
        backButton.background = null
        backButton.scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
        backButton.setOnClickListener { onBack?.invoke() }
        addView(
            backButton,
            frameParams(dp(44), dp(44), Gravity.TOP or Gravity.START, start = dp(12)),
        )
        updateBackTopMargin()

        ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.displayCutout())
            statusTop = bars.top
            updateBackTopMargin()
            insets
        }
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        swipeDetector.onTouchEvent(ev)
        return false
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        swipeDetector.onTouchEvent(event)
        return super.onTouchEvent(event)
    }

    fun show(lounge: VoiceLoungeInfo, reason: String? = null) {
        summaryView.bind(
            ownerName = lounge.ownerInfo.userName.ifEmpty { lounge.ownerInfo.userID },
            ownerAvatarURL = lounge.ownerInfo.avatarURL,
            backgroundURL = lounge.backgroundURL.ifEmpty { lounge.coverURL },
            reason = reason,
        )
        visibility = View.VISIBLE
        bringToFront()
    }

    fun hide() {
        visibility = View.GONE
    }

    private fun updateBackTopMargin() {
        val lp = backButton.layoutParams as LayoutParams
        val target = statusTop + dp(14) - dp(22)
        if (lp.topMargin != target) {
            lp.topMargin = target.coerceAtLeast(dp(4))
            backButton.layoutParams = lp
        }
    }

    private companion object {
        const val SWIPE_VELOCITY_THRESHOLD = 600f
    }
}
