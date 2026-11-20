package com.trtc.uikit.livekit.voicelounge.living.music

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.DrawableRes
import com.trtc.uikit.livekit.R
import com.trtc.uikit.livekit.common.theme.BottomSheetView
import com.trtc.uikit.livekit.voicelounge.MATCH
import com.trtc.uikit.livekit.voicelounge.VoiceLoungeBag
import com.trtc.uikit.livekit.voicelounge.VoiceLoungeMain
import com.trtc.uikit.livekit.voicelounge.WRAP
import com.trtc.uikit.livekit.voicelounge.dp
import com.trtc.uikit.livekit.voicelounge.frameParams
import com.trtc.uikit.livekit.voicelounge.linearParams
import com.trtc.uikit.livekit.voicelounge.roundedDrawable
import com.trtc.uikit.livekit.voicelounge.settings.theme.VoiceLoungeSkin
import com.trtc.uikit.livekit.voicelounge.setPressAlpha
import com.trtc.uikit.livekit.voicelounge.tint
import com.trtc.uikit.livekit.voicelounge.whiteAlpha
import com.trtc.uikit.livekit.voicelounge.withAlpha
import io.trtc.tuikit.atomicxcore.api.device.AudioMusicParam
import io.trtc.tuikit.atomicxcore.api.device.MusicListener
import io.trtc.tuikit.atomicxcore.api.device.MusicPlayStatus
import io.trtc.tuikit.atomicxcore.api.device.MusicStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

internal class VoiceLoungeMusicView(context: Context) : BottomSheetView(context) {

    private enum class PlayMode {
        ORDER, SINGLE, SHUFFLE;

        fun next(): PlayMode = values()[(ordinal + 1) % values().size]
    }

    private data class Track(
        val title: String,
        val artist: String,
        val url: String,
        val duration: String,
        @DrawableRes val coverRes: Int,
    )

    private val tracks: List<Track> = listOf(
        Track(
            title = context.getString(R.string.livekit_pusher_music_happy),
            artist = "TUIKit",
            url = "https://dldir1.qq.com/hudongzhibo/TUIKit/resource/music/PositiveHappyAdvertising.mp3",
            duration = "5:45",
            coverRes = R.drawable.livekit_voicefx_r_loud,
        ),
        Track(
            title = context.getString(R.string.livekit_pusher_music_sad),
            artist = "TUIKit",
            url = "https://dldir1.qq.com/hudongzhibo/TUIKit/resource/music/SadCinematicPiano.mp3",
            duration = "6:06",
            coverRes = R.drawable.livekit_voicefx_r_deep,
        ),
        Track(
            title = context.getString(R.string.livekit_pusher_music_wonder_world),
            artist = "TUIKit",
            url = "https://dldir1.qq.com/hudongzhibo/TUIKit/resource/music/WonderWorld.mp3",
            duration = "2:05",
            coverRes = R.drawable.livekit_voicefx_r_ktv,
        ),
    )

    private val countLabel = TextView(context)
    private val modeButton = MusicModeButton(context)
    private val prevButton = MusicControlButton(context, R.drawable.livekit_voicebgm_prev)
    private val nextButton = MusicControlButton(context, R.drawable.livekit_voicebgm_next)
    private val rowsStack = LinearLayout(context)
    private val rows: List<MusicTrackRow>

    private var musicStore: MusicStore? = null
    private var boundLiveID = ""
    private var currentIndex = 0
    private var mode = PlayMode.ORDER
    private val history = ArrayDeque<Int>()
    private var playURL: String? = null
    private var playStatus: MusicPlayStatus = MusicPlayStatus.IDLE
    private var isActive = false
    private var isSuspended = false
    private var resumeURL: String? = null
    private var skin = VoiceLoungeSkin.defaultSkin

    private var stateScope: CoroutineScope? = null
    private val bag = VoiceLoungeBag()
    private val listener = object : MusicListener() {
        override fun onPlayCompleted(playURL: String) {
            VoiceLoungeMain.post { handlePlayCompleted(playURL) }
        }
    }

    override val sheetHeight: Int
        get() = context.dp(LIST_TOP) + tracks.size * context.dp(ROW_HEIGHT + ROW_SPACING) + context.dp(BOTTOM_INSET)

    init {
        sheetTitle = context.getString(R.string.livekit_voiceroom_living_bgm)
        setupViews()
        setupStyles()
        setupBindings()
        rows = tracks.mapIndexed { index, track ->
            MusicTrackRow(context, index, track).also { row ->
                row.setOnClickListener { toggleOrSelect(index) }
                rowsStack.addView(
                    row,
                    linearParams(MATCH, context.dp(ROW_HEIGHT), top = if (index == 0) 0 else context.dp(ROW_SPACING))
                )
            }
        }
        applySkin(skin)
        render()
    }

