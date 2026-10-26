package com.trtc.uikit.roomkit.aitranscription

import android.os.Bundle
import android.widget.FrameLayout
import io.trtc.tuikit.atomicx.common.FullScreenActivity
import com.trtc.uikit.roomkit.aitranscription.minutesview.AIMinutesView
import com.trtc.uikit.roomkit.aitranscription.minutesview.AIMinutesViewListener
import com.trtc.uikit.roomkit.aitranscription.repository.AITranscriberRepository
import java.lang.ref.WeakReference

class AIMinutesActivity : FullScreenActivity(), AIMinutesViewListener {

    companion object {
        private var pendingRepository: AITranscriberRepository? = null
        private var currentInstance: WeakReference<AIMinutesActivity>? = null

        fun bindRepository(repository: AITranscriberRepository) {
            pendingRepository = repository
        }

        fun finishIfExists() {
            currentInstance?.get()?.finish()
            currentInstance = null
        }

        fun getForegroundInstance(): AIMinutesActivity? {
            val activity = currentInstance?.get() ?: return null
            return if (!activity.isFinishing && !activity.isDestroyed) activity else null
        }
    }

    private lateinit var minutesView: AIMinutesView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        currentInstance = WeakReference(this)

        minutesView = AIMinutesView(this)
        minutesView.listener = this
        setContentView(minutesView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT,
        ))

        pendingRepository?.let {
            minutesView.bindRepository(it)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (currentInstance?.get() === this) {
            currentInstance = null
        }
    }

    override fun onMinutesViewBackClicked() {
        finish()
    }
}
