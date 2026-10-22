package com.trtc.uikit.roomkit.base.error

import android.content.Context
import com.trtc.uikit.roomkit.R
import com.trtc.uikit.roomkit.base.RoomKitLogger
import com.trtc.uikit.roomkit.base.toast.RoomToast

///
///
object ErrorLocalized {

    enum class Level {
        SILENT,

        TOAST,
    }

    data class Rule(val level: Level, val messageRes: Int)

    private val logger = RoomKitLogger.getCommonLogger("ErrorLocalized")

    private val fallbackRes = R.string.roomkit_error_default

    private val rules: Map<Int, Rule> = mapOf(
        -1 to Rule(Level.TOAST, R.string.roomkit_error_default),
        -2 to Rule(Level.TOAST, R.string.roomkit_error_too_frequent),
        -1001 to Rule(Level.TOAST, R.string.roomkit_error_invalid_parameter),
        -1002 to Rule(Level.TOAST, R.string.roomkit_error_not_logged_in),
        100001 to Rule(Level.TOAST, R.string.roomkit_error_server),
        100002 to Rule(Level.SILENT, fallbackRes),
        100006 to Rule(Level.TOAST, fallbackRes),
        100007 to Rule(Level.TOAST, R.string.roomkit_error_package),
        100012 to Rule(Level.TOAST, R.string.roomkit_error_too_frequent),

        -2105 to Rule(Level.TOAST, R.string.roomkit_entry_failure_illegal_room_id),
        -2107 to Rule(Level.TOAST, R.string.roomkit_entry_failure_illegal_room_name),
        100003 to Rule(Level.TOAST, R.string.roomkit_entry_failure_room_exists),
        100004 to Rule(Level.TOAST, R.string.roomkit_entry_failure_room_not_found),
        100008 to Rule(Level.TOAST, R.string.roomkit_entry_failure_room_full),
        100011 to Rule(Level.TOAST, R.string.roomkit_entry_failure_room_exists),
        100026 to Rule(Level.TOAST, R.string.roomkit_entry_failure_sensitive_word),

        -1109 to Rule(Level.TOAST, R.string.roomkit_screen_share_failure_failed),
        -2361 to Rule(Level.TOAST, R.string.roomkit_device_all_mic_disabled),
        -2371 to Rule(Level.TOAST, R.string.roomkit_device_all_cam_disabled),
        -2373 to Rule(Level.TOAST, R.string.roomkit_screen_share_failure_owner_admin_only),
        12061 to Rule(Level.TOAST, R.string.roomkit_screen_share_failure_unsupported),

        100020 to Rule(Level.TOAST, R.string.roomkit_participants_admin_limit),

        101072 to Rule(Level.TOAST, R.string.roomkit_cloud_recording_failure_not_enabled),
    )

    fun rule(code: Int): Rule = rules[code] ?: Rule(Level.TOAST, fallbackRes)

    fun message(context: Context, code: Int): String = context.getString(rule(code).messageRes)

    ///
    fun showError(context: Context, code: Int, desc: String) {
        logger.error("code=$code, desc=$desc")
        show(context, code)
    }

    fun show(context: Context, code: Int) {
        if (rule(code).level == Level.SILENT) return
        RoomToast.show(context, message(context, code))
    }
}
