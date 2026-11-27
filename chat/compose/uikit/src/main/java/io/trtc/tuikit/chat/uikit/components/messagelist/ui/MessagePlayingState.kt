package io.trtc.tuikit.chat.uikit.components.messagelist.ui

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf

val LocalAudioPlayingState = compositionLocalOf {
    mutableStateOf(AudioPlayingState())
}

data class AudioPlayingState(
    val isPlaying: Boolean = false,
    val playingMessageId: String? = null,
    val playPosition: Int = 0
)

fun AudioPlayingState.isMessagePlaying(messageId: String): Boolean {
    return isPlaying && playingMessageId == messageId
}
