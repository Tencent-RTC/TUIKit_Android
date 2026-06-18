package com.tencent.rtcube.modules.live.service

import android.os.Handler
import android.os.Looper
import com.tencent.rtcube.assembly.AppAssembly
import com.tencent.rtcube.assembly.PrivacyAction
import com.tencent.rtcube.v2.login.LoginEntry
import io.trtc.tuikit.atomicxcore.api.live.LiveListStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object LiveTimeLimitHandler {

    private const val NINE_MINUTE_MS = 9 * 60 * 1000L

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var job: Job? = null
    private var liveId: String? = ""

    private val remainingTimerHandler = Handler(Looper.getMainLooper())
    private val remainingTimerRunnable = Runnable {
        AppAssembly.privacyActionHandler?.invoke(PrivacyAction.ShowLiveRemainingOneMinToast)
    }

    fun updateLiveID(currentLiveId: String) {
        liveId = currentLiveId
    }

    fun register() {
        job?.cancel()
        job = scope.launch {
            LiveListStore.shared().liveState.currentLive.collect { currentLive ->
                val roomId = liveId ?: return@collect
                val selfUseriId = LoginEntry.currentUser.value?.userId ?: return@collect
                if (currentLive.liveID == roomId && currentLive.liveOwner.userID == selfUseriId) {
                    liveId = ""
                    onLiveStarted()
                }
            }
        }
    }

    fun unregister() {
        job?.cancel()
        job = null
        liveId = null
        remainingTimerHandler.removeCallbacks(remainingTimerRunnable)
    }

    private fun onLiveStarted() {
        AppAssembly.privacyActionHandler?.invoke(PrivacyAction.ShowLiveTimeLimitAlert)
        remainingTimerHandler.removeCallbacks(remainingTimerRunnable)
        remainingTimerHandler.postDelayed(remainingTimerRunnable, NINE_MINUTE_MS)
    }
}
