package com.trtc.uikit.livekit.features.audienceview.view.scaffold

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.text.TextUtils
import android.util.AttributeSet
import androidx.annotation.ColorInt
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.AppCompatImageView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.target.SimpleTarget
import com.bumptech.glide.request.transition.Transition
import com.google.gson.Gson
import com.trtc.uikit.livekit.common.LiveKitLogger
import com.trtc.uikit.livekit.features.audienceview.store.AudienceStore
import io.trtc.tuikit.atomicx.common.imageloader.BlurUtils
import io.trtc.tuikit.atomicx.common.util.ScreenUtil
import com.trtc.uikit.livekit.component.pippanel.PictureInPictureStore
import io.trtc.tuikit.atomicxcore.api.live.SeatInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class LiveCoreViewMaskBackgroundView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatImageView(context, attrs, defStyleAttr) {

    companion object {
        private const val BLUR_RADIUS = 20
        private val LOGGER = LiveKitLogger.getFeaturesLogger("LiveCoreViewMaskBackgroundView")
    }

    private val oid = hashCode()
    private val videoLandscape4SeatsTemplateID = 200
    private lateinit var audienceStore: AudienceStore
    private var parentWidth = ScreenUtil.dip2px(720f)
    private var parentHeight = ScreenUtil.dip2px(1080f)
    private var backgroundDrawable: Drawable? = null
    private var backgroundColor: Int = Color.TRANSPARENT
    private var topRect = Rect()
    private var middleRect = Rect()
    private var bottomRect = Rect()
    private var enableBlur = true
    private var isPortrait = true

    private var subscribeStateJob: Job? = null

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        subscribeStateJob = CoroutineScope(Dispatchers.Main).launch {
            launch {
                audienceStore.getLiveSeatState().seatList.collect {
                    onSeatLayoutChange(it)
                }
            }
            launch {
                PictureInPictureStore.shared.state.isPictureInPictureMode.collect {
                    visibility = if (it) GONE else VISIBLE
                }
            }
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        subscribeStateJob?.cancel()
    }

    fun init(manager: AudienceStore) {
        this.audienceStore = manager
    }

    override fun setImageDrawable(drawable: Drawable?) {
        backgroundDrawable = drawable
        backgroundDrawable?.callback = this
        invalidate()
    }

    override fun setImageResource(resId: Int) {
        backgroundDrawable = AppCompatResources.getDrawable(context, resId)
        backgroundDrawable?.callback = this
        invalidate()
    }

    override fun setBackground(background: Drawable?) {
        backgroundDrawable = background
        backgroundDrawable?.callback = this
        invalidate()
    }

    override fun setBackgroundResource(resId: Int) {
        backgroundDrawable = AppCompatResources.getDrawable(context, resId)
        backgroundDrawable?.callback = this
        invalidate()
    }

    fun enableBlur(enable: Boolean) {
        enableBlur = enable
    }

    override fun setBackgroundColor(@ColorInt color: Int) {
        backgroundColor = color
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        LOGGER.info("onSizeChanged OID:$oid w:$w h:$h oldw:$oldw oldh:$oldh")
        if (w != oldw || h != oldh) {
            onSeatLayoutChange(audienceStore.getLiveSeatState().seatList.value)
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        parentWidth = measuredWidth
        parentHeight = measuredHeight
        LOGGER.info("onMeasure OID:$oid parentWidth:$parentWidth parentHeight:$parentHeight")
    }

    override fun onDraw(canvas: Canvas) {
        val isFullScreen = isFullScreenLayoutBySeatLayout()
        LOGGER.info(
            "onDraw OID:$oid,: isFullScreen:$isFullScreen" +
                    ", roomId:${audienceStore.getLiveListState().currentLive.value.liveID}" +
                    ", topRect:$topRect, middleRect:$middleRect" +
                    ", bottomRect:$bottomRect,backgroundColor:$backgroundColor" +
                    ", backgroundDrawable:$backgroundDrawable"
        )
        if (isFullScreen) {
            drawableTransparent(canvas)
            return
        }
        if (backgroundDrawable == null) {
            if (backgroundColor == Color.TRANSPARENT) {
                drawableTransparent(canvas)
            } else {
                drawableColor(canvas, backgroundColor)
            }
        } else {
            drawDrawable(canvas, backgroundDrawable!!)
        }
    }

    private fun drawableTransparent(canvas: Canvas) {
        val paint = Paint()
        paint.color = Color.TRANSPARENT
        val parentRect = Rect(0, 0, parentWidth, parentHeight)
        canvas.drawRect(parentRect, paint)
    }

    private fun drawableColor(canvas: Canvas, backgroundColor: Int) {
        val isFullScreen = isFullScreenLayoutBySeatLayout()
        if (isFullScreen) {
            drawableTransparent(canvas)
            return
        }
        val paint = Paint()
        paint.color = backgroundColor
        canvas.drawRect(topRect, paint)

        paint.color = Color.TRANSPARENT
        canvas.drawRect(middleRect, paint)

        paint.color = backgroundColor
        canvas.drawRect(bottomRect, paint)
    }

    private fun drawDrawable(canvas: Canvas, drawable: Drawable) {
        val isFullScreen = isFullScreenLayoutBySeatLayout()
        if (isFullScreen) {
            drawableTransparent(canvas)
            return
        }
        val bitmap = drawableToBitmap(drawable)
        if (bitmap == null) {
            LOGGER.error("drawDrawable bitmap is null")
            return
        }
        val topScale = topRect.bottom / parentHeight.toFloat()
        val bitmapTopRect = Rect(0, 0, bitmap.width, (bitmap.height * topScale).toInt())
        canvas.drawBitmap(bitmap, bitmapTopRect, topRect, null)

        val paint = Paint()
        paint.color = Color.TRANSPARENT
        canvas.drawRect(middleRect, paint)

        val bottomScale = bottomRect.top / parentHeight.toFloat()
        val bitmapBottomRect = Rect(
            0,
            (bitmap.height * bottomScale).toInt(),
            bitmap.width,
            bitmap.height
        )
        canvas.drawBitmap(bitmap, bitmapBottomRect, bottomRect, null)
    }

    fun drawableToBitmap(drawable: Drawable): Bitmap? {
        if (drawable is BitmapDrawable) {
            val bitmap = drawable.bitmap
            return if (enableBlur) BlurUtils.blur(context, bitmap, BLUR_RADIUS) else bitmap
        }
        val width = drawable.intrinsicWidth
        val height = drawable.intrinsicHeight

        if (width <= 0 || height <= 0) {
            LOGGER.error("Drawable dimensions are invalid")
            return null
        }
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return if (enableBlur) BlurUtils.blur(context, bitmap, BLUR_RADIUS) else bitmap
    }

    fun setBackgroundUrl(url: String?) {
        LOGGER.info(
            "setBackgroundUrl OID:$oid, roomId:${audienceStore.getLiveListState().currentLive.value.liveID}" +
                    ", topRect:$topRect, middleRect:$middleRect, bottomRect:$bottomRect,backgroundColor:$backgroundColor" +
                    ", backgroundDrawable:$backgroundDrawable, url:$url"
        )
        if (TextUtils.isEmpty(url)) {
            return
        }
        var processedUrl = url!!
        val color: Int
        try {
            if (processedUrl.startsWith("0x") || processedUrl.startsWith("0X")) {
                processedUrl = processedUrl.substring(2)
            } else if (processedUrl.startsWith("#")) {
                processedUrl = processedUrl.substring(1)
            }
            color = processedUrl.toInt(16)
        } catch (e: Exception) {
            LOGGER.info("Exception OID:$oid, url:$url")
            backgroundColor = Color.TRANSPARENT
            Glide.with(context)
                .load(url)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .into(object : SimpleTarget<Drawable>() {
                    override fun onResourceReady(
                        resource: Drawable,
                        transition: Transition<in Drawable>?
                    ) {
                        LOGGER.info(
                            "onBackgroundUrlChange OID:$oid, roomId:${audienceStore.getLiveListState().currentLive.value.liveID}, resource:$resource"
                        )
                        this@LiveCoreViewMaskBackgroundView.setImageDrawable(resource)
                    }

                    override fun onLoadFailed(errorDrawable: Drawable?) {
                        LOGGER.info("onLoadFailed OID:$oid, roomId:${audienceStore.getLiveListState().currentLive.value.liveID}")
                        backgroundDrawable = null
                        setBackgroundColor(Color.TRANSPARENT)
                    }
                })
            return
        }
        backgroundDrawable = null
        setBackgroundColor(color)
    }

    fun setPortrait(isPortrait: Boolean) {
        this.isPortrait = isPortrait
        invalidate()
    }

    fun isFullScreenLayoutBySeatLayout(): Boolean {
        if (audienceStore.getLiveSeatState().canvas.value.templateID == videoLandscape4SeatsTemplateID && isPortrait) {
            return false
        }
        if (audienceStore.getLiveSeatState().seatList.value.isEmpty()) {
            return true
        }
        if (audienceStore.getLiveSeatState().seatList.value.size <= 1) {
            return true
        }
        for (seatInfo in audienceStore.getLiveSeatState().seatList.value) {
            if (seatInfo.region.w == audienceStore.getLiveSeatState().canvas.value.w && seatInfo.region.h == audienceStore.getLiveSeatState().canvas.value.h) {
                return true
            }
        }
        return false
    }

    private fun onSeatLayoutChange(seatList: List<SeatInfo>) {
        LOGGER.info("setSeatLayout OID:$oid,seatLayout:${Gson().toJson(seatList)}")
        if (audienceStore.getLiveSeatState().canvas.value.w == 0
            || audienceStore.getLiveSeatState().canvas.value.h == 0
            || seatList.isEmpty()
        ) {
            invalidate()
            return
        }
        if (audienceStore.getLiveSeatState().canvas.value.templateID == videoLandscape4SeatsTemplateID && isPortrait) {
            val screenWidth = ScreenUtil.getScreenWidth(context)
            val videoTop = ScreenUtil.dip2px(150f)
            val videoBottom = screenWidth * 9 / 16 + ScreenUtil.dip2px(150f)
            topRect = Rect(0, 0, parentWidth, videoTop)
            middleRect = Rect(0, videoTop, parentWidth, videoBottom)
            bottomRect = Rect(0, videoBottom, parentWidth, parentHeight)
            invalidate()
            return
        }
        val rect = Rect()
        rect.left = Int.MAX_VALUE
        rect.top = Int.MAX_VALUE
        rect.right = 0
        rect.bottom = 0
        for (seatInfo in seatList) {
            rect.left = minOf(rect.left, seatInfo.region.x)
            rect.top = minOf(rect.top, seatInfo.region.y)
            rect.right =
                maxOf(
                    rect.right,
                    seatInfo.region.x + seatInfo.region.w
                )
            rect.bottom =
                maxOf(
                    rect.bottom,
                    seatInfo.region.y + seatInfo.region.h
                )
        }
        val scale = parentWidth / audienceStore.getLiveSeatState().canvas.value.w.toFloat()
        val width = (rect.width() * scale).toInt()
        val height = (rect.height() * scale).toInt()
        val top = (rect.top * scale).toInt()
        val left = (rect.left * scale).toInt()
        LOGGER.info("addFillScreenView OID:$oid l:$left,t:$top,w:$width,h:$height")

        topRect = Rect(0, 0, width, top)
        middleRect = Rect(0, top, width, top + height)
        bottomRect = Rect(0, top + height, width, parentHeight)

        invalidate()
    }
}
