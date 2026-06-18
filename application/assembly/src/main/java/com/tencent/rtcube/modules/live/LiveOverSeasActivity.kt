package com.tencent.rtcube.modules.live

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.tencent.rtcube.assembly.AppTarget
import com.tencent.rtcube.modules.R
import com.tencent.rtcube.modules.voiceroom.VoiceRoomListActivity

private const val TAG = "OverSeasLiveScreen"
private const val IDENTIFIER_LIVE = "live"
private const val IDENTIFIER_VOICE = "voice_chat"
private const val LIVE_DOC_URL = "https://trtc.io/document/60037?product=live&menulabel=uikit&platform=android"

class LiveOverSeasActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_TARGET = "extra_app_target"
    }

    private val appTarget: AppTarget by lazy {
        val name = intent.getStringExtra(EXTRA_TARGET)
        AppTarget.entries.find { it.name == name } ?: AppTarget.OVERSEAS
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            OverSeasLiveScreen(
                appTarget = appTarget,
                onBack = { finish() }
            )
        }
    }
}

@Composable
fun OverSeasLiveScreen(appTarget: AppTarget, onBack: () -> Unit) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .statusBarsPadding(),
    ) {
        LiveEntranceToolbar(
            onBack = onBack,
            onHelp = { openLiveDoc(context) },
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
        ) {
            LiveEntranceCard(
                title = stringResource(R.string.assembly_over_seas_live_title),
                subtitle = stringResource(R.string.assembly_over_seas_live_subtitle),
                decorationRes = R.drawable.live_overseas_live_decoration,
                onClick = { launchModule(context, IDENTIFIER_LIVE, appTarget) },
            )
            Spacer(modifier = Modifier.height(16.dp))
            LiveEntranceCard(
                title = stringResource(R.string.assembly_over_seas_voice_room_title),
                subtitle = stringResource(R.string.assembly_over_seas_voice_room_subtitle),
                decorationRes = R.drawable.live_overseas_voice_room_decoration,
                onClick = { launchModule(context, IDENTIFIER_VOICE, appTarget) },
            )
        }
    }
}

@Composable
private fun LiveEntranceToolbar(onBack: () -> Unit, onHelp: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp)
            .height(56.dp),
    ) {
        Image(
            painter = painterResource(R.drawable.live_back_button),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .size(56.dp)
                .clickable(
                    interactionSource = MutableInteractionSource(),
                    indication = null
                ) { onBack() }
                .padding(8.dp),
        )
        Text(
            text = stringResource(R.string.assembly_over_seas_live_title),
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Black,
            modifier = Modifier.align(Alignment.Center),
        )
        Image(
            painter = painterResource(R.drawable.live_question_link),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .size(56.dp)
                .clickable(
                    interactionSource = MutableInteractionSource(),
                    indication = null
                ) { onHelp() }
                .padding(14.dp),
        )
    }
}

@Composable
private fun LiveEntranceCard(
    title: String,
    subtitle: String,
    decorationRes: Int,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .clickable(
                interactionSource = MutableInteractionSource(),
                indication = null
            ) { onClick() },
    ) {
        Image(
            painter = painterResource(R.drawable.live_overseas_bg_live_card),
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = Modifier.fillMaxSize(),
        )
        Image(
            painter = painterResource(decorationRes),
            contentDescription = null,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 10.dp)
                .width(112.dp)
                .height(160.dp),
        )
        Text(
            text = title,
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 20.dp, top = 20.dp),
        )
        Text(
            text = subtitle,
            color = Color(0xBFFFFFFF),
            fontSize = 11.sp,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 20.dp, top = 56.dp)
                .width(160.dp),
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 20.dp, bottom = 20.dp)
                .size(width = 58.dp, height = 24.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White)
                .clickable(
                    interactionSource = MutableInteractionSource(),
                    indication = null
                ) { },
        ) {
            Image(
                painter = painterResource(R.drawable.live_ic_right_arrow),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 7.dp),
            )
        }
    }
}

private fun launchModule(context: Context, identifier: String, appTarget: AppTarget) {
    val intent = when (identifier) {
        IDENTIFIER_LIVE -> Intent(context, LiveListActivity::class.java).apply {
            putExtra(LiveListActivity.EXTRA_TARGET, appTarget.name)
        }

        IDENTIFIER_VOICE -> Intent(context, VoiceRoomListActivity::class.java).apply {
            putExtra(VoiceRoomListActivity.EXTRA_TARGET, appTarget.name)
        }

        else -> null
    }

    runCatching {
        intent?.let { context.startActivity(it) }
    }.onFailure { e ->
        Log.w(TAG, "Failed to launch module: $identifier", e)
        Toast.makeText(context, "Coming Soon", Toast.LENGTH_SHORT).show()
    }
}

private fun openLiveDoc(context: Context) {
    runCatching {
        val intent = Intent(Intent.ACTION_VIEW, LIVE_DOC_URL.toUri())
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }.onFailure { e ->
        Log.w(TAG, "Failed to open doc", e)
    }
}