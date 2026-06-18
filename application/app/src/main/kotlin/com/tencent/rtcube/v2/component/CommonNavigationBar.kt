package com.tencent.rtcube.v2.component

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.tencent.rtcube.v2.BuildConfig
import com.tencent.rtcube.v2.R
import com.tencent.rtcube.v2.login.LoginEntry
import com.tencent.rtcube.v2.main.common.utils.FileUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CommonNavigationBar(
    logoRes: Int,
    avatarUrl: String,
    onAvatarClick: () -> Unit,
    backgroundColor: Color = Color.White,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val hiddenCredentials by LoginEntry.hiddenCredentials.collectAsState()

    Column(modifier = modifier.fillMaxWidth()) {
        Surface(
            color = backgroundColor,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 20.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(
                    painter = painterResource(id = logoRes),
                    contentDescription = "App Logo",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .size(width = 150.dp, height = 32.dp)
                        .combinedClickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                            onClick = {},
                            onLongClick = { shareLogFile(context) },
                        ),
                )

                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .clickable { onAvatarClick() },
                ) {
                    if (avatarUrl.isNotEmpty()) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(avatarUrl)
                                .crossfade(true)
                                .build(),
                            contentDescription = "User Avatar",
                            contentScale = ContentScale.Crop,
                            placeholder = painterResource(id = R.drawable.main_default_avatar),
                            error = painterResource(id = R.drawable.main_default_avatar),
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Image(
                            painter = painterResource(id = R.drawable.main_default_avatar),
                            contentDescription = "Default Avatar",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }

        hiddenCredentials?.let { creds ->
            Surface(
                color = Color(0xFFFFF4E5),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = stringResource(
                        id = R.string.root_hidden_config_sdkappid_tip,
                        creds.sdkAppId
                    ),
                    color = Color(0xFFAA5A00),
                    fontSize = 12.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                )
            }
        }
    }
}

private fun shareLogFile(context: Context) {
    CoroutineScope(Dispatchers.IO).launch {
        val logFile = FileUtil.getLogFile(context)
        if (logFile != null) {
            withContext(Dispatchers.Main) {
                val shareIntent = Intent(Intent.ACTION_SEND)
                shareIntent.setType("application/zip")
                val uri = FileProvider.getUriForFile(context, "${BuildConfig.APPLICATION_ID}.fileprovider", logFile)
                shareIntent.putExtra(Intent.EXTRA_STREAM, uri)
                shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.root_logupload_share_log)))
            }
        }
    }
}