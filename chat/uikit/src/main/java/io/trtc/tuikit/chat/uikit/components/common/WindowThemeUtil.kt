package io.trtc.tuikit.chat.uikit.components.common

import android.graphics.drawable.ColorDrawable
import android.view.Window
import androidx.core.view.WindowInsetsControllerCompat
import io.trtc.tuikit.atomicx.theme.tokens.ColorTokens

internal object WindowThemeUtil {

    fun applyDialogSystemBarStyle(window: Window, colors: ColorTokens, backgroundColor: Int = colors.bgColorOperate) {
        window.setBackgroundDrawable(ColorDrawable(backgroundColor))
        window.statusBarColor = backgroundColor
        window.navigationBarColor = backgroundColor

        val controller = WindowInsetsControllerCompat(window, window.decorView)
        val isLight = isLightColor(backgroundColor)
        controller.isAppearanceLightStatusBars = isLight
        controller.isAppearanceLightNavigationBars = isLight
    }

    private fun isLightColor(color: Int): Boolean {
        val red = (color shr 16) and 0xFF
        val green = (color shr 8) and 0xFF
        val blue = color and 0xFF
        val luminance = (0.299 * red + 0.587 * green + 0.114 * blue) / 255
        return luminance > 0.5
    }
}
