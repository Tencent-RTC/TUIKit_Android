package com.trtc.uikit.livekit.player.preview

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.text.TextUtils
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.toColorInt
import com.trtc.uikit.livekit.R
import com.trtc.uikit.livekit.common.basic.setDebounceClickListener
import com.trtc.uikit.livekit.common.theme.avatar.AtomicAvatar
import io.trtc.tuikit.atomicxcore.api.live.LiveInfo
import kotlin.math.sin

class PreviewView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    companion object {
        private const val DISABLED_ALPHA = 0.46f
    }

    private val imageAvatar: AtomicAvatar
    private val textRoomName: TextView
    private val textAnchorName: TextView
    private val joinLive: LinearLayout

    var onTapEnter: (() -> Unit)? = null

    init {
        LayoutInflater.from(context).inflate(R.layout.livekit_player_preview, this, true)
        joinLive = findViewById(R.id.ll_join_channel)
        imageAvatar = findViewById(R.id.iv_avatar)
        textRoomName = findViewById(R.id.tv_room_name)
        textAnchorName = findViewById(R.id.tv_anchor_name)
        joinLive.setDebounceClickListener {
            onTapEnter?.invoke()
        }
    }

    fun bind(liveInfo: LiveInfo) {
        imageAvatar.setContent(
            AtomicAvatar.AvatarContent.URL(
                url = liveInfo.liveOwner.avatarURL,
                placeImage = R.drawable.livekit_livelist_default_avatar
            )
        )
        textRoomName.text = if (TextUtils.isEmpty(liveInfo.liveName)) liveInfo.liveID else liveInfo.liveName
        textAnchorName.text =
            if (TextUtils.isEmpty(liveInfo.liveOwner.userName)) liveInfo.liveOwner.userID else liveInfo.liveOwner.userName
    }

    fun setJoinEnabled(enabled: Boolean) {
        joinLive.isEnabled = enabled
        joinLive.alpha = if (enabled) 1f else DISABLED_ALPHA
    }
}

class EntranceAnimationView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    companion object {
        private const val BAR_COUNT = 3
        private const val BAR_WIDTH = 2
        private const val BAR_HEIGHT = 7
        private const val BAR_SPACING = 3
        private const val ANIM_DURATION = 1000
        private val COLOR = "#FFFFFF".toColorInt()
    }

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = COLOR
        style = Paint.Style.FILL
    }
    private val barHeights = FloatArray(BAR_COUNT) { dp2px(BAR_HEIGHT.toFloat()) }
    private var animator: ValueAnimator? = null

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val totalWidth = dp2px((BAR_WIDTH * BAR_COUNT + BAR_SPACING * (BAR_COUNT - 1)).toFloat())
        val startX = (width - totalWidth) / 2
        val centerY = height / 2

        repeat(BAR_COUNT) { i ->
            val left = startX + i * dp2px((BAR_WIDTH + BAR_SPACING).toFloat())
            val top = centerY - barHeights[i] / 2
            val right = left + dp2px(BAR_WIDTH.toFloat())
            val bottom = centerY + barHeights[i] / 2

            canvas.drawRoundRect(left, top, right, bottom, dp2px(2f), dp2px(2f), barPaint)
        }
    }

    private fun startAnimation() {
        animator?.takeIf { it.isRunning }?.cancel()

        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = ANIM_DURATION.toLong()
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = LinearInterpolator()

            addUpdateListener { animation ->
                val progress = animation.animatedValue as Float
                repeat(BAR_COUNT) { i ->
                    val phase = i * 0.6f
                    val adjustedProgress = (progress + phase) % 1.0f
                    barHeights[i] = dp2px(BAR_HEIGHT + (sin(adjustedProgress * Math.PI) * 15).toFloat())
                }
                invalidate()
            }
            start()
        }
    }

    fun stopAnimation() {
        animator?.cancel()
    }

    private fun dp2px(dp: Float): Float {
        return (dp * resources.displayMetrics.density + 0.5f)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        startAnimation()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopAnimation()
    }
}
