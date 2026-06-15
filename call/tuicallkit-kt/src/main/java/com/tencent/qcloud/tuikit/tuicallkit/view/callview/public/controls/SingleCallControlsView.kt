package com.tencent.qcloud.tuikit.tuicallkit.view.callview.public.controls

import android.content.Context
import android.widget.RelativeLayout
import androidx.constraintlayout.widget.ConstraintLayout
import com.tencent.qcloud.tuikit.tuicallkit.view.callview.core.common.utils.Logger
import io.trtc.tuikit.atomicxcore.api.call.CallMediaType
import io.trtc.tuikit.atomicxcore.api.call.CallStore
import io.trtc.tuikit.atomicxcore.api.call.CallParticipantStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class SingleCallControlsView(context: Context) : ConstraintLayout(context) {
    private var subscribeStateJob: Job? = null
    private var functionLayout: RelativeLayout? = null
    private var callStatus = CallParticipantStatus.None

    var enableVirtualBackground: Boolean = true

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        updateLayout()
        registerSelfObserver()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        subscribeStateJob?.cancel()
    }

    private fun registerSelfObserver() {
        subscribeStateJob = CoroutineScope(Dispatchers.Main).launch {
            CallStore.shared.observerState.selfInfo.collect { selfInfo ->
                if (callStatus != selfInfo.status && selfInfo.status == CallParticipantStatus.Accept) {
                    callStatus = selfInfo.status
                    updateLayout()
                }
            }
        }
    }

    private fun updateLayout() {
        val mediaType = CallStore.shared.observerState.activeCall.value.mediaType
        val selfStatus = CallStore.shared.observerState.selfInfo.value.status

        when {
            selfStatus == CallParticipantStatus.Waiting && !selfIsCaller() -> {
                functionLayout = AudioAndVideoCalleeWaitingView(context)
            }

            selfStatus == CallParticipantStatus.Waiting && selfIsCaller() -> {
                functionLayout = when (mediaType) {
                    CallMediaType.Video -> VideoCallerWaitingView(context).apply {
                        this.enableVirtualBackground = this@SingleCallControlsView.enableVirtualBackground
                    }
                    else -> AudioCallerWaitingAndAcceptedView(context)
                }
            }

            selfStatus == CallParticipantStatus.Accept -> {
                functionLayout = when (mediaType) {
                    CallMediaType.Video -> VideoCallerAndCalleeAcceptedView(context).apply {
                        this.enableVirtualBackground = this@SingleCallControlsView.enableVirtualBackground
                    }
                    else -> AudioCallerWaitingAndAcceptedView(context)
                }
            }
        }

        if (functionLayout == null) {
            Logger.e("functionLayout == null")
        } else {
            removeAllViews()
            addView(functionLayout)
        }
    }

    private fun selfIsCaller(): Boolean {
        val selfId = CallStore.shared.observerState.selfInfo.value.id
        val callerId = CallStore.shared.observerState.activeCall.value.inviterId
        return selfId == callerId
    }
}