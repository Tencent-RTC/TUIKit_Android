package com.trtc.uikit.livekit.common

object LiveKitResources {
    val presetCoverURLs: List<String> = (1..5).map { index ->
        "https://liteav-test-1252463788.cos.ap-guangzhou.myqcloud.com/live/live_cover$index.png"
    }

    const val defaultCoverURL =
        "https://liteav-test-1252463788.cos.ap-guangzhou.myqcloud.com/live/live_cover1.png"

    const val defaultBackgroundURL =
        "https://liteav.sdk.qcloud.com/app/res/picture/livekit/coGuestBackGroud.png"
}
