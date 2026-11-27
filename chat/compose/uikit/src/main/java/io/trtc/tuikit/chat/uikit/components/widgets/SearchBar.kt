package io.trtc.tuikit.chat.uikit.components.widgets

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.trtc.tuikit.chat.uikit.compose.R
import io.trtc.tuikit.chat.uikit.components.theme.LocalTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val SearchIconSize = 15.dp
private val ClearIconSize = 16.dp
private val BackIconSize = 16.dp
private val DefaultInputEndPadding = 36.dp
private val ClearIconMarginEnd = 10.dp
private val BackIconMarginEnd = 10.dp
private val CancelPaddingStart = 12.dp

@Composable
fun SearchBar(
    onQueryChanged: (String) -> Unit,
    modifier: Modifier = Modifier,
    showBack: Boolean = false,
    showCancel: Boolean = false,
    inputHeight: Dp = 40.dp,
    hint: String? = null,
    debounceMs: Long = 300L,
    paddingHorizontal: Dp = 16.dp,
    paddingVertical: Dp = 10.dp,
    paddingBottom: Dp = paddingVertical,
    inputCornerRadius: Dp = 10.dp,
    searchIconMarginStart: Dp = 8.dp,
    inputTextPaddingStart: Dp = 36.dp,
    expandTouchTargets: Boolean = true,
    query: String? = null,
    autoFocus: Boolean = false,
    onCancel: (() -> Unit)? = null,
    onBack: (() -> Unit)? = null,
    onSearch: ((String) -> Unit)? = null,
) {
    val colors = LocalTheme.current.colors
    val hintText = hint ?: stringResource(R.string.search_search_text)
    val cancelText = stringResource(R.string.base_component_cancel)
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    val debounceRef = remember { DebounceRef() }
    val onQueryChangedState by rememberUpdatedState(onQueryChanged)
    val onCancelState by rememberUpdatedState(onCancel)
    val onBackState by rememberUpdatedState(onBack)
    val debounceMsState by rememberUpdatedState(debounceMs)

    var textFieldValue by remember {
        mutableStateOf(
            TextFieldValue(
                text = query.orEmpty(),
                selection = TextRange(query.orEmpty().length)
            )
        )
    }

    LaunchedEffect(query) {
        if (query != null && query != textFieldValue.text) {
            textFieldValue = TextFieldValue(query, TextRange(query.length))
        }
    }

    LaunchedEffect(autoFocus) {
        if (autoFocus) {
            focusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    DisposableEffect(Unit) {
        onDispose { debounceRef.cancel() }
    }

    fun emitQuery(value: String) {
        debounceRef.cancel()
        if (debounceMsState <= 0L) {
            onQueryChangedState(value)
        } else {
            debounceRef.job = scope.launch {
                delay(debounceMsState)
                onQueryChangedState(value)
            }
        }
    }

    fun clearQuery() {
        textFieldValue = TextFieldValue("")
        debounceRef.cancel()
        onQueryChangedState("")
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.bgColorOperate)
            .padding(
                start = paddingHorizontal,
                top = paddingVertical,
                end = paddingHorizontal,
                bottom = paddingBottom
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showBack) {
            SearchBarIcon(
                painter = painterResource(R.drawable.uikit_ic_back),
                tint = colors.textColorSecondary,
                iconSize = BackIconSize,
                touchHeight = inputHeight,
                expandTouchTargets = expandTouchTargets,
                onClick = { onBackState?.invoke() },
                modifier = Modifier.padding(end = BackIconMarginEnd)
            )
        }

        BasicTextField(
            value = textFieldValue,
            onValueChange = { newValue ->
                val textChanged = newValue.text != textFieldValue.text
                textFieldValue = newValue
                if (textChanged) {
                    emitQuery(newValue.text)
                }
            },
            textStyle = TextStyle(
                color = colors.textColorPrimary,
                fontSize = 14.sp
            ),
            cursorBrush = SolidColor(colors.textColorLink),
            singleLine = true,
            keyboardOptions = if (onSearch != null) {
                KeyboardOptions(imeAction = ImeAction.Search)
            } else {
                KeyboardOptions.Default
            },
            keyboardActions = KeyboardActions(
                onSearch = {
                    onSearch?.invoke(textFieldValue.text)
                    keyboardController?.hide()
                }
            ),
            modifier = Modifier
                .weight(1f)
                .height(inputHeight)
                .clip(RoundedCornerShape(inputCornerRadius))
                .background(colors.bgColorInput, RoundedCornerShape(inputCornerRadius))
                .focusRequester(focusRequester),
            decorationBox = { innerTextField ->
                val hasText = textFieldValue.text.isNotEmpty()
                val iconToTextGap = (inputTextPaddingStart - searchIconMarginStart - SearchIconSize)
                    .coerceAtLeast(0.dp)
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = searchIconMarginStart, end = ClearIconMarginEnd),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        painter = painterResource(R.drawable.search_ic_search),
                        contentDescription = hintText,
                        colorFilter = ColorFilter.tint(colors.textColorTertiary, BlendMode.SrcAtop),
                        modifier = Modifier.size(SearchIconSize)
                    )
                    Spacer(modifier = Modifier.width(iconToTextGap))
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (!hasText) {
                            BasicText(
                                text = hintText,
                                style = TextStyle(
                                    color = colors.textColorTertiary,
                                    fontSize = 14.sp
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        innerTextField()
                    }
                    val endReserve = (DefaultInputEndPadding - ClearIconMarginEnd).coerceAtLeast(ClearIconSize)
                    if (hasText) {
                        Spacer(modifier = Modifier.width((endReserve - ClearIconSize).coerceAtLeast(0.dp)))
                        SearchBarIcon(
                            painter = painterResource(R.drawable.search_ic_search_clear),
                            tint = colors.textColorPrimary,
                            iconSize = ClearIconSize,
                            touchHeight = inputHeight,
                            expandTouchTargets = expandTouchTargets,
                            onClick = { clearQuery() },
                            blendMode = BlendMode.SrcAtop
                        )
                    } else {
                        Spacer(modifier = Modifier.width(endReserve))
                    }
                }
            }
        )

        if (showCancel) {
            val cancelInteraction = remember { MutableInteractionSource() }
            BasicText(
                text = cancelText,
                style = TextStyle(
                    color = colors.textColorPrimary,
                    fontSize = 16.sp
                ),
                maxLines = 1,
                modifier = Modifier
                    .clickable(
                        indication = null,
                        interactionSource = cancelInteraction,
                        onClick = {
                            clearQuery()
                            onCancelState?.invoke()
                        }
                    )
                    .padding(start = CancelPaddingStart)
            )
        }
    }
}

@Composable
private fun SearchBarIcon(
    painter: Painter,
    tint: Color,
    iconSize: Dp,
    touchHeight: Dp,
    expandTouchTargets: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    blendMode: BlendMode = BlendMode.SrcIn,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .height(if (expandTouchTargets) touchHeight else iconSize)
            .width(iconSize)
            .clickable(
                indication = null,
                interactionSource = interactionSource,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painter,
            contentDescription = null,
            colorFilter = ColorFilter.tint(tint, blendMode),
            modifier = Modifier.size(iconSize)
        )
    }
}

private class DebounceRef {
    var job: Job? = null

    fun cancel() {
        job?.cancel()
        job = null
    }
}
