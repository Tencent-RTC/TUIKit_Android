package com.tencent.rtcube.v2.privacy.service

import android.app.Activity
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.tencent.rtcube.v2.R
import com.tencent.rtcube.v2.privacy.PrivacyEntry
import com.tencent.rtcube.v2.privacy.PrivacyPageType

private val PrimaryTextColor = Color(0xFF1A1A1A)
private val PrimaryActionColor = Color(0xFF006EFF)

internal object FirstLaunchPrivacyAlertDialog {
    fun show(activity: Activity, onAgree: () -> Unit): Boolean {
        if (activity.isFinishing || activity.isDestroyed) return false

        val rootView = activity.findViewById<ViewGroup>(android.R.id.content) ?: return false
        val composeView = ComposeView(activity).apply {
            (activity as? LifecycleOwner)?.let { setViewTreeLifecycleOwner(it) }
            (activity as? ViewModelStoreOwner)?.let { setViewTreeViewModelStoreOwner(it) }
            (activity as? SavedStateRegistryOwner)?.let { setViewTreeSavedStateRegistryOwner(it) }
        }
        rootView.addView(
            composeView,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )

        val dismiss = { rootView.removeView(composeView) }
        composeView.setContent {
            var visible by remember { mutableStateOf(true) }
            if (visible) {
                Dialog(
                    onDismissRequest = {},
                    properties = DialogProperties(
                        dismissOnBackPress = false,
                        dismissOnClickOutside = false,
                    ),
                ) {
                    DialogContent(
                        onAgree = {
                            onAgree()
                            visible = false
                            dismiss()
                        },
                        onDisagree = {
                            visible = false
                            dismiss()
                        },
                    )
                }
            }
        }
        return true
    }
}

@Composable
private fun DialogContent(
    onAgree: () -> Unit,
    onDisagree: () -> Unit,
) {
    val configuration = LocalConfiguration.current
    val cardHeight = (configuration.screenHeightDp / 2).dp
    val cardWidth = configuration.screenWidthDp.dp - 40.dp
    val appName = stringResource(R.string.privacy_app_name)

    Column(
        modifier = Modifier
            .width(cardWidth)
            .height(cardHeight)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White),
    ) {
        Text(
            text = stringResource(R.string.privacy_alert_welcome_title, appName),
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = PrimaryTextColor,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 15.dp),
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(10.dp))

        PrivacyRichText(
            appName = appName,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 15.dp),
        )

        Spacer(modifier = Modifier.height(10.dp))
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 20.dp),
            thickness = 0.5.dp,
            color = Color.LightGray,
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp),
        ) {
            DialogButton(
                text = stringResource(R.string.privacy_alert_disagree),
                textColor = PrimaryTextColor,
                background = Color.Transparent,
                onClick = onDisagree,
                modifier = Modifier.weight(1f),
            )
            DialogButton(
                text = stringResource(R.string.privacy_alert_agree_continue),
                textColor = Color.White,
                background = PrimaryActionColor,
                onClick = onAgree,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun DialogButton(
    text: String,
    textColor: Color,
    background: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(background)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, fontSize = 16.sp, color = textColor)
    }
}

private data class PrivacyLink(
    val text: String,
    val matchAll: Boolean,
    val onClick: () -> Unit,
)

private data class LinkRange(val start: Int, val end: Int, val onClick: () -> Unit)

@Composable
private fun PrivacyRichText(
    appName: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    val privacySummary = stringResource(R.string.privacy_policy_summary)
    val dataCollection = stringResource(R.string.privacy_data_collection_list)
    val thirdShare = stringResource(R.string.privacy_third_share)
    val privacyGuide = stringResource(R.string.privacy_agreement)
    val userAgreement = stringResource(R.string.privacy_user_agreement)

    val fullText = stringResource(
        R.string.privacy_alert_full_content,
        appName, privacySummary, dataCollection, thirdShare, privacyGuide,
    ) + stringResource(
        R.string.privacy_alert_content_agree,
        userAgreement, privacyGuide, dataCollection, thirdShare,
    )

    val (annotated, linkRanges) = remember(fullText) {
        val links = listOf(
            PrivacyLink(privacySummary, matchAll = false) {
                PrivacyEntry.pushPrivacyPage(PrivacyPageType.PrivacySummary, context)
            },
            PrivacyLink(dataCollection, matchAll = true) {
                PrivacyEntry.pushPrivacyPage(PrivacyPageType.DataCollection, context)
            },
            PrivacyLink(thirdShare, matchAll = true) {
                PrivacyEntry.pushPrivacyPage(PrivacyPageType.ThirdShare, context)
            },
            PrivacyLink(privacyGuide, matchAll = true) {
                PrivacyEntry.pushPrivacyPage(PrivacyPageType.Privacy, context)
            },
            PrivacyLink(userAgreement, matchAll = false) {
                PrivacyEntry.pushPrivacyPage(PrivacyPageType.Agreement, context)
            },
        )
        buildPrivacyAnnotatedText(fullText, links, PrimaryActionColor)
    }

    ClickableText(
        text = annotated,
        modifier = modifier.verticalScroll(scrollState),
        style = TextStyle(color = PrimaryTextColor, fontSize = 14.sp),
        onClick = { offset ->
            linkRanges.firstOrNull { offset in it.start until it.end }?.onClick?.invoke()
        },
    )
}

private fun buildPrivacyAnnotatedText(
    fullText: String,
    links: List<PrivacyLink>,
    linkColor: Color,
): Pair<AnnotatedString, List<LinkRange>> {
    val ranges = mutableListOf<LinkRange>()
    links.forEach { link ->
        if (link.text.isEmpty()) return@forEach
        val bracketed = "《${link.text}》"
        val pattern = if (fullText.contains(bracketed)) bracketed else link.text
        var fromIndex = 0
        while (true) {
            val idx = fullText.indexOf(pattern, fromIndex)
            if (idx < 0) break
            ranges += LinkRange(idx, idx + pattern.length, link.onClick)
            if (!link.matchAll) break
            fromIndex = idx + pattern.length
        }
    }
    val annotated = buildAnnotatedString {
        append(fullText)
        ranges.forEach { addStyle(SpanStyle(color = linkColor), it.start, it.end) }
    }
    return annotated to ranges
}
