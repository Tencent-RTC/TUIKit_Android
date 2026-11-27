package com.trtc.uikit.livekit.common.barrage.emoji

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.text.Editable
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ReplacementSpan
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import com.trtc.uikit.livekit.R
import java.text.BreakIterator
import kotlin.math.ceil

internal data class BarrageEmoji(
    val token: String,
    @DrawableRes val drawableRes: Int,
) {
    companion object {
        val all: List<BarrageEmoji> = listOf(
            BarrageEmoji("[TUIEmoji_Smile]", R.drawable.live_barrage_emoji_0),
            BarrageEmoji("[TUIEmoji_Expect]", R.drawable.live_barrage_emoji_1),
            BarrageEmoji("[TUIEmoji_Blink]", R.drawable.live_barrage_emoji_2),
            BarrageEmoji("[TUIEmoji_Guffaw]", R.drawable.live_barrage_emoji_3),
            BarrageEmoji("[TUIEmoji_KindSmile]", R.drawable.live_barrage_emoji_4),
            BarrageEmoji("[TUIEmoji_Haha]", R.drawable.live_barrage_emoji_5),
            BarrageEmoji("[TUIEmoji_Cheerful]", R.drawable.live_barrage_emoji_6),
            BarrageEmoji("[TUIEmoji_Speechless]", R.drawable.live_barrage_emoji_7),
            BarrageEmoji("[TUIEmoji_Amazed]", R.drawable.live_barrage_emoji_8),
            BarrageEmoji("[TUIEmoji_Sorrow]", R.drawable.live_barrage_emoji_9),
            BarrageEmoji("[TUIEmoji_Complacent]", R.drawable.live_barrage_emoji_10),
            BarrageEmoji("[TUIEmoji_Silly]", R.drawable.live_barrage_emoji_11),
            BarrageEmoji("[TUIEmoji_Lustful]", R.drawable.live_barrage_emoji_12),
            BarrageEmoji("[TUIEmoji_Giggle]", R.drawable.live_barrage_emoji_13),
            BarrageEmoji("[TUIEmoji_Kiss]", R.drawable.live_barrage_emoji_14),
            BarrageEmoji("[TUIEmoji_Wail]", R.drawable.live_barrage_emoji_15),
            BarrageEmoji("[TUIEmoji_TearsLaugh]", R.drawable.live_barrage_emoji_16),
            BarrageEmoji("[TUIEmoji_Trapped]", R.drawable.live_barrage_emoji_17),
            BarrageEmoji("[TUIEmoji_Mask]", R.drawable.live_barrage_emoji_18),
            BarrageEmoji("[TUIEmoji_Fear]", R.drawable.live_barrage_emoji_19),
            BarrageEmoji("[TUIEmoji_BareTeeth]", R.drawable.live_barrage_emoji_20),
            BarrageEmoji("[TUIEmoji_FlareUp]", R.drawable.live_barrage_emoji_21),
            BarrageEmoji("[TUIEmoji_Yawn]", R.drawable.live_barrage_emoji_22),
            BarrageEmoji("[TUIEmoji_Tact]", R.drawable.live_barrage_emoji_23),
            BarrageEmoji("[TUIEmoji_Stareyes]", R.drawable.live_barrage_emoji_24),
            BarrageEmoji("[TUIEmoji_ShutUp]", R.drawable.live_barrage_emoji_25),
            BarrageEmoji("[TUIEmoji_Sigh]", R.drawable.live_barrage_emoji_26),
            BarrageEmoji("[TUIEmoji_Hehe]", R.drawable.live_barrage_emoji_27),
            BarrageEmoji("[TUIEmoji_Silent]", R.drawable.live_barrage_emoji_28),
            BarrageEmoji("[TUIEmoji_Surprised]", R.drawable.live_barrage_emoji_29),
            BarrageEmoji("[TUIEmoji_Askance]", R.drawable.live_barrage_emoji_30),
            BarrageEmoji("[TUIEmoji_Ok]", R.drawable.live_barrage_emoji_31),
            BarrageEmoji("[TUIEmoji_Shit]", R.drawable.live_barrage_emoji_32),
            BarrageEmoji("[TUIEmoji_Monster]", R.drawable.live_barrage_emoji_33),
            BarrageEmoji("[TUIEmoji_Daemon]", R.drawable.live_barrage_emoji_34),
            BarrageEmoji("[TUIEmoji_Rage]", R.drawable.live_barrage_emoji_35),
            BarrageEmoji("[TUIEmoji_Fool]", R.drawable.live_barrage_emoji_36),
            BarrageEmoji("[TUIEmoji_Pig]", R.drawable.live_barrage_emoji_37),
            BarrageEmoji("[TUIEmoji_Cow]", R.drawable.live_barrage_emoji_38),
            BarrageEmoji("[TUIEmoji_Ai]", R.drawable.live_barrage_emoji_39),
            BarrageEmoji("[TUIEmoji_Skull]", R.drawable.live_barrage_emoji_40),
            BarrageEmoji("[TUIEmoji_Bombs]", R.drawable.live_barrage_emoji_41),
            BarrageEmoji("[TUIEmoji_Coffee]", R.drawable.live_barrage_emoji_42),
            BarrageEmoji("[TUIEmoji_Cake]", R.drawable.live_barrage_emoji_43),
            BarrageEmoji("[TUIEmoji_Beer]", R.drawable.live_barrage_emoji_44),
            BarrageEmoji("[TUIEmoji_Flower]", R.drawable.live_barrage_emoji_45),
            BarrageEmoji("[TUIEmoji_Watermelon]", R.drawable.live_barrage_emoji_46),
            BarrageEmoji("[TUIEmoji_Rich]", R.drawable.live_barrage_emoji_47),
            BarrageEmoji("[TUIEmoji_Heart]", R.drawable.live_barrage_emoji_48),
            BarrageEmoji("[TUIEmoji_Moon]", R.drawable.live_barrage_emoji_49),
            BarrageEmoji("[TUIEmoji_Sun]", R.drawable.live_barrage_emoji_50),
            BarrageEmoji("[TUIEmoji_Star]", R.drawable.live_barrage_emoji_51),
            BarrageEmoji("[TUIEmoji_RedPacket]", R.drawable.live_barrage_emoji_52),
            BarrageEmoji("[TUIEmoji_Celebrate]", R.drawable.live_barrage_emoji_53),
            BarrageEmoji("[TUIEmoji_Bless]", R.drawable.live_barrage_emoji_54),
            BarrageEmoji("[TUIEmoji_Fortune]", R.drawable.live_barrage_emoji_55),
            BarrageEmoji("[TUIEmoji_Convinced]", R.drawable.live_barrage_emoji_56),
            BarrageEmoji("[TUIEmoji_Prohibit]", R.drawable.live_barrage_emoji_57),
            BarrageEmoji("[TUIEmoji_666]", R.drawable.live_barrage_emoji_58),
            BarrageEmoji("[TUIEmoji_857]", R.drawable.live_barrage_emoji_59),
            BarrageEmoji("[TUIEmoji_Knife]", R.drawable.live_barrage_emoji_60),
            BarrageEmoji("[TUIEmoji_Like]", R.drawable.live_barrage_emoji_61),
        )
    }
}

