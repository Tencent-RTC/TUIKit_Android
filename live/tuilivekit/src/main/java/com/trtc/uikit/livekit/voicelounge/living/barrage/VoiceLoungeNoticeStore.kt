package com.trtc.uikit.livekit.voicelounge.living.barrage

import com.trtc.uikit.livekit.R
import com.trtc.uikit.livekit.voicelounge.living.VoiceLoungeRole
import com.trtc.uikit.livekit.voicelounge.vlString
import io.trtc.tuikit.atomicxcore.api.barrage.Barrage
import io.trtc.tuikit.atomicxcore.api.barrage.BarrageStore
import io.trtc.tuikit.atomicxcore.api.barrage.BarrageType
import io.trtc.tuikit.atomicxcore.api.live.LiveListStore
import io.trtc.tuikit.atomicxcore.api.live.LiveUserInfo
import io.trtc.tuikit.atomicxcore.api.login.LoginStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

internal class VoiceLoungeNoticeStore {
    private var liveID = ""
    private var role = VoiceLoungeRole.AUDIENCE
    private var isActive = false
    private val pendingNotices = mutableListOf<String>()
    private var scope: CoroutineScope? = null
    private var sequence = 0L
    private var openingTopic = ""
    private var isOpeningTopicShown = false

    fun activate(liveID: String, role: VoiceLoungeRole, topic: String) {
        deactivate()
        if (liveID.isEmpty()) return
        this.liveID = liveID
        this.role = role
        isActive = true
        append(vlString(R.string.livekit_voiceroom_notice_civility))
        append(vlString(R.string.livekit_voiceroom_notice_no_ads))
        openingTopic = topic
        if (role == VoiceLoungeRole.HOST) showOpeningTopic()
        if (pendingNotices.isEmpty()) return
        val activeScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        scope = activeScope
        activeScope.launch {
            LiveListStore.shared().liveState.currentLive.first {
                it.liveID == liveID && it.liveOwner.userID.isNotEmpty()
            }
            flushPending()
        }
    }

    fun updateTopic(topic: String) {
        if (!isActive || isOpeningTopicShown) return
        openingTopic = topic
    }

    fun showOpeningTopic() {
        if (!isActive || isOpeningTopicShown) return
        isOpeningTopicShown = true
        if (openingTopic.isNotEmpty()) append(vlString(R.string.livekit_voiceroom_notice_topic, openingTopic))
    }

    fun deactivate() {
        isActive = false
        liveID = ""
        openingTopic = ""
        isOpeningTopicShown = false
        pendingNotices.clear()
        scope?.cancel()
        scope = null
    }

    private fun append(text: String) {
        val host = hostSender()
        if (host == null) {
            pendingNotices.add(text)
            return
        }
        appendTip(text, host)
    }

    private fun flushPending() {
        if (!isActive) return
        val host = hostSender() ?: return
        val notices = pendingNotices.toList()
        pendingNotices.clear()
        notices.forEach { appendTip(it, host) }
    }

    private fun appendTip(text: String, sender: LiveUserInfo) {
        sequence += 1
        val barrage = Barrage().apply {
            this.liveID = this@VoiceLoungeNoticeStore.liveID
            this.sender = sender
            this.sequence = -this@VoiceLoungeNoticeStore.sequence
            timestampInSecond = System.currentTimeMillis() / 1000
            messageType = BarrageType.TEXT
            textContent = text
        }
        BarrageStore.create(liveID).appendLocalTip(barrage)
    }

    private fun hostSender(): LiveUserInfo? {
        if (role == VoiceLoungeRole.HOST) {
            val profile = LoginStore.shared.loginState.loginUserInfo.value ?: return null
            if (profile.userID.isEmpty()) return null
            return LiveUserInfo().apply {
                userID = profile.userID
                userName = profile.nickname.orEmpty()
                avatarURL = profile.avatarURL.orEmpty()
                level = profile.level ?: 0
            }
        }
        val live = LiveListStore.shared().liveState.currentLive.value
        if (live.liveID != liveID || live.liveOwner.userID.isEmpty()) return null
        return live.liveOwner
    }
}
