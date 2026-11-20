package com.trtc.uikit.livekit.voicelounge.living.seat

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import android.text.TextUtils
import android.view.Choreographer
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import com.trtc.uikit.livekit.R
import com.trtc.uikit.livekit.common.theme.LiveTheme
import com.trtc.uikit.livekit.voicelounge.MATCH
import com.trtc.uikit.livekit.voicelounge.VoiceLoungeBag
import com.trtc.uikit.livekit.voicelounge.WRAP
import com.trtc.uikit.livekit.voicelounge.addTo
import com.trtc.uikit.livekit.voicelounge.dp
import com.trtc.uikit.livekit.voicelounge.frameParams
import com.trtc.uikit.livekit.voicelounge.linearParams
import com.trtc.uikit.livekit.voicelounge.living.VoiceLoungeLivingActions
import com.trtc.uikit.livekit.voicelounge.living.VoiceLoungeRole
import com.trtc.uikit.livekit.voicelounge.living.aitranscriber.VoiceLoungeAITranscriberStore
import com.trtc.uikit.livekit.voicelounge.living.aitranscriber.VoiceLoungeAITranscriberView
import com.trtc.uikit.livekit.voicelounge.roundedDrawable
import com.trtc.uikit.livekit.voicelounge.settings.VoiceLoungeSeatLayout
import com.trtc.uikit.livekit.voicelounge.settings.topic.VoiceLoungeTopicPanel
import com.trtc.uikit.livekit.voicelounge.voiceLoungeText
import com.trtc.uikit.livekit.voicelounge.whiteAlpha
import com.trtc.uikit.livekit.voicelounge.withAlpha
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

