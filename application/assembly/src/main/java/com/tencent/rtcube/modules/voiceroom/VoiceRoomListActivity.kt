package com.tencent.rtcube.modules.voiceroom

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.fragment.app.FragmentActivity
import com.tencent.cloud.tuikit.engine.extension.TUILiveListManager
import com.tencent.qcloud.tuicore.TUICore
import com.tencent.qcloud.tuicore.TUILogin
import com.tencent.rtcube.assembly.AppAssembly
import com.tencent.rtcube.assembly.AppTarget
import com.tencent.rtcube.assembly.CallGuard
import com.tencent.rtcube.assembly.PrivacyAction
import com.tencent.rtcube.modules.R
import com.tencent.rtcube.v2.login.LoginEntry
import com.trtc.uikit.livekit.common.EVENT_KEY_LIVE_KIT
import com.trtc.uikit.livekit.common.EVENT_SUB_KEY_DESTROY_LIVE_VIEW
import com.trtc.uikit.livekit.common.LiveIdentityGenerator
import com.trtc.uikit.livekit.component.pippanel.PIPPanelStore
import com.trtc.uikit.livekit.features.livelist.LiveListView
import com.trtc.uikit.livekit.features.livelist.Style
import com.trtc.uikit.livekit.livestream.VideoLiveKit
import com.trtc.uikit.livekit.livestream.impl.LiveInfoUtils.asEngineLiveInfo
import com.trtc.uikit.livekit.livestream.impl.LiveInfoUtils.asStoreLiveInfo
import com.trtc.uikit.livekit.voiceroom.VoiceRoomDefine
import com.trtc.uikit.livekit.voiceroom.VoiceRoomKit
import io.trtc.tuikit.atomicx.common.util.ActivityLauncher
import io.trtc.tuikit.atomicx.widget.basicwidget.toast.AtomicToast

class VoiceRoomListActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_TARGET = "extra_app_target"
        private const val TRTC_VOICE_ROOM_DOCUMENT_URL = "https://cloud.tencent.com/document/product/647/122992"
    }

    private val appTarget: AppTarget by lazy {
        val name = intent.getStringExtra(EXTRA_TARGET)
        AppTarget.entries.find { it.name == name } ?: AppTarget.DOMESTIC
    }

    private var liveListViewRef: LiveListView? = null
    private var isInit = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VoiceRoomListScreen(
                onBack = { finish() },
                onHelpClick = { openDocumentUrl() },
                onCreateLive = { handleCreateVoiceRoom() },
                onEnterRoom = { info -> enterRoom(info) },
                onLiveListViewCreated = { view -> liveListViewRef = view },
            )
        }
    }

    override fun onResume() {
        super.onResume()
        if (!isInit) {
            isInit = true
            return
        }
        liveListViewRef?.refreshData()
    }

    private fun openDocumentUrl() {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = TRTC_VOICE_ROOM_DOCUMENT_URL.toUri()
        }
        ActivityLauncher.startActivity(this, intent)
    }

    private fun handleCreateVoiceRoom() {
        if (!CallGuard.canStartNewRoom) {
            CallGuard.showCannotStartRoomToast(this)
            return
        }

        if (PIPPanelStore.sharedInstance().state.isAnchorStreaming) {
            AtomicToast.show(this, getString(R.string.assembly_live_list_exit_float_window_tip), AtomicToast.Style.ERROR)
            return
        }

        val currentUser = LoginEntry.currentUser.value ?: return
        val userId = currentUser.userId
        val token = currentUser.token

        if (appTarget == AppTarget.DOMESTIC) {
            AppAssembly.privacyActionHandler?.invoke(
                PrivacyAction.CheckRealNameAuth(this, userId, token) { isAuth, msg ->
                    if (isAuth) {
                        startVoiceLive()
                    } else {
                        AtomicToast.show(this, msg, AtomicToast.Style.ERROR)
                    }
                },
            )
        } else {
            startVoiceLive()
        }
    }

    private fun startVoiceLive() {
        TUICore.notifyEvent(EVENT_KEY_LIVE_KIT, EVENT_SUB_KEY_DESTROY_LIVE_VIEW, null)
        val roomId = LiveIdentityGenerator.generateId(
            TUILogin.getUserId(),
            LiveIdentityGenerator.RoomType.VOICE,
        )
        val voiceRoomInfo = VoiceRoomDefine.CreateRoomParams().apply {
            maxAnchorCount = VoiceRoomDefine.MAX_CONNECTED_VIEWERS_COUNT
        }
        VoiceRoomKit.createInstance(applicationContext).createRoom(roomId, voiceRoomInfo)
    }

    private fun enterRoom(info: TUILiveListManager.LiveInfo) {
        if (PIPPanelStore.sharedInstance().state.isAnchorStreaming) {
            AtomicToast.show(this, getString(R.string.assembly_live_list_exit_float_window_tip), AtomicToast.Style.ERROR)
            return
        }
        TUICore.notifyEvent(EVENT_KEY_LIVE_KIT, EVENT_SUB_KEY_DESTROY_LIVE_VIEW, null)
        if (info.roomId.startsWith("voice_")) {
            VoiceRoomKit.createInstance(this).enterRoom(info)
        } else {
            VideoLiveKit.createInstance(this).joinLive(info.asStoreLiveInfo())
        }
    }
}

