package com.tencent.rtcube.v2.login.components.views

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tencent.rtcube.v2.login.R

private const val CODE_LENGTH = 6

/**
 * 6-cell invite code input field.
 */
@Composable
fun InviteCodeInputField(
    code: String,
    isError: Boolean,
    onCodeChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequesters = remember { List(CODE_LENGTH) { FocusRequester() } }

    LaunchedEffect(Unit) {
        focusRequesters[0].requestFocus()
    }

    // Split code string into per-cell characters
    val codeChars = remember(code) {
        Array(CODE_LENGTH) { i -> if (i < code.length) code[i].toString() else "" }
    }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        for (i in 0 until CODE_LENGTH) {
            var isFocused by remember { mutableStateOf(false) }
            val borderColor = when {
                isError -> colorResource(R.color.login_color_red_light6)
                isFocused -> colorResource(R.color.login_color_theme_light6)
                else -> colorResource(R.color.login_color_input_normal)
            }
            var cellValue by remember(codeChars[i]) { mutableStateOf(codeChars[i]) }

            BasicTextField(
                value = cellValue,
                onValueChange = { newVal ->
                    val filtered = newVal.filter { it.isLetterOrDigit() }
                    if (filtered.isNotEmpty()) {
                        val char = filtered.last().toString()
                        cellValue = char
                        val newCode = buildCodeString(code, i, char)
                        onCodeChange(newCode)
                        if (i < CODE_LENGTH - 1) {
                            focusRequesters[i + 1].requestFocus()
                        }
                    } else {
                        cellValue = ""
                        val newCode = buildCodeString(code, i, "")
                        onCodeChange(newCode)
                        if (i > 0) {
                            focusRequesters[i - 1].requestFocus()
                        }
                    }
                },
                modifier = Modifier
                    .width(45.dp)
                    .height(45.dp)
                    .border(1.dp, borderColor, RoundedCornerShape(4.dp))
                    .background(Color.White, RoundedCornerShape(4.dp))
                    .focusRequester(focusRequesters[i])
                    .onFocusChanged { focusState -> isFocused = focusState.isFocused },
                textStyle = TextStyle(
                    color = colorResource(R.color.login_main_text),
                    fontSize = 18.sp,
                    textAlign = TextAlign.Center
                ),
                singleLine = true,
                cursorBrush = SolidColor(colorResource(R.color.login_color_theme_light6)),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = if (i == CODE_LENGTH - 1) ImeAction.Done else ImeAction.Next
                ),
                decorationBox = { innerTextField ->
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        innerTextField()
                    }
                }
            )
        }
    }
}

private fun buildCodeString(code: String, index: Int, char: String): String {
    return buildString {
        for (j in 0 until CODE_LENGTH) {
            when {
                j == index -> append(char)
                j < code.length -> append(code[j])
            }
        }
    }.trimEnd()
}
