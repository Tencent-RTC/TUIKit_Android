package com.tencent.uikit.app.common.utils

import android.content.Context
import android.content.res.Configuration
import android.view.View
import io.trtc.tuikit.atomicxcore.impl.common.ContextProvider

object LayoutUtil {
    fun isRTL(): Boolean {
        val context: Context = ContextProvider.getApplicationContext() ?: return false
        val configuration: Configuration = context.resources.configuration
        val layoutDirection = configuration.layoutDirection
        return layoutDirection == View.LAYOUT_DIRECTION_RTL
    }
}