package com.trtc.uikit.livekit.voiceroom.interaction.common

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.trtc.uikit.livekit.R
import com.trtc.uikit.livekit.voiceroom.manager.VoiceRoomManager
import com.trtc.uikit.livekit.voiceroomcore.view.VoiceWaveView
import io.trtc.tuikit.atomicx.common.imageloader.ImageLoader
import io.trtc.tuikit.atomicx.widget.basicwidget.avatar.AtomicAvatar
import io.trtc.tuikit.atomicx.widget.basicwidget.avatar.AtomicAvatar.AvatarContent
import io.trtc.tuikit.atomicx.widget.basicwidget.popover.AtomicPopover
import io.trtc.tuikit.atomicxcore.api.device.DeviceStatus
import io.trtc.tuikit.atomicxcore.api.live.LiveListStore
import io.trtc.tuikit.atomicxcore.api.live.LiveSeatStore
import io.trtc.tuikit.atomicxcore.api.live.SeatInfo
import jp.wasabeef.glide.transformations.BlurTransformation
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class CoHostView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private lateinit var imageAvatar: AtomicAvatar
    private lateinit var soundWaveView: VoiceWaveView
    private lateinit var seatInfo: SeatInfo
    private lateinit var imageAvatarBg: ImageView
    private lateinit var textName: TextView
    private lateinit var imageMuteAudio: ImageView
    private lateinit var liveSeatStore: LiveSeatStore
    private lateinit var layoutRoot: FrameLayout
    private var lifecycleOwner: LifecycleOwner? = null
    private val jobs = mutableListOf<Job>()
    private var voiceRoomManager: VoiceRoomManager? = null

    init {
        initView()
    }

    fun init(seatInfo: SeatInfo, voiceRoomManager: VoiceRoomManager?) {
        this.seatInfo = seatInfo
        this.voiceRoomManager = voiceRoomManager
        this.liveSeatStore =
            LiveSeatStore.create(LiveListStore.shared().liveState.currentLive.value.liveID)

        bindData()
        initClickListener()
        addObservers()
    }

    private fun bindData() {
        updateAvatar()
        setBlurredBackground()
        updateUserName()
        updateMicrophoneStatus()
    }

    private fun updateAvatar() {
        imageAvatar.apply {
            setContent(
                AvatarContent.URL(
                    url = seatInfo.userInfo.avatarURL,
                    placeImage = R.drawable.livekit_ic_avatar
                )
            )
        }
        ImageLoader.load(
            context,
            imageAvatarBg,
            seatInfo.userInfo.avatarURL,
            R.drawable.livekit_ic_avatar
        )
    }

    private fun setBlurredBackground() {
        val blurRadius = 20
        val sampling = 1

        Glide.with(this)
            .load(seatInfo.userInfo.avatarURL)
            .placeholder(R.drawable.livekit_ic_avatar)
            .error(R.drawable.livekit_ic_avatar)
            .apply(
                RequestOptions.bitmapTransform(
                    BlurTransformation(blurRadius, sampling)
                )
            )
            .into(imageAvatarBg)
    }


    private fun updateUserName() {
        textName.text = seatInfo.userInfo.userName.ifEmpty { seatInfo.userInfo.userID }
    }

    private fun updateMicrophoneStatus() {
        imageMuteAudio.visibility = if (seatInfo.userInfo.microphoneStatus == DeviceStatus.ON) GONE else VISIBLE
    }

    private fun initClickListener() {
        layoutRoot.setOnClickListener {
            val panel = CoHostViewManagerPanel(context)
            panel.init(seatInfo, voiceRoomManager)
            val popupDialog = AtomicPopover(context)
            popupDialog.setContent(panel)
            panel.setOnInviteButtonClickListener { popupDialog.hide() }
            popupDialog.show()
        }
    }

    private fun initView() {
        LayoutInflater.from(context).inflate(R.layout.livekit_voiceroom_co_host_view, this, true)
        layoutRoot = findViewById(R.id.fl_root)
        imageAvatar = findViewById(R.id.iv_avatar)
        soundWaveView = findViewById(R.id.rv_sound_wave)
        imageAvatarBg = findViewById(R.id.iv_avatar_bg)
        imageMuteAudio = findViewById(R.id.iv_mute_audio)
        textName = findViewById(R.id.tv_name)
    }

    private fun addObservers() {
        lifecycleOwner?.lifecycleScope?.let { scope ->
            liveSeatStore.liveSeatState.speakingUsers
                .onEach(::onSpeakingUsersChange)
                .launchIn(scope)
                .let(jobs::add)
        }
    }

    private fun onSpeakingUsersChange(speakingUsers: Map<String, Int>) {
        val volume = speakingUsers[seatInfo.userInfo.userID]
        val isSpeaking = volume != null && volume > 25
        soundWaveView.visibility = if (isSpeaking) VISIBLE else GONE
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        jobs.forEach { it.cancel() }
        jobs.clear()
        lifecycleOwner = null
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        jobs.forEach { it.cancel() }
        jobs.clear()
    }
}