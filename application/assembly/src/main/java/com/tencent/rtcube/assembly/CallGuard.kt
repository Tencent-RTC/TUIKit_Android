package com.tencent.rtcube.assembly

import android.content.Context
import android.widget.Toast
import com.tencent.rtcube.modules.R
import io.trtc.tuikit.atomicxcore.api.call.CallParticipantStatus
import io.trtc.tuikit.atomicxcore.api.call.CallStore

object CallGuard {
    val canStartNewRoom: Boolean
        get() = CallStore.shared.observerState.selfInfo.value.status == CallParticipantStatus.None

    fun showCannotStartRoomToast(context: Context) {
        Toast.makeText(context, context.getString(R.string.assembly_common_cannot_start_room_during_call), Toast.LENGTH_SHORT).show()
    }
}