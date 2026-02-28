package com.trtc.uikit.livekit.voiceroom.view.seatmanager

import android.annotation.SuppressLint
import android.content.Context
import android.view.View
import android.view.View.VISIBLE
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.tencent.cloud.tuikit.engine.room.TUIRoomDefine
import com.trtc.uikit.livekit.R
import com.trtc.uikit.livekit.common.ErrorLocalized
import com.trtc.uikit.livekit.common.LiveKitLogger
import com.trtc.uikit.livekit.common.completionHandler
import com.trtc.uikit.livekit.common.seatModeFromEngineSeatMode
import io.trtc.tuikit.atomicx.widget.basicwidget.button.AtomicButton
import io.trtc.tuikit.atomicx.widget.basicwidget.popover.AtomicPopover
import io.trtc.tuikit.atomicxcore.api.live.CoGuestStore
import io.trtc.tuikit.atomicxcore.api.live.LiveInfo
import io.trtc.tuikit.atomicxcore.api.live.LiveListStore
import io.trtc.tuikit.atomicxcore.api.live.LiveSeatStore
import io.trtc.tuikit.atomicxcore.api.live.SeatLayoutTemplate
import io.trtc.tuikit.atomicxcore.api.live.TakeSeatMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class SeatManagerDialog(
    private val context: Context
) : AtomicPopover(context) {

    private val liveListStore = LiveListStore.shared()
    private val liveSeatStore =
        LiveSeatStore.create(liveListStore.liveState.currentLive.value.liveID)
    private val coGuestStore = CoGuestStore.create(liveListStore.liveState.currentLive.value.liveID)
    private lateinit var imageBack: ImageView
    private lateinit var tvTitle: TextView
    private lateinit var seatListTitle: TextView
    private lateinit var seatApplicationTitle: TextView
    private lateinit var inviteButton: AtomicButton
    private lateinit var endButton: ImageView
    private lateinit var endButtonContainer: View
    private lateinit var seatListView: RecyclerView
    private lateinit var seatApplicationListView: RecyclerView
    private lateinit var emptyView: View
    private lateinit var seatApplicationAdapter: SeatApplicationAdapter
    private lateinit var seatListPanelAdapter: SeatListPanelAdapter
    private lateinit var switchNeedRequest: SwitchCompat
    private var seatInvitationDialog: SeatInvitationDialog? = null
    private var subscribeStateJob: Job? = null

    init {
        initView()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        addObserver()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        removeObserver()
    }

    private fun initView() {
        val rootView = View.inflate(context, R.layout.livekit_voiceroom_seat_manager_panel, null)
        setContent(rootView)
        setPanelHeight(PanelHeight.Ratio(0.9f))
        bindViewId(rootView)
        tvTitle.setText(R.string.common_link_mic_manager)
        inviteButton.setOnClickListener { showSeatInvitationPanel() }
        showBackButton()
        showEndButton()
        initSeatListView()
        initSeatApplicationListView()
        initNeedRequest()
    }

    private fun showEndButton() {
        endButton.setImageResource(R.drawable.livekit_ic_invite_user)
        endButtonContainer.visibility = VISIBLE
        endButtonContainer.setOnClickListener { showSeatInvitationPanel() }
    }

    private fun showBackButton() {
        imageBack.setOnClickListener { dismiss() }
        imageBack.visibility = VISIBLE
    }

    private fun addObserver() {
        subscribeStateJob = CoroutineScope(Dispatchers.Main).launch {
            launch {
                liveListStore.liveState.currentLive
                    .map { it.seatMode }
                    .distinctUntilChanged()
                    .collect {
                        onSeatModeChanged(it)
                    }
            }
            launch {
                liveSeatStore.liveSeatState.seatList.collect {
                    onSeatListChanged()
                }
            }
            launch {
                coGuestStore.coGuestState.applicants.collect {
                    onSeatApplicationListChanged()
                }
            }
        }

    }

    private fun removeObserver() {
        subscribeStateJob?.cancel()
    }

    private fun bindViewId(rootView: View) {
        tvTitle = rootView.findViewById(R.id.tv_title)
        imageBack = rootView.findViewById(R.id.iv_back)
        seatListTitle = rootView.findViewById(R.id.seat_list_title)
        seatApplicationTitle = rootView.findViewById(R.id.seat_application_title)
        seatListView = rootView.findViewById(R.id.rv_seat_list)
        emptyView = rootView.findViewById(R.id.empty_view_container)
        inviteButton = rootView.findViewById(R.id.atomic_btn_invite)
        endButton = rootView.findViewById(R.id.end_button)
        endButtonContainer = rootView.findViewById(R.id.end_button_container)
        seatApplicationListView = rootView.findViewById(R.id.rv_seat_application)
        switchNeedRequest = rootView.findViewById(R.id.need_request)
    }

    private fun initSeatListView() {
        seatListView.layoutManager =
            LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)
        seatListPanelAdapter = SeatListPanelAdapter(context)
        seatListView.adapter = seatListPanelAdapter
    }

    private fun initSeatApplicationListView() {
        seatApplicationListView.layoutManager =
            LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)
        seatApplicationAdapter = SeatApplicationAdapter(context)
        seatApplicationListView.adapter = seatApplicationAdapter
    }

    private fun initSeatApplicationTitleView() {
        if (coGuestStore.coGuestState.applicants.value.isNotEmpty()) {
            seatApplicationTitle.visibility = VISIBLE
        } else {
            seatApplicationTitle.visibility = View.GONE
        }
        seatApplicationTitle.text = context.getString(
            R.string.common_seat_application_title,
            coGuestStore.coGuestState.applicants.value.size
        )
    }

    private fun initNeedRequest() {
        val needRequest =
            liveListStore.liveState.currentLive.value.seatMode == TakeSeatMode.APPLY
        switchNeedRequest.isChecked = needRequest
        switchNeedRequest.setOnCheckedChangeListener { _, enable -> onSeatModeClicked(enable) }
    }

    @SuppressLint("StringFormatMatches")
    private fun initSeatListViewTitle() {
        val seatList = seatListPanelAdapter.data
        if (seatList.isEmpty()) {
            seatListTitle.visibility = View.GONE
        } else {
            seatListTitle.visibility = VISIBLE
            seatListTitle.text = context.getString(R.string.common_seat_list_title, seatList.size)
        }
    }

    private fun onSeatListChanged() {
        seatListPanelAdapter.updateData()
        initSeatListViewTitle()
        updateEmptyView()
    }

    private fun onSeatApplicationListChanged() {
        seatApplicationAdapter.updateData()
        initSeatApplicationTitleView()
        updateEmptyView()
    }

    private fun onSeatModeChanged(seatMode: TakeSeatMode) {
        switchNeedRequest.isChecked = seatMode == TakeSeatMode.APPLY
    }

    private fun showSeatInvitationPanel() {
        if (seatInvitationDialog == null) {
            seatInvitationDialog = SeatInvitationDialog(context)
        }
        seatInvitationDialog?.show()
    }

    private fun updateEmptyView() {
        val seatList = seatListPanelAdapter.data
        if (seatList.isEmpty() && coGuestStore.coGuestState.applicants.value.isEmpty()) {
            emptyView.visibility = VISIBLE
        } else {
            emptyView.visibility = View.GONE
        }
    }

    private fun onSeatModeClicked(enable: Boolean) {
        val seatMode = if (enable) {
            TUIRoomDefine.SeatMode.APPLY_TO_TAKE
        } else {
            TUIRoomDefine.SeatMode.FREE_TO_TAKE
        }
        val info = LiveInfo(seatTemplate = SeatLayoutTemplate.AudioSalon(0)).apply {
            this.seatMode = seatModeFromEngineSeatMode(seatMode)
            this.liveID = liveListStore.liveState.currentLive.value.liveID
        }
        val flagList = ArrayList<LiveInfo.ModifyFlag>()
        flagList.add(LiveInfo.ModifyFlag.SEAT_MODE)
        liveListStore.updateLiveInfo(info, flagList, completionHandler {
            onError { code, desc ->
                LOGGER.error("responseSeatInvitation failed, error: $code, message: $desc")
                ErrorLocalized.onError(code)
            }
        })

    }

    companion object {
        private val LOGGER = LiveKitLogger.getVoiceRoomLogger("SeatManagerDialog")
    }
}