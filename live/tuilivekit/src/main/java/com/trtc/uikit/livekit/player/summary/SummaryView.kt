package com.trtc.uikit.livekit.player.summary

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.widget.AppCompatImageView
import com.trtc.uikit.livekit.R
import com.trtc.uikit.livekit.common.LiveKitResources
import com.trtc.uikit.livekit.common.theme.imageloader.ImageLoader
import com.trtc.uikit.livekit.common.theme.imageloader.ImageOptions
import io.trtc.tuikit.atomicxcore.api.live.LiveInfo

class SummaryView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val avatar: AppCompatImageView
    private val anchorNameView: TextView
    private val backgroundView: ImageView
    private val titleView: TextView

    init {
        LayoutInflater.from(context).inflate(R.layout.livekit_player_summary, this, true)
        avatar = findViewById(R.id.iv_summary_avatar)
        anchorNameView = findViewById(R.id.tv_summary_anchor_name)
        backgroundView = findViewById(R.id.iv_background)
        titleView = findViewById(R.id.tv_summary_title)

        isClickable = true
        isFocusable = true
    }

    @JvmOverloads
    fun bind(liveInfo: LiveInfo, reason: String? = null) {
        bind(
            ownerName = liveInfo.liveOwner.userName.ifEmpty { liveInfo.liveOwner.userID },
            ownerAvatarURL = liveInfo.liveOwner.avatarURL,
            backgroundURL = liveInfo.backgroundURL.ifEmpty { liveInfo.coverURL },
            reason = reason,
        )
    }

    fun bind(ownerName: String, ownerAvatarURL: String, backgroundURL: String, reason: String? = null) {
        titleView.text = if (reason.isNullOrEmpty()) {
            context.getString(R.string.livelist_live_ended)
        } else {
            reason
        }
        ImageLoader.loadAvatar(avatar, ownerAvatarURL, R.drawable.livekit_livelist_default_avatar)
        anchorNameView.text = ownerName
        setBackground(backgroundURL)
    }

    private fun setBackground(backgroundURL: String) {
        val url = backgroundURL.ifEmpty { LiveKitResources.defaultBackgroundURL }

        val config = ImageOptions.Builder().setPlaceImage(R.drawable.livekit_video_background)
            .setErrorImage(R.drawable.livekit_video_background).build()
        ImageLoader.load(context, backgroundView, url, config = config)
        ImageLoader.applyBlur(backgroundView)
    }
}
