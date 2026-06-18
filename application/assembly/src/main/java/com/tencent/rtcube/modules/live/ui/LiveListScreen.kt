package com.tencent.rtcube.modules.live.ui

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.FragmentActivity
import com.tencent.qcloud.tuicore.TUICore
import com.tencent.rtcube.modules.R
import com.trtc.uikit.livekit.common.EVENT_KEY_LIVE_KIT
import com.trtc.uikit.livekit.common.EVENT_SUB_KEY_DESTROY_LIVE_VIEW
import com.trtc.uikit.livekit.component.pippanel.PIPPanelStore
import com.trtc.uikit.livekit.features.livelist.LiveListView
import com.trtc.uikit.livekit.features.livelist.Style
import com.trtc.uikit.livekit.livestream.VideoLiveAudienceActivity
import com.trtc.uikit.livekit.livestream.VideoLiveKit
import com.trtc.uikit.livekit.voiceroom.VoiceRoomKit
import io.trtc.tuikit.atomicx.widget.basicwidget.toast.AtomicToast
import io.trtc.tuikit.atomicxcore.api.live.LiveListStore

@Composable
fun LiveListScreen(
    onBack: () -> Unit,
    onCreateLive: () -> Unit,
    onToggleColumnClick: (String) -> Unit,
) {
    val context = LocalContext.current
    var columnStyle by remember { mutableStateOf(Style.DOUBLE_COLUMN) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF131417)),
    ) {
        LiveListTitleBar(
            columnStyle = columnStyle,
            onBack = onBack,
            onToggleColumn = {
                columnStyle = if (columnStyle == Style.DOUBLE_COLUMN) Style.SINGLE_COLUMN else Style.DOUBLE_COLUMN
                onToggleColumnClick(columnStyle.name.lowercase())
            },
        )

        Box(modifier = Modifier.weight(1f)) {
            AndroidView(
                factory = { ctx ->
                    LiveListView(ctx).also { view ->
                        view.init(ctx as FragmentActivity, columnStyle)
                        view.setOnItemClickListener { itemView, liveInfo ->
                            if (!itemView.isEnabled) return@setOnItemClickListener
                            itemView.isEnabled = false
                            itemView.postDelayed({ itemView.isEnabled = true }, 1000)
                            if (PIPPanelStore.sharedInstance().state.isAnchorStreaming) {
                                if (liveInfo.liveID == LiveListStore.shared().liveState.currentLive.value.liveID) {
                                    VideoLiveKit.createInstance(ctx).joinLive(liveInfo)
                                } else {
                                    val msg = ctx.getString(R.string.assembly_live_list_exit_float_window_tip)
                                    AtomicToast.show(ctx, msg, AtomicToast.Style.ERROR)
                                }
                                return@setOnItemClickListener
                            }
                            if (PIPPanelStore.sharedInstance().state.audienceIsPictureInPictureMode && liveInfo.liveID == PIPPanelStore.sharedInstance().state.roomId.value) {
                                val intent =
                                    Intent(context, VideoLiveAudienceActivity::class.java).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                context.startActivity(intent)
                                return@setOnItemClickListener
                            }
                            TUICore.notifyEvent(EVENT_KEY_LIVE_KIT, EVENT_SUB_KEY_DESTROY_LIVE_VIEW, null)
                            if (liveInfo.liveID.startsWith("voice_")) {
                                VoiceRoomKit.createInstance(ctx).enterRoom(liveInfo.liveID)
                            } else {
                                VideoLiveKit.createInstance(ctx).joinLive(liveInfo)
                            }
                        }
                    }
                },
                update = { view ->
                    view.updateColumnStyle(columnStyle)
                },
                modifier = Modifier.fillMaxSize(),
            )

            if (columnStyle == Style.DOUBLE_COLUMN) {
                CreateLiveButton(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(bottom = 15.dp),
                    onClick = { onCreateLive() },
                )
            }
        }
    }
}

@Composable
private fun LiveListTitleBar(
    columnStyle: Style,
    onBack: () -> Unit,
    onToggleColumn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(44.dp)
            .background(Color(0xFF131417)),
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier.align(Alignment.CenterStart),
        ) {
            Icon(
                painter = painterResource(R.drawable.live_list_ic_back),
                contentDescription = stringResource(R.string.assembly_live_list_back),
                modifier = Modifier.size(24.dp),
                tint = Color.White,
            )
        }

        Text(
            text = stringResource(R.string.assembly_live_list_title),
            fontSize = 17.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White,
            modifier = Modifier.align(Alignment.Center),
        )

        IconButton(
            onClick = onToggleColumn,
            modifier = Modifier.align(Alignment.CenterEnd),
        ) {
            Icon(
                painter = painterResource(
                    if (columnStyle == Style.DOUBLE_COLUMN) R.drawable.live_list_ic_single_column
                    else R.drawable.live_list_ic_double_column,
                ),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = Color.White,
            )
        }
    }
}

@Composable
private fun CreateLiveButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(52.dp),
        shape = RoundedCornerShape(26.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFF1C66E5),
        ),
    ) {
        Icon(
            painter = painterResource(R.drawable.live_list_ic_create_live),
            contentDescription = null,
            modifier = Modifier
                .size(24.dp)
                .padding(end = 8.dp),
            tint = Color.White,
        )
        Text(
            text = stringResource(R.string.assembly_live_list_create_room),
            fontSize = 20.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White,
        )
    }
}

