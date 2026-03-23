package com.example.atomicxcore

import android.app.Application

/**
 * Application entry point
 * Corresponds to iOS's AppDelegate
 */
class App : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        lateinit var instance: App
            private set
    }
}
