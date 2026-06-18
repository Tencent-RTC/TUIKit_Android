package com.tencent.rtcube.v2.login.components.views

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalMinimumInteractiveComponentEnforcement
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tencent.rtcube.v2.login.AgreementNavigator
import com.tencent.rtcube.v2.login.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TermsAgreementView(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    val termsOfService = stringResource(R.string.login_terms_of_service)
    val privacyPolicy = stringResource(R.string.login_terms_privacy_policy)
    val fullText = stringResource(R.string.login_invite_terms_agreement, termsOfService, privacyPolicy)

    val termsStart = fullText.indexOf(termsOfService)
    val termsEnd = termsStart + termsOfService.length
    val ppStart = fullText.indexOf(privacyPolicy, startIndex = termsEnd)
    val ppEnd = ppStart + privacyPolicy.length

    val linkColor = colorResource(R.color.login_color_blue)
    val annotatedText = buildAnnotatedString {
        append(fullText)
        addStyle(SpanStyle(color = linkColor), termsStart, termsEnd)
        addStyle(SpanStyle(color = linkColor), ppStart, ppEnd)
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompositionLocalProvider(LocalMinimumInteractiveComponentEnforcement provides false) {
            Checkbox(
                checked = checked,
                onCheckedChange = onCheckedChange,
                modifier = Modifier.size(20.dp),
                colors = CheckboxDefaults.colors(
                    checkedColor = Color(0xFF1C66E5),
                    uncheckedColor = Color(0xFFD9D9D9)
                )
            )
        }

        ClickableText(
            text = annotatedText,
            style = TextStyle(
                fontSize = 12.sp,
                color = colorResource(R.color.login_text_secondary)
            ),
            modifier = Modifier.padding(start = 4.dp),
            onClick = { offset ->
                when {
                    offset in termsStart until termsEnd -> AgreementNavigator.openTermsOfService(context)
                    offset in ppStart until ppEnd -> AgreementNavigator.openPrivacyPolicy(context)
                }
            }
        )
    }
}

@Composable
fun PrivacyAgreementView(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onUserAgreementClick: () -> Unit,
    onPrivacySummaryClick: () -> Unit,
    onPrivacyProtectClick: () -> Unit,
    shakeState: ShakeState? = null,
    modifier: Modifier = Modifier,
) {
    val shakeModifier = if (shakeState != null) Modifier.shake(shakeState) else Modifier
    var textLineCount by remember { mutableStateOf(1) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(shakeModifier),
        verticalAlignment = if (textLineCount > 1) Alignment.Top else Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(
                id = if (checked) R.drawable.login_ic_cb_select else R.drawable.login_ic_cb_normal
            ),
            contentDescription = null,
            modifier = Modifier
                .padding(end = 5.dp)
                .size(16.dp)
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { onCheckedChange(!checked) }
        )

        val privacySummary = stringResource(R.string.login_privacy_summary_link)
        val privacyProtect = stringResource(R.string.login_privacy_protection_guide)
        val userAgreement = stringResource(R.string.login_privacy_user_agreement)
        val fullText = stringResource(
            R.string.login_privacy_read_agreement,
            privacySummary,
            privacyProtect,
            userAgreement,
        )

        val psStart = fullText.indexOf(privacySummary)
        val psEnd = psStart + privacySummary.length
        val ppStart = fullText.indexOf(privacyProtect, startIndex = psEnd)
        val ppEnd = ppStart + privacyProtect.length
        val uaStart = fullText.indexOf(userAgreement, startIndex = ppEnd)
        val uaEnd = uaStart + userAgreement.length

        val linkColor = colorResource(R.color.login_color_blue)
        val annotatedText = buildAnnotatedString {
            append(fullText)
            addStyle(SpanStyle(color = linkColor), psStart, psEnd)
            addStyle(SpanStyle(color = linkColor), ppStart, ppEnd)
            addStyle(SpanStyle(color = linkColor), uaStart, uaEnd)
        }

        ClickableText(
            text = annotatedText,
            style = TextStyle(
                color = colorResource(R.color.login_main_text),
                fontSize = 12.sp
            ),
            onTextLayout = { textLineCount = it.lineCount },
            onClick = { offset ->
                when {
                    offset in psStart until psEnd -> onPrivacySummaryClick()
                    offset in ppStart until ppEnd -> onPrivacyProtectClick()
                    offset in uaStart until uaEnd -> onUserAgreementClick()
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarketingAgreementView(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompositionLocalProvider(LocalMinimumInteractiveComponentEnforcement provides false) {
            Checkbox(
                checked = checked,
                onCheckedChange = onCheckedChange,
                modifier = Modifier.size(20.dp),
                colors = CheckboxDefaults.colors(
                    checkedColor = Color(0xFF1C66E5),
                    uncheckedColor = Color(0xFFD9D9D9)
                )
            )
        }
        Text(
            text = stringResource(R.string.login_email_invite_code_marketing),
            fontSize = 12.sp,
            color = colorResource(R.color.login_text_secondary),
            modifier = Modifier.padding(start = 4.dp)
        )
    }
}
