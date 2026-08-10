package io.trtc.tuikit.chat.uikit.components.common

import android.content.Context
import android.view.View
import android.view.inputmethod.InputMethodManager

internal fun View.hideKeyboard() {
    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
    imm?.hideSoftInputFromWindow(windowToken, 0)
}

internal fun View.showKeyboard() {
    requestFocus()
    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
    imm?.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
}
