package com.trtc.uikit.roomkit.aitranscription.subtitleview

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.constraintlayout.widget.ConstraintLayout
import com.trtc.uikit.roomkit.R
import com.trtc.uikit.roomkit.base.utils.dpToPx
import com.trtc.uikit.roomkit.aitranscription.config.AISubtitleConfig
import com.trtc.uikit.roomkit.aitranscription.config.AITranscriptionData
import com.trtc.uikit.roomkit.aitranscription.config.DisplayMode
import com.trtc.uikit.roomkit.aitranscription.repository.AISubtitleDataEvent
import io.trtc.tuikit.atomicxcore.api.ai.TranslationLanguage
import com.trtc.uikit.roomkit.aitranscription.repository.AITranscriberRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Multi-speaker subtitle list view. ConstraintLayout-based, displays up to maxVisibleSpeakers
 * subtitle items with auto-fade support.
 */
class AISubtitleView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : ConstraintLayout(context, attrs, defStyleAttr) {

    // MARK: - Properties

    private var config: AISubtitleConfig = AISubtitleConfig.default(context)
    private var repository: AITranscriberRepository? = null
    private var subscribeJob: Job? = null

    private val activeSegmentKeys = mutableListOf<String>()
    private val fadeOutRunnables = mutableMapOf<String, Runnable>()
    private val handler = Handler(Looper.getMainLooper())

    private val itemViews = mutableMapOf<String, AISubtitleItemView>()

    var onSubtitleFadeOut: ((String) -> Unit)? = null
    var onTap: (() -> Unit)? = null

    private val contentLayout: LinearLayout
    private val arrowImageView: ImageView
    private val placeholderTextView: android.widget.TextView

    init {
        clipChildren = true
        clipToPadding = true
        isClickable = true

        LayoutInflater.from(context).inflate(R.layout.roomkit_view_ai_subtitle, this, true)
        contentLayout = findViewById(R.id.ll_content)
        arrowImageView = findViewById(R.id.iv_arrow)
        placeholderTextView = findViewById(R.id.tv_placeholder)

        applyConfig(config)
        updatePlaceholderVisibility()

        setOnClickListener { onTap?.invoke() }
    }

    // MARK: - Configuration

