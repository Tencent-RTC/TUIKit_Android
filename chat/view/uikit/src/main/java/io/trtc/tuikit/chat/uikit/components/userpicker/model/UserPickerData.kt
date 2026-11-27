package io.trtc.tuikit.chat.uikit.components.userpicker.model
data class UserPickerData<T>(
    val key: String,
    val label: String,
    val avatarUrl: Any?,
    val extraData: T
)
