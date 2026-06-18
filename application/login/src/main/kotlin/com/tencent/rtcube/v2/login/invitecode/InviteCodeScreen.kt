package com.tencent.rtcube.v2.login.invitecode

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tencent.rtcube.v2.login.R
import com.tencent.rtcube.v2.login.components.views.FullScreenLoading
import com.tencent.rtcube.v2.login.components.views.InviteCodeInputField
import com.tencent.rtcube.v2.login.components.views.TermsAgreementView
import com.tencent.rtcube.v2.login.invitecode.store.InviteCodeState

@Composable
fun InviteCodeScreen(
    state: InviteCodeState,
    onCodeChange: (String) -> Unit,
    onLogin: () -> Unit,
    onAgreeTermsChange: (Boolean) -> Unit,
    onDismissTermsTooltip: () -> Unit,
    onBack: () -> Unit = {},
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
            painter = painterResource(id = R.drawable.login_ic_full_screen_background),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
            ) {
                Spacer(modifier = Modifier.height(53.dp))

                Image(
                    painter = painterResource(id = R.drawable.login_title_bar_back),
                    contentDescription = null,
                    modifier = Modifier
                        .size(width = 16.dp, height = 28.dp)
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) { onBack() }
                )

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = stringResource(R.string.login_invite_title),
                    color = colorResource(R.color.login_main_text),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = stringResource(R.string.login_invite_subtitle),
                    color = colorResource(R.color.login_text_secondary),
                    fontSize = 12.sp
                )

                Spacer(modifier = Modifier.height(24.dp))

                InviteCodeInputField(
                    code = state.inviteCode,
                    isError = state.isErrorState,
                    onCodeChange = onCodeChange,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                if (state.isErrorState) {
                    Text(
                        text = stringResource(R.string.login_invite_error),
                        fontSize = 12.sp,
                        color = colorResource(R.color.login_color_red_light6)
                    )
                }

                Spacer(modifier = Modifier.height(72.dp))

                val isCodeComplete = state.inviteCode.length == 6
                Box {
                    Button(
                        onClick = onLogin,
                        enabled = isCodeComplete && !state.isErrorState && !state.isVerifying,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp),
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colorResource(R.color.login_color_theme_light6),
                            disabledContainerColor = colorResource(R.color.login_color_theme_light2),
                            contentColor = Color.White,
                            disabledContentColor = Color.White
                        ),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
                    ) {
                        Text(
                            text = if (state.isVerifying) {
                                stringResource(R.string.login_invite_verifying)
                            } else {
                                stringResource(R.string.login_invite_btn)
                            },
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (state.showTermsTooltip) {
                        Column(
                            modifier = Modifier
                                .offset(y = (-52).dp)
                                .clickable(
                                    indication = null,
                                    interactionSource = remember { MutableInteractionSource() }
                                ) { onDismissTermsTooltip() }
                        ) {
                            Text(
                                text = stringResource(R.string.login_invite_terms_tooltip),
                                fontSize = 14.sp,
                                color = Color.White,
                                modifier = Modifier
                                    .background(
                                        colorResource(R.color.login_color_g2),
                                        RoundedCornerShape(6.dp)
                                    )
                                    .padding(horizontal = 12.dp, vertical = 4.dp)
                            )
                            Image(
                                painter = painterResource(id = R.drawable.login_bg_terms_tooltip_arrow),
                                contentDescription = null,
                                modifier = Modifier
                                    .padding(start = 12.dp)
                                    .size(width = 10.dp, height = 6.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                TermsAgreementView(
                    checked = state.isAgreeTerms,
                    onCheckedChange = onAgreeTermsChange,
                )

                Spacer(modifier = Modifier.height(24.dp))
            }
        }

        if (state.isLoading) {
            FullScreenLoading(message = stringResource(R.string.login_status_logging_in))
        }
    }
}
