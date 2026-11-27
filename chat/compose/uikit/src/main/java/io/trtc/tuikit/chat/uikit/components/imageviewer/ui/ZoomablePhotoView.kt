package io.trtc.tuikit.chat.uikit.components.imageviewer.ui

import android.content.Context
import android.graphics.RectF
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import coil3.load
import io.trtc.tuikit.chat.uikit.components.imageviewer.ui.photoview.PhotoView
import io.trtc.tuikit.chat.uikit.components.imageviewer.utils.ImageUtils
import kotlin.math.abs

@Composable
fun ZoomablePhotoView(
    modifier: Modifier = Modifier,
    data: Any?,
    onTap: () -> Unit = {},
    onZoomChanged: (Boolean) -> Unit = {},
    onPagerScrollEnabled: (Boolean) -> Unit = {},
    onPagerOverscroll: (Float) -> Unit = {},
    onPagerOverscrollEnd: (Float) -> Unit = {}
) {
    val context = LocalContext.current
    val imageLoader = ImageUtils.getImageLoader()
    val host = remember { PagerCoordinatedPhotoHost(context) }
    var loadedData by remember { mutableStateOf<Any?>(null) }
    AndroidView(
        modifier = modifier,
        factory = { host },
        update = { view ->
            val photoView = view.photoView
            view.onPagerOverscroll = onPagerOverscroll
            view.onPagerOverscrollEnd = onPagerOverscrollEnd
            photoView.setOnPhotoTapListener { _, _, _ -> onTap() }
            photoView.setOnScaleChangeListener { _, _, _ ->
                onZoomChanged(photoView.scale > ZOOM_THRESHOLD)
                onPagerScrollEnabled(!isConsumingHorizontalDrag(photoView, photoView.displayRect))
            }
            photoView.setOnMatrixChangeListener { rect ->
                onZoomChanged(photoView.scale > ZOOM_THRESHOLD)
                onPagerScrollEnabled(!isConsumingHorizontalDrag(photoView, rect))
            }
            onPagerScrollEnabled(!isConsumingHorizontalDrag(photoView, photoView.displayRect))
            if (loadedData != data) {
                loadedData = data
                photoView.load(data = data, imageLoader = imageLoader)
                photoView.post {
                    photoView.setScale(MIN_SCALE, false)
                    onZoomChanged(false)
                    onPagerScrollEnabled(true)
                }
            }
        }
    )
}

private class PagerCoordinatedPhotoHost(
    context: Context
) : FrameLayout(context) {

    val photoView: PhotoView = PhotoView(context).apply {
        layoutParams = LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        scaleType = ImageView.ScaleType.FIT_CENTER
        setAllowParentInterceptOnEdge(true)
        setScaleLevels(MIN_SCALE, MID_SCALE, MAX_SCALE)
    }

    var onPagerOverscroll: (Float) -> Unit = {}
    var onPagerOverscrollEnd: (Float) -> Unit = {}

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val velocityTracker = VelocityTracker.obtain()
    private var downRawX = 0f
    private var lastRawX = 0f
    private var handedOffToPager = false
    private var passedTouchSlop = false
    private var forwardedToPager = false
    private var nestedScrollPagerThisGesture = false

    init {
        addView(photoView)
    }

    override fun requestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {
        if (nestedScrollPagerThisGesture) {
            super.requestDisallowInterceptTouchEvent(true)
        } else {
            super.requestDisallowInterceptTouchEvent(disallowIntercept)
        }
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) {
            return false
        }
        return handedOffToPager
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        return handedOffToPager || forwardedToPager
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                velocityTracker.clear()
                addVelocityMovement(ev)
                downRawX = ev.rawX
                lastRawX = ev.rawX
                handedOffToPager = false
                passedTouchSlop = false
                forwardedToPager = false
                nestedScrollPagerThisGesture = isConsumingHorizontalDrag(
                    photoView,
                    photoView.displayRect
                )
            }

            MotionEvent.ACTION_MOVE -> {
                addVelocityMovement(ev)
                handleMove(ev)
            }

            MotionEvent.ACTION_POINTER_DOWN,
            MotionEvent.ACTION_POINTER_UP -> {
                addVelocityMovement(ev)
                handedOffToPager = false
                lastRawX = ev.rawX
            }

            MotionEvent.ACTION_UP -> {
                addVelocityMovement(ev)
                settlePagerIfNeeded()
            }
        }
        val handled = super.dispatchTouchEvent(ev)
        if (ev.actionMasked == MotionEvent.ACTION_UP || ev.actionMasked == MotionEvent.ACTION_CANCEL) {
            resetGestureTracking()
        }
        return handled
    }

    private fun addVelocityMovement(ev: MotionEvent) {
        val tracked = MotionEvent.obtain(ev)
        tracked.setLocation(ev.rawX, ev.rawY)
        velocityTracker.addMovement(tracked)
        tracked.recycle()
    }

    private fun handleMove(ev: MotionEvent) {
        if (ev.pointerCount != 1) {
            lastRawX = ev.rawX
            handedOffToPager = false
            return
        }
        val rawDx = ev.rawX - lastRawX
        lastRawX = ev.rawX
        if (!passedTouchSlop) {
            if (abs(ev.rawX - downRawX) < touchSlop) {
                return
            }
            passedTouchSlop = true
        }
        if (!handedOffToPager && shouldHandoffToPager(rawDx)) {
            handedOffToPager = true
        }
        if (handedOffToPager && nestedScrollPagerThisGesture) {
            onPagerOverscroll(rawDx)
            forwardedToPager = true
        }
    }

    private fun shouldHandoffToPager(deltaX: Float): Boolean {
        if (deltaX < EDGE_HANDOFF_DELTA && deltaX > -EDGE_HANDOFF_DELTA) {
            return false
        }
        val rect = photoView.displayRect ?: return true
        val viewWidth = photoViewContentWidth(photoView)
        if (viewWidth <= 0f) {
            return true
        }
        val atLeft = rect.left >= -EDGE_EPSILON
        val atRight = rect.right <= viewWidth + EDGE_EPSILON
        return (atLeft && atRight) ||
            (atLeft && deltaX >= EDGE_HANDOFF_DELTA) ||
            (atRight && deltaX <= -EDGE_HANDOFF_DELTA)
    }

    private fun settlePagerIfNeeded() {
        if (!forwardedToPager) {
            return
        }
        velocityTracker.computeCurrentVelocity(VELOCITY_UNITS)
        onPagerOverscrollEnd(velocityTracker.xVelocity)
    }

    private fun resetGestureTracking() {
        handedOffToPager = false
        passedTouchSlop = false
        forwardedToPager = false
        nestedScrollPagerThisGesture = false
    }
}

private fun isConsumingHorizontalDrag(photoView: PhotoView, rect: RectF?): Boolean {
    if (rect == null) {
        return false
    }
    val viewWidth = photoViewContentWidth(photoView)
    return viewWidth > 0f && rect.width() > viewWidth + EDGE_EPSILON
}

private fun photoViewContentWidth(photoView: PhotoView): Float {
    return (photoView.width - photoView.paddingLeft - photoView.paddingRight).toFloat()
}

private const val MIN_SCALE = 1.0f
private const val MID_SCALE = 1.75f
private const val MAX_SCALE = 3.0f
private const val ZOOM_THRESHOLD = MIN_SCALE + 0.01f
private const val EDGE_EPSILON = 1f
private const val EDGE_HANDOFF_DELTA = 1f
private const val VELOCITY_UNITS = 1000
