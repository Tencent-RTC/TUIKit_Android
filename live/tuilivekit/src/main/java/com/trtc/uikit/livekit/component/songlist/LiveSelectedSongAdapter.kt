package com.trtc.uikit.livekit.component.songlist

import android.view.LayoutInflater
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.trtc.uikit.livekit.R
import com.trtc.uikit.livekit.component.karaoke.store.utils.PlaybackState
import io.trtc.tuikit.atomicx.common.imageloader.ImageLoader

class LiveSelectedSongAdapter(
    private val store: LiveMusicStore,
) : ListAdapter<LiveSong, LiveSelectedSongAdapter.SongViewHolder>(DIFF) {

    var onPlayPause: (() -> Unit)? = null
    var onNext: (() -> Unit)? = null
    var onTop: ((LiveSong) -> Unit)? = null
    var onRemove: ((LiveSong) -> Unit)? = null

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
            .inflate(R.layout.live_selected_song_item, parent, false)
        return SongViewHolder(view)
    }

    override fun onBindViewHolder(holder: SongViewHolder, position: Int) {
        holder.bind(getItem(position), position)
    }

    inner class SongViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imagePlaying: ImageView = itemView.findViewById(R.id.iv_playing)
        private val textOrderIndex: TextView = itemView.findViewById(R.id.tv_order_index)
        private val imageCover: ImageView = itemView.findViewById(R.id.iv_cover)
        private val textSongName: TextView = itemView.findViewById(R.id.tv_song_name)
        private val textRequester: TextView = itemView.findViewById(R.id.tv_requester)
        private val imagePlayPause: ImageView = itemView.findViewById(R.id.iv_play_pause)
        private val imageNext: ImageView = itemView.findViewById(R.id.iv_next)
        private val imagePin: ImageView = itemView.findViewById(R.id.iv_pin)
        private val imageDelete: ImageView = itemView.findViewById(R.id.iv_delete)

        fun bind(song: LiveSong, position: Int) {
            textSongName.text = song.songName
            textRequester.text = song.requester
            initCover(song)
            initPositionView(position)
            initButtons(song, position)
        }

        private fun initCover(song: LiveSong) {
            ImageLoader.load(
                imageCover.context,
                imageCover,
                song.coverUrl,
                R.drawable.karaoke_song_cover
            )
        }

        private fun initPositionView(position: Int) {
            val isCurrentlyPlaying = position == 0 && store.currentSongId.value.isNotEmpty()
                    && store.currentSongId.value == getItem(0).songId
            imagePlaying.visibility = if (isCurrentlyPlaying) VISIBLE else GONE
            textOrderIndex.visibility = if (isCurrentlyPlaying) GONE else VISIBLE
            textOrderIndex.text = (position + 1).toString()
        }

        private fun initButtons(song: LiveSong, position: Int) {
            val isCurrentlyPlaying = position == 0 && store.currentSongId.value.isNotEmpty()
                    && store.currentSongId.value == song.songId

            // Reset all buttons to hidden
            imagePlayPause.visibility = GONE
            imageNext.visibility = GONE
            imagePin.visibility = GONE
            imageDelete.visibility = GONE

            if (isCurrentlyPlaying) {
                // First item & playing: show play/pause + next
                imagePlayPause.visibility = VISIBLE
                imageNext.visibility = VISIBLE

                val isPlaying = store.playStatus.value == PlaybackState.START
                        || store.playStatus.value == PlaybackState.RESUME
                imagePlayPause.setImageResource(
                    if (isPlaying) R.drawable.karaoke_music_resume else R.drawable.karaoke_music_pause
                )

                imagePlayPause.setOnClickListener { onPlayPause?.invoke() }
                imageNext.setOnClickListener { onNext?.invoke() }
            } else if (position == 0 || position == 1) {
                // First/second item (not playing): only delete
                imageDelete.visibility = VISIBLE
                imageDelete.setOnClickListener { onRemove?.invoke(song) }
            } else {
                // Other items: pin + delete
                imagePin.visibility = VISIBLE
                imageDelete.visibility = VISIBLE
                imagePin.setOnClickListener { onTop?.invoke(song) }
                imageDelete.setOnClickListener { onRemove?.invoke(song) }
            }
        }
    }
}
