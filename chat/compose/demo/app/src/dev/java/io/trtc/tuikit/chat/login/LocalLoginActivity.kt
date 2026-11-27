package io.trtc.tuikit.chat.login

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tencent.mmkv.MMKV
import io.trtc.tuikit.chat.uikit.components.theme.LocalTheme
import io.trtc.tuikit.chat.BaseActivity
import io.trtc.tuikit.chat.R
import io.trtc.tuikit.chat.common.AppConstants
import io.trtc.tuikit.chat.signature.GenerateTestUserSig

class LocalLoginActivity : BaseActivity() {

    override val requiresLogin: Boolean = false

    private var userId by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val loginUser = MMKV.defaultMMKV().decodeString(AppConstants.KEY_LOGIN_USER, "")
        val loginType = MMKV.defaultMMKV().decodeString(AppConstants.KEY_LOGIN_TYPE, "")
        if (!loginUser.isNullOrEmpty() && loginType == AppConstants.LOGIN_TYPE_LOCAL) {
            login(loginUser)
            return
        }

        userId = MMKV.defaultMMKV().decodeString(AppConstants.KEY_LAST_LOCAL_USER_ID, "").orEmpty()

        setContent {
            LocalLoginScreen()
        }
    }

    @Composable
    private fun LocalLoginScreen() {
        val colors = LocalTheme.current.colors

        SetLoginSystemBarAppearance()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.bgColorOperate)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(Color(0xFF243047), Color(0xFF4086FF)),
                                start = Offset.Zero,
                                end = Offset.Infinite
                            )
                        )
                        .background(
                            Brush.linearGradient(
                                colors = listOf(Color(0x1AFFFFFF), Color(0x0AFFFFFF)),
                                start = Offset.Zero,
                                end = Offset.Infinite
                            )
                        )
                )
                Image(
                    painter = painterResource(R.drawable.demo_ic_login_logo_watermark),
                    contentDescription = null,
                    // painterResource ignores the vector root's android:alpha, so the
                    // watermark's 0.08 opacity from the View demo is applied explicitly.
                    alpha = 0.08f,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .offset(x = (-60).dp)
                )
                LoginTopSwitchers(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .statusBarsPadding()
                        .padding(top = 8.dp, end = 8.dp)
                )
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 24.dp, bottom = 28.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        painter = painterResource(R.drawable.demo_ic_login_logo),
                        contentDescription = stringResource(R.string.app_name),
                        modifier = Modifier
                            .width(80.dp)
                            .height(40.dp)
                    )
                    Column(modifier = Modifier.padding(start = 12.dp)) {
                        Text(
                            text = stringResource(R.string.compose_demo_login_hero_title),
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = stringResource(R.string.compose_demo_login_hero_subtitle),
                            fontSize = 13.sp,
                            color = Color(0xCCFFFFFF),
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1.6f)
                    .navigationBarsPadding()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 36.dp, start = 24.dp, end = 24.dp)
                ) {
                    Text(
                        text = stringResource(R.string.compose_demo_login_user_id_label),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textColorPrimary
                    )

                    BasicTextField(
                        value = userId,
                        onValueChange = { userId = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp)
                            .height(56.dp),
                        textStyle = TextStyle(
                            fontSize = 16.sp,
                            color = colors.textColorPrimary
                        ),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                if (userId.isNotBlank()) {
                                    login(userId.trim())
                                }
                            }
                        ),
                        cursorBrush = SolidColor(colors.textColorLink),
                        decorationBox = { innerTextField ->
                            Box(
                                modifier = Modifier.padding(horizontal = 2.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                if (userId.isEmpty()) {
                                    Text(
                                        text = stringResource(R.string.compose_demo_input_user_id_tips),
                                        fontSize = 16.sp,
                                        color = colors.textColorTertiary
                                    )
                                }
                                innerTextField()
                            }
                        }
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(colors.strokeColorPrimary)
                    )

                    PrimaryButton(
                        text = stringResource(R.string.compose_demo_login),
                        enabled = userId.isNotBlank(),
                        onClick = { login(userId.trim()) },
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .padding(top = 48.dp)
                            .fillMaxWidth()
                            .height(54.dp)
                    )
                }
            }
        }
    }

    private fun login(userID: String) {
        val sdkAppId = GenerateTestUserSig.SDKAPPID
        val userSig = GenerateTestUserSig.genTestUserSig(userID)
        performLogin(
            this,
            sdkAppId, userID, userSig, AppConstants.LOGIN_TYPE_LOCAL,
            onSuccess = {
                MMKV.defaultMMKV().encode(AppConstants.KEY_LAST_LOCAL_USER_ID, userID)
            }
        )
    }
}
