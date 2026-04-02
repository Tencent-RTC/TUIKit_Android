package com.trtc.uikit.roomkit.view.main

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.constraintlayout.utils.widget.ImageFilterView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.trtc.uikit.roomkit.R
import com.trtc.uikit.roomkit.base.error.ErrorLocalized
import com.trtc.uikit.roomkit.base.extension.getSenderDisplayName
import com.trtc.uikit.roomkit.base.log.RoomKitLogger
import com.trtc.uikit.roomkit.base.ui.BaseView
import io.trtc.tuikit.atomicx.common.imageloader.ImageLoader
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.device.DeviceType
import io.trtc.tuikit.atomicxcore.api.room.DeviceRequestInfo
import io.trtc.tuikit.atomicxcore.api.room.RoomParticipantStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class HandsUpListView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : BaseView(context, attrs, defStyleAttr) {

    private val logger = RoomKitLogger.getLogger("HandsUpListView")
    private var subscribeJob: Job? = null

    private val tvEmpty: TextView by lazy { findViewById(R.id.tv_empty) }
    private val rvHandsUpList: RecyclerView by lazy { findViewById(R.id.rv_hands_up_list) }

    private var participantStore: RoomParticipantStore? = null
    private val adapter = HandsUpRequestAdapter()

    init {
        LayoutInflater.from(context).inflate(R.layout.roomkit_view_hands_up_list, this)
    }

    public override fun init(roomID: String) {
        rvHandsUpList.layoutManager = LinearLayoutManager(context)
        rvHandsUpList.adapter = adapter
        super.init(roomID)
    }

    override fun initStore(roomID: String) {
        participantStore = RoomParticipantStore.create(roomID)
    }

    override fun addObserver() {
        subscribeJob?.cancel()
        subscribeJob = CoroutineScope(Dispatchers.Main).launch {
            participantStore?.state?.pendingDeviceApplications?.collect { applications ->
                val micApplications = applications.filter { it.device == DeviceType.MICROPHONE }
                logger.info("pendingDeviceApplications updated: size=${micApplications.size}")
                updateHandsUpList(micApplications)
            }
        }
    }

    override fun removeObserver() {
        subscribeJob?.cancel()
    }

    private fun updateHandsUpList(list: List<DeviceRequestInfo>) {
        adapter.updateData(list)
        val isEmpty = list.isEmpty()
        tvEmpty.visibility = if (isEmpty) VISIBLE else GONE
        rvHandsUpList.visibility = if (isEmpty) GONE else VISIBLE
    }

    private fun handleApprove(request: DeviceRequestInfo) {
        val store = participantStore ?: return
        logger.info("Approving hands up request from ${request.senderUserID}")
        store.promoteAudienceToParticipant(
            userID = request.senderUserID,
            completion = object : CompletionHandler {
                override fun onSuccess() {
                    logger.info("Promoted audience to participant: ${request.senderUserID}")
                    approveDeviceRequest(store, request)
                }

                override fun onFailure(code: Int, desc: String) {
                    logger.error("Failed to promote audience to participant: code=$code, desc=$desc")
                    ErrorLocalized.showError(context, code)
                }
            }
        )
    }

    private fun approveDeviceRequest(store: RoomParticipantStore, request: DeviceRequestInfo) {
        store.approveOpenDeviceRequest(
            device = request.device,
            userID = request.senderUserID,
            completion = object : CompletionHandler {
                override fun onSuccess() {
                    logger.info("Approved hands up request from ${request.senderUserID}")
                }

                override fun onFailure(code: Int, desc: String) {
                    logger.error("Failed to approve hands up request: code=$code, desc=$desc")
                    ErrorLocalized.showError(context, code)
                }
            }
        )
    }

    private fun handleReject(request: DeviceRequestInfo) {
        val store = participantStore ?: return
        logger.info("Rejecting hands up request from ${request.senderUserID}")
        store.rejectOpenDeviceRequest(
            device = request.device,
            userID = request.senderUserID,
            completion = object : CompletionHandler {
                override fun onSuccess() {
                    logger.info("Rejected hands up request from ${request.senderUserID}")
                }

                override fun onFailure(code: Int, desc: String) {
                    logger.error("Failed to reject hands up request: code=$code, desc=$desc")
                    ErrorLocalized.showError(context, code)
                }
            }
        )
    }

    private inner class HandsUpRequestAdapter : RecyclerView.Adapter<HandsUpRequestViewHolder>() {
        private var requestList: List<DeviceRequestInfo> = emptyList()

        fun updateData(data: List<DeviceRequestInfo>) {
            requestList = data
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HandsUpRequestViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.roomkit_item_hands_up_request, parent, false)
            return HandsUpRequestViewHolder(view)
        }

        override fun onBindViewHolder(holder: HandsUpRequestViewHolder, position: Int) {
            val request = requestList[position]
            holder.bind(request)
        }

        override fun getItemCount(): Int = requestList.size
    }

    private inner class HandsUpRequestViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivAvatar: ImageFilterView = itemView.findViewById(R.id.iv_avatar)
        private val tvUsername: TextView = itemView.findViewById(R.id.tv_username)
        private val btnReject: View = itemView.findViewById(R.id.btn_reject)
        private val btnAgree: View = itemView.findViewById(R.id.btn_agree)

        fun bind(request: DeviceRequestInfo) {
            tvUsername.text = request.getSenderDisplayName()
            if (request.senderAvatarURL.isEmpty()) {
                ivAvatar.setImageResource(R.drawable.roomkit_ic_default_avatar)
            } else {
                ImageLoader.load(
                    itemView.context,
                    ivAvatar,
                    request.senderAvatarURL,
                    R.drawable.roomkit_ic_default_avatar
                )
            }

            btnAgree.setOnClickListener {
                handleApprove(request)
            }

            btnReject.setOnClickListener {
                handleReject(request)
            }
        }
    }
}
