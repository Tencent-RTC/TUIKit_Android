package com.trtc.uikit.livekit.common

import android.view.View
import java.util.Locale

object LiveKitResources {
    val presetCoverURLs: List<String> = (1..5).map { index ->
        "https://liteav-test-1252463788.cos.ap-guangzhou.myqcloud.com/live/live_cover$index.png"
    }

    const val defaultCoverURL = "https://liteav-test-1252463788.cos.ap-guangzhou.myqcloud.com/live/live_cover1.png"

    const val defaultBackgroundURL = "https://liteav.sdk.qcloud.com/app/res/picture/livekit/coGuestBackGroud.png"

    internal val giftLanguageCode: String
        get() {
            val locale = Locale.getDefault()
            val language = locale.language
            val languageTag = locale.toLanguageTag().lowercase(locale)
            if (language.isEmpty() || languageTag.isEmpty() || !language.equals("zh", ignoreCase = true)) {
                return "en"
            }
            return if (languageTag.contains(
                    "zh-hans"
                ) || languageTag == "zh" || languageTag == "zh-cn" || languageTag == "zh-sg" || languageTag == "zh-my"
            ) {
                "zh-Hans"
            } else {
                "zh-Hant"
            }
        }
}

// View flags participate in Android's window-level aggregation without overwriting the Window.
internal class LiveScreenAwake(private val view: View) {
    private var previousValue: Boolean? = null

    fun setEnabled(enabled: Boolean) {
        if (enabled) {
            if (previousValue == null) previousValue = view.keepScreenOn
            view.keepScreenOn = true
        } else {
            previousValue?.let { view.keepScreenOn = it }
            previousValue = null
        }
    }
}
