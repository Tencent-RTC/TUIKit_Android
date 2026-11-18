package io.trtc.tuikit.chat.demo.common

import com.tencent.mmkv.MMKV

/**
 * 体验 Demo 首页 AI 引导是否已经被点过。存在应用私有目录里，卸载后消失，重装会再出现。
 * 不按 sdkAppID 区分，切换账号也不会重新出现。
 */
object AIHomeHintStore {

    private const val KEY_SEND_CLICKED = "ai_home_send_red_dot_clicked"
    private const val KEY_CALL_CLICKED = "ai_home_call_red_dot_clicked"

    private val mmkv by lazy { MMKV.defaultMMKV() }

    val shouldShowSend: Boolean
        get() = !mmkv.decodeBool(KEY_SEND_CLICKED, false)

    val shouldShowCall: Boolean
        get() = !mmkv.decodeBool(KEY_CALL_CLICKED, false)

    fun markSendClicked() {
        mmkv.encode(KEY_SEND_CLICKED, true)
    }

    fun markCallClicked() {
        mmkv.encode(KEY_CALL_CLICKED, true)
    }
}
