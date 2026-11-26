package com.trtc.uikit.roomkit.inroom.features.transcription.transcriptlist

import android.content.Context
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import com.trtc.uikit.roomkit.R
import com.trtc.uikit.roomkit.base.error.ErrorLocalized
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.ai.AITranscriberStore
import io.trtc.tuikit.atomicxcore.api.ai.SourceLanguage
import io.trtc.tuikit.atomicxcore.api.ai.TranscriptionConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class TranscriptSettingsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    private var roomID = ""

    private val mainContent: LinearLayout
    private val languageContent: LinearLayout
    private val languageList: LinearLayout
    private val sourceRow: View
    private val sourceValue: TextView
    private val translationSwitch: SwitchCompat
    private val backButton: View
    private var subscribeJob: Job? = null

    init {
        LayoutInflater.from(context).inflate(R.layout.roomkit_caption_settings, this, true)
        mainContent = findViewById(R.id.caption_settings_main)
        languageContent = findViewById(R.id.caption_settings_languages)
        languageList = findViewById(R.id.caption_language_list)
        sourceRow = findViewById(R.id.caption_source_row)
        sourceValue = findViewById(R.id.caption_source_value)
        translationSwitch = findViewById(R.id.caption_bilingual_switch)
        backButton = findViewById(R.id.caption_settings_back)
        sourceRow.setOnClickListener { showLanguages() }
        backButton.setOnClickListener { showMain() }
        translationSwitch.setOnCheckedChangeListener { _, checked ->
            updateTranslationEnabled(checked)
        }
        buildLanguageRows()
    }

    fun init(roomID: String) {
        this.roomID = roomID
        if (isAttachedToWindow) subscribe()
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
        val transcriberState = AITranscriberStore.create(roomID).transcriberState
        subscribeJob?.cancel()
        subscribeJob = CoroutineScope(Dispatchers.Main).launch {
            launch {
                transcriberState.selfLanguage.collect { language ->
                    sourceValue.text = languageLabel(language)
                    updateLanguageRows(language)
                }
            }
            launch {
                transcriberState.transcriptionConfig.collect { config ->
                    translationSwitch.isChecked = config.enableTranslation
                }
            }
        }
    }

    private fun buildLanguageRows() {
        listOf(
            SourceLanguage.CHINESE,
            SourceLanguage.ENGLISH,
            SourceLanguage.JAPANESE,
            SourceLanguage.KOREAN,
        ).forEach { language ->
            val row = LayoutInflater.from(context).inflate(R.layout.roomkit_caption_language_item, languageList, false)
            row.findViewById<TextView>(R.id.caption_language_name).text = languageLabel(language)
            row.setOnClickListener {
                updateSourceLanguage(language)
                showMain()
            }
            row.tag = language
            languageList.addView(row)
        }
    }

    private fun updateLanguageRows(selectedLanguage: SourceLanguage) {
        for (index in 0 until languageList.childCount) {
            val row = languageList.getChildAt(index)
            val selected = row.tag == selectedLanguage
            row.findViewById<TextView>(R.id.caption_language_name).apply {
                val color = ContextCompat.getColor(
                    context,
                    if (selected) R.color.roomkit_accent else R.color.roomkit_fg_primary,
                )
                setTextColor(color)
                typeface = Typeface.defaultFromStyle(if (selected) Typeface.BOLD else Typeface.NORMAL)
            }
            row.findViewById<ImageView>(R.id.caption_language_check).visibility = if (selected) VISIBLE else INVISIBLE
        }
    }

    private fun updateSourceLanguage(language: SourceLanguage) {
        val transcriberStore = AITranscriberStore.create(roomID)
        transcriberStore.updateTranscription(
            language,
            transcriberStore.transcriberState.transcriptionConfig.value,
            object : CompletionHandler {
                override fun onSuccess() {}

                override fun onFailure(code: Int, desc: String) {
                    ErrorLocalized.showError(context, code, desc)
                }
            },
        )
    }

    private fun updateTranslationEnabled(enabled: Boolean) {
        val transcriberStore = AITranscriberStore.create(roomID)
        transcriberStore.updateTranscription(
            transcriberStore.transcriberState.selfLanguage.value,
            TranscriptionConfig(enableTranslation = enabled),
            object : CompletionHandler {
                override fun onSuccess() {}

                override fun onFailure(code: Int, desc: String) {
                    ErrorLocalized.showError(context, code, desc)
                }
            },
        )
    }

    private fun showLanguages() {
        mainContent.visibility = GONE
        languageContent.visibility = VISIBLE
    }

    private fun showMain() {
        mainContent.visibility = VISIBLE
        languageContent.visibility = GONE
    }

    private fun languageLabel(language: SourceLanguage): String = context.getString(
        when (language) {
            SourceLanguage.CHINESE -> R.string.roomkit_caption_language_zh
            SourceLanguage.ENGLISH -> R.string.roomkit_caption_language_en
            SourceLanguage.JAPANESE -> R.string.roomkit_caption_language_ja
            SourceLanguage.KOREAN -> R.string.roomkit_caption_language_ko
            else -> R.string.roomkit_caption_language_en
        },
    )
}
