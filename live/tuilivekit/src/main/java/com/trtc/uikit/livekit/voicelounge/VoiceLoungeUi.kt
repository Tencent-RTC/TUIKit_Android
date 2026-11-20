package com.trtc.uikit.livekit.voicelounge

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.annotation.StringRes
import com.trtc.uikit.livekit.common.ContextProvider
import com.trtc.uikit.livekit.common.theme.LiveText
import com.trtc.uikit.livekit.common.theme.LiveTheme
import java.io.Closeable
import kotlin.math.roundToInt

internal class VoiceLoungeSignal {
    private val listeners = LinkedHashMap<Int, () -> Unit>()
    private var nextID = 0

    fun subscribe(listener: () -> Unit): Closeable {
        val id = nextID++
        listeners[id] = listener
        return Closeable { listeners.remove(id) }
    }

    fun send() {
        listeners.values.toList().forEach { it() }
    }
}

internal class VoiceLoungeEvents<T> {
    private val listeners = LinkedHashMap<Int, (T) -> Unit>()
    private var nextID = 0

    fun subscribe(listener: (T) -> Unit): Closeable {
        val id = nextID++
        listeners[id] = listener
        return Closeable { listeners.remove(id) }
    }

    fun send(value: T) {
        listeners.values.toList().forEach { it(value) }
    }
}

internal class VoiceLoungeBag {
    private val items = mutableListOf<Closeable>()

    val isEmpty: Boolean get() = items.isEmpty()

    fun add(item: Closeable) {
        items.add(item)
    }

    fun clear() {
        val snapshot = items.toList()
        items.clear()
        snapshot.forEach { runCatching { it.close() } }
    }
}

internal fun Closeable.addTo(bag: VoiceLoungeBag) = bag.add(this)

internal object VoiceLoungeMain {
    private val handler = Handler(Looper.getMainLooper())

    fun post(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) action() else handler.post(action)
    }

    fun postDelayed(delayMs: Long, action: Runnable) {
        handler.postDelayed(action, delayMs)
    }

    fun cancel(action: Runnable) {
        handler.removeCallbacks(action)
    }
}

internal val voiceLoungeAppContext: Context
    get() = requireNotNull(ContextProvider.applicationContext)

internal fun vlString(@StringRes id: Int, vararg args: Any): String =
    if (args.isEmpty()) voiceLoungeAppContext.getString(id) else voiceLoungeAppContext.getString(id, *args)

internal fun Context.dp(value: Float): Int =
    TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics).roundToInt()

internal fun Context.dp(value: Int): Int = dp(value.toFloat())

internal fun View.dp(value: Float): Int = context.dp(value)

internal fun View.dp(value: Int): Int = context.dp(value.toFloat())

@ColorInt
internal fun Int.withAlpha(alpha: Float): Int =
    Color.argb((alpha.coerceIn(0f, 1f) * 255).roundToInt(), Color.red(this), Color.green(this), Color.blue(this))

@ColorInt
internal fun rgb(red: Float, green: Float, blue: Float, alpha: Float = 1f): Int = Color.argb(
    (alpha * 255).roundToInt(),
    (red * 255).roundToInt(),
    (green * 255).roundToInt(),
    (blue * 255).roundToInt(),
)

@ColorInt
internal fun lightenColor(@ColorInt color: Int, saturationDelta: Float, brightnessDelta: Float): Int {
    val hsv = FloatArray(3)
    Color.colorToHSV(color, hsv)
    hsv[1] = (hsv[1] - saturationDelta).coerceAtLeast(0f)
    hsv[2] = (hsv[2] + brightnessDelta).coerceAtMost(1f)
    return Color.HSVToColor(hsv)
}

@ColorInt
internal fun whiteAlpha(alpha: Float): Int = Color.WHITE.withAlpha(alpha)

@ColorInt
internal fun blackAlpha(alpha: Float): Int = Color.BLACK.withAlpha(alpha)

internal fun roundedDrawable(
    @ColorInt color: Int,
    radius: Float,
    strokeWidth: Int = 0,
    @ColorInt strokeColor: Int = Color.TRANSPARENT,
): GradientDrawable = GradientDrawable().apply {
    shape = GradientDrawable.RECTANGLE
    cornerRadius = radius
    setColor(color)
    if (strokeWidth > 0) setStroke(strokeWidth, strokeColor)
}

internal fun ovalDrawable(
    @ColorInt color: Int,
    strokeWidth: Int = 0,
    @ColorInt strokeColor: Int = Color.TRANSPARENT,
): GradientDrawable = GradientDrawable().apply {
    shape = GradientDrawable.OVAL
    setColor(color)
    if (strokeWidth > 0) setStroke(strokeWidth, strokeColor)
}

internal fun gradientDrawable(
    colors: IntArray,
    radius: Float = 0f,
    orientation: GradientDrawable.Orientation = GradientDrawable.Orientation.LEFT_RIGHT,
): GradientDrawable = GradientDrawable(orientation, colors).apply { cornerRadius = radius }

internal fun TextView.applyFont(sizeSp: Float, semibold: Boolean = false) {
    setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
    typeface = Typeface.create(if (semibold) "sans-serif-medium" else "sans-serif", Typeface.NORMAL)
}

internal object VoiceLoungeFont {
    fun size(base: Float): Float = if (base < 13f) base + 2f else base + 1f
}

