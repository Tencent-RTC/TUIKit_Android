package com.tencent.effect.beautykit.tuiextension.utils

import com.tencent.effect.beautykit.utils.LogUtils
import java.util.Timer
import java.util.TimerTask

class TimerManager @JvmOverloads constructor(
    private val mTimerCallback: TimerManagerCallback?,
    private val intervalTime: Int = 30
) {

    private val TAG = TimerManager::class.java.name

    @Volatile
    private var taskState = TaskState.INIT
    private var timer: Timer? = null

    private fun createTask(): TimerTask {
        return object : TimerTask() {
            override fun run() {
                mTimerCallback?.onCall()
            }
        }
    }

    fun start() {
        LogUtils.d(TAG, "start")
        if (taskState == TaskState.RELEASE || taskState == TaskState.STARTED) {
            return
        }
        taskState = TaskState.STARTED
        timer = Timer()
        timer?.scheduleAtFixedRate(createTask(), 0, intervalTime.toLong())
    }

    fun pause() {
        LogUtils.d(TAG, "pause")
        taskState = TaskState.PAUSED
        timer?.cancel()
        timer?.purge()
        timer = null
    }

    fun release() {
        LogUtils.d(TAG, "release")
        taskState = TaskState.RELEASE
        timer?.cancel()
        timer?.purge()
        timer = null
    }

    fun interface TimerManagerCallback {
        fun onCall()
    }

    private enum class TaskState {
        INIT, STARTED, PAUSED, RELEASE
    }
}
