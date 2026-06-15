package com.trtc.uikit.livekit.voiceroom.view.preview

import android.content.Context
import android.text.TextUtils
import android.util.AttributeSet
import android.view.LayoutInflater
import com.trtc.uikit.livekit.R
import com.trtc.uikit.livekit.common.BACKGROUND_URL_LIST
import com.trtc.uikit.livekit.common.DEFAULT_BACKGROUND_URL
import com.trtc.uikit.livekit.component.audioeffect.AudioEffectPanel
import com.trtc.uikit.livekit.voiceroom.view.basic.BasicView
import io.trtc.tuikit.atomicx.widget.basicwidget.popover.AtomicPopover

class AnchorPreviewFunctionView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : BasicView(context, attrs, defStyleAttr) {

    private var settingsDialog: SettingsDialog? = null
    private var layoutSettingPanel: LayoutSettingPanel? = null
    private var audioEffectDialog: AtomicPopover? = null
    private var streamPresetImagePicker: StreamPresetImagePicker? = null

    init {
        LayoutInflater.from(context)
            .inflate(R.layout.livekit_voiceroom_anchor_preview_function, this, true)
        initBackgroundImageButton()
        initAudioEffectButton()
        initSettingsButton()
        initLayoutButton()
    }

    private fun initBackgroundImageButton() {
        findViewById<android.view.View>(R.id.iv_bg_image).setOnClickListener {
            val currentBackgroundURL =
                voiceRoomManager?.prepareStore?.prepareState?.liveInfo?.value?.backgroundURL
            if (streamPresetImagePicker == null) {
                val config = StreamPresetImagePicker.Config()
                config.title = context.getString(R.string.common_settings_bg_image)
                config.confirmButtonText = context.getString(R.string.common_set_as_background)
                config.data = BACKGROUND_URL_LIST
                config.currentImageUrl = currentBackgroundURL
                streamPresetImagePicker = StreamPresetImagePicker(context, config)
                streamPresetImagePicker?.setOnConfirmListener(object :
                    StreamPresetImagePicker.OnConfirmListener {
                    override fun onConfirm(imageUrl: String) {
                        voiceRoomManager?.prepareStore?.updateLiveBackgroundURL(
                            transferImageUrlFromThumb(imageUrl) ?: DEFAULT_BACKGROUND_URL
                        )

                    }
                })
            } else {
                streamPresetImagePicker?.updateCurrentImageUrl(currentBackgroundURL)
            }
            streamPresetImagePicker?.show()
        }
    }

    private fun initAudioEffectButton() {
        findViewById<android.view.View>(R.id.iv_audio_effect).setOnClickListener {
            if (audioEffectDialog == null) {
                audioEffectDialog = AtomicPopover(context)
                val audioEffectPanel = AudioEffectPanel(context)
                audioEffectPanel.init(liveID)
                audioEffectPanel.setOnBackButtonClickListener(object :
                    AudioEffectPanel.OnBackButtonClickListener {
                    override fun onClick() {
                        audioEffectDialog?.dismiss()
                    }
                })
                audioEffectDialog?.setContent(audioEffectPanel)
                audioEffectDialog?.setPanelHeight(AtomicPopover.PanelHeight.WrapContent)

            }
            audioEffectDialog?.show()
        }
    }

    private fun initSettingsButton() {
        findViewById<android.view.View>(R.id.iv_settings).setOnClickListener {
            if (voiceRoomManager == null) return@setOnClickListener
            if (settingsDialog == null) {
                settingsDialog = SettingsDialog(context, voiceRoomManager!!)
            }
            settingsDialog?.show()
        }
    }

    private fun initLayoutButton() {
        findViewById<android.view.View>(R.id.iv_layout).setOnClickListener {
            if (voiceRoomManager == null) return@setOnClickListener
            if (layoutSettingPanel == null) {
                layoutSettingPanel = LayoutSettingPanel(context, voiceRoomManager!!)
            }
            layoutSettingPanel?.show()
        }
    }

    private fun transferImageUrlFromThumb(thumbUrl: String?): String? {
        if (TextUtils.isEmpty(thumbUrl)) {
            return thumbUrl
        }

        val index = thumbUrl!!.indexOf("_thumb.png")
        if (index == -1) {
            return thumbUrl
        }
        return thumbUrl.substring(0, index) + ".png"
    }


    override fun addObserver() {

    }

    override fun removeObserver() {

    }

    override fun initStore() {

    }
}