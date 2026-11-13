package com.tencent.uikit.app

import android.app.Application
import com.tencent.mmkv.MMKV
import com.tencent.uikit.app.call.entry.CallEntryState

class App : Application() {
    val callEntryState: CallEntryState by lazy { CallEntryState(this) }

    override fun onCreate() {
        super.onCreate()
        MMKV.initialize(this)
    }
}
