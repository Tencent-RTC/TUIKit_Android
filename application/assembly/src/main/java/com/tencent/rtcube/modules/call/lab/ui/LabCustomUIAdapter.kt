package com.tencent.rtcube.modules.call.lab.ui

import android.graphics.Color
import android.view.ViewGroup
import android.widget.Button
import androidx.constraintlayout.widget.ConstraintLayout
import com.tencent.qcloud.tuikit.tuicallkit.manager.CallManager
import com.tencent.qcloud.tuikit.tuicallkit.view.CallAdapter
import io.trtc.tuikit.atomicxcore.api.call.CallParticipantStatus
import io.trtc.tuikit.atomicxcore.api.call.CallStore

class LabCustomUIAdapter {
    val streamViewAdapter: CallAdapter = object : CallAdapter() {
        override fun onCreateStreamView(view: ViewGroup): ViewGroup {
            val buttonHangup = Button(view.context)
            buttonHangup.text = "Hangup"
            buttonHangup.setBackgroundColor(Color.CYAN)
            buttonHangup.setOnClickListener {
                val selfUser = CallStore.Companion.shared.observerState.selfInfo.value
                val callerId = CallStore.Companion.shared.observerState.activeCall.value.inviterId
                if (selfUser.id == callerId && selfUser.status == CallParticipantStatus.Waiting) {
                    CallManager.Companion.instance.reject(null)
                } else {
                    CallManager.Companion.instance.hangup(null)
                }
            }
            val lp = ConstraintLayout.LayoutParams(200, 120)
            lp.topToTop = ConstraintLayout.LayoutParams.PARENT_ID
            lp.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
            lp.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
            lp.startToStart = ConstraintLayout.LayoutParams.PARENT_ID
            buttonHangup.layoutParams = lp
            view.addView(buttonHangup)
            return view
        }
    }

    val mainViewAdapter: CallAdapter = object : CallAdapter() {
        override fun onCreateMainView(view: ViewGroup): ViewGroup {
            val button = Button(view.context)
            button.setBackgroundColor(Color.LTGRAY)
            val lp = ConstraintLayout.LayoutParams(120, 120)
            lp.topToTop = ConstraintLayout.LayoutParams.PARENT_ID
            lp.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
            lp.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
            lp.startToStart = ConstraintLayout.LayoutParams.PARENT_ID
            button.layoutParams = lp
            view.addView(button)
            return view
        }
    }
}