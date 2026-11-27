package io.trtc.tuikit.chat.common

import com.tencent.mmkv.MMKV

object MessageInputCallRedDotStore {

    private val mmkv by lazy { MMKV.defaultMMKV() }

    fun shouldShow(sdkAppID: Int, actionID: String): Boolean {
        if (sdkAppID <= 0) {
            return false
        }
        return !mmkv.decodeBool(storageKey(sdkAppID, actionID), false)
    }

    fun markClicked(sdkAppID: Int, actionID: String) {
        if (sdkAppID <= 0) {
            return
        }
        mmkv.encode(storageKey(sdkAppID, actionID), true)
    }

    private fun storageKey(sdkAppID: Int, actionID: String): String {
        return AppConstants.KEY_MESSAGE_INPUT_CALL_RED_DOT_CLICKED_PREFIX + sdkAppID + actionID
    }
}