internal fun voiceLoungeText(
    context: Context,
    role: LiveTheme.TextRole,
    tone: LiveTheme.TextTone = LiveTheme.TextTone.PRIMARY,
    gravity: Int = Gravity.START or Gravity.CENTER_VERTICAL,
    emphasized: Boolean = false,
    text: String? = null,
): LiveText = LiveText(context).apply {
    this.role = role
    this.tone = tone
    isEmphasized = emphasized
    includeFontPadding = false
    this.gravity = gravity
    setTextSize(TypedValue.COMPLEX_UNIT_SP, VoiceLoungeFont.size(role.sizeSp))
    if (text != null) this.text = text
}

internal fun liveText(
    context: Context,
    role: LiveTheme.TextRole,
    tone: LiveTheme.TextTone = LiveTheme.TextTone.PRIMARY,
    gravity: Int = Gravity.START or Gravity.CENTER_VERTICAL,
    emphasized: Boolean = false,
): LiveText = LiveText(context).apply {
    this.role = role
    this.tone = tone
    isEmphasized = emphasized
    includeFontPadding = false
    this.gravity = gravity
}

internal fun TextView.singleLineEnd() {
    maxLines = 1
    isSingleLine = true
    ellipsize = android.text.TextUtils.TruncateAt.END
}

internal fun frameParams(
    width: Int,
    height: Int,
    gravity: Int = Gravity.NO_GRAVITY,
    start: Int = 0,
    top: Int = 0,
    end: Int = 0,
    bottom: Int = 0,
): FrameLayout.LayoutParams = FrameLayout.LayoutParams(width, height, gravity).apply {
    marginStart = start
    topMargin = top
    marginEnd = end
    bottomMargin = bottom
}

internal fun linearParams(
    width: Int,
    height: Int,
    weight: Float = 0f,
    start: Int = 0,
    top: Int = 0,
    end: Int = 0,
    bottom: Int = 0,
    gravity: Int = -1,
): LinearLayout.LayoutParams = LinearLayout.LayoutParams(width, height, weight).apply {
    marginStart = start
    topMargin = top
    marginEnd = end
    bottomMargin = bottom
    this.gravity = gravity
}

internal const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
internal const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

internal fun View.setPressAlpha(pressedAlpha: Float = 0.7f) {
    setOnTouchListener { view, event ->
        when (event.actionMasked) {
            android.view.MotionEvent.ACTION_DOWN -> view.alpha = pressedAlpha
            android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> view.alpha = 1f
        }
        false
    }
}

internal fun View.onClick(action: () -> Unit) {
    setOnClickListener { action() }
}

internal fun View.clipRounded(radius: Float) {
    outlineProvider = object : android.view.ViewOutlineProvider() {
        override fun getOutline(view: View, outline: android.graphics.Outline) {
            outline.setRoundRect(0, 0, view.width, view.height, radius)
        }
    }
    clipToOutline = true
}

internal fun View.clipOval() {
    outlineProvider = object : android.view.ViewOutlineProvider() {
        override fun getOutline(view: View, outline: android.graphics.Outline) {
            outline.setOval(0, 0, view.width, view.height)
        }
    }
    clipToOutline = true
}

internal val String.graphemeCount: Int
    get() {
        if (isEmpty()) return 0
        val iterator = java.text.BreakIterator.getCharacterInstance().apply { setText(this@graphemeCount) }
        var count = 0
        while (iterator.next() != java.text.BreakIterator.DONE) count++
        return count
    }

internal fun android.widget.ImageView.tint(@ColorInt color: Int?) {
    imageTintList = color?.let { android.content.res.ColorStateList.valueOf(it) }
}

internal fun android.widget.ImageView.loadUrl(
    url: String,
    @androidx.annotation.DrawableRes placeholder: Int = 0,
    onFailed: (() -> Unit)? = null,
) {
    val activity = context as? android.app.Activity
    if (activity != null && (activity.isFinishing || activity.isDestroyed)) return
    val request = com.bumptech.glide.Glide.with(this).load(url.trim().ifEmpty { null })
    val options = if (placeholder != 0) request.placeholder(placeholder).error(placeholder) else request
    options.listener(object : com.bumptech.glide.request.RequestListener<android.graphics.drawable.Drawable> {
        override fun onLoadFailed(
            e: com.bumptech.glide.load.engine.GlideException?,
            model: Any?,
            target: com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable>?,
            isFirstResource: Boolean,
        ): Boolean {
            onFailed?.invoke()
            return false
        }

        override fun onResourceReady(
            resource: android.graphics.drawable.Drawable?,
            model: Any?,
            target: com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable>?,
            dataSource: com.bumptech.glide.load.DataSource?,
            isFirstResource: Boolean,
        ): Boolean = false
    }).into(this)
}

internal fun android.widget.ImageView.clearImage() {
    runCatching { com.bumptech.glide.Glide.with(this).clear(this) }
    setImageDrawable(null)
}

@ColorInt
internal fun placeholderColor(name: String, palette: List<Int>): Int =
    palette[Math.floorMod(name.hashCode(), maxOf(1, palette.size))]

internal class CenteredImageSpan(drawable: android.graphics.drawable.Drawable) :
    android.text.style.ImageSpan(drawable) {
    override fun draw(
        canvas: android.graphics.Canvas,
        text: CharSequence?,
        start: Int,
        end: Int,
        x: Float,
        top: Int,
        y: Int,
        bottom: Int,
        paint: android.graphics.Paint,
    ) {
        val d = drawable
        val fm = paint.fontMetricsInt
        val transY = y + (fm.descent + fm.ascent) / 2 - d.bounds.height() / 2
        canvas.save()
        canvas.translate(x, transY.toFloat())
        d.draw(canvas)
        canvas.restore()
    }
}
