package com.trtc.uikit.livekit.component.roominfo

import android.annotation.SuppressLint
import android.content.Context
import android.text.TextUtils
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.trtc.uikit.livekit.R
import com.trtc.uikit.livekit.common.reportEventData
import com.trtc.uikit.livekit.common.ui.setDebounceClickListener
import com.trtc.uikit.livekit.component.roominfo.service.RoomInfoService
import com.trtc.uikit.livekit.component.roominfo.store.RoomInfoState
import com.trtc.uikit.livekit.component.roominfo.view.RoomInfoPanel
import io.trtc.tuikit.atomicx.widget.basicwidget.avatar.AtomicAvatar
import io.trtc.tuikit.atomicx.widget.basicwidget.avatar.AtomicAvatar.AvatarContent
import io.trtc.tuikit.atomicx.widget.basicwidget.avatar.AtomicAvatar.AvatarShape
import io.trtc.tuikit.atomicx.widget.basicwidget.button.AtomicButton
import io.trtc.tuikit.atomicx.widget.basicwidget.button.ButtonColorType
import io.trtc.tuikit.atomicx.widget.basicwidget.button.ButtonIconPosition
import io.trtc.tuikit.atomicx.widget.basicwidget.button.ButtonVariant
import io.trtc.tuikit.atomicxcore.api.live.LiveEndedReason
import io.trtc.tuikit.atomicxcore.api.live.LiveInfo
import io.trtc.tuikit.atomicxcore.api.live.LiveListListener
import io.trtc.tuikit.atomicxcore.api.live.LiveListStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@SuppressLint("ViewConstructor")
class LiveInfoView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    companion object {
        private const val LIVEKIT_METRICS_PANEL_SHOW_LIVE_ROOM_LIVE_INFO = 190009
        private const val LIVEKIT_METRICS_PANEL_SHOW_VOICE_ROOM_LIVE_INFO = 191008
    }

    private lateinit var textNickName: TextView
    private lateinit var imageAvatar: AtomicAvatar
    private lateinit var layoutRoot: LinearLayout
    private lateinit var followButton: AtomicButton
    private var roomInfoPopupDialog: RoomInfoPanel? = null
    private val roomInfoService = RoomInfoService()
    private val roomInfoState: RoomInfoState = roomInfoService.roomInfoState
    private var subscribeStateJob: Job? = null

    private val liveListListener = object : LiveListListener() {
        override fun onLiveEnded(liveID: String, reason: LiveEndedReason, message: String) {
            roomInfoPopupDialog?.dismiss()
        }
    }

    init {
        LayoutInflater.from(context).inflate(R.layout.live_info_view, this, true)
        bindViewId()
    }

    fun init(liveInfo: LiveInfo) {
        init(liveInfo, true)
    }

    fun init(liveInfo: LiveInfo, enableFollow: Boolean) {
        roomInfoService.init(liveInfo)
        roomInfoState.enableFollow = enableFollow
        reportData(liveInfo.liveID)
        refreshView()
    }

    fun unInit() {
        roomInfoService.unInit()
    }

    fun setScreenOrientation(isPortrait: Boolean) {
        layoutRoot.isEnabled = isPortrait
    }

    fun showLiveInfoPanel() {
        if (roomInfoPopupDialog == null) {
            roomInfoPopupDialog = RoomInfoPanel(context, roomInfoService)
        }
        roomInfoPopupDialog?.show()
    }

    private fun initView() {
        initHostNameView()
        initHostAvatarView()
        initRoomInfoPanelView()
    }

    private fun refreshView() {
        if (!roomInfoState.enableFollow) {
            followButton.visibility = GONE
        }
    }

    private fun addObserver() {
        LiveListStore.shared().addLiveListListener(liveListListener)
        subscribeStateJob = CoroutineScope(Dispatchers.Main).launch {
            launch {
                roomInfoState.ownerId.collect {
                    onHostIdChange(it)
                }
            }
            launch {
                roomInfoState.ownerName.collect {
                    onHostNickNameChange(it)
                }
            }
            launch {
                roomInfoState.ownerAvatarUrl.collect {
                    onHostAvatarChange(it)
                }
            }
            launch {
                roomInfoState.followingList.collect {
                    onFollowStatusChange(it)
                }
            }
        }
    }

    private fun removeObserver() {
        LiveListStore.shared().removeLiveListListener(liveListListener)
        subscribeStateJob?.cancel()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        initView()
        addObserver()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        removeObserver()
    }

    private fun bindViewId() {
        layoutRoot = findViewById(R.id.ll_root)
        textNickName = findViewById(R.id.tv_name)
        imageAvatar = findViewById(R.id.iv_avatar)
        followButton = findViewById(R.id.atomic_btn_follow)
    }

    private fun initHostNameView() {
        val ownerName = roomInfoState.ownerName.value
        val ownerId = roomInfoState.ownerId.value
        textNickName.text = if (!TextUtils.isEmpty(ownerName)) ownerName else ownerId
    }

    private fun initHostAvatarView() {
        imageAvatar.apply {
            setShape(AvatarShape.Round)
            setContent(
                AvatarContent.URL(
                    url = roomInfoState.ownerAvatarUrl.value,
                    placeImage = R.drawable.room_info_default_avatar
                )
            )
        }
    }

    private fun initRoomInfoPanelView() {
        layoutRoot.setDebounceClickListener {
            if (roomInfoPopupDialog == null) {
                roomInfoPopupDialog = RoomInfoPanel(context, roomInfoService)
            }
            roomInfoPopupDialog?.show()
        }
    }

    private fun onHostIdChange(ownerId: String?) {
        if (!roomInfoState.enableFollow) {
            return
        }
        if (!TextUtils.isEmpty(ownerId) && !TextUtils.equals(roomInfoState.selfUserId, ownerId)) {
            followButton.visibility = VISIBLE
            ownerId?.let { roomInfoService.checkFollowUser(it) }
            refreshFollowButton()
            followButton.setDebounceClickListener { onFollowButtonClick() }
        }
        initHostNameView()
    }

    private fun onHostNickNameChange(name: String?) {
        initHostNameView()
    }

    private fun onHostAvatarChange(avatar: String?) {
        imageAvatar.setContent(
            AvatarContent.URL(
                url = avatar ?: "",
                placeImage = R.drawable.room_info_default_avatar
            )
        )
    }

    private fun onFollowStatusChange(followUsers: Set<String>?) {
        refreshFollowButton()
    }

    private fun onFollowButtonClick() {
        val followingList = roomInfoState.followingList.value ?: emptySet()
        val ownerId = roomInfoState.ownerId.value

        ownerId?.let { id ->
            if (followingList.contains(id)) {
                roomInfoService.unfollowUser(id)
            } else {
                roomInfoService.followUser(id)
            }
        }
    }

    private fun refreshFollowButton() {
        val followingList = roomInfoState.followingList.value
        val ownerId = roomInfoState.ownerId.value
        val isFollowed = followingList.contains(ownerId)

        if (!isFollowed) {
            followButton.apply {
                text = context.getString(R.string.common_follow_anchor)
                iconDrawable = null
                iconPosition = ButtonIconPosition.NONE

                variant = ButtonVariant.FILLED
                colorType = ButtonColorType.PRIMARY
                isEnabled = true
            }
        } else {
            followButton.apply {
                text = ""
                iconDrawable = context.getDrawable(R.drawable.room_info_followed_button_check)
                iconPosition = ButtonIconPosition.START
                variant = ButtonVariant.FILLED
                colorType = ButtonColorType.SECONDARY
                isEnabled = true
            }
        }
    }

    private fun reportData(roomId: String?) {
        val isVoiceRoom = !TextUtils.isEmpty(roomId) && roomId?.startsWith("voice_") == true
        if (isVoiceRoom) {
            reportEventData(LIVEKIT_METRICS_PANEL_SHOW_VOICE_ROOM_LIVE_INFO)
        } else {
            reportEventData(LIVEKIT_METRICS_PANEL_SHOW_LIVE_ROOM_LIVE_INFO)
        }
    }
}