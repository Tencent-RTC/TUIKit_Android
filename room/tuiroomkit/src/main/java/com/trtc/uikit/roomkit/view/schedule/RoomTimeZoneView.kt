package com.trtc.uikit.roomkit.view.schedule

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.trtc.uikit.roomkit.R
import com.trtc.uikit.roomkit.base.ui.widget.RoomTopBar
import java.util.Locale
import java.util.TimeZone

class RoomTimeZoneView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val topBar: RoomTopBar by lazy { findViewById(R.id.top_bar) }
    private val rvTimeZone: RecyclerView by lazy { findViewById(R.id.rv_time_zone_list) }

    private lateinit var adapter: TimeZoneAdapter

    var onTimeZoneSelected: ((id: String) -> Unit)? = null
    var onBackClick: (() -> Unit)? = null

    init {
        LayoutInflater.from(context).inflate(R.layout.roomkit_view_time_zone, this)
    }

    fun setup(currentTimeZoneId: String) {
        topBar.onBackClick = { onBackClick?.invoke() }

        adapter = TimeZoneAdapter(context, currentTimeZoneId) { id ->
            onTimeZoneSelected?.invoke(id)
        }
        rvTimeZone.layoutManager = LinearLayoutManager(context)
        rvTimeZone.adapter = adapter
        rvTimeZone.scrollToPosition(adapter.selectedPosition)
    }

    private class TimeZoneItem(
        val id: String,
        val name: String,
        val zone: String,
        val offset: Int
    )

    private class TimeZoneAdapter(
        private val context: Context,
        currentId: String,
        private val onItemClick: (String) -> Unit
    ) : RecyclerView.Adapter<TimeZoneAdapter.VH>() {

        private val items: List<TimeZoneItem> = buildTimeZoneList()
        var selectedPosition: Int = findPosition(currentId)
            private set

        private val selectedColor: Int =
            ContextCompat.getColor(context, R.color.roomkit_color_button_primary)
        private val unselectedColor: Int =
            ContextCompat.getColor(context, R.color.roomkit_color_text_primary)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val view = LayoutInflater.from(context)
                .inflate(R.layout.roomkit_item_time_zone, parent, false)
            return VH(view)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = items[position]
            holder.tvContent.text = formatText(item)
            holder.tvContent.setTextColor(
                if (position == selectedPosition) selectedColor else unselectedColor
            )
            holder.layout.setOnClickListener {
                val old = selectedPosition
                selectedPosition = holder.bindingAdapterPosition
                if (old != selectedPosition) {
                    notifyItemChanged(old)
                    notifyItemChanged(selectedPosition)
                }
                onItemClick(item.id)
            }
        }

        override fun getItemCount(): Int = items.size

        private fun formatText(item: TimeZoneItem): String = "(${item.zone})${item.name}"

        private fun buildTimeZoneList(): List<TimeZoneItem> {
            val ids = TimeZone.getAvailableIDs()
            val seenNames = mutableSetOf<String>()
            val list = mutableListOf<TimeZoneItem>()
            for (id in ids) {
                val item = buildItem(id) ?: continue
                if (item.zone == item.name) continue
                if (!seenNames.add(item.name)) continue
                list.add(item)
            }
            return list.sortedBy { it.offset }
        }

        private fun findPosition(id: String): Int {
            val target = buildItem(id) ?: return 0
            items.forEachIndexed { index, item ->
                if (item.zone == target.zone && item.name == target.name) {
                    return index
                }
            }
            return 0
        }

        private fun buildItem(id: String): TimeZoneItem? = try {
            val timeZone = TimeZone.getTimeZone(id)
            val name = timeZone.getDisplayName(Locale.getDefault())
            val zone = TimeZoneFormatter.formatGmtOffset(timeZone.rawOffset)
            TimeZoneItem(id, name, zone, timeZone.rawOffset)
        } catch (e: Exception) {
            null
        }

        class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val layout: LinearLayout = itemView.findViewById(R.id.ll_time_zone_item)
            val tvContent: TextView = itemView.findViewById(R.id.tv_time_zone_content)
        }
    }
}
