package com.tencent.uikit.app.main

import com.tencent.uikit.app.common.utils.DEMO_CLICK_CALL
import com.tencent.uikit.app.common.utils.DEMO_CLICK_LIVE
import com.tencent.uikit.app.common.utils.DEMO_CLICK_ROOM

enum class MainTypeEnum(val type: Int, val properties: String, val reportEvent: Int = -1) {
    TYPE_ITEM_MEETING(100, "conference", DEMO_CLICK_ROOM),
    TYPE_ITEM_CALL(104, "call", DEMO_CLICK_CALL),
    TYPE_ITEM_LIVE(106, "live", DEMO_CLICK_LIVE),
    TYPE_ITEM_VOICE(107, "voice"),
    TYPE_ITEM_CHAT(109, "chat")
}