package com.trtc.uikit.livekit.pusher.living.music

import androidx.core.content.ContextCompat
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import android.view.animation.PathInterpolator
import android.widget.TextView
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.trtc.uikit.livekit.R
import com.trtc.uikit.livekit.common.theme.BottomSheetView
import io.trtc.tuikit.atomicxcore.api.device.MusicPlayStatus
import io.trtc.tuikit.atomicxcore.api.device.MusicStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

private data class Track(
    val id: Int,
    val name: String,
    val url: String,
    val duration: String,
)

private enum class PlayStatus {
    IDLE,
    LOADING,
    PLAYING,
    PAUSED,
}

private data class State(
    val tracks: List<Track>,
    val currentURL: String? = null,
    val playStatus: PlayStatus = PlayStatus.IDLE,
) {
    fun isPlaying(track: Track): Boolean =
        currentURL == track.url && (playStatus == PlayStatus.PLAYING || playStatus == PlayStatus.LOADING)

    fun isCurrent(track: Track): Boolean = currentURL == track.url && playStatus != PlayStatus.IDLE
}

@Suppress("UNUSED_PARAMETER")
internal class PusherMusicView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : BottomSheetView(context) {
    private val tracks = listOf(
        Track(
            id = 1,
            name = context.getString(R.string.livekit_pusher_music_happy),
            url = "https://dldir1.qq.com/hudongzhibo/TUIKit/resource/music/PositiveHappyAdvertising.mp3",
            duration = "05:45",
        ),
        Track(
            id = 2,
            name = context.getString(R.string.livekit_pusher_music_sad),
            url = "https://dldir1.qq.com/hudongzhibo/TUIKit/resource/music/SadCinematicPiano.mp3",
            duration = "06:06",
        ),
        Track(
            id = 3,
            name = context.getString(R.string.livekit_pusher_music_wonder_world),
            url = "https://dldir1.qq.com/hudongzhibo/TUIKit/resource/music/WonderWorld.mp3",
            duration = "02:05",
        ),
    )
    private val adapter = MusicAdapter(::toggle)
    private var state = State(tracks)
    private var boundLiveID = ""
    private var musicStore: MusicStore? = null
    private var stateScope: CoroutineScope? = null
    private var stateJob: Job? = null
    private var isActive = false
    private var isSuspended = false
    private var resumeURL: String? = null

    override val sheetHeight: Int
        get() = dp(370)

    init {
        sheetTitle = context.getString(R.string.livekit_settings_background_music)
        LayoutInflater.from(context).inflate(R.layout.livekit_pusher_music_view, contentView, true)
        contentView.findViewById<RecyclerView>(R.id.livekit_pusher_music_list).apply {
            layoutManager = LinearLayoutManager(context)
            adapter = this@PusherMusicView.adapter
        }
        adapter.bind(state)
    }

    fun bind(liveID: String) {
        if (boundLiveID == liveID) return
        val reactivate = isActive
        deactivate()
        boundLiveID = liveID
        musicStore = liveID.takeIf(String::isNotEmpty)?.let(MusicStore::create)
        state = State(tracks)
        adapter.bind(state)
        if (reactivate) activate()
    }

    fun activate() {
        if (isActive) return
        val store = musicStore ?: return
        isActive = true
        updateState(store.musicState.playURL.value, store.musicState.playStatus.value)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        stateScope = scope
        stateJob = scope.launch {
            combine(
                store.musicState.playURL,
                store.musicState.playStatus,
            ) { url, status -> url to status }.collectLatest { (url, status) ->
                updateState(url, status)
            }
        }
    }

    fun deactivate() {
        isSuspended = false
        resumeURL = null
        if (!isActive) return
        isActive = false
        stateJob?.cancel()
        stateJob = null
        stateScope?.cancel()
        stateScope = null
        musicStore?.stopPlay()
        state = state.copy(currentURL = null, playStatus = PlayStatus.IDLE)
        adapter.bind(state)
    }

