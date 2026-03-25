package com.trtc.uikit.livekit.features.audiencecontainer.view.settings

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.trtc.uikit.livekit.R
import com.trtc.uikit.livekit.component.dashboard.StreamDashboardDialog
import com.trtc.uikit.livekit.component.pippanel.PIPTogglePanel
import com.trtc.uikit.livekit.component.videoquality.VideoQualitySelectPanel
import com.trtc.uikit.livekit.features.audiencecontainer.store.AudienceStore
import io.trtc.tuikit.atomicxcore.api.device.VideoQuality

class AudienceSettingsListAdapter(
    private val context: Context,
    private val audienceStore: AudienceStore,
    private val settingsDialog: AudienceSettingsPanelDialog
) : RecyclerView.Adapter<AudienceSettingsListAdapter.ViewHolder>() {

    companion object {
        private const val ITEM_TYPE_DASHBOARD = 0
        private const val ITEM_TYPE_VIDEO_QUALITY = 1
        private const val ITEM_TYPE_PIP = 2
    }

    private val data: MutableList<SettingsItem> = ArrayList()

    init {
        initData()
    }

    private fun initData() {
        data.add(
            SettingsItem(
                context.getString(R.string.common_dashboard_title),
                R.drawable.livekit_settings_dashboard,
                ITEM_TYPE_DASHBOARD
            )
        )
        val coGuestState = audienceStore.getCoGuestState()
        val isCoGuestActive = coGuestState.connected.value.size > 1
                || coGuestState.applicants.value.isNotEmpty()
                || coGuestState.invitees.value.isNotEmpty()
        val hasMultipleQualityOptions = audienceStore.getMediaState().playbackQualityList.value.size > 1
        if (!isCoGuestActive && hasMultipleQualityOptions) {
            data.add(
                SettingsItem(
                    context.getString(R.string.live_video_resolution),
                    R.drawable.livekit_audience_video_quality_setting,
                    ITEM_TYPE_VIDEO_QUALITY
                )
            )
        }
        data.add(
            SettingsItem(
                context.getString(R.string.common_video_settings_item_pip),
                R.drawable.livekit_pip_icon,
                ITEM_TYPE_PIP
            )
        )
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val itemView = LayoutInflater.from(parent.context).inflate(
            R.layout.livekit_anchor_settings_panel_item,
            parent,
            false
        )
        return ViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.textTitle.text = data[position].title
        holder.imageIcon.setImageResource(data[position].icon)
        holder.layoutRoot.tag = data[position].type
        holder.layoutRoot.setOnClickListener { view ->
            val type = view.tag as Int
            when (type) {
                ITEM_TYPE_VIDEO_QUALITY -> showVideoQualityDialog()
                ITEM_TYPE_DASHBOARD -> showMediaDashboardDialog()
                ITEM_TYPE_PIP -> showPipDialog()
                else -> {}
            }
        }
    }

    private fun showMediaDashboardDialog() {
        settingsDialog.dismiss()
        val streamDashboardDialog = StreamDashboardDialog(context, audienceStore.getLiveListStore().liveState.currentLive.value.liveID)
        streamDashboardDialog.show()
    }

    private fun showVideoQualityDialog() {
        settingsDialog.dismiss()
        val videoQualitySelectPanel = VideoQualitySelectPanel(
            context,
            this@AudienceSettingsListAdapter.audienceStore.getMediaState().playbackQualityList.value
        )
        videoQualitySelectPanel.setOnVideoQualitySelectedListener(object :
            VideoQualitySelectPanel.OnVideoQualitySelectedListener {
            override fun onVideoQualitySelected(videoQuality: VideoQuality) {
                this@AudienceSettingsListAdapter.audienceStore.getMediaStore().switchPlaybackQuality(videoQuality)
            }
        })
        videoQualitySelectPanel.show()
    }

    private fun showPipDialog() {
        settingsDialog.dismiss()
        val pictureInPictureTogglePanel = PIPTogglePanel(
            context,
            audienceStore.getLiveListStore().liveState.currentLive.value.liveID
        )
        pictureInPictureTogglePanel.show()
    }

    override fun getItemCount(): Int {
        return data.size
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val layoutRoot: LinearLayout = itemView.findViewById(R.id.ll_root)
        val textTitle: TextView = itemView.findViewById(R.id.tv_title)
        val imageIcon: ImageView = itemView.findViewById(R.id.iv_icon)
    }

    data class SettingsItem(
        val title: String,
        val icon: Int,
        val type: Int
    )
}
