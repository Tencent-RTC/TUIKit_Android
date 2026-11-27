package com.tencent.uikit.app.call.entry

import android.content.Context
import com.tencent.uikit.app.R
import io.trtc.tuikit.atomicxcore.api.device.VideoQuality
import java.util.Locale

class CallEntryState(context: Context) {

    enum class Media {
        VIDEO,
        AUDIO
    }

    enum class Resolution(val displayNameRes: Int, val videoQuality: VideoQuality) {
        SD(R.string.callaccess_resolution_sd, VideoQuality.QUALITY_540P),
        HD(R.string.callaccess_resolution_hd, VideoQuality.QUALITY_720P),
        FHD(R.string.callaccess_resolution_fhd, VideoQuality.QUALITY_1080P),
    }

    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var captionOn: Boolean = true

    var ringtone: String = ""
    var bannerOn: Boolean = true
    var showFloatButton: Boolean = true
    var blurBackgroundOn: Boolean = true

    var muteModeEnabled: Boolean
        get() = preferences.getBoolean(KEY_MUTE_MODE, false)
        set(value) = preferences.edit().putBoolean(KEY_MUTE_MODE, value).apply()

    var roomID: String = ""
    var timeoutSeconds: Int = 30
    var extendedInfo: String = ""

    fun ringtoneLabel(context: Context): String {
        return ringtone.ifEmpty { context.getString(R.string.callaccess_settings_ringtone_default) }
    }

    fun isDefaultRingtone(): Boolean {
        return ringtone.isEmpty()
    }

    companion object {
        const val MAX_CALLEE_IDS = 8
        const val RINGTONE_BUILTIN = "rain.mp3"

        private const val PREFS_NAME = "com.tencent.uikit.app.call.entry"
        private const val KEY_MUTE_MODE = "callkit.access.muteMode"
        private val SEPARATOR = Regex("[\\s,，;；、]+")

        fun parseIDs(raw: String): List<String> {
            val seen = mutableSetOf<String>()
            val result = mutableListOf<String>()
            for (part in raw.split(SEPARATOR)) {
                val id = part.trim()
                if (id.isEmpty()) {
                    continue
                }
                if (!seen.add(id.lowercase(Locale.ROOT))) {
                    continue
                }
                result.add(id)
            }
            return result
        }

        fun containsSeparator(text: String): Boolean {
            return SEPARATOR.containsMatchIn(text.trim())
        }

        fun sameID(lhs: String, rhs: String): Boolean = lhs.equals(rhs, ignoreCase = true)
    }
}

/** 入口页本次呼叫的选择（被叫、媒体类型、分辨率），由入口页持有，页面销毁即丢弃。 */
class CallEntrySelection {
    var userIDs: List<String> = emptyList()
    var media: CallEntryState.Media = CallEntryState.Media.VIDEO
    var resolution: CallEntryState.Resolution = CallEntryState.Resolution.HD
}
