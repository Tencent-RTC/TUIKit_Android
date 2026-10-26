package com.trtc.uikit.roomkit.aitranscription.repository

import android.content.Context
import com.trtc.uikit.roomkit.R
import com.trtc.uikit.roomkit.aitranscription.config.AITranscriptionData
import io.trtc.tuikit.atomicxcore.api.ai.AITranscriberStore
import io.trtc.tuikit.atomicxcore.api.ai.AITranscriberStoreListener
import io.trtc.tuikit.atomicxcore.api.ai.SourceLanguage
import io.trtc.tuikit.atomicxcore.api.ai.TranscriberConfig
import io.trtc.tuikit.atomicxcore.api.ai.TranscriberMessage
import io.trtc.tuikit.atomicxcore.api.ai.TranslationLanguage
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// MARK: - Event

sealed class AISubtitleDataEvent {
    data class Added(val data: AITranscriptionData) : AISubtitleDataEvent()
    data class Updated(val data: AITranscriptionData) : AISubtitleDataEvent()
    data class Completed(val data: AITranscriptionData) : AISubtitleDataEvent()
    object ClearedAll : AISubtitleDataEvent()
}

// MARK: - AITranscriberRepository

class AITranscriberRepository(private val roomID: String) {

    // MARK: - Event Stream

    private val _subtitleEventFlow = MutableSharedFlow<AISubtitleDataEvent>(extraBufferCapacity = 64)
    val subtitleEventFlow: SharedFlow<AISubtitleDataEvent> = _subtitleEventFlow.asSharedFlow()

    // MARK: - State

    private val _subtitleDataMap = mutableMapOf<String, AITranscriptionData>()
    val subtitleDataMap: Map<String, AITranscriptionData> get() = _subtitleDataMap

    private val _orderedSegmentIds = mutableListOf<String>()
    val orderedSegmentIds: List<String> get() = _orderedSegmentIds

    val sourceLanguageList: List<SourceLanguage> = listOf(
        SourceLanguage.CHINESE_ENGLISH,
        SourceLanguage.CHINESE,
        SourceLanguage.ENGLISH,
        SourceLanguage.CANTONESE,
        SourceLanguage.VIETNAMESE,
        SourceLanguage.JAPANESE,
        SourceLanguage.KOREAN,
        SourceLanguage.INDONESIAN,
        SourceLanguage.THAI,
        SourceLanguage.PORTUGUESE,
        SourceLanguage.TURKISH,
        SourceLanguage.ARABIC,
        SourceLanguage.SPANISH,
        SourceLanguage.HINDI,
        SourceLanguage.FRENCH,
        SourceLanguage.MALAY,
        SourceLanguage.FILIPINO,
        SourceLanguage.GERMAN,
        SourceLanguage.ITALIAN,
        SourceLanguage.RUSSIAN,
    )

    val translationLanguageList: List<TranslationLanguage?> = listOf(
        null,
        TranslationLanguage.CHINESE,
        TranslationLanguage.ENGLISH,
        TranslationLanguage.VIETNAMESE,
        TranslationLanguage.JAPANESE,
        TranslationLanguage.KOREAN,
        TranslationLanguage.INDONESIAN,
        TranslationLanguage.THAI,
        TranslationLanguage.PORTUGUESE,
        TranslationLanguage.ARABIC,
        TranslationLanguage.SPANISH,
        TranslationLanguage.FRENCH,
        TranslationLanguage.MALAY,
        TranslationLanguage.GERMAN,
        TranslationLanguage.ITALIAN,
        TranslationLanguage.RUSSIAN,
    )

    private val _selectedSourceLanguage = MutableStateFlow(SourceLanguage.CHINESE_ENGLISH)
    val selectedSourceLanguage: StateFlow<SourceLanguage> = _selectedSourceLanguage.asStateFlow()

