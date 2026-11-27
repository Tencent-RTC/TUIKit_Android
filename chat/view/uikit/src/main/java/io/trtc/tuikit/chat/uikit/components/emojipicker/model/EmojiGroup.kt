package io.trtc.tuikit.chat.uikit.components.emojipicker.model
data class EmojiGroup(
    val id: String,
    val name: String,
    val desc: String = "",
    val emojiGroupIconUrl: Any,
    val emojis: List<Emoji>,
    val isLittleEmoji: Boolean = false,
    val supportReaction: Boolean = isLittleEmoji,
)

open class Emoji(
    val key: String,
    val emojiName: String,
    var emojiUrl: Any = "",
)
