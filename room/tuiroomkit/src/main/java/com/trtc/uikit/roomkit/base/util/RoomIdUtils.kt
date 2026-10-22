package com.trtc.uikit.roomkit.base.util

import kotlin.math.pow

private const val ROOM_ID_LENGTH = 6

fun generateRoomID(length: Int = ROOM_ID_LENGTH): String {
    val min = 10.0.pow(length - 1).toInt()
    val max = 10.0.pow(length).toInt() - 1
    return (min..max).random().toString()
}
