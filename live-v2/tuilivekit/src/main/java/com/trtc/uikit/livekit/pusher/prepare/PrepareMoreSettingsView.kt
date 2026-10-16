package com.trtc.uikit.livekit.pusher.prepare

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import androidx.annotation.StringRes
import androidx.core.view.isVisible
import com.trtc.uikit.livekit.R
import com.trtc.uikit.livekit.common.theme.BottomSheetView
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

    /** Pages of the drawer, each with its own panel height. */
    private enum class Page(@StringRes val titleRes: Int, val heightRes: Int) {
        MAIN(
            R.string.common_more_settings,
            R.dimen.livekit_pusher_settings_page_main_height
        ),
        AUDIO_EFFECT(
            R.string.livekit_pusher_audio_effects,
            R.dimen.livekit_pusher_settings_page_audio_effect_height
        ),
    }

    private val mainContentView: View
    private val audioEffectPage: AudioEffectView
    private val mirrorItem: PusherSettingsItemView

    private var page = Page.MAIN
    private var settings = PusherSettingsState()
    private var actionHandlerReference: WeakReference<PusherSettingsActionHandling>? = null

    override val sheetHeight: Int
        get() = context.resources.getDimensionPixelSize(page.heightRes)

    init {
        LayoutInflater.from(context).inflate(
            R.layout.livekit_pusher_prepare_more_settings_view,
            contentView,
            true,
        )

        mainContentView = contentView.findViewById(R.id.livekit_pusher_settings_main_content)
        audioEffectPage = contentView.findViewById(R.id.livekit_pusher_settings_audio_effect_page)
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
            animateToPage(Page.AUDIO_EFFECT)
        }
        bindAudioEffectActions()
        applyPage(Page.MAIN)
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
    override fun show() = show(Page.MAIN)

    /** True when the drawer is showing, so the host view can route the back gesture. */
    fun handleBackPressed(): Boolean {
        if (!isShowing) return false
        dismiss()
        return true
    }

    override fun sheetWillPresent() {
        audioEffectPage.setActive(page == Page.AUDIO_EFFECT)
    }

    override fun sheetWillDismiss() {
        audioEffectPage.setActive(false)
    }

    override fun sheetDidDismiss() {
        applyPage(Page.MAIN)
    }

    private fun show(target: Page) {
        applyPage(target)
        super.show()
    }

    private fun applyPage(target: Page) {
        page = target
        showPageContent(target)
        updateSheetHeight(animated = false)
    }

    /** Grows or shrinks the panel when the host switches page, matching the iOS animation. */
    private fun animateToPage(target: Page) {
        page = target
        showPageContent(target)
        updateSheetHeight(animated = true)
    }

    private fun showPageContent(target: Page) {
        sheetTitle = context.getString(target.titleRes)
        mainContentView.isVisible = target == Page.MAIN
        audioEffectPage.isVisible = target == Page.AUDIO_EFFECT
        audioEffectPage.setActive(target == Page.AUDIO_EFFECT && isShowing)
    }

}
