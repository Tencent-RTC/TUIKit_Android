package com.trtc.uikit.roomkit.view.main.participantlist

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.widget.AppCompatButton
import com.trtc.uikit.roomkit.R
import com.trtc.uikit.roomkit.base.error.ErrorLocalized
import com.trtc.uikit.roomkit.base.log.RoomKitLogger
import io.trtc.tuikit.atomicx.widget.basicwidget.toast.AtomicToast
import io.trtc.tuikit.atomicx.widget.basicwidget.toast.AtomicToast.Style
import io.trtc.tuikit.atomicxcore.api.room.CallUserToRoomCompletionHandler
import io.trtc.tuikit.atomicxcore.api.room.RoomCallResult
import io.trtc.tuikit.atomicxcore.api.room.RoomParticipantStatus
import io.trtc.tuikit.atomicxcore.api.room.RoomStore

class CallUserView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    companion object {
        private const val NOT_ENTERED_DISMISS_DELAY_MS = 3000L
    }

    private val logger = RoomKitLogger.getLogger("CallUserView")

    private val tvNotEnterForNow: TextView
    private val btnCall: AppCompatButton
    private val mainHandler = Handler(Looper.getMainLooper())

    private var roomID: String = ""
    private var userID: String = ""
    private var currentStatus: RoomParticipantStatus? = null

    init {
        LayoutInflater.from(context).inflate(R.layout.roomkit_view_call_user_item, this, true)
        tvNotEnterForNow = findViewById(R.id.tv_not_enter_for_now)
        btnCall = findViewById(R.id.btn_call_user)

        btnCall.setOnClickListener {
            if (btnCall.isSelected) {
                return@setOnClickListener
            }
            triggerCall()
        }
    }

    fun bind(roomID: String, userID: String, status: RoomParticipantStatus) {
        this.roomID = roomID
        this.userID = userID
        val previousStatus = currentStatus
        currentStatus = status

        val isCalling = status == RoomParticipantStatus.IN_CALLING
        btnCall.isSelected = isCalling
        btnCall.text =
            context.getString(
                if (isCalling) R.string.roomkit_calling else R.string.roomkit_call
            )

        if (status == RoomParticipantStatus.CALL_REJECTED) {
            if (previousStatus != RoomParticipantStatus.CALL_REJECTED) {
                showNotEnterForNowTip()
            }
        } else {
            mainHandler.removeCallbacksAndMessages(null)
            tvNotEnterForNow.visibility = INVISIBLE
        }
    }

    private fun showNotEnterForNowTip() {
        mainHandler.removeCallbacksAndMessages(null)
        tvNotEnterForNow.visibility = VISIBLE
        mainHandler.postDelayed({
            tvNotEnterForNow.visibility = INVISIBLE
        }, NOT_ENTERED_DISMISS_DELAY_MS)
    }

    private fun triggerCall() {
        if (roomID.isEmpty() || userID.isEmpty()) {
            logger.warn("triggerCall skipped: roomID or userID empty")
            return
        }
        logger.info("callUserToRoom userID=$userID roomID=$roomID")
        RoomStore.shared().callUserToRoom(
            roomID,
            listOf(userID),
            60,
            "",
            object : CallUserToRoomCompletionHandler {
                override fun onSuccess(result: Map<String, RoomCallResult>) {
                    logger.info("callUserToRoom success: $result")
                    AtomicToast.show(context, context.getString(R.string.roomkit_invite_success), Style.SUCCESS)
                }

                override fun onFailure(code: Int, desc: String) {
                    logger.error("callUserToRoom failed: code=$code, desc=$desc")
                    ErrorLocalized.showError(context, code)
                }
            }
        )
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        mainHandler.removeCallbacksAndMessages(null)
    }
}
