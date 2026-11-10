package com.trtc.uikit.roomkit.base.util

import android.app.Activity
import android.content.Context
import android.graphics.Rect
import android.view.MotionEvent
import android.view.inputmethod.InputMethodManager
import android.widget.EditText

fun Activity.dismissKeyboardOnOutsideTouch(event: MotionEvent) {
    if (event.actionMasked != MotionEvent.ACTION_DOWN) return
    val focused = currentFocus as? EditText ?: return
    val hitRect = Rect()
    focused.getHitRect(hitRect)
    if (!hitRect.contains(event.x.toInt(), event.y.toInt())) {
        (getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)
            ?.hideSoftInputFromWindow(focused.windowToken, 0)
        focused.clearFocus()
    }
}
