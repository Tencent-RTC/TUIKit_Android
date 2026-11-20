package com.trtc.uikit.livekit.voicelounge.living.aitranscriber

import com.trtc.uikit.livekit.common.log.LiveKitLog
import com.trtc.uikit.livekit.voicelounge.VoiceLoungeBag
import com.trtc.uikit.livekit.voicelounge.VoiceLoungeMain
import com.trtc.uikit.livekit.voicelounge.VoiceLoungeSignal
import com.trtc.uikit.livekit.voicelounge.addTo
import com.trtc.uikit.livekit.voicelounge.settings.VoiceLoungeSettings
import com.trtc.uikit.livekit.voicelounge.settings.VoiceLoungeTranscriberScale
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.ai.AITranscriberStore
import io.trtc.tuikit.atomicxcore.api.ai.SourceLanguage
import io.trtc.tuikit.atomicxcore.api.ai.TranscriberMessage
import io.trtc.tuikit.atomicxcore.api.ai.TranscriptionConfig
import io.trtc.tuikit.atomicxcore.api.ai.TranslationLanguage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

internal class VoiceLoungeAITranscriberStore(
    private val settings: VoiceLoungeSettings,
) {
    data class Line(
        val speakerId: String,
        val speaker: String,
        val translated: String,
        val source: String,
        val updatedAtMillis: Long,
    )

    val changes = VoiceLoungeSignal()

    var lines: List<Line> = emptyList()
        private set

    var isHintActive: Boolean = false
        private set

    val isEnabled: Boolean get() = settings.isTranscriberEnabled
    val showOriginal: Boolean get() = settings.transcriberShowOriginal
    val scale: VoiceLoungeTranscriberScale get() = settings.transcriberScale
    val targetLanguage: TranslationLanguage get() = settings.transcriberLanguage

    val isVisible: Boolean get() = isEnabled

    private var store: AITranscriberStore? = null
    private var liveID = ""
    private var isTranscribing = false
    private var lastEnabled = settings.isTranscriberEnabled
    private var lastLanguage = settings.transcriberLanguage
    private var scope: CoroutineScope? = null
    private val bindings = VoiceLoungeBag()
    private val logger = LiveKitLog.getVoiceRoomLogger("VoiceLoungeAITranscriberStore")

    init {
        settings.changes.subscribe {
            val enabled = settings.isTranscriberEnabled
            val language = settings.transcriberLanguage
            val changedEnable = enabled != lastEnabled
            val changedLanguage = language != lastLanguage
            if (!changedEnable && !changedLanguage) {
                changes.send()
                return@subscribe
            }
            lastEnabled = enabled
            lastLanguage = language
            syncTranscription()
            if (!enabled) clearLines()
            if (changedEnable && enabled) startHint()
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
        activeScope.launch { cleanupLoop() }
        syncTranscription()
        if (isEnabled) startHint()
    }

    fun deactivate() {
        scope?.cancel()
        scope = null
        if (isTranscribing) store?.stopTranscription(null)
        isTranscribing = false
        store = null
        liveID = ""
        isHintActive = false
        clearLines()
    }

    fun release() {
        deactivate()
        bindings.clear()
    }

    fun dismissHint() {
        if (!isHintActive) return
        isHintActive = false
        changes.send()
    }

    private fun startHint() {
        val currentScope = scope ?: return
        if (isHintActive) return
        isHintActive = true
        changes.send()
        currentScope.launch {
            delay(HINT_DURATION_MS)
            if (!isActive) return@launch
            if (!isHintActive) return@launch
            isHintActive = false
            changes.send()
        }
    }

    private fun syncTranscription() {
        val store = store ?: return
        if (isEnabled && !isTranscribing) {
            isTranscribing = true
            val liveID = liveID
            store.startTranscription(
                SourceLanguage.CHINESE_ENGLISH,
                TranscriptionConfig(true),
                object : CompletionHandler {
                    override fun onSuccess() = Unit

                    override fun onFailure(code: Int, desc: String) {
                        logger.error("startTranscription failed liveID=$liveID code=$code message=$desc")
                        VoiceLoungeMain.post { isTranscribing = false }
                    }
                },
            )
        } else if (isEnabled && isTranscribing) {
            store.updateTranscription(SourceLanguage.CHINESE_ENGLISH, TranscriptionConfig(true), null)
        } else if (!isEnabled && isTranscribing) {
            isTranscribing = false
            store.stopTranscription(null)
        }
    }

    private fun clearLines() {
        if (lines.isEmpty()) return
        lines = emptyList()
        changes.send()
    }

    private suspend fun cleanupLoop() {
        while (true) {
            delay(CLEANUP_INTERVAL_MS)
            if (lines.isEmpty()) continue
            val cutoff = System.currentTimeMillis() - LINE_TTL_MS
            val filtered = lines.filter { it.updatedAtMillis >= cutoff }
            if (filtered.size != lines.size) {
                lines = filtered
                changes.send()
            }
        }
    }

    private fun apply(list: List<TranscriberMessage>) {
        if (!isEnabled) return
        val sorted = list.sortedBy { it.timestamp }
        val now = System.currentTimeMillis()
        val latestBySpeaker = LinkedHashMap<String, Line>()
        for (line in lines) latestBySpeaker[line.speakerId] = line.copy()
        for (message in sorted) {
            val source = message.sourceText.trim()
            val translated = message.translationTexts[targetLanguage]?.trim().orEmpty()
            val primary = if (translated.isNotEmpty()) translated else source
            if (primary.isEmpty()) continue
            val speakerId = message.speakerUserId.ifEmpty { message.speakerUserName }
            if (speakerId.isEmpty()) continue
            val display = message.speakerUserName.ifEmpty { message.speakerUserId }
            latestBySpeaker.remove(speakerId)
            latestBySpeaker[speakerId] = Line(
                speakerId = speakerId,
                speaker = display,
                translated = translated,
                source = source,
                updatedAtMillis = now,
            )
        }
        val lastTwo = latestBySpeaker.values.toList().takeLast(MAX_SPEAKERS)
        if (lastTwo == lines) return
        lines = lastTwo
        if (isHintActive) {
            isHintActive = false
        }
        changes.send()
    }

    companion object {
        const val MAX_SPEAKERS = 2
        const val LINE_TTL_MS = 3_000L
        private const val CLEANUP_INTERVAL_MS = 500L
        private const val HINT_DURATION_MS = 3_000L
    }
}
