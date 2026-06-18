package com.tencent.rtcube.v2.login.phoneverify

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tencent.rtcube.v2.login.AgreementNavigator
import com.tencent.rtcube.v2.login.LoginEntry
import com.tencent.rtcube.v2.login.R
import com.tencent.rtcube.v2.login.components.views.FullScreenLoading
import com.tencent.rtcube.v2.login.components.views.IOALoginButton
import com.tencent.rtcube.v2.login.components.views.LoginButton
import com.tencent.rtcube.v2.login.components.views.LoginTextField
import com.tencent.rtcube.v2.login.components.views.LoginTopBackground
import com.tencent.rtcube.v2.login.components.views.OtherLoginMethodsDivider
import com.tencent.rtcube.v2.login.components.views.PrivacyAgreementView
import com.tencent.rtcube.v2.login.components.views.PrivacyBottomDialog
import com.tencent.rtcube.v2.login.components.views.VerifyCodeTextField
import com.tencent.rtcube.v2.login.components.views.rememberShakeState
import com.tencent.rtcube.v2.login.phoneverify.store.PhoneVerifyState
import kotlinx.coroutines.launch

@Composable
fun PhoneVerifyScreen(
    state: PhoneVerifyState,
    onPhoneChange: (String) -> Unit,
    onCodeChange: (String) -> Unit,
    onGetCode: () -> Unit,
    onLogin: () -> Unit,
    onIOALogin: () -> Unit,
    onTriggerHiddenConfig: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current

    val phoneFocusRequester = remember { FocusRequester() }
    val codeFocusRequester = remember { FocusRequester() }

    val privacyAccepted by LoginEntry.privacyAgreementAcceptedFlow.collectAsState()
    var privacyChecked by remember { mutableStateOf(privacyAccepted) }
    LaunchedEffect(privacyAccepted) {
        if (privacyAccepted) privacyChecked = true
    }
    var showPrivacyDialog by remember { mutableStateOf(false) }
    var pendingPrivacyAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val privacyShakeState = rememberShakeState()
    val codeShakeState = rememberShakeState()

    val isPhoneValid = state.phoneNumber.isNotEmpty()
    val isCodeReady = state.verifyCode.isNotEmpty() && state.verifyCode.length == 6
    val canLogin = isPhoneValid && isCodeReady
    val canSendCode = isPhoneValid && !state.isLoading && state.countdownSeconds == 0

    LaunchedEffect(state.toastMessage) {
        if (state.toastMessage.isNotEmpty()) {
            Toast.makeText(context, state.toastMessage, Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(state.focusCodeInputEvent) {
        if (state.focusCodeInputEvent > 0) {
            codeFocusRequester.requestFocus()
        }
    }

    LaunchedEffect(state.codeErrorEvent) {
        if (state.codeErrorEvent > 0) {
            scope.launch { codeShakeState.shake() }
        }
    }

    LaunchedEffect(Unit) {
        phoneFocusRequester.requestFocus()
    }

    fun checkPrivacyWithDialog(action: () -> Unit) {
        if (!privacyChecked) {
            pendingPrivacyAction = action
            showPrivacyDialog = true
        } else {
            action()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(colorResource(R.color.login_color_white))
        ) {
            LoginTopBackground(onLogoHiddenTrigger = onTriggerHiddenConfig)

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 20.dp)
            ) {
                Spacer(modifier = Modifier.height(30.dp))

                PhoneInputField(
                    phone = state.phoneNumber,
                    regionCode = state.regionCode,
                    onPhoneChange = onPhoneChange,
                    focusRequester = phoneFocusRequester,
                    onNext = { codeFocusRequester.requestFocus() }
                )

                Spacer(modifier = Modifier.height(20.dp))

                VerifyCodeTextField(
                    value = state.verifyCode,
                    onValueChange = onCodeChange,
                    countdownSeconds = state.countdownSeconds,
                    isLoading = state.isLoading,
                    hasSentCode = state.sessionId.isNotEmpty(),
                    canSendCode = canSendCode,
                    onGetCode = {
                        keyboardController?.hide()
                        checkPrivacyWithDialog(onGetCode)
                    },
                    isError = state.isCodeInputError,
                    focusRequester = codeFocusRequester,
                    shakeState = codeShakeState,
                    leadingContent = {
                        Icon(
                            painter = painterResource(id = R.drawable.login_ic_safe),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = androidx.compose.ui.graphics.Color.Unspecified
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                )

                Spacer(modifier = Modifier.height(20.dp))

                PrivacyAgreementView(
                    checked = privacyChecked,
                    onCheckedChange = { privacyChecked = it },
                    onUserAgreementClick = { AgreementNavigator.openUserAgreement(context) },
                    onPrivacySummaryClick = { AgreementNavigator.openPrivacySummary(context) },
                    onPrivacyProtectClick = { AgreementNavigator.openPrivacyPolicy(context) },
                    shakeState = privacyShakeState
                )

                Spacer(modifier = Modifier.height(30.dp))

                LoginButton(
                    text = stringResource(R.string.login_btn_login),
                    onClick = { checkPrivacyWithDialog(onLogin) },
                    enabled = canLogin
                )

                Spacer(modifier = Modifier.weight(1f))

                OtherLoginMethodsDivider()

                Spacer(modifier = Modifier.height(16.dp))

                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    IOALoginButton(onClick = { checkPrivacyWithDialog { onIOALogin() } })
                }

                Spacer(modifier = Modifier.height(20.dp))
            }
        }

        if (state.isFullScreenLoading) {
            FullScreenLoading(message = state.fullScreenLoadingMessage)
        }

        if (showPrivacyDialog) {
            PrivacyBottomDialog(
                onAgree = {
                    privacyChecked = true
                    showPrivacyDialog = false
                    pendingPrivacyAction?.invoke()
                    pendingPrivacyAction = null
                },
                onDismiss = {
                    showPrivacyDialog = false
                    pendingPrivacyAction = null
                }
            )
        }
    }
}

@Composable
private fun PhoneInputField(
    phone: String,
    regionCode: String,
    onPhoneChange: (String) -> Unit,
    focusRequester: FocusRequester,
    onNext: () -> Unit,
) {
    LoginTextField(
        value = phone,
        onValueChange = { newValue ->
            onPhoneChange(newValue.filter { it.isDigit() }.take(11))
        },
        placeholder = stringResource(R.string.login_phone_hint),
        keyboardType = KeyboardType.Number,
        imeAction = ImeAction.Next,
        onImeAction = onNext,
        maxLength = 11,
        focusRequester = focusRequester,
        leadingContent = {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.login_ic_phone),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = Color.Unspecified
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "+$regionCode",
                    color = colorResource(R.color.login_main_text),
                    fontSize = 16.sp
                )
                Spacer(modifier = Modifier.width(12.dp))
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(20.dp)
                        .background(Color(0xFFE4E8EE))
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
        },
        trailingContent = if (phone.isNotEmpty()) {
            {
                Icon(
                    painter = painterResource(id = android.R.drawable.ic_menu_close_clear_cancel),
                    contentDescription = stringResource(R.string.login_clear_phone_input),
                    modifier = Modifier
                        .size(18.dp)
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) { onPhoneChange("") },
                    tint = colorResource(R.color.login_text_hint)
                )
            }
        } else null
    )
}