    private val _selectedTranslationLanguage = MutableStateFlow<TranslationLanguage?>(TranslationLanguage.ENGLISH)
    val selectedTranslationLanguage: StateFlow<TranslationLanguage?> = _selectedTranslationLanguage.asStateFlow()

    var isTranscriptionStart: Boolean = false
        private set

    private val _isBilingualEnabled = MutableStateFlow(true)
    val isBilingualEnabled: StateFlow<Boolean> = _isBilingualEnabled.asStateFlow()

    fun setBilingualEnabled(enabled: Boolean) {
        _isBilingualEnabled.value = enabled
    }

    // MARK: - Internal

    private val transcriberStore: AITranscriberStore = AITranscriberStore.create(roomID)
    private var currentConfig: TranscriberConfig? = null
    private var subscribeJob: Job? = null
    private val transcriberListener = object : AITranscriberStoreListener() {
        override fun onRealtimeTranscriberStarted(roomID: String, transcriberRobotID: String) {
            if (this@AITranscriberRepository.roomID == roomID) {
                isTranscriptionStart = true
            }
        }

        override fun onRealtimeTranscriberStopped(roomID: String, transcriberRobotID: String, reason: Int) {
            if (this@AITranscriberRepository.roomID == roomID) {
                isTranscriptionStart = false
            }
        }
    }

    init {
        subscribeToTranscriberState()
        transcriberStore.addAITranscriberListener(transcriberListener)
    }

    // MARK: - Transcription Control

    fun startTranscription(
        completion: CompletionHandler? = null,
    ) {
        val translationLanguage = _selectedTranslationLanguage.value
        val translationLanguages = if (translationLanguage != null) mutableListOf(translationLanguage) else mutableListOf()
        val config = TranscriberConfig(
            sourceLanguage = _selectedSourceLanguage.value,
            translationLanguages = translationLanguages,
        )

        transcriberStore.startRealtimeTranscriber(config, completion)
        currentConfig = config
    }

    fun updateTranscriptionSourceLanguage(
        sourceLanguage: SourceLanguage,
        completion: CompletionHandler? = null,
    ) {
        val config = currentConfig ?: run {
            completion?.onFailure(-1, "Transcription not started")
            return
        }
        val updatedConfig = TranscriberConfig(
            sourceLanguage = sourceLanguage,
            translationLanguages = config.translationLanguages,
        )
        _selectedSourceLanguage.value = sourceLanguage
        transcriberStore.updateRealtimeTranscriber(updatedConfig, completion)
        currentConfig = updatedConfig
    }

    fun updateTranscriptionTranslationLanguage(
        translationLanguage: TranslationLanguage?,
        completion: CompletionHandler? = null,
    ) {
        val config = currentConfig ?: run {
            completion?.onFailure(-1, "Transcription not started")
            return
        }
        val translationLanguages = if (translationLanguage != null) mutableListOf(translationLanguage) else mutableListOf()
        val updatedConfig = TranscriberConfig(
            sourceLanguage = config.sourceLanguage,
            translationLanguages = translationLanguages,
        )
        _selectedTranslationLanguage.value = translationLanguage
        transcriberStore.updateRealtimeTranscriber(updatedConfig, completion)
        currentConfig = updatedConfig
    }

    fun stopTranscription(completion: CompletionHandler? = null) {
        transcriberStore.stopRealtimeTranscriber(completion)
        currentConfig = null
    }

    // MARK: - Data Access

    fun getData(segmentId: String): AITranscriptionData? {
        return _subtitleDataMap[segmentId]
    }

