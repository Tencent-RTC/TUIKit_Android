package io.trtc.tuikit.chat.uikit.components.common

import com.google.gson.Gson

internal object GroupCallAttributeParser {
    const val GROUP_CALL_ATTRIBUTE_KEY = "inner_attr_kit_info"

    private const val KEY_BUSINESS_TYPE = "business_type"
    private const val KEY_CALL_ID = "call_id"
    private const val KEY_CALL_MEDIA_TYPE = "call_media_type"
    private const val KEY_USER_LIST = "user_list"
    private const val KEY_USER_ID = "userid"
    private const val KEY_USER_ID_ALT = "userID"
    private const val VALUE_BUSINESS_TYPE = "callkit"

    private val gson = Gson()

    fun hasOngoingGroupCall(rawAttribute: String?): Boolean {
        if (rawAttribute.isNullOrEmpty()) {
            return false
        }
        val map = runCatching {
            @Suppress("UNCHECKED_CAST")
            gson.fromJson(rawAttribute, Map::class.java) as? Map<String, Any?>
        }.getOrNull() ?: return false

        val businessType = map[KEY_BUSINESS_TYPE] as? String
        val callId = map[KEY_CALL_ID] as? String
        val mediaType = map[KEY_CALL_MEDIA_TYPE] as? String
        if (businessType != VALUE_BUSINESS_TYPE || callId.isNullOrEmpty() || mediaType.isNullOrEmpty()) {
            return false
        }
        val list = map[KEY_USER_LIST] as? List<*> ?: return false
        var validUserCount = 0
        for (item in list) {
            val userMap = item as? Map<*, *> ?: continue
            val userId = (userMap[KEY_USER_ID] as? String) ?: (userMap[KEY_USER_ID_ALT] as? String)
            if (!userId.isNullOrEmpty()) {
                validUserCount++
            }
        }
        return validUserCount > 1
    }
}
