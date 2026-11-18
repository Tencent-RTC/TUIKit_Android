package io.trtc.tuikit.chat.uikit.components.aiassistant.call.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.SweepGradient
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import io.trtc.tuikit.atomicx.theme.ThemeStore
import io.trtc.tuikit.chat.uikit.components.aiassistant.call.AICallState
import kotlin.math.min

/**
 * 通话页中央的光球。颜色取自 App 主题主色，深浅用相邻的 hover / active 两档，
 * 换主题不用再改这里。
 *
 * LISTENING：主题色发光球，呼吸式缩放
 * SPEAKING ：同色软边球，半径跟平滑后的播放音量脉动，字缝里带一层浅呼吸
 * THINKING ：慢速旋转的扫描渐变
 * IDLE     ：聆听态的静帧——颜色和光晕都一样，只是半径钉死、不呼吸。
 *            进页那一下如果画成暗色实心圆，会像一颗还没点亮的空球。
 */
class VoiceOrbView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    /** 主线程写，采集线程和绘制线程读，故 volatile */
    @Volatile
    private var state = AICallState.IDLE

    /** 呼吸/旋转动画的进度，0f..1f。只在主线程读写。 */
    private var phase = 0f

    /**
     * 播放音量目标，0f..1f。采集线程写、主线程在 vsync 上读，故 volatile。
     *
     * TTS 分片往往是 20~60ms 一包，RMS 会台阶式跳变。真正画到屏幕上的是 [displayedLevel]，
     * 按快起慢收跟过去，避免光球一顿一顿。
     */
    @Volatile
    private var targetLevel = 0f

    /** 已经画在屏幕上的音量。只在主线程、动画回调里更新。 */
    private var displayedLevel = 0f

    private var animator: ValueAnimator? = null

    /** 主题色由浅到深：hover / default / active，进页时读一次即可。 */
    private var colorLight = 0
    private var colorCore = 0
    private var colorDark = 0
    private var colorFade = 0

    /**
     * 流光样式：多色扫描渐变 + 边缘缓慢形变，体验 Demo 的 AI 首屏和通话页用。
     * 默认关闭，关闭时绘制逻辑和以前完全一样。
     */
    var auroraStyle: Boolean = false
        set(value) {
            if (field == value) {
                return
            }
            field = value
            applyLayerType(state)
            restartAnimator()
            invalidate()
        }

    /** 深色背景下光晕更亮、更通透。 */
    var auroraOnDark: Boolean = false
        set(value) {
            if (field == value) {
                return
            }
            field = value
            glowShader = null
            invalidate()
        }

    private val auroraPath = Path()
    private val auroraMatrix = Matrix()
    private val auroraPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG)
    private val srcAtop = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC_ATOP)
    private val auroraPoints = FloatArray(AURORA_POINTS * 2)
    private var auroraTime = 0f
    private var lastAuroraNanos = 0L
    private var smoothEnergy = 0.2f
    private var smoothBoost = 0f
    private var glowShader: RadialGradient? = null
    private var highlightShader: RadialGradient? = null
    private var shadeShader: RadialGradient? = null
    private var blobShaders: Array<RadialGradient> = emptyArray()
    private var shaderBaseRadius = -1f
    private var shaderMaxRadius = -1f

    init {
        applyThemeColors()
        applyLayerType(AICallState.IDLE)
    }

    private fun applyThemeColors() {
        val colors = ThemeStore.shared(context).themeState.value.currentTheme.tokens.color
        colorLight = colors.buttonColorPrimaryHover
        colorCore = colors.buttonColorPrimaryDefault
        colorDark = colors.buttonColorPrimaryActive
        colorFade = colorDark and 0x00FFFFFF
    }

    fun setState(newState: AICallState) {
        if (state == newState) {
            return
        }
        state = newState
        if (newState != AICallState.SPEAKING) {
            targetLevel = 0f
            displayedLevel = 0f
        }
        applyLayerType(newState)
        restartAnimator()
        invalidate()
    }

    /**
     * 音量来自采集线程。这里只记下目标值，不 invalidate：
     * 播放态每 20ms 脏一次，再叠软件层栅格化，帧还没画完就又脏了，看起来就是卡。
     * SPEAKING 的绘制由下面的 ValueAnimator 跟 vsync 走。
     */
    fun setLevel(newLevel: Float) {
        targetLevel = newLevel.coerceIn(0f, 1f)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        restartAnimator()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        animator?.cancel()
        animator = null
    }

    private fun restartAnimator() {
        animator?.cancel()
        if (!isAttachedToWindow || (state == AICallState.IDLE && !auroraStyle)) {
            animator = null
            return
        }
        val duration = when (state) {
            AICallState.LISTENING -> 2600L
            AICallState.THINKING -> 1400L
            else -> 2000L
        }
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            this.duration = duration
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                phase = it.animatedValue as Float
                if (state == AICallState.SPEAKING) {
                    tickDisplayedLevel()
                }
                invalidate()
            }
            start()
        }
    }

    /**
     * 快起慢收：音量上来用约 40ms 跟上口型，落下用约 150ms 收，词与词之间不会抖成锯齿。
     * 系数是相对 60fps 一帧的近似，掉帧时只是包络稍慢，不会过冲。
     */
    private fun tickDisplayedLevel() {
        val target = targetLevel
        val alpha = if (target > displayedLevel) LEVEL_ATTACK else LEVEL_RELEASE
        displayedLevel += (target - displayedLevel) * alpha
        if (kotlin.math.abs(displayedLevel - target) < 0.002f) {
            displayedLevel = target
        }
    }

    /**
     * BlurMaskFilter 只能走软件层；播放态每帧重画，软件层会把光球画成一顿一顿。
     * SPEAKING 不用模糊，走 GPU 普通绘制。IDLE 要跟聆听态同色同光晕，所以也走软件层。
     */
    private fun applyLayerType(forState: AICallState) {
        val type = if (auroraStyle || forState == AICallState.SPEAKING) LAYER_TYPE_NONE else LAYER_TYPE_SOFTWARE
        if (layerType != type) {
            setLayerType(type, null)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val maxRadius = min(width, height) / 2f
        if (maxRadius <= 0f) {
            return
        }
        // 发光会向外弥散，球体半径必须留出这圈余量，否则光晕会被 View 边界切成方形
        val baseRadius = maxRadius * (1f - GLOW_RATIO)

        if (auroraStyle) {
            drawAurora(canvas, cx, cy, maxRadius, baseRadius)
            return
        }

        when (state) {
            AICallState.LISTENING -> drawListening(canvas, cx, cy, maxRadius, baseRadius)
            AICallState.SPEAKING -> drawSpeaking(canvas, cx, cy, baseRadius)
            AICallState.THINKING -> drawThinking(canvas, cx, cy, maxRadius, baseRadius)
            AICallState.IDLE -> drawIdle(canvas, cx, cy, maxRadius, baseRadius)
        }
    }

    /** 呼吸：半径在休息点附近来回。用 BlurMaskFilter 做出边缘发光的弥散感。 */
    private fun drawListening(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        maxRadius: Float,
        baseRadius: Float
    ) {
        val breath = REST_SCALE + 0.07f * kotlin.math.sin(phase * 2 * Math.PI).toFloat()
        drawGlowingOrb(canvas, cx, cy, maxRadius, baseRadius * breath)
    }

    /**
     * 播放：半径跟平滑后的音量走，并叠一层很浅的呼吸，避免字缝里完全停住。
     * 外圈用透明色收边，比 BlurMaskFilter 便宜，缩放时也不会一圈一圈跳。
     */
    private fun drawSpeaking(canvas: Canvas, cx: Float, cy: Float, baseRadius: Float) {
        val breath = kotlin.math.sin(phase * 2 * Math.PI).toFloat()
        val radius = baseRadius * (0.84f + 0.14f * displayedLevel + 0.025f * breath)
        paint.reset()
        paint.isAntiAlias = true
        paint.shader = RadialGradient(
            cx, cy, radius,
            intArrayOf(colorLight, colorCore, colorDark, colorFade),
            floatArrayOf(0f, 0.48f, 0.78f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, radius, paint)
    }

    /** 思考：扫描渐变绕圆心慢转。 */
    private fun drawThinking(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        maxRadius: Float,
        baseRadius: Float
    ) {
        val radius = baseRadius * 0.84f
        paint.reset()
        paint.isAntiAlias = true
        paint.maskFilter = BlurMaskFilter(maxRadius * 0.12f, BlurMaskFilter.Blur.NORMAL)
        paint.shader = SweepGradient(
            cx, cy,
            intArrayOf(colorCore, colorDark, colorLight, colorCore),
            floatArrayOf(0f, 0.34f, 0.67f, 1f)
        )
        canvas.save()
        canvas.rotate(phase * 360f, cx, cy)
        canvas.drawCircle(cx, cy, radius, paint)
        canvas.restore()
    }

    private fun drawIdle(canvas: Canvas, cx: Float, cy: Float, maxRadius: Float, baseRadius: Float) {
        drawGlowingOrb(canvas, cx, cy, maxRadius, baseRadius * REST_SCALE)
    }

    private fun drawGlowingOrb(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        maxRadius: Float,
        radius: Float
    ) {
        paint.reset()
        paint.isAntiAlias = true
        paint.maskFilter = BlurMaskFilter(maxRadius * GLOW_RATIO, BlurMaskFilter.Blur.NORMAL)
        paint.shader = RadialGradient(
            cx, cy, radius,
            intArrayOf(colorLight, colorCore, colorDark),
            floatArrayOf(0f, 0.6f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, radius, paint)
    }

    /**
     * 流光球：圆润轮廓内有几团彩色柔光缓慢绕转、互相融合，外圈一层柔和光晕，左上高光做立体感。
     *
     * 丝滑的关键：
     * 1. 所有运动都用自己累加的时间 [auroraTime]，转速变化只影响之后的增量，不会让画面跳变；
     * 2. 状态能量、音量半径都按指数平滑过渡，切状态和音量抖动都不会突变；
     * 3. 着色器只在尺寸变化时创建，每帧只改矩阵，不分配对象；
     * 4. 轮廓只用低频形变，再用二次贝塞尔连接采样点，边缘保持圆润。
     */
    private fun drawAurora(canvas: Canvas, cx: Float, cy: Float, maxRadius: Float, baseRadius: Float) {
        ensureAuroraShaders(baseRadius, maxRadius)
        val now = System.nanoTime()
        val dt = if (lastAuroraNanos == 0L) 0f else ((now - lastAuroraNanos) / 1e9f).coerceIn(0f, 0.05f)
        lastAuroraNanos = now

        val targetEnergy = when (state) {
            AICallState.IDLE -> 0.2f
            AICallState.LISTENING -> 0.4f
            AICallState.THINKING -> 0.7f
            AICallState.SPEAKING -> 0.55f + 0.35f * displayedLevel
        }
        smoothEnergy += (targetEnergy - smoothEnergy) * (1f - kotlin.math.exp(-dt * 3f))
        val targetBoost = if (state == AICallState.SPEAKING) displayedLevel else 0f
        smoothBoost += (targetBoost - smoothBoost) * (1f - kotlin.math.exp(-dt * 10f))
        auroraTime += dt * (0.5f + 1.3f * smoothEnergy)
        val t = auroraTime

        val r = baseRadius * (0.86f + 0.018f * kotlin.math.sin(t * 1.6f) + 0.1f * smoothBoost)
        val scale = r / baseRadius

        // 外圈光晕：着色器按 GLOW_BASE 建，放大上限 1.2 倍，始终落在绘制圆内，不会被切出硬边
        val glowScale = 1f + 0.03f * (kotlin.math.sin(t * 1.2f) + 1f) + 0.12f * smoothBoost
        auroraMatrix.setScale(glowScale, glowScale, cx, cy)
        glowShader!!.setLocalMatrix(auroraMatrix)
        auroraPaint.shader = glowShader
        val glowAlpha = if (auroraOnDark) 0.8f + 0.2f * smoothEnergy else 0.55f + 0.35f * smoothEnergy
        auroraPaint.alpha = (glowAlpha * 255).toInt().coerceIn(0, 255)
        canvas.drawCircle(cx, cy, maxRadius, auroraPaint)
        auroraPaint.alpha = 255

        // 圆润轮廓：低频形变 + 二次贝塞尔平滑
        val amp = r * (0.01f + 0.03f * smoothEnergy)
        val n = AURORA_POINTS
        for (k in 0 until n) {
            val theta = k * (2f * Math.PI.toFloat() / n)
            val d = 0.6f * kotlin.math.sin(2f * theta + t * 1.3f) + 0.4f * kotlin.math.sin(3f * theta - t * 0.9f)
            val rr = r + amp * d
            auroraPoints[2 * k] = cx + rr * kotlin.math.cos(theta)
            auroraPoints[2 * k + 1] = cy + rr * kotlin.math.sin(theta)
        }
        auroraPath.rewind()
        val lx = auroraPoints[2 * (n - 1)]
        val ly = auroraPoints[2 * (n - 1) + 1]
        auroraPath.moveTo((lx + auroraPoints[0]) / 2f, (ly + auroraPoints[1]) / 2f)
        for (k in 0 until n) {
            val x = auroraPoints[2 * k]
            val y = auroraPoints[2 * k + 1]
            val nk = (k + 1) % n
            auroraPath.quadTo(x, y, (x + auroraPoints[2 * nk]) / 2f, (y + auroraPoints[2 * nk + 1]) / 2f)
        }
        auroraPath.close()

        // 在独立图层里：先画抗锯齿轮廓，再用 SRC_ATOP 把彩色柔光"染"进去，边缘不会出锯齿
        val bound = r * 1.15f
        val save = canvas.saveLayer(cx - bound, cy - bound, cx + bound, cy + bound, null)
        auroraPaint.shader = null
        auroraPaint.color = AURORA_BASE
        canvas.drawPath(auroraPath, auroraPaint)
        auroraPaint.xfermode = srcAtop
        for (i in blobShaders.indices) {
            val angle = t * BLOB_SPEED[i] + BLOB_PHASE[i]
            val orbit = r * (0.42f + 0.08f * kotlin.math.sin(t * 0.7f + i))
            auroraMatrix.setScale(scale, scale)
            auroraMatrix.postTranslate(cx + kotlin.math.cos(angle) * orbit, cy + kotlin.math.sin(angle) * orbit * 0.9f)
            blobShaders[i].setLocalMatrix(auroraMatrix)
            auroraPaint.shader = blobShaders[i]
            canvas.drawRect(cx - bound, cy - bound, cx + bound, cy + bound, auroraPaint)
        }
        auroraMatrix.setScale(scale, scale)
        auroraMatrix.postTranslate(cx - r * 0.3f, cy - r * 0.38f)
        highlightShader!!.setLocalMatrix(auroraMatrix)
        auroraPaint.shader = highlightShader
        canvas.drawRect(cx - bound, cy - bound, cx + bound, cy + bound, auroraPaint)
        auroraMatrix.setScale(scale, scale)
        auroraMatrix.postTranslate(cx + r * 0.38f, cy + r * 0.5f)
        shadeShader!!.setLocalMatrix(auroraMatrix)
        auroraPaint.shader = shadeShader
        canvas.drawRect(cx - bound, cy - bound, cx + bound, cy + bound, auroraPaint)
        auroraPaint.xfermode = null
        auroraPaint.shader = null
        canvas.restoreToCount(save)
    }

    /** 着色器都以原点为圆心创建，绘制时用矩阵缩放、平移；只在尺寸变化时重建。 */
    private fun ensureAuroraShaders(baseRadius: Float, maxRadius: Float) {
        if (glowShader != null && shaderBaseRadius == baseRadius && shaderMaxRadius == maxRadius) {
            return
        }
        shaderBaseRadius = baseRadius
        shaderMaxRadius = maxRadius
        val cx = width / 2f
        val cy = height / 2f
        // 渐变末端用同色的全透明，而不是 Color.TRANSPARENT（透明黑）：
        // 后者会让过渡带混进黑色，深色背景上看起来发灰发浑
        val inner = if (auroraOnDark) AURORA_GLOW_DARK else AURORA_GLOW
        val outer = if (auroraOnDark) AURORA_GLOW_OUTER_DARK else AURORA_GLOW_OUTER
        glowShader = RadialGradient(
            cx, cy, maxRadius * GLOW_BASE,
            // 球体边缘大约落在 0.8 处：在这之前保持饱满，之后用多段缓降做出柔和的外晕
            intArrayOf(
                withAlpha(inner, 0.9f),
                withAlpha(inner, 0.8f),
                withAlpha(inner, 0.6f),
                withAlpha(outer, 0.32f),
                withAlpha(outer, 0.12f),
                withAlpha(outer, 0f),
            ),
            floatArrayOf(0f, 0.6f, 0.78f, 0.87f, 0.94f, 1f),
            Shader.TileMode.CLAMP
        )
        blobShaders = Array(BLOB_COLORS.size) { i ->
            RadialGradient(
                0f, 0f, baseRadius * 0.95f,
                intArrayOf(BLOB_COLORS[i], withAlpha(BLOB_COLORS[i], 0.55f), withAlpha(BLOB_COLORS[i], 0f)),
                floatArrayOf(0f, 0.45f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        highlightShader = RadialGradient(
            0f, 0f, baseRadius * 0.75f,
            intArrayOf(0xB3FFFFFF.toInt(), 0x33FFFFFF, 0x00FFFFFF),
            floatArrayOf(0f, 0.45f, 1f),
            Shader.TileMode.CLAMP
        )
        shadeShader = RadialGradient(
            0f, 0f, baseRadius * 0.9f,
            intArrayOf(0x55100A40, 0x00100A40),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP
        )
    }

    private fun withAlpha(color: Int, alpha: Float): Int {
        return (color and 0x00FFFFFF) or ((alpha.coerceIn(0f, 1f) * 255).toInt() shl 24)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        glowShader = null
    }

    companion object {
        private const val AURORA_POINTS = 48
        private const val AURORA_BASE = 0xFF5B3CFF.toInt()
        private const val AURORA_GLOW = 0xFF7B5CFF.toInt()
        private const val AURORA_GLOW_OUTER = 0xFF2EC5FF.toInt()
        private const val AURORA_GLOW_DARK = 0xFF9B87FF.toInt()
        private const val AURORA_GLOW_OUTER_DARK = 0xFF5AD8FF.toInt()
        /** 光晕着色器半径占可绘制半径的比例，留出 1.2 倍放大余量 */
        private const val GLOW_BASE = 0.82f
        private val BLOB_COLORS = intArrayOf(
            0xFF2EC5FF.toInt(),
            0xFF6AF2C6.toInt(),
            0xFFFF6FD8.toInt(),
            0xFFB36BFF.toInt(),
        )
        private val BLOB_SPEED = floatArrayOf(0.9f, -0.7f, 0.6f, -1.1f)
        private val BLOB_PHASE = floatArrayOf(0f, 1.6f, 3.2f, 4.7f)
        /** 光晕向外弥散占用的半径比例 */
        private const val GLOW_RATIO = 0.22f
        /** 聆听呼吸的中点，IDLE 钉在这里，进页时和开始听是同一颗球 */
        private const val REST_SCALE = 0.85f
        /** 约 40ms 跟上突然变大的音量 */
        private const val LEVEL_ATTACK = 0.38f
        /** 约 150ms 收回，词缝里不会抖 */
        private const val LEVEL_RELEASE = 0.10f
    }
}
