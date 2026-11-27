package io.trtc.tuikit.chat.uikit.components.emojipicker

import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import androidx.core.graphics.createBitmap
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import io.trtc.tuikit.chat.uikit.components.emojipicker.model.Emoji

private const val TEXT_REPLACEMENT_CACHE_SIZE = 200
private val textReplacementCache = object : LruCache<String, String>(TEXT_REPLACEMENT_CACHE_SIZE) {}

@Composable
fun rememberEmojiText(
    text: String,
    emojiSize: TextUnit = 24.sp,
    matchNames: Boolean = false
): Pair<AnnotatedString, Map<String, InlineTextContent>> {
    val context = LocalContext.current
    EmojiManager.initialize(context)
    val emojiIndexVersion = EmojiManager.emojiIndexVersion
    val (annotatedString, inlineContent) = remember(text, emojiSize, matchNames, emojiIndexVersion) {
        buildEmojiTextData(
            text = text,
            emojiSize = emojiSize,
            matchNames = matchNames
        )
    }
    return Pair(annotatedString, inlineContent)
}

private fun buildEmojiTextData(
    text: String,
    emojiSize: TextUnit,
    matchNames: Boolean
): Pair<AnnotatedString, Map<String, InlineTextContent>> {
    if (text.isEmpty()) {
        return Pair(AnnotatedString(text), emptyMap())
    }

    val matchTargets = collectEmojiMatchTargets(text, matchNames)
    val emojiMatches = findAllEmojiMatches(text, matchTargets)

    if (emojiMatches.isEmpty()) {
        return Pair(AnnotatedString(text), emptyMap())
    }

    val inlineContentMap = mutableMapOf<String, InlineTextContent>()
    val annotatedString = buildAnnotatedString {
        var currentPos = 0

        emojiMatches.forEach { match ->
            if (currentPos < match.startIndex) {
                append(text.substring(currentPos, match.startIndex))
            }

            val emojiId = generateEmojiId(match.emoji.key)
            appendInlineContent(emojiId, match.matchText)

            if (!inlineContentMap.containsKey(emojiId)) {
                inlineContentMap[emojiId] = createEmojiInlineContent(match.emoji, emojiSize)
            }

            currentPos = match.startIndex + match.matchText.length
        }

        if (currentPos < text.length) {
            append(text.substring(currentPos))
        }
    }

    return Pair(annotatedString, inlineContentMap)
}

private class EmojiMatch(
    val startIndex: Int,
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

private fun findAllEmojiMatches(text: String, matchTargets: List<Pair<String, Emoji>>): List<EmojiMatch> {
    val matches = mutableListOf<EmojiMatch>()

    var i = 0
    while (i < text.length) {
        var matched = false

        for ((matchText, emoji) in matchTargets) {
            if (i + matchText.length <= text.length &&
                text.substring(i, i + matchText.length) == matchText
            ) {
                matches.add(EmojiMatch(i, matchText, emoji))
                i += matchText.length
                matched = true
                break
            }
        }

        if (!matched) {
            i++
        }
    }

    return matches
}

private fun generateEmojiId(emojiKey: String): String {
    return "emoji_${emojiKey.hashCode()}"
}

private fun createEmojiInlineContent(
    emoji: Emoji,
    emojiSize: TextUnit
): InlineTextContent {
    return InlineTextContent(
        placeholder = Placeholder(
            width = emojiSize,
            height = emojiSize,
            placeholderVerticalAlign = PlaceholderVerticalAlign.Center
        )
    ) {
        EmojiInlineImage(
            emoji = emoji,
            size = emojiSize
        )
    }
}

fun emojiSizeForTextSize(textSize: TextUnit): TextUnit = textSize * 1.5f

@Composable
private fun EmojiInlineImage(
    emoji: Emoji,
    size: TextUnit
) {
    val context = LocalContext.current
    val sizeDp = with(LocalDensity.current) { size.toDp() }

    val cachedDrawable = remember(emoji.key) {
        EmojiManager.getCachedEmojiDrawable(emoji.key)
    }

    val painter = cachedDrawable?.let { drawable ->
        remember(drawable) {
            try {
                when (drawable) {
                    is android.graphics.drawable.BitmapDrawable -> {
                        val bitmap = drawable.bitmap
                        if (bitmap != null && !bitmap.isRecycled) {
                            BitmapPainter(bitmap.asImageBitmap())
                        } else {
                            null
                        }
                    }

                    else -> {
                        val bitmap = createBitmap(
                            drawable.intrinsicWidth.takeIf { it > 0 } ?: 64,
                            drawable.intrinsicHeight.takeIf { it > 0 } ?: 64
                        )
                        val canvas = android.graphics.Canvas(bitmap)
                        drawable.setBounds(0, 0, canvas.width, canvas.height)
                        drawable.draw(canvas)
                        BitmapPainter(bitmap.asImageBitmap())
                    }
                }
            } catch (e: Exception) {
                null
            }
        }
    }

    if (painter != null) {
        Image(
            painter = painter,
            contentDescription = emoji.emojiName,
            modifier = Modifier.size(sizeDp)
        )
    } else {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(emoji.emojiUrl)
                .crossfade(false)
                .memoryCacheKey(emoji.key)
                .diskCacheKey(emoji.key)
                .build(),
            contentDescription = emoji.emojiName,
            modifier = Modifier.size(sizeDp)
        )
    }
}

@Composable
fun rememberEmojiKeyToName(text: String): String {
    val context = LocalContext.current
    EmojiManager.initialize(context)
    val emojiIndexVersion = EmojiManager.emojiIndexVersion
    return remember(text, emojiIndexVersion) {
        replaceEmojiKeysWithNames(text)
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
