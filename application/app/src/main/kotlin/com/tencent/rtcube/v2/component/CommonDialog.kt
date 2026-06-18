package com.tencent.rtcube.v2.component

import android.app.Activity
import android.app.Dialog
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

/**
 * Generic Compose dialog host.
 *
 * @param gravity         Window gravity. Defaults to [Gravity.CENTER].
 * @param matchParentWidth Whether the dialog window should occupy full screen width.
 * @param dimAmount        Background dim amount in [0f, 1f]. `null` keeps the system default
 *                         (no extra dim, fully transparent background).
 */
fun Activity.showComposeDialog(
    gravity: Int = Gravity.CENTER,
    matchParentWidth: Boolean = false,
    dimAmount: Float? = null,
    content: @Composable (dialog: Dialog) -> Unit,
) {
    if (isFinishing || isDestroyed) return

    val dialog = Dialog(this, android.R.style.Theme_Translucent_NoTitleBar)
    val composeView = ComposeView(this).apply {
        (this@showComposeDialog as? LifecycleOwner)?.let { setViewTreeLifecycleOwner(it) }
        (this@showComposeDialog as? ViewModelStoreOwner)?.let { setViewTreeViewModelStoreOwner(it) }
        (this@showComposeDialog as? SavedStateRegistryOwner)?.let { setViewTreeSavedStateRegistryOwner(it) }
        setContent { content(dialog) }
    }
    val widthSpec = if (matchParentWidth) ViewGroup.LayoutParams.MATCH_PARENT else ViewGroup.LayoutParams.WRAP_CONTENT
    dialog.setContentView(composeView, ViewGroup.LayoutParams(widthSpec, ViewGroup.LayoutParams.WRAP_CONTENT))
    dialog.window?.apply {
        setGravity(gravity)
        if (matchParentWidth) {
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        if (dimAmount != null) {
            setBackgroundDrawableResource(android.R.color.transparent)
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            val attrs = attributes
            attrs.dimAmount = dimAmount
            attributes = attrs
        }
    }
    dialog.show()
}
