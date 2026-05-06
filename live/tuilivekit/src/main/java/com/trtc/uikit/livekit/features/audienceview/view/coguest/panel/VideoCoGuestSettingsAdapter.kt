package com.trtc.uikit.livekit.features.audienceview.view.coguest.panel

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.trtc.uikit.livekit.R

class VideoCoGuestSettingsAdapter(
    private val context: Context,
) : RecyclerView.Adapter<VideoCoGuestSettingsAdapter.BaseViewHolder>() {

    companion object {
        private const val ITEM_SETTINGS_BEAUTY = 101
        private const val ITEM_SETTINGS_FLIP = 105
    }

    private val settingsItem: MutableList<SettingsItem> = ArrayList()
    private val data: List<SettingsItem>
    private var onItemClickListener: OnItemClickListener? = null

    init {
        initSettingsItem()
        data = settingsItem
        notifyDataSetChanged()
    }

    fun setOnItemClickListener(listener: OnItemClickListener) {
        onItemClickListener = listener
    }

    private fun initSettingsItem() {
        settingsItem.add(
            SettingsItem(
                context.getString(R.string.common_video_settings_item_beauty),
                R.drawable.livekit_video_settings_beauty,
                ITEM_SETTINGS_BEAUTY
            ) {
                onItemClickListener?.onBeautyItemClicked()
            })

        settingsItem.add(
            SettingsItem(
                context.getString(R.string.common_video_settings_item_flip),
                R.drawable.livekit_video_settings_flip,
                ITEM_SETTINGS_FLIP
            ) {
                onItemClickListener?.onFlipItemClicked()
            })
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BaseViewHolder {
        val itemView = LayoutInflater.from(parent.context)
            .inflate(R.layout.livekit_video_settings_item, parent, false)
        return SettingsViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: BaseViewHolder, position: Int) {
        holder.bindData(position)
    }

    override fun getItemCount(): Int {
        return data.size
    }

    abstract class BaseViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        abstract fun bindData(position: Int)
    }

    inner class SettingsViewHolder(itemView: View) : BaseViewHolder(itemView) {
        val layoutRoot: RelativeLayout = itemView.findViewById(R.id.rl_settings_item_root)
        val textTitle: TextView = itemView.findViewById(R.id.item_text)
        val imageIcon: ImageView = itemView.findViewById(R.id.item_image)

        override fun bindData(position: Int) {
            textTitle.text = data[position].title
            imageIcon.setImageResource(data[position].icon)
            layoutRoot.tag = data[position].type
            layoutRoot.setOnClickListener(data[position].listener)
        }
    }

    class SettingsItem(
        val title: String,
        val icon: Int,
        val type: Int,
        val listener: View.OnClickListener
    )

    interface OnItemClickListener {
        fun onBeautyItemClicked()
        fun onFlipItemClicked()
    }
}