    private fun setupViews() {
        val hInset = context.dp(HORIZONTAL_INSET)
        val toolbarTop = context.dp(TOOLBAR_TOP + 2)
        val control = context.dp(CONTROL_SIZE)
        contentView.addView(
            nextButton,
            frameParams(control, control, Gravity.TOP or Gravity.END, top = toolbarTop, end = hInset + context.dp(4)),
        )
        contentView.addView(
            prevButton,
            frameParams(
                control, control, Gravity.TOP or Gravity.END, top = toolbarTop,
                end = hInset + context.dp(4) + control + context.dp(6)
            ),
        )
        contentView.addView(
            modeButton,
            frameParams(
                WRAP, context.dp(24),
                Gravity.TOP or Gravity.END,
                top = toolbarTop + (control - context.dp(24)) / 2,
                end = hInset + context.dp(4) + control * 2 + context.dp(12),
            ),
        )
        contentView.addView(
            countLabel,
            frameParams(
                WRAP, WRAP, Gravity.TOP or Gravity.START, start = hInset + context.dp(4),
                top = toolbarTop + (control - context.dp(14)) / 2
            ),
        )
        rowsStack.orientation = LinearLayout.VERTICAL
        contentView.addView(
            rowsStack, frameParams(MATCH, WRAP, Gravity.TOP, start = hInset, end = hInset, top = context.dp(LIST_TOP))
        )
    }

    private fun setupStyles() {
        countLabel.setTextColor(whiteAlpha(0.45f))
        countLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
        countLabel.includeFontPadding = false
        countLabel.text = context.getString(R.string.livekit_voiceroom_bgm_track_count, tracks.size)
        prevButton.contentDescription = context.getString(R.string.livekit_voiceroom_bgm_prev)
        nextButton.contentDescription = context.getString(R.string.livekit_voiceroom_bgm_next)
    }

    private fun setupBindings() {
        modeButton.setOnClickListener { switchMode() }
        prevButton.setOnClickListener { step(-1) }
        nextButton.setOnClickListener { step(1) }
    }

    fun bind(liveID: String) {
        if (boundLiveID == liveID) return
        val reactivate = isActive
        deactivate()
        boundLiveID = liveID
        musicStore = liveID.takeIf(String::isNotEmpty)?.let(MusicStore::create)
        currentIndex = 0
        history.clear()
        playURL = null
        playStatus = MusicPlayStatus.IDLE
        render()
        if (reactivate) activate()
    }

    fun activate() {
        val store = musicStore ?: return
        if (isActive) return
        isActive = true
        store.addMusicListener(listener)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        stateScope = scope
        scope.launch {
            combine(
                store.musicState.playURL, store.musicState.playStatus
            ) { url, status -> url to status }.collectLatest { (url, status) -> updateState(url, status) }
        }
        updateState(store.musicState.playURL.value, store.musicState.playStatus.value)
    }

    fun deactivate() {
        isSuspended = false
        resumeURL = null
        if (!isActive) return
        isActive = false
        musicStore?.removeMusicListener(listener)
        stateScope?.cancel()
        stateScope = null
        bag.clear()
        musicStore?.stopPlay()
        playURL = null
        playStatus = MusicPlayStatus.IDLE
        currentIndex = 0
        history.clear()
        render()
    }

    fun setSuspended(suspended: Boolean) {
        if (isSuspended == suspended) return
        isSuspended = suspended
        val store = musicStore ?: return
        if (!isActive) return
        val status = store.musicState.playStatus.value
        val url = store.musicState.playURL.value
        if (suspended) {
            resumeURL = url?.takeIf { status == MusicPlayStatus.PLAYING || status == MusicPlayStatus.LOADING }
            if (resumeURL != null) store.pausePlay()
        } else {
            val shouldResume = resumeURL != null && resumeURL == url && status == MusicPlayStatus.PAUSED
            resumeURL = null
            if (shouldResume) store.resumePlay()
        }
    }

    fun applySkin(skin: VoiceLoungeSkin) {
        if (this.skin == skin) return
        this.skin = skin
        modeButton.applySkin(skin)
        rows.forEach { it.applySkin(skin) }
    }

    override fun show() {
        render()
        super.show()
    }

    fun dismissIfShowing(): Boolean {
        if (!isShowing) return false
        dismiss()
        return true
    }

    private val isPlaying: Boolean
        get() = playURL == tracks[currentIndex].url && (playStatus == MusicPlayStatus.PLAYING || playStatus == MusicPlayStatus.LOADING)

