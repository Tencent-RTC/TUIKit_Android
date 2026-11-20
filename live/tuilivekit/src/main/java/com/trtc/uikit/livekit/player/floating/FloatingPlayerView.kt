package com.trtc.uikit.livekit.player.floating

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.Outline
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import com.trtc.uikit.livekit.R
import com.trtc.uikit.livekit.common.theme.ScreenUtil
import com.trtc.uikit.livekit.common.theme.ScreenUtil.dp
import io.trtc.tuikit.atomicxcore.api.view.AtomicLiveView
import kotlin.math.abs

internal class FloatingPlayerView(context: Context) : FrameLayout(context) {

    val coreView = AtomicLiveView(context)
    var onTap: (() -> Unit)? = null
    var onClose: (() -> Unit)? = null
    var onDrag: ((Int, Int) -> Unit)? = null
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var downRawX = 0f
    private var downRawY = 0f
    private var lastRawX = 0f
    private var lastRawY = 0f
    private var dragging = false

    init {
        val radius = ScreenUtil.dip2px(12f).toFloat()
        background = GradientDrawable().apply {
            setColor(Color.BLACK)
            cornerRadius = radius
        }
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, radius)
            }
        }
        clipToOutline = true
        keepScreenOn = true

        addView(coreView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        val closeButton = ImageView(context).apply {
            setImageResource(R.drawable.livekit_ic_living_close)
            setBackgroundResource(R.drawable.livekit_player_action_bg)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding( 4.dp() ,  4.dp() ,  4.dp() ,  4.dp() )
            contentDescription = context.getString(R.string.livekit_livingtopbarview_close_desc)
        }
        addView(closeButton, LayoutParams(24.dp(), 24.dp()).apply {
            gravity = Gravity.TOP or Gravity.END
            topMargin =  4.dp()
            marginEnd =  4.dp()
        })
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean = true

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downRawX = event.rawX
                downRawY = event.rawY
                lastRawX = event.rawX
                lastRawY = event.rawY
                dragging = false
            }

            MotionEvent.ACTION_MOVE -> {
                if (!dragging && (abs(event.rawX - downRawX) > touchSlop || abs(event.rawY - downRawY) > touchSlop)) {
                    dragging = true
                }
                if (dragging) {
                    onDrag?.invoke((event.rawX - lastRawX).toInt(), (event.rawY - lastRawY).toInt())
                    lastRawX = event.rawX
                    lastRawY = event.rawY
                }
            }

            MotionEvent.ACTION_UP -> {
                performClick()
                if (!dragging) {
                    if (isCloseButtonHit(event.x, event.y)) onClose?.invoke() else onTap?.invoke()
                }
                dragging = false
            }

            MotionEvent.ACTION_CANCEL -> dragging = false
        }
        return true
    }

    private fun isCloseButtonHit(x: Float, y: Float): Boolean {
        val left = width -  28.dp()
        val bottom =  28.dp()
        return x >= left && x <= width -  4.dp()  && y >=  4.dp()  && y <= bottom
    }
}
