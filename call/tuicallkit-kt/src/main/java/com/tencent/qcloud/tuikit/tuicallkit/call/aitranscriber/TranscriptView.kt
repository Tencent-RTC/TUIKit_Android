package com.tencent.qcloud.tuikit.tuicallkit.call.aitranscriber

import android.content.Context
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.widget.NestedScrollView
import com.tencent.qcloud.tuikit.tuicallkit.R
import com.tencent.qcloud.tuikit.tuicallkit.call.CallKitConfig
import com.tencent.qcloud.tuikit.tuicallkit.common.dp
import io.trtc.tuikit.atomicxcore.api.ai.AITranscriberStore
import io.trtc.tuikit.atomicxcore.api.ai.TranscriberMessage
import io.trtc.tuikit.atomicxcore.api.ai.TranslationLanguage
import io.trtc.tuikit.atomicxcore.api.call.CallStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

internal class TranscriptView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr) {

    sealed class LayoutMode {
        object Single : LayoutMode()
        data class Multi(val rows: Int) : LayoutMode()

        fun listMaxHeight(context: Context): Int {
            return when (this) {
                is Single -> 140.dp(context)
                is Multi -> 70.dp(context)
            }
        }
    }

    var layoutMode: LayoutMode = LayoutMode.Single
        set(value) {
            field = value
            updateHeight()
        }

    var isImmersiveExpanded = false
        set(value) {
            field = value
            updateHeight()
        }

    var availableMaxHeight: Int? = null
        set(value) {
            if (field == value) return
            field = value
            updateHeight()
        }

    var onPickSettings: (() -> Unit)? = null

    private val scrollView: NestedScrollView
    private val listStack: LinearLayout
    private val headRow: LinearLayout
    private var subscribeJob: Job? = null
    private var transcriberJob: Job? = null
    private var subscribedCallId = ""
    private var messages: List<TranscriberMessage> = emptyList()

    init {
        orientation = VERTICAL
        headRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(2.dp(context), 0, 2.dp(context), 0)
            isClickable = false
        }
        val title = TextView(context).apply {
            text = context.getString(R.string.tuicallkit_cc_title)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(ContextCompat.getColor(context, android.R.color.white))
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            if (android.os.Build.VERSION.SDK_INT >= 28) {
                lineHeight = 18.dp(context)
            }
            letterSpacing = 0.2f / 13f
        }
        headRow.addView(
            title,
            LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
        )
        val gearSizePx = 28.dp(context)
        val gear = ImageButton(context).apply {
            setImageResource(R.drawable.tuicallkit_icon_settings)
            background = null
            contentDescription = context.getString(R.string.tuicallkit_cc_title)
            scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
            val iconInset = 6.dp(context)
            setPadding(iconInset, iconInset, iconInset, iconInset)
            setOnClickListener { onPickSettings?.invoke() }
        }
        headRow.addView(gear, LayoutParams(gearSizePx, gearSizePx))
        addView(
            headRow,
            LayoutParams(LayoutParams.MATCH_PARENT, HEAD_ROW_HEIGHT_DP.dp(context))
        )

