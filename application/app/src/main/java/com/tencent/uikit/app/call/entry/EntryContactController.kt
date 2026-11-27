package com.tencent.uikit.app.call.entry

import android.os.Handler
import android.os.Looper
import com.tencent.imsdk.v2.V2TIMManager
import com.tencent.imsdk.v2.V2TIMUserFullInfo
import com.tencent.imsdk.v2.V2TIMValueCallback
import com.tencent.uikit.app.call.common.CallAccessLogger
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.contact.ContactInfo
import io.trtc.tuikit.atomicxcore.api.contact.ContactStore
import io.trtc.tuikit.atomicxcore.api.login.LoginStore
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal data class EntryContact(
    val userID: String,
    val displayName: String,
    val avatarURL: String?,
    val isFriend: Boolean,
) {
    val key: String = userID.lowercase(Locale.ROOT)
}

internal enum class EntrySearchFeedback { EMPTY_QUERY, SELF, NOT_REGISTERED, NETWORK_FAILED, LIMIT, PARTIAL_UNREGISTERED }

internal enum class EntryResultMode { DEFAULT, FRIEND_RESULTS, REMOTE_ID }

internal enum class EntryLoadState { LOADING, FAILED, READY }

internal class EntryContactController(val selection: CallEntrySelection) {
    var onUpdate: (() -> Unit)? = null
    var onFeedback: ((EntrySearchFeedback, String) -> Unit)? = null
    var onQueryConsumed: (() -> Unit)? = null

    private var friends: List<EntryContact> = emptyList()
    private var remoteContact: EntryContact? = null
    private val knownContacts = mutableMapOf<String, EntryContact>()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var subscribeJob: Job? = null
    private var searchToken = 0
    private var loadToken = 0
    private var closed = false

    var submittedQuery = ""
        private set
    var resultMode = EntryResultMode.DEFAULT
        private set
    var loadState = EntryLoadState.LOADING
        private set

    val isFull: Boolean get() = selection.userIDs.size >= CallEntryState.MAX_CALLEE_IDS

    val visibleContacts: List<EntryContact>
        get() = when (resultMode) {
            EntryResultMode.DEFAULT -> orderedFriends()
            EntryResultMode.FRIEND_RESULTS -> matchFriends(submittedQuery)
            EntryResultMode.REMOTE_ID -> listOfNotNull(remoteContact)
        }

    fun start(scope: CoroutineScope) {
        if (subscribeJob != null) return
        subscribeJob = scope.launch {
            ContactStore.shared.state.friendList.collect { friendList ->
                friends = friendList.map { it.toEntryContact() }
                friends.forEach { knownContacts[it.key] = it }
                if (friends.isNotEmpty()) loadState = EntryLoadState.READY
                onUpdate?.invoke()
            }
        }
        loadFriends()
    }

    fun reload() {
        if (closed) return
        loadFriends()
    }

    fun close() {
        closed = true
        searchToken += 1
        loadToken += 1
        subscribeJob?.cancel()
        subscribeJob = null
        mainHandler.removeCallbacksAndMessages(null)
    }

    fun submitSearch(rawQuery: String) {
        if (isFull) return
        val query = rawQuery.trim()
        if (query.isEmpty()) {
            clearSearch()
            onFeedback?.invoke(EntrySearchFeedback.EMPTY_QUERY, "")
            return
        }
        if (isSelfQuery(query)) {
            rejectSearch(EntrySearchFeedback.SELF)
            return
        }
        if (CallEntryState.containsSeparator(query)) {
            addIDs(query)
            return
        }
        if (matchFriends(query).isNotEmpty()) {
            searchToken += 1
            remoteContact = null
            submittedQuery = query
            resultMode = EntryResultMode.FRIEND_RESULTS
            onUpdate?.invoke()
            return
        }
        lookup(listOf(query)) { profiles, failed ->
            val profile = profiles.firstOrNull()
            when {
                failed -> onFeedback?.invoke(EntrySearchFeedback.NETWORK_FAILED, "")
                profile == null -> rejectSearch(EntrySearchFeedback.NOT_REGISTERED)
                else -> {
                    remoteContact = profile
                    submittedQuery = query
                    resultMode = EntryResultMode.REMOTE_ID
                    onUpdate?.invoke()
                }
            }
        }
    }

