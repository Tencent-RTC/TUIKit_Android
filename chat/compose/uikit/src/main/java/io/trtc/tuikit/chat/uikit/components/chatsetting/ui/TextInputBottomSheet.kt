package io.trtc.tuikit.chat.uikit.components.chatsetting.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.trtc.tuikit.chat.uikit.compose.R
import io.trtc.tuikit.chat.uikit.components.theme.LocalTheme
import kotlinx.coroutines.delay

@Composable
fun TextInputBottomSheet(
    isVisible: Boolean,
    title: String = "",
    initialText: String = "",
    placeholder: String = "",
    maxLength: Int = 0,
    multiline: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    val colors = LocalTheme.current.colors
    val radius = LocalTheme.current.radius
    if (isVisible) {
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(
                dismissOnBackPress = true,
                dismissOnClickOutside = true,
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .imePadding()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onDismiss() },
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    modifier = Modifier
                        .width(327.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {},
                    shape = RoundedCornerShape(radius.alertRadius),
                    color = colors.bgColorDialog
                ) {
                    TextInputDialogContent(
                        title = title,
                        initialText = initialText,
                        placeholder = placeholder,
                        maxLength = maxLength,
                        multiline = multiline,
                        onConfirm = { result ->
                            onDismiss()
                            onConfirm(result)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun TextInputDialogContent(
    title: String,
    initialText: String,
    placeholder: String,
    maxLength: Int,
    multiline: Boolean,
    onConfirm: (String) -> Unit
) {
    val colors = LocalTheme.current.colors
    val fonts = LocalTheme.current.fonts
    val radius = LocalTheme.current.radius
    val spacings = LocalTheme.current.spacings
    var textFieldValue by remember {
        mutableStateOf(
            TextFieldValue(
                text = initialText,
                selection = TextRange(initialText.length)
            )
        )
    }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val scrollState = rememberScrollState()
    val showCounter = multiline && maxLength > 0

    LaunchedEffect(Unit) {
        delay(200)
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(scrollState)
            .padding(spacings.contentSpacing)
    ) {
        Text(
            text = title,
            fontSize = fonts.caption1Bold.size,
            fontWeight = FontWeight.Bold,
            color = colors.textColorPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(spacings.contentSpacing))

        val inputModifier = Modifier
            .fillMaxWidth()
            .then(
                if (multiline) {
                    Modifier.heightIn(min = 160.dp, max = 280.dp)
                } else {
                    Modifier.height(40.dp)
                }
            )
            .background(
                color = colors.bgColorDialog,
                shape = RoundedCornerShape(radius.smallRadius)
            )
            .border(
                width = 1.dp,
                color = colors.strokeColorPrimary,
                shape = RoundedCornerShape(radius.smallRadius)
            )
            .padding(
                horizontal = spacings.bubbleSpacing,
                vertical = if (multiline) spacings.iconIconSpacing else 0.dp
            )

        Box(
            modifier = inputModifier,
            contentAlignment = if (multiline) Alignment.TopStart else Alignment.CenterStart
        ) {
            BasicTextField(
                value = textFieldValue,
                onValueChange = { newValue ->
                    if (maxLength <= 0 || newValue.text.length <= maxLength) {
                        textFieldValue = newValue
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                textStyle = TextStyle(
                    fontSize = fonts.caption1Regular.size,
                    color = colors.textColorPrimary,
                    fontWeight = FontWeight.W400
                ),
                singleLine = !multiline,
                maxLines = if (multiline) 10 else 1,
                cursorBrush = SolidColor(colors.textColorLink),
                decorationBox = { innerTextField ->
                    if (textFieldValue.text.isEmpty() && placeholder.isNotEmpty()) {
                        Text(
                            text = placeholder,
                            fontSize = fonts.caption1Regular.size,
                            color = colors.textColorTertiary,
                            fontWeight = FontWeight.W400
                        )
                    }
                    innerTextField()
                }
            )
        }

        if (showCounter) {
            Text(
                text = "${textFieldValue.text.length}/$maxLength",
                fontSize = fonts.caption3Regular.size,
                color = colors.textColorTertiary,
                textAlign = TextAlign.End,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
            )
        }

        Spacer(modifier = Modifier.height(spacings.contentSpacing))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp)
                .background(
                    color = colors.buttonColorPrimaryDefault,
                    shape = RoundedCornerShape(18.dp)
                )
                .clickable {
                    keyboardController?.hide()
                    onConfirm(textFieldValue.text.trim())
                },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.base_component_confirm),
                fontSize = fonts.caption1Regular.size,
                fontWeight = FontWeight.W400,
                color = colors.textColorButton
            )
        }
    }
}
