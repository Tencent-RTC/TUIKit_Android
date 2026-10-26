package com.trtc.uikit.roomkit.view

import android.content.Context
import android.content.Intent
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.trtc.uikit.roomkit.R
import com.trtc.uikit.roomkit.RoomMainActivity
import com.trtc.uikit.roomkit.base.extension.getDisplayName
import io.trtc.tuikit.atomicx.widget.basicwidget.toast.AtomicToast
import io.trtc.tuikit.atomicxcore.api.login.LoginStore
import io.trtc.tuikit.atomicxcore.api.login.UserProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Room joining screen with room ID input.
 * Allows users to enter room ID and configure audio/video settings before joining.
 * Displays current user info and provides room joining functionality.
 */
class RoomJoinView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val etRoomId: EditText by lazy { findViewById(R.id.et_room_id) }
    private val tvYourName: TextView by lazy { findViewById(R.id.tv_your_name) }
    private val llAudio: LinearLayout by lazy { findViewById(R.id.ll_audio) }
    private val ivAudioSwitch: ImageView by lazy { findViewById(R.id.iv_audio_switch) }
    private val llSpeaker: LinearLayout by lazy { findViewById(R.id.ll_speaker) }
    private val ivSpeakerSwitch: ImageView by lazy { findViewById(R.id.iv_speaker_switch) }
    private val llVideo: LinearLayout by lazy { findViewById(R.id.ll_video) }
    private val ivVideoSwitch: ImageView by lazy { findViewById(R.id.iv_video_switch) }
    private val btnJoinRoom: Button by lazy { findViewById(R.id.btn_join_room) }

    private var loginStore: LoginStore = LoginStore.shared
    private var subscribeStateJob: Job? = null

    private var isAudioEnabled: Boolean = true
    private var isSpeakerEnabled: Boolean = true
    private var isVideoEnabled: Boolean = true

    init {
        LayoutInflater.from(context).inflate(R.layout.roomkit_view_join, this)
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
        llAudio.setOnClickListener {
            handleAudioClick()
        }

        llSpeaker.setOnClickListener {
            handleSpeakerClick()
        }

        llVideo.setOnClickListener {
            handleVideoClick()
        }

        btnJoinRoom.setOnClickListener {
            handleJoinRoomClick()
        }

        // Initialize switch states
        updateAudioSwitch()
        updateSpeakerSwitch()
        updateVideoSwitch()
    }

    private fun updateUserInfo(userInfo: UserProfile) {
        tvYourName.text = userInfo.getDisplayName()
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

    private fun handleJoinRoomClick() {
        val roomID = etRoomId.text.toString().trim()
        if (roomID.isEmpty()) {
            AtomicToast.show(
                context,
                context.getString(R.string.roomkit_input_can_not_empty),
                AtomicToast.Style.WARNING
            )
            return
        }

        val intent = Intent(context, RoomMainActivity::class.java).apply {
            putExtra(RoomMainActivity.EXTRA_ROOM_ID, roomID)
            putExtra(RoomMainActivity.EXTRA_IS_CREATE, false)
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
