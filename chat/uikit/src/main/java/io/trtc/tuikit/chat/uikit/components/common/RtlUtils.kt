package io.trtc.tuikit.chat.uikit.components.common

import android.content.Context
import android.view.View

object RtlUtils {
    fun isLocaleRtl(context: Context): Boolean {
        return context.resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL
    }
}
