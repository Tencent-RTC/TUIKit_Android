package com.trtc.uikit.roomkit.inroom.features.roomview

import android.content.Context
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.ViewGroup
import com.trtc.uikit.roomkit.inroom.features.roomview.RoomVideoLayoutCalculator.FloatingSide
import com.trtc.uikit.roomkit.inroom.features.roomview.RoomVideoLayoutCalculator.FloatingWindowPosition
import com.trtc.uikit.roomkit.inroom.features.roomview.RoomVideoLayoutCalculator.LayoutMode
import com.trtc.uikit.roomkit.inroom.features.roomview.RoomVideoLayoutCalculator.VideoPageLayout
import io.trtc.tuikit.atomicxcore.api.device.NetworkInfo
import kotlin.math.roundToInt

class RoomVideoPageView(context: Context) : ViewGroup(context) {

    private val participantViews = linkedMapOf<String, RoomParticipantItemView>()
    private var pageLayout: VideoPageLayout? = null
    private var isPlaying = false
    private var floatingWindowPosition = FloatingWindowPosition()
    private var onFloatingWindowPositionChange: ((FloatingWindowPosition) -> Unit)? = null

    private val touchSlopSquared: Int
    private var isFloatingWindowPressed = false
    private var isFloatingWindowDragging = false
    private var pressX = 0f
    private var pressY = 0f
    private var dragStartLeft = 0f
    private var dragStartTop = 0f

    init {
        clipChildren = true
        clipToPadding = true
        val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
        touchSlopSquared = touchSlop * touchSlop
    }

    fun bind(
        page: VideoPageLayout,
        volumes: Map<String, Int>,
        floatingWindowPosition: FloatingWindowPosition,
        onFloatingWindowPositionChange: (FloatingWindowPosition) -> Unit,
        networkQualities: Map<String, NetworkInfo> = emptyMap(),
    ) {
        val layoutChanged = pageLayout?.hasSameLayout(page) != true
        pageLayout = page
        if (!isFloatingWindowDragging) this.floatingWindowPosition = floatingWindowPosition
        this.onFloatingWindowPositionChange = onFloatingWindowPositionChange
        removeUnusedItems(page)

        page.participants.forEachIndexed { index, participant ->
            val itemKey = page.itemKeyAt(index)
            val itemView = participantViews.getOrPut(itemKey) {
                RoomParticipantItemView(context).also { addView(it) }
            }
            itemView.bind(
                participant = participant,
                streamType = page.streamTypeAt(index),
                volume = volumes[participant.userID] ?: 0,
                networkQuality = networkQualities[participant.userID]?.quality,
            )
            itemView.setPlaying(isPlaying)
            if (layoutChanged) itemView.bringToFront()
        }
        if (layoutChanged) requestLayout()
    }

    fun setPlaying(value: Boolean) {
        if (isPlaying == value) return
        isPlaying = value
        participantViews.values.forEach { it.setPlaying(value) }
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                isFloatingWindowPressed = isFloatingWindowHit(event.x, event.y)
                pressX = event.x
                pressY = event.y
            }

