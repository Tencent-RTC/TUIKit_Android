package com.trtc.uikit.livekit.component.bgm

import android.annotation.SuppressLint
import android.content.Context
import android.text.TextUtils
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.trtc.uikit.livekit.R
import com.trtc.uikit.livekit.common.reportEventData
import com.trtc.uikit.livekit.component.bgm.store.BGMStore
import com.trtc.uikit.livekit.component.bgm.view.BGMListAdapter
import io.trtc.tuikit.atomicxcore.api.device.MusicStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@SuppressLint("ViewConstructor")
class BGMPanelView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private lateinit var bgmStore: BGMStore
    private var bgmListAdapter: BGMListAdapter? = null
    private var subscribeStateJob: Job? = null

    companion object {
        private const val LIVEKIT_METRICS_PANEL_SHOW_LIVE_ROOM_MUSIC = 190018
        private const val LIVEKIT_METRICS_PANEL_SHOW_VOICE_ROOM_MUSIC = 191016
    }

    init {
        LayoutInflater.from(context).inflate(R.layout.livekit_anchor_bgm_panel, this, true)
    }

    fun init(roomId: String) {
        bgmStore = BGMStore(roomId)
        initMusicListView()
        reportData(roomId)
        addObserver()
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun addObserver() {
        subscribeStateJob = CoroutineScope(Dispatchers.Main).launch {
            MusicStore.create(bgmStore.roomId).musicState.playStatus.collect {
                bgmListAdapter?.notifyDataSetChanged()
            }
        }
    }

    override fun onDetachedFromWindow() {
        subscribeStateJob?.cancel()
        super.onDetachedFromWindow()
    }

    private fun initMusicListView() {
        val recycleMusicList = findViewById<RecyclerView>(R.id.rv_music_list)
        recycleMusicList.layoutManager = LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)
        bgmListAdapter = BGMListAdapter(context, bgmStore)
        recycleMusicList.adapter = bgmListAdapter
    }

    private fun reportData(roomId: String) {
        val isVoiceRoom = !TextUtils.isEmpty(roomId) && roomId.startsWith("voice_")
        if (isVoiceRoom) {
            reportEventData(LIVEKIT_METRICS_PANEL_SHOW_VOICE_ROOM_MUSIC)
        } else {
            reportEventData(LIVEKIT_METRICS_PANEL_SHOW_LIVE_ROOM_MUSIC)
        }
    }
}