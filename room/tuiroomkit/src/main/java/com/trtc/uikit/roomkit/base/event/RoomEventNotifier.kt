package com.trtc.uikit.roomkit.base.event

/**
 * Observer interface for room events
 */
interface RoomObserver {
    /**
     * Called when the room is about to be left
     */
    fun willLeaveRoom() {}
}

/**
 * Notifier for room events
 */
object RoomEventNotifier {
    private val observers = mutableSetOf<RoomObserver>()

    /**
     * Register an observer
     */
    fun registerObserver(observer: RoomObserver) {
        observers.add(observer)
    }

    /**
     * Unregister an observer
     */
    fun unregisterObserver(observer: RoomObserver) {
        observers.remove(observer)
    }

    /**
     * Notify all observers that the room will be left
     */
    fun notifyWillLeaveRoom() {
        observers.forEach { it.willLeaveRoom() }
    }
}
