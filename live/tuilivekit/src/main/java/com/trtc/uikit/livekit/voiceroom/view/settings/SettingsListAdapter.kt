package com.trtc.uikit.livekit.voiceroom.view.settings

import android.content.Context
import android.graphics.Rect
import android.text.TextUtils
import android.util.DisplayMetrics
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.trtc.uikit.livekit.R
import com.trtc.uikit.livekit.common.BACKGROUND_THUMB_URL_LIST
import com.trtc.uikit.livekit.common.ErrorLocalized
import com.trtc.uikit.livekit.common.completionHandler
import com.trtc.uikit.livekit.component.audioeffect.AudioEffectPanel
import com.trtc.uikit.livekit.voiceroom.view.preview.StreamPresetImagePicker
import io.trtc.tuikit.atomicx.widget.basicwidget.popover.AtomicPopover
import io.trtc.tuikit.atomicxcore.api.live.LiveInfo
import io.trtc.tuikit.atomicxcore.api.live.LiveListStore
import io.trtc.tuikit.atomicxcore.api.live.SeatLayoutTemplate

class SettingsListAdapter(
    private val context: Context
) : RecyclerView.Adapter<SettingsListAdapter.ViewHolder>() {
    private val data = ArrayList<SettingsItem>()

    private var audioEffectDialog: AtomicPopover? = null
    private var streamPresetImagePicker: StreamPresetImagePicker? = null
    private var liveListStore = LiveListStore.shared()

    init {
        initData()
    }

    private fun initData() {
        data.add(
            SettingsItem(
                context.getString(R.string.common_settings_bg_image),
                R.drawable.livekit_setting_bg_image,
                ITEM_TYPE_BGM_IMAGE
            )
        )
        data.add(
            SettingsItem(
                context.getString(R.string.common_audio_effect),
                R.drawable.livekit_settings_audio_effect,
                ITEM_TYPE_AUDIO_EFFECT
            )
        )
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val itemView = LayoutInflater.from(parent.context).inflate(
            R.layout.livekit_voiceroom_settings_panel_item,
            parent, false
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
                ITEM_TYPE_BGM_IMAGE -> showBGMImagePanel()
                ITEM_TYPE_AUDIO_EFFECT -> showAudioEffectPanel()
                else -> {}
            }
        }
    }

    override fun getItemCount(): Int {
        return data.size
    }

    private fun showAudioEffectPanel() {
        if (audioEffectDialog == null) {
            audioEffectDialog = AtomicPopover(context)
            val audioEffectPanel = AudioEffectPanel(context)
            audioEffectPanel.init(liveListStore.liveState.currentLive.value.liveID)
            audioEffectPanel.setOnBackButtonClickListener(object :
                AudioEffectPanel.OnBackButtonClickListener {
                override fun onClick() {
                    audioEffectDialog?.dismiss()
                }
            })
            audioEffectDialog?.setContent(audioEffectPanel)
        }
        audioEffectDialog?.show()
    }

    private fun showBGMImagePanel() {
        if (streamPresetImagePicker == null) {
            val config = StreamPresetImagePicker.Config().apply {
                title = context.getString(R.string.common_settings_bg_image)
                confirmButtonText = context.getString(R.string.common_set_as_background)
                data = BACKGROUND_THUMB_URL_LIST
                currentImageUrl =
                    transferThumbUrlFromImage(liveListStore.liveState.currentLive.value.backgroundURL)
            }
            streamPresetImagePicker = StreamPresetImagePicker(context, config)
            streamPresetImagePicker?.setOnConfirmListener(object :
                StreamPresetImagePicker.OnConfirmListener {
                override fun onConfirm(imageUrl: String) {
                    val backgroundUrl = transferImageUrlFromThumb(imageUrl)
                    val liveInfo =
                        LiveInfo(
                            liveListStore.liveState.currentLive.value.liveID,
                            backgroundURL = backgroundUrl,
                            seatTemplate = SeatLayoutTemplate.AudioSalon(0),
                        )
                    liveListStore.updateLiveInfo(
                        liveInfo,
                        listOf(
                            LiveInfo.ModifyFlag.BACKGROUND_URL
                        ),
                        completionHandler {
                            onError { code, _ ->
                                ErrorLocalized.onError(code)
                            }
                        })
                }
            })
        }
        streamPresetImagePicker?.show()
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

    class SpaceItemDecoration(context: Context) : RecyclerView.ItemDecoration() {
        private val mSpace: Int

        init {
            val metrics: DisplayMetrics = context.resources.displayMetrics
            mSpace = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 20f, metrics).toInt()
        }

        override fun getItemOffsets(
            outRect: Rect,
            view: View,
            parent: RecyclerView,
            state: RecyclerView.State
        ) {
            outRect.left = mSpace
            outRect.right = mSpace
        }
    }

    private fun transferThumbUrlFromImage(imageUrl: String?): String? {
        if (TextUtils.isEmpty(imageUrl)) {
            return imageUrl
        }

        val index = imageUrl!!.indexOf(".png")
        if (index == -1) {
            return imageUrl
        }
        return imageUrl.substring(0, index) + "_thumb.png"
    }

    private fun transferImageUrlFromThumb(thumbUrl: String): String {
        if (TextUtils.isEmpty(thumbUrl)) {
            return thumbUrl
        }

        val index = thumbUrl.indexOf("_thumb.png")
        if (index == -1) {
            return thumbUrl
        }
        return thumbUrl.substring(0, index) + ".png"
    }

    companion object {
        private const val ITEM_TYPE_BGM_IMAGE = 0
        private const val ITEM_TYPE_AUDIO_EFFECT = 1
    }
}