package com.trtc.uikit.livekit.common.theme.toast

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import com.tencent.imsdk.v2.V2TIMManager
import com.trtc.uikit.livekit.R
import com.trtc.uikit.livekit.common.theme.LiveText
import com.trtc.uikit.livekit.common.theme.LiveTheme
import org.json.JSONObject

private const val ATOMIC_EVENT_ID = 100011
private const val FRAMEWORK_NAME = "AtomicXCore"

object LiveToast {

    enum class Style {
        TEXT,
        INFO,
        HELP,
        LOADING,
        SUCCESS,
        WARNING,
        ERROR
    }

    enum class Position {
        TOP,
        CENTER,
        BOTTOM
    }

    enum class Duration(val value: Int) {
        SHORT(Toast.LENGTH_SHORT),
        LONG(Toast.LENGTH_LONG)
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var pendingTask: Runnable? = null

    fun show(
        context: Context,
        text: String,
        style: Style = Style.TEXT,
        position: Position = Position.CENTER,
        @DrawableRes customIcon: Int? = null,
        duration: Duration = Duration.SHORT,
        code: Int? = null,
        extensionInfo: Map<String, Any>? = null
    ) {
        if (text.isBlank()) return

        pendingTask?.let {
            mainHandler.removeCallbacks(it)
            pendingTask = null
        }

        val appContext = context.applicationContext

        val task = object : Runnable {
            override fun run() {
                executeRealShow(appContext, text, position, duration)
                if (code != null) {
                    reportAtomicEvent(code, text, extensionInfo)
                }
                if (pendingTask === this) {
                    pendingTask = null
                }
            }
        }
        pendingTask = task
        if (Looper.myLooper() == Looper.getMainLooper()) {
            task.run()
        } else {
            mainHandler.post(task)
        }
    }

    @androidx.annotation.MainThread
    internal fun showScoped(context: Context, text: String): Toast =
        executeRealShow(context.applicationContext, text, Position.CENTER, Duration.SHORT)

    private fun executeRealShow(
        appContext: Context,
        text: String,
        position: Position,
        duration: Duration,
    ): Toast {
        val view = createToastView(appContext, text)
        val toast = Toast(appContext)

        @Suppress("DEPRECATION")
        toast.view = view

        toast.duration = duration.value
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

    private fun createToastView(context: Context, text: String): LiveText {
        val density = context.resources.displayMetrics.density
        fun dp(value: Float): Float = value * density
        fun color(colorRes: Int): Int = ContextCompat.getColor(context, colorRes)

        return LiveText(context).apply {
            role = LiveTheme.TextRole.SECTION_TITLE
            tone = LiveTheme.TextTone.PRIMARY
            gravity = Gravity.CENTER
            includeFontPadding = false
            minHeight = dp(34f).toInt()
            val paddingH = dp(16f).toInt()
            val paddingV = dp(9f).toInt()
            setPadding(paddingH, paddingV, paddingH, paddingV)
            setText(text, lineHeightSp = 16f)
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(
                    color(R.color.livekit_toast_gradient_start),
                    color(R.color.livekit_toast_gradient_middle),
                    color(R.color.livekit_toast_gradient_end),
                ),
            ).apply {
                cornerRadius = dp(17f)
                setStroke(dp(1f).toInt(), color(R.color.livekit_toast_border))
            }
            elevation = dp(24f)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                outlineSpotShadowColor = color(R.color.livekit_toast_glow)
                outlineAmbientShadowColor = Color.TRANSPARENT
            }
            isClickable = false
            isFocusable = false
        }
    }

    private fun reportAtomicEvent(code: Int, message: String?, extensionInfo: Map<String, Any>?) {
        val params = JSONObject().apply {
            put("event_id", ATOMIC_EVENT_ID)
            put("event_code", code)
            put("event_message", message)
            put("more_message", FRAMEWORK_NAME)
            put("extension_message", extensionInfo)
        }.toString()
        V2TIMManager.getInstance().callExperimentalAPI("reportRoomEngineEvent", params, null)
    }
}