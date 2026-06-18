package com.tencent.rtcube.v2.login.components.views

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tencent.rtcube.v2.login.R
import kotlin.math.roundToInt

/** Full-screen semi-transparent loading overlay. */
@Composable
fun FullScreenLoading(message: String = "", modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colorResource(R.color.login_loading_bg)),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(
                modifier = Modifier
                    .height(20.dp)
                    .padding(end = 8.dp),
                color = Color.White,
                strokeWidth = 2.dp
            )
            if (message.isNotEmpty()) {
                Text(text = message, color = Color.White, fontSize = 14.sp)
            }
        }
    }
}

@Composable
fun LoginButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = RoundedCornerShape(28.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFF006EFF),
            disabledContainerColor = Color(0xFFBBBBBB),
            contentColor = Color.White,
            disabledContentColor = Color.White
        )
    ) {
        Text(text = text, fontSize = 18.sp)
    }
}

@Composable
fun LoginTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "",
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Done,
    onImeAction: (() -> Unit)? = null,
    maxLength: Int = Int.MAX_VALUE,
    focusRequester: FocusRequester? = null,
    leadingContent: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val borderColor = if (isFocused) colorResource(R.color.login_color_blue) else colorResource(R.color.login_input_border)

    BasicTextField(
        value = value,
        onValueChange = { if (it.length <= maxLength) onValueChange(it) },
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier),
        textStyle = TextStyle(color = colorResource(R.color.login_main_text), fontSize = 16.sp),
        singleLine = true,
        cursorBrush = SolidColor(colorResource(R.color.login_color_blue)),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
        keyboardActions = KeyboardActions(onAny = { onImeAction?.invoke() }),
        interactionSource = interactionSource,
        visualTransformation =
            if (keyboardType == KeyboardType.Password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        decorationBox = { innerTextField ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .border(1.dp, borderColor, RoundedCornerShape(10.dp))
                    .background(Color.White, RoundedCornerShape(10.dp))
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (leadingContent != null) {
                    leadingContent()
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Box(modifier = Modifier.weight(1f)) {
                    if (value.isEmpty()) {
                        Text(text = placeholder, color = colorResource(R.color.login_text_hint), fontSize = 16.sp)
                    }
                    innerTextField()
                }
                if (trailingContent != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    trailingContent()
                }
            }
        }
    )
}

class ShakeState {
    val offsetX = Animatable(0f)

    suspend fun shake() {
        offsetX.animateTo(
            targetValue = 0f,
            animationSpec = keyframes {
                durationMillis = 400
                0f at 0
                (-10f) at 50
                10f at 100
                (-10f) at 150
                10f at 200
                (-6f) at 250
                6f at 300
                0f at 400
            }
        )
    }
}

@Composable
fun rememberShakeState(): ShakeState = remember { ShakeState() }

fun Modifier.shake(shakeState: ShakeState): Modifier =
    this.offset { IntOffset(shakeState.offsetX.value.roundToInt(), 0) }
@Composable
fun VerifyCodeTextField(
    value: String,
    onValueChange: (String) -> Unit,
    countdownSeconds: Int,
    isLoading: Boolean,
    hasSentCode: Boolean,
    canSendCode: Boolean,
    onGetCode: () -> Unit,
    focusRequester: FocusRequester? = null,
    isError: Boolean = false,
    shakeState: ShakeState? = null,
    leadingContent: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val borderColor = when {
        isError -> Color(0xFFFF4D4F)
        isFocused -> colorResource(R.color.login_color_blue)
        else -> colorResource(R.color.login_input_border)
    }

    val shakeModifier = if (shakeState != null) Modifier.shake(shakeState) else Modifier

    BasicTextField(
        value = value,
        onValueChange = { if (it.length <= 6) onValueChange(it) },
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .then(shakeModifier)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier),
        textStyle = TextStyle(color = colorResource(R.color.login_main_text), fontSize = 16.sp),
        singleLine = true,
        cursorBrush = SolidColor(colorResource(R.color.login_color_blue)),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
        interactionSource = interactionSource,
        decorationBox = { innerTextField ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .border(1.dp, borderColor, RoundedCornerShape(10.dp))
                    .background(Color.White, RoundedCornerShape(10.dp))
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (leadingContent != null) {
                    leadingContent()
                }
                Box(modifier = Modifier.weight(1f)) {
                    if (value.isEmpty()) {
                        Text(
                            text = stringResource(R.string.login_verify_code_hint),
                            color = colorResource(R.color.login_text_hint),
                            fontSize = 16.sp
                        )
                    }
                    innerTextField()
                }
                CountdownButton(
                    countdownSeconds = countdownSeconds,
                    canSend = canSendCode,
                    onClick = onGetCode
                )
            }
        }
    )
}