@Composable
private fun VoiceRoomListScreen(
    onBack: () -> Unit,
    onHelpClick: () -> Unit,
    onCreateLive: () -> Unit,
    onEnterRoom: (TUILiveListManager.LiveInfo) -> Unit,
    onLiveListViewCreated: (LiveListView) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White),
    ) {
        VoiceRoomTitleBar(
            onBack = onBack,
            onHelpClick = onHelpClick,
        )

        Box(modifier = Modifier.weight(1f)) {
            AndroidView(
                factory = { ctx ->
                    LiveListView(ctx).also { view ->
                        view.init(ctx as FragmentActivity, Style.DOUBLE_COLUMN)
                        onLiveListViewCreated(view)
                        view.setOnItemClickListener { itemView, liveInfo ->
                            if (!itemView.isEnabled) return@setOnItemClickListener
                            itemView.isEnabled = false
                            itemView.postDelayed({ itemView.isEnabled = true }, 1000)
                            onEnterRoom(liveInfo.asEngineLiveInfo())
                        }
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )

            CreateVoiceRoomButton(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 20.dp),
                onClick = onCreateLive,
            )
        }
    }
}

@Composable
private fun VoiceRoomTitleBar(
    onBack: () -> Unit,
    onHelpClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(56.dp)
            .background(Color.White),
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 15.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.voice_room_ic_back),
                contentDescription = stringResource(R.string.assembly_live_list_back),
                modifier = Modifier.size(18.dp),
                tint = Color.Unspecified,
            )
        }

        Text(
            text = stringResource(R.string.assembly_voiceroom_card_title),
            fontSize = 20.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF333333),
            modifier = Modifier.align(Alignment.Center),
        )

        IconButton(
            onClick = onHelpClick,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 10.dp)
                .size(34.dp),
        ) {
            Icon(
                painter = painterResource(com.trtc.uikit.livekit.R.drawable.livekit_question_link),
                contentDescription = null,
                modifier = Modifier
                    .size(34.dp)
                    .padding(5.dp),
                tint = Color.Unspecified,
            )
        }
    }
}

@Composable
private fun CreateVoiceRoomButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .height(48.dp),
        shape = RoundedCornerShape(24.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFF1C66E5),
        ),
    ) {
        Icon(
            painter = painterResource(R.drawable.voice_room_ic_create),
            contentDescription = null,
            modifier = Modifier
                .size(24.dp)
                .padding(end = 8.dp),
            tint = Color.White,
        )
        Text(
            text = stringResource(R.string.assembly_voiceroom_list_go_live),
            fontSize = 20.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White,
        )
    }
}
