package com.tencent.qcloud.tuikit.tuicallkit.call

import android.media.MediaPlayer
import androidx.annotation.RawRes
import com.tencent.qcloud.tuikit.tuicallkit.R
import com.tencent.qcloud.tuikit.tuicallkit.common.CallKitContext
import com.tencent.qcloud.tuikit.tuicallkit.common.CallKitLogger

internal object Ringtone {

    private val logger = CallKitLogger.getLogger("Ringtone")

    private var player: MediaPlayer? = null

    fun startDialTone() {
        play(rawRes = R.raw.phone_dialing)
    }

    fun startRinging() {
        if (CallKitConfig.shared.enableMuteMode) {
            return
        }
        val customPath = CallKitConfig.shared.callingBellPath
        if (customPath.isNotEmpty() && play(filePath = customPath)) {
            return
        }
        play(rawRes = R.raw.phone_ringing)
    }

    fun stopAll() {
        try {
            player?.stop()
        } catch (e: IllegalStateException) {
            logger.warn("ringtone stop in illegal state")
        }
        player?.release()
        player = null
    }

    private fun play(@RawRes rawRes: Int? = null, filePath: String? = null): Boolean {
        if (player != null) {
            return false
        }
        val mediaPlayer = MediaPlayer()
        try {
            if (rawRes != null) {
                val descriptor = CallKitContext.requireContext().resources.openRawResourceFd(rawRes) ?: return false
                mediaPlayer.setDataSource(descriptor.fileDescriptor, descriptor.startOffset, descriptor.length)
                descriptor.close()
            } else if (filePath != null) {
                mediaPlayer.setDataSource(filePath)
            } else {
                return false
            }
            mediaPlayer.isLooping = true
            mediaPlayer.prepare()
            mediaPlayer.start()
            player = mediaPlayer
            return true
        } catch (e: Exception) {
            logger.warn("ringtone play failed: ${e.javaClass.simpleName}")
            mediaPlayer.release()
            return false
        }
    }
}
