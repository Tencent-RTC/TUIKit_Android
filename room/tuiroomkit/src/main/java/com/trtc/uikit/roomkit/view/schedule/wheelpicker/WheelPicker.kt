package com.trtc.uikit.roomkit.view.schedule.wheelpicker

import android.content.Context
import android.graphics.Camera
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Region
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.widget.Scroller
import androidx.core.content.withStyledAttributes
import androidx.core.graphics.withSave
import com.trtc.uikit.roomkit.R
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

class WheelPicker @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs), IWheelPicker, Runnable {

    companion object {
        const val ALIGN_CENTER = 0
        const val ALIGN_LEFT = 1
        const val ALIGN_RIGHT = 2

        const val SCROLL_STATE_IDLE = 0
        const val SCROLL_STATE_DRAGGING = 1
        const val SCROLL_STATE_SCROLLING = 2

        private const val DEFAULT_VISIBLE_ITEM_COUNT = 7
        private const val DEFAULT_ITEM_TEXT_COLOR = -0x77777778
        private const val DEFAULT_INDICATOR_COLOR = -0x11cccd
        private const val DEFAULT_CURTAIN_COLOR = -0x77000001
        private const val DEFAULT_SELECTED_TEXT_COLOR = -1
        private const val FRAME_INTERVAL_MS = 16L
    }

    private val handler = Handler(Looper.getMainLooper())
    private val paint: Paint
    private val scroller: Scroller
    private var tracker: VelocityTracker? = null

    override var onItemSelectedListener: OnItemSelectedListener? = null
    override var onWheelChangeListener: OnWheelChangeListener? = null

    private val rectDrawn = Rect()
    private val rectIndicatorHead = Rect()
    private val rectIndicatorFoot = Rect()
    private val rectCurrentItem = Rect()
    private val camera = Camera()
    private val matrixRotate = Matrix()
    private val matrixDepth = Matrix()

    private var data: List<String> = emptyList()

    private var maxWidthText: String? = null
    override var visibleItemCount: Int = DEFAULT_VISIBLE_ITEM_COUNT
        set(value) {
            field = value
            updateVisibleItemCount()
            requestLayout()
        }
    private var drawnItemCount: Int = 0
    private var halfDrawnItemCount: Int = 0
    private var textMaxWidth: Int = 0
    private var textMaxHeight: Int = 0
    private var itemTextColor: Int = DEFAULT_ITEM_TEXT_COLOR
    private var selectedItemTextColor: Int = DEFAULT_SELECTED_TEXT_COLOR
    private var itemTextSize: Int = 0
    private var indicatorSize: Int = 0
    private var indicatorColor: Int = DEFAULT_INDICATOR_COLOR
    private var curtainColor: Int = DEFAULT_CURTAIN_COLOR
    private var itemSpace: Int = 0
    private var itemAlign: Int = ALIGN_CENTER
    private var itemHeight: Int = 0
    private var halfItemHeight: Int = 0
    private var halfWheelHeight: Int = 0
    override var selectedItemPosition: Int = 0
        set(value) {
            val safe = if (data.isEmpty()) value else value.coerceIn(0, data.size - 1)
            field = safe
            currentItemPosition = safe
            scrollOffsetY = 0
            computeFlingLimitY()
            requestLayout()
            invalidate()
        }
    override var currentItemPosition: Int = 0
        private set
    private var minFlingY: Int = 0
    private var maxFlingY: Int = 0
    private var minimumVelocity: Int = 50
    private var maximumVelocity: Int = 8000
    private var wheelCenterX: Int = 0
    private var wheelCenterY: Int = 0
    private var drawnCenterX: Int = 0
    private var drawnCenterY: Int = 0
    private var scrollOffsetY: Int = 0
    private var textMaxWidthPosition: Int = -1
    private var lastPointY: Int = 0
    private var downPointY: Int = 0
    private var touchSlop: Int = 8
    private var hasSameWidth: Boolean = false
    private var hasIndicator: Boolean = false
    private var hasCurtain: Boolean = false
    private var hasAtmospheric: Boolean = false
    private var isCyclic: Boolean = false
    private var isCurved: Boolean = false
    private var isClick: Boolean = false
    private var isForceFinishScroll: Boolean = false