    internal fun setSuspended(suspended: Boolean) {
        if (isSuspended == suspended) return
        isSuspended = suspended
        val store = musicStore ?: return
        if (!isActive) return
        val status = store.musicState.playStatus.value
        val url = store.musicState.playURL.value
        if (suspended) {
            resumeURL = url?.takeIf {
                status == MusicPlayStatus.PLAYING || status == MusicPlayStatus.LOADING
            }
            if (resumeURL != null) store.pausePlay()
        } else {
            val shouldResume = resumeURL != null && resumeURL == url && status == MusicPlayStatus.PAUSED
            resumeURL = null
            if (shouldResume) store.resumePlay()
        }
    }

    override fun show() {
        adapter.bind(state)
        super.show()
    }

    fun dismissIfShowing(): Boolean {
        if (!isShowing) return false
        dismiss()
        return true
    }

    private fun toggle(track: Track) {
        if (!isActive || isSuspended) return
        val store = musicStore ?: return
        if (state.currentURL == track.url) {
            when (state.playStatus) {
                PlayStatus.PLAYING, PlayStatus.LOADING -> {
                    store.pausePlay()
                    return
                }
                PlayStatus.PAUSED -> {
                    store.resumePlay()
                    return
                }
                PlayStatus.IDLE -> Unit
            }
        }
        if (state.currentURL != null && state.currentURL != track.url) store.stopPlay()
        store.setPitch(0.0)
        store.startPlay(track.url, null)
    }

    private fun updateState(currentURL: String?, status: MusicPlayStatus) {
        val playStatus = when (status) {
            MusicPlayStatus.IDLE -> PlayStatus.IDLE
            MusicPlayStatus.LOADING -> PlayStatus.LOADING
            MusicPlayStatus.PLAYING -> PlayStatus.PLAYING
            MusicPlayStatus.PAUSED -> PlayStatus.PAUSED
        }
        if (state.currentURL == currentURL && state.playStatus == playStatus) return
        state = state.copy(currentURL = currentURL, playStatus = playStatus)
        adapter.bind(state)
        // Loading can finish after the live was paused; keep that late playback silent.
        if (isActive && isSuspended && playStatus == PlayStatus.PLAYING) {
            musicStore?.pausePlay()
        }
    }

    private fun dp(value: Int) = (value * context.resources.displayMetrics.density).toInt()
}

private class MusicAdapter(
    private val onClick: (Track) -> Unit,
) : RecyclerView.Adapter<MusicAdapter.Holder>() {
    private var state = State(emptyList())

    fun bind(state: State) {
        val previousCount = this.state.tracks.size
        this.state = state
        when {
            previousCount == state.tracks.size && previousCount > 0 -> {
                notifyItemRangeChanged(0, previousCount)
            }
            previousCount > 0 -> notifyItemRangeRemoved(0, previousCount)
        }
        if (previousCount != state.tracks.size && state.tracks.isNotEmpty()) {
            notifyItemRangeInserted(0, state.tracks.size)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder = Holder(
        LayoutInflater.from(parent.context).inflate(R.layout.livekit_pusher_music_item, parent, false),
    )

    override fun getItemCount() = state.tracks.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val track = state.tracks[position]
        val isCurrent = state.isCurrent(track)
        val isPlaying = state.isPlaying(track)
        holder.name.text = track.name
        holder.duration.text = track.duration
        holder.wave.bind(isCurrent, isPlaying)
        holder.control.bind(isCurrent, isPlaying)
        holder.itemView.setOnClickListener { onClick(track) }
    }

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.livekit_music_name)
        val duration: TextView = view.findViewById(R.id.livekit_music_duration)
        val wave: MusicWaveView = view.findViewById(R.id.livekit_music_wave)
        val control: MusicPlaybackControlView = view.findViewById(R.id.livekit_music_control)
    }
}

