package com.example.atomicxcore.utils

import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/**
 * Adds system status bar height as top padding to the View, used for Toolbar adaptation in immersive pages.
 *
 * Applicable scenario: Full-screen immersive pages like live streaming where the video fills the entire screen
 * (cannot use fitsSystemWindows), but the Toolbar needs to avoid the status bar area to prevent content overlap.
 */
fun View.applyStatusBarTopPadding() {
    ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
        val statusBarHeight = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
        view.setPadding(
            view.paddingLeft,
            statusBarHeight,
            view.paddingRight,
            view.paddingBottom
        )
        insets
    }
    requestApplyInsets()
}
