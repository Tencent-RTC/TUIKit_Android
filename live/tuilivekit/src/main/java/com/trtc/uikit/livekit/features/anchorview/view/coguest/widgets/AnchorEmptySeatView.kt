package com.trtc.uikit.livekit.features.anchorview.view.coguest.widgets

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.TextView
import com.google.gson.Gson
import com.trtc.uikit.livekit.R
import com.trtc.uikit.livekit.common.LiveKitLogger
import com.trtc.uikit.livekit.features.anchorview.store.AnchorStore
import com.trtc.uikit.livekit.features.anchorview.view.BasicView
import io.trtc.tuikit.atomicxcore.api.live.SeatInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class AnchorEmptySeatView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : BasicView(context, attrs, defStyleAttr) {
    private val logger = LiveKitLogger.getFeaturesLogger("coGuest-AnchorEmptySeatView")
    private var seatInfo: SeatInfo? = null
    private lateinit var textSeatIndex: TextView
    private var subscribeStateJob: Job? = null

    fun init(manager: AnchorStore, seatInfo: SeatInfo) {
        logger.info("init seatInfo:" + Gson().toJson(seatInfo))
        this.seatInfo = seatInfo
        super.init(manager)
    }

    override fun initView() {
        LayoutInflater.from(baseContext).inflate(R.layout.livekit_co_guest_empty_anchor_widgets_view, this, true)
        textSeatIndex = findViewById(R.id.tv_seat_index)
    }

    override fun refreshView() {
        seatInfo?.let { info ->
            val seatIndex = info.index.toString()
            textSeatIndex.text = seatIndex
        }
    }

    override fun addObserver() {
        subscribeStateJob = CoroutineScope(Dispatchers.Main).launch {
            onPipModeObserver()
        }
    }

    override fun removeObserver() {
        subscribeStateJob?.cancel()
    }

    private suspend fun onPipModeObserver() {
        mediaState?.isPipModeEnabled?.collect {
            visibility = if (it) GONE else VISIBLE
        }
    }
}