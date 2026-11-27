package io.trtc.tuikit.chat.settings

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import io.trtc.tuikit.chat.BaseActivity
import io.trtc.tuikit.chat.R
import io.trtc.tuikit.chat.uikit.components.ai.tts.VoiceMessageConfig
import io.trtc.tuikit.chat.uikit.components.common.SetActivitySystemBarAppearance
import io.trtc.tuikit.chat.uikit.components.theme.LocalTheme

class VoiceMessageSettingActivity : BaseActivity() {

    companion object {
        fun start(context: Context) {
            context.startActivity(Intent(context, VoiceMessageSettingActivity::class.java))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SetActivitySystemBarAppearance()
            VoiceMessageSettingScreen(onBackClick = { finish() })
        }
    }
}

@Composable
private fun VoiceMessageSettingScreen(onBackClick: () -> Unit) {
    val colors = LocalTheme.current.colors
    val context = LocalContext.current
    var selectedVoiceName by remember { mutableStateOf("") }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val name = VoiceMessageConfig.getSelectedVoiceName(context)
                selectedVoiceName = name
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        val name = VoiceMessageConfig.getSelectedVoiceName(context)
        selectedVoiceName = name
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val defaultVoiceName = stringResource(io.trtc.tuikit.chat.uikit.compose.R.string.voice_message_voice_default)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bgColorTopBar)
            .navigationBarsPadding()
    ) {
        DemoPageHeader(
            title = stringResource(R.string.compose_demo_voice_message_settings),
            onBackClick = onBackClick
        )

        Spacer(modifier = Modifier.height(10.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.bgColorOperate)
        ) {
            VoiceSettingEntry(
                title = stringResource(R.string.compose_demo_voice_clone),
                value = "",
                showDivider = true,
                onClick = { VoiceCloneActivity.start(context) }
            )
            VoiceSettingEntry(
                title = stringResource(R.string.compose_demo_voice_select),
                value = selectedVoiceName.ifEmpty { defaultVoiceName },
                showDivider = false,
                onClick = { VoiceSelectActivity.start(context) }
            )
        }
    }
}

@Composable
private fun VoiceSettingEntry(
    title: String,
    value: String,
    showDivider: Boolean,
    onClick: () -> Unit
) {
    val colors = LocalTheme.current.colors
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                fontSize = 16.sp,
                maxLines = 1,
                color = colors.textColorSecondary
            )
            Text(
                text = value,
                fontSize = 16.sp,
                maxLines = 1,
                textAlign = TextAlign.End,
                overflow = TextOverflow.Ellipsis,
                color = colors.textColorPrimary,
                modifier = Modifier
                    .padding(start = 16.dp)
                    .weight(1f)
            )
            Icon(
                painter = painterResource(id = R.drawable.app_navigation_right_icon),
                contentDescription = null,
                tint = colors.textColorTertiary,
                modifier = Modifier
                    .padding(start = 8.dp)
                    .width(7.dp)
                    .height(12.dp)
            )
        }
        if (showDivider) {
            Spacer(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .background(colors.strokeColorPrimary)
            )
        }
    }
}
