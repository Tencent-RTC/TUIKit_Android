package com.tencent.rtcube.assembly

import android.content.Context
import android.graphics.Bitmap

/**
 * Module config — describes a home entry card.
 */
data class ModuleConfig(
    val identifier: String,
    val title: String = "",
    val description: String,
    val iconResId: Int = 0,
    val iconUrl: String = "",
    val cardStyle: EntranceCardStyle,
    val gradientColors: IntArray = intArrayOf(),
    val isHot: Boolean = false,
    val targetProvider: (Context) -> android.content.Intent?,
    val analyticsEvent: String = "",
    val iconName: String = "",
    val iconImage: Bitmap? = null,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ModuleConfig) return false
        return identifier == other.identifier
    }

    override fun hashCode(): Int = identifier.hashCode()
}
