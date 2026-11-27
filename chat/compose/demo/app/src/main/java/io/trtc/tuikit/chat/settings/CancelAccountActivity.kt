package io.trtc.tuikit.chat.settings

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tencent.mmkv.MMKV
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.login.LoginStore
import io.trtc.tuikit.chat.BaseActivity
import io.trtc.tuikit.chat.R
import io.trtc.tuikit.chat.common.AppConstants
import io.trtc.tuikit.chat.login.AuthApi
import io.trtc.tuikit.chat.login.LoginActivity
import io.trtc.tuikit.chat.uikit.components.common.SetActivitySystemBarAppearance
import io.trtc.tuikit.chat.uikit.components.theme.LocalTheme
import io.trtc.tuikit.chat.uikit.components.widgets.AlertDialog
import io.trtc.tuikit.chat.uikit.components.widgets.Avatar
import io.trtc.tuikit.chat.uikit.components.widgets.AvatarSize
import io.trtc.tuikit.chat.uikit.components.widgets.Toast
import io.trtc.tuikit.chat.viewmodels.displayName

class CancelAccountActivity : BaseActivity() {

    companion object {
        fun start(context: Context) {
            context.startActivity(Intent(context, CancelAccountActivity::class.java))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SetActivitySystemBarAppearance()
            CancelAccountScreen(
                onBackClick = { finish() },
                onCancelAccountFinished = { onCancelAccountFinished() }
            )
        }
    }

    private fun onCancelAccountFinished() {
        if (isFinishing || isDestroyed) return
        MMKV.defaultMMKV().encode(AppConstants.KEY_LOGIN_USER, "")
        MMKV.defaultMMKV().encode(AppConstants.KEY_LOGIN_TOKEN, "")
        MMKV.defaultMMKV().encode(AppConstants.KEY_LOGIN_TYPE, "")
        Toast.success(this, getString(R.string.compose_demo_about_cancel_account_successful))
        val intent = Intent(this, LoginActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        startActivity(intent)
        finish()
    }
}

@Composable
private fun CancelAccountScreen(
    onBackClick: () -> Unit,
    onCancelAccountFinished: () -> Unit
) {
    val colors = LocalTheme.current.colors
    val context = LocalContext.current
    val userInfo by LoginStore.shared.loginState.loginUserInfo.collectAsState()
    var showConfirmDialog by remember { mutableStateOf(false) }

    val userId = userInfo?.userID.orEmpty()
    val errorTip = stringResource(R.string.compose_demo_about_cancel_account_error_msg)
    val avatarScale = 70f / 64f

    fun cancelAccount() {
        val account = MMKV.defaultMMKV().decodeString(AppConstants.KEY_LOGIN_USER, "").orEmpty()
            .ifEmpty { userId }
        val token = MMKV.defaultMMKV().decodeString(AppConstants.KEY_LOGIN_TOKEN, "").orEmpty()
        if (token.isEmpty()) {
            Toast.error(context, errorTip)
            return
        }
        AuthApi.deleteAccount(account, token, object : AuthApi.RequestCallback<Unit> {
            override fun onError(code: Int, message: String?) {
                Toast.error(context, "$code , $errorTip")
            }

            override fun onSuccess(result: Unit) {
                LoginStore.shared.logout(object : CompletionHandler {
                    override fun onSuccess() {
                        onCancelAccountFinished()
                    }

                    override fun onFailure(code: Int, desc: String) {
                        onCancelAccountFinished()
                    }
                })
            }
        })
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bgColorOperate)
    ) {
        DemoPageHeader(
            title = stringResource(R.string.compose_demo_about_cancel_account),
            onBackClick = onBackClick
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(80.dp))

            Box(modifier = Modifier.size(70.dp)) {
                Avatar(
                    url = userInfo?.avatarURL,
                    name = userInfo?.displayName ?: "",
                    size = AvatarSize.XL,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .graphicsLayer {
                            scaleX = avatarScale
                            scaleY = avatarScale
                        }
                )
                Image(
                    painter = painterResource(R.drawable.demo_ic_cancel_account_badge),
                    contentDescription = null,
                    modifier = Modifier
                        .size(25.dp)
                        .align(Alignment.BottomEnd)
                )
            }

            Text(
                text = stringResource(R.string.compose_demo_about_cancel_account_tip, userId),
                fontSize = 14.sp,
                color = colors.textColorSecondary,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp)
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 60.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.bgColorOperate)
                    .border(1.dp, colors.textColorError, RoundedCornerShape(8.dp))
                    .clickable { showConfirmDialog = true }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.compose_demo_about_cancel_account),
                    fontSize = 16.sp,
                    color = colors.textColorError
                )
            }
        }
    }

    AlertDialog(
        isVisible = showConfirmDialog,
        title = stringResource(R.string.compose_demo_about_cancel_account_alert_tip),
        message = null,
        cancelText = stringResource(R.string.compose_demo_about_cancel),
        confirmText = stringResource(R.string.compose_demo_about_confirm),
        isConfirmDestructive = true,
        onDismiss = { showConfirmDialog = false },
        onCancel = { showConfirmDialog = false },
        onConfirm = {
            showConfirmDialog = false
            cancelAccount()
        }
    )
}