        scrollView = NestedScrollView(context).apply {
            isVerticalScrollBarEnabled = true
            scrollBarStyle = View.SCROLLBARS_INSIDE_OVERLAY
            setScrollBarSize(3.dp(context))
            if (android.os.Build.VERSION.SDK_INT >= 29) {
                verticalScrollbarThumbDrawable = android.graphics.drawable.GradientDrawable().apply {
                    setColor(0x59FFFFFF)
                    cornerRadius = 2f.dp(context).toFloat()
                }
            }
        }
        listStack = LinearLayout(context).apply {
            orientation = VERTICAL
        }
        scrollView.addView(listStack, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        addView(
            scrollView,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                topMargin = HEAD_ROW_GAP_DP.dp(context)
            }
        )
        updateHeight()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        subscribeState()
    }

    override fun onDetachedFromWindow() {
        subscribeJob?.cancel()
        subscribeJob = null
        transcriberJob?.cancel()
        transcriberJob = null
        super.onDetachedFromWindow()
    }

    private val headBlockPx: Int
        get() = (HEAD_ROW_HEIGHT_DP + HEAD_ROW_GAP_DP).dp(context)

    private val maxListHeight: Int
        get() {
            availableMaxHeight?.let { available ->
                val listAvailable = (available - headBlockPx).coerceAtLeast(0)
                return if (isImmersiveExpanded) {
                    listAvailable
                } else {
                    min(layoutMode.listMaxHeight(context), listAvailable)
                }
            }
            if (isImmersiveExpanded) {
                val base = (parent as? View)?.height ?: resources.displayMetrics.heightPixels
                val ratio = if (layoutMode is LayoutMode.Single) 325f / 852f else 343f / 852f
                return (base * ratio).toInt()
            }
            return layoutMode.listMaxHeight(context)
        }

    private val targetHeight: Int
        get() {
            listStack.measure(
                MeasureSpec.makeMeasureSpec(width - paddingStart - paddingEnd, MeasureSpec.AT_MOST),
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
            )
            val content = listStack.measuredHeight
            val minHeight = min(MIN_BLOCK_HEIGHT_DP.dp(context), maxListHeight)
            val listHeight = min(max(content, minHeight), maxListHeight)
            return listHeight + headBlockPx
        }

    private fun updateHeight() {
        val target = targetHeight
        if (layoutParams != null && layoutParams.height != target) {
            layoutParams.height = target
            requestLayout()
        }
    }

    private fun subscribeState() {
        subscribeJob?.cancel()
        subscribeJob = CoroutineScope(Dispatchers.Main + SupervisorJob()).launch {
            launch {
                CallStore.shared.observerState.activeCall
                    .distinctUntilChanged { old, new -> old.callId == new.callId }
                    .collect { subscribeTranscriber(it.callId) }
            }
            launch {
                CallKitConfig.shared.transcriptionChanged.collect { renderAll() }
            }
        }
    }

    private fun subscribeTranscriber(callId: String) {
        if (callId == subscribedCallId) {
            return
        }
        subscribedCallId = callId
        transcriberJob?.cancel()
        transcriberJob = null
        messages = emptyList()
        renderAll()
        if (callId.isEmpty()) {
            return
        }
        val store = AITranscriberStore.create(callId)
        transcriberJob = CoroutineScope(Dispatchers.Main + SupervisorJob()).launch {
            store.transcriberState.realtimeMessageList.collect { list ->
                applyMessages(list)
            }
        }
    }

    private fun applyMessages(list: List<TranscriberMessage>) {
        val last = list.lastOrNull()
        val current = messages.lastOrNull()
        if (list.size == messages.size && last != null && current != null
            && last.segmentId == current.segmentId && !last.isCompleted
        ) {
            messages = messages.toMutableList().also { it[it.size - 1] = last }
            updateRowAt(messages.size - 1)
            scrollToBottom()
            return
        }
        messages = list
        renderAll()
    }

    private fun renderAll() {
        listStack.removeAllViews()
        val spacing = 8.dp(context)
        messages.forEachIndexed { index, message ->
            val row = makeRow(message)
            val params = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
            if (index > 0) {
                params.topMargin = spacing
            }
            listStack.addView(row, params)
        }
        post { updateHeight() }
        scrollToBottom()
    }

    private fun updateRowAt(index: Int) {
        if (index >= listStack.childCount) {
            renderAll()
            return
        }
        listStack.removeViewAt(index)
        val params = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        if (index > 0) {
            params.topMargin = 8.dp(context)
        }
        listStack.addView(makeRow(messages[index]), index, params)
        post { updateHeight() }
    }

    private fun makeRow(message: TranscriberMessage): View {
        val config = CallKitConfig.shared
        val fontSize = config.transcriptFontSize.textSizeSp

        val line = LinearLayout(context).apply {
            orientation = HORIZONTAL
        }
        val speakerLabel = TextView(context).apply {
            text = message.speakerUserName.ifEmpty { message.speakerUserId }
            setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSize)
            setTextColor(ContextCompat.getColor(context, R.color.tuicallkit_brand))
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        speakerLabel.applyFixedLineHeight(18)
        val speakerParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
        speakerParams.marginEnd = 8.dp(context)
        line.addView(speakerLabel, speakerParams)

        val display = displayTextFor(message)
        val textLabel = TextView(context).apply {
            text = display
            setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSize)
            setTextColor(ContextCompat.getColor(context, android.R.color.white))
            applyFixedLineHeight(18)
        }
        line.addView(textLabel, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))

        val row = LinearLayout(context).apply {
            orientation = VERTICAL
            addView(line)
        }
        if (config.showOriginalTranscript && display != message.sourceText) {
            val sourceLabel = TextView(context).apply {
                text = message.sourceText
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                setTextColor(0x9EFFFFFF.toInt())
                applyFixedLineHeight(15)
            }
            val sourceParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
            sourceParams.topMargin = 2.dp(context)
            row.addView(sourceLabel, sourceParams)
        }
        return row
    }

    private fun displayTextFor(message: TranscriberMessage): String {
        val target = targetLanguage
        if (target != null) {
            val text = message.translationTexts[target]
            if (!text.isNullOrEmpty()) {
                return text
            }
        }
        return message.sourceText
    }

    private val targetLanguage: TranslationLanguage?
        get() = when (CallKitConfig.shared.transcriptionTargetLanguage) {
            "zh" -> TranslationLanguage.CHINESE
            "en" -> TranslationLanguage.ENGLISH
            "ar" -> TranslationLanguage.ARABIC
            "ja" -> TranslationLanguage.JAPANESE
            else -> null
        }

    private fun scrollToBottom() {
        post {
            scrollView.scrollTo(0, scrollView.getChildAt(0)?.bottom ?: 0)
        }
    }

    private fun TextView.applyFixedLineHeight(heightDp: Int) {
        if (android.os.Build.VERSION.SDK_INT >= 28) {
            lineHeight = heightDp.dp(context)
        }
    }

    private companion object {
        const val MIN_BLOCK_HEIGHT_DP = 78
        const val HEAD_ROW_HEIGHT_DP = 28
        const val HEAD_ROW_GAP_DP = 8
    }
}
