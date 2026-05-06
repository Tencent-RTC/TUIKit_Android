package com.trtc.uikit.livekit.features.audienceview.view.coguest.widgets

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import com.trtc.uikit.livekit.R
import com.trtc.uikit.livekit.features.audienceview.store.AudienceStore
import com.trtc.uikit.livekit.features.audienceview.view.BasicView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AudienceEmptySeatView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : BasicView(context, attrs, defStyleAttr) {

    override fun init(manager: AudienceStore) {
        super.init(manager)
    }

    override fun initView() {
        LayoutInflater.from(context)
            .inflate(R.layout.livekit_co_guest_empty_audience_widgets_view, this, true)
    }

    override fun addObserver() {
        subscribeStateJob = CoroutineScope(Dispatchers.Main).launch {
            mediaState.isPictureInPictureMode.collect {
                onPictureInPictureObserver(it)
            }
        }
    }

    override fun removeObserver() {
        subscribeStateJob?.cancel()
    }

    private fun onPictureInPictureObserver(isPipMode: Boolean?) {
        visibility = if (isPipMode == true) {
            GONE
        } else {
            VISIBLE
        }
    }
}
