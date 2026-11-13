package com.tencent.qcloud.tuikit.tuicallkit.view

import android.view.ViewGroup

abstract class CallAdapter {
    open fun onCreateStreamView(view: ViewGroup): ViewGroup? {
        return null
    }

    open fun onCreateMainView(view: ViewGroup): ViewGroup? {
        return null
    }
}
