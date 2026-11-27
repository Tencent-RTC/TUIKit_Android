package io.trtc.tuikit.chat


sealed class Event {
    data class ContactDeleted(val contactID: String) : Event()
    data class GroupDeleted(val groupID: String) : Event()
}
