package com.trtc.uikit.livekit.pusher.living

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.view.isVisible
import com.trtc.uikit.livekit.R
import com.trtc.uikit.livekit.common.theme.BottomSheetView
import com.trtc.uikit.livekit.pusher.settings.AudioEffectView
import com.trtc.uikit.livekit.pusher.settings.PusherSettingsActionHandling
import com.trtc.uikit.livekit.pusher.settings.PusherSettingsItemView
import com.trtc.uikit.livekit.pusher.settings.PusherSettingsState
import java.lang.ref.WeakReference

/** Living-stage settings drawer, matching iOS PusherLivingSettingsView. */
class PusherLivingSettingsView(context: Context) : BottomSheetView(context) {
    internal var settingsActionHandler: PusherSettingsActionHandling?
        get() = settingsActionHandlerReference?.get()
        set(value) {
            settingsActionHandlerReference = value?.let(::WeakReference)
        }
    internal var onShowBeauty: (() -> Unit)? = null
    var onPause: (() -> Unit)? = null
    var onToggleMute: (() -> Unit)? = null
    var onShowComment: (() -> Unit)? = null
    var onShowMusic: (() -> Unit)? = null
    internal var onFlipCamera: (() -> Unit)? = null

    private enum class Page(val heightDp: Int, @StringRes val title: Int?) {
        MAIN(385, null),
        AUDIO(466, R.string.livekit_pusher_audio_effects),
    }

    private val main: View
    private val audio: AudioEffectView
    private val micItem: PusherSettingsItemView
    private val mirrorItem: PusherSettingsItemView
    private var page = Page.MAIN
    private var renderedSettings: PusherSettingsState? = null
    private var settingsActionHandlerReference: WeakReference<PusherSettingsActionHandling>? = null
    private var pendingDismissAction: (() -> Unit)? = null

    override val sheetHeight: Int
        get() = dp(page.heightDp)

    init {
        LayoutInflater.from(context).inflate(R.layout.livekit_pusher_living_settings_view, contentView, true)
        main = contentView.findViewById(R.id.livekit_pusher_living_settings_main)
        audio = contentView.findViewById(R.id.livekit_pusher_living_settings_audio)
        micItem = contentView.findViewById(R.id.cap_mic)
        mirrorItem = contentView.findViewById(R.id.cap_mirror)

        bindItem(R.id.cap_pause, R.string.livekit_settings_pause_live, R.drawable.livekit_pusher_living_setting_pause, 17f, 17f) {
            animateDismiss { onPause?.invoke() }
        }
        bindItem(R.id.cap_mic, R.string.common_mute_audio, R.drawable.livekit_pusher_living_setting_mic_on, 44f, 44f, false) {
            onToggleMute?.invoke()
        }
        bindItem(R.id.cap_beauty, R.string.common_video_settings_item_beauty, R.drawable.livekit_pusher_living_setting_beauty, 18.05f, 16.3f) {
            animateDismiss { onShowBeauty?.invoke() }
        }
        bindItem(R.id.cap_mirror, R.string.common_video_settings_item_mirror, R.drawable.livekit_pusher_living_setting_mirror, 21f, 22f) {
            val settings = renderedSettings ?: PusherSettingsState()
            bind(settings.copy(isMirrorEnabled = !settings.isMirrorEnabled))
            settingsActionHandler?.toggleMirror()
        }
        bindItem(R.id.cap_flip, R.string.common_video_settings_item_flip, R.drawable.livekit_pusher_living_setting_flip, 21f, 18.5f) {
            onFlipCamera?.invoke()
        }
        bindItem(R.id.cap_sound_effect, R.string.livekit_pusher_audio_effects, R.drawable.livekit_pusher_living_setting_audio, 18.1f, 18.6f) {
            animateTo(Page.AUDIO)
        }
        bindItem(R.id.cap_background_music, R.string.livekit_settings_background_music, R.drawable.livekit_pusher_living_setting_music, 14.93f, 17.63f) {
            animateDismiss { onShowMusic?.invoke() }
        }
        bindItem(R.id.cap_comments, R.string.livekit_settings_comments, R.drawable.livekit_pusher_living_setting_comment, 19f, 16f) {
            animateDismiss { onShowComment?.invoke() }
        }

        audio.onSetCaptureVolume = { settingsActionHandler?.setCaptureVolume(it) }
        applyPage(Page.MAIN)
        bind(PusherSettingsState())
    }

    override fun show() {
        applyPage(Page.MAIN)
        super.show()
    }

    internal fun cancelPendingAction() {
        pendingDismissAction = null
    }

    fun dismissIfShowing(): Boolean {
        if (!isShowing) return false
        dismiss()
        return true
    }

    override fun sheetWillPresent() {
        audio.setActive(page == Page.AUDIO)
    }

    override fun sheetWillDismiss() {
        audio.setActive(false)
    }

    override fun sheetDidDismiss() {
        applyPage(Page.MAIN)
        val action = pendingDismissAction
        pendingDismissAction = null
        action?.invoke()
    }

    internal fun bind(settings: PusherSettingsState) {
        val previous = renderedSettings
        if (previous == settings) return
        renderedSettings = settings
        if (previous?.isMuted != settings.isMuted) {
            micItem.setTitle(
                if (settings.isMuted) R.string.common_unmute_audio else R.string.common_mute_audio,
            )
            micItem.setIcon(
                if (settings.isMuted) R.drawable.livekit_pusher_living_setting_mic_off
                else R.drawable.livekit_pusher_living_setting_mic_on,
            )
        }
        if (previous?.isMirrorEnabled != settings.isMirrorEnabled) {
            renderMirror(settings.isMirrorEnabled)
        }
        if (previous?.captureVolume != settings.captureVolume) {
            audio.bindCaptureVolume(settings.captureVolume)
        }
    }

    internal fun bindMuted(isMuted: Boolean) {
        bind((renderedSettings ?: PusherSettingsState()).copy(isMuted = isMuted))
    }

    private fun renderMirror(isEnabled: Boolean) {
        mirrorItem.setIcon(
            if (isEnabled) {
                R.drawable.livekit_pusher_settings_ic_mirror_on
            } else {
                R.drawable.livekit_pusher_settings_ic_mirror_off
            },
        )
    }

    private fun bindItem(
        id: Int,
        @StringRes text: Int,
        @DrawableRes icon: Int,
        iconWidth: Float,
        iconHeight: Float,
        showsIconBackground: Boolean = true,
        action: () -> Unit,
    ) {
        contentView.findViewById<PusherSettingsItemView>(id).apply {
            configure(
                text,
                icon,
                iconWidthDp = iconWidth,
                iconHeightDp = iconHeight,
                showsIconBackground = showsIconBackground,
            )
            setOnClickListener { action() }
        }
    }

    private fun animateTo(page: Page) {
        this.page = page
        showPage(page)
        updateSheetHeight(animated = true)
    }

    private fun applyPage(page: Page) {
        this.page = page
        showPage(page)
        updateSheetHeight(animated = false)
    }

    private fun showPage(page: Page) {
        sheetTitle = page.title?.let(context::getString)
        main.isVisible = page == Page.MAIN
        audio.isVisible = page == Page.AUDIO
        audio.setActive(page == Page.AUDIO && isShowing)
    }


    private fun dp(value: Int) = (value * context.resources.displayMetrics.density).toInt()

    private fun animateDismiss(action: () -> Unit) {
        pendingDismissAction = action
        dismiss()
    }
}