    private fun render() {
        val playing = isPlaying
        rows.forEachIndexed { index, row ->
            row.bind(isCurrent = index == currentIndex, isPlaying = playing && index == currentIndex)
        }
        modeButton.bind(mode)
    }

    private fun updateState(url: String?, status: MusicPlayStatus) {
        if (!isActive) return
        if (playURL == url && playStatus == status) return
        playURL = url
        playStatus = status
        if (url != null && status != MusicPlayStatus.IDLE) {
            tracks.indexOfFirst { it.url == url }.takeIf { it >= 0 }?.let { currentIndex = it }
        }
        render()
        if (isSuspended && status == MusicPlayStatus.PLAYING) musicStore?.pausePlay()
    }

    private fun toggleOrSelect(index: Int) {
        if (index == currentIndex) togglePlay() else select(index)
    }

    private fun togglePlay() {
        if (!isActive || isSuspended) return
        val store = musicStore ?: return
        val url = tracks[currentIndex].url
        if (playURL != url) {
            start(url)
            return
        }
        when (playStatus) {
            MusicPlayStatus.PLAYING, MusicPlayStatus.LOADING -> store.pausePlay()
            MusicPlayStatus.PAUSED -> store.resumePlay()
            MusicPlayStatus.IDLE -> start(url)
        }
    }

    private fun select(index: Int, remember: Boolean = true) {
        if (!isActive || isSuspended || index !in tracks.indices) return
        if (remember && index != currentIndex) history.addLast(currentIndex)
        currentIndex = index
        render()
        start(tracks[index].url)
    }

    private fun start(url: String) {
        val store = musicStore ?: return
        if (playURL != null && playStatus != MusicPlayStatus.IDLE) store.stopPlay()
        store.setPitch(0.0)
        store.startPlay(AudioMusicParam(path = url), null)
    }

    private fun step(direction: Int) {
        if (tracks.isEmpty()) return
        if (mode == PlayMode.SHUFFLE) {
            if (direction < 0 && history.isNotEmpty()) {
                select(history.removeLast(), remember = false)
                return
            }
            val candidates = tracks.indices.filter { tracks.size == 1 || it != currentIndex }
            select(candidates.random())
            return
        }
        select((currentIndex + direction + tracks.size) % tracks.size)
    }

    private fun handlePlayCompleted(url: String) {
        if (!isActive || isSuspended) return
        if (url != tracks[currentIndex].url) return
        if (mode == PlayMode.SINGLE) start(url) else step(1)
    }

    private fun switchMode() {
        mode = mode.next()
        modeButton.bind(mode)
        modeButton.contentDescription = context.getString(
            R.string.livekit_voiceroom_bgm_mode_tip, context.getString(modeLabelRes(mode)),
        )
    }

    private fun modeLabelRes(mode: PlayMode): Int = when (mode) {
        PlayMode.ORDER -> R.string.livekit_voiceroom_bgm_mode_order
        PlayMode.SINGLE -> R.string.livekit_voiceroom_bgm_mode_single
        PlayMode.SHUFFLE -> R.string.livekit_voiceroom_bgm_mode_shuffle
    }

    private inner class MusicModeButton(context: Context) : LinearLayout(context) {
        private val iconView = ImageView(context)
        private val titleLabel = TextView(context)

        init {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(context.dp(10), 0, context.dp(10), 0)
            iconView.scaleType = ImageView.ScaleType.FIT_CENTER
            addView(iconView, linearParams(context.dp(12), context.dp(12)))
            titleLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            titleLabel.includeFontPadding = false
            titleLabel.gravity = Gravity.CENTER_VERTICAL
            addView(titleLabel, linearParams(WRAP, WRAP, start = context.dp(4)))
            background = roundedDrawable(Color.TRANSPARENT, context.dp(12).toFloat(), context.dp(1), whiteAlpha(0.08f))
            setPressAlpha()
            applySkin(VoiceLoungeSkin.defaultSkin)
        }

        fun bind(mode: PlayMode) {
            iconView.setImageResource(
                when (mode) {
                    PlayMode.ORDER -> R.drawable.livekit_voicebgm_order
                    PlayMode.SINGLE -> R.drawable.livekit_voicebgm_single
                    PlayMode.SHUFFLE -> R.drawable.livekit_voicebgm_shuffle
                },
            )
            titleLabel.text = context.getString(modeLabelRes(mode))
        }

        fun applySkin(skin: VoiceLoungeSkin) {
            background =
                roundedDrawable(skin.mid.withAlpha(0.12f), context.dp(12).toFloat(), context.dp(1), whiteAlpha(0.08f))
            val tint = skin.textSoft
            titleLabel.setTextColor(tint)
            iconView.tint(tint)
        }
    }

