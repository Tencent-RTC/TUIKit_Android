package io.trtc.tuikit.chat.uikit.components.emojipicker

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.tencent.mmkv.MMKV
import io.trtc.tuikit.chat.uikit.components.common.ContextProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object RecentEmojiManager {
    private const val MMKV_ID = "recent_emoji_cache"
    private const val KEY_RECENT_EMOJI_PREFIX = "recent_emoji_list_"
    private const val KEY_LEGACY_RECENT_EMOJI = "recent_emoji_list"
    private const val MAX_RECENT_EMOJI_COUNT = 8

    private lateinit var mmkv: MMKV
    private val gson = Gson()

    private val _recentEmojiVersion = MutableStateFlow(0L)
    val recentEmojiVersion: StateFlow<Long> = _recentEmojiVersion.asStateFlow()

    private val _recentEmojis = MutableStateFlow<List<String>>(emptyList())
    val recentEmojis: StateFlow<List<String>> = _recentEmojis.asStateFlow()

    init {
        val appContext = runCatching { ContextProvider.appContext }.getOrNull()
        if (appContext != null) {
            initialize(appContext)
        }
    }

    @JvmStatic
    fun initialize(context: Context) {
        synchronized(this) {
            if (::mmkv.isInitialized) {
                return
            }
            MMKV.initialize(context.applicationContext)
            mmkv = MMKV.mmkvWithID(MMKV_ID)
            migrateLegacyRecentEmojis()
        }
        refreshRecentEmojis(EmojiManager.BUILT_IN_EMOJI_GROUP_ID)
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

    fun getRecentEmojiList(): List<String> {
        return getRecentEmojiList(EmojiManager.BUILT_IN_EMOJI_GROUP_ID)
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
            if (groupId == EmojiManager.BUILT_IN_EMOJI_GROUP_ID) {
                _recentEmojis.value = recentList
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun updateRecentEmoji(emojiKey: String) {
        updateRecentEmoji(EmojiManager.BUILT_IN_EMOJI_GROUP_ID, emojiKey)
    }

    fun saveRecentEmojiList(emojiList: List<String>) {
        if (!::mmkv.isInitialized) {
            return
        }

        try {
            val json = gson.toJson(emojiList)
            mmkv.putString(storageKey(EmojiManager.BUILT_IN_EMOJI_GROUP_ID), json)
            _recentEmojiVersion.value += 1
            _recentEmojis.value = emojiList
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun migrateLegacyRecentEmojis() {
        try {
            val newKey = storageKey(EmojiManager.BUILT_IN_EMOJI_GROUP_ID)
            val legacyJson = mmkv.getString(KEY_LEGACY_RECENT_EMOJI, null)
            if (mmkv.getString(newKey, null).isNullOrEmpty() && !legacyJson.isNullOrEmpty()) {
                mmkv.putString(newKey, legacyJson)
            }
            if (legacyJson != null) {
                mmkv.removeValueForKey(KEY_LEGACY_RECENT_EMOJI)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun refreshRecentEmojis(groupId: String) {
        _recentEmojis.value = getRecentEmojiList(groupId)
    }

    private fun storageKey(groupId: String) = KEY_RECENT_EMOJI_PREFIX + groupId
}
