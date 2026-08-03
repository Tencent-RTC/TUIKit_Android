package com.trtc.uikit.roomkit.view.schedule

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.trtc.uikit.roomkit.R
import com.trtc.uikit.roomkit.RoomMainActivity
import io.trtc.tuikit.atomicxcore.api.room.RoomInfo
import io.trtc.tuikit.atomicxcore.api.room.RoomStatus
import java.util.Calendar
import java.util.Locale

/**
 * Adapter for the scheduled-room list. Rooms are grouped by date with a header inserted before
 * each group; tapping a row opens the detail page, while the "Enter" button on the right joins
 * the room directly.
 */
class RoomScheduledRoomAdapter(
    private val context: Context,
    private val onItemClick: (RoomInfo) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        const val TYPE_HEADER = 0
        const val TYPE_ROOM = 1
        private const val TIME_FORMAT = "%02d:%02d"
    }

    private sealed class Item {
        data class Header(val date: String) : Item()
        data class Room(val info: RoomInfo) : Item()
    }

    private val items = mutableListOf<Item>()

    fun setDataList(rooms: List<RoomInfo>) {
        items.clear()
        val sorted = rooms.sortedBy { it.scheduledStartTime }
        val grouped = sorted.groupBy { room -> getDateHeader(room.scheduledStartTime) }
        for ((header, roomList) in grouped) {
            items.add(Item.Header(header))
            items.addAll(roomList.map { Item.Room(it) })
        }
        notifyDataSetChanged()
    }

    /**
     * Header uses an absolute date on purpose. Relative labels like "Today / Tomorrow" are avoided
     * because paginated fetches can split the same calendar day across batches, causing multiple
     * header groups for one date.
     */
    private fun getDateHeader(timestamp: Long): String =
        ScheduleDateFormatter.formatDate(context, timestamp)

    override fun getItemViewType(position: Int): Int = when (items[position]) {
        is Item.Header -> TYPE_HEADER
        is Item.Room -> TYPE_ROOM
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_HEADER) {
            val view = inflater.inflate(R.layout.roomkit_item_scheduled_room_header, parent, false)
            HeaderViewHolder(view)
        } else {
            val view = inflater.inflate(R.layout.roomkit_item_scheduled_room, parent, false)
            RoomViewHolder(view, onItemClick)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is Item.Header -> (holder as HeaderViewHolder).bind(item.date)
            is Item.Room -> (holder as RoomViewHolder).bind(item.info)
        }
    }

    override fun getItemCount(): Int = items.size

    class HeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvDate: TextView = itemView.findViewById(R.id.tv_scheduled_date)

        fun bind(date: String) {
            tvDate.text = date
        }
    }

    class RoomViewHolder(
        itemView: View,
        private val onItemClick: (RoomInfo) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {
        private val tvRoomName: TextView = itemView.findViewById(R.id.tv_room_name)
        private val tvRoomId: TextView = itemView.findViewById(R.id.tv_room_id)
        private val tvRoomTime: TextView = itemView.findViewById(R.id.tv_room_time)
        private val tvRoomStatus: TextView = itemView.findViewById(R.id.tv_room_status)
        private val divideStatusLine: View = itemView.findViewById(R.id.divide_status_line)
        private val llEnterRoom: LinearLayout = itemView.findViewById(R.id.ll_enter_scheduled_room)
        private var roomInfo: RoomInfo? = null

        init {
            itemView.setOnClickListener {
                roomInfo?.let { onItemClick(it) }
            }
            llEnterRoom.setOnClickListener {
                roomInfo?.let { enterRoom(it) }
            }
        }

        fun bind(info: RoomInfo) {
            roomInfo = info
            tvRoomName.text = info.roomName
            tvRoomId.text = addSpacesEveryThreeChars(info.roomID)
            tvRoomTime.text = itemView.context.getString(
                R.string.roomkit_scheduled_room_time_range,
                formatTime(info.scheduledStartTime),
                formatTime(info.scheduledEndTime)
            )

            // Only show the status tag and its leading divider when the room is running.
            if (info.roomStatus == RoomStatus.RUNNING) {
                divideStatusLine.visibility = View.VISIBLE
                tvRoomStatus.visibility = View.VISIBLE
                tvRoomStatus.text = itemView.context.getString(R.string.roomkit_room_running)
            } else {
                divideStatusLine.visibility = View.GONE
                tvRoomStatus.visibility = View.GONE
            }
        }

        private fun enterRoom(info: RoomInfo) {
            val context = itemView.context
            val intent = Intent(context, RoomMainActivity::class.java).apply {
                putExtra(RoomMainActivity.EXTRA_ROOM_ID, info.roomID)
                putExtra(RoomMainActivity.EXTRA_ROOM_NAME, info.roomName)
                putExtra(RoomMainActivity.EXTRA_IS_CREATE, false)
                putExtra(RoomMainActivity.EXTRA_AUTO_ENABLE_MICROPHONE, true)
                putExtra(RoomMainActivity.EXTRA_AUTO_ENABLE_CAMERA, false)
                putExtra(RoomMainActivity.EXTRA_AUTO_ENABLE_SPEAKER, true)
            }
            context.startActivity(intent)
        }

        private fun formatTime(timestamp: Long): String {
            val cal = Calendar.getInstance().apply { timeInMillis = timestamp }
            val hour = cal.get(Calendar.HOUR_OF_DAY)
            val minute = cal.get(Calendar.MINUTE)
            return String.format(Locale.getDefault(), TIME_FORMAT, hour, minute)
        }

        private fun addSpacesEveryThreeChars(roomId: String): String =
            roomId.chunked(3).joinToString(" ")
    }
}
