package com.trtc.uikit.livekit.voicelounge.living.giftrank

import com.trtc.uikit.livekit.common.log.LiveKitLog
import com.trtc.uikit.livekit.voicelounge.VoiceLoungeMain
import com.trtc.uikit.livekit.voicelounge.VoiceLoungeSignal
import com.trtc.uikit.livekit.voicelounge.living.VoiceLoungeRole
import io.trtc.tuikit.atomicxcore.api.gift.Gift
import io.trtc.tuikit.atomicxcore.api.gift.GiftListener
import io.trtc.tuikit.atomicxcore.api.gift.GiftStore
import io.trtc.tuikit.atomicxcore.api.live.LiveListStore
import io.trtc.tuikit.atomicxcore.api.live.LiveUserInfo
import io.trtc.tuikit.atomicxcore.api.live.MetaDataCompletionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

internal class VoiceLoungeGiftRankStore {
    data class Entry(
        val userID: String,
        val name: String,
        val avatarURL: String,
        val coins: Long,
    ) {
        fun toJson(): JSONObject = JSONObject().put("u", userID).put("n", name).put("a", avatarURL).put("c", coins)

        companion object {
            fun fromJson(json: JSONObject): Entry? {
                if (!json.has("u") || !json.has("c")) return null
                return Entry(
                    userID = json.optString("u"),
                    name = json.optString("n"),
                    avatarURL = json.optString("a"),
                    coins = json.optLong("c"),
                )
            }
        }
    }

    var onPublish: (() -> Unit)? = null
    val changes = VoiceLoungeSignal()

    private var liveID = ""
    private var store: GiftStore? = null
    private var role = VoiceLoungeRole.AUDIENCE
    private var hostUserID = ""
    private val localTotals = LinkedHashMap<String, Entry>()
    private var remoteRanking: List<Entry>? = null
    private var localGiftCount = 0
    private var remoteGiftCount: Int? = null
    private var scope: CoroutineScope? = null
    private var refreshToken = 0
    private val logger = LiveKitLog.getVoiceRoomLogger("VoiceLoungeGiftRankStore")
    private val publishRunnable = Runnable { onPublish?.invoke() }
    private val refreshRunnable = Runnable { refresh() }

    private val giftListener = object : GiftListener() {
        override fun onReceiveGiftWithReceiver(
            liveID: String,
            gift: Gift,
            count: Int,
            sender: LiveUserInfo,
            receiver: LiveUserInfo?,
        ) {
            VoiceLoungeMain.post { handle(gift, count, sender, receiver) }
        }
    }

    val ranking: List<Entry>
        get() {
            val remote = remoteRanking
            if (role == VoiceLoungeRole.AUDIENCE && remote != null) return remote
            return localRanking
        }

    private val localRanking: List<Entry> get() = localTotals.values.sortedByDescending { it.coins }

    val giftCount: Int
        get() {
            val remote = remoteGiftCount
            if (role == VoiceLoungeRole.AUDIENCE && remote != null) return maxOf(remote, localGiftCount)
            return localGiftCount
        }

    fun activate(liveID: String, role: VoiceLoungeRole, hostUserID: String) {
        if (liveID.isEmpty() || store != null) return
        this.role = role
        this.liveID = liveID
        this.hostUserID = hostUserID
        val store = GiftStore.create(liveID)
        this.store = store
        store.addGiftListener(giftListener)
        if (role != VoiceLoungeRole.AUDIENCE) return
        val activeScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        scope = activeScope
        activeScope.launch {
            LiveListStore.shared().liveState.currentLive.filter { it.liveID == liveID }.map { it.metaData }
                .collect { applyRemote(it) }
        }
        refresh()
    }

    fun updateHostUserID(userID: String) {
        if (userID.isEmpty() || userID == hostUserID) return
        hostUserID = userID
    }

    fun deactivate() {
        store?.removeGiftListener(giftListener)
        scope?.cancel()
        scope = null
        VoiceLoungeMain.cancel(publishRunnable)
        VoiceLoungeMain.cancel(refreshRunnable)
        refreshToken += 1
        store = null
        liveID = ""
        localTotals.clear()
        remoteRanking = null
        localGiftCount = 0
        remoteGiftCount = null
        changes.send()
    }

    fun refresh() {
        if (role != VoiceLoungeRole.AUDIENCE || liveID.isEmpty()) return
        refreshToken += 1
        val token = refreshToken
        val liveID = liveID
        LiveListStore.shared().queryMetaData(
            listOf(META_KEY, COUNT_META_KEY),
            object : MetaDataCompletionHandler {
                override fun onSuccess(metaData: HashMap<String, String>) {
                    VoiceLoungeMain.post { if (token == refreshToken) applyRemote(metaData) }
                }

                override fun onFailure(code: Int, desc: String) {
                    logger.error("query gift rank failed liveID=$liveID code=$code message=$desc")
                }
            },
        )
    }

    private fun applyRemote(metaData: Map<String, String>) {
        if (role != VoiceLoungeRole.AUDIENCE) return
        val count = metaData[COUNT_META_KEY]?.toIntOrNull()
        if (count != null && count != remoteGiftCount) {
            remoteGiftCount = count
            changes.send()
        }
        val raw = metaData[META_KEY] ?: return
        val entries = runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { array.optJSONObject(it)?.let(Entry::fromJson) }
        }.getOrNull() ?: return
        val sorted = entries.sortedByDescending { it.coins }
        if (sorted == remoteRanking) return
        remoteRanking = sorted
        changes.send()
    }

    fun publishedMetaData(): Map<String, String> {
        val entries = localRanking.take(MAX_PUBLISHED_ENTRIES).toMutableList()
        while (true) {
            val json = JSONArray().apply { entries.forEach { put(it.toJson()) } }.toString()
            if (json.toByteArray(Charsets.UTF_8).size <= MAX_PUBLISHED_BYTES || entries.isEmpty()) {
                return mapOf(META_KEY to json, COUNT_META_KEY to localGiftCount.toString())
            }
            entries.removeAt(entries.lastIndex)
        }
    }

    private fun handle(gift: Gift, count: Int, sender: LiveUserInfo, receiver: LiveUserInfo?) {
        if (store == null) return
        val receiverID = receiver?.userID.orEmpty()
        val receivedByHost = receiverID.isEmpty() || hostUserID.isEmpty() || receiverID == hostUserID
        if (!receivedByHost || sender.userID.isEmpty()) return
        val add = gift.coins * count
        localGiftCount += count
        val existing = localTotals[sender.userID]
        localTotals[sender.userID] = existing?.copy(coins = existing.coins + add) ?: Entry(
            userID = sender.userID,
            name = sender.userName.ifEmpty { sender.userID },
            avatarURL = sender.avatarURL,
            coins = add,
        )
        changes.send()
        when (role) {
            VoiceLoungeRole.HOST -> schedule(publishRunnable, PUBLISH_DELAY_MS)
            VoiceLoungeRole.AUDIENCE -> schedule(refreshRunnable, REFRESH_DELAY_MS)
        }
    }

    private fun schedule(runnable: Runnable, delayMs: Long) {
        VoiceLoungeMain.cancel(runnable)
        VoiceLoungeMain.postDelayed(delayMs, runnable)
    }

    companion object {
        const val META_KEY = "vl_rank"
        const val COUNT_META_KEY = "vl_gift_count"
        private const val MAX_PUBLISHED_ENTRIES = 20
        private const val MAX_PUBLISHED_BYTES = 3800
        private const val PUBLISH_DELAY_MS = 1_000L
        private const val REFRESH_DELAY_MS = 2_500L
    }
}
