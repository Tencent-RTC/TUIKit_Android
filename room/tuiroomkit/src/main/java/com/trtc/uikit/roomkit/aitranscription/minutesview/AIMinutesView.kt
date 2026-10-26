package com.trtc.uikit.roomkit.aitranscription.minutesview

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.ImageView
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.trtc.uikit.roomkit.R
import com.trtc.uikit.roomkit.base.utils.dpToPx
import com.trtc.uikit.roomkit.aitranscription.config.AIMinutesConfig
import com.trtc.uikit.roomkit.aitranscription.config.DisplayMode
import com.trtc.uikit.roomkit.aitranscription.repository.AISubtitleDataEvent
import com.trtc.uikit.roomkit.aitranscription.repository.AITranscriberRepository
import io.trtc.tuikit.atomicxcore.api.ai.TranslationLanguage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

interface AIMinutesViewListener {
    fun onMinutesViewBackClicked()
}

/**
 * Scrollable list view displaying all AI transcription minutes entries.
 */
class AIMinutesView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : ConstraintLayout(context, attrs, defStyleAttr) {

    // MARK: - Properties

    private var config: AIMinutesConfig = AIMinutesConfig.default(context)
    var listener: AIMinutesViewListener? = null
    private var repository: AITranscriberRepository? = null
    private var subscribeJob: Job? = null
    private var adapter: AIMinutesAdapter? = null

    private var isUserDragging = false
    private var isNearBottom = true

    // MARK: - UI Elements

    private val backButtonContainer: ConstraintLayout
    private val recyclerView: RecyclerView

    init {
        LayoutInflater.from(context).inflate(R.layout.roomkit_view_ai_minutes, this, true)

        backButtonContainer = findViewById(R.id.cl_back_button)
        recyclerView = findViewById(R.id.rv_minutes)

        backButtonContainer.setOnClickListener { listener?.onMinutesViewBackClicked() }

        recyclerView.setHasFixedSize(false)
        recyclerView.layoutManager = LinearLayoutManager(context)

        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(rv: RecyclerView, newState: Int) {
                when (newState) {
                    RecyclerView.SCROLL_STATE_DRAGGING -> isUserDragging = true
                    RecyclerView.SCROLL_STATE_IDLE -> {
                        isUserDragging = false
                        updateAutoScrollState()
                    }
                }
            }
        })
    }

    // MARK: - Configuration

    fun bindRepository(repository: AITranscriberRepository) {
        this.repository = repository
        subscribeJob?.cancel()

        adapter = AIMinutesAdapter(repository, config)
        recyclerView.adapter = adapter
        adapter?.syncFromRepository(repository)

        subscribeJob = CoroutineScope(Dispatchers.Main).launch {
            launch {
                repository.subtitleEventFlow.collect { event ->
                    handleEvent(event)
                }
            }
            launch {
                repository.isBilingualEnabled.collect { isBilingual ->
                    config = config.copy(
                        displayMode = resolveDisplayMode(isBilingual, repository.selectedTranslationLanguage.value)
                    )
                    adapter?.updateConfig(config)
                    adapter?.notifyDataSetChanged()
                }
            }
            launch {
                repository.selectedTranslationLanguage.collect { translationLanguage ->
                    config = config.copy(
                        displayMode = resolveDisplayMode(repository.isBilingualEnabled.value, translationLanguage)
                    )
                    adapter?.updateConfig(config)
                    adapter?.notifyDataSetChanged()
                }
            }
        }

        if ((adapter?.segmentCount ?: 0) > 0) {
            post { scrollToBottom(animated = false) }
        }
    }

    fun configure(config: AIMinutesConfig) {
        this.config = config
        setBackgroundColor(config.backgroundColor)
        recyclerView.setPadding(0, dpToPx(config.listPaddingTopDp), 0, dpToPx(config.listPaddingBottomDp))
        adapter?.updateConfig(config)
        adapter?.notifyDataSetChanged()
    }

    private fun resolveDisplayMode(isBilingual: Boolean, translationLanguage: TranslationLanguage?): DisplayMode {
        if (translationLanguage == null) return DisplayMode.SOURCE_ONLY
        return if (isBilingual) DisplayMode.DUAL else DisplayMode.TRANSLATION_ONLY
    }

    // MARK: - Event Handling

    private fun handleEvent(event: AISubtitleDataEvent) {
        when (event) {
            is AISubtitleDataEvent.Added -> {
                adapter?.addSegment(event.data.segmentId)
                if (isNearBottom && !isUserDragging) {
                    scrollToBottom(animated = true)
                }
            }
            is AISubtitleDataEvent.Updated -> {
                adapter?.updateSegment(event.data.segmentId)
                if (isNearBottom && !isUserDragging) {
                    scrollToBottom(animated = false)
                }
            }
            is AISubtitleDataEvent.Completed -> {
                adapter?.updateSegment(event.data.segmentId)
                if (isNearBottom && !isUserDragging) {
                    scrollToBottom(animated = false)
                }
            }
            is AISubtitleDataEvent.ClearedAll -> {
                adapter?.clearAll()
            }
        }
    }

    // MARK: - Public Methods

    val minutesCount: Int get() = adapter?.segmentCount ?: 0

    fun scrollToBottom(animated: Boolean) {
        val count = adapter?.segmentCount ?: return
        if (count <= 0) return
        if (animated) {
            recyclerView.smoothScrollToPosition(count - 1)
        } else {
            recyclerView.scrollToPosition(count - 1)
        }
    }

    fun clearAll() {
        adapter?.clearAll()
    }

    private fun updateAutoScrollState() {
        val layoutManager = recyclerView.layoutManager as? LinearLayoutManager ?: return
        val lastVisible = layoutManager.findLastVisibleItemPosition()
        val itemCount = adapter?.itemCount ?: 0
        isNearBottom = lastVisible >= itemCount - 2
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        subscribeJob?.cancel()
    }
}
