package com.trtc.uikit.roomkit.base.error

import android.content.Context
import com.trtc.uikit.roomkit.R
import com.trtc.uikit.roomkit.base.log.RoomKitLogger
import io.trtc.tuikit.atomicx.widget.basicwidget.toast.AtomicToast
import io.trtc.tuikit.atomicx.widget.basicwidget.toast.AtomicToast.Style

/**
 * Error localization and handling utility.
 * Provides localized error messages for different error codes from RoomEngine and IM SDK.
 *
 * Usage:
 * ```kotlin
 * ErrorLocalized.showError(context, -1001)
 * ```
 */
object ErrorLocalized {
    private val logger = RoomKitLogger.getLogger("ErrorLocalized")

    /**
     * Show error toast with localized message.
     */
    @JvmStatic
    fun showError(context: Context, code: Int) {
        logger.info("showError: code: $code")
        val localizedMessage = getLocalizedMessage(context, code)
        when (code) {
            RoomError.OPEN_MICROPHONE_NEED_PERMISSION_FROM_ADMIN.code,
            RoomError.OPEN_CAMERA_NEED_PERMISSION_FROM_ADMIN.code ->
                AtomicToast.show(context, localizedMessage, Style.WARNING)

            else -> AtomicToast.show(context, localizedMessage, Style.ERROR)
        }
    }

    /**
     * Get localized error message for error code
     */
    private fun getLocalizedMessage(context: Context, code: Int): String {
        return RoomError.fromCode(code)?.getDescription(context)
            ?: TIMError.fromCode(code)?.getDescription(context)
            ?: (context.getString(R.string.roomkit_err_general) + ": $code")
    }

    /**
     * TIM (IM SDK) error codes.
     */
    private enum class TIMError(val code: Int) {
        SUCCESS(0),
        FAILED(-1),
        INVALID_USER_ID(7002),
        ERR_SDK_COMM_API_CALL_FREQUENCY_LIMIT(7008),
        ERR_SVR_GROUP_SHUT_UP_DENY(10017),
        ERR_SDK_BLOCKED_BY_SENSITIVE_WORD(7015),
        ERR_SDK_NET_PKG_SIZE_LIMIT(9522),
        ERR_SDK_NET_DISCONNECT(9508),
        ERR_SDK_NET_WAIT_ACK_TIMEOUT(9520),
        ERR_SDK_NET_ALREADY_CONN(9509),
        ERR_SDK_NET_CONN_TIMEOUT(9510),
        ERR_SDK_NET_CONN_REFUSE(9511),
        ERR_SDK_NET_NET_UNREACH(9512),
        ERR_SDK_NET_WAIT_IN_QUEUE_TIMEOUT(9518),
        ERR_SDK_NET_WAIT_SEND_TIMEOUT(9519),
        ERR_SDK_NET_WAIT_SEND_REMAINING_TIMEOUT(9521),
        ERR_SDK_NET_WAIT_SEND_TIMEOUT_NO_NETWORK(9523),
        ERR_SDK_NET_WAIT_ACK_TIMEOUT_NO_NETWORK(9524),
        ERR_SDK_NET_SEND_REMAINING_TIMEOUT_NO_NETWORK(9525);

        fun getDescription(context: Context): String {
            return when (this) {
                SUCCESS -> context.getString(R.string.roomkit_err_0_success)
                FAILED -> context.getString(R.string.roomkit_err_general) + ": $code"
                INVALID_USER_ID -> context.getString(R.string.roomkit_err_7002_invalid_user_id)
                ERR_SDK_COMM_API_CALL_FREQUENCY_LIMIT -> context.getString(R.string.roomkit_err_7008_request_rate_limited)
                ERR_SVR_GROUP_SHUT_UP_DENY -> context.getString(R.string.roomkit_err_10017_muted_in_room)
                ERR_SDK_BLOCKED_BY_SENSITIVE_WORD -> context.getString(R.string.roomkit_err_7015_sensitive_words)
                ERR_SDK_NET_PKG_SIZE_LIMIT -> context.getString(R.string.roomkit_err_9522_content_too_long)
                ERR_SDK_NET_DISCONNECT,
                ERR_SDK_NET_WAIT_ACK_TIMEOUT,
                ERR_SDK_NET_ALREADY_CONN,
                ERR_SDK_NET_CONN_TIMEOUT,
                ERR_SDK_NET_CONN_REFUSE,
                ERR_SDK_NET_NET_UNREACH,
                ERR_SDK_NET_WAIT_IN_QUEUE_TIMEOUT,
                ERR_SDK_NET_WAIT_SEND_TIMEOUT,
                ERR_SDK_NET_WAIT_SEND_REMAINING_TIMEOUT,
                ERR_SDK_NET_WAIT_SEND_TIMEOUT_NO_NETWORK,
                ERR_SDK_NET_WAIT_ACK_TIMEOUT_NO_NETWORK,
                ERR_SDK_NET_SEND_REMAINING_TIMEOUT_NO_NETWORK ->
                    context.getString(R.string.roomkit_err_network_error)
            }
        }

