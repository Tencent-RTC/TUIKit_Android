package com.trtc.uikit.roomkit.base.toast

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.content.ContextCompat
import com.trtc.uikit.roomkit.R
import com.trtc.uikit.roomkit.base.util.dp

object RoomToast {

    enum class Duration(val value: Int) {
        SHORT(Toast.LENGTH_SHORT),
        LONG(Toast.LENGTH_LONG),
    }

    private const val TOAST_TEXT_MAX_WIDTH_DP = 260

    private val mainHandler = Handler(Looper.getMainLooper())

    fun show(
        context: Context,
        text: CharSequence,
        duration: Duration = Duration.SHORT,
    ) {
        if (text.isBlank()) return
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { show(context, text, duration) }
            return
        }
        val appContext = context.applicationContext
        val paddingHorizontal = appContext.dp(16)
        val paddingVertical = appContext.dp(6)

        val label = AppCompatTextView(appContext).apply {
            this.text = text
            setTextColor(ContextCompat.getColor(appContext, R.color.roomkit_fg_on_accent))
            textSize = 13f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            includeFontPadding = false
            gravity = Gravity.CENTER
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            maxWidth = minOf(
                appContext.dp(TOAST_TEXT_MAX_WIDTH_DP),
                appContext.resources.displayMetrics.widthPixels - appContext.dp(48) - paddingHorizontal * 2,
            )
        }

        val container = FrameLayout(appContext).apply {
            setPadding(paddingHorizontal, paddingVertical, paddingHorizontal, paddingVertical)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = appContext.dp(8).toFloat()
                setColor(ContextCompat.getColor(appContext, R.color.roomkit_toast_bg))
            }
            addView(
                label,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER,
                )
            )
        }

        Toast(appContext).apply {
            @Suppress("DEPRECATION")
            setView(container)
            setDuration(duration.value)
            setGravity(Gravity.CENTER, 0, 0)
            show()
        }
    }
}
