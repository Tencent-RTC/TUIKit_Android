package com.trtc.uikit.roomkit.base.error

import android.content.Context
import com.trtc.uikit.roomkit.R
import com.trtc.uikit.roomkit.base.RoomKitLogger
import com.trtc.uikit.roomkit.base.toast.RoomToast

object ErrorLocalized {

    enum class Level {
        SILENT,

        TOAST,
    }

    enum class Rule(val code: Int) {
        FALLBACK(0),
        UNKNOWN(-1),
        TOO_FREQUENT(-2),
        INVALID_PARAMETER(-1001),
        NOT_LOGGED_IN(-1002),
        SERVER_ERROR(100001),
        SERVER_INVALID_PARAMETER(100002),
        IGNORED(100100),
        UNCLASSIFIED(100006),
        PACKAGE_ERROR(100007),
        OPERATION_TOO_FREQUENT(100012),
        ILLEGAL_ROOM_ID(-2105),
        ILLEGAL_ROOM_NAME(-2107),
        ROOM_EXISTS(100003),
        ROOM_NOT_FOUND(100004),
        ROOM_FULL(100008),
        ROOM_ALREADY_EXISTS(100011),
        SENSITIVE_WORD(100026),
        PASSWORD_REQUIRED(100018),
        WRONG_PASSWORD(100019),
        CAMERA_START_FAILED(-1100),
        CAMERA_NO_PERMISSION(-1101),
        CAMERA_OCCUPIED(-1102),
        CAMERA_NO_DEVICE(-1103),
        MIC_START_FAILED(-1104),
        MIC_NO_PERMISSION(-1105),
        MIC_OCCUPIED(-1106),
        MIC_NO_DEVICE(-1107),
        SCREEN_SHARE_SOURCE_UNAVAILABLE(-1108),
        SCREEN_SHARE_FAILED(-1109),
        AUDIO_INTERRUPTED(-1110),
        ALL_MIC_DISABLED(-2361),
        ALL_CAMERA_DISABLED(-2371),
        SCREEN_SHARE_OWNER_ADMIN_ONLY(-2373),
        SCREEN_SHARE_UNSUPPORTED(12061),
        ADMIN_LIMIT(100020),
        CHAT_MUTED_ALL(-2380),
        CHAT_MUTED(100017),
        PACKAGE_UNAVAILABLE(101072);

        val level: Level
            get() = if (this == IGNORED) Level.SILENT else Level.TOAST

        val messageRes: Int
            get() = when (this) {
                TOO_FREQUENT, OPERATION_TOO_FREQUENT -> R.string.roomkit_error_too_frequent
                INVALID_PARAMETER, SERVER_INVALID_PARAMETER -> R.string.roomkit_error_invalid_parameter
                NOT_LOGGED_IN -> R.string.roomkit_error_not_logged_in
                SERVER_ERROR -> R.string.roomkit_error_server
                PACKAGE_ERROR, PACKAGE_UNAVAILABLE -> R.string.roomkit_error_package
                ILLEGAL_ROOM_ID -> R.string.roomkit_entry_failure_illegal_room_id
                ILLEGAL_ROOM_NAME -> R.string.roomkit_entry_failure_illegal_room_name
                ROOM_EXISTS, ROOM_ALREADY_EXISTS -> R.string.roomkit_entry_failure_room_exists
                ROOM_NOT_FOUND -> R.string.roomkit_entry_failure_room_not_found
                ROOM_FULL -> R.string.roomkit_entry_failure_room_full
                SENSITIVE_WORD -> R.string.roomkit_entry_failure_sensitive_word
                PASSWORD_REQUIRED -> R.string.roomkit_entry_failure_password_required
                WRONG_PASSWORD -> R.string.roomkit_entry_failure_wrong_password
                CAMERA_START_FAILED -> R.string.roomkit_device_camera_start_failed
                CAMERA_NO_PERMISSION -> R.string.roomkit_device_camera_no_permission
                CAMERA_OCCUPIED -> R.string.roomkit_device_camera_occupied
                CAMERA_NO_DEVICE -> R.string.roomkit_device_camera_no_device
                MIC_START_FAILED -> R.string.roomkit_device_mic_start_failed
                MIC_NO_PERMISSION -> R.string.roomkit_device_mic_no_permission
                MIC_OCCUPIED -> R.string.roomkit_device_mic_occupied
                MIC_NO_DEVICE -> R.string.roomkit_device_mic_no_device
                SCREEN_SHARE_SOURCE_UNAVAILABLE -> R.string.roomkit_screen_share_failure_source_unavailable
                SCREEN_SHARE_FAILED -> R.string.roomkit_screen_share_failure_failed
                AUDIO_INTERRUPTED -> R.string.roomkit_device_audio_interrupted
                ALL_MIC_DISABLED -> R.string.roomkit_device_all_mic_disabled
                ALL_CAMERA_DISABLED -> R.string.roomkit_device_all_cam_disabled
                SCREEN_SHARE_OWNER_ADMIN_ONLY -> R.string.roomkit_screen_share_failure_owner_admin_only
                SCREEN_SHARE_UNSUPPORTED -> R.string.roomkit_screen_share_failure_unsupported
                ADMIN_LIMIT -> R.string.roomkit_participants_admin_limit
                CHAT_MUTED_ALL -> R.string.roomkit_chat_muted_all
                CHAT_MUTED -> R.string.roomkit_chat_muted
                else -> R.string.roomkit_error_default
            }

        companion object {
            fun fromCode(code: Int): Rule = entries.firstOrNull { it.code == code } ?: FALLBACK
        }
    }

    private val logger = RoomKitLogger.getCommonLogger("ErrorLocalized")

    fun rule(code: Int): Rule = Rule.fromCode(code)

    fun message(context: Context, code: Int): String = context.getString(rule(code).messageRes)

    fun showError(context: Context, code: Int, desc: String) {
        logger.error("code=$code, desc=$desc")
        show(context, code)
    }

    fun show(context: Context, code: Int) {
        if (rule(code).level == Level.SILENT) return
        RoomToast.show(context, message(context, code))
    }
}