    init {
        context.withStyledAttributes(attrs, R.styleable.WheelPicker) {
            itemTextSize = getDimensionPixelSize(
                R.styleable.WheelPicker_wheel_item_text_size,
                resources.getDimensionPixelSize(R.dimen.roomkit_wheel_text_size)
            )
            visibleItemCount = getInt(
                R.styleable.WheelPicker_wheel_visible_item_count,
                DEFAULT_VISIBLE_ITEM_COUNT
            )
            selectedItemPosition = getInt(R.styleable.WheelPicker_wheel_selected_item_position, 0)
            hasSameWidth = getBoolean(R.styleable.WheelPicker_wheel_same_width, false)
            textMaxWidthPosition = getInt(R.styleable.WheelPicker_wheel_maximum_width_text_position, -1)
            maxWidthText = getString(R.styleable.WheelPicker_wheel_maximum_width_text)
            selectedItemTextColor = getColor(
                R.styleable.WheelPicker_wheel_selected_item_text_color,
                DEFAULT_SELECTED_TEXT_COLOR
            )
            itemTextColor = getColor(
                R.styleable.WheelPicker_wheel_item_text_color,
                DEFAULT_ITEM_TEXT_COLOR
            )
            itemSpace = getDimensionPixelSize(
                R.styleable.WheelPicker_wheel_item_space,
                resources.getDimensionPixelSize(R.dimen.roomkit_wheel_item_space)
            )
            isCyclic = getBoolean(R.styleable.WheelPicker_wheel_cyclic, false)
            hasIndicator = getBoolean(R.styleable.WheelPicker_wheel_indicator, false)
            indicatorColor = getColor(
                R.styleable.WheelPicker_wheel_indicator_color,
                DEFAULT_INDICATOR_COLOR
            )
            indicatorSize = getDimensionPixelSize(
                R.styleable.WheelPicker_wheel_indicator_size,
                resources.getDimensionPixelSize(R.dimen.roomkit_wheel_indication_size)
            )
            hasCurtain = getBoolean(R.styleable.WheelPicker_wheel_curtain, false)
            curtainColor = getColor(R.styleable.WheelPicker_wheel_curtain_color, DEFAULT_CURTAIN_COLOR)
            hasAtmospheric = getBoolean(R.styleable.WheelPicker_wheel_atmospheric, false)
            isCurved = getBoolean(R.styleable.WheelPicker_wheel_curved, false)
            itemAlign = getInt(R.styleable.WheelPicker_wheel_item_align, ALIGN_CENTER)
        }

        updateVisibleItemCount()

        paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG or Paint.LINEAR_TEXT_FLAG).apply {
            textSize = itemTextSize.toFloat()
        }
        updateItemTextAlign()
        computeTextSize()

