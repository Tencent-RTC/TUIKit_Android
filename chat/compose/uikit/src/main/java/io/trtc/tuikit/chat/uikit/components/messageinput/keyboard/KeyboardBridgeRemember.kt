package io.trtc.tuikit.chat.uikit.components.messageinput.keyboard

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

@Composable
internal fun rememberKeyboardBridge(): KeyboardBridge {
    val activity = LocalActivity.current
    val view = LocalView.current
    val bridge = remember(activity) {
        KeyboardBridge.create(activity ?: view.context)
    }
    DisposableEffect(bridge, view) {
        bridge.attach(view)
        onDispose {
            bridge.listener = null
            bridge.detach(view)
        }
    }
    return bridge
}
