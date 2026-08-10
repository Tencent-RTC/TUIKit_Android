package io.trtc.tuikit.chat.uikit.components.messagelist.ui.messagerenderers
import android.content.Context
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import io.trtc.tuikit.chat.uikit.R
import io.trtc.tuikit.chat.uikit.components.config.MessageAlignment
import io.trtc.tuikit.chat.uikit.components.messagelist.config.MessageListConfigProtocol
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.MessageRenderer
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.RecyclableMessageRenderer
import io.trtc.tuikit.chat.uikit.components.messagelist.viewmodel.MessageListViewModel
import io.trtc.tuikit.atomicx.theme.tokens.ColorTokens
import io.trtc.tuikit.atomicxcore.api.message.AudioMessagePayload
import io.trtc.tuikit.atomicxcore.api.message.MessageInfo
import io.trtc.tuikit.atomicxcore.api.message.MessageStatus

class SoundMessageRenderer : MessageRenderer, RecyclableMessageRenderer {
    companion object {
        private const val TAG_ANIM_ICON = "sound_anim"
        private const val TAG_DURATION = "sound_duration"
        private val TAG_PLAY_RUNNABLE = R.id.message_list_sound_play_runnable_tag
        private const val FRAME_INTERVAL_MS = 300L
        private const val DIMMED_ALPHA_INT = 76
        private const val FULL_ALPHA_INT = 255

        private const val MAX_DURATION_SECONDS = 60

        private const val ABSOLUTE_MIN_WIDTH_DP = 80
        private const val ABSOLUTE_MAX_WIDTH_DP = 260

        private const val MIN_WIDTH_SCREEN_RATIO = 0.22f
        private const val MAX_WIDTH_SCREEN_RATIO = 0.55f

        private val LAYER_IDS = intArrayOf(
            R.id.message_list_sound_voice_layer_dot,
            R.id.message_list_sound_voice_layer_arc_inner,
            R.id.message_list_sound_voice_layer_arc_outer,
        )

        private fun shouldAlignContentEnd(
            alignment: MessageAlignment,
            isSelf: Boolean,
            isRtl: Boolean,
        ): Boolean {
            return when (alignment) {
                MessageAlignment.LEFT -> false
                MessageAlignment.RIGHT -> true
                MessageAlignment.TWO_SIDED -> if (isRtl) !isSelf else isSelf
                else -> if (isRtl) !isSelf else isSelf
            }
        }

        private fun isLocaleRtl(context: Context): Boolean {
            return context.resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL
        }
    }

    override fun createView(context: Context, parent: ViewGroup): View {
        val density = context.resources.displayMetrics.density
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_LTR
            gravity = Gravity.CENTER_VERTICAL
            val horizontalPadding = (12 * density).toInt()
            val verticalPadding = (8 * density).toInt()
            setPadding(horizontalPadding, verticalPadding, horizontalPadding, verticalPadding)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        }

        val animIcon = ImageView(context).apply {
            setImageDrawable(createVoiceLayerDrawable(context))
            tag = TAG_ANIM_ICON
        }

