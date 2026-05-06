package com.trtc.uikit.livekit.features.audienceview.view.coguest.panel

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import android.widget.TextView
import com.trtc.uikit.livekit.R
import io.trtc.tuikit.atomicx.widget.basicwidget.avatar.AtomicAvatar
import io.trtc.tuikit.atomicx.widget.basicwidget.avatar.AtomicAvatar.AvatarContent
import io.trtc.tuikit.atomicxcore.api.login.LoginStore

class CoGuestRequestFloatView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    companion object {
        private const val MIN_DOT_COUNT = 1
        private const val MAX_DOT_COUNT = 3
    }

    private lateinit var textWaitingPass: TextView
    private var dotCount: Int = MIN_DOT_COUNT
    private var timerHandler: Handler? = null
    private var timerRunnable: Runnable? = null

    init {
        initView()
    }

    private fun initView() {
        LayoutInflater.from(context)
            .inflate(R.layout.livekit_audience_link_mic_waiting_pass, this, true)
        bindViewId()
        initUserAvatarView()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        startTimerTask()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopTimerTask()
    }

    private fun initUserAvatarView() {
        val avatar = findViewById<AtomicAvatar>(R.id.link_mic_audience_icon)
        avatar.setContent(
            AvatarContent.URL(
                LoginStore.shared.loginState.loginUserInfo.value?.avatarURL ?: "",
                R.drawable.livekit_ic_avatar
            )
        )
    }

    private fun bindViewId() {
        textWaitingPass = findViewById(R.id.text_waiting_pass)
    }

    private fun stopTimerTask() {
        timerHandler?.removeCallbacks(timerRunnable!!)
        timerHandler = null
        timerRunnable = null
    }

    private fun startTimerTask() {
        timerHandler = Handler(Looper.getMainLooper())
        timerRunnable = Runnable {
            dotCount++
            if (dotCount > MAX_DOT_COUNT) {
                dotCount = 1
            }
            val text = StringBuilder(context.getString(R.string.common_waiting_pass))
            for (i in 0 until dotCount) {
                text.append(".")
            }
            textWaitingPass.text = text.toString()
            timerHandler?.postDelayed(timerRunnable!!, 1000)
        }
        timerHandler?.post(timerRunnable!!)
    }
}
