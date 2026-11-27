package io.trtc.tuikit.chat.uikit.components.messagelist.utils
import io.trtc.tuikit.chat.uikit.components.emojipicker.EmojiManager
import io.trtc.tuikit.chat.uikit.components.messageinput.model.MentionInfo
import java.util.regex.Pattern

object TranslationTextParser {
    const val KEY_SPLIT_STRING_RESULT = "result"
    const val KEY_SPLIT_STRING_TEXT = "text"
    const val KEY_SPLIT_STRING_TEXT_INDEX = "textIndex"

    fun splitTextByEmojiAndAtUsers(text: String, atUserNames: List<String>?): Map<String, Any>? {
        if (text.isEmpty()) {
            return null
        }

        val result = mutableListOf<String>()
        val atUsers = atUserNames?.map { "@$it " } ?: emptyList()
        val atUserRanges = rangeOfAtUsers(atUsers, text)
        val splitResult = splitArrayWithRanges(atUserRanges, text) ?: return null
        val splitArrayByAtUser = splitResult.first
        val atUserIndexArray = splitResult.second
        val atUserIndex = atUserIndexArray.toSet()
        val textIndexArray = mutableListOf<Int>()
        var outputIndex = -1

        for ((index, segment) in splitArrayByAtUser.withIndex()) {
            if (atUserIndex.contains(index)) {
                result.add(segment)
                outputIndex++
                continue
            }
            val emojiRanges = matchTextByEmoji(segment)
            val emojiSplitResult = splitArrayWithRanges(emojiRanges, segment)
            if (emojiSplitResult == null) {
                continue
            }
            val splitArrayByEmoji = emojiSplitResult.first
            val emojiIndex = emojiSplitResult.second.toSet()
            for (emojiSegmentIndex in splitArrayByEmoji.indices) {
                val item = splitArrayByEmoji[emojiSegmentIndex]
                result.add(item)
                outputIndex++
                if (!emojiIndex.contains(emojiSegmentIndex)) {
                    textIndexArray.add(outputIndex)
                }
            }
        }

        val textArray = textIndexArray.mapNotNull { output ->
            result.getOrNull(output)
        }
        return mapOf(
            KEY_SPLIT_STRING_RESULT to result,
            KEY_SPLIT_STRING_TEXT to textArray,
            KEY_SPLIT_STRING_TEXT_INDEX to textIndexArray
        )
    }

    fun replacedStringWithArray(
        array: List<String>,
        indexArray: List<Int>,
        replaceMap: Map<String, String>?
    ): String? {
        if (replaceMap == null) {
            return null
        }
        val mutableArray = array.toMutableList()
        for (index in indexArray) {
            if (index !in mutableArray.indices) {
                continue
            }
            replaceMap[mutableArray[index]]?.let { replacement ->
                mutableArray[index] = replacement
            }
        }
        return mutableArray.joinToString(separator = "")
    }

    fun getAtUserNames(
        atUserList: List<String>,
        displayNamesByUserID: Map<String, String>,
        allMembersText: String
    ): List<String>? {
        if (atUserList.isEmpty()) {
            return null
        }
        return atUserList.map { userID ->
            if (userID == MentionInfo.AT_ALL_USER_ID) {
                allMembersText
            } else {
                displayNamesByUserID[userID]?.takeIf { it.isNotBlank() } ?: userID
            }
        }
    }

    fun hasReusableTranslation(translatedTextMap: Map<String, String>?): Boolean {
        return !translatedTextMap.isNullOrEmpty()
    }

    private fun rangeOfAtUsers(atUsers: List<String>, text: String): List<IntRange> {
        val atIndexes = mutableSetOf<Int>()
        text.forEachIndexed { index, char ->
            if (char == '@') {
                atIndexes.add(index)
            }
        }
        val result = mutableListOf<IntRange>()
        for (atUser in atUsers) {
            val iterator = atIndexes.iterator()
            while (iterator.hasNext()) {
                val index = iterator.next()
                if (index > text.length - atUser.length) {
                    continue
                }
                if (text.substring(index, index + atUser.length) == atUser) {
                    result.add(index..(index + atUser.length - 1))
                    iterator.remove()
                }
            }
        }
        return result
    }