        companion object {
            private val codeMap = TIMError.entries.associateBy { it.code }
            fun fromCode(code: Int): TIMError? = codeMap[code]
        }
    }

    /**
     * Room error codes from RoomEngine.
     */
    private enum class RoomError(val code: Int) {
        SUCCESS(0),
        FREQ_LIMIT(-2),
        REPEAT_OPERATION(-3),
        ROOM_MISMATCH(-4),
        SDK_APPID_NOT_FOUND(-1000),
        INVALID_PARAMETER(-1001),
        SDK_NOT_INITIALIZED(-1002),
        PERMISSION_DENIED(-1003),
        REQUIRE_PAYMENT(-1004),
        INVALID_LICENSE(-1005),
        CAMERA_START_FAIL(-1100),
        CAMERA_NOT_AUTHORIZED(-1101),
        CAMERA_OCCUPIED(-1102),
        CAMERA_DEVICE_EMPTY(-1103),
        MICROPHONE_START_FAIL(-1104),
        MICROPHONE_NOT_AUTHORIZED(-1105),
        MICROPHONE_OCCUPIED(-1106),
        MICROPHONE_DEVICE_EMPTY(-1107),
        GET_SCREEN_SHARING_TARGET_FAILED(-1108),
        START_SCREEN_SHARING_FAILED(-1109),
        OPERATION_INVALID_BEFORE_ENTER_ROOM(-2101),
        EXIT_NOT_SUPPORTED_FOR_ROOM_OWNER(-2102),
        OPERATION_NOT_SUPPORTED_IN_CURRENT_ROOM_TYPE(-2103),
        ROOM_ID_INVALID(-2105),
        ROOM_NAME_INVALID(-2107),
        ALREADY_IN_OTHER_ROOM(-2108),
        USER_NOT_EXIST(-2200),
        USER_NEED_OWNER_PERMISSION(-2300),
        USER_NEED_ADMIN_PERMISSION(-2301),
        REQUEST_NO_PERMISSION(-2310),
        REQUEST_ID_INVALID(-2311),
        REQUEST_ID_REPEAT(-2312),
        MAX_SEAT_COUNT_LIMIT(-2340),
        SEAT_INDEX_NOT_EXIST(-2344),
        OPEN_MICROPHONE_NEED_SEAT_UNLOCK(-2360),
        OPEN_MICROPHONE_NEED_PERMISSION_FROM_ADMIN(-2361),
        OPEN_CAMERA_NEED_SEAT_UNLOCK(-2370),
        OPEN_CAMERA_NEED_PERMISSION_FROM_ADMIN(-2371),
        OPEN_SCREEN_SHARE_NEED_SEAT_UNLOCK(-2372),
        OPEN_SCREEN_SHARE_NEED_PERMISSION_FROM_ADMIN(-2373),
        SEND_MESSAGE_DISABLED_FOR_ALL(-2380),
        SEND_MESSAGE_DISABLED_FOR_CURRENT(-2381),
        ROOM_NOT_SUPPORT_PRELOADING(-4001),
        CALL_IN_PROGRESS(-6001),
        SYSTEM_INTERNAL_ERROR(100001),
        PARAM_ILLEGAL(100002),
        ROOM_ID_OCCUPIED(100003),
        ROOM_ID_NOT_EXIST(100004),
        USER_NOT_ENTERED(100005),
        INSUFFICIENT_OPERATION_PERMISSIONS(100006),
        NO_PAYMENT_INFORMATION(100007),
        ROOM_IS_FULL(100008),
        TAG_QUANTITY_EXCEEDS_UPPER_LIMIT(100009),
        ROOM_ID_HAS_BEEN_USED(100010),
        ROOM_ID_HAS_BEEN_OCCUPIED_BY_CHAT(100011),
        CREATING_ROOMS_EXCEEDS_THE_FREQUENCY_LIMIT(100012),
        EXCEEDS_THE_UPPER_LIMIT(100013),
        INVALID_ROOM_TYPE(100015),
        MEMBER_HAS_BEEN_BANNED(100016),
        MEMBER_HAS_BEEN_MUTED(100017),
        REQUIRES_PASSWORD(100018),
        ROOM_ENTRY_PASSWORD_ERROR(100019),
        ROOM_ADMIN_QUANTITY_EXCEEDS_THE_UPPER_LIMIT(100020),
        REQUEST_ID_CONFLICT(100102),
        SEAT_LOCKED(100200),
        SEAT_OCCUPIED(100201),
        ALREADY_ON_THE_SEAT_QUEUE(100202),
        ALREADY_IN_SEAT(100203),
        NOT_ON_THE_SEAT_QUEUE(100204),
        ALL_SEAT_OCCUPIED(100205),
        USER_NOT_IN_SEAT(100206),
        USER_ALREADY_ON_SEAT(100210),
        SEAT_NOT_SUPPORT_LINK_MIC(100211),
        EMPTY_SEAT_LIST(100251),
        SPEAKER_SEAT_OCCUPIED(100253),
        METADATA_KEY_EXCEEDS_LIMIT(100500),
        METADATA_VALUE_SIZE_EXCEEDS_BYTE_LIMIT(100501),
        METADATA_TOTAL_VALUE_SIZE_EXCEEDS_BYTE_LIMIT(100502),
        METADATA_NO_VALID_KEY(100503),
        METADATA_KEY_SIZE_EXCEEDS_BYTE_LIMIT(100504);

