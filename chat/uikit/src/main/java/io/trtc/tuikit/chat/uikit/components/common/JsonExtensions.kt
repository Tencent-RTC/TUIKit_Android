package io.trtc.tuikit.chat.uikit.components.common
import com.google.gson.Gson

fun jsonData2Dictionary(json: String?): Map<String, String>? {
    if (json.isNullOrBlank()) {
        return null
    }
    val map = runCatching {
        Gson().fromJson(json, Map::class.java)
    }.getOrNull() ?: return null
    return map.mapNotNull { (key, value) ->
        val stringKey = key as? String ?: return@mapNotNull null
        stringKey to value.toString()
    }.toMap()
}
