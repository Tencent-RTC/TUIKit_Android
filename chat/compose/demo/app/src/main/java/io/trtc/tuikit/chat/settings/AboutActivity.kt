package io.trtc.tuikit.chat.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.tencent.imsdk.v2.V2TIMManager
import com.tencent.mmkv.MMKV
import io.trtc.tuikit.chat.BaseActivity
import io.trtc.tuikit.chat.R
import io.trtc.tuikit.chat.common.AppConstants
import io.trtc.tuikit.chat.uikit.components.common.SetActivitySystemBarAppearance
import io.trtc.tuikit.chat.uikit.components.theme.LocalTheme

private const val URL_PRIVACY =
    "https://privacy.qq.com/document/preview/1cfe904fb7004b8ab1193a55857f7272"
private const val URL_USER_AGREEMENT =
    "https://web.sdk.qcloud.com/document/Tencent-IM-User-Agreement.html"
private const val URL_SELF_INFORMATION_COLLECTION =
    "https://privacy.qq.com/document/preview/45ba982a1ce6493597a00f8c86b52a1e"
private const val URL_THIRD_SHARED =
    "https://privacy.qq.com/document/preview/dea84ac4bb88454794928b77126e9246"
private const val URL_CONTACT_US = "https://cloud.tencent.com/product/im"
private const val URL_ICP_SEARCH = "https://beian.miit.gov.cn"

class AboutActivity : BaseActivity() {

    companion object {
        fun start(context: Context) {
            context.startActivity(Intent(context, AboutActivity::class.java))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SetActivitySystemBarAppearance()
            AboutScreen(
                isLocalUserSigLogin = isLocalUserSigLogin(),
                onBackClick = { finish() }
            )
        }
    }

    private fun isLocalUserSigLogin(): Boolean {
        val loginType = MMKV.defaultMMKV()
            .decodeString(AppConstants.KEY_LOGIN_TYPE, "").orEmpty()
        return loginType == AppConstants.LOGIN_TYPE_LOCAL
    }
}

@Composable
private fun AboutScreen(
    isLocalUserSigLogin: Boolean,
    onBackClick: () -> Unit
) {
    val colors = LocalTheme.current.colors
    val context = LocalContext.current
    var showStatementDialog by remember { mutableStateOf(false) }

    fun openWeb(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.w("AboutActivity", "openWeb failed: $url, ${e.message}")
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bgColorTopBar)
    ) {
        DemoPageHeader(
            title = stringResource(R.string.compose_demo_about_im),
            onBackClick = onBackClick
        )

        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(colors.bgColorTopBar)
                .navigationBarsPadding()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = maxHeight)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Spacer(modifier = Modifier.height(10.dp))

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(colors.bgColorOperate)
                    ) {
                        AboutEntryItem(
                            title = stringResource(R.string.compose_demo_about_sdk_version),
                            value = V2TIMManager.getInstance().version.orEmpty(),
                            showArrow = false
                        )
                        AboutEntryItem(
                            title = stringResource(R.string.compose_demo_about_privacy),
                            onClick = { openWeb(URL_PRIVACY) }
                        )
                        AboutEntryItem(
                            title = stringResource(R.string.compose_demo_about_user_agreement),
                            onClick = { openWeb(URL_USER_AGREEMENT) }
                        )
                        AboutEntryItem(
                            title = stringResource(R.string.compose_demo_about_statement),
                            onClick = { showStatementDialog = true }
                        )
                        AboutEntryItem(
                            title = stringResource(R.string.compose_demo_about_self_information_collection),
                            onClick = { openWeb(URL_SELF_INFORMATION_COLLECTION) }
                        )
                        AboutEntryItem(
                            title = stringResource(R.string.compose_demo_about_third_part_shared),
                            onClick = { openWeb(URL_THIRD_SHARED) }
                        )
                        if (!isLocalUserSigLogin) {
                            AboutEntryItem(
                                title = stringResource(R.string.compose_demo_about_cancel_account),
                                onClick = { CancelAccountActivity.start(context) }
                            )
                        }
                        AboutEntryItem(
                            title = stringResource(R.string.compose_demo_about_contact_us),
                            showDivider = false,
                            onClick = { openWeb(URL_CONTACT_US) }
                        )
                    }

                    Spacer(modifier = Modifier.height(40.dp))
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 30.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(R.string.compose_demo_about_icp_number),
                        fontSize = 11.sp,
                        color = colors.textColorTertiary,
                        modifier = Modifier
                            .clickable { openWeb(URL_ICP_SEARCH) }
                            .padding(vertical = 3.dp)
                    )
                    Text(
                        text = stringResource(R.string.compose_demo_about_company),
                        fontSize = 11.sp,
                        color = colors.textColorTertiary,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                    Text(
                        text = stringResource(R.string.compose_demo_about_copyright),
                        fontSize = 11.sp,
                        color = colors.textColorTertiary,
                        modifier = Modifier.padding(top = 3.dp)
                    )
                }
            }
        }
    }

    StatementDialog(
        isVisible = showStatementDialog,
        title = stringResource(R.string.compose_demo_about_statement),
        message = stringResource(R.string.compose_demo_about_statement_content),
        confirmText = stringResource(R.string.compose_demo_about_confirm),
        onDismiss = { showStatementDialog = false }
    )
}

