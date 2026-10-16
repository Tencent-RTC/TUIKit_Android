package com.trtc.uikit.livekit.common.barrage

import android.content.Context
import androidx.annotation.StringRes
import com.trtc.uikit.livekit.R
import com.trtc.uikit.livekit.common.basic.LiveKitLogger
import com.trtc.uikit.livekit.common.gift.player.LiveGiftComment
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.barrage.BarrageStore
import io.trtc.tuikit.atomicxcore.api.live.LiveAudienceStore
import io.trtc.tuikit.atomicxcore.api.login.LoginStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.UUID

internal class BarrageUIStore(private val context: Context) {
    companion object {
        @StringRes
        fun sendErrorResource(code: Int): Int = when (code) {
            10017, -2381 -> R.string.livekit_comment_muted
            -2380 -> R.string.livekit_comment_all_muted
            10023, 10006, 7008, 9506, -2 -> R.string.livekit_comment_too_frequent
            7015, 80001 -> R.string.livekit_comment_content_rejected
            8001, 80002 -> R.string.livekit_comment_too_long
            6014, 6206, -1002 -> R.string.livekit_comment_login_expired
            6208 -> R.string.livekit_comment_login_elsewhere
            -2101 -> R.string.livekit_comment_not_joined
            10010 -> R.string.livekit_comment_room_unavailable
            10007 -> R.string.livekit_comment_no_permission
            9508, 9512 -> R.string.livekit_comment_network_error
            9510, in 9518..9521, in 9523..9525 -> R.string.livekit_comment_timeout
            10016 -> R.string.livekit_comment_send_rejected
            else -> R.string.livekit_comment_send_failed
        }
    }

    data class Comment(
        val id: String,
        val senderName: String,
        val text: String,
        val level: Int,
        val isHost: Boolean = false,
        val gift: LiveGiftComment? = null,
        val timestamp: Double,
    )

    data class State(val liveID: String = "", val comments: List<Comment> = emptyList())

    private val logger = LiveKitLogger.getComponentLogger("BarrageUIStore")
    private val mutableState = MutableStateFlow(State())
    val state = mutableState.asStateFlow()
    private val mutableSendErrors = MutableSharedFlow<Int>()
    val sendErrors = mutableSendErrors.asSharedFlow()
    private var scope: CoroutineScope? = null
    private var sessionID = UUID.randomUUID()
    private val pendingSends = mutableSetOf<UUID>()
    private var barrageStore: BarrageStore? = null
    private var hostUserID = ""
    private var textComments: List<Comment> = emptyList()
    private var giftComments: List<Comment> = emptyList()

    fun activate(liveID: String, hostUserID: String) {
        if (liveID.isEmpty()) {
            deactivate()
            return
        }
        if (state.value.liveID == liveID && this.hostUserID == hostUserID && barrageStore != null) return
        deactivate()
        this.hostUserID = hostUserID
        mutableState.value = State(liveID)
        val currentSession = sessionID
        val store = BarrageStore.create(liveID)
        barrageStore = store
        val currentScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        scope = currentScope
        currentScope.launch {
            store.barrageState.messageList.collectLatest { barrages ->
                if (sessionID != currentSession) return@collectLatest
                textComments = barrages.takeLast(100).map { barrage ->
                    Comment(
                        id = "${barrage.sequence}-${barrage.timestampInSecond}",
                        senderName = barrage.sender.userName.ifEmpty { barrage.sender.userID }
                            .ifEmpty { context.getString(R.string.livekit_common_viewer) },
                        text = barrage.textContent,
                        level = barrage.sender.level,
                        isHost = hostUserID.isNotEmpty() && barrage.sender.userID == hostUserID,
                        timestamp = barrage.timestampInSecond.toDouble(),
                    )
                }
                rebuildComments()
            }
        }
    }

    fun deactivate() {
        sessionID = UUID.randomUUID()
        pendingSends.clear()
        scope?.cancel()
        scope = null
        barrageStore = null
        hostUserID = ""
        textComments = emptyList()
        giftComments = emptyList()
        mutableState.value = State()
    }

    fun sendComment(text: String) {
        val value = text.trim()
        if (value.isEmpty()) return
        val store = barrageStore ?: return
        val currentScope = scope ?: return
        if (isSelfMessageBanned()) {
            currentScope.launch { mutableSendErrors.emit(sendErrorResource(10017)) }
            return
        }
        val currentSession = sessionID
        val requestID = UUID.randomUUID()
        val liveID = state.value.liveID
        pendingSends.add(requestID)
        store.sendTextMessage(value, null, object : CompletionHandler {
            override fun onSuccess() {
                currentScope.launch {
                    if (sessionID == currentSession) pendingSends.remove(requestID)
                }
            }

            override fun onFailure(code: Int, desc: String) {
                currentScope.launch {
                    if (sessionID != currentSession || !pendingSends.remove(requestID)) return@launch
                    logger.error("sendComment failed, liveID:$liveID, code:$code, desc:$desc")
                    mutableSendErrors.emit(sendErrorResource(code))
                }
            }
        })
    }

    fun isSelfMessageBanned(): Boolean {
        val liveID = state.value.liveID
        val userID = LoginStore.shared.loginState.loginUserInfo.value?.userID.orEmpty()
        if (liveID.isEmpty() || userID.isEmpty()) return false
        return LiveAudienceStore.create(liveID).liveAudienceState.messageBannedUserList.value
            .any { it.userID == userID }
    }

    fun appendGiftComment(gift: LiveGiftComment) {
        if (state.value.liveID.isEmpty() || giftComments.any { it.id == "gift-${gift.id}" }) return
        giftComments = (giftComments + Comment(
            id = "gift-${gift.id}",
            senderName = gift.senderName.ifEmpty { gift.senderID }
                .ifEmpty { context.getString(R.string.livekit_common_viewer) },
            text = "",
            level = gift.senderLevel,
            gift = gift,
            timestamp = System.currentTimeMillis() / 1_000.0,
        )).takeLast(100)
        rebuildComments()
    }

    private fun rebuildComments() {
        mutableState.value = state.value.copy(
            comments = (textComments + giftComments)
                .sortedWith(compareBy({ it.timestamp }, { it.id })).takeLast(100),
        )
    }
}
