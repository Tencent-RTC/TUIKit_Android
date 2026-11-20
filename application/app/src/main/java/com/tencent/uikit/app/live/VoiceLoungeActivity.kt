package com.tencent.uikit.app.live

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import com.tencent.uikit.app.main.BaseActivity
import com.trtc.uikit.livekit.voicelounge.VoiceLoungeView
import io.trtc.tuikit.atomicxcore.api.login.LoginStore

/**
 * 语聊房入口：所有人复用同一个 [VoiceLoungeView]，Mode 决定创建还是加入房间。
 * - 不带 [EXTRA_JOIN_LIVE_ID]：创建房间，liveID 自动生成 `voice_<当前用户 ID>`。
 * - 带 [EXTRA_JOIN_LIVE_ID]：加入指定房间。
 */
class VoiceLoungeActivity : BaseActivity() {

    private var voiceLoungeView: VoiceLoungeView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!requireLoggedIn()) return
        initLiveWindow()

        val joinLiveID = intent.getStringExtra(EXTRA_JOIN_LIVE_ID).orEmpty()
        val mode = if (joinLiveID.isNotEmpty()) {
            VoiceLoungeView.Mode.Join(joinLiveID)
        } else {
            val userID = LoginStore.shared.loginState.loginUserInfo.value?.userID.orEmpty()
            VoiceLoungeView.Mode.Create(if (userID.isEmpty()) "" else "$LIVE_ID_PREFIX$userID")
        }
        val view = VoiceLoungeView(this, mode)
        view.onClose = { finish() }
        voiceLoungeView = view
        setContentView(view)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (view.handleBackPressed()) return
                finish()
            }
        })
    }

    override fun onDestroy() {
        voiceLoungeView?.release()
        voiceLoungeView = null
        super.onDestroy()
    }

    companion object {
        /** 语聊房主播开播时 liveID 固定为 `voice_<userID>`，直播列表按该前缀分流。 */
        const val LIVE_ID_PREFIX = "voice_"
        private const val EXTRA_JOIN_LIVE_ID = "joinLiveID"

        fun createIntent(context: Context): Intent = Intent(context, VoiceLoungeActivity::class.java)

        fun joinIntent(context: Context, liveID: String): Intent =
            Intent(context, VoiceLoungeActivity::class.java).putExtra(EXTRA_JOIN_LIVE_ID, liveID)
    }
}