internal class VoiceLoungeSeatAreaView(
    context: Context,
    private val store: VoiceLoungeSeatStore,
    private val transcriberStore: VoiceLoungeAITranscriberStore,
    private val livingActions: VoiceLoungeLivingActions,
) : FrameLayout(context) {
    var onSendGift: ((userID: String, name: String) -> Unit)? = null

    private val tapHandler = VoiceLoungeSeatTapHandler(context, store)
    private val topicPanel by lazy {
        VoiceLoungeTopicPanel(context).apply { onConfirm = { livingActions.changeTopic(it) } }
    }
    private val bag = VoiceLoungeBag()
    private var layout: VoiceLoungeSeatLayout? = null
    private var lastTranscriberVisible = false
    private var seats: List<VoiceLoungeSeat> = emptyList()
    private val seatItems = HashMap<Int, VoiceLoungeSeatView>()

    private val welcomeBar = FrameLayout(context)
    private val welcomeLabel = voiceLoungeText(context, LiveTheme.TextRole.CAPTION, LiveTheme.TextTone.SECONDARY)
    private val transcriberView = VoiceLoungeAITranscriberView(context, transcriberStore)
    private val ecgLineView = EcgLineView(context).apply { visibility = View.GONE }
    private val hostHalo = HaloEmitterView(context).apply { visibility = View.GONE }
    private val cohostHalo = HaloEmitterView(context).apply { visibility = View.GONE }
    private var columnView: View? = null
    private var columnMaxHeight = Int.MAX_VALUE
    private var columnFullHeight = 0
    private var oneV8xAreaHeight = 0

    val contentBottom: Int
        get() = if (layout == VoiceLoungeSeatLayout.ONE_V8X && oneV8xAreaHeight > 0) oneV8xAreaHeight else height

    fun updateColumnLimit(maxHeight: Int) {
        if (maxHeight == columnMaxHeight) return
        columnMaxHeight = maxHeight
        applyColumnLimit()
    }

    private fun applyColumnLimit() {
        if (layout != VoiceLoungeSeatLayout.ONE_V8X) return
        val column = columnView ?: return
        val target = max(oneV8xAreaHeight, min(columnFullHeight, columnMaxHeight.coerceAtLeast(0)))
        val params = column.layoutParams ?: return
        if (params.height == target) return
        params.height = target
        column.layoutParams = params
    }

    init {
        clipChildren = false
        clipToPadding = false
        welcomeBar.background = roundedDrawable(whiteAlpha(0.07f), dp(14).toFloat())
        welcomeBar.minimumHeight = dp(30)
        welcomeLabel.maxLines = 2
        welcomeLabel.ellipsize = TextUtils.TruncateAt.END
        welcomeBar.addView(
            welcomeLabel,
            frameParams(
                MATCH, WRAP, Gravity.CENTER_VERTICAL,
                start = dp(12), top = dp(7), end = dp(12), bottom = dp(7),
            ),
        )
        if (store.role == VoiceLoungeRole.HOST) welcomeBar.setOnClickListener { tapWelcome() }
        tapHandler.onSendGift = { userID, name -> onSendGift?.invoke(userID, name) }
        tapHandler.onConfirm = { title, message, confirmTitle, action ->
            livingActions.confirm(title, message, confirmTitle, action)
        }
        transcriberView.onTap = { livingActions.showTranscriberPanel() }
        lastTranscriberVisible = transcriberStore.isVisible
        render()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (bag.isEmpty) {
            store.changes.subscribe { render() }.addTo(bag)
            transcriberStore.changes.subscribe { onTranscriberChanged() }.addTo(bag)
        }
        render()
    }

    private fun onTranscriberChanged() {
        val nowVisible = transcriberStore.isVisible
        if (nowVisible != lastTranscriberVisible) {
            lastTranscriberVisible = nowVisible
            if (layout == VoiceLoungeSeatLayout.ONE_V8) {
                rebuild(VoiceLoungeSeatLayout.ONE_V8)
                requestLayout()
            }
        }
    }

    override fun onDetachedFromWindow() {
        bag.clear()
        dismissPanels()
        super.onDetachedFromWindow()
    }

    fun dismissPanels() {
        tapHandler.dismissAll()
        if (topicPanel.isShowing) topicPanel.dismiss()
    }

    private fun tapWelcome() {
        if (store.role != VoiceLoungeRole.HOST) return
        topicPanel.show(store.settings.topic, store.skin)
    }

    private fun renderWelcome() {
        val topic = store.settings.topic
        welcomeLabel.text = if (topic.isEmpty()) {
            context.getString(R.string.livekit_voiceroom_room_welcome)
        } else {
            context.getString(R.string.livekit_voiceroom_room_welcome_topic_format, topic)
        }
    }

    private fun render() {
        val layout = store.layout
        val needsRebuild = this.layout != layout || seatItems.isEmpty()
        this.layout = layout
        seats = store.seats
        renderWelcome()
        if (needsRebuild) rebuild(layout) else updateSeatContents()
        ecgLineView.setActive(layout == VoiceLoungeSeatLayout.DUO)
        updateHaloActivities()
    }

    private fun hasHalo(seatIndex: Int): Boolean =
        layout == VoiceLoungeSeatLayout.DUO && (seatIndex == 0 || seatIndex == 1)

    private fun haloActivity(seatIndex: Int): HaloEmitterView.Activity {
        val seat = seats.firstOrNull { it.index == seatIndex } ?: return HaloEmitterView.Activity.NONE
        if (seat.state != VoiceLoungeSeat.State.OCCUPIED) return HaloEmitterView.Activity.NONE
        return if (seat.isSpeaking) HaloEmitterView.Activity.SPEAKING else HaloEmitterView.Activity.NONE
    }

    private fun updateHaloActivities() {
        hostHalo.setActivity(if (hasHalo(0)) haloActivity(0) else HaloEmitterView.Activity.NONE)
        cohostHalo.setActivity(if (hasHalo(1)) haloActivity(1) else HaloEmitterView.Activity.NONE)
    }

    private fun rebuild(layout: VoiceLoungeSeatLayout) {
        seatItems.clear()
        removeAllViews()
        listOf<View>(welcomeBar, transcriberView, ecgLineView, hostHalo, cohostHalo).forEach {
            (it.parent as? ViewGroup)?.removeView(it)
        }
        ecgLineView.visibility = View.GONE
        hostHalo.visibility = View.GONE
        cohostHalo.visibility = View.GONE
        columnView = null
        oneV8xAreaHeight = 0

        for (seat in seats) {
            val isColumn = layout == VoiceLoungeSeatLayout.ONE_V8X && seat.role == VoiceLoungeSeat.Role.GUEST
            val item = VoiceLoungeSeatView(
                context,
                avatarSize = seatAvatarSize(seat.role, layout),
                emptyTitle = emptyTitle(seat.role),
                nameGap = if (isColumn) 0f else VoiceLoungeSeatView.DEFAULT_NAME_GAP,
                isCompact = isColumn,
                showsWave = !hasHalo(seat.index),
            )
            item.onTap = { tapHandler.handleTap(it) }
            seatItems[seat.index] = item
        }
        transcriberView.fitsContent = layout == VoiceLoungeSeatLayout.ONE_V8X
        when (layout) {
            VoiceLoungeSeatLayout.ONE_V8 -> layoutFocusGrid(cohost = false)
            VoiceLoungeSeatLayout.TWO_V8 -> layoutFocusGrid(cohost = true)
            VoiceLoungeSeatLayout.ONE_V8X -> layoutOneV8x()
            VoiceLoungeSeatLayout.DUO -> layoutDuo()
        }
        updateSeatContents()
    }

    private fun updateSeatContents() = seats.forEach { seatItems[it.index]?.configure(it) }

    private fun seatAvatarSize(role: VoiceLoungeSeat.Role, layout: VoiceLoungeSeatLayout): Float = when {
        layout == VoiceLoungeSeatLayout.DUO && role != VoiceLoungeSeat.Role.COHOST -> DUO_AVATAR
        layout == VoiceLoungeSeatLayout.ONE_V8X && role == VoiceLoungeSeat.Role.GUEST -> COLUMN_AVATAR
        role == VoiceLoungeSeat.Role.HOST || role == VoiceLoungeSeat.Role.COHOST -> VoiceLoungeSeatStyle.FOCUS_AVATAR
        else -> VoiceLoungeSeatStyle.GUEST_AVATAR
    }

    private fun emptyTitle(role: VoiceLoungeSeat.Role): String = context.getString(
        when (role) {
            VoiceLoungeSeat.Role.HOST -> R.string.livekit_voiceroom_seat_host
            VoiceLoungeSeat.Role.COHOST -> R.string.livekit_voiceroom_layout_cohost
            VoiceLoungeSeat.Role.GUEST -> if (store.role == VoiceLoungeRole.HOST) R.string.livekit_voiceroom_seat_invite
            else R.string.livekit_voiceroom_seat_join
        },
    )

    private val focusWidth: Int get() = dp(VoiceLoungeSeatStyle.FOCUS_AVATAR + 16)

    private fun focusHeight(avatar: Float = VoiceLoungeSeatStyle.FOCUS_AVATAR): Int =
        VoiceLoungeSeatView.height(context, avatar)

    private fun layoutFocusGrid(cohost: Boolean) {
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
        }
        addView(column, frameParams(MATCH, WRAP))

        val topRow = FrameLayout(context).apply { clipChildren = false }
        column.addView(topRow, linearParams(MATCH, dp(VoiceLoungeAITranscriberView.HEIGHT)))
        val host = seatItems[0] ?: return
        val focusHeight = focusHeight()
        if (cohost) {
            val mate = seatItems[1] ?: return
            topRow.addView(host, frameParams(focusWidth, focusHeight, Gravity.CENTER_VERTICAL, start = dp(6)))
            topRow.addView(
                mate, frameParams(focusWidth, focusHeight, Gravity.CENTER_VERTICAL or Gravity.END, end = dp(6))
            )
            val inset = dp(6) + focusWidth + dp(8)
            topRow.addView(transcriberView, frameParams(MATCH, MATCH, start = inset, end = inset))
        } else {
            val transcriberVisible = transcriberStore.isVisible
            if (transcriberVisible) {
                topRow.addView(host, frameParams(focusWidth, focusHeight, Gravity.CENTER_VERTICAL, start = dp(10)))
                topRow.addView(
                    transcriberView, frameParams(MATCH, MATCH, start = dp(10) + focusWidth + dp(14), end = dp(6))
                )
            } else {
                topRow.addView(
                    host, frameParams(focusWidth, focusHeight, Gravity.CENTER),
                )
            }
        }

        column.addView(welcomeBar, linearParams(MATCH, WRAP, top = dp(12)))

        val rowHeight = VoiceLoungeSeatView.height(context, VoiceLoungeSeatStyle.GUEST_AVATAR)
        val guestStart = if (cohost) 2 else 1
        for (row in 0 until 2) {
            val rowView = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                clipChildren = false
            }
            column.addView(rowView, linearParams(MATCH, rowHeight, top = if (row == 0) dp(14) else dp(ROW_GAP)))
            for (col in 0 until 4) {
                val item = seatItems[guestStart + row * 4 + col]
                rowView.addView(item ?: View(context), linearParams(0, rowHeight, 1f))
            }
        }
    }

    private fun layoutOneV8x() {
        val focus = seatItems[0] ?: return
        val focusHeight = focusHeight()
        val transcriberTop = dp(COLUMN_FOCUS_TOP) + focusHeight + dp(14)
        val areaHeight = transcriberTop + dp(VoiceLoungeAITranscriberView.HEIGHT)
        val columnWidth = dp(COLUMN_WIDTH)
        val cell = VoiceLoungeSeatView.height(context, COLUMN_AVATAR, 0f)
        oneV8xAreaHeight = areaHeight
        columnFullHeight = cell * 8

        val scrollView = ScrollView(context).apply {
            isVerticalScrollBarEnabled = false
            overScrollMode = OVER_SCROLL_NEVER
        }
        val list = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
        }
        for (i in 1..8) {
            val item = seatItems[i] ?: continue
            list.addView(item, linearParams(MATCH, cell))
        }
        scrollView.addView(list, FrameLayout.LayoutParams(MATCH, WRAP))
        val columnClip = FrameLayout(context).apply { translationX = dp(4).toFloat() }
        columnClip.addView(scrollView, frameParams(MATCH, MATCH))
        addView(columnClip, frameParams(columnWidth, areaHeight, Gravity.TOP or Gravity.END))
        columnView = columnClip
        applyColumnLimit()

        val topRow = FrameLayout(context).apply { clipChildren = false }
        addView(topRow, frameParams(MATCH, areaHeight, Gravity.TOP, end = columnWidth - dp(4)))
        topRow.addView(
            focus,
            frameParams(
                dp(VoiceLoungeSeatStyle.FOCUS_AVATAR + 40), focusHeight, Gravity.TOP or Gravity.CENTER_HORIZONTAL,
                top = dp(COLUMN_FOCUS_TOP),
            ),
        )
        topRow.addView(
            transcriberView,
            frameParams(
                MATCH, dp(VoiceLoungeAITranscriberView.HEIGHT), Gravity.TOP,
                start = dp(14), top = transcriberTop, end = dp(14),
            ),
        )
    }

    private fun layoutDuo() {
        val host = seatItems[0] ?: return
        val mate = seatItems[1] ?: return
        val itemWidth = dp(DUO_ITEM_WIDTH)
        val itemHeight = VoiceLoungeSeatView.height(context, DUO_AVATAR)
        val offset = (dp(DUO_HALF_GAP) + itemWidth / 2).toFloat()
        val top = dp(DUO_TOP)

        val haloExtent = dp(HaloEmitterView.MAX_SPREAD_DP + DUO_AVATAR / 2) * 2
        val haloTop = top + dp(DUO_AVATAR + 8) / 2 - haloExtent / 2
        addView(
            hostHalo,
            frameParams(haloExtent, haloExtent, Gravity.TOP or Gravity.CENTER_HORIZONTAL, top = haloTop),
        )
        hostHalo.translationX = -offset
        hostHalo.setAvatarRadius(dp(DUO_AVATAR / 2).toFloat())
        hostHalo.visibility = View.VISIBLE

        addView(
            cohostHalo,
            frameParams(haloExtent, haloExtent, Gravity.TOP or Gravity.CENTER_HORIZONTAL, top = haloTop),
        )
        cohostHalo.translationX = offset
        cohostHalo.setAvatarRadius(dp(DUO_AVATAR / 2).toFloat())
        cohostHalo.visibility = View.VISIBLE

        addView(host, frameParams(itemWidth, itemHeight, Gravity.TOP or Gravity.CENTER_HORIZONTAL, top = top))
        host.translationX = -offset
        addView(mate, frameParams(itemWidth, itemHeight, Gravity.TOP or Gravity.CENTER_HORIZONTAL, top = top))
        mate.translationX = offset

        val ecgHeight = dp(ECG_HEIGHT)
        val ecgTop = top + dp((DUO_AVATAR + 8f) / 2f) - ecgHeight / 2
        addView(
            ecgLineView,
            frameParams(MATCH, ecgHeight, Gravity.TOP or Gravity.CENTER_HORIZONTAL, top = ecgTop),
        )
        ecgLineView.setGeometry(
            avatarOffsetPx = offset,
            avatarRadiusPx = dp(DUO_AVATAR / 2).toFloat(),
            lineGapPx = dp(DUO_LINE_GAP).toFloat(),
        )
        ecgLineView.visibility = View.VISIBLE

        addView(
            transcriberView,
            frameParams(
                MATCH, dp(VoiceLoungeAITranscriberView.HEIGHT), Gravity.TOP,
                start = dp(6), top = top + itemHeight + dp(18), end = dp(6),
            ),
        )
    }

    private companion object {
        const val COLUMN_AVATAR = 40f
        const val COLUMN_WIDTH = 64
        const val COLUMN_FOCUS_TOP = 100
        const val DUO_TOP = 36
        const val DUO_AVATAR = 72f
        const val DUO_ITEM_WIDTH = 96f
        const val DUO_HALF_GAP = 40f
        const val DUO_LINE_GAP = 10f
        const val ROW_GAP = 10
        const val ECG_HEIGHT = 80
    }
}

