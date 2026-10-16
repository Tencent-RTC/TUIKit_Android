package com.trtc.uikit.livekit.player.summary

import android.content.Context
import android.text.TextUtils
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import com.trtc.uikit.livekit.R
import com.trtc.uikit.livekit.common.LiveKitResources
import com.trtc.uikit.livekit.common.basic.imageloader.ImageLoader
import com.trtc.uikit.livekit.common.basic.imageloader.ImageOptions
import com.trtc.uikit.livekit.common.theme.avatar.AtomicAvatar
import io.trtc.tuikit.atomicxcore.api.live.LiveInfo

class SummaryView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val avatar: AtomicAvatar
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
        titleView.text = if (reason.isNullOrEmpty()) {
            context.getString(R.string.livelist_live_ended)
        } else {
            reason
        }
        avatar.setContent(
            AtomicAvatar.AvatarContent.URL(
                url = liveInfo.liveOwner.avatarURL,
                placeImage = R.drawable.livekit_livelist_default_avatar
            )
        )
        anchorNameView.text = if (TextUtils.isEmpty(liveInfo.liveOwner.userName)) {
            liveInfo.liveOwner.userID
        } else {
            liveInfo.liveOwner.userName
        }

        setBackground(liveInfo)
    }

    private fun setBackground(liveInfo: LiveInfo) {
        val url = liveInfo.backgroundURL.ifEmpty {
            liveInfo.coverURL.ifEmpty { LiveKitResources.defaultBackgroundURL }
        }

        val config = ImageOptions.Builder()
            .setBlurEffect(80f)
            .setPlaceImage(R.drawable.livekit_video_background)
            .setErrorImage(R.drawable.livekit_video_background)
            .build()
        ImageLoader.load(context, backgroundView, url, config = config)
    }
}
