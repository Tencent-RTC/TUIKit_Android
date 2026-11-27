package io.trtc.tuikit.chat.uikit.components.messageinput.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

// Shares the bottom input surface (IME / emoji / more panel) height animation state with the
// message list. While the height is animating, the list viewport resizes every frame, so the
// list suppresses item placement animations to keep messages tracking the bottom edge
// immediately. The keyboard and panel animations are tracked independently because they can
// run concurrently during keyboard <-> panel transitions.
class InputSurfaceAnimationState internal constructor() {
    var isKeyboardAnimating by mutableStateOf(false)
        internal set
    var isPanelHeightAnimating by mutableStateOf(false)
        internal set

    val isHeightAnimating: Boolean
        get() = isKeyboardAnimating || isPanelHeightAnimating
}

val LocalInputSurfaceAnimationState = staticCompositionLocalOf<InputSurfaceAnimationState?> { null }

@Composable
fun rememberInputSurfaceAnimationState(): InputSurfaceAnimationState {
    return remember { InputSurfaceAnimationState() }
}