private class EcgLineView(context: Context) : View(context) {
    private val baseLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.MITER
        strokeWidth = context.dp(1.7f).toFloat()
        color = baseLineColor.withAlpha(0.5f)
    }
    private val sweepPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val baseLinePath = Path()
    private val segmentPath = Path()

    private var avatarOffsetPx = context.dp(110f).toFloat()
    private var avatarRadiusPx = context.dp(36f).toFloat()
    private var lineGapPx = context.dp(10f).toFloat()

    private var pathPoints: List<PointF> = emptyList()
    private var pathLengths: FloatArray = FloatArray(0)
    private var totalLength = 0f

    private val speedPx = context.dp(120f).toFloat()
    private val amplitudePx = context.dp(AMPLITUDE_DP).toFloat()
    private val trailWidthPx = context.dp(TRAIL_WIDTH_DP).toFloat()
    private val coreWidthPx = context.dp(CORE_WIDTH_DP).toFloat()
    private val glowWidthsPx = GLOW_LAYERS.map { context.dp(it.widthDp).toFloat() }

    private var startNanos = 0L
    private var isActive = false
    private var isAnimating = false

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!isAnimating) return
            invalidate()
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    fun setActive(active: Boolean) {
        if (isActive == active) return
        isActive = active
        if (active) startAnimating() else stopAnimating()
    }

    fun setGeometry(avatarOffsetPx: Float, avatarRadiusPx: Float, lineGapPx: Float) {
        this.avatarOffsetPx = avatarOffsetPx
        this.avatarRadiusPx = avatarRadiusPx
        this.lineGapPx = lineGapPx
        rebuildPath()
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        rebuildPath()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (isActive) startAnimating()
    }

    override fun onDetachedFromWindow() {
        stopAnimating()
        super.onDetachedFromWindow()
    }

    private fun startAnimating() {
        if (isAnimating || !isAttachedToWindow) return
        isAnimating = true
        startNanos = System.nanoTime()
        Choreographer.getInstance().postFrameCallback(frameCallback)
    }

    private fun stopAnimating() {
        if (!isAnimating) return
        isAnimating = false
        Choreographer.getInstance().removeFrameCallback(frameCallback)
    }

    private fun rebuildPath() {
        if (width <= 0 || height <= 0) return
        val cx = width / 2f
        val cy = height / 2f
        val xA = cx - avatarOffsetPx + avatarRadiusPx + lineGapPx
        val xB = cx + avatarOffsetPx - avatarRadiusPx - lineGapPx
        val points = VERTICES.map { (nx, ny) ->
            PointF(xA + (xB - xA) * nx, cy + ny * amplitudePx)
        }
        val lengths = FloatArray(points.size)
        for (i in 1 until points.size) {
            val dx = points[i].x - points[i - 1].x
            val dy = points[i].y - points[i - 1].y
            lengths[i] = lengths[i - 1] + kotlin.math.sqrt(dx * dx + dy * dy)
        }
        pathPoints = points
        pathLengths = lengths
        totalLength = lengths.lastOrNull() ?: 0f
        baseLinePath.reset()
        if (points.isNotEmpty()) {
            baseLinePath.moveTo(points[0].x, points[0].y)
            for (i in 1 until points.size) baseLinePath.lineTo(points[i].x, points[i].y)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (pathPoints.size < 2 || totalLength <= 0f) return
        canvas.drawPath(baseLinePath, baseLinePaint)
        val elapsed = if (startNanos != 0L) (System.nanoTime() - startNanos) / 1_000_000_000f else 0f
        val head = (elapsed * speedPx) % totalLength
        val piece = totalLength * TRAIL_RATIO / TRAIL_PIECES
        val cap = sweepPaint.strokeCap
        for (k in 0 until TRAIL_PIECES) {
            var s0 = head - piece * (TRAIL_PIECES - k)
            while (s0 < 0f) s0 += totalLength
            val s1 = s0 + piece
            val alpha = min(1f, (k + 1).toFloat() / (TRAIL_PIECES * 0.4f))
            val mid = (s0 + piece / 2f) % totalLength / totalLength
            val trailColor = gradientColor(mid)
            val coreColor = lift(trailColor, CORE_LIFT)
            drawSweepStroke(canvas, s0, s1, alpha, trailColor, coreColor)
        }
        sweepPaint.strokeCap = cap
    }

    private fun drawSweepStroke(
        canvas: Canvas, s0: Float, s1: Float, alpha: Float, trailColor: Int, coreColor: Int,
    ) {
        for (i in GLOW_LAYERS.indices) {
            strokeSegment(canvas, s0, s1, glowWidthsPx[i], trailColor, alpha * GLOW_LAYERS[i].alphaScale)
        }
        strokeSegment(canvas, s0, s1, trailWidthPx, trailColor, alpha)
        strokeSegment(canvas, s0, s1, coreWidthPx, coreColor, alpha)
    }

    private fun strokeSegment(
        canvas: Canvas, s0: Float, s1: Float, width: Float, color: Int, alpha: Float,
    ) {
        if (alpha <= 0f) return
        sweepPaint.strokeWidth = width
        sweepPaint.color = color.withAlpha(alpha.coerceIn(0f, 1f))
        segmentPath.reset()
        if (s1 <= totalLength) {
            addSubpath(s0, s1)
        } else {
            addSubpath(s0, totalLength)
            addSubpath(0f, s1 - totalLength)
        }
        canvas.drawPath(segmentPath, sweepPaint)
    }

    private fun addSubpath(s0: Float, s1: Float) {
        val startPoint = pointAt(s0)
        segmentPath.moveTo(startPoint.x, startPoint.y)
        var i = segmentIndex(s0) + 1
        while (i < pathLengths.size && pathLengths[i] < s1) {
            segmentPath.lineTo(pathPoints[i].x, pathPoints[i].y)
            i++
        }
        val endPoint = pointAt(s1)
        segmentPath.lineTo(endPoint.x, endPoint.y)
    }

    private fun segmentIndex(s: Float): Int {
        var low = 0
        var high = pathLengths.size - 2
        while (low < high) {
            val mid = (low + high + 1) / 2
            if (pathLengths[mid] <= s) low = mid else high = mid - 1
        }
        return low
    }

    private fun pointAt(s: Float): PointF {
        val i = segmentIndex(s)
        val span = pathLengths[i + 1] - pathLengths[i]
        val f = if (span > 0f) (s - pathLengths[i]) / span else 0f
        val a = pathPoints[i]
        val b = pathPoints[i + 1]
        return PointF(a.x + (b.x - a.x) * f, a.y + (b.y - a.y) * f)
    }

    private fun gradientColor(f: Float): Int {
        val clamped = f.coerceIn(0f, 1f)
        val x = clamped * (LIGHT_STOPS.size - 1)
        val i = min(x.toInt(), LIGHT_STOPS.size - 2)
        val m = x - i
        val a = LIGHT_STOPS[i]
        val b = LIGHT_STOPS[i + 1]
        val r = a[0] + (b[0] - a[0]) * m
        val g = a[1] + (b[1] - a[1]) * m
        val bl = a[2] + (b[2] - a[2]) * m
        return Color.rgb((r * 255).toInt(), (g * 255).toInt(), (bl * 255).toInt())
    }

    private fun lift(color: Int, k: Float): Int {
        val r = Color.red(color) / 255f
        val g = Color.green(color) / 255f
        val b = Color.blue(color) / 255f
        return Color.rgb(
            ((r + (1f - r) * k) * 255).toInt(),
            ((g + (1f - g) * k) * 255).toInt(),
            ((b + (1f - b) * k) * 255).toInt(),
        )
    }

    private data class Glow(val widthDp: Float, val alphaScale: Float)

    private companion object {
        const val TRAIL_RATIO = 0.3f
        const val TRAIL_PIECES = 24
        const val AMPLITUDE_DP = 24f
        const val TRAIL_WIDTH_DP = 2f
        const val CORE_WIDTH_DP = 0.9f
        const val CORE_LIFT = 0.5f
        val GLOW_LAYERS = listOf(Glow(6f, 0.10f), Glow(4.2f, 0.20f), Glow(2.8f, 0.45f))
        val LIGHT_STOPS = arrayOf(floatArrayOf(1.0f, 0.525f, 0.827f), floatArrayOf(0.663f, 0.545f, 1.0f))
        val baseLineColor: Int = Color.rgb((0.80f * 255).toInt(), (0.63f * 255).toInt(), (0.95f * 255).toInt())
        val VERTICES = arrayOf(
            0f to 0f, 0.15f to 0f, 0.18f to -0.15f, 0.22f to 0f, 0.30f to 0f, 0.315f to 0.14f, 0.389f to -1f,
            0.438f to 0.72f, 0.475f to 0f, 0.61f to 0f, 0.655f to -0.19f, 0.70f to 0f, 0.855f to -0.22f,
            0.89f to 0f, 1f to 0f,
        )
    }
}

