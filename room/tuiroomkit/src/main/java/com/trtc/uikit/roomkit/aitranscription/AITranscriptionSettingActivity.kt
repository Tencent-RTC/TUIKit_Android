package com.trtc.uikit.roomkit.aitranscription

import android.os.Bundle
import android.widget.FrameLayout
import io.trtc.tuikit.atomicx.common.FullScreenActivity
import com.trtc.uikit.roomkit.aitranscription.repository.AITranscriberRepository
import com.trtc.uikit.roomkit.aitranscription.settingview.AITranscriptionSettingView
import com.trtc.uikit.roomkit.aitranscription.settingview.AITranscriptionSettingViewListener

class AITranscriptionSettingActivity : FullScreenActivity(), AITranscriptionSettingViewListener {

    companion object {
        private var pendingRepository: AITranscriberRepository? = null

        fun bindRepository(repository: AITranscriberRepository) {
            pendingRepository = repository
        }
    }

    private lateinit var settingView: AITranscriptionSettingView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        settingView = AITranscriptionSettingView(this)
        settingView.listener = this
        setContentView(settingView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT,
        ))

        pendingRepository?.let {
            settingView.bindRepository(it)
        }
    }

    override fun onSettingViewBackClicked() {
        finish()
    }
}
