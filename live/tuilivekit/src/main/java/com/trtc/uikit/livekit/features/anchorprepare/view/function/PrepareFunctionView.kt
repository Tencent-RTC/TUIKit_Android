package com.trtc.uikit.livekit.features.anchorprepare.view.function

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import com.trtc.uikit.livekit.R
import com.trtc.uikit.livekit.component.audioeffect.AudioEffectPanel
import com.trtc.uikit.livekit.component.beauty.BeautyIntegration
import com.trtc.uikit.livekit.component.beauty.tebeauty.TEBeautyManager
import com.trtc.uikit.livekit.features.anchorprepare.store.AnchorPrepareStore
import com.trtc.uikit.livekit.features.anchorprepare.store.AnchorPrepareConfig
import com.trtc.uikit.livekit.features.anchorprepare.view.liveinfoedit.livetemplatepicker.LiveTemplatePicker
import io.trtc.tuikit.atomicx.widget.basicwidget.popover.AtomicPopover
import io.trtc.tuikit.atomicxcore.api.device.DeviceStore
import io.trtc.tuikit.atomicxcore.api.view.LiveCoreView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class PrepareFunctionView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private var audioEffectPanel: AtomicPopover? = null
    private var videoSettingPanel: PrepareVideoSettingPanel? = null
    private var manager: AnchorPrepareStore? = null
    private var liveCoreView: LiveCoreView? = null
    private var subscribeStateJob: Job? = null

    init {
        LayoutInflater.from(context).inflate(R.layout.anchor_prepare_layout_function, this, true)
    }

    fun init(manager: AnchorPrepareStore, liveCoreView: LiveCoreView) {
        this.manager = manager
        this.liveCoreView = liveCoreView
        TEBeautyManager.setCustomVideoProcess()

        initView()
        addObserver()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        removeObserver()
    }

    private fun addObserver() {
        subscribeStateJob = CoroutineScope(Dispatchers.Main).launch {
            launch {
                AnchorPrepareConfig.disableMenuAudioEffectButton.collect {
                    onAudioEffectDisableChange(it)
                }
            }

            launch {
                AnchorPrepareConfig.disableMenuBeautyButton.collect {
                    onBeautyDisableChange(it)
                }
            }

            launch {
                AnchorPrepareConfig.disableMenuSwitchButton.collect {
                    onMirrorDisableChange(it)
                }
            }
        }
    }

    private fun removeObserver() {
        subscribeStateJob?.cancel()
    }

    private fun initView() {
        initBeautyButton()
        initAudioEffectButton()
        initFlipButton()
        initLayoutButton()
        initVideoSettingButton()
    }

    private fun initBeautyButton() {
        findViewById<android.view.View>(R.id.iv_beauty).setOnClickListener {
            BeautyIntegration.showBeautyDialog(context)
        }
    }

    private fun initAudioEffectButton() {
        findViewById<android.view.View>(R.id.iv_audio_effect).setOnClickListener {
            if (audioEffectPanel == null) {
                audioEffectPanel = AtomicPopover(context)
                val audioEffectPanelView = AudioEffectPanel(context)
                audioEffectPanelView.init(manager?.getState()?.roomId ?: "")
                audioEffectPanelView.setOnBackButtonClickListener(object : AudioEffectPanel.OnBackButtonClickListener {
                    override fun onClick() {
                        audioEffectPanel?.dismiss()
                    }
                })
                audioEffectPanel?.setContent(audioEffectPanelView)
            }
            audioEffectPanel?.show()
        }
    }

    private fun initFlipButton() {
        findViewById<android.view.View>(R.id.iv_flip).setOnClickListener {
            val isFront = DeviceStore.shared().deviceState.isFrontCamera.value
            DeviceStore.shared().switchCamera(!isFront)
        }
    }

    private fun initLayoutButton() {
        findViewById<android.view.View>(R.id.iv_layout).setOnClickListener {
            manager?.let { mgr ->
                liveCoreView?.let { coreView ->
                    val picker = LiveTemplatePicker(context, mgr, coreView)
                    picker.show()
                }
            }
        }
    }

    private fun initVideoSettingButton() {
        findViewById<android.view.View>(R.id.iv_video_setting).setOnClickListener {
            if (videoSettingPanel == null) {
                liveCoreView?.let {
                    videoSettingPanel = PrepareVideoSettingPanel(context)
                }
            }
            videoSettingPanel?.show()
        }
    }

    private fun onMirrorDisableChange(disable: Boolean?) {
        if (AnchorPrepareConfig.disableMenuSwitchButton.value == true) {
            findViewById<android.view.View>(R.id.rl_flip).visibility = GONE
        } else {
            findViewById<android.view.View>(R.id.rl_flip).visibility = VISIBLE
        }
    }

    private fun onAudioEffectDisableChange(disable: Boolean?) {
        if (AnchorPrepareConfig.disableMenuAudioEffectButton.value == true) {
            findViewById<android.view.View>(R.id.rl_audio_effect).visibility = GONE
        } else {
            findViewById<android.view.View>(R.id.rl_audio_effect).visibility = VISIBLE
        }
    }

    private fun onBeautyDisableChange(disable: Boolean?) {
        if (AnchorPrepareConfig.disableMenuBeautyButton.value == true) {
            findViewById<android.view.View>(R.id.rl_beauty).visibility = GONE
        } else {
            findViewById<android.view.View>(R.id.rl_beauty).visibility = VISIBLE
        }
    }
}