package io.trtc.tuikit.chat.uikit.components.contactlist.ui.blacklist

import android.widget.Toast
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.roundToIntRect
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import io.trtc.tuikit.atomicxcore.api.contact.ContactInfo
import io.trtc.tuikit.chat.uikit.compose.R
import io.trtc.tuikit.chat.uikit.components.contactlist.utils.displayName
import io.trtc.tuikit.chat.uikit.components.contactlist.utils.matchesSearchQuery
import io.trtc.tuikit.chat.uikit.components.contactlist.viewmodel.BlacklistViewModel
import io.trtc.tuikit.chat.uikit.components.contactlist.viewmodel.BlacklistViewModelFactory
import io.trtc.tuikit.chat.uikit.components.theme.LocalTheme
import io.trtc.tuikit.chat.uikit.components.widgets.DialogNavBar
import io.trtc.tuikit.chat.uikit.components.widgets.DialogNavBarMode
import io.trtc.tuikit.chat.uikit.components.widgets.SearchBar
import io.trtc.tuikit.chat.uikit.components.widgets.azorderedlist.AZOrderedList
import io.trtc.tuikit.chat.uikit.components.widgets.azorderedlist.AZOrderedListItem
import kotlin.math.max
import kotlin.math.min

private const val MENU_ENTER_ANIMATION_MS = 180
private const val MENU_EXIT_ANIMATION_MS = 160

private data class BlacklistMenuState(
    val user: ContactInfo,
    val anchorBoundsInWindow: IntRect
)

