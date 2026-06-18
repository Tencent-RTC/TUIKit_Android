package com.tencent.rtcube.v2.login.components.views

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tencent.rtcube.v2.login.AgreementNavigator
import com.tencent.rtcube.v2.login.R

/** Privacy agreement bottom sheet dialog. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun PrivacyBottomDialog(
    onAgree: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.White,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.login_privacy_read_conditions),
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF1A1A1A),
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(20.dp))

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                LinkText(
                    text = stringResource(R.string.login_privacy_summary_link),
                    onClick = { AgreementNavigator.openPrivacySummary(context) },
                )
                LinkText(
                    text = stringResource(R.string.login_privacy_protection_guide),
                    onClick = { AgreementNavigator.openPrivacyPolicy(context) },
                )
                LinkText(
                    text = stringResource(R.string.login_privacy_user_agreement),
                    onClick = { AgreementNavigator.openUserAgreement(context) },
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = onAgree,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF0062E3),
                    contentColor = Color.White,
                ),
            ) {
                Text(
                    text = stringResource(R.string.login_privacy_agree_continue),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Normal,
                )
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
private fun LinkText(
    text: String,
    onClick: () -> Unit,
) {
    Text(
        text = text,
        fontSize = 15.sp,
        color = Color(0xFF1C66E5),
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 4.dp),
    )
}
