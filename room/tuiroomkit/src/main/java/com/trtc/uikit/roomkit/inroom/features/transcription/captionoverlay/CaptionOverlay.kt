package com.trtc.uikit.roomkit.inroom.features.transcription.captionoverlay

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.children
import androidx.core.view.isVisible
import com.trtc.uikit.roomkit.R
import com.trtc.uikit.roomkit.base.avatar.RoomAvatar
import com.trtc.uikit.roomkit.base.error.ErrorLocalized
import com.trtc.uikit.roomkit.base.popup.RoomPopup
import com.trtc.uikit.roomkit.base.theme.RoomLoadingDotsView
import com.trtc.uikit.roomkit.base.theme.TypewriterTextView
import com.trtc.uikit.roomkit.inroom.features.transcription.transcriptlist.TranscriptListView
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.ai.AITranscriberStore
import io.trtc.tuikit.atomicxcore.api.ai.TranscriberMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class CaptionOverlay @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    private val rowsContainer: LinearLayout
    private val rowViews: Sequence<CaptionRowView>
        get() = rowsContainer.children.filterIsInstance<CaptionRowView>()
    private var subscribeJob: Job? = null
    private val transcriptPopup = RoomPopup(context)
    private var transcriptContent: TranscriptListView? = null
    private var roomID = ""
    private var isTranscriptionRunning = false

    init {
        LayoutInflater.from(context).inflate(R.layout.roomkit_caption_overlay, this, true)
        rowsContainer = findViewById(R.id.caption_rows)
        findViewById<View>(R.id.caption_close).setOnClickListener { stopTranscription() }
        setOnClickListener { showTranscript() }
        visibility = GONE
    }

    fun init(roomID: String) {
        this.roomID = roomID
        subscribe()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        subscribe()
    }

    override fun onDetachedFromWindow() {
        subscribeJob?.cancel()
        subscribeJob = null
        super.onDetachedFromWindow()
    }

    private fun subscribe() {
        val transcriber = AITranscriberStore.create(roomID)
        subscribeJob?.cancel()
        subscribeJob = CoroutineScope(Dispatchers.Main).launch {
            combine(
                transcriber.transcriberState.realtimeMessageList,
                transcriber.transcriberState.isTranscriptionRunning,
            ) { messages, running -> messages to running }.collect { (messages, running) ->
                isTranscriptionRunning = running
                showMessages(messages)
            }
        }
    }

    private fun stopTranscription() {
        AITranscriberStore.create(roomID).stopTranscription(object : CompletionHandler {
            override fun onSuccess() {}

            override fun onFailure(code: Int, desc: String) {
                post { ErrorLocalized.showError(context, code, desc) }
            }
        })
    }

    private fun showTranscript() {
        if (transcriptContent == null) {
            transcriptContent = TranscriptListView(context).also {
                it.init(roomID)
                transcriptPopup.setContentView(it)
            }
        }
        transcriptPopup.show()
    }

    private fun showMessages(messages: List<TranscriberMessage>) {
        val latestMessages = messages.reversed()
            .distinctBy { it.speakerUserId }
            .filter { message ->
                !message.isCompleted || rowViews.any { it.segmentId == message.segmentId }
            }
            .take(2)
            .reversed()
        val latestUserIds = latestMessages.map { it.speakerUserId }.toSet()
        rowViews.toList().forEach { row ->
            if (row.speakerUserId !in latestUserIds) rowsContainer.removeView(row)
        }
        latestMessages.forEach { message ->
            val boundRow = rowViews.firstOrNull { it.speakerUserId == message.speakerUserId }
            if (boundRow != null) {
                boundRow.bind(message)
                return@forEach
            }
            val newRow = CaptionRowView(context) { hiddenRow ->
                rowsContainer.removeView(hiddenRow)
                updateVisibility()
            }
            rowsContainer.addView(newRow)
            newRow.bind(message)
        }
        updateVisibility()
    }

    private fun updateVisibility() {
        isVisible = isTranscriptionRunning && rowViews.any { it.isVisible }
    }

    private class CaptionRowView(
        context: Context,
        private val onTimeout: (CaptionRowView) -> Unit,
    ) : LinearLayout(context) {
        var speakerUserId: String? = null
            private set
        var segmentId: String? = null
            private set

        private val timeoutRunnable = Runnable { onTimeout(this) }
        private var boundMessage: TranscriberMessage? = null
        private var prefixText = ""
        private var fullText = ""
        private val avatar: RoomAvatar
        private val name: TextView
        private val source: TypewriterTextView
        private val translation: TypewriterTextView
        private val loading: RoomLoadingDotsView

        init {
            orientation = HORIZONTAL
            gravity = Gravity.TOP
            LayoutInflater.from(context).inflate(R.layout.roomkit_caption_overlay_item, this, true)
            avatar = findViewById(R.id.caption_avatar)
            name = findViewById(R.id.caption_name)
            source = findViewById(R.id.caption_source)
            translation = findViewById(R.id.caption_translation)
            loading = findViewById(R.id.caption_transcribing)
            loading.setDotColor(ContextCompat.getColor(context, R.color.roomkit_caption_transcribing))
            source.showCaret = true
            source.showLastLineOnly = true
        }

        fun bind(message: TranscriberMessage) {
            if (boundMessage == message) return
            removeCallbacks(timeoutRunnable)
            val userId = message.speakerUserId
            if (speakerUserId == null) {
                speakerUserId = userId
                segmentId = message.segmentId
            } else if (segmentId != message.segmentId) {
                prefixText = fullText
                segmentId = message.segmentId
            }
            fullText = prefixText + message.sourceText
            val speakerName = message.speakerUserName.ifBlank { userId }
            avatar.text = speakerName
            name.text = speakerName
            val isTranscribing = !message.isCompleted
            source.updateText(userId, fullText, isTranscribing)
            val translatedText = message.translationTexts.values.firstOrNull().orEmpty()
            val showTranslation = translatedText.isNotBlank()
            translation.isVisible = showTranslation
            translation.updateText(message.segmentId, translatedText, showTranslation && isTranscribing)
            loading.isVisible = isTranscribing
            boundMessage = message.copy(translationTexts = message.translationTexts.toMutableMap())
            if (message.isCompleted) postDelayed(timeoutRunnable, 5000L)
        }

        override fun onDetachedFromWindow() {
            removeCallbacks(timeoutRunnable)
            super.onDetachedFromWindow()
        }
    }
}
