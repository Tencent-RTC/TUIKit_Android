package com.tencent.rtcube.v2.login.emailverify

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tencent.rtcube.v2.login.R
import com.tencent.rtcube.v2.login.components.views.FullScreenLoading
import com.tencent.rtcube.v2.login.emailverify.store.EmailVerifyState

private val EMAIL_REGEX = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")
fun isValidEmail(email: String): Boolean = email.matches(EMAIL_REGEX)

@Composable
fun EmailVerifyScreen(
    state: EmailVerifyState,
    onEmailChange: (String) -> Unit,
    onContinue: () -> Unit,
    onEnterInviteCode: () -> Unit,
    onIOALogin: () -> Unit,
    onTriggerHiddenConfig: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val isEmailValid = isValidEmail(state.email)
    val context = LocalContext.current

    val hiddenClickCount = remember { androidx.compose.runtime.mutableIntStateOf(0) }
    val hiddenLastClick = remember { androidx.compose.runtime.mutableLongStateOf(0L) }

    LaunchedEffect(state.toastMessage) {
        if (state.toastMessage.isNotEmpty()) {
            Toast.makeText(context, state.toastMessage, Toast.LENGTH_SHORT).show()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Image(
            painter = painterResource(id = R.drawable.login_email_welcome_bg),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(80.dp))

            Image(
                painter = painterResource(id = R.drawable.login_email_welcome_logo),
                contentDescription = null,
                modifier = Modifier
                    .padding(start = 24.dp)
                    .size(40.dp)
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) {
                        val now = System.currentTimeMillis()
                        hiddenClickCount.intValue =
                            if (now - hiddenLastClick.longValue > 2000L) 1 else hiddenClickCount.intValue + 1
                        hiddenLastClick.longValue = now
                        if (hiddenClickCount.intValue >= 5) {
                            hiddenClickCount.intValue = 0
                            onTriggerHiddenConfig?.invoke()
                        }
                    }
            )

            Spacer(modifier = Modifier.height(12.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
            ) {
                Text(
                    text = stringResource(R.string.login_email_welcome_title),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = colorResource(R.color.login_main_text)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = stringResource(R.string.login_email_welcome_subtitle),
                    fontSize = 14.sp,
                    color = colorResource(R.color.login_text_secondary)
                )

                Spacer(modifier = Modifier.height(24.dp))

                BasicTextField(
                    value = state.email,
                    onValueChange = onEmailChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    textStyle = TextStyle(
                        color = colorResource(R.color.login_main_text),
                        fontSize = 14.sp
                    ),
                    singleLine = true,
                    cursorBrush = SolidColor(colorResource(R.color.login_color_blue)),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { if (isEmailValid) onContinue() }
                    ),
                    decorationBox = { innerTextField ->
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                if (state.email.isEmpty()) {
                                    Text(
                                        text = stringResource(R.string.login_email_welcome_hint),
                                        color = colorResource(R.color.login_text_hint),
                                        fontSize = 14.sp
                                    )
                                }
                                innerTextField()
                            }
                        }
                    }
                )

                HorizontalDivider(
                    color = colorResource(R.color.login_text_hint),
                    thickness = 1.dp
                )

                Spacer(modifier = Modifier.height(64.dp))

                Button(
                    onClick = onContinue,
                    enabled = isEmailValid && !state.isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF1C66E5),
                        disabledContainerColor = Color(0xFFCCE2FF),
                        contentColor = Color.White,
                        disabledContentColor = Color.White
                    )
                ) {
                    Text(
                        text = stringResource(R.string.login_email_welcome_continue),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                val hasCodeText = stringResource(R.string.login_email_welcome_has_invite_code)
                val enterCodeText = stringResource(R.string.login_email_welcome_enter_code)
                val inviteAnnotated = buildAnnotatedString {
                    withStyle(SpanStyle(color = colorResource(R.color.login_text_secondary), fontSize = 12.sp)) {
                        append(hasCodeText)
                        append(" ")
                    }
                    withStyle(SpanStyle(color = colorResource(R.color.login_color_blue), fontSize = 12.sp)) {
                        append(enterCodeText)
                    }
                }
                Text(
                    text = inviteAnnotated,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) { onEnterInviteCode() }
                )

                Spacer(modifier = Modifier.height(8.dp))

                if (state.showIoaLogin) {
                    val ioaLinkText = stringResource(R.string.login_other_methods_divider)
                    val ssoFullText = stringResource(R.string.login_email_welcome_sso_text, ioaLinkText)
                    val linkStart = ssoFullText.indexOf(ioaLinkText)
                    val linkEnd = linkStart + ioaLinkText.length
                    val ssoAnnotated = buildAnnotatedString {
                        withStyle(SpanStyle(color = colorResource(R.color.login_text_secondary), fontSize = 12.sp)) {
                            append(ssoFullText.substring(0, linkStart))
                        }
                        pushStringAnnotation(tag = "IOA", annotation = "ioa_login")
                        withStyle(SpanStyle(color = colorResource(R.color.login_color_blue), fontSize = 12.sp)) {
                            append(ioaLinkText)
                        }
                        pop()
                        if (linkEnd < ssoFullText.length) {
                            withStyle(SpanStyle(color = colorResource(R.color.login_text_secondary), fontSize = 12.sp)) {
                                append(ssoFullText.substring(linkEnd))
                            }
                        }
                    }
                    ClickableText(
                        text = ssoAnnotated,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { offset ->
                            ssoAnnotated.getStringAnnotations(tag = "IOA", start = offset, end = offset)
                                .firstOrNull()?.let { onIOALogin() }
                        }
                    )
                }
            }
        }

        if (state.isLoading) {
            FullScreenLoading()
        }
    }
}
