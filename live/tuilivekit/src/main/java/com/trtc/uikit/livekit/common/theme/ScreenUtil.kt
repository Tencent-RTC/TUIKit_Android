package com.trtc.uikit.livekit.common.theme

import android.content.Context
import android.util.DisplayMetrics
import android.view.WindowManager
import com.trtc.uikit.livekit.common.ContextProvider

object ScreenUtil {
    @JvmStatic
    fun getScreenWidth(context: Context): Int {
        val metric = DisplayMetrics()
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        wm.defaultDisplay.getMetrics(metric)
        return metric.widthPixels
    }

    @JvmStatic
    fun getRealScreenHeight(context: Context): Int {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val display = wm.defaultDisplay
        val dm = DisplayMetrics()
        display.getRealMetrics(dm)
        return dm.heightPixels
    }

    @JvmStatic
    fun getRealScreenWidth(context: Context): Int {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val display = wm.defaultDisplay
        val dm = DisplayMetrics()
        display.getRealMetrics(dm)
        return dm.widthPixels
    }

    @JvmStatic
    fun dip2px(dpValue: Float): Int {
        val scale =
            ContextProvider.Companion.applicationContext?.resources?.displayMetrics?.density ?: 1.0f
        return (dpValue * scale + 0.5f).toInt()
    }

    fun Int.dp() = dip2px(this.toFloat())
}