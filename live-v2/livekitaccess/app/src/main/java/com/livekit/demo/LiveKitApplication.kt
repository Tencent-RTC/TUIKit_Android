package com.livekit.demo

import android.app.Application

class LiveKitApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        APPConfig.init(this)
    }
}
