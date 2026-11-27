package io.trtc.tuikit.chat.common

import com.tencent.mmkv.MMKV
import kotlinx.coroutines.flow.MutableStateFlow

object DemoTabState {

    private val mmkv by lazy { MMKV.defaultMMKV() }

    val showCallsTabFlow by lazy {
        MutableStateFlow(mmkv.decodeBool(AppConstants.KEY_SHOW_CALLS_TAB, true))
    }

    fun setShowCallsTab(show: Boolean) {
        mmkv.encode(AppConstants.KEY_SHOW_CALLS_TAB, show)
        showCallsTabFlow.value = show
    }
}
