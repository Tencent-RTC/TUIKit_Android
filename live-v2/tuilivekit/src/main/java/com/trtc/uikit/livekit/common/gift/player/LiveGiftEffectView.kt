package com.trtc.uikit.livekit.common.gift.player

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.widget.FrameLayout
import androidx.constraintlayout.widget.ConstraintLayout
import com.trtc.uikit.livekit.common.gift.player.AnimationView
import com.trtc.uikit.livekit.common.gift.player.SVGAAnimationView
import com.trtc.uikit.livekit.common.gift.player.TCEffectAnimationView
import java.io.File
import java.util.UUID
import java.util.concurrent.Future

internal class LiveGiftEffectView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : ConstraintLayout(context, attrs, defStyleAttr) {

    private enum class ResourceKind {
        SVGA,
        MP4;

        companion object {
            fun from(resourceURL: String): ResourceKind? {
                val path = runCatching { java.net.URL(resourceURL).path }.getOrNull() ?: return null
                return when (path.substringAfterLast('.', "").lowercase()) {
                    "svga" -> SVGA
                    "mp4" -> MP4
                    else -> null
                }
            }
        }
    }

    private data class PendingEffect(
        val id: String = UUID.randomUUID().toString(),
        val resourceURL: String,
        val kind: ResourceKind,
    )

    private val loader = LiveGiftEffectLoader()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val animationCanvasView = FrameLayout(context)
    private val svgaPlayerView = SVGAAnimationView(context)
    private val effectPlayerView = TCEffectAnimationView(context)
    private val queue = mutableListOf<PendingEffect>()
    private val preloadInFlightURLs = mutableSetOf<String>()
    private val preloadWaiters = mutableMapOf<String, MutableList<(File?) -> Unit>>()
    private val requestedPreloadURLs = mutableSetOf<String>()
    private var currentEffect: PendingEffect? = null
    private var downloadTask: Future<*>? = null
    private var timeoutRunnable: Runnable? = null
    private var generation = UUID.randomUUID().toString()

    init {
        visibility = GONE
        isClickable = false
        isFocusable = false

        animationCanvasView.isClickable = false
        addView(animationCanvasView, GiftEffectLayoutParams.canvas())

        val playerCallback = object : AnimationView.Callback {
            override fun onFinished(error: Int) {
                finishCurrentEffect()
            }
        }
        svgaPlayerView.isClickable = false
        svgaPlayerView.callback = playerCallback
        animationCanvasView.addView(svgaPlayerView, GiftEffectLayoutParams.player())

        effectPlayerView.isClickable = false
        effectPlayerView.callback = playerCallback
        animationCanvasView.addView(effectPlayerView, GiftEffectLayoutParams.player())
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        reset()
        loader.release()
    }

    fun enqueue(resourceURL: String) {
        val kind = ResourceKind.from(resourceURL) ?: return
        queue.add(PendingEffect(resourceURL = resourceURL, kind = kind))
        playNextIfNeeded()
    }

    fun preload(resourceURLs: List<String>) {
        for (resourceURL in resourceURLs.toSet()) {
            if (ResourceKind.from(resourceURL) == null) continue
            if (!requestedPreloadURLs.add(resourceURL)) continue

            preloadInFlightURLs.add(resourceURL)
            loader.load(resourceURL) { file ->
                preloadInFlightURLs.remove(resourceURL)
                preloadWaiters.remove(resourceURL)?.forEach { it(file) }
            }
        }
    }

    fun reset() {
        generation = UUID.randomUUID().toString()
        queue.clear()
        currentEffect = null
        cleanupPlayback()
        visibility = GONE
    }

    fun updatePreviewBottomInset(inset: Int) {
        val params = animationCanvasView.layoutParams as LayoutParams
        val bottomMargin = inset.coerceAtLeast(0)
        if (params.bottomMargin == bottomMargin) return
        params.bottomMargin = bottomMargin
        animationCanvasView.layoutParams = params
    }

    private fun playNextIfNeeded() {
        if (currentEffect != null || queue.isEmpty()) return
        val effect = queue.removeAt(0)
        currentEffect = effect
        val currentGeneration = generation
        visibility = VISIBLE

        val completion: (File?) -> Unit = { file ->
            if (generation == currentGeneration && currentEffect?.id == effect.id) {
                downloadTask = null
                if (file != null) {
                    play(effect, file, currentGeneration)
                } else {
                    finishCurrentEffect()
                }
            }
        }
        if (preloadInFlightURLs.contains(effect.resourceURL)) {
            preloadWaiters.getOrPut(effect.resourceURL) { mutableListOf() }.add(completion)
        } else {
            downloadTask = loader.load(effect.resourceURL, completion)
        }
    }

    private fun play(effect: PendingEffect, localFile: File, generation: String) {
        scheduleTimeout(effect.id, generation)
        when (effect.kind) {
            ResourceKind.SVGA -> {
                effectPlayerView.visibility = GONE
                svgaPlayerView.visibility = VISIBLE
                svgaPlayerView.playAnimation(localFile.absolutePath)
            }

            ResourceKind.MP4 -> {
                svgaPlayerView.visibility = GONE
                effectPlayerView.visibility = VISIBLE
                effectPlayerView.playAnimation(localFile.absolutePath)
            }
        }
    }

    private fun scheduleTimeout(effectID: String, generation: String) {
        timeoutRunnable?.let { mainHandler.removeCallbacks(it) }
        val runnable = Runnable {
            if (this.generation == generation && currentEffect?.id == effectID) {
                finishCurrentEffect()
            }
        }
        timeoutRunnable = runnable
        mainHandler.postDelayed(runnable, PLAY_TIMEOUT_MS)
    }

    private fun finishCurrentEffect() {
        if (currentEffect == null) return
        cleanupPlayback()
        currentEffect = null
        visibility = GONE
        playNextIfNeeded()
    }

    private fun cleanupPlayback() {
        downloadTask?.cancel(true)
        downloadTask = null
        timeoutRunnable?.let { mainHandler.removeCallbacks(it) }
        timeoutRunnable = null
        svgaPlayerView.stopPlay()
        effectPlayerView.stopPlay()
    }

    private object GiftEffectLayoutParams {
        fun canvas(): LayoutParams = LayoutParams(0, 0).apply {
            startToStart = LayoutParams.PARENT_ID
            endToEnd = LayoutParams.PARENT_ID
            bottomToBottom = LayoutParams.PARENT_ID
            topToTop = LayoutParams.PARENT_ID
        }

        fun player(): FrameLayout.LayoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT,
        )
    }

    private companion object {
        const val PLAY_TIMEOUT_MS = 30 * 1000L
    }
}