        val durationView = TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            maxLines = 1
            isSingleLine = true
            ellipsize = android.text.TextUtils.TruncateAt.END
            tag = TAG_DURATION
        }
        applyContentOrder(container, animIcon, durationView, isRtl = false)

        return container
    }

    override fun bindView(
        view: View,
        message: MessageInfo,
        viewModel: MessageListViewModel,
        config: MessageListConfigProtocol,
        colors: ColorTokens,
    ) {
        val container = view as LinearLayout
        val animIcon = view.findViewWithTag<ImageView>(TAG_ANIM_ICON)
        val durationView = view.findViewWithTag<TextView>(TAG_DURATION)
        val payload = message.messagePayload as? AudioMessagePayload

        val duration = payload?.audioDuration ?: 0
        durationView.text = formatVoiceDuration(duration)
        val contentColor = if (message.isSentBySelf) {
            colors.textColorAntiPrimary
        } else {
            colors.textColorPrimary
        }
        durationView.setTextColor(contentColor)

        val audioState = viewModel.audioPlayingState.value
        val isCurrentMessage = audioState.playingMessageId == message.msgID
        val isPlaying = isCurrentMessage && audioState.isPlaying

        val isRtl = isLocaleRtl(view.context)
        val shouldAlignEnd = shouldAlignContentEnd(
            alignment = config.alignment,
            isSelf = message.isSentBySelf,
            isRtl = isRtl,
        )
        container.gravity = if (shouldAlignEnd) {
            Gravity.END or Gravity.CENTER_VERTICAL
        } else {
            Gravity.START or Gravity.CENTER_VERTICAL
        }
        applyContentOrder(container, animIcon, durationView, isRtl)
        animIcon.scaleX = if (isRtl) -1f else 1f

        tintVoiceLayers(animIcon.drawable, contentColor)

        durationView.alpha = if (isCurrentMessage) 1f else 0.85f
        container.alpha = if (isCurrentMessage || !audioState.isPlaying) 1f else 0.85f

        stopPlayingAnimation(animIcon)
        if (isPlaying) {
            startPlayingAnimation(animIcon)
        } else {
            resetVoiceLayersAlpha(animIcon.drawable)
        }

        val density = view.context.resources.displayMetrics.density
        val screenWidth = view.context.resources.displayMetrics.widthPixels
        val absoluteMinWidth = (ABSOLUTE_MIN_WIDTH_DP * density).toInt()
        val absoluteMaxWidth = (ABSOLUTE_MAX_WIDTH_DP * density).toInt()
        val minWidth = (screenWidth * MIN_WIDTH_SCREEN_RATIO).toInt()
            .coerceIn(absoluteMinWidth, absoluteMaxWidth)
        val maxWidth = (screenWidth * MAX_WIDTH_SCREEN_RATIO).toInt()
            .coerceIn(minWidth, absoluteMaxWidth)
        val clampedDuration = duration.coerceIn(1, MAX_DURATION_SECONDS)
        val width = minWidth + ((maxWidth - minWidth).toFloat() *
            (clampedDuration.toFloat() / MAX_DURATION_SECONDS)).toInt()
        val layoutParams = container.layoutParams
        layoutParams.width = width
        container.layoutParams = layoutParams

        if (message.status == MessageStatus.VIOLATION) {
            view.setOnClickListener(null)
            view.isClickable = false
        } else {
            view.isClickable = true
            view.setOnClickListener {
                viewModel.playAudioMessage(message)
            }
        }
    }

    override fun onViewRecycled(view: View) {
        val animIcon = view.findViewWithTag<ImageView>(TAG_ANIM_ICON) ?: return
        stopPlayingAnimation(animIcon)
        resetVoiceLayersAlpha(animIcon.drawable)
    }

    private fun applyContentOrder(
        container: LinearLayout,
        animIcon: ImageView,
        durationView: TextView,
        isRtl: Boolean,
    ) {
        val density = container.resources.displayMetrics.density
        val spacing = (4 * density).toInt()
        val iconSize = (16 * density).toInt()
        val iconLp = LinearLayout.LayoutParams(iconSize, iconSize)
        val durationLp = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )
        container.removeAllViews()
        if (isRtl) {
            container.addView(durationView, durationLp)
            iconLp.marginStart = spacing
            container.addView(animIcon, iconLp)
        } else {
            container.addView(animIcon, iconLp)
            durationLp.marginStart = spacing
            container.addView(durationView, durationLp)
        }
    }

    private fun createVoiceLayerDrawable(context: Context): LayerDrawable {
        val layers = arrayOf<Drawable>(
            ContextCompat.getDrawable(context, R.drawable.message_list_voice_icon_dot)!!.mutate(),
            ContextCompat.getDrawable(context, R.drawable.message_list_voice_icon_arc_inner)!!.mutate(),
            ContextCompat.getDrawable(context, R.drawable.message_list_voice_icon_arc_outer)!!.mutate(),
        )
        return LayerDrawable(layers).apply {
            setId(0, LAYER_IDS[0])
            setId(1, LAYER_IDS[1])
            setId(2, LAYER_IDS[2])
        }
    }

    private fun tintVoiceLayers(drawable: Drawable?, color: Int) {
        val layer = drawable as? LayerDrawable ?: return
        for (id in LAYER_IDS) {
            layer.findDrawableByLayerId(id)?.setTint(color)
        }
    }

    private fun resetVoiceLayersAlpha(drawable: Drawable?) {
        val layer = drawable as? LayerDrawable ?: return
        for (id in LAYER_IDS) {
            layer.findDrawableByLayerId(id)?.alpha = FULL_ALPHA_INT
        }
    }

    private fun startPlayingAnimation(animIcon: ImageView) {
        val layer = animIcon.drawable as? LayerDrawable ?: return
        val handler = Handler(Looper.getMainLooper())
        val runnable = object : Runnable {
            private var step = 0
            override fun run() {
                for (i in LAYER_IDS.indices) {
                    layer.findDrawableByLayerId(LAYER_IDS[i])?.alpha =
                        if (i == step) FULL_ALPHA_INT else DIMMED_ALPHA_INT
                }
                step = (step + 1) % LAYER_IDS.size
                handler.postDelayed(this, FRAME_INTERVAL_MS)
            }
        }
        animIcon.setTag(TAG_PLAY_RUNNABLE, PlayAnimHolder(handler, runnable))
        handler.post(runnable)
    }

    private fun stopPlayingAnimation(animIcon: ImageView) {
        val holder = animIcon.getTag(TAG_PLAY_RUNNABLE) as? PlayAnimHolder ?: return
        holder.handler.removeCallbacks(holder.runnable)
        animIcon.setTag(TAG_PLAY_RUNNABLE, null)
    }

    private data class PlayAnimHolder(val handler: Handler, val runnable: Runnable)

    private fun formatVoiceDuration(totalSeconds: Int?): String {
        val safeSeconds = (totalSeconds ?: 0).coerceAtLeast(0)
        return "${safeSeconds}\""
    }
}
