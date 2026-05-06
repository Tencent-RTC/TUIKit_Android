package com.trtc.uikit.livekit.features.anchorview.view.game

import android.content.Context
import android.text.TextUtils
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.tencent.imsdk.v2.V2TIMManager
import com.trtc.uikit.livekit.R
import io.trtc.tuikit.atomicx.widget.basicwidget.avatar.AtomicAvatar
import io.trtc.tuikit.atomicx.widget.basicwidget.avatar.AtomicAvatar.AvatarContent
import io.trtc.tuikit.atomicxcore.api.device.DeviceStatus
import io.trtc.tuikit.atomicxcore.api.live.SeatInfo

class AnchorSeatItemView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private var seatInfo: SeatInfo? = null
    private var isOwner: Boolean = false
    private var connectedCount: Int = 0

    private lateinit var flAvatarContainer: FrameLayout
    private lateinit var flEmptySeat: FrameLayout
    private lateinit var ivEmptyIcon: ImageView
    private lateinit var ivAvatar: AtomicAvatar
    private lateinit var llUserInfo: LinearLayout
    private lateinit var ivMicMute: ImageView
    private lateinit var tvUserName: TextView

    var onSeatClick: ((SeatInfo) -> Unit)? = null

    init {
        initView()
    }

    private fun initView() {
        LayoutInflater.from(context).inflate(R.layout.livekit_anchor_co_guest_seat_item_view, this, true)
        flAvatarContainer = findViewById(R.id.fl_avatar_container)
        flEmptySeat = findViewById(R.id.fl_empty_seat)
        ivEmptyIcon = findViewById(R.id.iv_empty_icon)
        ivAvatar = findViewById(R.id.iv_avatar)
        llUserInfo = findViewById(R.id.ll_user_info)
        ivMicMute = findViewById(R.id.iv_mic_mute)
        tvUserName = findViewById(R.id.tv_user_name)

        setOnClickListener {
            seatInfo?.let { info ->
                onSeatClick?.invoke(info)
            }
        }
    }

    fun setSeatInfo(seatInfo: SeatInfo?, isOwner: Boolean, connectedCount: Int) {
        this.seatInfo = seatInfo
        this.isOwner = isOwner
        this.connectedCount = connectedCount
        updateView()
    }

    private fun updateView() {
        val info = seatInfo ?: return

        val userID = info.userInfo.userID
        val hasUser = !TextUtils.isEmpty(userID)

        if (hasUser) {
            // 有用户在麦位上
            llUserInfo.setBackgroundResource(R.drawable.livekit_seat_item_name_bg)
            flEmptySeat.visibility = GONE
            ivAvatar.visibility = VISIBLE
            ivAvatar.setContent(
                AvatarContent.URL(
                    info.userInfo.avatarURL,
                    R.drawable.livekit_ic_avatar
                )
            )

            // 用户名
            val displayName = if (!TextUtils.isEmpty(info.userInfo.userName)) {
                info.userInfo.userName
            } else {
                info.userInfo.userID
            }
            tvUserName.visibility = VISIBLE
            tvUserName.text = displayName

            // 显示麦克风状态
            val isMicMuted = info.userInfo.microphoneStatus != DeviceStatus.ON
            ivMicMute.visibility = if (isMicMuted) VISIBLE else GONE
        } else {
            // 空麦位
            llUserInfo.setBackgroundResource(io.trtc.tuikit.atomicx.R.color.transparent)
            flEmptySeat.visibility = VISIBLE
            ivAvatar.visibility = GONE
            tvUserName.visibility = GONE
            ivMicMute.visibility = GONE

            // 主播显示占位图标，观众显示加号
            ivEmptyIcon.setImageResource(
                if (isOwner) R.drawable.livekit_voiceroom_empty_seat else R.drawable.livekit_empty_add
            )
        }
    }
}
