package io.trtc.tuikit.chat.uikit.components.contactlist.ui.mygroup

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.trtc.tuikit.atomicxcore.api.contact.ContactInfo
import io.trtc.tuikit.chat.uikit.compose.R
import io.trtc.tuikit.chat.uikit.components.contactlist.utils.displayName
import io.trtc.tuikit.chat.uikit.components.contactlist.utils.matchesSearchQuery
import io.trtc.tuikit.chat.uikit.components.contactlist.viewmodel.MyGroupViewModel
import io.trtc.tuikit.chat.uikit.components.contactlist.viewmodel.MyGroupViewModelFactory
import io.trtc.tuikit.chat.uikit.components.theme.LocalTheme
import io.trtc.tuikit.chat.uikit.components.widgets.DialogNavBar
import io.trtc.tuikit.chat.uikit.components.widgets.DialogNavBarMode
import io.trtc.tuikit.chat.uikit.components.widgets.SearchBar
import io.trtc.tuikit.chat.uikit.components.widgets.azorderedlist.AZOrderedList
import io.trtc.tuikit.chat.uikit.components.widgets.azorderedlist.AZOrderedListItem

@Composable
fun MyGroup(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    myGroupViewModelFactory: MyGroupViewModelFactory = MyGroupViewModelFactory(),
    onGroupClick: (ContactInfo) -> Unit
) {
    val colors = LocalTheme.current.colors
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val hideKeyboard: () -> Unit = {
        keyboardController?.hide()
        focusManager.clearFocus()
    }
    val myGroupViewModel = viewModel(MyGroupViewModel::class, factory = myGroupViewModelFactory)

    val groups by myGroupViewModel.groups.collectAsState()

    var searchQuery by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        myGroupViewModel.fetchGroups()
    }

    val filteredGroups = remember(groups, searchQuery) {
        groups.filter { group -> group.matchesSearchQuery(searchQuery) }
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
                title = stringResource(R.string.contact_list_my_group),
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

            if (filteredGroups.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(hideKeyboard) {
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                    if (event.changes.any { it.changedToDownIgnoreConsumed() }) {
                                        hideKeyboard()
                                    }
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(
                            if (searchQuery.isBlank()) {
                                R.string.contact_list_no_group
                            } else {
                                R.string.contact_list_cannot_found_group
                            }
                        ),
                        fontSize = 17.sp,
                        color = colors.textColorSecondary
                    )
                }
            } else {
                AZOrderedList(
                    modifier = Modifier.fillMaxSize(),
                    dataSource = filteredGroups.map { groupInfo ->
                        AZOrderedListItem(
                            key = groupInfo.userID,
                            label = groupInfo.displayName,
                            avatarUrl = groupInfo.avatarURL,
                            extraData = groupInfo
                        )
                    },
                    onItemClick = { item ->
                        hideKeyboard()
                        onGroupClick(item.extraData)
                    },
                    onUserInteraction = hideKeyboard
                )
            }
        }

    }
}
