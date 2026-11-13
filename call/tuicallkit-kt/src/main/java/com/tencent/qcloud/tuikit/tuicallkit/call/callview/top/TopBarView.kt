package com.tencent.qcloud.tuikit.tuicallkit.call.callview.top

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isVisible
import com.tencent.qcloud.tuikit.tuicallkit.R
import com.tencent.qcloud.tuikit.tuicallkit.call.CallKitConfig
import com.tencent.qcloud.tuikit.tuicallkit.common.CallKitToast
import io.trtc.tuikit.atomicxcore.api.call.CallStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

internal class TopBarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    internal var onInviteTap: (() -> Unit)? = null

    private val pipButton: ImageView
    private val timerLabel: TextView
    private val inviteButton: ImageView
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    init {
        LayoutInflater.from(context).inflate(R.layout.tuicallkit_top_bar, this, true)
        pipButton = findViewById(R.id.pip_button)
        timerLabel = findViewById(R.id.timer_label)
        inviteButton = findViewById(R.id.invite_button)
        pipButton.setColorFilter(Color.WHITE)
        inviteButton.setColorFilter(Color.WHITE)
        pipButton.setOnClickListener {
            CallKitToast.show(context.getString(R.string.tuicallkit_call_minimize_unsupported))
        }
        inviteButton.setOnClickListener { onInviteTap?.invoke() }
        pipButton.isVisible = CallKitConfig.shared.enableFloatWindow

        scope.launch {
            CallStore.shared.observerState.activeCall
                .map { it.duration }
                .distinctUntilChanged()
                .collect { seconds ->
                    timerLabel.text = formatDuration(seconds.coerceAtLeast(0))
                }
        }
    }

    private fun formatDuration(total: Long): String {
        if (total >= 3600) {
            return String.format("%d:%02d:%02d", total / 3600, (total % 3600) / 60, total % 60)
        }
        return String.format("%02d:%02d", total / 60, total % 60)
    }

    fun handleConnected() {
        timerLabel.alpha = 1f
    }

    fun setImmersive(immersive: Boolean) {
        pipButton.isVisible = CallKitConfig.shared.enableFloatWindow
        inviteButton.isVisible = true
    }

    override fun onDetachedFromWindow() {
        scope.cancel()
        super.onDetachedFromWindow()
    }
}
