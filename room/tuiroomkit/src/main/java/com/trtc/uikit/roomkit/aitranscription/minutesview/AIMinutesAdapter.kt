package com.trtc.uikit.roomkit.aitranscription.minutesview

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.RecyclerView
import com.trtc.uikit.roomkit.R
import com.trtc.uikit.roomkit.base.utils.dpToPx
import com.trtc.uikit.roomkit.aitranscription.config.AIMinutesConfig
import com.trtc.uikit.roomkit.aitranscription.config.AITranscriptionData
import com.trtc.uikit.roomkit.aitranscription.config.DisplayMode
import com.trtc.uikit.roomkit.aitranscription.repository.AITranscriberRepository

class AIMinutesAdapter(
    private val repository: AITranscriberRepository,
    private var config: AIMinutesConfig,
) : RecyclerView.Adapter<AIMinutesAdapter.MinutesViewHolder>() {

    private val segmentIds = mutableListOf<String>()
    private val segmentIndexMap = mutableMapOf<String, Int>()

    fun updateConfig(config: AIMinutesConfig) {
        this.config = config
    }

    fun addSegment(segmentId: String) {
        val index = segmentIds.size
        segmentIds.add(segmentId)
        segmentIndexMap[segmentId] = index
        notifyItemInserted(index)
    }

    fun updateSegment(segmentId: String) {
        val index = segmentIndexMap[segmentId] ?: return
        notifyItemChanged(index, PAYLOAD_TEXT_UPDATE)
    }

    fun clearAll() {
        segmentIds.clear()
        segmentIndexMap.clear()
        notifyDataSetChanged()
    }

    fun syncFromRepository(repository: AITranscriberRepository) {
        segmentIds.clear()
        segmentIndexMap.clear()
        segmentIds.addAll(repository.orderedSegmentIds)
        for ((index, id) in segmentIds.withIndex()) {
            segmentIndexMap[id] = index
        }
        notifyDataSetChanged()
    }

    val segmentCount: Int get() = segmentIds.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MinutesViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.roomkit_item_ai_minutes_cell, parent, false)
        return MinutesViewHolder(view)
    }

    override fun onBindViewHolder(holder: MinutesViewHolder, position: Int) {
        val segmentId = segmentIds[position]
        val data = repository.getData(segmentId) ?: return
        val showSpeakerInfo = shouldShowSpeakerInfo(position, data)
        holder.bindData(data, config, showSpeakerInfo)
    }

    override fun onBindViewHolder(holder: MinutesViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.contains(PAYLOAD_TEXT_UPDATE)) {
            val segmentId = segmentIds[position]
            val data = repository.getData(segmentId) ?: return
            holder.updateTexts(data, config)
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    override fun getItemCount(): Int = segmentIds.size

    class MinutesViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val speakerContainer: ViewGroup = itemView.findViewById(R.id.ll_speaker_container)
        private val nameLabel: TextView = itemView.findViewById(R.id.tv_speaker_name)
        private val timestampLabel: TextView = itemView.findViewById(R.id.tv_timestamp)
        private val cardView: LinearLayout = itemView.findViewById(R.id.ll_card)
        private val sourceLabel: TextView = itemView.findViewById(R.id.tv_source_text)
        private val translationLabel: TextView = itemView.findViewById(R.id.tv_translation_text)

        fun bindData(data: AITranscriptionData, config: AIMinutesConfig, showSpeakerInfo: Boolean = true) {
            if (showSpeakerInfo && config.showSpeaker && data.speakerUserName.isNotEmpty()) {
                nameLabel.text = data.speakerUserName
                speakerContainer.visibility = View.VISIBLE
            } else {
                speakerContainer.visibility = View.GONE
            }

            if (showSpeakerInfo && config.showTimestamp && data.timestamp > 0) {
                timestampLabel.text = config.formatTimestamp(data.timestamp)
                timestampLabel.visibility = View.VISIBLE
            } else {
                timestampLabel.visibility = View.GONE
            }

            applyStyle(config)
            updateTextLabels(data, config)
        }

        fun updateTexts(data: AITranscriptionData, config: AIMinutesConfig) {
            updateTextLabels(data, config)
        }

        private fun updateTextLabels(data: AITranscriptionData, config: AIMinutesConfig) {
            when (config.displayMode) {
                DisplayMode.SOURCE_ONLY -> {
                    if (sourceLabel.text.toString() != data.sourceText) sourceLabel.text = data.sourceText
                    sourceLabel.visibility = if (data.sourceText.isEmpty()) View.GONE else View.VISIBLE
                    translationLabel.visibility = View.GONE
                }
                DisplayMode.TRANSLATION_ONLY -> {
                    sourceLabel.visibility = View.GONE
                    if (translationLabel.text.toString() != data.translationText) translationLabel.text = data.translationText
                    translationLabel.visibility = if (data.translationText.isEmpty()) View.GONE else View.VISIBLE
                }
                DisplayMode.DUAL, DisplayMode.DUAL_REVERSED -> {
                    if (sourceLabel.text.toString() != data.sourceText) sourceLabel.text = data.sourceText
                    sourceLabel.visibility = if (data.sourceText.isEmpty()) View.GONE else View.VISIBLE
                    if (translationLabel.text.toString() != data.translationText) translationLabel.text = data.translationText
                    translationLabel.visibility = if (data.translationText.isEmpty()) View.GONE else View.VISIBLE
                }
            }
        }

        private fun applyStyle(config: AIMinutesConfig) {
            val context = itemView.context
            val listPaddingLeft = context.dpToPx(config.listPaddingLeftDp)
            val listPaddingRight = context.dpToPx(config.listPaddingRightDp)
            val itemPaddingLeft = context.dpToPx(config.itemContentPaddingLeftDp)
            val itemPaddingRight = context.dpToPx(config.itemContentPaddingRightDp)

            // Speaker
            nameLabel.setTextColor(config.speakerStyle.nameColor)
            nameLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, config.speakerStyle.nameSize)
            nameLabel.typeface = config.speakerStyle.nameTypeface
            timestampLabel.setTextColor(config.speakerStyle.timestampColor)
            timestampLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, config.speakerStyle.timestampSize)
            (timestampLabel.layoutParams as? ViewGroup.MarginLayoutParams)?.marginStart = context.dpToPx(config.speakerStyle.nameTimestampSpacing)

            val showSpeaker = speakerContainer.visibility == View.VISIBLE
            (speakerContainer.layoutParams as? ViewGroup.MarginLayoutParams)?.let {
                it.topMargin = context.dpToPx(12)
                it.leftMargin = listPaddingLeft + itemPaddingLeft
                it.rightMargin = listPaddingRight + itemPaddingRight
            }

            // Card
            val bgDrawable = GradientDrawable().apply {
                setColor(config.itemBackgroundColor)
                cornerRadius = context.dpToPx(config.itemCornerRadiusDp).toFloat()
            }
            cardView.background = bgDrawable
            cardView.setPadding(
                itemPaddingLeft,
                context.dpToPx(config.itemContentPaddingTopDp),
                itemPaddingRight,
                context.dpToPx(config.itemContentPaddingBottomDp),
            )
            (cardView.layoutParams as? ViewGroup.MarginLayoutParams)?.let {
                it.topMargin = if (showSpeaker) context.dpToPx(config.speakerStyle.bottomSpacing) else context.dpToPx(10)
                it.leftMargin = listPaddingLeft + itemPaddingLeft
                it.rightMargin = listPaddingRight + itemPaddingRight
            }

            // Spacing
            (sourceLabel.layoutParams as? ViewGroup.MarginLayoutParams)?.bottomMargin = context.dpToPx(config.lineSpacingDp)

            // Text styles
            sourceLabel.setTextColor(config.sourceStyle.textColor)
            sourceLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, config.sourceStyle.fontSize)
            sourceLabel.typeface = config.sourceStyle.typeface
            translationLabel.setTextColor(config.translationStyle.textColor)
            translationLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, config.translationStyle.fontSize)
            translationLabel.typeface = config.translationStyle.typeface
        }
    }

    /**
     * Determines whether the cell at the given position should display speaker name and timestamp.
     * Returns false when the previous cell belongs to the same speaker and the time gap is ≤ 60 seconds.
     */
    private fun shouldShowSpeakerInfo(position: Int, currentData: AITranscriptionData): Boolean {
        if (position <= 0) return true
        val previousSegmentId = segmentIds[position - 1]
        val previousData = repository.getData(previousSegmentId) ?: return true

        val isSameSpeaker = previousData.speakerUserId == currentData.speakerUserId
        val timeDiffMs = kotlin.math.abs(currentData.timestamp - previousData.timestamp)
        val isWithin60Seconds = timeDiffMs <= 60_000

        return !(isSameSpeaker && isWithin60Seconds)
    }

    companion object {
        private const val PAYLOAD_TEXT_UPDATE = "text_update"
    }
}
