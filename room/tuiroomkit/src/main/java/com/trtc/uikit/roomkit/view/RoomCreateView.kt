package com.trtc.uikit.roomkit.view

import android.content.Context
import android.content.Intent
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.trtc.uikit.roomkit.R
import com.trtc.uikit.roomkit.RoomMainActivity
import com.trtc.uikit.roomkit.base.extension.getDisplayName
import com.trtc.uikit.roomkit.base.ui.RoomActionSheetDialog
import com.trtc.uikit.roomkit.base.utils.generateRoomID
import com.trtc.uikit.roomkit.base.utils.generateWebinarRoomID
import io.trtc.tuikit.atomicxcore.api.login.LoginStore
import io.trtc.tuikit.atomicxcore.api.login.UserProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Room creation configuration screen.
 * Allows users to configure room settings including audio, speaker, and video options.
 * Displays current user info and provides room creation functionality.
 */
class RoomCreateView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    enum class RoomType { STANDARD, WEBINAR }

    private val tvYourName: TextView by lazy { findViewById(R.id.tv_your_name) }
    private val llRoomType: LinearLayout by lazy { findViewById(R.id.ll_room_type_container) }
    private val tvRoomType: TextView by lazy { findViewById(R.id.tv_room_type) }
    private val llAudio: LinearLayout by lazy { findViewById(R.id.ll_audio) }
    private val ivAudioSwitch: ImageView by lazy { findViewById(R.id.iv_audio_switch) }
    private val llSpeaker: LinearLayout by lazy { findViewById(R.id.ll_speaker) }
    private val ivSpeakerSwitch: ImageView by lazy { findViewById(R.id.iv_speaker_switch) }
    private val llVideo: LinearLayout by lazy { findViewById(R.id.ll_video) }
    private val ivVideoSwitch: ImageView by lazy { findViewById(R.id.iv_video_switch) }
    private val btnCreateRoom: Button by lazy { findViewById(R.id.btn_create_room) }

    private var loginStore: LoginStore = LoginStore.shared
    private var subscribeStateJob: Job? = null

    private var isAudioEnabled: Boolean = true
    private var isSpeakerEnabled: Boolean = true
    private var isVideoEnabled: Boolean = true
    private var selectedRoomType: RoomType = RoomType.STANDARD

    init {
        LayoutInflater.from(context).inflate(R.layout.roomkit_view_create, this)
        initView()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        addObserver()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        removeObserver()
    }

    private fun addObserver() {
        subscribeStateJob = CoroutineScope(Dispatchers.Main).launch {
            loginStore.loginState.loginUserInfo.collect { loginUserInfo ->
                loginUserInfo?.let {
                    updateUserInfo(it)
                }
            }
        }
    }

    private fun removeObserver() {
        subscribeStateJob?.cancel()
    }

    private fun initView() {
        llRoomType.setOnClickListener {
            showRoomTypeDialog()
        }

        llAudio.setOnClickListener {
            handleAudioClick()
        }

        llSpeaker.setOnClickListener {
            handleSpeakerClick()
        }

        llVideo.setOnClickListener {
            handleVideoClick()
        }

        btnCreateRoom.setOnClickListener {
            handleCreateRoomClick()
        }

        selectRoomType(selectedRoomType)
    }

    private fun selectRoomType(type: RoomType) {
        selectedRoomType = type
        tvRoomType.text = when (type) {
            RoomType.STANDARD -> context.getString(R.string.roomkit_room_type_meeting)
            RoomType.WEBINAR -> context.getString(R.string.roomkit_room_type_webinar)
        }
    }

    private fun showRoomTypeDialog() {
        RoomActionSheetDialog.Builder(context)
            .setBackgroundResource(R.drawable.roomkit_bg_bottom_sheet_dialog_white)
            .setTextColor(R.color.roomkit_color_text_primary)
            .setDividerColor(R.color.roomkit_color_background)
            .addAction(R.string.roomkit_room_type_meeting) {
                selectRoomType(RoomType.STANDARD)
            }
            .addAction(R.string.roomkit_room_type_webinar) {
                selectRoomType(RoomType.WEBINAR)
            }
            .show()
    }

    private fun updateUserInfo(userInfo: UserProfile) {
        val userName = when {
            !userInfo.nickname.isNullOrEmpty() -> userInfo.nickname
            else -> userInfo.userID
        }
        tvYourName.text = userName
    }

    private fun handleAudioClick() {
        isAudioEnabled = !isAudioEnabled
        updateAudioSwitch()
    }

    private fun handleSpeakerClick() {
        isSpeakerEnabled = !isSpeakerEnabled
        updateSpeakerSwitch()
    }

    private fun handleVideoClick() {
        isVideoEnabled = !isVideoEnabled
        updateVideoSwitch()
    }

    private fun handleCreateRoomClick() {
        val roomID = when (selectedRoomType) {
            RoomType.WEBINAR -> generateWebinarRoomID()
            RoomType.STANDARD -> generateRoomID()
        }
        val localUserName = LoginStore.shared.loginState.loginUserInfo.value?.getDisplayName() ?: ""
        val roomName = if (selectedRoomType == RoomType.WEBINAR) {
            context.getString(R.string.roomkit_user_webinar_room, localUserName)
        } else {
            context.getString(R.string.roomkit_user_room, localUserName)
        }
        val intent = Intent(context, RoomMainActivity::class.java).apply {
            putExtra(RoomMainActivity.EXTRA_ROOM_ID, roomID)
            putExtra(RoomMainActivity.EXTRA_ROOM_NAME, roomName)
            putExtra(RoomMainActivity.EXTRA_IS_CREATE, true)
            putExtra(RoomMainActivity.EXTRA_AUTO_ENABLE_MICROPHONE, isAudioEnabled)
            putExtra(RoomMainActivity.EXTRA_AUTO_ENABLE_CAMERA, isVideoEnabled)
            putExtra(RoomMainActivity.EXTRA_AUTO_ENABLE_SPEAKER, isSpeakerEnabled)
        }
        context.startActivity(intent)
    }

    private fun updateAudioSwitch() {
        ivAudioSwitch.setImageResource(
            if (isAudioEnabled) R.drawable.roomkit_ic_switch_on
            else R.drawable.roomkit_ic_switch_off
        )
    }

    private fun updateSpeakerSwitch() {
        ivSpeakerSwitch.setImageResource(
            if (isSpeakerEnabled) R.drawable.roomkit_ic_switch_on
            else R.drawable.roomkit_ic_switch_off
        )
    }

    private fun updateVideoSwitch() {
        ivVideoSwitch.setImageResource(
            if (isVideoEnabled) R.drawable.roomkit_ic_switch_on
            else R.drawable.roomkit_ic_switch_off
        )
    }
}