    private class MusicControlButton(context: Context, @DrawableRes icon: Int) : FrameLayout(context) {
        init {
            val iv = ImageView(context).apply {
                setImageResource(icon)
                scaleType = ImageView.ScaleType.FIT_CENTER
                tint(whiteAlpha(0.72f))
            }
            addView(iv, frameParams(context.dp(13), context.dp(13), Gravity.CENTER))
            background = roundedDrawable(
                whiteAlpha(0.05f), context.dp(CONTROL_SIZE / 2).toFloat(), context.dp(1), whiteAlpha(0.08f)
            )
            setPressAlpha()
        }
    }

    private inner class MusicTrackRow(context: Context, val index: Int, val track: Track) : FrameLayout(context) {
        private val indexLabel = TextView(context)
        private val equalizer = MusicEqualizerView(context)
        private val cover = ImageView(context)
        private val titleLabel = TextView(context)
        private val artistLabel = TextView(context)
        private val durationLabel = TextView(context)
        private val playButton = FrameLayout(context)
        private val playIcon = ImageView(context)
        private val playGradient = GradientDrawable().apply {
            orientation = GradientDrawable.Orientation.TL_BR
            shape = GradientDrawable.OVAL
        }
        private var skin = VoiceLoungeSkin.defaultSkin
        private var isCurrent = false
        private var isPlaying = false
        private val background = GradientDrawable().apply {
            cornerRadius = context.dp(12).toFloat()
            setStroke(context.dp(1), Color.TRANSPARENT)
            setColor(Color.TRANSPARENT)
        }

        init {
            setBackground(background)
            indexLabel.setTextColor(whiteAlpha(0.45f))
            indexLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            indexLabel.gravity = Gravity.CENTER
            indexLabel.text = "%02d".format(index + 1)
            addView(
                indexLabel,
                frameParams(context.dp(22), WRAP, Gravity.CENTER_VERTICAL or Gravity.START, start = context.dp(6))
            )
            addView(
                equalizer,
                frameParams(
                    context.dp(14), context.dp(16), Gravity.CENTER_VERTICAL or Gravity.START,
                    start = context.dp(6) + context.dp(4)
                ),
            )
            cover.setImageResource(track.coverRes)
            cover.scaleType = ImageView.ScaleType.CENTER_CROP
            cover.background =
                roundedDrawable(whiteAlpha(0.08f), context.dp(10).toFloat(), context.dp(1), whiteAlpha(0.08f))
            cover.clipToOutline = true
            cover.outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
            addView(
                cover,
                frameParams(
                    context.dp(40), context.dp(40), Gravity.CENTER_VERTICAL or Gravity.START,
                    start = context.dp(6 + 22 + 8)
                ),
            )
            titleLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            titleLabel.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            titleLabel.setTextColor(Color.WHITE)
            titleLabel.includeFontPadding = false
            titleLabel.maxLines = 1
            titleLabel.ellipsize = android.text.TextUtils.TruncateAt.END
            titleLabel.text = track.title
            artistLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            artistLabel.setTextColor(whiteAlpha(0.45f))
            artistLabel.includeFontPadding = false
            artistLabel.maxLines = 1
            artistLabel.text = track.artist
            val titleRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(
                    titleLabel,
                    LinearLayout.LayoutParams(0, WRAP, 1f),
                )
                addView(
                    artistLabel,
                    LinearLayout.LayoutParams(WRAP, WRAP).apply { marginStart = context.dp(6) },
                )
            }
            val titleRowStart = context.dp(6 + 22 + 8 + 40 + 10)
            val titleRowEnd = context.dp(4 + 44 + 8)
            addView(
                titleRow,
                frameParams(
                    MATCH, WRAP, Gravity.CENTER_VERTICAL or Gravity.START,
                    start = titleRowStart, end = titleRowEnd,
                ),
            )
            durationLabel.setTextColor(whiteAlpha(0.45f))
            durationLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            durationLabel.gravity = Gravity.END
            durationLabel.text = track.duration
            addView(
                durationLabel,
                frameParams(context.dp(44), WRAP, Gravity.CENTER_VERTICAL or Gravity.END, end = context.dp(4))
            )
            playButton.background = playGradient
            playButton.elevation = context.dp(4).toFloat()
            playIcon.scaleType = ImageView.ScaleType.FIT_CENTER
            playIcon.tint(Color.WHITE)
            playButton.addView(playIcon, frameParams(context.dp(16), context.dp(16), Gravity.CENTER))
            addView(
                playButton, frameParams(
                    context.dp(PLAY_SIZE), context.dp(PLAY_SIZE), Gravity.CENTER_VERTICAL or Gravity.END,
                    end = context.dp(4 + (44 - PLAY_SIZE) / 2)
                )
            )
            playButton.setOnClickListener { toggleOrSelect(index) }
            // Mirror iOS MusicTrackRow: apply the current skin during init so the
            // play-button gradient has colors before bind() first turns it visible.
            applySkin(skin)
            bind(false, false)
        }

