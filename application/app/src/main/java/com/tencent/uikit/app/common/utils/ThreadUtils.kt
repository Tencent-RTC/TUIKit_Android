package com.tencent.uikit.app.common.utils

import android.os.Handler
import android.os.Looper
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

object ThreadUtils {
    private val handler = Handler(Looper.getMainLooper())
    private val executors: ExecutorService = Executors.newCachedThreadPool()

    fun execute(runnable: Runnable) {
        executors.execute(runnable)
    }

    fun postOnUiThread(runnable: Runnable): Boolean {
        return handler.post(runnable)
    }
}