        scroller = Scroller(context)
        val viewConfig = ViewConfiguration.get(context)
        minimumVelocity = viewConfig.scaledMinimumFlingVelocity
        maximumVelocity = viewConfig.scaledMaximumFlingVelocity
        touchSlop = viewConfig.scaledTouchSlop
    }

    private fun updateVisibleItemCount() {
        require(visibleItemCount >= 2) { "Wheel's visible item count can not be less than 2!" }
        if (visibleItemCount % 2 == 0) {
            visibleItemCount++
        }
        drawnItemCount = visibleItemCount + 2
        halfDrawnItemCount = drawnItemCount / 2
    }

    private fun computeTextSize() {
        textMaxWidth = 0
        textMaxHeight = 0
        when {
            hasSameWidth -> {
                if (data.isNotEmpty()) {
                    textMaxWidth = paint.measureText(data[0]).toInt()
                }
            }
            isPositionInRange(textMaxWidthPosition) -> {
                textMaxWidth = paint.measureText(data[textMaxWidthPosition]).toInt()
            }
            !maxWidthText.isNullOrEmpty() -> {
                textMaxWidth = paint.measureText(maxWidthText).toInt()
            }
            else -> {
                for (text in data) {
                    val width = paint.measureText(text).toInt()
                    if (width > textMaxWidth) textMaxWidth = width
                }
            }
        }
        val metrics = paint.fontMetrics
        textMaxHeight = (metrics.bottom - metrics.top).toInt()
    }

    private fun updateItemTextAlign() {
        paint.textAlign = when (itemAlign) {
            ALIGN_LEFT -> Paint.Align.LEFT
            ALIGN_RIGHT -> Paint.Align.RIGHT
            else -> Paint.Align.CENTER
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val modeWidth = MeasureSpec.getMode(widthMeasureSpec)
        val modeHeight = MeasureSpec.getMode(heightMeasureSpec)
        val sizeWidth = MeasureSpec.getSize(widthMeasureSpec)
        val sizeHeight = MeasureSpec.getSize(heightMeasureSpec)
        var resultWidth = textMaxWidth
        var resultHeight = textMaxHeight * visibleItemCount + itemSpace * (visibleItemCount - 1)
        if (isCurved) {
            resultHeight = (2.0 * resultHeight / Math.PI).toInt()
        }
        resultWidth += paddingLeft + paddingRight
        resultHeight += paddingTop + paddingBottom

        resultWidth = measureSize(modeWidth, sizeWidth, resultWidth)
        resultHeight = measureSize(modeHeight, sizeHeight, resultHeight)
        setMeasuredDimension(resultWidth, resultHeight)
    }

    private fun measureSize(mode: Int, sizeExpect: Int, sizeActual: Int): Int = when (mode) {
        MeasureSpec.EXACTLY -> sizeExpect
        MeasureSpec.AT_MOST -> min(sizeActual, sizeExpect)
        else -> sizeActual
    }

    override fun onSizeChanged(w: Int, h: Int, oldW: Int, oldH: Int) {
        rectDrawn.set(paddingLeft, paddingTop, width - paddingRight, height - paddingBottom)
        wheelCenterX = rectDrawn.centerX()
        wheelCenterY = rectDrawn.centerY()
        computeDrawnCenter()
        halfWheelHeight = rectDrawn.height() / 2
        itemHeight = rectDrawn.height() / visibleItemCount
        halfItemHeight = itemHeight / 2
        computeFlingLimitY()
        computeIndicatorRect()
        computeCurrentItemRect()
    }

    private fun computeDrawnCenter() {
        drawnCenterX = when (itemAlign) {
            ALIGN_LEFT -> rectDrawn.left
            ALIGN_RIGHT -> rectDrawn.right
            else -> wheelCenterX
        }
        drawnCenterY = (wheelCenterY - (paint.ascent() + paint.descent()) / 2.0f).toInt()
    }

    private fun computeFlingLimitY() {
        val currentItemOffset = selectedItemPosition * itemHeight
        minFlingY = if (isCyclic) Int.MIN_VALUE else -itemHeight * (data.size - 1) + currentItemOffset
        maxFlingY = if (isCyclic) Int.MAX_VALUE else currentItemOffset
    }

    private fun computeIndicatorRect() {
        if (!hasIndicator) return
        val halfIndicatorSize = indicatorSize / 2
        val indicatorHeadCenterY = wheelCenterY + halfItemHeight
        val indicatorFootCenterY = wheelCenterY - halfItemHeight
        rectIndicatorHead.set(
            rectDrawn.left, indicatorHeadCenterY - halfIndicatorSize,
            rectDrawn.right, indicatorHeadCenterY + halfIndicatorSize
        )
        rectIndicatorFoot.set(
            rectDrawn.left, indicatorFootCenterY - halfIndicatorSize,
            rectDrawn.right, indicatorFootCenterY + halfIndicatorSize
        )
    }

    private fun computeCurrentItemRect() {
        if (hasCurtain || selectedItemTextColor != DEFAULT_SELECTED_TEXT_COLOR) {
            rectCurrentItem.set(
                rectDrawn.left, wheelCenterY - halfItemHeight,
                rectDrawn.right, wheelCenterY + halfItemHeight
            )
        }
    }

    override fun onDraw(canvas: Canvas) {
        onWheelChangeListener?.onWheelScrolled(scrollOffsetY)
        if (itemHeight == 0 || data.isEmpty()) return

        val drawnDataStartPos = -scrollOffsetY / itemHeight - halfDrawnItemCount
        var drawnDataPos = drawnDataStartPos + selectedItemPosition
        var drawnOffsetPos = -halfDrawnItemCount
        while (drawnDataPos < drawnDataStartPos + selectedItemPosition + drawnItemCount) {
            var itemText = ""
            if (isCyclic) {
                var pos = drawnDataPos % data.size
                if (pos < 0) pos += data.size
                itemText = data[pos]
            } else if (isPositionInRange(drawnDataPos)) {
                itemText = data[drawnDataPos]
            }

            paint.color = itemTextColor
            paint.style = Paint.Style.FILL
            val drawnItemCenterY = drawnCenterY + drawnOffsetPos * itemHeight + scrollOffsetY % itemHeight
            var distanceToCenter = 0
            if (isCurved) {
                val ratio = (drawnCenterY - abs(drawnCenterY - drawnItemCenterY) - rectDrawn.top).toFloat() /
                    (drawnCenterY - rectDrawn.top)
                val unit = when {
                    drawnItemCenterY > drawnCenterY -> 1
                    drawnItemCenterY < drawnCenterY -> -1
                    else -> 0
                }
                val degree = (-(1.0f - ratio) * 90.0f * unit.toFloat()).coerceIn(-90.0f, 90.0f)
                distanceToCenter = computeSpace(degree.toInt())
                val transX = when (itemAlign) {
                    ALIGN_LEFT -> rectDrawn.left
                    ALIGN_RIGHT -> rectDrawn.right
                    else -> wheelCenterX
                }
                val transY = wheelCenterY - distanceToCenter
                camera.save()
                camera.rotateX(degree)
                camera.getMatrix(matrixRotate)
                camera.restore()
                matrixRotate.preTranslate(-transX.toFloat(), -transY.toFloat())
                matrixRotate.postTranslate(transX.toFloat(), transY.toFloat())
                camera.save()
                camera.translate(0.0f, 0.0f, computeDepth(degree.toInt()).toFloat())
                camera.getMatrix(matrixDepth)
                camera.restore()
                matrixDepth.preTranslate(-transX.toFloat(), -transY.toFloat())
                matrixDepth.postTranslate(transX.toFloat(), transY.toFloat())
                matrixRotate.postConcat(matrixDepth)
            }

            if (hasAtmospheric) {
                val alpha = ((drawnCenterY - abs(drawnCenterY - drawnItemCenterY)).toFloat() /
                    drawnCenterY * 255).toInt().coerceAtLeast(0)
                paint.alpha = alpha
            }

            val centerY = if (isCurved) drawnCenterY - distanceToCenter else drawnItemCenterY

            if (selectedItemTextColor != DEFAULT_SELECTED_TEXT_COLOR) {
                canvas.withSave {
                    if (isCurved) concat(matrixRotate)
                    clipOutRectCompat(rectCurrentItem)
                    drawText(itemText, drawnCenterX.toFloat(), centerY.toFloat(), paint)
                }
                paint.color = selectedItemTextColor
                canvas.withSave {
                    if (isCurved) concat(matrixRotate)
                    clipRect(rectCurrentItem)
                    drawText(itemText, drawnCenterX.toFloat(), centerY.toFloat(), paint)
                }
            } else {
                canvas.withSave {
                    clipRect(rectDrawn)
                    if (isCurved) concat(matrixRotate)
                    drawText(itemText, drawnCenterX.toFloat(), centerY.toFloat(), paint)
                }
            }

            drawnOffsetPos++
            drawnDataPos++
        }

        if (hasCurtain) {
            paint.color = curtainColor
            paint.style = Paint.Style.FILL
            canvas.drawRect(rectCurrentItem, paint)
        }
        if (hasIndicator) {
            paint.color = indicatorColor
            paint.style = Paint.Style.FILL
            canvas.drawRect(rectIndicatorHead, paint)
            canvas.drawRect(rectIndicatorFoot, paint)
        }
    }

    private fun isPositionInRange(position: Int): Boolean = position in data.indices

    private fun computeSpace(degree: Int): Int =
        (sin(Math.toRadians(degree.toDouble())) * halfWheelHeight).toInt()

    private fun computeDepth(degree: Int): Int =
        (halfWheelHeight - cos(Math.toRadians(degree.toDouble())) * halfWheelHeight).toInt()

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                val vt = tracker ?: VelocityTracker.obtain().also { tracker = it }
                vt.clear()
                vt.addMovement(event)
                if (!scroller.isFinished) {
                    scroller.abortAnimation()
                    isForceFinishScroll = true
                }
                downPointY = event.y.toInt()
                lastPointY = downPointY
            }
            MotionEvent.ACTION_MOVE -> {
                if (abs(downPointY - event.y) < touchSlop.toFloat()) {
                    isClick = true
                } else {
                    isClick = false
                    tracker?.addMovement(event)
                    onWheelChangeListener?.onWheelScrollStateChanged(SCROLL_STATE_DRAGGING)
                    val move = event.y - lastPointY
                    if (abs(move) >= 1.0f) {
                        scrollOffsetY += move.toInt()
                        lastPointY = event.y.toInt()
                        invalidate()
                    }
                }
            }
            MotionEvent.ACTION_UP -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                if (isClick) {
                    performClick()
                } else {
                    val vt = tracker
                    vt?.addMovement(event)
                    vt?.computeCurrentVelocity(1000, maximumVelocity.toFloat())
                    isForceFinishScroll = false
                    val velocity = vt?.yVelocity?.toInt() ?: 0
                    if (abs(velocity) > minimumVelocity) {
                        scroller.fling(0, scrollOffsetY, 0, velocity, 0, 0, minFlingY, maxFlingY)
                        scroller.finalY = scroller.finalY + computeDistanceToEndPoint(scroller.finalY % itemHeight)
                    } else {
                        scroller.startScroll(
                            0, scrollOffsetY, 0,
                            computeDistanceToEndPoint(scrollOffsetY % itemHeight)
                        )
                    }
                    if (!isCyclic) {
                        scroller.finalY = scroller.finalY.coerceIn(minFlingY, maxFlingY)
                    }
                    handler.post(this)
                    vt?.recycle()
                    tracker = null
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                tracker?.recycle()
                tracker = null
            }
        }
        return true
    }

    override fun performClick(): Boolean = super.performClick()

    /** `Canvas.clipRect(rect, Region.Op.DIFFERENCE)` is deprecated from API 26; this handles both branches. */
    private fun Canvas.clipOutRectCompat(rect: Rect) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            clipOutRect(rect)
        } else {
            @Suppress("DEPRECATION")
            clipRect(rect, Region.Op.DIFFERENCE)
        }
    }

    private fun computeDistanceToEndPoint(remainder: Int): Int = if (abs(remainder) > halfItemHeight) {
        if (scrollOffsetY < 0) -itemHeight - remainder else itemHeight - remainder
    } else {
        -remainder
    }

    override fun run() {
        if (data.isEmpty()) return
        if (scroller.isFinished && !isForceFinishScroll) {
            if (itemHeight == 0) return
            var position = (-scrollOffsetY / itemHeight + selectedItemPosition) % data.size
            if (position < 0) position += data.size
            currentItemPosition = position
            onItemSelectedListener?.onItemSelected(this, data[position], position)
            onWheelChangeListener?.onWheelSelected(position)
            onWheelChangeListener?.onWheelScrollStateChanged(SCROLL_STATE_IDLE)
        }
        if (scroller.computeScrollOffset()) {
            onWheelChangeListener?.onWheelScrollStateChanged(SCROLL_STATE_SCROLLING)
            scrollOffsetY = scroller.currY
            postInvalidate()
            handler.postDelayed(this, FRAME_INTERVAL_MS)
        }
    }

    override fun setData(data: List<String>) {
        this.data = data
        if (selectedItemPosition <= data.size - 1 && currentItemPosition <= data.size - 1) {
            selectedItemPosition = currentItemPosition
        } else {
            val last = (data.size - 1).coerceAtLeast(0)
            selectedItemPosition = last
            currentItemPosition = last
        }
        scrollOffsetY = 0
        computeTextSize()
        computeFlingLimitY()
        requestLayout()
        invalidate()
    }

    interface OnWheelChangeListener {
        fun onWheelScrolled(position: Int)
        fun onWheelSelected(position: Int)
        fun onWheelScrollStateChanged(state: Int)
    }

    interface OnItemSelectedListener {
        fun onItemSelected(picker: WheelPicker, item: Any?, position: Int)
    }
}
