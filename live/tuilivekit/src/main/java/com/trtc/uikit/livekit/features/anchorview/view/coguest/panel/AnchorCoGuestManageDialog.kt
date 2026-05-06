package com.trtc.uikit.livekit.features.anchorview.view.coguest.panel

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.trtc.uikit.livekit.R
import io.trtc.tuikit.atomicx.widget.basicwidget.popover.AtomicPopover
import io.trtc.tuikit.atomicxcore.api.live.CoGuestStore
import io.trtc.tuikit.atomicxcore.api.live.LiveListStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@SuppressLint("ViewConstructor")
class AnchorCoGuestManageDialog(
    context: Context
) : AtomicPopover(context) {

    private lateinit var textMicUpTitle: TextView
    private lateinit var textMicDownTitle: TextView
    private lateinit var imageBack: ImageView
    private lateinit var viewSeparation: View
    private lateinit var recyclerLinkAudienceView: RecyclerView
    private lateinit var recyclerApplyLinkAudienceView: RecyclerView
    private lateinit var anchorLinkMicAdapter: AnchorCoGuestAdapter
    private lateinit var anchorApplyLinkMicAdapter: AnchorApplyCoGuestAdapter
    private var subscribeStateJob: Job? = null

    init {
        initView()
    }

    private fun initView() {
        val view = LayoutInflater.from(context).inflate(R.layout.livekit_layout_anchor_link_manage_panel, null)
        bindViewId(view)

        initBackView()
        initMicUpTitleView()
        initMicDownTitleView()
        initViewSeparation()
        initLinkAudienceListView()
        initApplyLinkAudienceListView()

        setContent(view)
    }

    private fun initViewSeparation() {
        val applicationUserList =
            CoGuestStore.create(LiveListStore.shared().liveState.currentLive.value.liveID).coGuestState.applicants.value
        val connectedUserList =
            CoGuestStore.create(LiveListStore.shared().liveState.currentLive.value.liveID).coGuestState.connected.value.filterNot {
                it.liveID != LiveListStore.shared().liveState.currentLive.value.liveID
            }
        if (applicationUserList.isNotEmpty() && connectedUserList.size > 1) {
            viewSeparation.visibility = VISIBLE
        } else {
            viewSeparation.visibility = GONE
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        addObserver()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        removeObserver()
    }

    private fun addObserver() {
        subscribeStateJob = CoroutineScope(Dispatchers.Main).launch {
            launch {
                onLinkAudienceListChange()
            }
            launch {
                onApplyLinkAudienceListChange()
            }
        }
    }

    private fun removeObserver() {
        subscribeStateJob?.cancel()
    }

    private fun bindViewId(view: View) {
        textMicUpTitle = view.findViewById(R.id.tv_mic_up_title)
        textMicDownTitle = view.findViewById(R.id.tv_mic_down_title)
        viewSeparation = view.findViewById(R.id.view_separation)
        recyclerLinkAudienceView = view.findViewById(R.id.rv_link_user_list)
        recyclerApplyLinkAudienceView = view.findViewById(R.id.rv_apply_link_user_list)
        imageBack = view.findViewById(R.id.iv_back)
    }

    private fun initBackView() {
        imageBack.setOnClickListener {
            dismiss()
        }
    }

    private fun initApplyLinkAudienceListView() {
        recyclerApplyLinkAudienceView.layoutManager = LinearLayoutManager(
            context, LinearLayoutManager.VERTICAL, false
        )
        anchorApplyLinkMicAdapter = AnchorApplyCoGuestAdapter(context)
        recyclerApplyLinkAudienceView.adapter = anchorApplyLinkMicAdapter
    }

    private fun initLinkAudienceListView() {
        recyclerLinkAudienceView.layoutManager = LinearLayoutManager(
            context, LinearLayoutManager.VERTICAL, false
        )
        anchorLinkMicAdapter = AnchorCoGuestAdapter(context)
        recyclerLinkAudienceView.adapter = anchorLinkMicAdapter
    }

    @SuppressLint("StringFormatMatches")
    private fun initMicDownTitleView() {
        val applicationUserList =
            CoGuestStore.create(LiveListStore.shared().liveState.currentLive.value.liveID).coGuestState.applicants.value
        if (applicationUserList.isNotEmpty()) {
            textMicDownTitle.visibility = VISIBLE
        } else {
            textMicDownTitle.visibility = GONE
        }
        textMicDownTitle.text = context.getString(
            R.string.common_seat_application_title,
            applicationUserList.size
        )
    }

    @SuppressLint("StringFormatMatches")
    private fun initMicUpTitleView() {
        val connectedUserList =
            CoGuestStore.create(LiveListStore.shared().liveState.currentLive.value.liveID).coGuestState.connected.value.filterNot {
                it.liveID != LiveListStore.shared().liveState.currentLive.value.liveID
            }
        if (connectedUserList.size > 1) {
            textMicUpTitle.visibility = VISIBLE
            textMicUpTitle.text = context.getString(
                R.string.common_seat_list_title,
                connectedUserList.size - 1
            )
        } else {
            textMicUpTitle.visibility = GONE
        }
    }

    private suspend fun onLinkAudienceListChange() {
        val coGuestStore = CoGuestStore.create(LiveListStore.shared().liveState.currentLive.value.liveID)
        coGuestStore.coGuestState.connected.collect {
            initMicUpTitleView()
            initViewSeparation()
            anchorLinkMicAdapter.updateData()
        }
    }

    private suspend fun onApplyLinkAudienceListChange() {
        val coGuestStore = CoGuestStore.create(LiveListStore.shared().liveState.currentLive.value.liveID)
        coGuestStore.coGuestState.applicants.collect {
            initMicDownTitleView()
            initViewSeparation()
            anchorApplyLinkMicAdapter.updateData()
        }
    }
}