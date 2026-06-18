package com.tencent.rtcube.v2

import com.tencent.rtcube.assembly.AppTarget

/**
 * Resolves the [AppTarget] according to the current build Flavor.
 *
 * - `tencentrtc` → [AppTarget.OVERSEAS]
 * - `rtcubelab`  → [AppTarget.LAB]
 * - others       → [AppTarget.DOMESTIC]
 */
object AppTargetResolver {

    fun getAppTarget(): AppTarget = when {
        BuildConfig.FLAVOR.startsWith("tencentrtc") -> AppTarget.OVERSEAS
        BuildConfig.FLAVOR.startsWith("rtcubelab") -> AppTarget.LAB
        else -> AppTarget.DOMESTIC
    }
}
