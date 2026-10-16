package com.trtc.uikit.livekit.common.gift.player

import java.util.Locale

internal object GiftLanguage {
    fun currentCode(): String {
        val locale = Locale.getDefault()
        val language = locale.language
        val languageTag = locale.toLanguageTag().lowercase(locale)
        if (language.isEmpty() || languageTag.isEmpty() || !language.equals("zh", ignoreCase = true)) {
            return "en"
        }
        return if (
            languageTag.contains("zh-hans") ||
            languageTag == "zh" ||
            languageTag == "zh-cn" ||
            languageTag == "zh-sg" ||
            languageTag == "zh-my"
        ) {
            "zh-Hans"
        } else {
            "zh-Hant"
        }
    }
}
