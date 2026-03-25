package com.trtc.uikit.livekit.voiceroom.view.seatmanager

import android.annotation.SuppressLint
import android.content.Context
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.trtc.uikit.livekit.R
import com.trtc.uikit.livekit.common.ErrorLocalized
import com.trtc.uikit.livekit.common.LiveKitLogger
import com.trtc.uikit.livekit.common.completionHandler
import io.trtc.tuikit.atomicx.widget.basicwidget.avatar.AtomicAvatar
import io.trtc.tuikit.atomicx.widget.basicwidget.avatar.AtomicAvatar.AvatarContent
import io.trtc.tuikit.atomicx.widget.basicwidget.button.AtomicButton
import io.trtc.tuikit.atomicx.widget.basicwidget.button.ButtonColorType
import io.trtc.tuikit.atomicx.widget.basicwidget.button.ButtonSize
import io.trtc.tuikit.atomicx.widget.basicwidget.button.ButtonVariant
import io.trtc.tuikit.atomicx.widget.basicwidget.toast.AtomicToast
import io.trtc.tuikit.atomicxcore.api.live.CoGuestStore
import io.trtc.tuikit.atomicxcore.api.live.CoHostStore
import io.trtc.tuikit.atomicxcore.api.live.LiveListStore
import io.trtc.tuikit.atomicxcore.api.live.LiveSeatStore
import io.trtc.tuikit.atomicxcore.api.live.LiveUserInfo

class SeatApplicationAdapter(
    private val context: Context,
) : RecyclerView.Adapter<SeatApplicationAdapter.ViewHolder>() {
    private val liveListStore = LiveListStore.shared()
    private val coHostStore = CoHostStore.create(liveListStore.liveState.currentLive.value.liveID)
    private val liveSeatStore =
        LiveSeatStore.create(liveListStore.liveState.currentLive.value.liveID)
    private val coGuestStore =
        CoGuestStore.create(LiveListStore.shared().liveState.currentLive.value.liveID)
    private val data: MutableList<LiveUserInfo> =
        coGuestStore.coGuestState.applicants.value.toMutableList()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(context).inflate(
            R.layout.livekit_layout_voiceroom_item_seat_application,
            parent,
            false
        )
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val request = data[position]
        if (!TextUtils.isEmpty(request.userName)) {
            holder.textName.text = request.userName
        } else {
            holder.textName.text = request.userID
        }
        holder.imageHead.setContent(
            AvatarContent.URL(
                request.avatarURL,
                R.drawable.livekit_ic_avatar
            )
        )

        holder.buttonAccept.apply {
            text = context.getString(R.string.common_accept)
            setOnClickListener { acceptApplication(request.userID) }
        }

        holder.buttonReject.apply {
            text = context.getString(R.string.common_reject)
            setOnClickListener { rejectApplication(request.userID) }
        }
    }

    private fun acceptApplication(userId: String) {
        val isConnected =
            coHostStore.coHostState.connected.value.any { it.liveID == LiveListStore.shared().liveState.currentLive.value.liveID }
        if (isConnected && !hasAvailableSeat()) {
            AtomicToast.show(
                context,
                context.getString(R.string.common_server_error_the_seats_are_all_taken),
                AtomicToast.Style.ERROR
            )
            return
        }
        coGuestStore.acceptApplication(userId, completionHandler {
            onError { code, desc ->
                LOGGER.error("acceptApplication failed,error:$code,message:$desc")
                ErrorLocalized.onError(code)
            }
        })
    }

    private fun hasAvailableSeat(): Boolean {
        val allSeats = liveSeatStore.liveSeatState.seatList.value
        if (allSeats.isEmpty()) {
            return false
        }
        val currentLiveId = liveListStore.liveState.currentLive.value.liveID
        if (currentLiveId.isEmpty()) {
            return false
        }

        val currentRoomSeats = allSeats.filter { seat ->
            seat.userInfo.liveID == currentLiveId
        }

        return currentRoomSeats.take(6).any { seat ->
            val userId = seat.userInfo.userID
            val isLocked = seat.isLocked
            userId.isEmpty() && !isLocked
        }
    }

    private fun rejectApplication(userId: String) {
        coGuestStore.rejectApplication(userId, completionHandler {
            onError { code, desc ->
                LOGGER.error("rejectApplication failed,error:$code,message:$desc")
                ErrorLocalized.onError(code)
            }
        })
    }

    @SuppressLint("NotifyDataSetChanged")
    fun updateData() {
        data.clear()
        data.addAll(coGuestStore.coGuestState.applicants.value)
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int {
        return data.size
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imageHead: AtomicAvatar = itemView.findViewById(R.id.iv_head)
        val textName: TextView = itemView.findViewById(R.id.tv_name)
        val buttonAccept: TextView = itemView.findViewById(R.id.atomic_btn_accept)
        val buttonReject: TextView = itemView.findViewById(R.id.atomic_btn_reject)
    }

    companion object {
        private val LOGGER = LiveKitLogger.getVoiceRoomLogger("SeatApplicationAdapter")
    }
}