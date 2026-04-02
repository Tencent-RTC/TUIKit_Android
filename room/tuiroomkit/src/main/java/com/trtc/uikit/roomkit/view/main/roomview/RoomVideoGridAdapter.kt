package com.trtc.uikit.roomkit.view.main.roomview

import android.graphics.Outline
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import androidx.constraintlayout.utils.widget.ImageFilterView
import androidx.recyclerview.widget.AsyncListDiffer
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.trtc.uikit.roomkit.R
import com.trtc.uikit.roomkit.base.utils.dpToPx
import io.trtc.tuikit.atomicx.common.imageloader.ImageLoader
import io.trtc.tuikit.atomicxcore.api.device.DeviceStatus
import io.trtc.tuikit.atomicxcore.api.room.RoomParticipant
import io.trtc.tuikit.atomicxcore.api.view.FillMode
import io.trtc.tuikit.atomicxcore.api.view.RoomParticipantView
import io.trtc.tuikit.atomicxcore.api.view.VideoStreamType

/**
 * Room video grid adapter for managing video stream items
 * Manages video stream items and their corresponding ViewHolders in room view
 */
class RoomVideoGridAdapter : RecyclerView.Adapter<RoomVideoGridAdapter.VideoStreamViewHolder>() {

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
        return VideoStreamViewHolder(itemView, viewType == VIEW_TYPE_SCREEN_SHARE)
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
     * ViewHolder for video stream items in the room video grid
     *
     * Layout hierarchy (from bottom to top):
     *   Layer 1: RoomParticipantView - Video rendering layer
     *   Layer 2: Avatar placeholder - Shown when camera is off (hidden for screen share)
     *   Layer 3: Speaking border - Visual indicator for speaking state
     *   Layer 4: Name overlay - User name and status information
     *
     * @param itemView The item view
     * @param isScreenShare Whether this ViewHolder displays screen share content
     */
    inner class VideoStreamViewHolder(
        itemView: View,
        private val isScreenShare: Boolean
    ) : RecyclerView.ViewHolder(itemView) {

        // Layer 1: Bottom layer - video rendering
        private val participantView: RoomParticipantView = itemView.findViewById(R.id.room_participant_view)

        // Layer 2: Avatar placeholder layer
        private val avatarPlaceholder: ImageFilterView = itemView.findViewById(R.id.iv_avatar_placeholder)

        // Layer 3: Speaking state border
        private val speakingBorder: View = itemView.findViewById(R.id.view_speaking_border)

        // Layer 4: User name and status information
        private val nameOverlay: RoomVideoNameOverlayView = itemView.findViewById(R.id.video_name_overlay)

        // Track current stream to detect stream changes
        private var currentStreamId: String? = null

        init {
            setupRoundedCorners()
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
         * Bind video stream data to this ViewHolder
         * - Initializes participant view if stream changed
         * - Updates participant info if stream unchanged
         *
         * @param streamItem The video stream item to bind
         */
        fun bind(streamItem: VideoStreamItem) {
            val isStreamChanged = currentStreamId != streamItem.uniqueId

            if (isStreamChanged) {
                // Stream changed - reinitialize participant view
                currentStreamId = streamItem.uniqueId
                participantView.init(streamItem.streamType, streamItem.participant)
                participantView.setFillMode(FillMode.FILL)
            } else {
                // Same stream - just update participant info
                participantView.updateParticipant(streamItem.participant)
            }

            nameOverlay.updateParticipant(streamItem.participant)
            updateAvatarVisibility(streamItem.participant)
            resetSpeakingState(streamItem.participant)
        }

        /**
         * Update avatar placeholder visibility based on camera status
         *
         * Rules:
         * - Screen share: Never show avatar
         * - Camera stream: Show avatar when camera is off
         *
         * @param participant The room participant
         */
        private fun updateAvatarVisibility(participant: RoomParticipant) {
            // Screen share never shows avatar
            if (isScreenShare) {
                avatarPlaceholder.visibility = View.GONE
                return
            }

            val shouldShowAvatar = participant.cameraStatus != DeviceStatus.ON

            if (shouldShowAvatar) {
                loadAvatar(participant)
                avatarPlaceholder.visibility = View.VISIBLE
            } else {
                avatarPlaceholder.visibility = View.GONE
            }
        }

        /**
         * Load participant avatar image
         */
        private fun loadAvatar(participant: RoomParticipant) {
            if (participant.avatarURL.isEmpty()) {
                avatarPlaceholder.setImageResource(R.drawable.roomkit_ic_default_avatar)
            } else {
                ImageLoader.load(
                    participantView.context,
                    avatarPlaceholder,
                    participant.avatarURL,
                    R.drawable.roomkit_ic_default_avatar
                )
            }
        }

        /**
         * Set video rendering active state
         * Controls whether video is actively rendered
         *
         * @param active true to activate video rendering, false to deactivate
         */
        fun setActive(active: Boolean) {
            participantView.setActive(active)
        }

        /**
         * Update speaking state visual indicator
         *
         * @param isSpeaking true to show speaking border, false to hide
         */
        fun updateSpeakingState(isSpeaking: Boolean) {
            speakingBorder.visibility = if (isSpeaking) View.VISIBLE else View.INVISIBLE
        }

        fun resetSpeakingState(participant: RoomParticipant) {
            if (participant.microphoneStatus == DeviceStatus.OFF) {
                speakingBorder.visibility = View.INVISIBLE
            }
        }

        /**
         * Update participant state (used for partial updates via DiffUtil payloads)
         *
         * @param item The updated stream item data
         */
        fun updateParticipantState(item: VideoStreamItem) {
            nameOverlay.updateParticipant(item.participant)
            participantView.updateParticipant(item.participant)
            updateAvatarVisibility(item.participant)
            resetSpeakingState(item.participant)
        }
    }
}
