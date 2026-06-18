package com.tencent.rtcube.v2.launchanim

import android.content.Context

/**
 * Decides whether to play the launch animation when entering home.
 *
 *  | Scenario                              | Play |
 *  |---------------------------------------|------|
 *  | First install & login                 | ✓    |
 *  | Re-login after token/userSig expired  | ✓    |
 *  | Login after upgrading to a new build  | ✓    |
 *  | Re-login after being kicked offline   | ✗    |
 *  | Re-login after logout (process alive) | ✗    |
 *  | Logout → kill process → re-login      | ✗    |
 *  | Auto login                            | ✗    |
 *  | Lab build                             | ✗    |
 */
object LaunchAnimationCoordinator {

    private const val VIDEO_RAW_RES_NAME = "main_launch_animation"

    @Volatile
    private var replayRequested = false

    fun videoRawResId(context: Context): Int = with(context.applicationContext) {
        resources.getIdentifier(VIDEO_RAW_RES_NAME, "raw", packageName)
    }

    fun shouldPlayOnEnterHome(context: Context): Boolean {
        if (videoRawResId(context) == 0) return false
        return replayRequested || !LaunchAnimationPlaybackRecord.hasPlayedForCurrentVersion(context)
    }

    fun markPlayedForCurrentVersion(context: Context) {
        LaunchAnimationPlaybackRecord.markPlayedForCurrentVersion(context)
        replayRequested = false
    }

    fun reLaunchAnimation() {
        replayRequested = true
    }
}
