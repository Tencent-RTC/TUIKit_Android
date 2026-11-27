package io.trtc.tuikit.chat.uikit.components.messagelist.ui.messagerenderers
import io.trtc.tuikit.chat.uikit.components.emojipicker.model.Emoji

object FaceMessageResourceResolver {
    fun resolve(
        faceName: String?,
        faceIndex: Int,
        emojis: List<Emoji>
    ): Emoji? {
        val normalizedName = faceName?.takeIf { it.isNotEmpty() }?.trim()
        return normalizedName
            ?.let { name ->
                emojis.firstOrNull {
                    it.key == name ||
                        it.emojiName.equals(name, ignoreCase = true) ||
                        it.key.trim('[', ']').equals(name, ignoreCase = true)
                }
            }
            ?: emojis.getOrNull(faceIndex)
    }
}
