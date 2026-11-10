package com.trtc.uikit.livekit.common.gift.display

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout
import com.trtc.uikit.livekit.common.log.LiveKitLog
import io.trtc.uikit.giftplayer.GiftPlaybackEvent
import io.trtc.uikit.giftplayer.GiftPlayer
import io.trtc.uikit.giftplayer.GiftPreparationTask
import io.trtc.uikit.giftplayer.GiftPreparedResource

internal class GiftAnimationView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : ConstraintLayout(context, attrs, defStyleAttr) {
    private companion object {
        const val PLAY_TIMEOUT_MS = 30_000L
        val LOGGER = LiveKitLog.getComponentLogger("GiftAnimationView")
    }

    private class PendingEffect(val resourceURL: String) {
        var task: GiftPreparationTask? = null
        var result: Result<GiftPreparedResource>? = null
        var disposed = false
            private set

        fun close() {
            if (disposed) return
            disposed = true
            task?.cancel()
            task = null
            result?.getOrNull()?.close()
            result = null
        }
    }

    private val giftPlayer = GiftPlayer(context)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val queue = mutableListOf<PendingEffect>()
    private var currentEffect: PendingEffect? = null
    private var hasStartedPlayback = false
    private var timeoutRunnable: Runnable? = null

    init {
        visibility = GONE
        isClickable = false
        isFocusable = false
        addView(giftPlayer.view, GiftEffectLayoutParams.canvas())
    }

    override fun onDetachedFromWindow() {
        reset()
        super.onDetachedFromWindow()
    }

    fun enqueue(resourceURL: String) {
        queue.add(PendingEffect(resourceURL))
        playNextIfNeeded()
        prepareNextIfNeeded()
    }

    fun reset() {
        val pending = queue.toList()
        queue.clear()
        clearCurrentEffect()
        pending.forEach(PendingEffect::close)
    }

    fun updatePreviewBottomInset(inset: Int) {
        val params = giftPlayer.view.layoutParams as LayoutParams
        val bottomMargin = inset.coerceAtLeast(0)
        if (params.bottomMargin == bottomMargin) return
        params.bottomMargin = bottomMargin
        giftPlayer.view.layoutParams = params
    }

    private object GiftEffectLayoutParams {
        fun canvas(): LayoutParams = LayoutParams(0, 0).apply {
            startToStart = LayoutParams.PARENT_ID
            endToEnd = LayoutParams.PARENT_ID
            bottomToBottom = LayoutParams.PARENT_ID
            topToTop = LayoutParams.PARENT_ID
        }
    }

    private fun playNextIfNeeded() {
        if (currentEffect != null || queue.isEmpty()) return
        val effect = queue.removeAt(0)
        currentEffect = effect
        visibility = View.VISIBLE
        scheduleTimeout(effect)
        if (effect.result != null) consumePreparation(effect) else prepare(effect)
    }

    private fun prepareNextIfNeeded() {
        if (hasStartedPlayback) queue.firstOrNull()?.let(::prepare)
    }

    private fun prepare(effect: PendingEffect) {
        if (effect.task != null || effect.result != null || effect.disposed) return
        effect.task = giftPlayer.prepare(effect.resourceURL) completion@ { result ->
            effect.task = null
            if (effect.disposed) {
                result.getOrNull()?.close()
                return@completion
            }
            effect.result = result
            consumePreparation(effect)
        }
    }

    private fun consumePreparation(effect: PendingEffect) {
        if (currentEffect !== effect) return
        effect.result?.fold(onSuccess = { resource ->
            giftPlayer.play(resource) { event ->
                if (currentEffect !== effect) return@play
                when (event) {
                    GiftPlaybackEvent.Started -> {
                        hasStartedPlayback = true
                        scheduleTimeout(effect)
                        prepareNextIfNeeded()
                    }
                    GiftPlaybackEvent.Finished -> finishCurrentEffect()
                    is GiftPlaybackEvent.Failed -> {
                        LOGGER.warn("Gift playback failed: ${event.error.message}")
                        finishCurrentEffect()
                    }
                }
            }
        }, onFailure = {
            LOGGER.warn("Gift preparation failed: ${it.message}")
            finishCurrentEffect()
        })
    }

    private fun scheduleTimeout(effect: PendingEffect) {
        timeoutRunnable?.let(mainHandler::removeCallbacks)
        timeoutRunnable = Runnable {
            if (currentEffect === effect) finishCurrentEffect()
        }.also { mainHandler.postDelayed(it, PLAY_TIMEOUT_MS) }
    }

    private fun finishCurrentEffect() {
        clearCurrentEffect()
        playNextIfNeeded()
    }

    private fun clearCurrentEffect() {
        val previous = currentEffect
        currentEffect = null
        previous?.close()
        timeoutRunnable?.let(mainHandler::removeCallbacks)
        timeoutRunnable = null
        hasStartedPlayback = false
        giftPlayer.stop()
        visibility = GONE
    }
}
