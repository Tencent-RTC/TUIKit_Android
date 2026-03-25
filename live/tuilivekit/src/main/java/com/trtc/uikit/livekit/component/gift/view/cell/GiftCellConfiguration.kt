package com.trtc.uikit.livekit.component.gift.view.cell

data class GiftCellConfiguration(
    val comboDuration: Long = 5000L,
    val batchStep: Int = 5,
    val longPressInterval: Long = 100L,
    val longPressDelay: Long = 300L,
    val maxBatchCount: Int = 999,
    val animationDuration: Long = 200L
)