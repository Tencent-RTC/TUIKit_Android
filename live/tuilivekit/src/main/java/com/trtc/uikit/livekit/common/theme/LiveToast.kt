package com.trtc.uikit.livekit.common.theme

import android.content.Context
import android.content.res.Resources
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.Toast
import androidx.annotation.MainThread
import androidx.core.content.ContextCompat
import com.trtc.uikit.livekit.R

private val Float.dp: Float get() = this * Resources.getSystem().displayMetrics.density
private fun Context.color(colorRes: Int): Int = ContextCompat.getColor(this, colorRes)

object LiveToast {

    enum class Style { INFO, WARNING }
    enum class Position { TOP, CENTER, BOTTOM }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var pendingTask: Runnable? = null

    fun show(context: Context, text: String, style: Style = Style.INFO, position: Position = Position.CENTER) {
        if (text.isBlank()) return
        pendingTask?.let {
            mainHandler.removeCallbacks(it)
            pendingTask = null
        }
        val appContext = context.applicationContext
        val task = Runnable {
            executeRealShow(appContext, text, position)
            pendingTask = null
        }
        pendingTask = task
        if (Looper.myLooper() == Looper.getMainLooper()) task.run() else mainHandler.post(task)
    }

    @MainThread
    internal fun showScoped(context: Context, text: String): Toast =
        executeRealShow(context.applicationContext, text, Position.CENTER)

    private fun executeRealShow(appContext: Context, text: String, position: Position): Toast {
        val toast = Toast(appContext)

        @Suppress("DEPRECATION")
        toast.view = createToastView(appContext, text)

        toast.duration = Toast.LENGTH_SHORT
        val gravity = when (position) {
            Position.TOP -> Gravity.TOP or Gravity.CENTER_HORIZONTAL
            Position.CENTER -> Gravity.CENTER
            Position.BOTTOM -> Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        }
        val yOffset = appContext.resources.getDimension(R.dimen.spacing_20).toInt()
        toast.setGravity(gravity, 0, if (position == Position.CENTER) 0 else yOffset)
        toast.show()
        return toast
    }

    private fun createToastView(context: Context, text: String): LiveText = LiveText(context).apply {
        role = LiveTheme.TextRole.SECTION_TITLE
        tone = LiveTheme.TextTone.PRIMARY
        gravity = Gravity.CENTER
        includeFontPadding = false
        minHeight = 34f.dp.toInt()
        setPadding(16f.dp.toInt(), 9f.dp.toInt(), 16f.dp.toInt(), 9f.dp.toInt())
        setText(text, lineHeightSp = 16f)
        background = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(
                context.color(R.color.livekit_toast_gradient_start),
                context.color(R.color.livekit_toast_gradient_middle),
                context.color(R.color.livekit_toast_gradient_end),
            ),
        ).apply {
            cornerRadius = 17f.dp
            setStroke(1f.dp.toInt(), context.color(R.color.livekit_toast_border))
        }
        elevation = 24f.dp
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            outlineSpotShadowColor = context.color(R.color.livekit_toast_glow)
            outlineAmbientShadowColor = Color.TRANSPARENT
        }
        isClickable = false
        isFocusable = false
    }
}