        fun bind(isCurrent: Boolean, isPlaying: Boolean) {
            this.isCurrent = isCurrent
            this.isPlaying = isPlaying
            indexLabel.visibility = if (isCurrent) View.GONE else View.VISIBLE
            equalizer.visibility = if (isCurrent) View.VISIBLE else View.GONE
            equalizer.setPlaying(isPlaying)
            durationLabel.visibility = if (isCurrent) View.GONE else View.VISIBLE
            playButton.visibility = if (isCurrent) View.VISIBLE else View.GONE
            playIcon.setImageResource(
                if (isPlaying) R.drawable.livekit_voicebgm_pause else R.drawable.livekit_voicebgm_play,
            )
            updateColors()
        }

        fun applySkin(skin: VoiceLoungeSkin) {
            this.skin = skin
            playGradient.colors = intArrayOf(skin.mid, skin.deep)
            equalizer.setTint(skin.mid)
            updateColors()
        }

        private fun updateColors() {
            background.setColor(if (isCurrent) skin.mid.withAlpha(0.12f) else Color.TRANSPARENT)
            background.setStroke(
                context.dp(1), if (isCurrent) skin.mid.withAlpha(0.32f) else Color.TRANSPARENT,
            )
            titleLabel.setTextColor(if (isCurrent) whiteAlpha(0.72f) else Color.WHITE)
        }
    }

    private class MusicEqualizerView(context: Context) : View(context) {
        private val bars = arrayOf(0f, 0f, 0f)
        private val restScale = floatArrayOf(6f / MAX_HEIGHT, 11f / MAX_HEIGHT, 8f / MAX_HEIGHT)
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val animators = Array(3) { index ->
            ValueAnimator.ofFloat(5f / MAX_HEIGHT, 1f).apply {
                duration = 400L
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.REVERSE
                interpolator = AccelerateDecelerateInterpolator()
                startDelay = (150L * index)
                addUpdateListener {
                    bars[index] = it.animatedValue as Float
                    invalidate()
                }
            }
        }
        private var tint: Int = VoiceLoungeSkin.defaultSkin.mid
        private var isPlaying = false

        init {
            paint.color = tint
            bars[0] = restScale[0]
            bars[1] = restScale[1]
            bars[2] = restScale[2]
        }

        fun setTint(color: Int) {
            tint = color
            paint.color = color
            invalidate()
        }

        fun setPlaying(playing: Bool) {
            if (isPlaying == playing) return
            isPlaying = playing
            if (playing && isAttachedToWindow) {
                animators.forEach { if (!it.isStarted) it.start() }
            } else {
                animators.forEach { it.cancel() }
                bars[0] = restScale[0]
                bars[1] = restScale[1]
                bars[2] = restScale[2]
                invalidate()
            }
        }

        override fun onDetachedFromWindow() {
            animators.forEach { it.cancel() }
            super.onDetachedFromWindow()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val density = resources.displayMetrics.density
            val totalWidth = 3 * BAR_WIDTH + 2 * BAR_GAP
            var x = (width - totalWidth * density) / 2f
            val maxH = MAX_HEIGHT * density
            for (i in 0 until 3) {
                val h = maxH * bars[i]
                val top = height - h
                val rect = RectF(x, top, x + BAR_WIDTH * density, height.toFloat())
                canvas.drawRoundRect(rect, 1.5f * density, 1.5f * density, paint)
                x += (BAR_WIDTH + BAR_GAP) * density
            }
        }

        companion object {
            const val MAX_HEIGHT = 15f
            const val BAR_WIDTH = 3f
            const val BAR_GAP = 2.5f
        }
    }

    companion object {
        private const val HORIZONTAL_INSET = 16
        private const val TOOLBAR_TOP = 58
        private const val LIST_TOP = 96
        private const val BOTTOM_INSET = 20
        private const val ROW_HEIGHT = 52
        private const val ROW_SPACING = 2
        private const val CONTROL_SIZE = 26
        private const val PLAY_SIZE = 36
    }
}

private typealias Bool = Boolean
