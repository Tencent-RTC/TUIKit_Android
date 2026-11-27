package io.trtc.tuikit.chat.login

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.trtc.tuikit.chat.uikit.components.widgets.Toast
import io.trtc.tuikit.chat.uikit.components.theme.LocalTheme
import io.trtc.tuikit.chat.R

private const val TAG = "LoginPrivacyDialog"

private const val URL_PRIVACY =
    "https://privacy.qq.com/document/preview/1cfe904fb7004b8ab1193a55857f7272"
private const val URL_PRIVACY_SUMMARY =
    "https://privacy.qq.com/document/preview/c63a48325d0e4a35b93f675205a65a77"
private const val URL_USER_AGREEMENT =
    "https://web.sdk.qcloud.com/document/Tencent-IM-User-Agreement.html"
private const val URL_SELF_INFO =
    "https://privacy.qq.com/document/preview/45ba982a1ce6493597a00f8c86b52a1e"
private const val URL_THIRD_SHARED =
    "https://privacy.qq.com/document/preview/dea84ac4bb88454794928b77126e9246"

private const val TAG_URL = "URL"

private const val DIALOG_WIDTH_RATIO = 0.8f

// Android enables the OpenType `chws` feature by default, which half-widths adjacent
// CJK punctuation. Being contextual, it survives measurement (runs split on
// MetricAffectingSpan only) but dies during drawing, where the link color spans split
// the runs too. Link-dense lines then measure narrower than they paint and spill past
// the dialog padding. Disabling it keeps both passes on the same metrics.
private const val NO_CJK_PUNCTUATION_SQUEEZE = "\"chws\" 0"

@Composable
internal fun LoginPrivacyDialog(
    onAgree: () -> Unit,
    onRefuse: () -> Unit
) {
    val colors = LocalTheme.current.colors
    val context = LocalContext.current

    Dialog(
        onDismissRequest = onRefuse,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(DIALOG_WIDTH_RATIO),
                shape = RoundedCornerShape(16.dp),
                color = colors.bgColorOperate
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    PrivacyContentText(onOpenUrl = { openWeb(context, it) })

                    Box(
                        modifier = Modifier
                            .padding(top = 24.dp)
                            .fillMaxWidth()
                            .height(44.dp)
                            .background(colors.buttonColorPrimaryDefault, RoundedCornerShape(14.dp))
                            .clickable(onClick = onAgree),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.compose_demo_login_privacy_agree),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.textColorButton,
                            textAlign = TextAlign.Center
                        )
                    }

                    Box(
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .fillMaxWidth()
                            .height(44.dp)
                            .clickable(onClick = onRefuse),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.compose_demo_login_privacy_refuse),
                            fontSize = 14.sp,
                            color = colors.textColorSecondary,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PrivacyContentText(onOpenUrl: (String) -> Unit) {
    val colors = LocalTheme.current.colors
    val fullText = stringResource(R.string.compose_demo_login_privacy_dialog_tips)
    val links = listOf(
        stringResource(R.string.compose_demo_login_user_agreement) to URL_USER_AGREEMENT,
        stringResource(R.string.compose_demo_login_privacy_policy_summary) to URL_PRIVACY_SUMMARY,
        stringResource(R.string.compose_demo_login_privacy_policy) to URL_PRIVACY,
        stringResource(R.string.compose_demo_login_self_information_collection) to URL_SELF_INFO,
        stringResource(R.string.compose_demo_login_third_part_shared) to URL_THIRD_SHARED
    )

    val annotatedText = remember(fullText, colors.textColorLink) {
        buildAnnotatedString {
            append(fullText)
            links.forEach { (phrase, url) ->
                val index = fullText.lastIndexOf(phrase)
                if (index >= 0) {
                    val end = index + phrase.length
                    addStringAnnotation(TAG_URL, url, index, end)
                    addStyle(SpanStyle(color = colors.textColorLink), index, end)
                }
            }
        }
    }

    var layoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
    Text(
        text = annotatedText,
        fontSize = 14.sp,
        lineHeight = 18.sp,
        color = colors.textColorPrimary,
        onTextLayout = { layoutResult = it },
        style = TextStyle(
            lineBreak = LineBreak.Paragraph,
            fontFeatureSettings = NO_CJK_PUNCTUATION_SQUEEZE
        ),
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(annotatedText) {
                detectTapGestures { position ->
                    val layout = layoutResult ?: return@detectTapGestures
                    val offset = layout.getOffsetForPosition(position)
                    annotatedText.getStringAnnotations(TAG_URL, offset, offset)
                        .firstOrNull()
                        ?.let { onOpenUrl(it.item) }
                }
            }
    )
}

private fun openWeb(context: Context, url: String) {
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    } catch (e: Exception) {
        Log.w(TAG, "openWeb failed: $url, ${e.message}")
        Toast.error(context, context.getString(R.string.compose_demo_open_browser_failed))
    }
}
