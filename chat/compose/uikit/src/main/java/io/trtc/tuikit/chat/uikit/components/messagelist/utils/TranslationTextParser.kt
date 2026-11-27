package io.trtc.tuikit.chat.uikit.components.messagelist.utils

import io.trtc.tuikit.chat.uikit.components.emojipicker.EmojiManager
import java.util.regex.Pattern

/**
 * Parser for text translation that handles emoji and @ mentions.
 * This implementation mirrors iOS's TranslationTextParser logic.
 */
object TranslationTextParser {
    const val KEY_SPLIT_STRING_RESULT = "result"
    const val KEY_SPLIT_STRING_TEXT = "text"
    const val KEY_SPLIT_STRING_TEXT_INDEX = "textIndex"

    /**
     * Parse text message and return components for translation.
     * @param text The original text to parse
     * @param atUserNames List of @ user names (without @ prefix)
     * @return Map with "result", "text", and "textIndex" keys
     */
    fun splitTextByEmojiAndAtUsers(text: String, atUserNames: List<String>?): Map<String, Any>? {
        if (text.isEmpty()) return null

        val result = mutableListOf<String>()

        // Build @user strings with @ prefix and trailing space
        val atUsers = atUserNames?.map { "@$it " } ?: emptyList()

        // Find @user ranges in string
        val atUserRanges = rangeOfAtUsers(atUsers, text)

        // Split text using @user ranges
        val splitResult = splitArrayWithRanges(atUserRanges, text) ?: return null
        val splitArrayByAtUser = splitResult.first
        val atUserIndexArray = splitResult.second
        val atUserIndex = atUserIndexArray.toSet()

        // Iterate split array to match emoji in non-@ parts
        var k = -1
        val textIndexArray = mutableListOf<Int>()

        for ((i, str) in splitArrayByAtUser.withIndex()) {
            if (atUserIndex.contains(i)) {
                // str is @user info, keep as-is
                result.add(str)
                k++
            } else {
                // str is not @user info, parse emoji
                val emojiRanges = matchTextByEmoji(str)
                val emojiSplitResult = splitArrayWithRanges(emojiRanges, str)
                if (emojiSplitResult != null) {
                    val splitArrayByEmoji = emojiSplitResult.first
                    val emojiIndex = emojiSplitResult.second.toSet()

                    for (j in splitArrayByEmoji.indices) {
                        val tmp = splitArrayByEmoji[j]
                        result.add(tmp)
                        k++
                        if (!emojiIndex.contains(j)) {
                            // This is text that needs translation
                            textIndexArray.add(k)
                        }
                    }
                }
            }
        }

        // Extract text array from result using indices
        val textArray = textIndexArray.mapNotNull { n ->
            if (n < result.size) result[n] else null
        }

        return mapOf(
            KEY_SPLIT_STRING_RESULT to result,
            KEY_SPLIT_STRING_TEXT to textArray,
            KEY_SPLIT_STRING_TEXT_INDEX to textIndexArray
        )
    }

    /**
     * Reconstruct translated text by replacing text segments with translations.
     * @param array The result array from splitTextByEmojiAndAtUsers
     * @param indexArray The textIndex array from splitTextByEmojiAndAtUsers
     * @param replaceDict Map mapping original text to translated text
     * @return Reconstructed string with translations
     */
    fun replacedStringWithArray(
        array: List<String>,
        indexArray: List<Int>,
        replaceDict: Map<String, String>?
    ): String? {
        if (replaceDict == null) return null
        val mutableArray = array.toMutableList()

        for (value in indexArray) {
            if (value < 0 || value >= mutableArray.size) continue
            val replacement = replaceDict[mutableArray[value]]
            if (replacement != null) {
                mutableArray[value] = replacement
            }
        }

        return mutableArray.joinToString("")
    }

    /**
     * Find ranges of @user strings in text.
     */
    private fun rangeOfAtUsers(atUsers: List<String>, string: String): List<IntRange> {
        // Find all '@' positions
        val atIndex = mutableSetOf<Int>()
        for ((i, char) in string.withIndex()) {
            if (char == '@') {
                atIndex.add(i)
            }
        }

        val result = mutableListOf<IntRange>()
        for (user in atUsers) {
            val iterator = atIndex.iterator()
            while (iterator.hasNext()) {
                val idx = iterator.next()
                if (string.length >= user.length && idx <= string.length - user.length) {
                    val substring = string.substring(idx, idx + user.length)
                    if (substring == user) {
                        result.add(IntRange(idx, idx + user.length - 1))
                        iterator.remove()
                    }
                }
            }
        }
        return result
    }