    fun addIDs(raw: String) {
        if (isSelfQuery(raw)) {
            rejectSearch(EntrySearchFeedback.SELF)
            return
        }
        val ids = CallEntryState.parseIDs(raw)
        if (ids.isEmpty()) return
        lookup(ids) { profiles, failed ->
            if (failed) {
                onFeedback?.invoke(EntrySearchFeedback.NETWORK_FAILED, "")
                return@lookup
            }
            val registered = profiles.associateBy { it.key }
            val hits = ids.mapNotNull { registered[it.lowercase(Locale.ROOT)] }
            val missing = ids.filter { registered[it.lowercase(Locale.ROOT)] == null }
            if (hits.isNotEmpty() && !appendContacts(hits)) return@lookup
            if (missing.isNotEmpty()) {
                onFeedback?.invoke(EntrySearchFeedback.PARTIAL_UNREGISTERED, missing.joinToString("、"))
            }
            consumeQuery()
        }
    }

    fun toggle(contact: EntryContact) {
        if (isSelected(contact.userID)) {
            remove(contact.userID)
            return
        }
        if (!appendContacts(listOf(contact))) return
        if (resultMode != EntryResultMode.DEFAULT) consumeQuery()
    }

    fun addRemote(contact: EntryContact) {
        if (isSelected(contact.userID)) return
        if (appendContacts(listOf(contact))) consumeQuery()
    }

    fun remove(userID: String) {
        selection.userIDs = selection.userIDs.filterNot { CallEntryState.sameID(it, userID) }
        onUpdate?.invoke()
    }

    fun clearSelection() {
        selection.userIDs = emptyList()
        onUpdate?.invoke()
    }

    fun clearSearch() {
        searchToken += 1
        submittedQuery = ""
        remoteContact = null
        resultMode = EntryResultMode.DEFAULT
        onUpdate?.invoke()
    }

    fun isSelected(userID: String): Boolean = selection.userIDs.any { CallEntryState.sameID(it, userID) }

    fun isSelf(userID: String): Boolean {
        val selfID = LoginStore.shared.loginState.loginUserInfo.value?.userID ?: return false
        return CallEntryState.sameID(selfID, userID)
    }

    fun contactFor(userID: String): EntryContact {
        return knownContacts[userID.lowercase(Locale.ROOT)] ?: EntryContact(userID, userID, null, false)
    }

    private fun loadFriends() {
        val token = ++loadToken
        loadState = EntryLoadState.LOADING
        onUpdate?.invoke()
        ContactStore.shared.loadFriends(object : CompletionHandler {
            override fun onSuccess() {
                mainHandler.post {
                    if (closed || token != loadToken) return@post
                    loadState = EntryLoadState.READY
                    onUpdate?.invoke()
                }
            }

            override fun onFailure(code: Int, desc: String) {
                CallAccessLogger.e(TAG, "loadFriends failed: $code")
                mainHandler.post {
                    if (closed || token != loadToken) return@post
                    loadState = EntryLoadState.FAILED
                    onUpdate?.invoke()
                }
            }
        })
    }

    private fun lookup(ids: List<String>, completion: (List<EntryContact>, Boolean) -> Unit) {
        val token = ++searchToken
        V2TIMManager.getInstance().getUsersInfo(ids, object : V2TIMValueCallback<List<V2TIMUserFullInfo>> {
            override fun onSuccess(users: List<V2TIMUserFullInfo>?) {
                deliver(token) {
                    val profiles = users.orEmpty().mapNotNull { it.toEntryContact() }
                    completion(profiles, false)
                }
            }

            override fun onError(code: Int, desc: String?) {
                if (code == ERR_USER_NOT_FOUND) {
                    deliver(token) { completion(emptyList(), false) }
                    return
                }
                CallAccessLogger.e(TAG, "getUsersInfo failed: $code")
                deliver(token) { completion(emptyList(), true) }
            }
        })
    }

