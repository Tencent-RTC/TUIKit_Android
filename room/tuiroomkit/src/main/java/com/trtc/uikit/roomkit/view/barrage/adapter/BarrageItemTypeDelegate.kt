package com.trtc.uikit.roomkit.view.barrage.adapter

import io.trtc.tuikit.atomicxcore.api.barrage.Barrage

interface BarrageItemTypeDelegate {
    fun getItemType(position: Int, barrage: Barrage): Int
}