internal class MusicPlaybackControlView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : AppCompatImageView(context, attrs, defStyleAttr) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.livekit_pusher_music_cover_foreground)
    }
    private val playPath = Path()
    private var playing = false

    init {
        val density = resources.displayMetrics.density
        playPath.apply {
            moveTo(10.99f * density, 8.62f * density)
            cubicTo(
                10.99f * density, 7.81f * density, 11.88f * density,
                7.31f * density, 12.56f * density, 7.75f * density,
            )
            lineTo(20.64f * density, 12.83f * density)
            cubicTo(
                21.52f * density, 13.38f * density, 21.52f * density,
                14.02f * density, 20.64f * density, 14.57f * density,
            )
            lineTo(12.56f * density, 19.65f * density)
            cubicTo(
                11.88f * density, 20.09f * density, 10.99f * density,
                19.59f * density, 10.99f * density, 18.78f * density,
            )
            close()
        }
    }

    fun bind(current: Boolean, playing: Boolean) {
        isVisible = current
        this.playing = playing
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val density = resources.displayMetrics.density
        if (playing) {
            canvas.drawRoundRect(
                10.04f * density, 8.46f * density, 12.89f * density, 19.54f * density,
                1.19f * density, 1.19f * density, paint,
            )
            canvas.drawRoundRect(
                15.11f * density, 8.46f * density, 17.96f * density, 19.54f * density,
                1.19f * density, 1.19f * density, paint,
            )
            return
        }
        canvas.drawPath(playPath, paint)
    }
}

internal class MusicWaveView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : AppCompatImageView(context, attrs, defStyleAttr) {
    private data class AnimationParameters(
        val duration: Float,
        val delay: Float,
        val low: Float,
        val high: Float,
    )

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ContextCompat.getColor(context, R.color.livekit_brand_cyan) }
    private val interpolator = PathInterpolator(0.37f, 0f, 0.63f, 1f)
    private val parameters = listOf(
        AnimationParameters(duration = 620f, delay = -180f, low = 3f, high = -4f),
        AnimationParameters(duration = 790f, delay = -510f, low = 2f, high = -5f),
        AnimationParameters(duration = 530f, delay = -340f, low = 4f, high = -3f),
        AnimationParameters(duration = 710f, delay = -620f, low = 2f, high = -4f),
    )
    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 1000L
        interpolator = LinearInterpolator()
        repeatMode = ValueAnimator.RESTART
        repeatCount = ValueAnimator.INFINITE
        addUpdateListener { invalidate() }
    }
    private var playing = false
    private var startTime = 0L

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
        paint.setShadowLayer(
            5f * resources.displayMetrics.density, 0f, 0f, ContextCompat.getColor(context, R.color.livekit_pusher_music_wave_shadow),
        )
    }

    fun bind(current: Boolean, playing: Boolean) {
        isVisible = current
        this.playing = playing
        if (playing && !animator.isStarted) {
            startTime = System.nanoTime()
            animator.start()
        }
        if (!playing && animator.isStarted) animator.cancel()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val density = resources.displayMetrics.density
        val elapsed = if (playing) (System.nanoTime() - startTime) / 1_000_000f else 0f
        parameters.forEachIndexed { index, parameters ->
            val fraction = if (playing) autoreversedFraction(elapsed, parameters) else 0f
            val eased = interpolator.getInterpolation(fraction)
            val translationY = parameters.low + (parameters.high - parameters.low) * eased
            val scale = 0.82f + (1.08f - 0.82f) * eased
            val alpha = if (playing) 0.64f + (1f - 0.64f) * eased else 0.9f
            val half = 2f * density * scale
            val centerX = width / 2f + (index - 1.5f) * 7f * density
            val centerY = height / 2f + translationY * density
            paint.alpha = (alpha * 255f).toInt().coerceIn(0, 255)
            canvas.drawRoundRect(
                centerX - half, centerY - half, centerX + half, centerY + half,
                2f * density * scale, 2f * density * scale, paint,
            )
        }
    }

    private fun autoreversedFraction(elapsed: Float, parameters: AnimationParameters): Float {
        val local = (elapsed - parameters.delay) % (parameters.duration * 2f)
        val progress = local / parameters.duration
        return if (progress <= 1f) progress else 2f - progress
    }

    override fun onDetachedFromWindow() {
        animator.cancel()
        super.onDetachedFromWindow()
    }
}