    fun displayName(context: Context, sourceLanguage: SourceLanguage): String {
        return when (sourceLanguage) {
            SourceLanguage.CHINESE_ENGLISH -> context.getString(R.string.roomkit_transcription_auto_detect_chinese_english)
            SourceLanguage.CHINESE -> context.getString(R.string.roomkit_transcription_speaking_chinese)
            SourceLanguage.ENGLISH -> context.getString(R.string.roomkit_transcription_speaking_english)
            SourceLanguage.CANTONESE -> context.getString(R.string.roomkit_transcription_speaking_cantonese)
            SourceLanguage.VIETNAMESE -> context.getString(R.string.roomkit_transcription_speaking_vietnamese)
            SourceLanguage.JAPANESE -> context.getString(R.string.roomkit_transcription_speaking_japanese)
            SourceLanguage.KOREAN -> context.getString(R.string.roomkit_transcription_speaking_korean)
            SourceLanguage.INDONESIAN -> context.getString(R.string.roomkit_transcription_speaking_indonesian)
            SourceLanguage.THAI -> context.getString(R.string.roomkit_transcription_speaking_thai)
            SourceLanguage.PORTUGUESE -> context.getString(R.string.roomkit_transcription_speaking_portuguese)
            SourceLanguage.TURKISH -> context.getString(R.string.roomkit_transcription_speaking_turkish)
            SourceLanguage.ARABIC -> context.getString(R.string.roomkit_transcription_speaking_arabic)
            SourceLanguage.SPANISH -> context.getString(R.string.roomkit_transcription_speaking_spanish)
            SourceLanguage.HINDI -> context.getString(R.string.roomkit_transcription_speaking_hindi)
            SourceLanguage.FRENCH -> context.getString(R.string.roomkit_transcription_speaking_french)
            SourceLanguage.MALAY -> context.getString(R.string.roomkit_transcription_speaking_malay)
            SourceLanguage.FILIPINO -> context.getString(R.string.roomkit_transcription_speaking_filipino)
            SourceLanguage.GERMAN -> context.getString(R.string.roomkit_transcription_speaking_german)
            SourceLanguage.ITALIAN -> context.getString(R.string.roomkit_transcription_speaking_italian)
            SourceLanguage.RUSSIAN -> context.getString(R.string.roomkit_transcription_speaking_russian)
            else -> sourceLanguage.name
        }
    }

    fun displayName(context: Context, translationLanguage: TranslationLanguage?): String {
        if (translationLanguage == null) return context.getString(R.string.roomkit_transcription_no_translation)
        return when (translationLanguage) {
            TranslationLanguage.CHINESE -> context.getString(R.string.roomkit_transcription_language_chinese)
            TranslationLanguage.ENGLISH -> context.getString(R.string.roomkit_transcription_language_english)
            TranslationLanguage.VIETNAMESE -> context.getString(R.string.roomkit_transcription_language_vietnamese)
            TranslationLanguage.JAPANESE -> context.getString(R.string.roomkit_transcription_language_japanese)
            TranslationLanguage.KOREAN -> context.getString(R.string.roomkit_transcription_language_korean)
            TranslationLanguage.INDONESIAN -> context.getString(R.string.roomkit_transcription_language_indonesian)
            TranslationLanguage.THAI -> context.getString(R.string.roomkit_transcription_language_thai)
            TranslationLanguage.PORTUGUESE -> context.getString(R.string.roomkit_transcription_language_portuguese)
            TranslationLanguage.ARABIC -> context.getString(R.string.roomkit_transcription_language_arabic)
            TranslationLanguage.SPANISH -> context.getString(R.string.roomkit_transcription_language_spanish)
            TranslationLanguage.FRENCH -> context.getString(R.string.roomkit_transcription_language_french)
            TranslationLanguage.MALAY -> context.getString(R.string.roomkit_transcription_language_malay)
            TranslationLanguage.GERMAN -> context.getString(R.string.roomkit_transcription_language_german)
            TranslationLanguage.ITALIAN -> context.getString(R.string.roomkit_transcription_language_italian)
            TranslationLanguage.RUSSIAN -> context.getString(R.string.roomkit_transcription_language_russian)
            else -> translationLanguage.name
        }
    }

    // MARK: - State Subscription

