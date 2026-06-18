package com.tencent.rtcube.v2.login.debugauth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.tencent.rtcube.v2.login.R
import com.tencent.rtcube.v2.login.components.views.FullScreenLoading
import com.tencent.rtcube.v2.login.components.views.LoginButton
import com.tencent.rtcube.v2.login.components.views.LoginTextField
import com.tencent.rtcube.v2.login.components.views.LoginTopBackground
import com.tencent.rtcube.v2.login.debugauth.store.DebugAuthState

/**
 * Debug login screen (direct login with userId).
 */
@Composable
fun DebugAuthScreen(
    state: DebugAuthState,
    onUserIdChange: (String) -> Unit,
    onLogin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val userIdFocusRequester = remember { FocusRequester() }
    val canLogin = state.userId.isNotEmpty()

    LaunchedEffect(Unit) {
        userIdFocusRequester.requestFocus()
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(colorResource(R.color.login_color_white))
        ) {
            LoginTopBackground()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 24.dp)
            ) {
                LoginTextField(
                    value = state.userId,
                    onValueChange = onUserIdChange,
                    placeholder = stringResource(R.string.login_debug_hint),
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Done,
                    onImeAction = { if (canLogin) onLogin() },
                    focusRequester = userIdFocusRequester,
                    leadingContent = {
                        Icon(
                            painter = painterResource(id = R.drawable.login_ic_phone),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = Color.Unspecified
                        )
                    },
                )

                Spacer(modifier = Modifier.height(30.dp))

                LoginButton(
                    text = stringResource(R.string.login_menu_debug),
                    onClick = onLogin,
                    enabled = canLogin
                )
            }
        }

        if (state.isLoading) {
            FullScreenLoading(message = stringResource(R.string.login_status_logging_in))
        }
    }
}
