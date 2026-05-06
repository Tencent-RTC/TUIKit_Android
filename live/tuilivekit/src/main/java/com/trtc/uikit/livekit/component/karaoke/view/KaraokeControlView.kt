package com.trtc.uikit.livekit.component.karaoke.view


import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import com.tencent.cloud.tuikit.engine.extension.TUISongListManager
import com.tencent.trtc.TXChorusMusicPlayer
import com.tencent.trtc.TXChorusMusicPlayer.TXChorusRole
import com.trtc.uikit.livekit.R
import com.trtc.uikit.livekit.component.karaoke.store.KaraokeStore
import com.trtc.uikit.livekit.component.karaoke.store.utils.LyricAlign
import com.trtc.uikit.livekit.component.karaoke.store.utils.PlaybackState
import io.trtc.tuikit.atomicx.widget.basicwidget.avatar.AtomicAvatar
import io.trtc.tuikit.atomicx.widget.basicwidget.popover.AtomicPopover
import io.trtc.tuikit.atomicxcore.api.login.LoginStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class KaraokeControlView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0,
) : ConstraintLayout(context, attrs, defStyleAttr) {
    var isAudienceFirstEnterRoom = true
    private lateinit var roomId: String
    private lateinit var store: KaraokeStore
    private lateinit var lyricView: LyricView
    private lateinit var pitchView: PitchView
    private lateinit var textScore: TextView
    private lateinit var textSeg: TextView
    private lateinit var imageNext: ImageView
    private lateinit var imagePause: ImageView
    private lateinit var imageSetting: ImageView
    private lateinit var buttonJoinChorus: TextView
    private lateinit var textMusicName: TextView
    private lateinit var layoutRoot: FrameLayout
    private lateinit var layoutTime: LinearLayout
    private lateinit var layoutScore: FrameLayout
    private lateinit var textMusicAuthor: TextView
    private lateinit var textPlayProgress: TextView
    private lateinit var textPlayDuration: TextView
    private lateinit var textRequesterName: TextView
    private lateinit var layoutFunction: LinearLayout
    private lateinit var imageEnableOriginal: ImageView
    private lateinit var layoutRequestMusic: LinearLayout
    private lateinit var textAudienceWaitingTips: TextView
    private lateinit var textAudiencePauseTips: TextView
    private lateinit var imageRequesterAvatar: AtomicAvatar
    private lateinit var songRequestPanel: SongRequestPanel
    private val mainHandler = Handler(Looper.getMainLooper())
    private var subscribeStateJob: Job? = null

    init {
        LayoutInflater.from(context).inflate(R.layout.karaoke_control_view, this, true)
        bindViewId()
    }

    fun init(roomId: String, isOwner: Boolean) {
        this.roomId = roomId
        store = KaraokeStore.getInstance(context)
        store.init(roomId, isOwner)
        songRequestPanel = SongRequestPanel(context, store, false)
        initViews()
        addObservers()
    }

    fun release() {
        removeObservers()
        KaraokeStore.destroyInstance()
        mainHandler.removeCallbacksAndMessages(null)
    }

    fun showSongRequestPanel() {
        songRequestPanel.show()
    }

    private fun bindViewId() {
        layoutRoot = findViewById(R.id.fl_root)
        layoutTime = findViewById(R.id.ll_time_bar)
        imagePause = findViewById(R.id.iv_pause)
        textScore = findViewById(R.id.tv_score)
        layoutScore = findViewById(R.id.fl_score)
        textSeg = findViewById(R.id.tv_seg)
        imageNext = findViewById(R.id.iv_next)
        textMusicName = findViewById(R.id.tv_music_name)
        imageSetting = findViewById(R.id.iv_setting)
        textPlayProgress = findViewById(R.id.progress)
        textMusicAuthor = findViewById(R.id.tv_music_artist)
        layoutFunction = findViewById(R.id.ll_right_icons)
        imageEnableOriginal = findViewById(R.id.iv_original)
        textPlayDuration = findViewById(R.id.duration)
        textRequesterName = findViewById(R.id.tv_requester_name)
        imageRequesterAvatar = findViewById(R.id.iv_user_avatar)
        textAudienceWaitingTips = findViewById(R.id.tv_waiting_tips)
        textAudiencePauseTips = findViewById(R.id.tv_pause_tips)
        layoutRequestMusic = findViewById(R.id.ll_order_music)
        buttonJoinChorus = findViewById(R.id.btn_join_chorus)
    }

    private fun initViews() {
        initPitchView()
        initLyricView()
        initClickListeners()
    }

    private fun initClickListeners() {
        imagePause.setOnClickListener {
            if (store.playbackState.value == PlaybackState.START || store.playbackState.value == PlaybackState.RESUME) {
                store.pausePlayback()
            } else {
                store.resumePlayback()
            }
        }

        imageSetting.setOnClickListener {
            val atomicPopover = AtomicPopover(context)
            val karaokeSettingPanel = KaraokeSettingPanel(context)
            karaokeSettingPanel.init(store)
            karaokeSettingPanel.setOnBackButtonClickListener(object :
                KaraokeSettingPanel.OnBackButtonClickListener {
                override fun onClick() {
                    atomicPopover.dismiss()
                }
            })
            atomicPopover.setContent(karaokeSettingPanel)
            atomicPopover.show()
        }
        layoutRequestMusic.setOnClickListener { view ->
            view.post {
                if (songRequestPanel.isShowing) {
                    return@post
                }
                songRequestPanel.show()
            }
        }
        buttonJoinChorus.setOnClickListener {
            if (store.currentChorusRole.value == TXChorusRole.TXChorusRoleBackSinger) {
                store.setChorusRole(roomId, TXChorusRole.TXChorusRoleAudience)
            } else {
                store.setChorusRole(roomId, TXChorusRole.TXChorusRoleBackSinger)
            }
        }
        imageNext.setOnClickListener {
            store.playNextSong()
            store.setIsDisplayScoreView(false)
        }
        imageEnableOriginal.setOnClickListener {
            if (store.currentTrack.value == TXChorusMusicPlayer.TXChorusMusicTrack.TXChorusOriginalSong) {
                store.switchMusicTrack(TXChorusMusicPlayer.TXChorusMusicTrack.TXChorusAccompaniment)
            } else {
                store.switchMusicTrack(TXChorusMusicPlayer.TXChorusMusicTrack.TXChorusOriginalSong)
            }
        }
    }

    private fun addObservers() {
        subscribeStateJob = CoroutineScope(Dispatchers.Main).launch {
            launch {
                store.playbackProgressMs.collect { onProgressChanged(it) }
            }
            launch {
                store.playbackState.collect { onPlaybackStateChanged(it) }
            }
            launch {
                store.songDurationMs.collect { onDurationChanged(it) }
            }
            launch {
                store.currentTrack.collect { onCurrentTrackChanged(it) }
            }
            launch {
                store.currentPlayingSong.collect { onCurrentMusicChanged(it) }
            }
            launch {
                store.songQueue.collect { onPlayQueueChanged(it) }
            }
            launch {
                store.pitchList.collect { onPitchListChanged(it) }
            }
            launch {
                store.currentPitch.collect { onCurrentPitchChanged(it) }
            }
            launch {
                store.currentScore.collect { onCurrentScoreChanged(it) }
            }
            launch {
                store.enableScore.collect { onEnableScoreChanged(it) }
            }
            launch {
                store.hostScore.collect { onRemoteScoresChanged(it) }
            }
            launch {
                store.hostPitch.collect { onRemotePitchesChanged(it) }
            }
            launch {
                store.currentChorusRole.collect { updateChorusButton() }
            }
        }
    }

    private fun removeObservers() {
        subscribeStateJob?.cancel()
    }

    private fun onProgressChanged(progress: Long) {
        lyricView.setPlayProgress(progress)
        pitchView.setPlayProgress(progress)
        textPlayProgress.text = formatTime(progress)
    }

    private fun onDurationChanged(durationMs: Long) {
        textPlayDuration.text = formatTime(durationMs)
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (!this::store.isInitialized) {
            return
        }
        if (visibility == VISIBLE) {
            store.setFullScreenUIMode(true)
        }
    }

    private fun onCurrentTrackChanged(currentTrack: TXChorusMusicPlayer.TXChorusMusicTrack) {
        val resource =
            if (currentTrack == TXChorusMusicPlayer.TXChorusMusicTrack.TXChorusOriginalSong) R.drawable.karaoke_original_on
            else R.drawable.karaoke_original_off
        imageEnableOriginal.setImageResource(resource)
    }

    private fun onCurrentMusicChanged(currentSong: TUISongListManager.SongInfo) {
        if (currentSong.songId.isEmpty() || store.songQueue.value?.isEmpty() == true) {
            textMusicName.text = context.getString(R.string.karaoke_no_song)
            textMusicAuthor.text = null
            return
        }
        textMusicName.text = currentSong.songName
        textMusicAuthor.text = "- " + currentSong?.artistName
    }

    private fun onPlaybackStateChanged(playbackState: PlaybackState) {
        when (playbackState) {
            PlaybackState.IDLE -> handleIdleState()
            PlaybackState.START -> handleStartState()
            PlaybackState.RESUME -> handleResumeState()
            PlaybackState.PAUSE -> handlePausedState()
            PlaybackState.STOP -> handleStoppedState()
        }
    }

    private fun onPitchListChanged(pitchList: List<TXChorusMusicPlayer.TXReferencePitch>) {
        pitchView.setPitchList(pitchList)
    }

    private fun onCurrentPitchChanged(pitch: Int) {
        pitchView.setUserPitch(pitch)
    }

    private fun onCurrentScoreChanged(score: Int) {
        pitchView.setScore(score)
    }

    private fun onEnableScoreChanged(enableScore: Boolean) {
        pitchView.setScoringEnabled(enableScore)
    }

    private fun onRemoteScoresChanged(score: Int) {
        if (store.currentChorusRole.value != TXChorusRole.TXChorusRoleLeadSinger) {
            pitchView.setScore(score)
        }
    }

    private fun onRemotePitchesChanged(pitch: Int) {
        if (store.currentChorusRole.value != TXChorusRole.TXChorusRoleLeadSinger) {
            pitchView.setUserPitch(pitch)
        }
    }

    private fun handleIdleState() {
        layoutFunction.visibility = GONE
        layoutScore.visibility = GONE
        lyricView.visibility = GONE
        pitchView.visibility = GONE
        store.resetPlaybackInfo()
        pitchView.resetState()

        val currentSong = store.currentPlayingSong.value
        val queueNotEmpty = store.songQueue.value.orEmpty().isNotEmpty()
        val hasValidSong = !currentSong?.songId.isNullOrEmpty() && queueNotEmpty
        if (hasValidSong) {
            textMusicName.text = currentSong?.songName
            textMusicAuthor.text = "- " + currentSong?.artistName
        } else {
            textMusicName.text = context.getString(R.string.karaoke_no_song)
            textMusicAuthor.text = null
        }

        if (store.currentChorusRole.value == TXChorusRole.TXChorusRoleLeadSinger) {
            layoutRequestMusic.visibility = VISIBLE
        } else {
            updateAudienceWaitingUI()
        }
    }

    private fun updateUIForPlayingState() {
        if (store.currentChorusRole.value != TXChorusRole.TXChorusRoleLeadSinger) {
            isAudienceFirstEnterRoom = false
        }
        layoutScore.visibility = GONE
        lyricView.visibility = VISIBLE
        pitchView.visibility = VISIBLE
        layoutTime.visibility = VISIBLE
        layoutRequestMusic.visibility = GONE
        textAudienceWaitingTips.visibility = GONE
        textAudiencePauseTips.visibility = GONE
        layoutFunction.visibility = VISIBLE
        updateChorusButton()
        setSongProgressViewsVisible(true)
        imagePause.setImageResource(R.drawable.karaoke_music_resume)
        imageNext.setImageResource(R.drawable.karaoke_music_next)
        imageSetting.setImageResource(R.drawable.karaoke_setting)
    }

    private fun handleStartState() {
        updateUIForPlayingState()
    }

    private fun handleResumeState() {
        updateUIForPlayingState()
    }

    private fun handlePausedState() {
        layoutFunction.visibility = VISIBLE
        updateChorusButton()
        if (store.currentChorusRole.value != TXChorusRole.TXChorusRoleLeadSinger) {
            updateAudienceWaitingUI()
        }
        imagePause.setImageResource(R.drawable.karaoke_music_pause)
    }

    private fun setSongProgressViewsVisible(isVisible: Boolean) {
        val visibility = if (isVisible) VISIBLE else GONE
        textPlayDuration.visibility = visibility
        textPlayProgress.visibility = visibility
        textSeg.visibility = visibility
    }

    private fun handleStoppedState() {
        layoutFunction.visibility = GONE
        lyricView.visibility = GONE
        pitchView.visibility = GONE

        if (store.enableScore.value == true && store.isAwaitingScoreDisplay) {
            store.songDurationMs.value?.let { duration ->
                textPlayProgress.text = formatTime(duration)
            }
            layoutScore.visibility = VISIBLE
            textScore.text = store.averageScore.value.toString()
            imageRequesterAvatar.setContent(
                AtomicAvatar.AvatarContent.URL(
                    store.currentPlayingSong.value?.requester?.avatarUrl ?: "",
                    R.drawable.karaoke_song_cover
                )
            )
            if (store.currentChorusRole.value == TXChorusRole.TXChorusRoleBackSinger) {
                textRequesterName.text = LoginStore.shared.loginState.loginUserInfo.value?.nickname
            } else {
                textRequesterName.text = store.currentPlayingSong.value?.requester?.userName
            }
        } else {
            store.updatePlaybackStatus(PlaybackState.IDLE)
        }
    }

    private fun updateOwnerSpecificViews() {
        val isOwner = store.currentChorusRole.value == TXChorusRole.TXChorusRoleLeadSinger
        if (isOwner) {
            layoutRequestMusic.visibility = VISIBLE
        } else {
            updateAudienceWaitingUI()
        }
        updateChorusButton()
    }

    private fun updateChorusButton() {
        val isOwner = store.currentChorusRole.value == TXChorusRole.TXChorusRoleLeadSinger
        val currentRole = store.currentChorusRole.value
        
        if (isOwner) {
            buttonJoinChorus.visibility = GONE
            imagePause.visibility = VISIBLE
            imageNext.visibility = VISIBLE
            imageSetting.visibility = VISIBLE
            imageEnableOriginal.visibility = VISIBLE
        } else {
            imagePause.visibility = GONE
            imageNext.visibility = GONE

            when (currentRole) {
                TXChorusRole.TXChorusRoleBackSinger -> {
                    buttonJoinChorus.visibility = VISIBLE
                    buttonJoinChorus.text = context.getString(R.string.karaoke_exit_chorus)
                    imageSetting.visibility = VISIBLE
                    imageEnableOriginal.visibility = VISIBLE
                }
                TXChorusRole.TXChorusRoleAudience -> {
                    buttonJoinChorus.visibility = VISIBLE
                    buttonJoinChorus.text = context.getString(R.string.karaoke_join_chorus)
                    imageSetting.visibility = GONE
                    imageEnableOriginal.visibility = GONE
                }
                else -> {
                    buttonJoinChorus.visibility = GONE
                }
            }
        }
    }

    private fun updateAudienceWaitingUI() {
        if (store.currentChorusRole.value == TXChorusRole.TXChorusRoleLeadSinger) {
            textAudienceWaitingTips.visibility = GONE
            textAudiencePauseTips.visibility = GONE
            return
        }
        val isQueueEmpty = store.songQueue.value.orEmpty().isEmpty()
        val currentState = store.playbackState.value
        if (isQueueEmpty) {
            textAudienceWaitingTips.visibility = VISIBLE
            textAudiencePauseTips.visibility = GONE
        } else {
            if (currentState == PlaybackState.PAUSE && isAudienceFirstEnterRoom) {
                textAudienceWaitingTips.visibility = GONE
                textAudiencePauseTips.visibility = VISIBLE
                layoutTime.visibility = GONE
                setSongProgressViewsVisible(false)
                lyricView.visibility = GONE
                pitchView.visibility = GONE
            } else {
                textAudienceWaitingTips.visibility = GONE
                textAudiencePauseTips.visibility = GONE
            }
        }
    }

    private fun onPlayQueueChanged(list: List<TUISongListManager.SongInfo>) {
        if (store.currentChorusRole.value == TXChorusRole.TXChorusRoleLeadSinger) {
            return
        }
        updateAudienceWaitingUI()
        if (list.isEmpty()) {
            store.updatePlaybackStatus(PlaybackState.IDLE)
        }
    }

    private fun formatTime(millis: Long): String {
        val totalSeconds = millis / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format("%d:%02d", minutes, seconds)
    }

    fun initPitchView() {
        if (layoutRoot is ViewGroup) {
            (layoutRoot as ViewGroup).clipChildren = false
            (layoutRoot as ViewGroup).clipToPadding = false
        }

        pitchView = PitchView(context)
        val width = FrameLayout.LayoutParams.MATCH_PARENT
        val height =
            TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 56f, resources.displayMetrics)
                .toInt()
        val lp = FrameLayout.LayoutParams(width, height)
        lp.topMargin =
            TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 56f, resources.displayMetrics)
                .toInt()
        pitchView.layoutParams = lp
        pitchView.setBackgroundResource(R.drawable.karaoke_pitch_bg)
        layoutRoot.addView(pitchView)
        pitchView.visibility = GONE
    }

    fun initLyricView() {
        lyricView = LyricView(context, store)
        val width = FrameLayout.LayoutParams.MATCH_PARENT
        val height =
            TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 50f, resources.displayMetrics)
                .toInt()
        val lp = FrameLayout.LayoutParams(width, height)
        lp.topMargin =
            TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 116f, resources.displayMetrics)
                .toInt()
        lyricView.layoutParams = lp
        layoutRoot.addView(lyricView)
        lyricView.setLyricAlign(LyricAlign.CENTER)
        lyricView.setLyricTextSize(18f, 12f)
        lyricView.visibility = GONE
    }
}