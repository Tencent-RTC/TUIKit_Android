package io.trtc.tuikit.chat.uikit.components.common

import android.content.Context
import android.content.ContextWrapper
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelStoreOwner

internal tailrec fun Context.findViewModelStoreOwner(): ViewModelStoreOwner? {
    return when (this) {
        is ViewModelStoreOwner -> this
        is ContextWrapper -> baseContext.findViewModelStoreOwner()
        else -> null
    }
}

internal tailrec fun Context.findLifecycleOwner(): LifecycleOwner? {
    return when (this) {
        is LifecycleOwner -> this
        is ContextWrapper -> baseContext.findLifecycleOwner()
        else -> null
    }
}
