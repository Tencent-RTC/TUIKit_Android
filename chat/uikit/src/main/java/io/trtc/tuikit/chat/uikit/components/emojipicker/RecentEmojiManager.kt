package io.trtc.tuikit.chat.uikit.components.emojipicker
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.tencent.cloud.tuikit.engine.common.ContextProvider
import com.tencent.mmkv.MMKV
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object RecentEmojiManager {
    private const val MMKV_ID = "recent_emoji_cache"
    private const val KEY_RECENT_EMOJI_PREFIX = "recent_emoji_list_"
    private const val MAX_RECENT_EMOJI_COUNT = 8

    private lateinit var mmkv: MMKV
    private val gson = Gson()

    private val _recentEmojiVersion = MutableStateFlow(0L)
    val recentEmojiVersion: StateFlow<Long> = _recentEmojiVersion.asStateFlow()

    init {
        val appContext = ContextProvider.getApplicationContext()
        if (appContext == null) {
            IllegalStateException("RecentEmojiManager init failed: application context is null").printStackTrace()
        } else {
            MMKV.initialize(appContext)
            mmkv = MMKV.mmkvWithID(MMKV_ID)
        }
    }

    fun getRecentEmojiList(groupId: String): List<String> {
        if (!::mmkv.isInitialized) return emptyList()

        val json = mmkv.getString(storageKey(groupId), null)
        return if (json.isNullOrEmpty()) {
            emptyList()
        } else {
            try {
                val type = TypeToken.getParameterized(List::class.java, String::class.java).type
                gson.fromJson(json, type)
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList()
            }
        }
    }

    fun updateRecentEmoji(groupId: String, emojiKey: String) {
        if (!::mmkv.isInitialized) return

        val recentList = getRecentEmojiList(groupId).toMutableList()
        recentList.remove(emojiKey)
        recentList.add(0, emojiKey)

        if (recentList.size > MAX_RECENT_EMOJI_COUNT) {
            recentList.removeAt(recentList.size - 1)
        }

        try {
            val json = gson.toJson(recentList)
            mmkv.putString(storageKey(groupId), json)
            _recentEmojiVersion.value += 1
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun storageKey(groupId: String) = KEY_RECENT_EMOJI_PREFIX + groupId
}