            MotionEvent.ACTION_MOVE -> {
                if (!isFloatingWindowPressed || isFloatingWindowDragging) return false
                val dx = event.x - pressX
                val dy = event.y - pressY
                if (dx * dx + dy * dy < touchSlopSquared) return false
                parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> isFloatingWindowPressed = false
        }
        return false
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isFloatingWindowPressed) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (floatingWindowView() == null) return false
                parent?.requestDisallowInterceptTouchEvent(true)
                pressX = event.x
                pressY = event.y
            }

            MotionEvent.ACTION_MOVE -> {
                if (!isFloatingWindowDragging) {
                    val dx = event.x - pressX
                    val dy = event.y - pressY
                    if (dx * dx + dy * dy < touchSlopSquared) return true
                    isFloatingWindowDragging = true
                    val floatingView = floatingWindowView() ?: return true
                    dragStartLeft = floatingView.left + floatingView.translationX
                    dragStartTop = floatingView.top + floatingView.translationY
                }
                moveFloatingWindowTo(
                    left = dragStartLeft + event.x - pressX,
                    top = dragStartTop + event.y - pressY,
                )
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                if (isFloatingWindowDragging) settleFloatingWindow()
                isFloatingWindowDragging = false
                isFloatingWindowPressed = false
            }
        }
        return true
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        setMeasuredDimension(width, height)
        val page = pageLayout ?: return
        val density = resources.displayMetrics.density
        val bounds = RoomVideoLayoutCalculator.calculateBounds(
            page, width / density, height / density, floatingWindowPosition,
        )
        bounds.forEachIndexed { index, itemBounds ->
            val itemView = participantViews[page.itemKeyAt(index)] ?: return@forEachIndexed
            val itemWidthPx = (itemBounds.width * density).roundToInt()
            val itemHeightPx = (itemBounds.height * density).roundToInt()
            itemView.measure(
                MeasureSpec.makeMeasureSpec(itemWidthPx, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(itemHeightPx, MeasureSpec.EXACTLY),
            )
        }
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val page = pageLayout ?: return
        val density = resources.displayMetrics.density
        val bounds = RoomVideoLayoutCalculator.calculateBounds(
            page, width / density, height / density, floatingWindowPosition,
        )
        bounds.forEachIndexed { index, itemBounds ->
            val itemView = participantViews[page.itemKeyAt(index)] ?: return@forEachIndexed
            val leftPx = (itemBounds.x * density).roundToInt()
            val topPx = (itemBounds.y * density).roundToInt()
            itemView.layout(leftPx, topPx, leftPx + itemView.measuredWidth, topPx + itemView.measuredHeight)
        }
    }

    override fun onDetachedFromWindow() {
        setPlaying(false)
        super.onDetachedFromWindow()
    }

    override fun generateDefaultLayoutParams(): LayoutParams =
        LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)

    private fun removeUnusedItems(page: VideoPageLayout) {
        val itemKeys = page.participants.indices.map { page.itemKeyAt(it) }.toSet()
        val removedKeys = participantViews.keys.filter { it !in itemKeys }
        for (key in removedKeys) {
            val itemView = participantViews.remove(key) ?: continue
            itemView.setPlaying(false)
            removeView(itemView)
        }
    }

    private fun floatingWindowView(): RoomParticipantItemView? {
        val page = pageLayout ?: return null
        if (page.mode != LayoutMode.SCREEN_SHARE || page.participants.size <= 1) return null
        return participantViews[page.itemKeyAt(1)]
    }

    private fun isFloatingWindowHit(x: Float, y: Float): Boolean {
        val view = floatingWindowView() ?: return false
        val left = view.left + view.translationX
        val top = view.top + view.translationY
        return x >= left && x <= left + view.width && y >= top && y <= top + view.height
    }

    private fun moveFloatingWindowTo(left: Float, top: Float) {
        val view = floatingWindowView() ?: return
        val inset = floatingInsetPx()
        val maxLeft = (width - view.width - inset).coerceAtLeast(inset)
        val maxTop = (height - view.height - inset).coerceAtLeast(inset)
        view.translationX = left.coerceIn(inset.toFloat(), maxLeft.toFloat()) - view.left
        view.translationY = top.coerceIn(inset.toFloat(), maxTop.toFloat()) - view.top
    }

    private fun settleFloatingWindow() {
        val view = floatingWindowView() ?: return
        val inset = floatingInsetPx()
        val maxTop = (height - view.height - inset).coerceAtLeast(inset)
        val clampedTop = (view.top + view.translationY).toInt().coerceIn(inset, maxTop)
        val currentLeft = view.left + view.translationX
        val side = if (currentLeft + view.width / 2f < width / 2f) FloatingSide.LEFT else FloatingSide.RIGHT
        val fraction = if (maxTop > inset) (clampedTop - inset).toFloat() / (maxTop - inset) else 0f
        floatingWindowPosition = FloatingWindowPosition(side, fraction)
        view.translationX = 0f
        view.translationY = 0f
        onFloatingWindowPositionChange?.invoke(floatingWindowPosition)
        requestLayout()
    }

    private fun floatingInsetPx(): Int {
        val density = resources.displayMetrics.density
        val logicalInset = minOf(
            RoomVideoLayoutCalculator.FLOATING_INSET_DP,
            minOf(width, height) / density / 4f,
        )
        return (logicalInset * density).roundToInt()
    }
}
