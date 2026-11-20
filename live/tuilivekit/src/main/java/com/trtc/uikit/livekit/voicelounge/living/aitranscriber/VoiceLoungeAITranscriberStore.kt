package com.trtc.uikit.livekit.voicelounge.living.aitranscriber

import com.trtc.uikit.livekit.common.log.LiveKitLog
import com.trtc.uikit.livekit.voicelounge.VoiceLoungeBag
import com.trtc.uikit.livekit.voicelounge.VoiceLoungeMain
import com.trtc.uikit.livekit.voicelounge.VoiceLoungeSignal
import com.trtc.uikit.livekit.voicelounge.addTo
import com.trtc.uikit.livekit.voicelounge.settings.VoiceLoungeSettings
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.ai.AITranscriberStore
import io.trtc.tuikit.atomicxcore.api.ai.SourceLanguage
import io.trtc.tuikit.atomicxcore.api.ai.TranscriberMessage
import io.trtc.tuikit.atomicxcore.api.ai.TranscriptionConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

internal class VoiceLoungeAITranscriberStore(private val settings: VoiceLoungeSettings) {
    data class Line(
        val id: String,
        val speaker: String,
        val text: String,
    )

    val changes = VoiceLoungeSignal()
    var lines: List<Line> = emptyList()
        private set
    val isEnabled: Boolean get() = settings.isTranscriberEnabled

    private var store: AITranscriberStore? = null
    private var liveID = ""
    private var isTranscribing = false
    private var lastEnabled = settings.isTranscriberEnabled
    private var scope: CoroutineScope? = null
    private val bindings = VoiceLoungeBag()
    private val logger = LiveKitLog.getVoiceRoomLogger("VoiceLoungeAITranscriberStore")

    init {
        settings.changes.subscribe {
            val enabled = settings.isTranscriberEnabled
            if (enabled == lastEnabled) return@subscribe
            lastEnabled = enabled
            syncTranscription()
            changes.send()
        }.addTo(bindings)
    }

    fun activate(liveID: String) {
        if (liveID.isEmpty() || store != null) return
        this.liveID = liveID
        val store = AITranscriberStore.create(liveID)
        this.store = store
        val activeScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        scope = activeScope
        activeScope.launch { store.transcriberState.realtimeMessageList.collect { apply(it) } }
        syncTranscription()
    }

    fun deactivate() {
        scope?.cancel()
        scope = null
        if (isTranscribing) store?.stopTranscription(null)
        isTranscribing = false
        store = null
        liveID = ""
        clearLines()
    }

    fun release() {
        deactivate()
        bindings.clear()
    }

    private fun syncTranscription() {
        val store = store ?: return
        if (isEnabled && !isTranscribing) {
            isTranscribing = true
            val liveID = liveID
            store.startTranscription(
                SourceLanguage.CHINESE_ENGLISH,
                TranscriptionConfig(),
                object : CompletionHandler {
                    override fun onSuccess() = Unit

                    override fun onFailure(code: Int, desc: String) {
                        logger.error("startTranscription failed liveID=$liveID code=$code message=$desc")
                        VoiceLoungeMain.post { isTranscribing = false }
                    }
                },
            )
        } else if (!isEnabled && isTranscribing) {
            isTranscribing = false
            store.stopTranscription(null)
            clearLines()
        }
    }

    private fun clearLines() {
        if (lines.isEmpty()) return
        lines = emptyList()
        changes.send()
    }

    private fun apply(list: List<TranscriberMessage>) {
        val next = list.sortedBy { it.timestamp }.takeLast(MAX_LINES).mapNotNull { message ->
            val text = message.sourceText.trim()
            if (text.isEmpty()) return@mapNotNull null
            Line(message.segmentId, message.speakerUserName.ifEmpty { message.speakerUserId }, text)
        }
        if (next == lines) return
        lines = next
        changes.send()
    }

    companion object {
        const val MAX_LINES = 3
    }
}
