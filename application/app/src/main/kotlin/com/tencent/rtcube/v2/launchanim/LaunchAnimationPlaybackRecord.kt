package com.tencent.rtcube.v2.launchanim

import android.content.Context
import androidx.core.content.edit

/**
 * Persistence for the launch animation playback record.
 */
internal object LaunchAnimationPlaybackRecord {

    private const val PREFS_NAME = "rtcube_version"
    private const val KEY_LAST_PLAYED_VERSION = "lastPlayedAppVersion"

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun currentAppVersion(context: Context): String = runCatching {
        with(context.applicationContext) {
            packageManager.getPackageInfo(packageName, 0).versionName.orEmpty()
        }
    }.getOrDefault("")

    fun hasPlayedForCurrentVersion(context: Context): Boolean {
        val last = prefs(context).getString(KEY_LAST_PLAYED_VERSION, null)
        return !last.isNullOrEmpty() && last == currentAppVersion(context)
    }

    fun markPlayedForCurrentVersion(context: Context) =
        prefs(context).edit { putString(KEY_LAST_PLAYED_VERSION, currentAppVersion(context)) }
}