@Composable
private fun AboutEntryItem(
    title: String,
    value: String = "",
    showArrow: Boolean = true,
    showDivider: Boolean = true,
    onClick: (() -> Unit)? = null
) {
    val colors = LocalTheme.current.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.bgColorOperate)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .then(
                    if (onClick != null) {
                        Modifier.clickable { onClick() }
                    } else {
                        Modifier
                    }
                )
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                fontSize = 16.sp,
                color = colors.textColorSecondary,
                maxLines = 1
            )
            Text(
                text = value,
                fontSize = 16.sp,
                color = colors.textColorPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.End,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 16.dp, end = 8.dp)
            )
            if (showArrow) {
                Icon(
                    painter = painterResource(id = R.drawable.app_navigation_right_icon),
                    contentDescription = null,
                    tint = colors.textColorTertiary,
                    modifier = Modifier
                        .width(7.dp)
                        .height(12.dp)
                )
            }
        }
        if (showDivider) {
            HorizontalDivider(
                thickness = 0.5.dp,
                color = colors.strokeColorPrimary
            )
        }
    }
}

@Composable
private fun StatementDialog(
    isVisible: Boolean,
    title: String,
    message: String,
    confirmText: String,
    onDismiss: () -> Unit
) {
    val colors = LocalTheme.current.colors
    if (!isVisible) {
        return
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(indication = null, interactionSource = null) { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .width(327.dp)
                    .heightIn(min = 134.dp, max = 480.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(color = colors.bgColorDialog)
                    .clickable(indication = null, interactionSource = null) {},
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .fillMaxWidth()
                        .heightIn(min = 88.dp, max = 424.dp)
                        .padding(start = 24.dp, end = 24.dp, top = 32.dp, bottom = 20.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = title,
                        style = TextStyle(
                            fontSize = 18.sp,
                            fontWeight = FontWeight.W600,
                            color = colors.textColorPrimary,
                            lineHeight = 26.sp
                        ),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = message,
                        style = TextStyle(
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Normal,
                            color = colors.textColorPrimary,
                            lineHeight = 24.sp
                        ),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clickable { onDismiss() },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    HorizontalDivider(
                        thickness = 0.5.dp,
                        color = colors.strokeColorModule
                    )
                    Text(
                        text = confirmText,
                        style = TextStyle(
                            fontSize = 16.sp,
                            fontWeight = FontWeight.W500,
                            color = colors.textColorLink,
                            lineHeight = 24.sp
                        ),
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(vertical = 16.dp, horizontal = 20.dp)
                    )
                }
            }
        }
    }
}