@Composable
fun Blacklist(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    blacklistViewModelFactory: BlacklistViewModelFactory = BlacklistViewModelFactory(),
    onContactClick: (ContactInfo) -> Unit
) {
    val colors = LocalTheme.current.colors
    val context = LocalContext.current
    val density = LocalDensity.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val hideKeyboard: () -> Unit = {
        keyboardController?.hide()
        focusManager.clearFocus()
    }
    val blacklistViewModel =
        viewModel(BlacklistViewModel::class, factory = blacklistViewModelFactory)

    val blacklistUsers by blacklistViewModel.blacklistUsers.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var menuState by remember { mutableStateOf<BlacklistMenuState?>(null) }
    var listContainerBoundsInWindow by remember { mutableStateOf(IntRect.Zero) }
    var lastPressLocal by remember { mutableStateOf(Offset.Zero) }

    LaunchedEffect(Unit) {
        blacklistViewModel.fetchBlacklistUsers()
    }

    val filteredUsers = remember(blacklistUsers, searchQuery) {
        blacklistUsers.filter { user -> user.matchesSearchQuery(searchQuery) }
    }

    Box(
        modifier = modifier
            .navigationBarsPadding()
            .statusBarsPadding()
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            DialogNavBar(
                mode = DialogNavBarMode.BackTitle,
                title = stringResource(R.string.contact_list_blacklist),
                onLeadingClick = onBackClick
            )

            HorizontalDivider(
                thickness = 0.5.dp,
                color = colors.strokeColorSecondary
            )

            SearchBar(
                onQueryChanged = { searchQuery = it },
                inputHeight = 36.dp,
                debounceMs = 300L,
                paddingVertical = 16.dp,
                expandTouchTargets = false
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .onGloballyPositioned { listContainerBoundsInWindow = it.boundsInWindow().roundToIntRect() }
                    .pointerInput(hideKeyboard) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                val change = event.changes.firstOrNull() ?: continue
                                if (change.changedToDownIgnoreConsumed()) {
                                    lastPressLocal = change.position
                                    hideKeyboard()
                                }
                            }
                        }
                    }
            ) {
                if (filteredUsers.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(
                                if (searchQuery.isBlank()) {
                                    R.string.contact_list_no_blacklist_users
                                } else {
                                    R.string.contact_list_cannot_found_blacklist_user
                                }
                            ),
                            fontSize = 17.sp,
                            color = colors.textColorSecondary
                        )
                    }
                } else {
                    AZOrderedList(
                        modifier = Modifier.fillMaxSize(),
                        dataSource = filteredUsers.map { contactInfo ->
                            AZOrderedListItem(
                                key = contactInfo.userID,
                                label = contactInfo.displayName,
                                avatarUrl = contactInfo.avatarURL,
                                extraData = contactInfo
                            )
                        },
                        onItemClick = { item ->
                            hideKeyboard()
                            onContactClick(item.extraData)
                        },
                        onItemLongClick = { item ->
                            hideKeyboard()
                            val itemHeight = with(density) { 60.dp.roundToPx() }
                            val pressY = listContainerBoundsInWindow.top + lastPressLocal.y.toInt()
                            val top = pressY - itemHeight / 2
                            menuState = BlacklistMenuState(
                                user = item.extraData,
                                anchorBoundsInWindow = IntRect(
                                    left = listContainerBoundsInWindow.left,
                                    top = top,
                                    right = listContainerBoundsInWindow.right,
                                    bottom = top + itemHeight
                                )
                            )
                        },
                        onUserInteraction = hideKeyboard
                    )
                }

                menuState?.let { state ->
                    if (listContainerBoundsInWindow != IntRect.Zero) {
                        BlacklistRemovePopupMenu(
                            menuState = state,
                            containerBoundsInWindow = listContainerBoundsInWindow,
                            onDismissed = { shouldRemove ->
                                menuState = null
                                if (shouldRemove) {
                                    blacklistViewModel.removeFromBlacklist(state.user.userID) { desc ->
                                        if (desc.isNotBlank()) {
                                            Toast.makeText(context, desc, Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BlacklistRemovePopupMenu(
    menuState: BlacklistMenuState,
    containerBoundsInWindow: IntRect,
    onDismissed: (shouldRemove: Boolean) -> Unit
) {
    val colors = LocalTheme.current.colors
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val removeText = stringResource(R.string.contact_list_blacklist_remove)

    var menuWidthPx by remember { mutableStateOf(0) }
    var menuHeightPx by remember { mutableStateOf(0) }

    val anchor = menuState.anchorBoundsInWindow
    val anchorX = anchor.left - containerBoundsInWindow.left
    val anchorY = anchor.top - containerBoundsInWindow.top
    val anchorWidth = anchor.width
    val anchorHeight = anchor.height
    val hostWidth = containerBoundsInWindow.width
    val hostHeight = containerBoundsInWindow.height

    val position = remember(menuState, menuWidthPx, menuHeightPx, containerBoundsInWindow, layoutDirection) {
        with(density) {
            val margin = 4.dp.roundToPx()
            val horizontalInset = 16.dp.roundToPx()
            val aboveOverlap = 8.dp.roundToPx()
            val belowOverlap = max(anchorHeight * 3 / 5, 24.dp.roundToPx())
            val isRtl = layoutDirection == LayoutDirection.Rtl
            val preferredX = if (isRtl) {
                anchorX + horizontalInset
            } else {
                anchorX + anchorWidth - menuWidthPx - horizontalInset
            }
            val maxX = max(hostWidth - menuWidthPx - margin, margin)
            val x = preferredX.coerceIn(margin, maxX)
            val belowY = anchorY + anchorHeight - belowOverlap
            val aboveY = anchorY - menuHeightPx + aboveOverlap
            val showBelow = belowY + menuHeightPx <= hostHeight - margin || aboveY < margin
            val y = if (showBelow) {
                min(belowY, hostHeight - menuHeightPx - margin)
            } else {
                max(aboveY, margin)
            }
            Triple(x, y, showBelow)
        }
    }
    val showBelow = position.third

    var dismissing by remember { mutableStateOf(false) }
    var removeAfterDismiss by remember { mutableStateOf(false) }
    val progress = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(durationMillis = MENU_ENTER_ANIMATION_MS))
    }

    LaunchedEffect(dismissing) {
        if (dismissing) {
            progress.animateTo(0f, tween(durationMillis = MENU_EXIT_ANIMATION_MS))
            onDismissed(removeAfterDismiss)
        }
    }

    fun startDismiss(remove: Boolean) {
        if (dismissing) return
        removeAfterDismiss = remove
        dismissing = true
    }

    val offsetPx = with(density) { (if (dismissing) 6.dp else 8.dp).toPx() }
    val pivotXFraction = with(density) {
        if (menuWidthPx <= 0) {
            1f
        } else if (layoutDirection == LayoutDirection.Rtl) {
            16.dp.toPx() / menuWidthPx
        } else {
            1f - 16.dp.toPx() / menuWidthPx
        }
    }

    Popup(
        alignment = Alignment.TopStart,
        offset = IntOffset(position.first, position.second),
        onDismissRequest = { startDismiss(false) },
        properties = PopupProperties(focusable = true)
    ) {
        Surface(
            modifier = Modifier
                .onGloballyPositioned {
                    menuWidthPx = it.size.width
                    menuHeightPx = it.size.height
                }
                .graphicsLayer {
                    alpha = progress.value
                    val scale = 0.92f + 0.08f * progress.value
                    scaleX = scale
                    scaleY = scale
                    translationY = (1f - progress.value) * offsetPx * (if (showBelow) -1f else 1f)
                    transformOrigin = TransformOrigin(pivotXFraction, if (showBelow) 0f else 1f)
                },
            shape = RoundedCornerShape(8.dp),
            color = colors.bgColorOperate,
            shadowElevation = 8.dp
        ) {
            Text(
                text = removeText,
                fontSize = 16.sp,
                color = colors.textColorError,
                maxLines = 1,
                textAlign = TextAlign.Start,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { startDismiss(true) }
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            )
        }
    }
}
