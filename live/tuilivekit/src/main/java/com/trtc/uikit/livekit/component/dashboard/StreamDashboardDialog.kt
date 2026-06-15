package com.trtc.uikit.livekit.component.dashboard

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.PagerSnapHelper
import androidx.recyclerview.widget.RecyclerView
import com.trtc.uikit.livekit.R
import com.trtc.uikit.livekit.component.dashboard.view.CircleIndicator
import com.trtc.uikit.livekit.component.dashboard.view.StreamInfoAdapter
import io.trtc.tuikit.atomicx.common.util.ScreenUtil
import io.trtc.tuikit.atomicx.widget.basicwidget.popover.AtomicPopover
import io.trtc.tuikit.atomicxcore.api.device.DeviceStore
import io.trtc.tuikit.atomicxcore.api.live.AVStatistics
import io.trtc.tuikit.atomicxcore.api.live.LiveEndedReason
import io.trtc.tuikit.atomicxcore.api.live.LiveListListener
import io.trtc.tuikit.atomicxcore.api.live.LiveListStore
import io.trtc.tuikit.atomicxcore.api.live.LiveSeatStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class StreamDashboardDialog(context: Context, val roomId: String) : AtomicPopover(context) {
    private val pagerSnapHelper = PagerSnapHelper()
    private lateinit var recyclerMediaInfo: RecyclerView
    private lateinit var circleIndicator: CircleIndicator
    private lateinit var textUpLoss: TextView
    private lateinit var textDownLoss: TextView
    private lateinit var textRtt: TextView
    private lateinit var adapter: StreamInfoAdapter
    private var colorGreen: Int = 0
    private var colorPink: Int = 0
    private val videoStatusList = ArrayList<AVStatistics>()
    private var subscribeStateJob: Job? = null

    private val liveListListener = object : LiveListListener() {
        override fun onLiveEnded(liveID: String, reason: LiveEndedReason, message: String) {
            dismiss()
        }
    }

    init {
        initView()
    }

    private fun initView() {
        val view = LayoutInflater.from(context).inflate(R.layout.livekit_stream_dashboard, null)

        bindViewId(view)
        initMediaInfoRecyclerView()
        updateNetworkStatistics(0, 0, 0)
        setContent(view)
    }

    private fun bindViewId(view: View) {
        textRtt = view.findViewById(R.id.tv_rtt)
        textDownLoss = view.findViewById(R.id.tv_downLoss)
        textUpLoss = view.findViewById(R.id.tv_upLoss)
        recyclerMediaInfo = view.findViewById(R.id.rv_media_info)
        circleIndicator = view.findViewById(R.id.ci_pager)
        colorGreen = context.resources.getColor(R.color.common_text_color_normal)
        colorPink = context.resources.getColor(R.color.common_not_standard_pink_f9)
    }

    override fun onStart() {
        super.onStart()
        addObserver()
        window?.let { setDialogMaxHeight(it) }
    }

    override fun onStop() {
        super.onStop()
        removeObserver()
    }

    protected fun setDialogMaxHeight(window: Window) {
        val configuration = context.resources.configuration
        window.setBackgroundDrawableResource(android.R.color.transparent)
        val params = window.attributes
        val screenHeight = context.resources.displayMetrics.heightPixels
        val height = (screenHeight * 0.75).toInt()

        if (configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            params.gravity = Gravity.END
            params.width = context.resources.displayMetrics.widthPixels / 2
        } else {
            params.gravity = Gravity.BOTTOM
            params.width = WindowManager.LayoutParams.MATCH_PARENT
        }
        params.height = height
        window.attributes = params
    }

    private fun initMediaInfoRecyclerView() {
        circleIndicator.setCircleRadius(ScreenUtil.dip2px(3f))
        pagerSnapHelper.attachToRecyclerView(recyclerMediaInfo)
        adapter = StreamInfoAdapter(context, videoStatusList)
        recyclerMediaInfo.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        recyclerMediaInfo.adapter = adapter
        recyclerMediaInfo.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    updateCircleIndicator()
                }
            }
        })
    }

    private fun updateCircleIndicator() {
        val count = adapter.itemCount
        circleIndicator.visibility = if (count > 1) View.VISIBLE else View.GONE
        circleIndicator.setCircleCount(count)
        val snapView = pagerSnapHelper.findSnapView(recyclerMediaInfo.layoutManager)
        val position = if (snapView != null) {
            recyclerMediaInfo.layoutManager?.getPosition(snapView) ?: 0
        } else {
            0
        }
        circleIndicator.setSelected(position)
    }

    @SuppressLint("DefaultLocale")
    private fun updateNetworkStatistics(rtt: Int, upLoss: Int, downLoss: Int) {
        textRtt.text = String.format("%dms", rtt)
        textRtt.setTextColor(if (rtt > 100) colorPink else colorGreen)
        textDownLoss.text = String.format("%d%%", downLoss)
        textDownLoss.setTextColor(if (downLoss > 10) colorPink else colorGreen)
        textUpLoss.text = String.format("%d%%", upLoss)
        textUpLoss.setTextColor(if (upLoss > 10) colorPink else colorGreen)
    }

    private fun addObserver() {
        subscribeStateJob = CoroutineScope(Dispatchers.Main).launch {
            launch {
                DeviceStore.shared().deviceState.networkInfo.collect {
                    updateNetworkStatistics(it.delay, it.upLoss, it.downLoss)
                }
            }

            launch {
                val liveSeatStore = LiveSeatStore.create(roomId)
                liveSeatStore.liveSeatState.avStatistics.collect {
                    adapter.updateRemoteVideoStatus(it)
                    recyclerMediaInfo.post { updateCircleIndicator() }
                }
            }
        }
        LiveListStore.shared().addLiveListListener(liveListListener)
    }

    private fun removeObserver() {
        LiveListStore.shared().removeLiveListListener(liveListListener)
    }
}