    private fun deliver(token: Int, block: () -> Unit) {
        mainHandler.post {
            if (closed || token != searchToken) return@post
            block()
            onUpdate?.invoke()
        }
    }

    private fun appendContacts(contacts: List<EntryContact>): Boolean {
        if (contacts.any { isSelf(it.userID) }) {
            onFeedback?.invoke(EntrySearchFeedback.SELF, "")
            return false
        }
        val next = selection.userIDs.toMutableList()
        contacts.forEach { contact ->
            knownContacts[contact.key] = knownContacts[contact.key]?.takeIf { it.isFriend } ?: contact
            if (next.none { CallEntryState.sameID(it, contact.userID) }) next.add(contact.userID)
        }
        if (next.size > CallEntryState.MAX_CALLEE_IDS) {
            onFeedback?.invoke(EntrySearchFeedback.LIMIT, "")
            return false
        }
        selection.userIDs = next
        onUpdate?.invoke()
        return true
    }

    private fun rejectSearch(feedback: EntrySearchFeedback) {
        clearSearch()
        onFeedback?.invoke(feedback, "")
    }

    private fun consumeQuery() {
        clearSearch()
        onQueryConsumed?.invoke()
    }

    private fun orderedFriends(): List<EntryContact> {
        val rank = HashMap<String, Int>(selection.userIDs.size)
        selection.userIDs.forEachIndexed { index, id -> rank.putIfAbsent(id.lowercase(Locale.ROOT), index) }
        val head = ArrayList<Pair<Int, EntryContact>>()
        val rest = ArrayList<EntryContact>(friends.size)
        friends.forEach { contact ->
            val at = rank[contact.key]
            if (at != null) head.add(at to contact) else rest.add(contact)
        }
        head.sortBy { it.first }
        return head.map { it.second } + rest
    }

    private fun matchFriends(query: String): List<EntryContact> {
        val key = query.trim().lowercase(Locale.ROOT)
        if (key.isEmpty()) return emptyList()
        return friends.filter { it.key.contains(key) || it.displayName.lowercase(Locale.ROOT).contains(key) }
    }

    private fun selfKeys(): Set<String> {
        val info = LoginStore.shared.loginState.loginUserInfo.value
        return listOfNotNull(info?.userID, info?.nickname)
            .map { it.trim().lowercase(Locale.ROOT) }
            .filter { it.isNotEmpty() }
            .toSet()
    }

    private fun isSelfQuery(raw: String): Boolean {
        val keys = selfKeys()
        if (keys.isEmpty()) return false
        return CallEntryState.parseIDs(raw).any { keys.contains(it.lowercase(Locale.ROOT)) }
    }

    private fun ContactInfo.toEntryContact(): EntryContact {
        val name = friendRemark?.takeIf { it.isNotEmpty() }
            ?: nickname?.takeIf { it.isNotEmpty() }
            ?: userID
        return EntryContact(userID, name, avatarURL, true)
    }

    private fun V2TIMUserFullInfo.toEntryContact(): EntryContact? {
        val id = userID?.takeIf { it.isNotEmpty() } ?: return null
        knownContacts[id.lowercase(Locale.ROOT)]?.takeIf { it.isFriend }?.let { return it }
        return EntryContact(
            userID = id,
            displayName = nickName?.takeIf { it.isNotEmpty() } ?: id,
            avatarURL = faceUrl,
            isFriend = false,
        )
    }

    private companion object {
        const val TAG = "EntryContactController"
        const val ERR_USER_NOT_FOUND = 70107
    }
}