private class HaloEmitterView(context: Context) : View(context) {
    enum class Activity { NONE, SPEAKING }

    private val particles = ArrayList<Particle>()
    private val particlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val particleRect = RectF()

    private var avatarRadiusPx = context.dp(36f).toFloat()
    private var activity = Activity.NONE
    private var emitCarry = 0f
    private var lastNanos = 0L
    private var isAnimating = false

    private val sizeMin = context.dp(2.4f).toFloat()
    private val sizeMax = context.dp(3.8f).toFloat()
    private val speedMin = context.dp(14f).toFloat()
    private val speedMax = context.dp(22f).toFloat()
    private val insetMin = context.dp(3f).toFloat()
    private val insetMax = context.dp(6f).toFloat()
    private val spreadPx = context.dp(MAX_SPREAD_DP).toFloat()

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!isAnimating) return
            tick(frameTimeNanos)
            if (isAnimating) Choreographer.getInstance().postFrameCallback(this)
        }
    }

    fun setAvatarRadius(radiusPx: Float) {
        if (kotlin.math.abs(radiusPx - avatarRadiusPx) < 0.5f) return
        avatarRadiusPx = radiusPx
    }

    fun setActivity(next: Activity) {
        if (activity == next) return
        activity = next
        if (next == Activity.NONE) emitCarry = 0f
        ensureAnimating()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (activity != Activity.NONE || particles.isNotEmpty()) ensureAnimating()
    }

    override fun onDetachedFromWindow() {
        stopAnimating()
        super.onDetachedFromWindow()
    }

    private fun ensureAnimating() {
        if (!isAttachedToWindow) return
        if (activity == Activity.NONE && particles.isEmpty()) {
            stopAnimating()
            return
        }
        if (isAnimating) return
        isAnimating = true
        lastNanos = 0L
        Choreographer.getInstance().postFrameCallback(frameCallback)
    }

    private fun stopAnimating() {
        if (!isAnimating) return
        isAnimating = false
        Choreographer.getInstance().removeFrameCallback(frameCallback)
    }

    private fun tick(frameTimeNanos: Long) {
        val dt = if (lastNanos == 0L) 0f else min((frameTimeNanos - lastNanos) / 1_000_000_000f, 0.05f)
        lastNanos = frameTimeNanos
        update(dt)
        invalidate()
        if (activity == Activity.NONE && particles.isEmpty()) stopAnimating()
    }

    private fun update(dt: Float) {
        if (dt <= 0f) return
        val it = particles.iterator()
        while (it.hasNext()) {
            val p = it.next()
            p.age += dt
            if (p.age >= p.life) {
                it.remove()
                continue
            }
            p.radius += p.speed * dt
            p.angle += p.drift * dt
            if (p.radius - p.startRadius >= p.spread) it.remove()
        }
        if (activity != Activity.SPEAKING) return
        emitCarry += dt * SPEAKING_RATE_HZ
        while (emitCarry >= 1f) {
            emitCarry -= 1f
            particles.add(spawnParticle())
        }
    }

    private fun spawnParticle(): Particle {
        val mix = Random.nextFloat()
        val r = PINK[0] + (PURPLE[0] - PINK[0]) * mix
        val g = PINK[1] + (PURPLE[1] - PINK[1]) * mix
        val b = PINK[2] + (PURPLE[2] - PINK[2]) * mix
        val color = Color.rgb((r * 255).toInt(), (g * 255).toInt(), (b * 255).toInt())
        val baseAlpha = 0.85f + 0.15f * Random.nextFloat()
        val startRadius = avatarRadiusPx + insetMin + (insetMax - insetMin) * Random.nextFloat()
        return Particle(
            angle = Random.nextFloat() * (2f * Math.PI.toFloat()),
            startRadius = startRadius,
            radius = startRadius,
            spread = spreadPx,
            speed = speedMin + (speedMax - speedMin) * Random.nextFloat(),
            drift = -0.175f + 0.35f * Random.nextFloat(),
            age = 0f,
            life = 1.1f + 0.5f * Random.nextFloat(),
            size = sizeMin + (sizeMax - sizeMin) * Random.nextFloat(),
            color = color,
            baseAlpha = baseAlpha,
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (particles.isEmpty()) return
        val cx = width / 2f
        val cy = height / 2f
        for (p in particles) {
            val k = max(p.age / p.life, (p.radius - p.startRadius) / p.spread)
            val fade = if (k < 0.18f) k / 0.18f else 1f - (k - 0.18f) / 0.82f
            if (fade <= 0f) continue
            val x = cx + cos(p.angle) * p.radius
            val y = cy + sin(p.angle) * p.radius
            particlePaint.color = p.color.withAlpha((p.baseAlpha * fade).coerceIn(0f, 1f))
            particleRect.set(x - p.size / 2f, y - p.size / 2f, x + p.size / 2f, y + p.size / 2f)
            canvas.drawOval(particleRect, particlePaint)
        }
    }

    private class Particle(
        var angle: Float,
        val startRadius: Float,
        var radius: Float,
        val spread: Float,
        val speed: Float,
        val drift: Float,
        var age: Float,
        val life: Float,
        val size: Float,
        val color: Int,
        val baseAlpha: Float,
    )

    companion object {
        const val MAX_SPREAD_DP = 26f
        private const val SPEAKING_RATE_HZ = 28f
        private val PINK = floatArrayOf(1f, 0.525f, 0.827f)
        private val PURPLE = floatArrayOf(0.663f, 0.545f, 1f)
    }
}
