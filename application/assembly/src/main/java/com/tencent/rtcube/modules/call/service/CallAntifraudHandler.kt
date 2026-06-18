package com.tencent.rtcube.modules.call.service

import com.tencent.rtcube.assembly.AppAssembly
import com.tencent.rtcube.assembly.PrivacyAction
import io.trtc.tuikit.atomicxcore.api.call.CallParticipantStatus
import io.trtc.tuikit.atomicxcore.api.call.CallStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object CallAntifraudHandler {

    private const val TAG = "CallAntifraudHandler"

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var job: Job? = null
    private var currentStatus: CallParticipantStatus? = null

    fun register() {
        job?.cancel()
        currentStatus = null
        job = scope.launch {
            CallStore.shared.observerState.selfInfo.collect { selfInfo ->
                if (selfInfo.status == CallParticipantStatus.Accept && currentStatus != CallParticipantStatus.Accept) {
                    AppAssembly.privacyActionHandler?.invoke(PrivacyAction.ShowAntifraudReminder)
                }
                currentStatus = selfInfo.status
            }
        }
    }

    fun unregister() {
        job?.cancel()
        job = null
        currentStatus = null
    }
}