    fun bindRepository(repository: AITranscriberRepository) {
        this.repository = repository
        subscribeJob?.cancel()

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
                    reloadAllItems()
                }
            }
            launch {
                repository.selectedTranslationLanguage.collect { translationLanguage ->
                    config = config.copy(
                        displayMode = resolveDisplayMode(repository.isBilingualEnabled.value, translationLanguage)
                    )
                    reloadAllItems()
                }
            }
        }
    }

    fun configure(config: AISubtitleConfig) {
        this.config = config
        applyConfig(config)
        reloadAllItems()
    }

    private fun resolveDisplayMode(isBilingual: Boolean, translationLanguage: TranslationLanguage?): DisplayMode {
        if (translationLanguage == null) return DisplayMode.SOURCE_ONLY
        return if (isBilingual) DisplayMode.DUAL else DisplayMode.TRANSLATION_ONLY
    }

    // MARK: - Event Handling

    private fun handleEvent(event: AISubtitleDataEvent) {
        when (event) {
            is AISubtitleDataEvent.Added -> handleAdded(event.data)
            is AISubtitleDataEvent.Updated -> handleUpdated(event.data)
            is AISubtitleDataEvent.Completed -> {
                itemViews[event.data.segmentId]?.bindData(event.data, config)
                if (config.displayDurationMs > 0) {
                    scheduleFadeOut(event.data.segmentId)
                }
            }
            is AISubtitleDataEvent.ClearedAll -> clearAll(animated = true)
        }
    }

    // MARK: - Public Methods

    fun removeSubtitle(segmentId: String, animated: Boolean = true) {
        cancelFadeOutTimer(segmentId)
        activeSegmentKeys.remove(segmentId)

        val itemView = itemViews.remove(segmentId) ?: run {
            onSubtitleFadeOut?.invoke(segmentId)
            return
        }

        if (animated) {
            itemView.animate().alpha(0f).setDuration(300).withEndAction {
                contentLayout.removeView(itemView)
                updateAllItemMaxLines()
            }.start()
        } else {
            contentLayout.removeView(itemView)
            updateAllItemMaxLines()
        }
        onSubtitleFadeOut?.invoke(segmentId)
        updateArrowVisibility()
        updatePlaceholderVisibility()
    }

    fun clearAll(animated: Boolean = true) {
        fadeOutRunnables.values.forEach { handler.removeCallbacks(it) }
        fadeOutRunnables.clear()
        activeSegmentKeys.clear()

        if (animated) {
            val views = (0 until contentLayout.childCount).map { contentLayout.getChildAt(it) }
            views.forEach { view ->
                view.animate().alpha(0f).setDuration(300).withEndAction {
                    contentLayout.removeView(view)
                }.start()
            }
            itemViews.clear()
        } else {
            contentLayout.removeAllViews()
            itemViews.clear()
        }
        updateArrowVisibility()
        updatePlaceholderVisibility()
    }

    val activeSegmentCount: Int get() = activeSegmentKeys.size

    // MARK: - Private Methods

    private fun updateArrowVisibility() {
        arrowImageView.visibility = if (activeSegmentKeys.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun updatePlaceholderVisibility() {
        val hasSubtitles = activeSegmentKeys.isNotEmpty()
        placeholderTextView.visibility = if (hasSubtitles) View.GONE else View.VISIBLE
        contentLayout.visibility = if (hasSubtitles) View.VISIBLE else View.GONE
    }

    private fun applyConfig(config: AISubtitleConfig) {
        val bgDrawable = android.graphics.drawable.GradientDrawable().apply {
            setColor(config.backgroundColor)
            cornerRadius = dpToPx(config.backgroundCornerRadiusDp).toFloat()
        }
        background = bgDrawable
    }

    private fun handleAdded(data: AITranscriptionData) {
        val segmentId = data.segmentId
        cancelFadeOutTimer(segmentId)
        activeSegmentKeys.add(segmentId)
        trimExcessSegments()
        rebuildStackItems()
        updateArrowVisibility()
        updatePlaceholderVisibility()

        if (config.displayDurationMs > 0) {
            scheduleFadeOut(segmentId)
        }
    }

    private fun handleUpdated(data: AITranscriptionData) {
        val segmentId = data.segmentId
        cancelFadeOutTimer(segmentId)

        if (!activeSegmentKeys.contains(segmentId)) {
            activeSegmentKeys.add(segmentId)
            trimExcessSegments()
            rebuildStackItems()
            updatePlaceholderVisibility()
        } else {
            moveToLatest(segmentId)
            itemViews[segmentId]?.let { itemView ->
                itemView.bindData(data, config)
                itemView.updateMaxLines(maxLinesForCurrentLayout())
            }
        }
        if (config.displayDurationMs > 0) {
            scheduleFadeOut(segmentId)
        }
    }

    private fun visibleSegmentKeys(): List<String> {
        val max = config.maxVisibleSpeakers
        if (max <= 0 || activeSegmentKeys.size <= max) return activeSegmentKeys.toList()
        return activeSegmentKeys.takeLast(max)
    }

    private fun trimExcessSegments() {
        val max = config.maxVisibleSpeakers
        if (max <= 0) return
        while (activeSegmentKeys.size > max) {
            val removed = activeSegmentKeys.removeFirst()
            cancelFadeOutTimer(removed)
            itemViews.remove(removed)?.let { contentLayout.removeView(it) }
        }
    }

    private fun moveToLatest(segmentId: String) {
        val index = activeSegmentKeys.indexOf(segmentId)
        if (index < 0) return
        val oldVisible = visibleSegmentKeys()
        activeSegmentKeys.removeAt(index)
        activeSegmentKeys.add(segmentId)
        val newVisible = visibleSegmentKeys()

        if (oldVisible != newVisible) {
            rebuildStackItems()
        }
    }

    private fun updateAllItemMaxLines() {
        val maxLines = maxLinesForCurrentLayout()
        itemViews.values.forEach { it.updateMaxLines(maxLines) }
    }

    private fun maxLinesForCurrentLayout(): Int {
        val visibleCount = visibleSegmentKeys().size
        return if (visibleCount <= 1) 2 else 1
    }

    private fun rebuildStackItems() {
        val visible = visibleSegmentKeys()
        val maxLines = maxLinesForCurrentLayout()

        contentLayout.removeAllViews()

        for (segmentId in visible) {
            val itemView = itemViews.getOrPut(segmentId) { AISubtitleItemView(context) }

            repository?.getData(segmentId)?.let { data ->
                itemView.bindData(data, config)
            }
            itemView.updateMaxLines(maxLines)
            itemView.alpha = 1f

            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.bottomMargin = dpToPx(config.speakerItemSpacingDp)
            contentLayout.addView(itemView, lp)
        }

        // Clean up invisible items
        val visibleSet = visible.toSet()
        itemViews.keys.filter { it !in visibleSet }.forEach { key ->
            itemViews.remove(key)
        }
    }

    private fun reloadAllItems() {
        val visible = visibleSegmentKeys()
        for (segmentId in visible) {
            val data = repository?.getData(segmentId) ?: continue
            itemViews[segmentId]?.bindData(data, config)
        }
    }

    // MARK: - Fade Out Timer

    private fun scheduleFadeOut(segmentId: String) {
        cancelFadeOutTimer(segmentId)
        val runnable = Runnable {
            fadeOutRunnables.remove(segmentId)
            removeSubtitle(segmentId, animated = true)
        }
        fadeOutRunnables[segmentId] = runnable
        handler.postDelayed(runnable, config.displayDurationMs)
    }

    private fun cancelFadeOutTimer(segmentId: String) {
        fadeOutRunnables.remove(segmentId)?.let { handler.removeCallbacks(it) }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        subscribeJob?.cancel()
        fadeOutRunnables.values.forEach { handler.removeCallbacks(it) }
        fadeOutRunnables.clear()
    }
}