    private fun splitArrayWithRanges(
        ranges: List<IntRange>,
        text: String
    ): Pair<List<String>, List<Int>>? {
        if (text.isEmpty()) {
            return null
        }
        if (ranges.isEmpty()) {
            return Pair(listOf(text), emptyList())
        }

        val sortedRanges = ranges.sortedBy { it.first }
        val result = mutableListOf<String>()
        val indexes = mutableListOf<Int>()
        var previous = 0
        var outputIndex = -1

        for ((rangeIndex, currentRange) in sortedRanges.withIndex()) {
            if (currentRange.first > previous) {
                result.add(text.substring(previous, currentRange.first))
                outputIndex++
            }
            result.add(text.substring(currentRange.first, currentRange.last + 1))
            outputIndex++
            indexes.add(outputIndex)
            previous = currentRange.last + 1
            if (rangeIndex == sortedRanges.lastIndex && previous < text.length) {
                result.add(text.substring(previous))
            }
        }
        return Pair(result, indexes)
    }

    private fun matchTextByEmoji(text: String): List<IntRange> {
        val result = mutableListOf<IntRange>()
        runCatching {
            val customPattern = Pattern.compile(getRegexEmoji(), Pattern.CASE_INSENSITIVE)
            val customMatcher = customPattern.matcher(text)
            while (customMatcher.find()) {
                val matched = text.substring(customMatcher.start(), customMatcher.end())
                if (EmojiManager.sortedLittleEmojiKeyList.contains(matched)) {
                    result.add(customMatcher.start()..(customMatcher.end() - 1))
                }
            }
        }
        runCatching {
            val unicodePattern = Pattern.compile(unicodeEmojiReString(), Pattern.CASE_INSENSITIVE)
            val unicodeMatcher = unicodePattern.matcher(text)
            while (unicodeMatcher.find()) {
                result.add(unicodeMatcher.start()..(unicodeMatcher.end() - 1))
            }
        }
        return result
    }

    fun getRegexEmoji(): String {
        return "\\[[a-zA-Z0-9_\\u4e00-\\u9fa5]+\\]"
    }

    fun unicodeEmojiReString(): String {
        val support = "\\u00A9|\\u00AE|\\u203C|\\u2049|\\u2122|\\u2139|[\\u2194-\\u2199]|[\\u21A9-\\u21AA]|[\\u231A-\\u231B]|\\u2328|\\u23CF|[\\u23E9-\\u23EF]|[\\u23F0-\\u23F3]|[\\u23F8-\\u23FA]|\\u24C2|[\\u25AA-\\u25AB]|\\u25B6|\\u25C0|[\\u25FB-\\u25FE]|[\\u2600-\\u2604]|\\u260E|\\u2611|[\\u2614-\\u2615]|\\u2618|\\u261D|\\u2620|[\\u2622-\\u2623]|\\u2626|\\u262A|[\\u262E-\\u262F]|[\\u2638-\\u263A]|\\u2640|\\u2642|[\\u2648-\\u264F]|[\\u2650-\\u2653]|\\u265F|\\u2660|\\u2663|[\\u2665-\\u2666]|\\u2668|\\u267B|[\\u267E-\\u267F]|[\\u2692-\\u2697]|\\u2699|[\\u269B-\\u269C]|[\\u26A0-\\u26A1]|\\u26A7|[\\u26AA-\\u26AB]|[\\u26B0-\\u26B1]|[\\u26BD-\\u26BE]|[\\u26C4-\\u26C5]|\\u26C8|[\\u26CE-\\u26CF]|\\u26D1|[\\u26D3-\\u26D4]|[\\u26E9-\\u26EA]|[\\u26F0-\\u26F5]|[\\u26F7-\\u26FA]|\\u26FD|\\u2702|\\u2705|[\\u2708-\\u270D]|\\u270F|\\u2712|\\u2714|\\u2716|\\u271D|\\u2721|\\u2728|[\\u2733-\\u2734]|\\u2744|\\u2747|\\u274C|\\u274E|[\\u2753-\\u2755]|\\u2757|[\\u2763-\\u2764]|[\\u2795-\\u2797]|\\u27A1|\\u27B0|\\u27BF|[\\u2934-\\u2935]|[\\u2B05-\\u2B07]|[\\u2B1B-\\u2B1C]|\\u2B50|\\u2B55|\\u3030|\\u303D|\\u3297|\\u3299"
        val emoji = "[$support]"
        val modifier = "[\\u1F3FB-\\u1F3FF]"
        val variationSelector = "\\uFE0F"
        val keyCap = "\\u20E3"
        val zwj = "\\u200D"
        val element = "[$emoji]($modifier|$variationSelector$keyCap?)?"
        return "$element($zwj$element)*"
    }
}
