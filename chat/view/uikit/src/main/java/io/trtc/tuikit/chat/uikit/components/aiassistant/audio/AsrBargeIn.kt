package io.trtc.tuikit.chat.uikit.components.aiassistant.audio

/**
 * 用 ASR 文本判打断时的过滤：会话常开后，思考 / 播放期回来的字既可能是用户开口，
 * 也可能是 TTS 漏进麦被识别成的回声，或零星的杂音、语气词。
 *
 * 已发出句子的补包不在这里判，由调用方按 chunkIndex 水位直接丢掉。
 */
object AsrBargeIn {

    fun significantCharCount(text: String): Int =
        text.count { it.isLetterOrDigit() || it in '\u4e00'..'\u9fff' }

    fun normalize(text: String): String = buildString(text.length) {
        for (ch in text) {
            if (ch.isWhitespace() || ch in PUNCTUATION) continue
            append(ch)
        }
    }

    /**
     * TTS 漏音被识别出来时，结果通常是正在念的 LLM 文本的一段子串。
     */
    fun looksLikeTtsEcho(asrText: String, llmText: String): Boolean {
        val asr = normalize(asrText)
        val llm = normalize(llmText)
        if (asr.length < 2 || llm.isEmpty()) {
            return false
        }
        return llm.contains(asr)
    }

    fun shouldBargeIn(
        asrText: String,
        llmText: String,
        minChars: Int
    ): Boolean {
        if (significantCharCount(asrText) < minChars) {
            return false
        }
        if (looksLikeTtsEcho(asrText, llmText)) {
            return false
        }
        return true
    }

    private const val PUNCTUATION = "，。、；：？！,“”‘’\"'（）()[]【】…—-,:;?!."
}
