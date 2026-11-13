package com.tencent.uikit.app.call.common

import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import kotlin.math.max

internal object WindowInsetsHelper {

    fun applySystemBarsInset(view: View) {
        val initialPaddingTop = view.paddingTop
        val initialPaddingBottom = view.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(view) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(
                view.paddingLeft,
                initialPaddingTop + bars.top,
                view.paddingRight,
                initialPaddingBottom + bars.bottom
            )
            insets
        }
    }

    fun applyStatusBarInset(view: View) {
        val initialPaddingTop = view.paddingTop
        ViewCompat.setOnApplyWindowInsetsListener(view) { _, insets ->
            val top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            view.setPadding(view.paddingLeft, initialPaddingTop + top, view.paddingRight, view.paddingBottom)
            insets
        }
    }

    fun applyNavigationBarInset(view: View) {
        val initialPaddingBottom = view.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(view) { _, insets ->
            val bottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            view.setPadding(view.paddingLeft, view.paddingTop, view.paddingRight, initialPaddingBottom + bottom)
            insets
        }
    }

    /** 仅叠加键盘底部高度（键盘收起时为 0），供已处理导航栏的场景使用。 */
    fun applyImeInset(view: View) {
        val initialPaddingBottom = view.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(view) { _, insets ->
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            view.setPadding(view.paddingLeft, view.paddingTop, view.paddingRight, initialPaddingBottom + ime)
            insets
        }
    }

    fun applyImeAndNavInset(view: View) {
        val initialPaddingBottom = view.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(view) { _, insets ->
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            val nav = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            view.setPadding(
                view.paddingLeft,
                view.paddingTop,
                view.paddingRight,
                initialPaddingBottom + max(ime, nav)
            )
            insets
        }
    }
}
