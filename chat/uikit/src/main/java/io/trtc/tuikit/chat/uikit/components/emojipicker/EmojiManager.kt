package io.trtc.tuikit.chat.uikit.components.emojipicker
import android.content.Context
import android.content.res.Configuration
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import androidx.core.os.ConfigurationCompat
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.tencent.cloud.tuikit.engine.common.ContextProvider
import io.trtc.tuikit.chat.uikit.R
import io.trtc.tuikit.chat.uikit.components.emojipicker.model.Emoji
import io.trtc.tuikit.chat.uikit.components.emojipicker.model.EmojiGroup
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object EmojiManager {

    const val BUILT_IN_EMOJI_GROUP_ID = "tui_built_in_little_emoji"

    private val SHIPPED_EMOJI_LOCALES = listOf(
        Locale.ROOT,
        Locale.SIMPLIFIED_CHINESE,
        Locale.TRADITIONAL_CHINESE,
        Locale("ar")
    )

    private var _appContext: Context? = null
    private var _chineseElementEmojiKeySet: Set<String> = emptySet()
    private var _emojiNamesLoadedLanguageTag: String? = null
    private val _emojiGroupList = mutableListOf<EmojiGroup>()
    private val _emojiByKeyMap = mutableMapOf<String, Emoji>()
    private val _emojiByNameMap = mutableMapOf<String, Emoji>()
    private var _allLocaleEmojiNamesLoaded = false

    private var _sortedLittleEmojiList: List<Emoji> = emptyList()
    private var _sortedLittleEmojiKeyList: List<String> = emptyList()
    private var _sortedLittleEmojiNameList: List<String> = emptyList()

    private val _emojiImageCache = mutableMapOf<String, Drawable>()
    private val _preloadingKeys = mutableSetOf<String>()

    private val _emojiGroupState = MutableStateFlow<List<EmojiGroup>>(emptyList())
    val emojiGroupState: StateFlow<List<EmojiGroup>> = _emojiGroupState.asStateFlow()

    private var _emojiIndexVersion = 0
    val emojiIndexVersion: Int
        get() = synchronized(this) {
            _appContext?.let { reloadEmojiNamesIfLocaleChangedLocked(it) }
            _emojiIndexVersion
        }

    val sortedLittleEmojiList: List<Emoji>
        get() = synchronized(this) {
            _appContext?.let { reloadEmojiNamesIfLocaleChangedLocked(it) }
            _sortedLittleEmojiList.toList()
        }

    val sortedLittleEmojiKeyList: List<String>
        get() = synchronized(this) { _sortedLittleEmojiKeyList.toList() }

    val sortedLittleEmojiNameList: List<String>
        get() = synchronized(this) {
            ensureAllLocaleEmojiNamesLoadedLocked()
            _sortedLittleEmojiNameList.toList()
        }

    val reactionEmojiGroup: EmojiGroup?
        get() = synchronized(this) {
            _emojiGroupList.firstOrNull { it.supportReaction }
        }

    val reactionEmojiList: List<Emoji>
        get() = synchronized(this) {
            _emojiGroupList.firstOrNull { it.supportReaction }?.emojis.orEmpty()
        }

    val emojiGroupList: List<EmojiGroup>
        get() = synchronized(this) {
            _appContext?.let { reloadEmojiNamesIfLocaleChangedLocked(it) }
            _emojiGroupState.value
        }

    init {
        val appContext = ContextProvider.getApplicationContext()
        if (appContext == null) {
            IllegalStateException("EmojiManager init failed: application context is null").printStackTrace()
        } else {
            val builtInEmojiGroup = try {
                loadBuiltInEmojiGroup(appContext)
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
            if (builtInEmojiGroup != null) {
                synchronized(this) {
                    _appContext = appContext
                    _chineseElementEmojiKeySet = parseChineseElementEmojiKeys(appContext)
                    _emojiGroupList.add(builtInEmojiGroup)
                    rebuildEmojiIndexLocked()
                    _emojiNamesLoadedLanguageTag = currentLanguageTag(appContext)
                }
                preloadEmojiGroups(listOf(builtInEmojiGroup))
            }
        }
    }

    fun addEmojiGroup(emojiGroup: EmojiGroup): EmojiManager {
        return addEmojiGroup(emojiGroup, -1)
    }

    fun addEmojiGroup(emojiGroup: EmojiGroup, index: Int): EmojiManager {
        synchronized(this) {
            _emojiGroupList.removeAll { it.id == emojiGroup.id }
            _emojiGroupList.add(coerceInsertIndex(index, _emojiGroupList.size), emojiGroup)
            rebuildEmojiIndexLocked()
        }
        preloadEmojiGroups(listOf(emojiGroup))
        return this
    }

    fun addEmojiGroups(emojiGroups: List<EmojiGroup>): EmojiManager {
        synchronized(this) {
            val uniqueEmojiGroups = linkedMapOf<String, EmojiGroup>()
            emojiGroups.forEach { uniqueEmojiGroups[it.id] = it }
            val ids = uniqueEmojiGroups.keys
            _emojiGroupList.removeAll { it.id in ids }
            _emojiGroupList.addAll(uniqueEmojiGroups.values)
            rebuildEmojiIndexLocked()
        }
        preloadEmojiGroups(emojiGroups)
        return this
    }

    fun removeEmojiGroup(id: String): EmojiManager {
        synchronized(this) {
            if (_emojiGroupList.removeAll { it.id == id }) {
                rebuildEmojiIndexLocked()
            }
        }
        return this
    }

    fun removeEmojiGroup(emojiGroup: EmojiGroup): EmojiManager {
        return removeEmojiGroup(emojiGroup.id)
    }

    fun removeEmojiGroups(emojiGroups: List<EmojiGroup>): EmojiManager {
        synchronized(this) {
            val ids = emojiGroups.mapTo(hashSetOf()) { it.id }
            if (_emojiGroupList.removeAll { it.id in ids }) {
                rebuildEmojiIndexLocked()
            }
        }
        return this
    }

    fun clearEmojiGroups(): EmojiManager {
        synchronized(this) {
            if (_emojiGroupList.isNotEmpty()) {
                _emojiGroupList.clear()
                rebuildEmojiIndexLocked()
            }
        }
        return this
    }

    fun getEmojiGroup(groupId: String): EmojiGroup? {
        return synchronized(this) {
            _emojiGroupList.firstOrNull { it.id == groupId }
        }
    }

    fun getEmojiGroups(): List<EmojiGroup> {
        return synchronized(this) { _emojiGroupList.toList() }
    }

    fun containsEmojiGroup(groupId: String): Boolean {
        return getEmojiGroup(groupId) != null
    }

    fun moveEmojiGroup(groupId: String, toIndex: Int): EmojiManager {
        synchronized(this) {
            val position = _emojiGroupList.indexOfFirst { it.id == groupId }
            if (position >= 0) {
                val target = toIndex.coerceIn(0, _emojiGroupList.lastIndex)
                if (target != position) {
                    _emojiGroupList.add(target, _emojiGroupList.removeAt(position))
                    rebuildEmojiIndexLocked()
                }
            }
        }
        return this
    }

    fun findEmojiByKey(key: String): Emoji? {
        return synchronized(this) { _emojiByKeyMap[key] }
    }

    fun findEmojiByName(name: String): Emoji? {
        return synchronized(this) {
            ensureAllLocaleEmojiNamesLoadedLocked()
            _emojiByNameMap[name]
        }
    }

    fun containsEmojiKey(text: String): Boolean {
        return synchronized(this) { _sortedLittleEmojiKeyList.any { text.contains(it) } }
    }

    fun isChineseLocale(): Boolean {
        val context = synchronized(this) { _appContext } ?: return false
        val locale = ConfigurationCompat.getLocales(context.resources.configuration)[0] ?: Locale.getDefault()
        return locale.language == Locale.CHINESE.language
    }


    fun isEmojiVisibleInPicker(key: String): Boolean {
        if (isChineseLocale()) {
            return true
        }
        return synchronized(this) { key !in _chineseElementEmojiKeySet }
    }

    fun filterEmojiGroupsForPicker(groups: List<EmojiGroup>): List<EmojiGroup> {
        if (isChineseLocale()) {
            return groups
        }
        return groups.map { group ->
            if (group.id != BUILT_IN_EMOJI_GROUP_ID) {
                group
            } else {
                group.copy(emojis = group.emojis.filter { emoji ->
                    synchronized(this) { emoji.key !in _chineseElementEmojiKeySet }
                })
            }
        }
    }

    fun reactionEmojiListForPicker(): List<Emoji> {
        val group = reactionEmojiGroup ?: return emptyList()
        if (isChineseLocale() || group.id != BUILT_IN_EMOJI_GROUP_ID) {
            return group.emojis
        }
        return group.emojis.filter { emoji ->
            synchronized(this) { emoji.key !in _chineseElementEmojiKeySet }
        }
    }

    private fun reloadEmojiNamesIfLocaleChangedLocked(context: Context) {
        val languageTag = currentLanguageTag(context)
        if (_emojiNamesLoadedLanguageTag == languageTag) {
            return
        }

        val groupIndex = _emojiGroupList.indexOfFirst { it.id == BUILT_IN_EMOJI_GROUP_ID }
        if (groupIndex < 0) {
            _emojiNamesLoadedLanguageTag = languageTag
            return
        }

        try {
            val emojiKeys: Array<String> = context.resources
                .getStringArray(R.array.emoji_picker_key_array)
            val emojiNames: Array<String> = context.resources
                .getStringArray(R.array.emoji_picker_name_array)
            if (emojiKeys.size != emojiNames.size) {
                return
            }

            val nameByKey = emojiKeys.indices.associate { emojiKeys[it] to emojiNames[it] }
            val builtInGroup = _emojiGroupList[groupIndex]
            val localizedEmojis = builtInGroup.emojis.map { emoji ->
                val localizedName = nameByKey[emoji.key]
                if (localizedName != null && localizedName != emoji.emojiName) {
                    Emoji(emoji.key, localizedName, emoji.emojiUrl)
                } else {
                    emoji
                }
            }
            _emojiGroupList[groupIndex] = builtInGroup.copy(emojis = localizedEmojis)
            _emojiNamesLoadedLanguageTag = languageTag
            rebuildEmojiIndexLocked()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun currentLanguageTag(context: Context): String {
        val locale = ConfigurationCompat.getLocales(context.resources.configuration)[0] ?: Locale.getDefault()
        return locale.toLanguageTag()
    }

    private fun parseChineseElementEmojiKeys(context: Context): Set<String> {
        return try {
            context.resources.getStringArray(R.array.emoji_picker_chinese_element_key_array).toSet()
        } catch (e: Exception) {
            e.printStackTrace()
            emptySet()
        }
    }

    fun getCachedEmojiDrawable(key: String): Drawable? {
        val cachedDrawable = synchronized(this) { _emojiImageCache[key] } ?: return null
        return cachedDrawable.constantState?.newDrawable()?.mutate()
    }

    private fun loadBuiltInEmojiGroup(context: Context): EmojiGroup {
        val emojiKeys: Array<String> = context.resources
            .getStringArray(R.array.emoji_picker_key_array)
        val emojiNames: Array<String> = context.resources
            .getStringArray(R.array.emoji_picker_name_array)
        val emojiPath: Array<String> = context.resources
            .getStringArray(R.array.emoji_picker_file_name_array)

        require(emojiKeys.size == emojiNames.size && emojiKeys.size == emojiPath.size) {
            "Emoji resource arrays must have the same size"
        }

        val emojis = emojiKeys.indices.map { i ->
            Emoji(
                emojiKeys[i],
                emojiNames[i],
                "file:///android_asset/buildinemojis/" + emojiPath[i]
            )
        }

        return EmojiGroup(
            id = BUILT_IN_EMOJI_GROUP_ID,
            name = "LittleYellowFaceEmoji",
            emojiGroupIconUrl = emojis.firstOrNull()?.emojiUrl ?: "",
            emojis = emojis,
            isLittleEmoji = true
        )
    }

    private fun coerceInsertIndex(index: Int, size: Int): Int {
        return if (index < 0 || index > size) size else index
    }

    private fun rebuildEmojiIndexLocked() {
        _emojiByKeyMap.clear()

        val allGroups = _emojiGroupList.toList()
        val littleEmojis = mutableListOf<Emoji>()
        allGroups.forEach { group ->
            group.emojis.forEach { emoji ->
                _emojiByKeyMap[emoji.key] = emoji
                if (group.isLittleEmoji) {
                    littleEmojis.add(emoji)
                }
            }
        }

        _emojiByNameMap.clear()
        littleEmojis.forEach { emoji -> _emojiByNameMap[emoji.emojiName] = emoji }
        _allLocaleEmojiNamesLoaded = false
        _sortedLittleEmojiNameList = _emojiByNameMap.keys.sortedByDescending { it.length }

        _sortedLittleEmojiList = littleEmojis.sortedByDescending { it.key.length }
        _sortedLittleEmojiKeyList = _sortedLittleEmojiList.map { it.key }
        _emojiGroupState.value = allGroups
        _emojiIndexVersion += 1
    }

    private fun ensureAllLocaleEmojiNamesLoadedLocked() {
        if (_allLocaleEmojiNamesLoaded) return
        val context = _appContext ?: return
        _allLocaleEmojiNamesLoaded = true
        try {
            for (locale in SHIPPED_EMOJI_LOCALES) {
                val config = Configuration(context.resources.configuration).apply { setLocale(locale) }
                val localeResources = context.createConfigurationContext(config).resources
                val emojiKeys: Array<String> = localeResources.getStringArray(R.array.emoji_picker_key_array)
                val emojiNames: Array<String> = localeResources.getStringArray(R.array.emoji_picker_name_array)
                val count = minOf(emojiKeys.size, emojiNames.size)
                for (i in 0 until count) {
                    val emoji = _emojiByKeyMap[emojiKeys[i]] ?: continue
                    _emojiByNameMap.putIfAbsent(emojiNames[i], emoji)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        _sortedLittleEmojiNameList = _emojiByNameMap.keys.sortedByDescending { it.length }
    }

    private fun preloadEmojiGroups(groups: List<EmojiGroup>) {
        val context = synchronized(this) { _appContext } ?: return
        val emojis = groups
            .filter { it.isLittleEmoji || it.supportReaction }
            .flatMap { it.emojis }
        if (emojis.isEmpty()) return

        val pendingEmojis = synchronized(this) {
            emojis.filter { emoji ->
                !_emojiImageCache.containsKey(emoji.key) && _preloadingKeys.add(emoji.key)
            }
        }
        if (pendingEmojis.isEmpty()) return

        runOnMainThread { loadEmojiDrawables(context, pendingEmojis) }
    }

    private fun loadEmojiDrawables(context: Context, emojis: List<Emoji>) {
        emojis.forEach { emoji ->
            try {
                Glide.with(context)
                    .asDrawable()
                    .load(emoji.emojiUrl)
                    .into(object : CustomTarget<Drawable>() {
                        override fun onResourceReady(
                            resource: Drawable,
                            transition: Transition<in Drawable>?
                        ) {
                            synchronized(this@EmojiManager) {
                                if (resource.constantState != null) {
                                    _emojiImageCache[emoji.key] = resource
                                }
                                _preloadingKeys.remove(emoji.key)
                            }
                        }

                        override fun onLoadCleared(placeholder: Drawable?) {
                            synchronized(this@EmojiManager) { _preloadingKeys.remove(emoji.key) }
                        }

                        override fun onLoadFailed(errorDrawable: Drawable?) {
                            synchronized(this@EmojiManager) { _preloadingKeys.remove(emoji.key) }
                        }
                    })
            } catch (e: Exception) {
                e.printStackTrace()
                synchronized(this) { _preloadingKeys.remove(emoji.key) }
            }
        }
    }

    private fun runOnMainThread(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action()
        } else {
            Handler(Looper.getMainLooper()).post(action)
        }
    }

    fun clearImageCache(): EmojiManager {
        synchronized(this) {
            _emojiImageCache.clear()
        }
        return this
    }

    fun getCacheSize(): Int {
        return synchronized(this) { _emojiImageCache.size }
    }
}