    /**
     * Split string into substrings by given ranges.
     * Returns Pair of (result array, indexes of special elements in result).
     */
    private fun splitArrayWithRanges(ranges: List<IntRange>, string: String): Pair<List<String>, List<Int>>? {
        if (ranges.isEmpty()) return Pair(listOf(string), emptyList())
        if (string.isEmpty()) return null

        val sortedRanges = ranges.sortedBy { it.first }

        val result = mutableListOf<String>()
        val indexes = mutableListOf<Int>()
        var prev = 0
        var j = -1

        for ((i, cur) in sortedRanges.withIndex()) {
            // Add text before current range
            if (cur.first > prev) {
                val str = string.substring(prev, cur.first)
                result.add(str)
                j++
            }

            // Add content within current range (special element)
            val str = string.substring(cur.first, cur.last + 1)
            result.add(str)
            j++
            indexes.add(j)

            prev = cur.last + 1

            // Handle text after last range
            if (i == sortedRanges.lastIndex && prev < string.length) {
                val last = string.substring(prev)
                result.add(last)
            }
        }

        return Pair(result, indexes)
    }

    /**
     * Match emoji in text (both TUIKit custom and Unicode emoji).
     */
    private fun matchTextByEmoji(text: String): List<IntRange> {
        val result = mutableListOf<IntRange>()

        // TUIKit custom emoji: \[[a-zA-Z0-9_\u4e00-\u9fa5]+\]
        try {
            val regexOfCustomEmoji = getRegexEmoji()
            val pattern = Pattern.compile(regexOfCustomEmoji, Pattern.CASE_INSENSITIVE)
            val matcher = pattern.matcher(text)

            while (matcher.find()) {
                val substring = text.substring(matcher.start(), matcher.end())
                // Validate against registered emoji
                if (isRegisteredEmoji(substring)) {
                    result.add(IntRange(matcher.start(), matcher.end() - 1))
                }
            }
        } catch (e: Exception) {
            // Ignore regex errors
        }

        // Unicode emoji
        try {
            val regexOfUnicodeEmoji = unicodeEmojiReString()
            val pattern = Pattern.compile(regexOfUnicodeEmoji, Pattern.CASE_INSENSITIVE)
            val matcher = pattern.matcher(text)

            while (matcher.find()) {
                result.add(IntRange(matcher.start(), matcher.end() - 1))
            }
        } catch (e: Exception) {
            // Ignore regex errors
        }

        return result
    }

    /**
     * Check if a string is a registered TUIKit emoji.
     */
    private fun isRegisteredEmoji(name: String): Boolean {
        // Check against EmojiManager's registered emoji
        return EmojiManager.littleEmojiKeyList.contains(name)
    }

    /**
     * Regex pattern for TUIKit custom emoji.
     */
    fun getRegexEmoji(): String {
        return "\\[[a-zA-Z0-9_\\u4e00-\\u9fa5]+\\]"
    }

    /**
     * Regex pattern for Unicode emoji.
     */
    fun unicodeEmojiReString(): String {
        val ri = "[\u1F1E6-\u1F1FF]"

        val support = "\u00A9|\u00AE|\u203C|\u2049|\u2122|\u2139|[\u2194-\u2199]|[\u21A9-\u21AA]|[\u231A-\u231B]|\u2328|\u23CF|[\u23E9-\u23EF]|[\u23F0-\u23F3]|[\u23F8-\u23FA]|\u24C2|[\u25AA-\u25AB]|\u25B6|\u25C0|[\u25FB-\u25FE]|[\u2600-\u2604]|\u260E|\u2611|[\u2614-\u2615]|\u2618|\u261D|\u2620|[\u2622-\u2623]|\u2626|\u262A|[\u262E-\u262F]|[\u2638-\u263A]|\u2640|\u2642|[\u2648-\u264F]|[\u2650-\u2653]|\u265F|\u2660|\u2663|[\u2665-\u2666]|\u2668|\u267B|[\u267E-\u267F]|[\u2692-\u2697]|\u2699|[\u269B-\u269C]|[\u26A0-\u26A1]|\u26A7|[\u26AA-\u26AB]|[\u26B0-\u26B1]|[\u26BD-\u26BE]|[\u26C4-\u26C5]|\u26C8|[\u26CE-\u26CF]|\u26D1|[\u26D3-\u26D4]|[\u26E9-\u26EA]|[\u26F0-\u26F5]|[\u26F7-\u26FA]|\u26FD|\u2702|\u2705|[\u2708-\u270D]|\u270F|\u2712|\u2714|\u2716|\u271D|\u2721|\u2728|[\u2733-\u2734]|\u2744|\u2747|\u274C|\u274E|[\u2753-\u2755]|\u2757|[\u2763-\u2764]|[\u2795-\u2797]|\u27A1|\u27B0|\u27BF|[\u2934-\u2935]|[\u2B05-\u2B07]|[\u2B1B-\u2B1C]|\u2B50|\u2B55|\u3030|\u303D|\u3297|\u3299"
        val emoji = "[$support]"

        val eMod = "[\u1F3FB-\u1F3FF]"
        val variationSelector = "\uFE0F"
        val keycap = "\u20E3"
        val zwj = "\u200D"

        val element = "[$emoji]($eMod|$variationSelector$keycap?)?"

        return "$element($zwj$element)*"
    }
}
