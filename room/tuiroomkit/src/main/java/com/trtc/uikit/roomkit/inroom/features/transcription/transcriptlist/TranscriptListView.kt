package com.trtc.uikit.roomkit.inroom.features.transcription.transcriptlist

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.text.Editable
import android.text.SpannableString
import android.text.Spanned
import android.text.TextWatcher
import android.text.format.DateFormat
import android.text.style.BackgroundColorSpan
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.trtc.uikit.roomkit.R
import com.trtc.uikit.roomkit.base.avatar.RoomAvatar
import com.trtc.uikit.roomkit.base.popup.RoomPopup
import com.trtc.uikit.roomkit.base.theme.RoomLoadingDotsView
import com.trtc.uikit.roomkit.base.toast.RoomToast
import com.trtc.uikit.roomkit.base.theme.TypewriterTextView
import io.trtc.tuikit.atomicxcore.api.ai.AITranscriberStore
import io.trtc.tuikit.atomicxcore.api.ai.TranscriberMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class TranscriptListView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    private val searchInput: EditText
    private val recyclerView: RecyclerView
    private val adapter = TranscriptAdapter()
    private val emptyView: View
    private val emptyText: TextView
    private val jumpButton: View
    private val copyButton: ImageView
    private val settingsPopup = RoomPopup(context)
    private var settingsContent: TranscriptSettingsView? = null
    private var subscribeJob: Job? = null
    private var roomID = ""
    private var messages: List<TranscriberMessage> = emptyList()
    private var displayedMessages: List<TranscriberMessage> = emptyList()
    private var searchWords = ""

    init {
        LayoutInflater.from(context).inflate(R.layout.roomkit_transcript_list, this, true)
        searchInput = findViewById(R.id.transcript_search)
        recyclerView = findViewById(R.id.transcript_recycler)
        emptyView = findViewById(R.id.transcript_empty)
        emptyText = findViewById(R.id.transcript_empty_text)
        jumpButton = findViewById(R.id.transcript_jump)
        copyButton = findViewById(R.id.transcript_copy)
        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.adapter = adapter
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                updateJumpVisibility()
            }
        })
        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                searchWords = s?.toString()?.trim().orEmpty()
                updateMessages(scrollToTop = true)
            }
        })
        copyButton.setOnClickListener { copyTranscript() }
        findViewById<View>(R.id.transcript_settings).setOnClickListener { showSettings() }
        jumpButton.setOnClickListener { scrollToLatest() }
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
            transcriber.transcriberState.realtimeMessageList.collect { list ->
                messages = list
                updateMessages()
            }
        }
    }

    private fun updateMessages(scrollToTop: Boolean = false) {
        val wasAtBottom = !recyclerView.canScrollVertically(1)
        displayedMessages = if (searchWords.isEmpty()) {
            messages
        } else {
            messages.filter { message ->
                message.sourceText.contains(searchWords, ignoreCase = true)
                        || message.translationTexts.values.any { it.contains(searchWords, ignoreCase = true) }
            }
        }
        val isEmpty = displayedMessages.isEmpty()
        recyclerView.visibility = if (isEmpty) GONE else VISIBLE
        emptyView.visibility = if (isEmpty) VISIBLE else GONE
        val emptyTextRes = if (searchWords.isNotEmpty()) {
            R.string.roomkit_transcription_no_match
        } else {
            R.string.roomkit_transcription_empty_hint
        }
        emptyText.setText(emptyTextRes)
        val copyDescriptionRes = if (searchWords.isEmpty()) {
            R.string.roomkit_transcription_copy_all
        } else {
            R.string.roomkit_transcription_copy_results
        }
        copyButton.contentDescription = context.getString(copyDescriptionRes)
        adapter.updateData(displayedMessages, searchWords) {
            recyclerView.post {
                when {
                    isEmpty -> Unit
                    scrollToTop -> recyclerView.scrollToPosition(0)
                    searchWords.isEmpty() && wasAtBottom -> recyclerView.scrollToPosition(displayedMessages.size - 1)
                }
                updateJumpVisibility()
            }
        }
    }

    private fun updateJumpVisibility() {
        val canScrollDown = displayedMessages.isNotEmpty() && recyclerView.canScrollVertically(1)
        jumpButton.visibility = if (canScrollDown) VISIBLE else GONE
    }

    private fun scrollToLatest() {
        if (adapter.itemCount == 0) return
        recyclerView.smoothScrollToPosition(adapter.itemCount - 1)
        recyclerView.post { updateJumpVisibility() }
    }

    private fun copyTranscript() {
        val text = displayedMessages.joinToString("\n") { message ->
            val translatedText = translatedText(message)
            val translation = if (translatedText.isEmpty()) "" else "\n$translatedText"
            val speakerName = message.speakerUserName.ifBlank { message.speakerUserId }
            "[${transcriptTime(message.timestamp)}] $speakerName：${message.sourceText}$translation"
        }
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.roomkit_caption_title), text))
        RoomToast.show(
            context,
            context.getString(
                if (searchWords.isEmpty()) R.string.roomkit_transcription_copied_all
                else R.string.roomkit_transcription_copied_results,
                displayedMessages.size.toString(),
            ),
        )
    }

    private fun showSettings() {
        if (settingsContent == null) {
            settingsContent = TranscriptSettingsView(context).also {
                it.init(roomID)
                settingsPopup.heightFraction = 0.60f
                settingsPopup.setContentView(it)
            }
        }
        settingsPopup.show()
    }

    class TranscriptAdapter : ListAdapter<TranscriberMessage, TranscriptAdapter.TranscriptHolder>(TranscriptDiffCallback) {

        private var searchWords = ""

        fun updateData(messages: List<TranscriberMessage>, searchWords: String, onCommitted: () -> Unit) {
            val refreshHighlights = this.searchWords != searchWords
            this.searchWords = searchWords
            if (refreshHighlights) notifyItemRangeChanged(0, currentList.size)
            submitList(messages.toList(), onCommitted)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TranscriptHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.roomkit_transcript_item, parent, false)
            return TranscriptHolder(view)
        }

        override fun onBindViewHolder(holder: TranscriptHolder, position: Int) {
            holder.bind(currentList[position], searchWords)
        }

        class TranscriptHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val avatar: RoomAvatar = itemView.findViewById(R.id.transcript_avatar)
            private val name: TextView = itemView.findViewById(R.id.transcript_name)
            private val time: TextView = itemView.findViewById(R.id.transcript_time)
            private val source: TypewriterTextView = itemView.findViewById(R.id.transcript_source)
            private val sourceLoading: RoomLoadingDotsView =
                itemView.findViewById(R.id.transcript_source_transcribing)
            private val translationContainer: LinearLayout =
                itemView.findViewById(R.id.transcript_translation_container)
            private val translation: TypewriterTextView = itemView.findViewById(R.id.transcript_translation)
            private val translationLoading: RoomLoadingDotsView =
                itemView.findViewById(R.id.transcript_translation_transcribing)

            fun bind(message: TranscriberMessage, searchWords: String) {
                val context = itemView.context
                val speakerName = message.speakerUserName.ifBlank { message.speakerUserId }
                avatar.text = speakerName
                name.text = speakerName
                time.text = transcriptTime(message.timestamp)
                source.formatText = { highlighted(it, searchWords) }
                translation.formatText = { highlighted(it, searchWords) }
                val isTranscribing = !message.isCompleted
                source.updateText(message.segmentId, message.sourceText, isTranscribing)
                sourceLoading.visibility = if (isTranscribing) View.VISIBLE else View.GONE
                val translatedText = translatedText(message)
                val showTranslation = translatedText.isNotBlank()
                translationContainer.visibility = if (showTranslation) View.VISIBLE else View.GONE
                translation.updateText(message.segmentId, translatedText, showTranslation && isTranscribing)
                translationLoading.visibility =
                    if (showTranslation && isTranscribing) View.VISIBLE else View.GONE
                val loadingColor = ContextCompat.getColor(context, R.color.roomkit_fg_tertiary)
                sourceLoading.setDotColor(loadingColor)
                translationLoading.setDotColor(loadingColor)
            }

            private fun highlighted(text: String, searchWords: String): CharSequence {
                val keyword = searchWords.trim()
                if (keyword.isEmpty()) return text
                val result = SpannableString(text)
                val highlightColor = ContextCompat.getColor(itemView.context, R.color.roomkit_accent_border)
                var start = text.indexOf(keyword, startIndex = 0, ignoreCase = true)
                while (start >= 0) {
                    result.setSpan(
                        BackgroundColorSpan(highlightColor),
                        start,
                        start + keyword.length,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                    )
                    start = text.indexOf(keyword, startIndex = start + keyword.length, ignoreCase = true)
                }
                return result
            }
        }
    }
}

private object TranscriptDiffCallback : DiffUtil.ItemCallback<TranscriberMessage>() {
    override fun areItemsTheSame(oldItem: TranscriberMessage, newItem: TranscriberMessage): Boolean =
        oldItem.segmentId == newItem.segmentId

    override fun areContentsTheSame(oldItem: TranscriberMessage, newItem: TranscriberMessage): Boolean =
        oldItem == newItem
}

private fun transcriptTime(timestamp: Long): String = DateFormat.format("HH:mm", timestamp).toString()

private fun translatedText(message: TranscriberMessage): String =
    message.translationTexts.values.firstOrNull().orEmpty()
