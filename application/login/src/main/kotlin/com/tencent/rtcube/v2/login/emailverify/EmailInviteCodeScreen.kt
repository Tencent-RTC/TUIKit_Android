package com.tencent.rtcube.v2.login.emailverify

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tencent.rtcube.v2.login.R
import com.tencent.rtcube.v2.login.components.views.FullScreenLoading
import com.tencent.rtcube.v2.login.components.views.InviteCodeInputField
import com.tencent.rtcube.v2.login.components.views.MarketingAgreementView
import com.tencent.rtcube.v2.login.components.views.TermsAgreementView
import com.tencent.rtcube.v2.login.emailverify.store.EmailInviteCodeState

@Composable
fun EmailInviteCodeScreen(
    state: EmailInviteCodeState,
    onCodeChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onResend: () -> Unit,
    onBack: () -> Unit,
    onAgreeTermsChange: (Boolean) -> Unit,
    onMarketingChange: (Boolean) -> Unit,
    onDismissTermsTooltip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

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
            Spacer(modifier = Modifier.height(53.dp))

            Image(
                painter = painterResource(id = R.drawable.login_email_invite_back),
                contentDescription = stringResource(R.string.login_back),
                modifier = Modifier
                    .padding(start = 24.dp)
                    .size(width = 16.dp, height = 28.dp)
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) { onBack() }
            )

            Spacer(modifier = Modifier.height(12.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
            ) {
                Text(
                    text = stringResource(R.string.login_email_invite_code_title),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = colorResource(R.color.login_main_text)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = stringResource(R.string.login_email_invite_code_subtitle, state.email),
                    fontSize = 12.sp,
                    color = colorResource(R.color.login_text_secondary)
                )

                Spacer(modifier = Modifier.height(36.dp))

                InviteCodeInputField(
                    code = state.inviteCode,
                    isError = state.isErrorState,
                    onCodeChange = onCodeChange,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                if (state.isErrorState) {
                    Text(
                        text = state.toastMessage,
                        fontSize = 12.sp,
                        color = Color(0xFFFF4D4F)
                    )
                } else {
                    Text(
                        text = stringResource(R.string.login_email_invite_code_spam_hint),
                        fontSize = 12.sp,
                        color = colorResource(R.color.login_text_hint)
                    )
                    Spacer(modifier = Modifier.height(4.dp))

                    if (state.countdownSeconds > 0) {
                        Text(
                            text = stringResource(R.string.login_email_invite_code_resend_hint, state.countdownSeconds),
                            fontSize = 12.sp,
                            color = colorResource(R.color.login_text_hint)
                        )
                    } else {
                        val resendAnnotated = buildAnnotatedString {
                            withStyle(SpanStyle(color = colorResource(R.color.login_color_blue), fontSize = 12.sp)) {
                                append(stringResource(R.string.login_email_invite_code_resend))
                            }
                        }
                        Text(
                            text = resendAnnotated,
                            modifier = Modifier.clickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() }
                            ) { onResend() }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                val isCodeComplete = state.inviteCode.length == 6
                Box {
                    Button(
                        onClick = onSubmit,
                        enabled = isCodeComplete && !state.isErrorState && !state.isVerifying,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp),
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF1C66E5),
                            disabledContainerColor = Color(0xFFCCE2FF),
                            contentColor = Color.White,
                            disabledContentColor = Color.White
                        ),
                        elevation = androidx.compose.material3.ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
                    ) {
                        Text(
                            text = if (state.isVerifying) {
                                stringResource(R.string.login_email_invite_code_verifying)
                            } else {
                                stringResource(R.string.login_email_invite_code_get_started)
                            },
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (state.showTermsTooltip) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .offset(y = (-44).dp)
                                .background(Color(0xFF333333), RoundedCornerShape(6.dp))
                                .clickable(
                                    indication = null,
                                    interactionSource = remember { MutableInteractionSource() }
                                ) { onDismissTermsTooltip() }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.login_email_invite_code_terms_tooltip),
                                fontSize = 12.sp,
                                color = Color.White
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                TermsAgreementView(
                    checked = state.isAgreeTerms,
                    onCheckedChange = onAgreeTermsChange
                )

                Spacer(modifier = Modifier.height(4.dp))

                MarketingAgreementView(
                    checked = state.isMarketing,
                    onCheckedChange = onMarketingChange
                )
            }
        }

        if (state.isFullScreenLoading) {
            FullScreenLoading(message = state.fullScreenLoadingMessage)
        }
    }
}