internal object BarrageEmojiConverter {
    private sealed interface Segment {
        data class Text(val value: String) : Segment
        data class Emoji(val emoji: BarrageEmoji) : Segment
    }

    private val emojiByToken = BarrageEmoji.all.associateBy(BarrageEmoji::token)

    fun isKnownToken(token: String): Boolean = emojiByToken.containsKey(token)

    fun drawable(context: Context, emoji: BarrageEmoji): Drawable? =
        ContextCompat.getDrawable(context, emoji.drawableRes)

    fun parse(context: Context, text: String, paint: Paint): SpannableStringBuilder {
        val result = SpannableStringBuilder()
        for (segment in segments(text)) {
            when (segment) {
                is Segment.Text -> result.append(segment.value)
                is Segment.Emoji -> result.append(attachment(context, segment.emoji, paint) ?: segment.emoji.token)
            }
        }
        return result
    }

    fun attachment(context: Context, emoji: BarrageEmoji, paint: Paint): SpannableStringBuilder? {
        val drawable = drawable(context, emoji) ?: return null
        return SpannableStringBuilder("\uFFFC").apply {
            setSpan(BarrageEmojiSpan(emoji.token, drawable, paint), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    fun serialize(text: Spanned): String {
        val result = SpannableStringBuilder(text)
        val spans = result.getSpans(0, result.length, BarrageEmojiSpan::class.java)
        spans.sortedByDescending(result::getSpanStart).forEach { span ->
            val start = result.getSpanStart(span)
            val end = result.getSpanEnd(span)
            if (start >= 0 && end >= start) result.replace(start, end, span.token)
        }
        return result.toString()
    }

    fun deleteBackward(text: Editable, selectionStart: Int, selectionEnd: Int): Int? {
        if (selectionStart !in 0..text.length || selectionEnd !in 0..text.length) return null
        if (selectionStart != selectionEnd) {
            val start = minOf(selectionStart, selectionEnd)
            text.delete(start, maxOf(selectionStart, selectionEnd))
            return start
        }
        if (selectionStart == 0) return 0
        val span = text.getSpans(0, text.length, BarrageEmojiSpan::class.java).firstOrNull {
            text.getSpanEnd(it) == selectionStart
        }
        if (span != null) {
            val start = text.getSpanStart(span)
            text.delete(start, selectionStart)
            return start
        }
        val value = text.toString()
        val iterator = BreakIterator.getCharacterInstance().apply { setText(value) }
        var start = iterator.preceding(selectionStart)
        while (start > 0 && continuesCharacter(value, start)) start = iterator.preceding(start)
        if (start == BreakIterator.DONE) return null
        text.delete(start, selectionStart)
        return start
    }

    fun visibleLength(text: String): Int = segments(text).sumOf { segment ->
        when (segment) {
            is Segment.Text -> characterCount(segment.value)
            is Segment.Emoji -> 1
        }
    }

    fun visiblePrefix(text: String, limit: Int): String {
        if (limit <= 0) return ""
        val result = StringBuilder()
        var count = 0
        for (segment in segments(text)) {
            when (segment) {
                is Segment.Text -> {
                    val remaining = limit - count
                    if (remaining <= 0) return result.toString()
                    val prefix = characterPrefix(segment.value, remaining)
                    result.append(prefix)
                    count += characterCount(prefix)
                    if (prefix.length < segment.value.length) return result.toString()
                }

                is Segment.Emoji -> {
                    if (count >= limit) return result.toString()
                    result.append(segment.emoji.token)
                    count += 1
                }
            }
        }
        return result.toString()
    }

    private fun segments(text: String): List<Segment> {
        val result = mutableListOf<Segment>()
        var current = 0
        while (current < text.length) {
            val opening = text.indexOf('[', current)
            if (opening < 0) {
                result.add(Segment.Text(text.substring(current)))
                break
            }
            if (opening > current) result.add(Segment.Text(text.substring(current, opening)))
            val closing = text.indexOf(']', opening + 1)
            if (closing < 0) {
                result.add(Segment.Text(text.substring(opening)))
                break
            }
            val token = text.substring(opening, closing + 1)
            val emoji = emojiByToken[token]
            result.add(if (emoji == null) Segment.Text(token) else Segment.Emoji(emoji))
            current = closing + 1
        }
        return result
    }

    fun plainPrefix(text: String, limit: Int): String = characterPrefix(text, limit)

    private fun characterCount(text: String): Int {
        val iterator = BreakIterator.getCharacterInstance().apply { setText(text) }
        iterator.first()
        var end = iterator.next()
        var count = 0
        while (end != BreakIterator.DONE) {
            if (!continuesCharacter(text, end)) count += 1
            end = iterator.next()
        }
        return count
    }

    private fun characterPrefix(text: String, limit: Int): String {
        if (limit <= 0) return ""
        val iterator = BreakIterator.getCharacterInstance().apply { setText(text) }
        iterator.first()
        var count = 0
        while (true) {
            val end = iterator.next()
            if (end == BreakIterator.DONE) return text
            if (!continuesCharacter(text, end)) {
                count += 1
                if (count == limit) return text.substring(0, end)
            }
        }
    }

    private fun continuesCharacter(text: String, boundary: Int): Boolean {
        if (boundary <= 0 || boundary >= text.length) return false
        val previous = text.codePointBefore(boundary)
        val next = text.codePointAt(boundary)
        val type = Character.getType(next)
        if (previous == 0x200D || next == 0x200D || previous in 0xFE00..0xFE0F || next in 0xFE00..0xFE0F || previous == 0x20E3 || next == 0x20E3 || next in 0x1F3FB..0x1F3FF || next in 0xE0020..0xE007F || type == Character.NON_SPACING_MARK.toInt() || type == Character.COMBINING_SPACING_MARK.toInt() || type == Character.ENCLOSING_MARK.toInt()) return true
        if (next !in 0x1F1E6..0x1F1FF) return false
        var position = boundary
        var regionalIndicators = 0
        while (position > 0) {
            val codePoint = text.codePointBefore(position)
            if (codePoint !in 0x1F1E6..0x1F1FF) break
            regionalIndicators += 1
            position -= Character.charCount(codePoint)
        }
        return regionalIndicators % 2 == 1
    }
}

private class BarrageEmojiSpan(
    val token: String,
    private val drawable: Drawable,
    paint: Paint,
) : ReplacementSpan() {
    private val size = paint.fontMetrics.let { ceil(it.descent - it.ascent).toInt().coerceAtLeast(1) }

    init {
        drawable.setBounds(0, 0, drawable.intrinsicWidth, drawable.intrinsicHeight)
    }

    override fun getSize(
        paint: Paint,
        text: CharSequence?,
        start: Int,
        end: Int,
        metrics: Paint.FontMetricsInt?,
    ): Int {
        if (metrics != null) {
            val fontMetrics = paint.fontMetricsInt
            val lineHeight = fontMetrics.descent - fontMetrics.ascent
            val extra = (size - lineHeight) / 2
            metrics.ascent = fontMetrics.ascent - extra
            metrics.top = fontMetrics.top - extra
            metrics.descent = fontMetrics.descent + extra
            metrics.bottom = fontMetrics.bottom + extra
            metrics.leading = fontMetrics.leading
        }
        return size
    }

    override fun draw(
        canvas: Canvas,
        text: CharSequence?,
        start: Int,
        end: Int,
        x: Float,
        top: Int,
        y: Int,
        bottom: Int,
        paint: Paint,
    ) {
        if (drawable.intrinsicWidth <= 0 || drawable.intrinsicHeight <= 0) return
        val scale = minOf(size.toFloat() / drawable.intrinsicWidth, size.toFloat() / drawable.intrinsicHeight)
        val scaledWidth = drawable.intrinsicWidth * scale
        val scaledHeight = drawable.intrinsicHeight * scale
        val fontMetrics = paint.fontMetricsInt
        val lineHeight = fontMetrics.descent - fontMetrics.ascent
        canvas.save()
        canvas.translate(x + (size - scaledWidth) / 2f, y + fontMetrics.ascent + (lineHeight - scaledHeight) / 2f)
        canvas.scale(scale, scale)
        drawable.draw(canvas)
        canvas.restore()
    }
}
