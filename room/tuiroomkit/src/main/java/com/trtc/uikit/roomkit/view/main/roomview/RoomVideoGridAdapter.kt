package com.trtc.uikit.roomkit.view.main.roomview

import android.graphics.Outline
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import androidx.recyclerview.widget.AsyncListDiffer
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.trtc.uikit.roomkit.R
import com.trtc.uikit.roomkit.base.utils.dpToPx
import io.trtc.tuikit.atomicxcore.api.view.VideoStreamType

/**
 * Room video grid adapter for managing video stream items
 * Manages video stream items and their corresponding ViewHolders in room view
 */
class RoomVideoGridAdapter : RecyclerView.Adapter<RoomVideoGridAdapter.VideoStreamViewHolder>() {
    var onOrientationSwitchClick: (() -> Unit)? = null

    companion object {
        const val VIEW_TYPE_SCREEN_SHARE = 1
        const val VIEW_TYPE_CAMERA = 2
        private const val VIDEO_CORNER_RADIUS_DP = 16
    }

    private val diffCallback = DiffCallback()

    /**
     * DiffUtil callback for efficient list updates
     */
    private class DiffCallback : DiffUtil.ItemCallback<VideoStreamItem>() {
        override fun areItemsTheSame(oldItem: VideoStreamItem, newItem: VideoStreamItem): Boolean {
            return oldItem.uniqueId == newItem.uniqueId
        }

        override fun areContentsTheSame(oldItem: VideoStreamItem, newItem: VideoStreamItem): Boolean {
            val oldP = oldItem.participant
            val newP = newItem.participant
            return oldP.userName == newP.userName &&
                    oldP.nameCard == newP.nameCard &&
                    oldP.avatarURL == newP.avatarURL &&
                    oldP.cameraStatus == newP.cameraStatus &&
                    oldP.microphoneStatus == newP.microphoneStatus &&
                    oldP.screenShareStatus == newP.screenShareStatus &&
                    oldP.role == newP.role &&
                    oldItem.streamType == newItem.streamType
        }

        override fun getChangePayload(oldItem: VideoStreamItem, newItem: VideoStreamItem): Any? {
            return newItem
        }
    }

    private val differ = AsyncListDiffer(this, diffCallback)

    var onDataUpdateCompleted: (() -> Unit)? = null

    init {
        differ.addListListener { previousList, currentList ->
            val oldSize = previousList.size
            val newSize = currentList.size

            val hasChanges = if (oldSize != newSize) {
                true
            } else {
                previousList.indices.any { index ->
                    previousList[index].uniqueId != currentList[index].uniqueId
                }
            }

            if (hasChanges) {
                onDataUpdateCompleted?.invoke()
            }
        }
    }

    fun updateData(newData: List<VideoStreamItem>) {
        differ.submitList(newData.toList())
    }

    override fun getItemViewType(position: Int): Int {
        val item = differ.currentList[position]
        return if (item.streamType == VideoStreamType.SCREEN) {
            VIEW_TYPE_SCREEN_SHARE
        } else {
            VIEW_TYPE_CAMERA
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VideoStreamViewHolder {
        val itemView = LayoutInflater.from(parent.context)
            .inflate(R.layout.roomkit_item_room_video_grid, parent, false)
        return VideoStreamViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: VideoStreamViewHolder, position: Int) {
        holder.bind(differ.currentList[position])
    }

    override fun onBindViewHolder(holder: VideoStreamViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.isEmpty()) {
            super.onBindViewHolder(holder, position, payloads)
            return
        }

        val item = payloads.firstOrNull() as? VideoStreamItem ?: differ.currentList[position]
        holder.updateParticipantState(item)
    }

    override fun getItemCount(): Int = differ.currentList.size

    fun getStreamItems(): List<VideoStreamItem> = differ.currentList

    // ========== Inner Classes ==========

    /**
     * ViewHolder for video stream items in the room video grid.
     *
     * The item view IS a [RoomViewVideoStreamCell] — a self-contained widget
     * that owns the four visual layers (video renderer, avatar placeholder,
     * speaking border, name overlay). This ViewHolder is therefore purely a
     * thin adapter between DiffUtil callbacks and the cell's API, plus the
     * grid-specific chrome (16dp rounded outline).
     *
     */
    inner class VideoStreamViewHolder(
        itemView: View
    ) : RecyclerView.ViewHolder(itemView) {

        private val cell: RoomViewVideoStreamCell = itemView as RoomViewVideoStreamCell

        init {
            setupRoundedCorners()
            cell.setOrientationSwitchClickListener(onOrientationSwitchClick)
        }

        /**
         * Setup rounded corners for the video item
         */
        private fun setupRoundedCorners() {
            itemView.clipToOutline = true
            itemView.outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    val radius = view.dpToPx(VIDEO_CORNER_RADIUS_DP).toFloat()
                    outline.setRoundRect(0, 0, view.width, view.height, radius)
                }
            }
        }

        /**
         * Bind video stream data to this ViewHolder. Delegates the four-layer
         * update (renderer, avatar visibility, speaking-border reset, overlay
         * refresh) to [RoomViewVideoStreamCell.bind]; stream-change detection
         * happens inside the cell.
         */
        fun bind(streamItem: VideoStreamItem) {
            cell.bind(streamItem.participant, streamItem.streamType)
        }

        /**
         * Set video rendering active state
         * Controls whether video is actively rendered
         *
         * @param active true to activate video rendering, false to deactivate
         */
        fun setActive(active: Boolean) {
            cell.setActive(active)
        }

        /**
         * Update speaking state visual indicator
         *
         * @param isSpeaking true to show speaking border, false to hide
         */
        fun updateSpeakingState(isSpeaking: Boolean) {
            cell.updateSpeakingState(isSpeaking)
        }

        fun updateParticipantState(item: VideoStreamItem) {
            cell.updateParticipant(item.participant)
        }
    }
}
