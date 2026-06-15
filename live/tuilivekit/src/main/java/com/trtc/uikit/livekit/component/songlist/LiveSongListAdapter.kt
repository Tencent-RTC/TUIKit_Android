package com.trtc.uikit.livekit.component.songlist

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.trtc.uikit.livekit.R
import io.trtc.tuikit.atomicx.common.imageloader.ImageLoader

class LiveSongListAdapter(
    private val store: LiveMusicStore,
) : ListAdapter<LiveSong, LiveSongListAdapter.SongViewHolder>(DIFF) {

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<LiveSong>() {
            override fun areItemsTheSame(oldItem: LiveSong, newItem: LiveSong): Boolean {
                return oldItem.songId == newItem.songId
            }

            override fun areContentsTheSame(oldItem: LiveSong, newItem: LiveSong): Boolean {
                return false
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SongViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.live_song_library_item, parent, false)
        return SongViewHolder(view)
    }

    override fun onBindViewHolder(holder: SongViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class SongViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imageCover: ImageView = itemView.findViewById(R.id.iv_cover)
        private val textSongName: TextView = itemView.findViewById(R.id.tv_song_name)
        private val textArtist: TextView = itemView.findViewById(R.id.tv_artist)
        private val buttonSelect: Button = itemView.findViewById(R.id.btn_select_song)

        fun bind(song: LiveSong) {
            textSongName.text = song.songName
            textArtist.text = song.artist
            initCover(song)
            initSelectButton(song)
        }

        private fun initCover(song: LiveSong) {
            ImageLoader.load(
                imageCover.context,
                imageCover,
                song.coverUrl,
                R.drawable.karaoke_song_cover
            )
        }

        private fun initSelectButton(song: LiveSong) {
            val isOrdered = store.isSongSelected(song.songId)
            if (isOrdered) {
                buttonSelect.apply {
                    background = ContextCompat.getDrawable(context, R.drawable.karaoke_btn_grey_edge_bg)
                    text = context.getString(R.string.karaoke_ordered)
                    isEnabled = false
                    setOnClickListener(null)
                }
            } else {
                buttonSelect.apply {
                    background = ContextCompat.getDrawable(context, R.drawable.karaoke_btn_blue_bg)
                    text = context.getString(R.string.karaoke_order_song)
                    isEnabled = true
                    setOnClickListener {
                        store.addSong(song)
                    }
                }
            }
        }
    }
}
