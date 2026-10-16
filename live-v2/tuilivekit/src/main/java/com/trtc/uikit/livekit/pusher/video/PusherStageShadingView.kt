package com.trtc.uikit.livekit.pusher.video

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.content.ContextCompat
import com.trtc.uikit.livekit.R

/**
 * Top and bottom scrims of the media stage, shared by the prepare and countdown stages.
 * Mirrors ios/live/.../Pusher/Shared/PusherStageShadingView.swift and the Ardot
 * 顶部压暗渐变 / 底部压暗渐变 layers of 开播准备页 / 倒计时.
 *
 * Both scrims are laid out against the stage area, which is this view minus the dark bar
 * at the bottom, so they keep the design proportions on any screen height.
 */
class PusherStageShadingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    companion object {
        /** Stage height of the Ardot frame, used as the denominator of every ratio. */
        private const val STAGE_VB_HEIGHT = 732f

        private const val TOP_VB_HEIGHT = 337f
        private const val BOTTOM_VB_TOP = 425f
        private const val BOTTOM_VB_HEIGHT = 307f

        private const val TOP_MIDDLE_STOP = 0.4f
        private const val BOTTOM_MIDDLE_STOP = 0.62f
    }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    /** Bottom corners of the stage, so the bottom scrim cannot square off the video corners. */
    private val cornerRadius =
        resources.getDimension(R.dimen.livekit_pusher_stage_corner_radius)
    private val bottomBounds = RectF()
    private val bottomPath = Path()

    private val topStartColor =
        ContextCompat.getColor(context, R.color.livekit_pusher_stage_shading_top_start)
    private val topMiddleColor =
        ContextCompat.getColor(context, R.color.livekit_pusher_stage_shading_top_middle)
    private val bottomMiddleColor =
        ContextCompat.getColor(context, R.color.livekit_pusher_stage_shading_bottom_middle)
    private val bottomEndColor =
        ContextCompat.getColor(context, R.color.livekit_pusher_stage_shading_bottom_end)

    private var barHeight =
        resources.getDimensionPixelSize(R.dimen.livekit_pusher_bottom_bar_height).toFloat()

    private var topShader: Shader? = null
    private var topHeight = 0f
    private var bottomShader: Shader? = null
    private var bottomTop = 0f
    private var bottomHeight = 0f

    init {
        ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
            val nextHeight = PusherVideoView.bottomInset(context, insets).toFloat()
            if (barHeight != nextHeight) {
                barHeight = nextHeight
                updateShaders(width, height)
                invalidate()
            }
            insets
        }
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        updateShaders(width, height)
    }

    private fun updateShaders(width: Int, height: Int) {
        val stageHeight = (height - barHeight).coerceAtLeast(0f)
        topHeight = stageHeight * TOP_VB_HEIGHT / STAGE_VB_HEIGHT
        bottomTop = stageHeight * BOTTOM_VB_TOP / STAGE_VB_HEIGHT
        bottomHeight = stageHeight * BOTTOM_VB_HEIGHT / STAGE_VB_HEIGHT

        topShader = if (topHeight > 0f) {
            LinearGradient(
                0f,
                0f,
                0f,
                topHeight,
                intArrayOf(topStartColor, topMiddleColor, Color.TRANSPARENT),
                floatArrayOf(0f, TOP_MIDDLE_STOP, 1f),
                Shader.TileMode.CLAMP
            )
        } else {
            null
        }

        bottomShader = if (bottomHeight > 0f) {
            bottomBounds.set(0f, bottomTop, width.toFloat(), bottomTop + bottomHeight)
            bottomPath.reset()
            bottomPath.addRoundRect(
                bottomBounds,
                floatArrayOf(
                    0f, 0f,
                    0f, 0f,
                    cornerRadius, cornerRadius,
                    cornerRadius, cornerRadius
                ),
                Path.Direction.CW
            )
            LinearGradient(
                0f,
                bottomTop,
                0f,
                bottomTop + bottomHeight,
                intArrayOf(Color.TRANSPARENT, bottomMiddleColor, bottomEndColor),
                floatArrayOf(0f, BOTTOM_MIDDLE_STOP, 1f),
                Shader.TileMode.CLAMP
            )
        } else {
            null
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        topShader?.let {
            paint.shader = it
            canvas.drawRect(0f, 0f, width.toFloat(), topHeight, paint)
        }
        bottomShader?.let {
            paint.shader = it
            canvas.drawPath(bottomPath, paint)
        }
        paint.shader = null
    }
}