        fun getDescription(context: Context): String {
            return when (this) {
                SUCCESS -> context.getString(R.string.roomkit_err_0_success)
                FREQ_LIMIT -> context.getString(R.string.roomkit_err_n2_request_rate_limited)
                REPEAT_OPERATION -> context.getString(R.string.roomkit_err_n3_repeat_operation)
                ROOM_MISMATCH -> context.getString(R.string.roomkit_err_n4_roomID_not_match)
                SDK_APPID_NOT_FOUND -> context.getString(R.string.roomkit_err_n1000_sdk_appid_not_found)
                INVALID_PARAMETER -> context.getString(R.string.roomkit_err_n1001_invalid_parameter)
                SDK_NOT_INITIALIZED -> context.getString(R.string.roomkit_err_n1002_not_logged_in)
                PERMISSION_DENIED -> context.getString(R.string.roomkit_err_n1003_permission_denied)
                REQUIRE_PAYMENT -> context.getString(R.string.roomkit_err_n1004_package_required)
                INVALID_LICENSE -> context.getString(R.string.roomkit_err_n1005_invalid_license)
                CAMERA_START_FAIL -> context.getString(R.string.roomkit_err_n1100_camera_open_failed)
                CAMERA_NOT_AUTHORIZED -> context.getString(R.string.roomkit_err_n1101_camera_no_permission)
                CAMERA_OCCUPIED -> context.getString(R.string.roomkit_err_n1102_camera_occupied)
                CAMERA_DEVICE_EMPTY -> context.getString(R.string.roomkit_err_n1103_camera_not_found)
                MICROPHONE_START_FAIL -> context.getString(R.string.roomkit_err_n1104_mic_open_failed)
                MICROPHONE_NOT_AUTHORIZED -> context.getString(R.string.roomkit_err_n1105_mic_no_permission)
                MICROPHONE_OCCUPIED -> context.getString(R.string.roomkit_err_n1106_mic_occupied)
                MICROPHONE_DEVICE_EMPTY -> context.getString(R.string.roomkit_err_n1107_mic_not_found)
                GET_SCREEN_SHARING_TARGET_FAILED -> context.getString(R.string.roomkit_err_n1108_screen_share_get_source_failed)
                START_SCREEN_SHARING_FAILED -> context.getString(R.string.roomkit_err_n1109_screen_share_start_failed)
                OPERATION_INVALID_BEFORE_ENTER_ROOM -> context.getString(R.string.roomkit_err_n2101_not_in_room)
                EXIT_NOT_SUPPORTED_FOR_ROOM_OWNER -> context.getString(R.string.roomkit_err_n2102_owner_cannot_leave)
                OPERATION_NOT_SUPPORTED_IN_CURRENT_ROOM_TYPE -> context.getString(R.string.roomkit_err_n2103_unsupported_in_room_type)
                ROOM_ID_INVALID -> context.getString(R.string.roomkit_err_n2105_invalid_room_id)
                ROOM_NAME_INVALID -> context.getString(R.string.roomkit_err_n2107_invalid_room_name)
                ALREADY_IN_OTHER_ROOM -> context.getString(R.string.roomkit_err_n2108_user_already_in_other_room)
                USER_NOT_EXIST -> context.getString(R.string.roomkit_err_n2200_user_not_exist)
                USER_NEED_OWNER_PERMISSION -> context.getString(R.string.roomkit_err_n2300_need_owner_permission)
                USER_NEED_ADMIN_PERMISSION -> context.getString(R.string.roomkit_err_n2301_need_admin_permission)
                REQUEST_NO_PERMISSION -> context.getString(R.string.roomkit_err_n2310_signal_no_permission)
                REQUEST_ID_INVALID -> context.getString(R.string.roomkit_err_n2311_signal_invalid_request_id)
                REQUEST_ID_REPEAT -> context.getString(R.string.roomkit_err_n2312_signal_request_duplicated)
                MAX_SEAT_COUNT_LIMIT -> context.getString(R.string.roomkit_err_n2340_seat_count_limit_exceeded)
                SEAT_INDEX_NOT_EXIST -> context.getString(R.string.roomkit_err_n2344_seat_not_exist)
                OPEN_MICROPHONE_NEED_SEAT_UNLOCK -> context.getString(R.string.roomkit_err_n2360_seat_audio_locked)
                OPEN_MICROPHONE_NEED_PERMISSION_FROM_ADMIN -> context.getString(R.string.roomkit_tip_all_muted_cannot_unmute)
                OPEN_CAMERA_NEED_SEAT_UNLOCK -> context.getString(R.string.roomkit_err_n2370_seat_video_locked)
                OPEN_CAMERA_NEED_PERMISSION_FROM_ADMIN -> context.getString(R.string.roomkit_tip_all_video_off_cannot_start)
                OPEN_SCREEN_SHARE_NEED_SEAT_UNLOCK -> context.getString(R.string.roomkit_err_n2372_screen_share_seat_locked)
                OPEN_SCREEN_SHARE_NEED_PERMISSION_FROM_ADMIN -> context.getString(R.string.roomkit_err_n2373_screen_share_need_permission)
                SEND_MESSAGE_DISABLED_FOR_ALL -> context.getString(R.string.roomkit_err_n2380_all_members_muted)
                SEND_MESSAGE_DISABLED_FOR_CURRENT -> context.getString(R.string.roomkit_err_10017_muted_in_room)
                ROOM_NOT_SUPPORT_PRELOADING -> context.getString(R.string.roomkit_err_n4001_room_not_support_preload)
                CALL_IN_PROGRESS -> context.getString(R.string.roomkit_err_n6001_device_busy_during_call)
                SYSTEM_INTERNAL_ERROR -> context.getString(R.string.roomkit_err_100001_server_internal_error)
                PARAM_ILLEGAL -> context.getString(R.string.roomkit_err_100002_server_invalid_parameter)
                ROOM_ID_OCCUPIED -> context.getString(R.string.roomkit_err_100003_room_id_already_exists)
                ROOM_ID_NOT_EXIST -> context.getString(R.string.roomkit_err_100004_room_not_exist)
                USER_NOT_ENTERED -> context.getString(R.string.roomkit_err_100005_not_room_member)
                INSUFFICIENT_OPERATION_PERMISSIONS -> context.getString(R.string.roomkit_err_100006_operation_not_allowed)
                NO_PAYMENT_INFORMATION -> context.getString(R.string.roomkit_err_100007_no_payment_info)
                ROOM_IS_FULL -> context.getString(R.string.roomkit_err_100008_room_is_full)
                TAG_QUANTITY_EXCEEDS_UPPER_LIMIT -> context.getString(R.string.roomkit_err_100009_room_tag_limit_exceeded)
                ROOM_ID_HAS_BEEN_USED -> context.getString(R.string.roomkit_err_100010_room_id_reusable_by_owner)
                ROOM_ID_HAS_BEEN_OCCUPIED_BY_CHAT -> context.getString(R.string.roomkit_err_100011_room_id_occupied_by_im)
                CREATING_ROOMS_EXCEEDS_THE_FREQUENCY_LIMIT -> context.getString(R.string.roomkit_err_100012_create_room_frequency_limit)
                EXCEEDS_THE_UPPER_LIMIT -> context.getString(R.string.roomkit_err_100013_payment_limit_exceeded)
                INVALID_ROOM_TYPE -> context.getString(R.string.roomkit_err_100015_invalid_room_type)
                MEMBER_HAS_BEEN_BANNED -> context.getString(R.string.roomkit_err_100016_member_already_banned)
                MEMBER_HAS_BEEN_MUTED -> context.getString(R.string.roomkit_err_100017_member_already_muted)
                REQUIRES_PASSWORD -> context.getString(R.string.roomkit_err_100018_room_password_required)
                ROOM_ENTRY_PASSWORD_ERROR -> context.getString(R.string.roomkit_err_100019_room_password_incorrect)
                ROOM_ADMIN_QUANTITY_EXCEEDS_THE_UPPER_LIMIT -> context.getString(R.string.roomkit_err_100020_admin_limit_exceeded)
                REQUEST_ID_CONFLICT -> context.getString(R.string.roomkit_err_100102_signal_request_conflict)
                SEAT_LOCKED -> context.getString(R.string.roomkit_err_100200_seat_is_locked)
                SEAT_OCCUPIED -> context.getString(R.string.roomkit_err_100201_seat_is_occupied)
                ALREADY_ON_THE_SEAT_QUEUE -> context.getString(R.string.roomkit_err_100202_already_in_seat_queue)
                ALREADY_IN_SEAT -> context.getString(R.string.roomkit_err_100203_already_on_seat)
                NOT_ON_THE_SEAT_QUEUE -> context.getString(R.string.roomkit_err_100204_not_in_seat_queue)
                ALL_SEAT_OCCUPIED -> context.getString(R.string.roomkit_err_100205_all_seats_are_full)
                USER_NOT_IN_SEAT -> context.getString(R.string.roomkit_err_100206_not_on_seat)
                USER_ALREADY_ON_SEAT -> context.getString(R.string.roomkit_err_100210_user_already_on_seat)
                SEAT_NOT_SUPPORT_LINK_MIC -> context.getString(R.string.roomkit_err_100211_seat_not_supported)
                EMPTY_SEAT_LIST -> context.getString(R.string.roomkit_err_100251_seat_list_is_empty)
                SPEAKER_SEAT_OCCUPIED -> context.getString(R.string.roomkit_err_100253_speaker_seat_occupied)
                METADATA_KEY_EXCEEDS_LIMIT -> context.getString(R.string.roomkit_err_100500_room_metadata_key_limit)
                METADATA_VALUE_SIZE_EXCEEDS_BYTE_LIMIT -> context.getString(R.string.roomkit_err_100501_room_metadata_value_limit)
                METADATA_TOTAL_VALUE_SIZE_EXCEEDS_BYTE_LIMIT -> context.getString(R.string.roomkit_err_100502_room_metadata_total_limit)
                METADATA_NO_VALID_KEY -> context.getString(R.string.roomkit_err_100503_room_metadata_no_valid_keys)
                METADATA_KEY_SIZE_EXCEEDS_BYTE_LIMIT -> context.getString(R.string.roomkit_err_100504_room_metadata_key_size_limit)
            }
        }

        companion object {
            private val codeMap = RoomError.entries.associateBy { it.code }
            fun fromCode(code: Int): RoomError? = codeMap[code]
        }
    }
}
