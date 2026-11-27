package io.trtc.tuikit.chat.uikit.components.ai.tts
import io.trtc.tuikit.chat.uikit.components.emojipicker.EmojiManager

// Strips emoji from text so they are not spoken by TTS. Mirrors the Flutter
// EmojiManager.findEmojiKeyListFromText logic: IM custom emoji tokens (e.g.
// "[TUIEmoji_xxx]") are removed first, then any "[...]" token, then universal
// unicode emoji matched by a ported version of the Flutter emoji regex.
object TtsTextSanitizer {

    fun sanitize(text: String): String {
        if (text.isEmpty()) return text
        var result = text
        // Registered custom emoji keys first (longest-first to avoid partial-token
        // corruption), matching the Android EmojiManager key table.
        for (key in EmojiManager.sortedLittleEmojiKeyList) {
            if (key.isEmpty()) continue
            if (result.contains(key)) {
                result = result.replace(key, "")
            }
        }
        // Regex fallback removes any remaining custom emoji token "[\S+?]".
        result = result.replace(CUSTOM_EMOJI_REGEX, "")
        // Universal unicode emoji.
        result = result.replace(UNIVERSAL_EMOJI_REGEX, "")
        result = result.replace(WHITESPACE_REGEX, " ").trim()
        return result
    }

    fun isBlankAfterSanitize(text: String): Boolean {
        return sanitize(text).isBlank()
    }

    private val WHITESPACE_REGEX = Regex("\\s+")

    private val CUSTOM_EMOJI_REGEX = Regex("\\[\\S+?\\]")

    private val UNIVERSAL_EMOJI_REGEX = Regex(buildUniversalEmojiPattern())

