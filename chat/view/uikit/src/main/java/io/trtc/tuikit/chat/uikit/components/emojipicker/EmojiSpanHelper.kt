package io.trtc.tuikit.chat.uikit.components.emojipicker
import android.content.Context
import android.graphics.drawable.Drawable
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.util.LruCache
import android.view.View
import android.widget.EditText
import com.bumptech.glide.Glide
import com.bumptech.glide.RequestManager
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import io.trtc.tuikit.chat.uikit.components.emojipicker.model.Emoji
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

object EmojiSpanHelper {

    private const val TEXT_REPLACEMENT_CACHE_SIZE = 200
    private val textReplacementCache = object : LruCache<String, String>(TEXT_REPLACEMENT_CACHE_SIZE) {}

    fun setEmojiSpanText(
        context: Context,
        text: String,
        textSizePx: Float,
        requestView: View? = null,
        onResult: (CharSequence) -> Unit
    ) {
        if (text.isEmpty()) {
            onResult(text)
            return
        }

        val emojiSize = (textSizePx * 1.5f).toInt()
        val spannable = SpannableStringBuilder(text)
        val sortedKeys = EmojiManager.sortedLittleEmojiKeyList

        val pendingLoads = mutableListOf<Triple<Int, Int, String>>()

        for (emojiKey in sortedKeys) {
            var startIndex = spannable.indexOf(emojiKey)
            while (startIndex != -1) {
                val endIndex = startIndex + emojiKey.length
                val cachedDrawable = EmojiManager.getCachedEmojiDrawable(emojiKey)
                if (cachedDrawable != null) {
                    cachedDrawable.setBounds(0, 0, emojiSize, emojiSize)
                    val imageSpan = CenterImageSpan(cachedDrawable)
                    spannable.setSpan(
                        imageSpan,
                        startIndex,
                        endIndex,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                } else {
                    pendingLoads.add(Triple(startIndex, endIndex, emojiKey))
                }
                startIndex = spannable.indexOf(emojiKey, endIndex)
            }
        }

        if (pendingLoads.isEmpty()) {
            onResult(spannable)
            return
        }

        val remaining = AtomicInteger(pendingLoads.size)
        for ((start, end, key) in pendingLoads) {
            val emoji = EmojiManager.findEmojiByKey(key)
            if (emoji == null) {
                if (remaining.decrementAndGet() == 0) onResult(spannable)
                continue
            }
            glideWith(context, requestView)
                .asDrawable()
                .load(emoji.emojiUrl)
                .into(object : CustomTarget<Drawable>() {
                    private val completed = AtomicBoolean(false)

                    override fun onResourceReady(
                        resource: Drawable,
                        transition: Transition<in Drawable>?
                    ) {
                        val spanDrawable = resource.newDrawableForSpan()
                        spanDrawable.setBounds(0, 0, emojiSize, emojiSize)
                        if (start < spannable.length && end <= spannable.length) {
                            val imageSpan = CenterImageSpan(spanDrawable)
                            spannable.setSpan(
                                imageSpan,
                                start,
                                end,
                                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                            )
                        }
                        completePendingLoad(remaining, completed, spannable, onResult)
                    }

                    override fun onLoadCleared(placeholder: Drawable?) {
                        completePendingLoad(remaining, completed, spannable, onResult)
                    }

                    override fun onLoadFailed(errorDrawable: Drawable?) {
                        completePendingLoad(remaining, completed, spannable, onResult)
                    }
                })
        }
    }

    fun applyEmojiSpans(
        context: Context,
        text: CharSequence,
        textSizePx: Float,
        requestView: View? = null,
        matchNames: Boolean = false,
        onResult: (CharSequence) -> Unit
    ) {
        if (text.isEmpty()) {
            onResult(text)
            return
        }

        val matchTargets = collectEmojiMatchTargets(text.toString(), matchNames)
        if (matchTargets.isEmpty()) {
            onResult(text)
            return
        }

        val emojiSize = (textSizePx * 1.5f).toInt()
        val spannable = SpannableStringBuilder(text)

        val pendingLoads = mutableListOf<PendingEmojiSpan>()

        for ((matchText, emoji) in matchTargets) {
            var startIndex = spannable.indexOf(matchText)
            while (startIndex != -1) {
                val endIndex = startIndex + matchText.length
                val cachedDrawable = EmojiManager.getCachedEmojiDrawable(emoji.key)
                if (cachedDrawable != null) {
                    cachedDrawable.setBounds(0, 0, emojiSize, emojiSize)
                    val imageSpan = CenterImageSpan(cachedDrawable)
                    spannable.setSpan(
                        imageSpan,
                        startIndex,
                        endIndex,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                } else {
                    pendingLoads.add(PendingEmojiSpan(startIndex, endIndex, matchText, emoji))
                }
                startIndex = spannable.indexOf(matchText, endIndex)
            }
        }

        if (pendingLoads.isEmpty()) {
            onResult(spannable)
            return
        }

        val failedLoads = mutableListOf<Triple<Int, Int, String>>()
        val remaining = AtomicInteger(pendingLoads.size)
        for (pending in pendingLoads) {
            val (start, end, matchText, emoji) = pending
            glideWith(context, requestView)
                .asDrawable()
                .load(emoji.emojiUrl)
                .into(object : CustomTarget<Drawable>() {
                    private val completed = AtomicBoolean(false)

                    override fun onResourceReady(
                        resource: Drawable,
                        transition: Transition<in Drawable>?
                    ) {
                        if (start >= 0 && end <= spannable.length &&
                            spannable.substring(start, end) == matchText
                        ) {
                            val spanDrawable = resource.newDrawableForSpan()
                            spanDrawable.setBounds(0, 0, emojiSize, emojiSize)
                            spannable.setSpan(
                                CenterImageSpan(spanDrawable),
                                start,
                                end,
                                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                            )
                        }
                        completePendingLoadWithNameFallback(remaining, completed, spannable, failedLoads, onResult)
                    }

                    override fun onLoadCleared(placeholder: Drawable?) {
                        addKeyFailureFallback(failedLoads, pending)
                        completePendingLoadWithNameFallback(remaining, completed, spannable, failedLoads, onResult)
                    }

                    override fun onLoadFailed(errorDrawable: Drawable?) {
                        addKeyFailureFallback(failedLoads, pending)
                        completePendingLoadWithNameFallback(remaining, completed, spannable, failedLoads, onResult)
                    }
                })
        }
    }

    private data class PendingEmojiSpan(
        val start: Int,
        val end: Int,
        val matchText: String,
        val emoji: Emoji
    )

    private fun collectEmojiMatchTargets(text: String, matchNames: Boolean): List<Pair<String, Emoji>> {
        val targets = mutableListOf<Pair<String, Emoji>>()
        EmojiManager.sortedLittleEmojiKeyList.forEach { key ->
            if (text.contains(key)) {
                EmojiManager.findEmojiByKey(key)?.let { emoji -> targets.add(key to emoji) }
            }
        }
        if (matchNames) {
            EmojiManager.sortedLittleEmojiNameList.forEach { name ->
                if (text.contains(name)) {
                    EmojiManager.findEmojiByName(name)?.let { emoji -> targets.add(name to emoji) }
                }
            }
        }
        return targets.sortedByDescending { it.first.length }
    }

    private fun addKeyFailureFallback(
        failedLoads: MutableList<Triple<Int, Int, String>>,
        pending: PendingEmojiSpan
    ) {
        if (pending.matchText == pending.emoji.key) {
            failedLoads.add(Triple(pending.start, pending.end, pending.emoji.emojiName))
        }
    }

    fun processEditTextEmoji(editText: EditText) {
        val content = editText.text ?: return
        if (content.isEmpty()) return

        val textSizePx = editText.textSize
        val emojiSize = (textSizePx * 1.5f).toInt()
        val spannable = content
        val sortedKeys = EmojiManager.sortedLittleEmojiKeyList

        val existingSpans = spannable.getSpans(0, spannable.length, CenterImageSpan::class.java)
        val existingSpanRanges = mutableSetOf<Pair<Int, Int>>()
        val invalidSpans = mutableListOf<CenterImageSpan>()
        val pendingLoads = mutableListOf<Triple<Int, Int, String>>()
        val originalText = content.toString()

        for (span in existingSpans) {
            val start = spannable.getSpanStart(span)
            val end = spannable.getSpanEnd(span)
            if (start >= 0 && end <= spannable.length) {
                val spanText = spannable.substring(start, end)
                if (sortedKeys.contains(spanText)) {
                    existingSpanRanges.add(start to end)
                } else {
                    invalidSpans.add(span)
                }
            } else {
                invalidSpans.add(span)
            }
        }

        for (span in invalidSpans) {
            spannable.removeSpan(span)
        }

        var hasChanges = false

        for (emojiKey in sortedKeys) {
            var startIndex = spannable.indexOf(emojiKey)
            while (startIndex != -1) {
                val endIndex = startIndex + emojiKey.length
                val range = startIndex to endIndex

                if (startIndex >= 0 && endIndex <= spannable.length && !existingSpanRanges.contains(range)) {
                    val cachedDrawable = EmojiManager.getCachedEmojiDrawable(emojiKey)
                    if (cachedDrawable != null) {
                        cachedDrawable.setBounds(0, 0, emojiSize, emojiSize)
                        val imageSpan = CenterImageSpan(cachedDrawable)
                        spannable.setSpan(
                            imageSpan,
                            startIndex,
                            endIndex,
                            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                        hasChanges = true
                    } else {
                        pendingLoads.add(Triple(startIndex, endIndex, emojiKey))
                    }
                }

                startIndex = spannable.indexOf(emojiKey, endIndex)
            }
        }

        if (hasChanges) {
            val currentSelection = editText.selectionStart
            if (currentSelection >= 0 && currentSelection <= editText.text.length) {
                editText.setSelection(currentSelection)
            }
        }

        if (pendingLoads.isNotEmpty()) {
            loadMissingEditTextEmojiSpans(editText, originalText, pendingLoads, emojiSize)
        }
    }

    fun replaceEmojiKeysWithNames(text: String): String {
        if (text.isEmpty()) return text
        val cacheKey = "${EmojiManager.emojiIndexVersion}:$text"
        textReplacementCache.get(cacheKey)?.let { return it }

        if (!EmojiManager.containsEmojiKey(text)) {
            textReplacementCache.put(cacheKey, text)
            return text
        }

        val sortedEmojis = EmojiManager.sortedLittleEmojiList
        if (sortedEmojis.isEmpty()) return text

        var result = text
        sortedEmojis.forEach { emoji ->
            if (result.contains(emoji.key)) {
                result = result.replace(emoji.key, emoji.emojiName)
            }
        }

        textReplacementCache.put(cacheKey, result)
        return result
    }

    fun clearTextReplacementCache() {
        textReplacementCache.evictAll()
    }

    private fun loadMissingEditTextEmojiSpans(
        editText: EditText,
        expectedText: String,
        pendingLoads: List<Triple<Int, Int, String>>,
        emojiSize: Int
    ) {
        for ((start, end, key) in pendingLoads) {
            val emoji = EmojiManager.findEmojiByKey(key) ?: continue
            Glide.with(editText)
                .asDrawable()
                .load(emoji.emojiUrl)
                .into(object : CustomTarget<Drawable>() {
                    override fun onResourceReady(
                        resource: Drawable,
                        transition: Transition<in Drawable>?
                    ) {
                        val spanDrawable = resource.newDrawableForSpan()
                        spanDrawable.setBounds(0, 0, emojiSize, emojiSize)
                        editText.post {
                            if (!editText.isAttachedToWindow) return@post
                            val editable = editText.text ?: return@post
                            if (editable.toString() != expectedText) return@post
                            if (start < 0 || end > editable.length || editable.substring(start, end) != key) {
                                return@post
                            }
                            val existingSpans = editable.getSpans(start, end, CenterImageSpan::class.java)
                            val hasExistingSpan = existingSpans.any { span ->
                                editable.getSpanStart(span) == start && editable.getSpanEnd(span) == end
                            }
                            if (!hasExistingSpan) {
                                editable.setSpan(
                                    CenterImageSpan(spanDrawable),
                                    start,
                                    end,
                                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                                )
                                editText.invalidate()
                            }
                        }
                    }

                    override fun onLoadCleared(placeholder: Drawable?) {}
                })
        }
    }

    private fun glideWith(context: Context, requestView: View?): RequestManager {
        return if (requestView != null) {
            Glide.with(requestView)
        } else {
            Glide.with(context.applicationContext)
        }
    }

    private fun completePendingLoad(
        remaining: AtomicInteger,
        completed: AtomicBoolean,
        spannable: SpannableStringBuilder,
        onResult: (CharSequence) -> Unit
    ) {
        if (completed.compareAndSet(false, true) && remaining.decrementAndGet() == 0) {
            onResult(spannable)
        }
    }

    private fun completePendingLoadWithNameFallback(
        remaining: AtomicInteger,
        completed: AtomicBoolean,
        spannable: SpannableStringBuilder,
        failedLoads: MutableList<Triple<Int, Int, String>>,
        onResult: (CharSequence) -> Unit
    ) {
        if (completed.compareAndSet(false, true) && remaining.decrementAndGet() == 0) {
            replaceFailedLoadsWithNames(spannable, failedLoads)
            onResult(spannable)
        }
    }

    private fun replaceFailedLoadsWithNames(
        spannable: SpannableStringBuilder,
        failedLoads: MutableList<Triple<Int, Int, String>>
    ) {
        failedLoads.sortByDescending { it.first }
        for ((start, end, name) in failedLoads) {
            if (start >= 0 && end <= spannable.length && start < end) {
                spannable.replace(start, end, name)
            }
        }
    }

    private fun Drawable.newDrawableForSpan(): Drawable {
        return constantState?.newDrawable()?.mutate() ?: mutate()
    }
}
