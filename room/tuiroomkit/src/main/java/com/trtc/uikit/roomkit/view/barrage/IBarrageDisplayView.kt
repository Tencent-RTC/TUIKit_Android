package com.trtc.uikit.roomkit.view.barrage

import io.trtc.tuikit.atomicxcore.api.barrage.Barrage

interface IBarrageDisplayView {
    fun insertBarrages(vararg barrages: Barrage)
}
