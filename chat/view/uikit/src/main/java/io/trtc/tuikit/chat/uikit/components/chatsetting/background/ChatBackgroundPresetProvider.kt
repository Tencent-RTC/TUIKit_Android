package io.trtc.tuikit.chat.uikit.components.chatsetting.background
data class ChatBackgroundPresetItem(
    val imageUri: String?,
    val thumbnailUri: String?
) {
    val isDefault: Boolean
        get() = imageUri == null
}

object ChatBackgroundPresetProvider {
    private const val BACKGROUND_URL_TEMPLATE =
        "https://im.sdk.qcloud.com/download/tuikit-resource/conversation-backgroundImage/backgroundImage_%s_full.png"
    private const val THUMBNAIL_URL_TEMPLATE =
        "https://im.sdk.qcloud.com/download/tuikit-resource/conversation-backgroundImage/backgroundImage_%s.png"
    private const val BACKGROUND_COUNT = 7

    fun getPresetItems(): List<ChatBackgroundPresetItem> {
        val items = ArrayList<ChatBackgroundPresetItem>(BACKGROUND_COUNT + 1)
        items.add(ChatBackgroundPresetItem(imageUri = null, thumbnailUri = null))
        for (index in 1..BACKGROUND_COUNT) {
            items.add(
                ChatBackgroundPresetItem(
                    imageUri = String.format(BACKGROUND_URL_TEMPLATE, index.toString()),
                    thumbnailUri = String.format(THUMBNAIL_URL_TEMPLATE, index.toString())
                )
            )
        }
        return items
    }
}
