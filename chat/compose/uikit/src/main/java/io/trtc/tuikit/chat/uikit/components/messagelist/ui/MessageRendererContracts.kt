package io.trtc.tuikit.chat.uikit.components.messagelist.ui

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntOffset
import io.trtc.tuikit.chat.uikit.components.messagelist.config.MessageListConfigProtocol
import io.trtc.tuikit.chat.uikit.components.theme.ColorScheme
import io.trtc.tuikit.chat.uikit.components.theme.LocalTheme
import io.trtc.tuikit.atomicxcore.api.message.MessageInfo
import java.util.concurrent.atomic.AtomicBoolean

enum class BubbleStyle {
    DEFAULT,
    NONE,
    CARD
}

data class MessageRenderConfig(
    val showMessageMeta: Boolean = true,
    val useDefaultBubble: Boolean = true,
    val bubbleStyle: BubbleStyle = if (useDefaultBubble) BubbleStyle.DEFAULT else BubbleStyle.NONE
)

interface MessageRenderActions {
    fun openImageViewer(message: MessageInfo)
    fun openVideoPlayer(message: MessageInfo)
    fun playSound(message: MessageInfo)
    fun toggleSelection(message: MessageInfo)

    /**
     * Shows the long-press menu using the full [anchorBounds].
     * Pass null to fall back to list-centered placement.
     */
    fun showLongPressMenu(message: MessageInfo, anchorBounds: Rect?)

    fun showLongPressMenu(message: MessageInfo, anchor: IntOffset) {
        showLongPressMenu(
            message,
            Rect(
                left = anchor.x.toFloat(),
                top = anchor.y.toFloat(),
                right = anchor.x.toFloat() + 1f,
                bottom = anchor.y.toFloat() + 1f
            )
        )
    }

    fun showLongPressMenu(message: MessageInfo) {
        showLongPressMenu(message, null)
    }
}

internal object NoOpMessageRenderActions : MessageRenderActions {
    override fun openImageViewer(message: MessageInfo) = Unit
    override fun openVideoPlayer(message: MessageInfo) = Unit
    override fun playSound(message: MessageInfo) = Unit
    override fun toggleSelection(message: MessageInfo) = Unit
    override fun showLongPressMenu(message: MessageInfo, anchorBounds: Rect?) = Unit
}

class MessageRenderContext(
    val conversationID: String,
    val message: MessageInfo,
    val colors: ColorScheme,
    val config: MessageListConfigProtocol,
    val isMultiSelectMode: Boolean,
    val isSelected: Boolean,
    val actions: MessageRenderActions
)

val LocalMessageRenderContext = compositionLocalOf<MessageRenderContext> {
    error("No MessageRenderContext provided")
}

val LocalMessageRenderActions = compositionLocalOf<MessageRenderActions> { NoOpMessageRenderActions }

interface MessageRenderer {
    @Composable
    fun Render(message: MessageInfo) {
        error("Override Render(message) or Render(message, context)")
    }

    @Composable
    fun Render(message: MessageInfo, context: MessageRenderContext) {
        Render(message)
    }

    val renderConfig: MessageRenderConfig
        get() = MessageRenderConfig()

    /**
     * Called when the composed message leaves composition, after [onDetached].
     *
     * Compose has no RecyclerView cache. Under the default LazyColumn, [onDetached] and
     * [onRecycled] may run back-to-back in the same frame. A two-beat gap mainly appears
     * during prefetch or animateItem windows.
     *
     * Implementations must be re-entrant and null-safe.
     * The Activity entering the background does not trigger this hook; observe
     * Lifecycle yourself if needed.
     */
    fun onRecycled(message: MessageInfo) = Unit

    /**
     * Called when the composed message scrolls out of the visible viewport, or immediately
     * before [onRecycled] if it is disposed while still visible.
     *
     * Compose has no RecyclerView cache. Under the default LazyColumn, [onDetached] and
     * [onRecycled] may run back-to-back in the same frame. A two-beat gap mainly appears
     * during prefetch or animateItem windows.
     *
     * Implementations must be re-entrant and null-safe.
     * The Activity entering the background does not trigger this hook; observe
     * Lifecycle yourself if needed.
     */
    fun onDetached(message: MessageInfo) = Unit

    /**
     * Called when the composed message re-enters the visible viewport after [onDetached].
     *
     * Not invoked on first appearance. Resume work paused in [onDetached] here.
     * Implementations must be re-entrant and null-safe.
     */
    fun onAttached(message: MessageInfo) = Unit
}

interface MessageCellRenderer {
    @Composable
    fun Render(message: MessageInfo) {
        error("Override Render(message) or Render(message, context)")
    }

    @Composable
    fun Render(message: MessageInfo, context: MessageRenderContext) {
        Render(message)
    }

    /**
     * Called when the composed message leaves composition, after [onDetached].
     *
     * Compose has no RecyclerView cache. Under the default LazyColumn, [onDetached] and
     * [onRecycled] may run back-to-back in the same frame. A two-beat gap mainly appears
     * during prefetch or animateItem windows.
     *
     * Implementations must be re-entrant and null-safe.
     * The Activity entering the background does not trigger this hook; observe
     * Lifecycle yourself if needed.
     */
    fun onRecycled(message: MessageInfo) = Unit