    private fun subscribeToTranscriberState() {
        subscribeJob?.cancel()
        subscribeJob = CoroutineScope(Dispatchers.Main).launch {
            launch {
                transcriberStore.transcriberState.realtimeMessageList.collect { messageList ->
                    processMessageListUpdate(messageList)
                }
            }
        }
    }

    private fun processMessageListUpdate(messageList: List<TranscriberMessage>) {
        val groups = groupMessages(messageList)
        for (groupData in groups) {
            val groupId = groupData.segmentId
            val existingData = _subtitleDataMap[groupId]
            if (existingData != null) {
                if (groupData == existingData) continue
                _subtitleDataMap[groupId] = groupData
                _subtitleEventFlow.tryEmit(
                    if (groupData.isCompleted) AISubtitleDataEvent.Completed(groupData)
                    else AISubtitleDataEvent.Updated(groupData)
                )
            } else {
                _subtitleDataMap[groupId] = groupData
                _orderedSegmentIds.add(groupId)
                _subtitleEventFlow.tryEmit(AISubtitleDataEvent.Added(groupData))
                if (groupData.isCompleted) {
                    _subtitleEventFlow.tryEmit(AISubtitleDataEvent.Completed(groupData))
                }
            }
        }
    }

    // MARK: - Message Grouping

    /// Groups messages by: filter → sort → merge (same speaker + 60s time window).
    private fun groupMessages(messageList: List<TranscriberMessage>): List<AITranscriptionData> {
        // Step 1: Filter — keep messages with displayable sourceText and valid timestamp
        val filtered = messageList.filter { message ->
            val hasContent = message.sourceText.trim().isNotEmpty()
            val hasValidTimestamp = message.timestamp > 0 && message.timestamp < Long.MAX_VALUE
            hasContent && hasValidTimestamp
        }

        // Step 2: Sort by timestamp ascending
        val sorted = filtered.sortedBy { it.timestamp }

        // Step 3: Group by same speaker + 60s time window
        val groups = mutableListOf<AITranscriptionData>()

        for (message in sorted) {
            val data = convertToSubtitleData(message) ?: continue

            val lastGroup = groups.lastOrNull()
            if (lastGroup != null
                && lastGroup.speakerUserId == data.speakerUserId
                && Math.abs(data.timestamp - lastGroup.timestamp) <= 60
            ) {
                // Merge into existing group
                val mergedSourceText = lastGroup.sourceText + "\n" + data.sourceText
                val mergedTranslationText = when {
                    data.translationText.isEmpty() -> lastGroup.translationText
                    lastGroup.translationText.isEmpty() -> data.translationText
                    else -> lastGroup.translationText + "\n" + data.translationText
                }
                groups[groups.size - 1] = lastGroup.copy(
                    sourceText = mergedSourceText,
                    translationText = mergedTranslationText,
                    isCompleted = data.isCompleted,
                )
            } else {
                // Start a new group (uses this message's segmentId as group ID)
                groups.add(data)
            }
        }

        return groups
    }

    private fun clearAllData() {
        _subtitleDataMap.clear()
        _orderedSegmentIds.clear()
        _subtitleEventFlow.tryEmit(AISubtitleDataEvent.ClearedAll)
    }

    // MARK: - Data Conversion

    private fun convertToSubtitleData(message: TranscriberMessage): AITranscriptionData? {
        val translationText = message.translationTexts.values.firstOrNull() ?: ""
        return AITranscriptionData(
            segmentId = message.segmentId,
            speakerUserId = message.speakerUserId,
            speakerUserName = message.speakerUserName,
            sourceText = message.sourceText,
            translationText = translationText,
            timestamp = message.timestamp,
            isCompleted = message.isCompleted,
        )
    }

    fun destroy() {
        clearAllData()
        subscribeJob?.cancel()
        subscribeJob = null
        transcriberStore.removeAITranscriberListener(transcriberListener)
    }
}
