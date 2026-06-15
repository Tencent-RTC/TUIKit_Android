package com.trtc.uikit.livekit.component.bgm.view

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.trtc.uikit.livekit.R
import com.trtc.uikit.livekit.component.bgm.store.BGMInfo
import com.trtc.uikit.livekit.component.bgm.store.BGMStore
import com.trtc.uikit.livekit.component.bgm.store.BGMState
class BGMListAdapter(
    private val context: Context,
    private val bgmStore: BGMStore
) : RecyclerView.Adapter<BGMListAdapter.ViewHolder>() {

    private val bgmState: BGMState = bgmStore.bgmState
    private var selectedPosition: Int = 0

    init {
        initData()
        bgmStore.refreshCurrentMusicInfo()
    }

    private fun initData() {
        if (bgmState.musicList.isEmpty()) {
            bgmState.musicList.add(
                BGMInfo(
                    1,
                    context.getString(R.string.common_music_cheerful),
                    "https://dldir1.qq.com/hudongzhibo/TUIKit/resource/music/PositiveHappyAdvertising.mp3"
                )
            )
            bgmState.musicList.add(
                BGMInfo(
                    2,
                    context.getString(R.string.common_music_melancholy),
                    "https://dldir1.qq.com/hudongzhibo/TUIKit/resource/music/SadCinematicPiano.mp3"
                )
            )
            bgmState.musicList.add(
                BGMInfo(
                    3,
                    context.getString(R.string.common_music_wonder_world),
                    "https://dldir1.qq.com/hudongzhibo/TUIKit/resource/music/WonderWorld.mp3"
                )
            )
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.livekit_recycle_item_bgm, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val musicInfo = bgmState.musicList[position]
        holder.textMusicName.text = musicInfo.name
        if (bgmStore.isMusicPlaying(musicInfo)) {
            holder.imageStartStop.setImageResource(R.drawable.livekit_music_pause)
            selectedPosition = bgmState.musicList.indexOf(musicInfo)
        } else {
            holder.imageStartStop.setImageResource(R.drawable.livekit_music_start)
        }
        holder.imageStartStop.setOnClickListener {
            val index = holder.bindingAdapterPosition
            if (index != RecyclerView.NO_POSITION) {
                bgmStore.operatePlayMusic(musicInfo)
                notifyItemChanged(index)
                notifyItemChanged(selectedPosition)
            }
        }
    }

    override fun getItemCount(): Int = bgmState.musicList.size

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imageStartStop: ImageView = itemView.findViewById(R.id.iv_start_stop)
        val textMusicName: TextView = itemView.findViewById(R.id.tv_music_name)
    }
}