package io.trtc.tuikit.chat.uikit.components.common
import com.google.gson.JsonObject
import io.trtc.tuikit.atomicxcore.api.message.OfflinePushInfo

internal object MessageOfflinePushInfoFactory {
    fun create(
        title: String,
        description: String,
        isGroup: Boolean,
        senderId: String,
        senderNickName: String,
        faceUrl: String?
    ): OfflinePushInfo {
        return OfflinePushInfo(
            title = title,
            description = trimDescription(description),
            extensionInfo = createExtensionInfo(
                isGroup = isGroup,
                description = description,
                senderId = senderId,
                senderNickName = senderNickName,
                faceUrl = faceUrl
            )
        )
    }

    fun trimDescription(text: String, maxLength: Int = 50): String {
        val normalized = text.trim().replace("\n", " ").replace("\r", " ")
        if (normalized.length <= maxLength) return normalized
        return normalized.substring(0, maxLength)
    }

    private fun createExtensionInfo(
        isGroup: Boolean,
        description: String,
        senderId: String,
        senderNickName: String,
        faceUrl: String?
    ): Map<String, Any> {
        val ext = createExtJson(
            isGroup = isGroup,
            description = description,
            senderId = senderId,
            senderNickName = senderNickName,
            faceUrl = faceUrl
        )
        return mapOf(
            "ext" to ext,
            "AndroidOPPOChannelID" to "tuikit",
            "AndroidHuaWeiCategory" to "IM",
            "AndroidVIVOCategory" to "IM",
            "AndroidHonorImportance" to "NORMAL",
            "AndroidMeizuNotifyType" to 1,
            "iOSInterruptionLevel" to "time-sensitive",
            "enableIOSBackgroundNotification" to false
        )
    }

    private fun createExtJson(
        isGroup: Boolean,
        description: String,
        senderId: String,
        senderNickName: String,
        faceUrl: String?
    ): String {
        val businessInfo = JsonObject().apply {
            addOptionalProperty("content", description.takeIf { it.isNotEmpty() })
            addProperty("sender", senderId)
            addOptionalProperty("faceUrl", faceUrl)
            addProperty("nickname", senderNickName)
            addProperty("chatType", if (isGroup) 2 else 1)
        }
        val configInfo = JsonObject().apply {
            addProperty("fcmPushType", 0)
            addProperty("fcmNotificationType", 0)
        }
        return JsonObject().apply {
            add("entity", businessInfo)
            add("timPushFeatures", configInfo)
        }.toString()
    }

    private fun JsonObject.addOptionalProperty(name: String, value: String?) {
        if (value != null) {
            addProperty(name, value)
        }
    }
}
