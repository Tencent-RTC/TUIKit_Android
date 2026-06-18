package com.tencent.rtcube.v2.privacy.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.tencent.rtcube.v2.R
import com.tencent.rtcube.v2.privacy.store.PrivacyStore

@Composable
internal fun PersonalInfoScreen(
    store: PrivacyStore,
    onBack: () -> Unit,
) {
    val state by store.state.collectAsState()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val copySuccess = stringResource(R.string.privacy_tip_copy)
    val noneText = stringResource(R.string.privacy_none)

    LaunchedEffect(Unit) {
        store.loadUserInfo()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colorResource(R.color.privacy_color_white))
            .verticalScroll(rememberScrollState()),
    ) {
        PrivacyTopBar(
            title = stringResource(R.string.privacy_personal_info),
            onBack = onBack,
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.privacy_avatar),
                color = colorResource(R.color.privacy_color_main_text),
                fontSize = 16.sp,
                modifier = Modifier.weight(1f),
            )
            AsyncImage(
                model = state.userAvatar.ifEmpty { null },
                placeholder = painterResource(id = R.drawable.privacy_ic_head),
                error = painterResource(id = R.drawable.privacy_ic_head),
                contentDescription = null,
                modifier = Modifier
                    .size(50.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop,
            )
        }

        HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp), thickness = 1.dp, color = colorResource(R.color.privacy_color_line))

        PersonalInfoRow(
            label = stringResource(R.string.privacy_name),
            value = state.userName.ifEmpty { noneText },
            onCopy = {
                if (state.userName.isNotEmpty()) {
                    clipboardManager.setText(AnnotatedString(state.userName))
                    Toast.makeText(context, copySuccess, Toast.LENGTH_SHORT).show()
                }
            },
        )

        PersonalInfoRow(
            label = stringResource(R.string.privacy_id),
            value = state.userId.ifEmpty { noneText },
            onCopy = {
                if (state.userId.isNotEmpty()) {
                    clipboardManager.setText(AnnotatedString(state.userId))
                    Toast.makeText(context, copySuccess, Toast.LENGTH_SHORT).show()
                }
            },
        )

        PersonalInfoRow(
            label = stringResource(R.string.privacy_phone),
            value = state.phone.ifEmpty { noneText },
            onCopy = {
                if (state.phone.isNotEmpty()) {
                    clipboardManager.setText(AnnotatedString(state.phone))
                    Toast.makeText(context, copySuccess, Toast.LENGTH_SHORT).show()
                }
            },
        )

        PersonalInfoRow(
            label = stringResource(R.string.privacy_email),
            value = state.email.ifEmpty { noneText },
            onCopy = {
                if (state.email.isNotEmpty()) {
                    clipboardManager.setText(AnnotatedString(state.email))
                    Toast.makeText(context, copySuccess, Toast.LENGTH_SHORT).show()
                }
            },
        )
    }
}

@Composable
private fun PersonalInfoRow(
    label: String,
    value: String,
    onCopy: () -> Unit,
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(55.dp)
                .clickable { onCopy() }
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                color = colorResource(R.color.privacy_color_main_text),
                fontSize = 16.sp,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = value,
                color = colorResource(R.color.privacy_color_permission_result),
                fontSize = 14.sp,
            )
        }
        HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp), thickness = 1.dp, color = colorResource(R.color.privacy_color_line))
    }
}
