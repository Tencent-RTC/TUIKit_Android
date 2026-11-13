package com.tencent.uikit.app.call.entry

import android.content.Context
import com.tencent.uikit.app.R
import io.trtc.tuikit.atomicxcore.api.device.VideoQuality

class CallEntryState(context: Context) {

    enum class Media {
        VIDEO,
        AUDIO
    }

    enum class Resolution(val displayNameRes: Int) {
        SD(R.string.callaccess_resolution_sd),
        HD(R.string.callaccess_resolution_hd),
        FHD(R.string.callaccess_resolution_fhd),
        PREMIUM_2K(R.string.callaccess_resolution_2k);

        val videoQuality: VideoQuality?
            get() = when (this) {
                SD -> VideoQuality.QUALITY_540P
                HD -> VideoQuality.QUALITY_720P
                FHD -> VideoQuality.QUALITY_1080P
                PREMIUM_2K -> null
            }
    }

    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var userIDs: List<String> = emptyList()
    var media: Media = Media.VIDEO
    var resolution: Resolution = Resolution.SD
    var captionOn: Boolean = true

    var ringtone: String = ""
    var bannerOn: Boolean = true
    var showFloatButton: Boolean = true
    var blurBackgroundOn: Boolean = true
    var proBeautyOn: Boolean = true

    var muteModeEnabled: Boolean
        get() = preferences.getBoolean(KEY_MUTE_MODE, false)
        set(value) = preferences.edit().putBoolean(KEY_MUTE_MODE, value).apply()

    var roomID: String = ""
    var timeoutSeconds: Int = 60
    var extendedInfo: String = ""

    fun ringtoneLabel(context: Context): String {
        return ringtone.ifEmpty { context.getString(R.string.callaccess_settings_ringtone_default) }
    }

    fun isDefaultRingtone(): Boolean {
        return ringtone.isEmpty()
    }

    companion object {
        const val MAX_CALLEE_IDS = 8
        const val RINGTONE_BUILTIN = "phone_ringing.mp3"

        private const val PREFS_NAME = "com.tencent.uikit.app.call.entry"
        private const val KEY_MUTE_MODE = "callkit.access.muteMode"
        private val SEPARATORS = charArrayOf(' ', '\t', '\n', ',', '，', ';', '；', '、')

        fun parseIDs(raw: String): List<String> {
            val seen = mutableSetOf<String>()
            val result = mutableListOf<String>()
            for (part in raw.split(*SEPARATORS)) {
                val id = part.trim()
                if (id.isEmpty()) {
                    continue
                }
                if (!seen.add(id.lowercase())) {
                    continue
                }
                result.add(id)
            }
            return result
        }

        fun containsSeparator(text: String): Boolean {
            return text.any { it in SEPARATORS }
        }
    }
}