    /**
     * Called when the composed message scrolls out of the visible viewport, or immediately
     * before [onRecycled] if it is disposed while still visible.
     *
     * Compose has no RecyclerView cache. Under the default LazyColumn, [onDetached] and
     * [onRecycled] may run back-to-back in the same frame. A two-beat gap mainly appears
     * during prefetch or animateItem windows.
     *
     * Implementations must be re-entrant and null-safe.
     * The Activity entering the background does not trigger this hook; observe
     * Lifecycle yourself if needed.
     */
    fun onDetached(message: MessageInfo) = Unit

    /**
     * Called when the composed message re-enters the visible viewport after [onDetached].
     *
     * Not invoked on first appearance. Resume work paused in [onDetached] here.
     * Implementations must be re-entrant and null-safe.
     */
    fun onAttached(message: MessageInfo) = Unit
}

fun interface MessageRendererMatcher {
    fun matches(message: MessageInfo): Boolean
}

@Composable
internal fun rememberMessageRenderContext(
    message: MessageInfo,
    isMultiSelectMode: Boolean,
    isSelected: Boolean,
    actions: MessageRenderActions = LocalMessageRenderActions.current
): MessageRenderContext {
    val colors = LocalTheme.current.colors
    val config = LocalMessageListConfig.current
    val conversationID = LocalMessageListViewModel.current.conversationID
    return remember(
        conversationID,
        message,
        colors,
        config,
        isMultiSelectMode,
        isSelected,
        actions
    ) {
        MessageRenderContext(
            conversationID = conversationID,
            message = message,
            colors = colors,
            config = config,
            isMultiSelectMode = isMultiSelectMode,
            isSelected = isSelected,
            actions = actions
        )
    }
}

@Composable
internal fun ProvideMessageRenderContext(
    context: MessageRenderContext,
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(
        LocalMessageRenderContext provides context,
        LocalMessageRenderActions provides context.actions
    ) {
        content()
    }
}

private val UntrackedVisibleMessageIds: State<Set<String>?> = mutableStateOf(null)

internal val LocalVisibleMessageIds = staticCompositionLocalOf<State<Set<String>?>> {
    UntrackedVisibleMessageIds
}

private class RendererAttachState {
    var hasBeenVisible = false
    val detached = AtomicBoolean(false)
}

@Composable
internal fun rememberVisibleMessageIds(listState: LazyListState): State<Set<String>?> {
    val visibleIds = remember { mutableStateOf<Set<String>?>(emptySet()) }
    LaunchedEffect(listState) {
        snapshotFlow {
            val items = listState.layoutInfo.visibleItemsInfo
            buildSet {
                items.forEach { info ->
                    val key = info.key as? String
                    if (key != null) {
                        add(key)
                    }
                }
            }
        }.collect { ids ->
            visibleIds.value = ids
        }
    }
    return visibleIds
}

@Composable
internal fun MessageRendererDisposeEffect(
    message: MessageInfo,
    renderer: MessageRenderer,
    cellRenderer: MessageCellRenderer?
) {
    val currentMessage = rememberUpdatedState(message)
    val currentRenderer = rememberUpdatedState(renderer)
    val currentCellRenderer = rememberUpdatedState(cellRenderer)
    val visibleIdsState = LocalVisibleMessageIds.current
    val messageId = message.msgID
    val attachState = remember(messageId) { RendererAttachState() }

    LaunchedEffect(messageId, visibleIdsState) {
        snapshotFlow {
            val ids = visibleIdsState.value
            ids == null || messageId in ids
        }.collect { visible ->
            if (visible) {
                val resumed = attachState.hasBeenVisible &&
                    attachState.detached.compareAndSet(true, false)
                attachState.hasBeenVisible = true
                if (resumed) {
                    dispatchRendererAttached(
                        message = currentMessage.value,
                        renderer = currentRenderer.value,
                        cellRenderer = currentCellRenderer.value
                    )
                }
            } else if (attachState.hasBeenVisible && attachState.detached.compareAndSet(false, true)) {
                dispatchRendererDetached(
                    message = currentMessage.value,
                    renderer = currentRenderer.value,
                    cellRenderer = currentCellRenderer.value
                )
            }
        }
    }

    DisposableEffect(messageId) {
        onDispose {
            val latestMessage = currentMessage.value
            val latestRenderer = currentRenderer.value
            val latestCellRenderer = currentCellRenderer.value
            if (attachState.detached.compareAndSet(false, true)) {
                dispatchRendererDetached(latestMessage, latestRenderer, latestCellRenderer)
            }
            dispatchRendererRecycled(latestMessage, latestRenderer, latestCellRenderer)
        }
    }
}

private fun dispatchRendererAttached(
    message: MessageInfo,
    renderer: MessageRenderer,
    cellRenderer: MessageCellRenderer?
) {
    if (cellRenderer != null) {
        cellRenderer.onAttached(message)
    } else {
        renderer.onAttached(message)
    }
}

private fun dispatchRendererDetached(
    message: MessageInfo,
    renderer: MessageRenderer,
    cellRenderer: MessageCellRenderer?
) {
    if (cellRenderer != null) {
        cellRenderer.onDetached(message)
    } else {
        renderer.onDetached(message)
    }
}

private fun dispatchRendererRecycled(
    message: MessageInfo,
    renderer: MessageRenderer,
    cellRenderer: MessageCellRenderer?
) {
    if (cellRenderer != null) {
        cellRenderer.onRecycled(message)
    } else {
        renderer.onRecycled(message)
    }
}