    // Ported from Flutter EmojiManager.getRegexOfUniversalEmoji(). Dart code-point
    // escapes "\u{XXXXX}" are rewritten as Java/Kotlin "\x{XXXXX}"; four-digit
    // "\uXXXX" escapes are kept as-is (supported by java.util.regex.Pattern).
    private fun buildUniversalEmojiPattern(): String {
        val ri = "[\\x{1F1E6}-\\x{1F1FF}]"

        val support = "\\x{A9}|\\x{AE}|\\u203C|\\u2049|\\u2122|\\u2139|[\\u2194-\\u2199]|[\\u21A9-\\u21AA]" +
            "|[\\u231A-\\u231B]|\\u2328|\\u23CF|[\\u23E9-\\u23EF]|[\\u23F0-\\u23F3]|[\\u23F8-\\u23FA]|\\u24C2" +
            "|[\\u25AA-\\u25AB]|\\u25B6|\\u25C0|[\\u25FB-\\u25FE]|[\\u2600-\\u2604]|\\u260E|\\u2611|[\\u2614-\\u2615]" +
            "|\\u2618|\\u261D|\\u2620|[\\u2622-\\u2623]|\\u2626|\\u262A|[\\u262E-\\u262F]|[\\u2638-\\u263A]|\\u2640" +
            "|\\u2642|[\\u2648-\\u264F]|[\\u2650-\\u2653]|\\u265F|\\u2660|\\u2663|[\\u2665-\\u2666]|\\u2668|\\u267B" +
            "|[\\u267E-\\u267F]|[\\u2692-\\u2697]|\\u2699|[\\u269B-\\u269C]|[\\u26A0-\\u26A1]|\\u26A7|[\\u26AA-\\u26AB]" +
            "|[\\u26B0-\\u26B1]|[\\u26BD-\\u26BE]|[\\u26C4-\\u26C5]|\\u26C8|[\\u26CE-\\u26CF]|\\u26D1|[\\u26D3-\\u26D4]" +
            "|[\\u26E9-\\u26EA]|[\\u26F0-\\u26F5]|[\\u26F7-\\u26FA]|\\u26FD|\\u2702|\\u2705|[\\u2708-\\u270D]|\\u270F|\\u2712" +
            "|\\u2714|\\u2716|\\u271D|\\u2721|\\u2728|[\\u2733-\\u2734]|\\u2744|\\u2747|\\u274C|\\u274E|[\\u2753-\\u2755]" +
            "|\\u2757|[\\u2763-\\u2764]|[\\u2795-\\u2797]|\\u27A1|\\u27B0|\\u27BF|[\\u2934-\\u2935]|[\\u2B05-\\u2B07]" +
            "|[\\u2B1B-\\u2B1C]|\\u2B50|\\u2B55|\\u3030|\\u303D|\\u3297|\\u3299|\\x{1F004}|\\x{1F0CF}|[\\x{1F170}-\\x{1F171}]" +
            "|[\\x{1F17E}-\\x{1F17F}]|\\x{1F18E}|[\\x{1F191}-\\x{1F19A}]|[\\x{1F1E6}-\\x{1F1FF}]|[\\x{1F201}-\\x{1F202}]" +
            "|\\x{1F21A}|\\x{1F22F}|[\\x{1F232}-\\x{1F23A}]|[\\x{1F250}-\\x{1F251}]|[\\x{1F300}-\\x{1F30F}]" +
            "|[\\x{1F310}-\\x{1F31F}]|[\\x{1F320}-\\x{1F321}]|[\\x{1F324}-\\x{1F32F}]|[\\x{1F330}-\\x{1F33F}]" +
            "|[\\x{1F340}-\\x{1F34F}]|[\\x{1F350}-\\x{1F35F}]|[\\x{1F360}-\\x{1F36F}]|[\\x{1F370}-\\x{1F37F}]" +
            "|[\\x{1F380}-\\x{1F38F}]|[\\x{1F390}-\\x{1F393}]|[\\x{1F396}-\\x{1F397}]|[\\x{1F399}-\\x{1F39B}]" +
            "|[\\x{1F39E}-\\x{1F39F}]|[\\x{1F3A0}-\\x{1F3AF}]|[\\x{1F3B0}-\\x{1F3BF}]|[\\x{1F3C0}-\\x{1F3CF}]" +
            "|[\\x{1F3D0}-\\x{1F3DF}]|[\\x{1F3E0}-\\x{1F3EF}]|\\x{1F3F0}|[\\x{1F3F3}-\\x{1F3F5}]|[\\x{1F3F7}-\\x{1F3FF}]" +
            "|[\\x{1F400}-\\x{1F40F}]|[\\x{1F410}-\\x{1F41F}]|[\\x{1F420}-\\x{1F42F}]|[\\x{1F430}-\\x{1F43F}]" +
            "|[\\x{1F440}-\\x{1F44F}]|[\\x{1F450}-\\x{1F45F}]|[\\x{1F460}-\\x{1F46F}]|[\\x{1F470}-\\x{1F47F}]" +
            "|[\\x{1F480}-\\x{1F48F}]|[\\x{1F490}-\\x{1F49F}]|[\\x{1F4A0}-\\x{1F4AF}]|[\\x{1F4B0}-\\x{1F4BF}]" +
            "|[\\x{1F4C0}-\\x{1F4CF}]|[\\x{1F4D0}-\\x{1F4DF}]|[\\x{1F4E0}-\\x{1F4EF}]|[\\x{1F4F0}-\\x{1F4FF}]" +
            "|[\\x{1F500}-\\x{1F50F}]|[\\x{1F510}-\\x{1F51F}]|[\\x{1F520}-\\x{1F52F}]|[\\x{1F530}-\\x{1F53D}]" +
            "|[\\x{1F549}-\\x{1F54E}]|[\\x{1F550}-\\x{1F55F}]|[\\x{1F560}-\\x{1F567}]|\\x{1F56F}|\\x{1F570}" +
            "|[\\x{1F573}-\\x{1F57A}]|\\x{1F587}|[\\x{1F58A}-\\x{1F58D}]|\\x{1F590}|[\\x{1F595}-\\x{1F596}]" +
            "|[\\x{1F5A4}-\\x{1F5A5}]|\\x{1F5A8}|[\\x{1F5B1}-\\x{1F5B2}]|\\x{1F5BC}|[\\x{1F5C2}-\\x{1F5C4}]" +
            "|[\\x{1F5D1}-\\x{1F5D3}]|[\\x{1F5DC}-\\x{1F5DE}]|\\x{1F5E1}|\\x{1F5E3}|\\x{1F5E8}|\\x{1F5EF}|\\x{1F5F3}" +
            "|[\\x{1F5FA}-\\x{1F5FF}]|[\\x{1F600}-\\x{1F60F}]|[\\x{1F610}-\\x{1F61F}]|[\\x{1F620}-\\x{1F62F}]" +
            "|[\\x{1F630}-\\x{1F63F}]|[\\x{1F640}-\\x{1F64F}]|[\\x{1F650}-\\x{1F65F}]|[\\x{1F660}-\\x{1F66F}]" +
            "|[\\x{1F670}-\\x{1F67F}]|[\\x{1F680}-\\x{1F68F}]|[\\x{1F690}-\\x{1F69F}]|[\\x{1F6A0}-\\x{1F6AF}]" +
            "|[\\x{1F6B0}-\\x{1F6BF}]|[\\x{1F6C0}-\\x{1F6C5}]|[\\x{1F6CB}-\\x{1F6CF}]|[\\x{1F6D0}-\\x{1F6D2}]" +
            "|[\\x{1F6D5}-\\x{1F6D7}]|[\\x{1F6DD}-\\x{1F6DF}]|[\\x{1F6E0}-\\x{1F6E5}]|\\x{1F6E9}|[\\x{1F6EB}-\\x{1F6EC}]" +
            "|\\x{1F6F0}|[\\x{1F6F3}-\\x{1F6FC}]|[\\x{1F7E0}-\\x{1F7EB}]|\\x{1F7F0}|[\\x{1F90C}-\\x{1F90F}]" +
            "|[\\x{1F910}-\\x{1F91F}]|[\\x{1F920}-\\x{1F92F}]|[\\x{1F930}-\\x{1F93A}]|[\\x{1F93C}-\\x{1F93F}]" +
            "|[\\x{1F940}-\\x{1F945}]|[\\x{1F947}-\\x{1F94C}]|[\\x{1F94D}-\\x{1F94F}]|[\\x{1F950}-\\x{1F95F}]" +
            "|[\\x{1F960}-\\x{1F96F}]|[\\x{1F970}-\\x{1F97F}]|[\\x{1F980}-\\x{1F98F}]|[\\x{1F990}-\\x{1F99F}]" +
            "|[\\x{1F9A0}-\\x{1F9AF}]|[\\x{1F9B0}-\\x{1F9BF}]|[\\x{1F9C0}-\\x{1F9CF}]|[\\x{1F9D0}-\\x{1F9DF}]" +
            "|[\\x{1F9E0}-\\x{1F9EF}]|[\\x{1F9F0}-\\x{1F9FF}]|[\\x{1FA70}-\\x{1FA74}]|[\\x{1FA78}-\\x{1FA7C}]" +
            "|[\\x{1FA80}-\\x{1FA86}]|[\\x{1FA90}-\\x{1FA9F}]|[\\x{1FAA0}-\\x{1FAAC}]|[\\x{1FAB0}-\\x{1FABA}]" +
            "|[\\x{1FAC0}-\\x{1FAC5}]|[\\x{1FAD0}-\\x{1FAD9}]|[\\x{1FAE0}-\\x{1FAE7}]|[\\x{1FAF0}-\\x{1FAF6}]"

        val keycapBase = "[\\u0023\\u002A\\u0030-\\u0039]"
        val eMod = "[\\x{1F3FB}-\\x{1F3FF}]"
        val variationSelector = "\\uFE0F"
        val keycap = "\\u20E3"
        val tags = "[\\x{E0020}-\\x{E007E}]"
        val termTag = "\\x{E007F}"
        val zwj = "\\u200D"

        val risequence = "$ri$ri"
        val keycapEmoji = "$keycapBase$variationSelector?$keycap"
        val element = "(?:$support)(?:$eMod|$variationSelector|$tags+$termTag?)?"

        return "$keycapEmoji|$risequence|$element(?:$zwj(?:$risequence|$element))*"
    }
}
