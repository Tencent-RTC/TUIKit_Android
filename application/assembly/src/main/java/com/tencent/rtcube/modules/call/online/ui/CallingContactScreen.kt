package com.tencent.rtcube.modules.call.online.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.tencent.rtcube.modules.R

data class CallingUserModel(
    val userId: String,
    val name: String,
    val avatar: String,
)

private sealed class SearchState {
    object Idle : SearchState()           // Initial / typing
    object NotFound : SearchState()       // No result
    data class Found(val users: List<CallingUserModel>) : SearchState() // Has results
}

@Composable
fun CallingContactScreen(
    currentUserId: String = "",
    onSearchUser: (userId: String, onResult: (List<CallingUserModel>) -> Unit) -> Unit = { _, _ -> },
    onStartCall: (userId: String) -> Unit = {},
    onShowToast: (String) -> Unit = {},
    onBack: () -> Unit = {},
    onNavigateToGuide: () -> Unit = {},
) {
    var searchText by remember { mutableStateOf("") }
    var searchState by remember { mutableStateOf<SearchState>(SearchState.Idle) }
    val keyboardController = LocalSoftwareKeyboardController.current
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    val doSearch: () -> Unit = remember(searchText) {
        {
            if (searchText.isNotBlank()) {
                keyboardController?.hide()
                onSearchUser(searchText) { results ->
                    searchState = if (results.isEmpty()) SearchState.NotFound else SearchState.Found(results)
                }
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color.White,
        topBar = {
            CallTitleBar(title = stringResource(R.string.assembly_call_btn_start_call), onBack = onBack)
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp),
        ) {

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(colorResource(R.color.call_color_bg_btn_unselect)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Spacer(modifier = Modifier.width(12.dp))
                Image(
                    painter = painterResource(id = R.drawable.call_contact_ic_search),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                BasicTextField(
                    value = searchText,
                    onValueChange = { input ->
                        searchText = input
                        if (input.isEmpty()) searchState = SearchState.Idle
                    },
                    singleLine = true,
                    textStyle = TextStyle(fontSize = 14.sp, color = Color(0xFF262B32)),
                    cursorBrush = SolidColor(Color(0xFF90EE90)),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Search,
                    ),
                    keyboardActions = KeyboardActions(onSearch = { doSearch() }),
                    visualTransformation = VisualTransformation.None,
                    decorationBox = { innerTextField ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 8.dp),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            if (searchText.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.assembly_call_input_registered_user),
                                    fontSize = 13.sp,
                                    color = Color(0xFFBBBBBB),
                                )
                            }
                            innerTextField()
                        }
                    },
                    modifier = Modifier.weight(1f),
                )
                if (searchText.isNotEmpty()) {
                    Image(
                        painter = painterResource(id = R.drawable.call_contact_ic_clear),

                        contentDescription = null,
                        modifier = Modifier
                            .size(16.dp)
                            .clickable {
                                searchText = ""
                                searchState = SearchState.Idle
                            },
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                TextButton(
                    onClick = { doSearch() },
                    modifier = Modifier.padding(end = 4.dp),
                ) {
                    Text(
                        text = stringResource(R.string.assembly_call_btn_search),
                        color = colorResource(R.color.call_color_tab_select),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Normal,
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (currentUserId.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp)
                        .clickable {
                            clipboardManager.setText(AnnotatedString(currentUserId))
                            onShowToast(context.getString(R.string.assembly_call_copied_to_clipboard))
                        },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = buildAnnotatedString {
                            append(stringResource(R.string.assembly_call_my_user_id_template, currentUserId))
                            append("  ")
                            withStyle(SpanStyle(color = colorResource(R.color.call_color_call_search_text))) {
                                append(stringResource(R.string.assembly_call_btn_copy))
                            }
                        },
                        fontSize = 12.sp,
                        color = Color(0xFF515151),
                    )
                }
                HorizontalDivider(
                    color = Color(0xFFF0F0F0),
                    thickness = 1.dp,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }

            when (val state = searchState) {
                is SearchState.Found -> {
                    Text(
                        text = stringResource(R.string.assembly_call_search_results_template, state.users.size),
                        fontSize = 12.sp,
                        color = Color(0xFF757575),
                        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
                    )
                    LazyColumn(modifier = Modifier.fillMaxWidth()) {
                        items(state.users) { user ->
                            CallingUserRow(
                                user = user,
                                onCall = {
                                    if (user.userId == currentUserId) {
                                        onShowToast(context.getString(R.string.assembly_call_cannot_invite_self))
                                    } else {
                                        onStartCall(user.userId)
                                    }
                                },
                            )
                        }
                    }
                }

                is SearchState.NotFound -> {
                    Spacer(modifier = Modifier.height(16.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFFFF3F3))
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.assembly_call_user_not_exist),
                            fontSize = 13.sp,
                            color = colorResource(R.color.call_color_cancel),
                        )
                    }
                    CallingContactGuide(onNavigateToGuide = onNavigateToGuide)
                }

                is SearchState.Idle -> {
                    CallingContactGuide(onNavigateToGuide = onNavigateToGuide)
                }
            }
        }
    }
}

@Composable
private fun CallingContactGuide(onNavigateToGuide: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.offset(y = (-40).dp),
        ) {
            Image(
                painter = painterResource(id = R.drawable.call_contact_ic_guidance),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .width(220.dp)
                    .height(160.dp),
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier
                    .clickable { onNavigateToGuide() }
                    .padding(vertical = 8.dp),
            ) {
                Text(
                    text = stringResource(R.string.assembly_call_call_other_users_guide),
                    fontSize = 14.sp,
                    color = colorResource(R.color.call_color_call_search_text),
                    fontWeight = FontWeight.Medium,
                )
                Spacer(modifier = Modifier.width(4.dp))
                Image(
                    painter = painterResource(id = R.drawable.call_common_ic_arrow_right),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@Composable
private fun CallingUserRow(
    user: CallingUserModel,
    onCall: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(68.dp)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (user.avatar.isNotEmpty()) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(user.avatar)
                        .crossfade(true)
                        .build(),
                    contentDescription = user.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    error = painterResource(id = R.drawable.call_contact_ic_default_avatar),
                    placeholder = painterResource(id = R.drawable.call_contact_ic_default_avatar),
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFFD0E4FF)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = (user.name.ifEmpty { user.userId }).take(1).uppercase(),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = colorResource(R.color.call_color_call_search_text),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = user.name.ifEmpty { user.userId },
                fontSize = 15.sp,
                fontWeight = FontWeight.Normal,
                color = Color(0xFF262B32),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Spacer(modifier = Modifier.width(2.dp))
            Text(
                text = "(${user.userId})",
                fontSize = 12.sp,
                color = Color(0xFF9E9E9E),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
        }

        OutlinedButton(
            onClick = onCall,
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = colorResource(R.color.call_color_call_search_text)),
            border = BorderStroke(1.dp, colorResource(R.color.call_color_call_search_text)),
            modifier = Modifier.height(34.dp),
            contentPadding = PaddingValues(horizontal = 14.dp),
        ) {
            Text(
                text = stringResource(R.string.assembly_call_btn_streaming_call),
                fontSize = 13.sp,
                color = colorResource(R.color.call_color_call_search_text),
                fontWeight = FontWeight.Medium,
            )
        }
    }
    HorizontalDivider(
        color = Color(0xFFF0F0F0),
        thickness = 1.dp,
    )
}
