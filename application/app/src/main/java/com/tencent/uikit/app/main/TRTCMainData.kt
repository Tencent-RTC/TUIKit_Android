package com.tencent.uikit.app.main

import com.tencent.uikit.app.R
import com.tencent.uikit.app.live.WaterfallActivity
import com.tencent.uikit.app.room.RoomHomeActivity

class TRTCMainData {
    val itemDataList = ArrayList<MainItemData?>()

    init {
        itemDataList.add(
            MainItemData(
                MainTypeEnum.TYPE_ITEM_CALL, R.drawable.app_ic_main_call, R.string.app_main_item_call,
                R.string.app_main_item_call_sub, ComingActivity::class.java, MainItemData.Category.KIT
            )
        )
        itemDataList.add(
            MainItemData(
                MainTypeEnum.TYPE_ITEM_LIVE, R.drawable.app_ic_main_live, R.string.app_main_item_live,
                R.string.app_main_item_live_sub, WaterfallActivity::class.java, MainItemData.Category.KIT
            )
        )
        itemDataList.add(
            MainItemData(
                MainTypeEnum.TYPE_ITEM_MEETING, R.drawable.app_ic_main_meeting, R.string.app_main_item_meeting,
                R.string.app_main_item_meeting_sub, RoomHomeActivity::class.java, MainItemData.Category.KIT
            )
        )
    }
}