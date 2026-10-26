package com.trtc.uikit.roomkit.base.utils

import kotlin.math.pow

private const val DEFAULT_ROOM_ID_LENGTH = 6
private const val WEBINAR_ROOM_ID_PREFIX = "webinar_"

/**
 * Generates a random numeric room ID with the leading digit non-zero, whose length equals [length].
 * @param length digit count; must be >= 1. Defaults to [DEFAULT_ROOM_ID_LENGTH].
 */
fun generateRoomID(length: Int = DEFAULT_ROOM_ID_LENGTH): String {
    require(length >= 1) { "room id length must be >= 1, got $length" }
    val min = 10.0.pow(length - 1).toInt()
    val max = 10.0.pow(length).toInt() - 1
    return (min..max).random().toString()
}

/** Generates a webinar room ID in the form `webinar_<numericId>`. */
fun generateWebinarRoomID(length: Int = DEFAULT_ROOM_ID_LENGTH): String {
    return WEBINAR_ROOM_ID_PREFIX + generateRoomID(length)
}
