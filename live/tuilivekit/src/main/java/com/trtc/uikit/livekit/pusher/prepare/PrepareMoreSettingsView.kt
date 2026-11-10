package com.trtc.uikit.livekit.pusher.prepare

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import com.trtc.uikit.livekit.R
import com.trtc.uikit.livekit.common.theme.BottomSheetView
import com.trtc.uikit.livekit.pusher.settings.PusherAudioEffectPage
import com.trtc.uikit.livekit.pusher.settings.AudioEffectView
import com.trtc.uikit.livekit.pusher.settings.PusherSettingsActionHandling
import com.trtc.uikit.livekit.pusher.settings.PusherSettingsItemView
import com.trtc.uikit.livekit.pusher.settings.PusherSettingsState
import java.lang.ref.WeakReference

/** Settings drawer. Subpages close to the current stage. */
internal class PrepareMoreSettingsView(context: Context) : BottomSheetView(context) {
    var actionHandler: PusherSettingsActionHandling?
        get() = actionHandlerReference?.get()
        set(value) {
            actionHandlerReference = value?.let(::WeakReference)
        }

    private val mainContentView: View
    private val audioEffectPage: AudioEffectView
    private val mirrorItem: PusherSettingsItemView

    private val audioPage: PusherAudioEffectPage
    private var settings = PusherSettingsState()
    private var actionHandlerReference: WeakReference<PusherSettingsActionHandling>? = null

    override val sheetHeight: Int
        get() = audioPage.sheetHeight

    init {
        LayoutInflater.from(context).inflate(
            R.layout.livekit_pusher_prepare_more_settings_view,
            contentView,
            true,
        )

        mainContentView = contentView.findViewById(R.id.livekit_pusher_settings_main_content)
        audioEffectPage = contentView.findViewById(R.id.livekit_pusher_settings_audio_effect_page)
        audioPage = PusherAudioEffectPage(
            mainContentView, audioEffectPage,
            context.resources.getDimensionPixelSize(R.dimen.livekit_pusher_settings_page_main_height),
            R.string.common_more_settings,
        ) { title, animated ->
            sheetTitle = title
            updateSheetHeight(animated)
        }
        mirrorItem = contentView.findViewById(R.id.livekit_pusher_settings_mirror)

        mirrorItem.configure(
            R.string.common_video_settings_item_mirror,
            R.drawable.livekit_pusher_settings_ic_mirror_on,
        )
        val audioEffectItem =
            contentView.findViewById<PusherSettingsItemView>(R.id.livekit_pusher_settings_audio_effect).apply {
                configure(
                    R.string.livekit_pusher_audio_effects,
                    R.drawable.livekit_pusher_settings_ic_audio_effect,
                )
            }

        mirrorItem.setOnClickListener {
            settings = settings.copy(isMirrorEnabled = !settings.isMirrorEnabled)
            mirrorItem.setIcon(
                if (settings.isMirrorEnabled) {
                    R.drawable.livekit_pusher_settings_ic_mirror_on
                } else {
                    R.drawable.livekit_pusher_settings_ic_mirror_off
                },
            )
            actionHandler?.toggleMirror()
        }
        audioEffectItem.setOnClickListener {
            bindAudioEffectState(settings)
            audioPage.show(true, animated = true)
        }
        bindAudioEffectActions()
        audioPage.show(false)
    }

    fun bind(settings: PusherSettingsState) {
        this.settings = settings
        mirrorItem.setIcon(
            if (settings.isMirrorEnabled) {
                R.drawable.livekit_pusher_settings_ic_mirror_on
            } else {
                R.drawable.livekit_pusher_settings_ic_mirror_off
            },
        )
        bindAudioEffectState(settings)
    }

    private fun bindAudioEffectActions() {
        audioEffectPage.onSetCaptureVolume = { actionHandler?.setCaptureVolume(it) }
    }

    private fun bindAudioEffectState(settings: PusherSettingsState) {
        audioEffectPage.bindCaptureVolume(settings.captureVolume)
    }

    /** Opens the drawer on the main page. */
    override fun show() {
        audioPage.show(false)
        super.show()
    }

    /** True when the drawer is showing, so the host view can route the back gesture. */
    fun handleBackPressed(): Boolean {
        if (!isShowing) return false
        dismiss()
        return true
    }

    override fun sheetWillPresent() {
        audioPage.setPresented(true)
    }

    override fun sheetWillDismiss() {
        audioPage.setPresented(false)
    }

    override fun sheetDidDismiss() {
        audioPage.show(false)
    }

}
