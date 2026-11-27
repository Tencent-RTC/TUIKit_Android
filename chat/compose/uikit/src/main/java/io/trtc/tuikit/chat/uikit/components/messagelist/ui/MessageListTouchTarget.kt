package io.trtc.tuikit.chat.uikit.components.messagelist.ui

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow

internal data class WindowBoundsSnapshot(
    val isAttached: Boolean,
    val boundsInWindow: Rect
)

internal fun computeClippedBoundsInWindow(
    node: WindowBoundsSnapshot,
    ancestors: List<WindowBoundsSnapshot> = emptyList()
): Rect {
    if (!node.isAttached) {
        return Rect.Zero
    }
    var bounds = node.boundsInWindow
    if (bounds.isEmpty) {
        return Rect.Zero
    }
    for (parent in ancestors) {
        if (!parent.isAttached) {
            return Rect.Zero
        }
        bounds = bounds.intersect(parent.boundsInWindow)
        if (bounds.isEmpty) {
            return Rect.Zero
        }
    }
    return bounds
}

internal fun containsLiveCoordinates(
    isAttached: Boolean,
    clippedBoundsInWindow: Rect,
    windowOffset: Offset
): Boolean {
    if (!isAttached) {
        return false
    }
    return !clippedBoundsInWindow.isEmpty && clippedBoundsInWindow.contains(windowOffset)
}

private data class LiveCoordinatesSnapshot(
    val isAttached: Boolean,
    val clippedBoundsInWindow: Rect
)

class MessageListTouchTargetHitTester {
    private val handles = mutableSetOf<Handle>()

    class Handle {
        var bounds: Rect = Rect.Zero
            private set
        var isAttached: Boolean = true
            private set
        var isVisible: Boolean = true
            private set
        var clipBoundsInWindow: Rect? = null
            private set
        var coordinates: LayoutCoordinates? = null
            private set
        private var liveCoordinatesSnapshot: LiveCoordinatesSnapshot? = null

        fun update(
            rect: Rect,
            isAttached: Boolean = true,
            isVisible: Boolean = true,
            clipBoundsInWindow: Rect? = null
        ) {
            bounds = rect
            this.isAttached = isAttached
            this.isVisible = isVisible
            this.clipBoundsInWindow = clipBoundsInWindow
            coordinates = null
            liveCoordinatesSnapshot = null
        }

        fun update(coordinates: LayoutCoordinates) {
            liveCoordinatesSnapshot = null
            this.coordinates = coordinates
            this.isAttached = coordinates.isAttached
            if (!coordinates.isAttached) {
                bounds = Rect.Zero
                isVisible = false
                clipBoundsInWindow = Rect.Zero
                return
            }
            val pos = coordinates.positionInWindow()
            bounds = Rect(
                left = pos.x,
                top = pos.y,
                right = pos.x + coordinates.size.width,
                bottom = pos.y + coordinates.size.height
            )
            val visible = coordinates.clippedBoundsInWindow()
            clipBoundsInWindow = visible
            isVisible = !visible.isEmpty
        }

        internal fun updateLiveCoordinates(
            isAttached: Boolean,
            clippedBoundsInWindow: Rect
        ) {
            coordinates = null
            liveCoordinatesSnapshot = LiveCoordinatesSnapshot(isAttached, clippedBoundsInWindow)
            this.isAttached = isAttached
            this.clipBoundsInWindow = clippedBoundsInWindow
            this.isVisible = isAttached && !clippedBoundsInWindow.isEmpty
            bounds = clippedBoundsInWindow
        }

        internal fun contains(windowOffset: Offset): Boolean {
            val coords = coordinates
            if (coords != null) {
                return containsLiveCoordinates(
                    isAttached = coords.isAttached,
                    clippedBoundsInWindow = coords.clippedBoundsInWindow(),
                    windowOffset = windowOffset
                )
            }
            liveCoordinatesSnapshot?.let { snapshot ->
                return containsLiveCoordinates(
                    isAttached = snapshot.isAttached,
                    clippedBoundsInWindow = snapshot.clippedBoundsInWindow,
                    windowOffset = windowOffset
                )
            }
            if (!isAttached || !isVisible) {
                return false
            }
            val visibleBounds = clipBoundsInWindow?.let { bounds.intersect(it) } ?: bounds
            return !visibleBounds.isEmpty && visibleBounds.contains(windowOffset)
        }
    }

    fun register(): Handle {
        val handle = Handle()
        synchronized(handles) {
            handles.add(handle)
        }
        return handle
    }

    fun unregister(handle: Handle) {
        synchronized(handles) {
            handles.remove(handle)
        }
    }

    fun isHit(windowOffset: Offset): Boolean {
        val snapshot = synchronized(handles) { handles.toList() }
        return snapshot.any { it.contains(windowOffset) }
    }
}

internal fun LayoutCoordinates.clippedBoundsInWindow(): Rect {
    val ancestors = buildList {
        var parent = parentLayoutCoordinates
        while (parent != null) {
            add(WindowBoundsSnapshot(parent.isAttached, parent.boundsInWindow()))
            parent = parent.parentLayoutCoordinates
        }
    }
    return computeClippedBoundsInWindow(
        node = WindowBoundsSnapshot(isAttached, boundsInWindow()),
        ancestors = ancestors
    )
}

private val FallbackMessageListTouchTargetHitTester = MessageListTouchTargetHitTester()

val LocalMessageListTouchTargetHitTester = staticCompositionLocalOf {
    FallbackMessageListTouchTargetHitTester
}

fun Modifier.messageListTouchTarget(): Modifier = composed {
    val tester = LocalMessageListTouchTargetHitTester.current
    val handle = remember(tester) { tester.register() }
    DisposableEffect(tester, handle) {
        onDispose { tester.unregister(handle) }
    }
    Modifier.onGloballyPositioned { coords ->
        handle.update(coords)
    }
}
