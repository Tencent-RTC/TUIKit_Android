package com.trtc.uikit.livekit.voiceroom.view.preview

import android.app.Activity
import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import com.tencent.qcloud.tuicore.TUIConstants
import com.tencent.qcloud.tuicore.TUICore
import com.tencent.qcloud.tuicore.TUIThemeManager
import com.trtc.uikit.livekit.R
import com.trtc.uikit.livekit.common.DEFAULT_BACKGROUND_URL
import com.trtc.uikit.livekit.common.DEFAULT_COVER_URL
import com.trtc.uikit.livekit.common.ErrorLocalized
import com.trtc.uikit.livekit.common.LiveKitLogger
import com.trtc.uikit.livekit.common.TEMPLATE_ID_VOICE_ROOM
import com.trtc.uikit.livekit.common.completionHandler
import com.trtc.uikit.livekit.voiceroom.manager.VoiceRoomManager
import com.trtc.uikit.livekit.voiceroom.store.LayoutType
import com.trtc.uikit.livekit.voiceroom.store.LiveStatus
import com.trtc.uikit.livekit.voiceroom.store.LiveStreamPrivacyStatus
import com.trtc.uikit.livekit.voiceroom.view.basic.BasicView
import io.trtc.tuikit.atomicxcore.api.live.LiveAudienceStore
import io.trtc.tuikit.atomicxcore.api.live.LiveInfo
import io.trtc.tuikit.atomicxcore.api.live.LiveInfoCompletionHandler
import io.trtc.tuikit.atomicxcore.api.live.LiveListStore
import io.trtc.tuikit.atomicxcore.api.live.SeatLayoutTemplate
import io.trtc.tuikit.atomicxcore.api.live.TakeSeatMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Locale

class AnchorPreviewView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : BasicView(context, attrs, defStyleAttr) {

    private var isExit = false
    private var imageKaraokeView: ImageView? = null
    private lateinit var liveListStore: LiveListStore
    private lateinit var liveAudienceStore: LiveAudienceStore

    init {
        LayoutInflater.from(context).inflate(R.layout.livekit_anchor_preview, this, true)
        findViewById<View>(R.id.iv_back).setOnClickListener {
            isExit = true
            (context as Activity).finish()
        }
        findViewById<View>(R.id.btn_start_live).setOnClickListener { view ->
            createRoom(view)
        }
    }

    override fun init(liveID: String, voiceRoomManager: VoiceRoomManager) {
        super.init(liveID, voiceRoomManager)
        initLiveInfoEditView()
        initFunctionView()
        initKTVView()
    }

    override fun addObserver() {
        subscribeStateJob = CoroutineScope(Dispatchers.Main).launch {
            voiceRoomManager?.prepareStore?.prepareState?.layoutType?.collect {
                onVoiceRoomLayoutChanged(it)
            }
        }
    }

    override fun removeObserver() {
        subscribeStateJob?.cancel()
    }

    override fun initStore() {
        liveListStore = LiveListStore.shared()
        liveAudienceStore = LiveAudienceStore.create(liveID)
    }

    private fun initLiveInfoEditView() {
        if (voiceRoomManager == null) {
            return
        }
        val liveStreamSettingsCard = findViewById<LiveInfoEditView>(R.id.rl_live_info_edit_view)
        liveStreamSettingsCard.init(liveID, voiceRoomManager!!)
    }

    private fun initFunctionView() {
        if (voiceRoomManager == null) {
            return
        }
        val functionView = findViewById<AnchorPreviewFunctionView>(R.id.rl_function)
        functionView.init(liveID, voiceRoomManager!!)
    }

    private fun initKTVView() {
        imageKaraokeView = findViewById(R.id.iv_ktv)
        when (TUIThemeManager.getInstance().currentLanguage) {
            Locale.SIMPLIFIED_CHINESE.language -> {
                imageKaraokeView?.setImageResource(R.drawable.karaoke_preview_song_request_zh)
            }

            Locale.TRADITIONAL_CHINESE.language -> {
                imageKaraokeView?.setImageResource(R.drawable.karaoke_preview_song_request_tw)
            }

            else -> {
                imageKaraokeView?.setImageResource(R.drawable.karaoke_preview_song_request_en)
            }
        }
    }

    private fun createRoom(view: View) {
        if (!view.isEnabled) {
            return
        }
        view.isEnabled = false
        val prepareState = voiceRoomManager?.prepareStore?.prepareState
        val maxSeatCount = prepareState?.liveInfo?.value?.maxSeatCount ?: 9
        val liveInfo = LiveInfo(seatTemplate = SeatLayoutTemplate.AudioSalon(maxSeatCount))
        liveInfo.isSeatEnabled = true
        liveInfo.keepOwnerOnSeat = true
        liveInfo.seatLayoutTemplateID = TEMPLATE_ID_VOICE_ROOM
        liveInfo.liveID = prepareState?.liveInfo?.value?.liveID ?: ""
        liveInfo.liveName = prepareState?.liveInfo?.value?.liveName ?: ""
        liveInfo.maxSeatCount = maxSeatCount
        liveInfo.seatMode = prepareState?.liveInfo?.value?.seatMode ?: TakeSeatMode.FREE
        liveInfo.backgroundURL = prepareState?.liveInfo?.value?.backgroundURL ?: DEFAULT_BACKGROUND_URL
        liveInfo.coverURL = prepareState?.liveInfo?.value?.coverURL ?: DEFAULT_COVER_URL
        liveInfo.isPublicVisible = prepareState?.liveExtraInfo?.value?.liveMode == LiveStreamPrivacyStatus.PUBLIC
        liveListStore.createLive(
            liveInfo,
            object : LiveInfoCompletionHandler {
                override fun onSuccess(liveInfo: LiveInfo) {
                    if (isExit) {
                        liveListStore.endLive(null)
                        return
                    }
                    LOGGER.info("create room success")
                    getAudienceList()
                    voiceRoomManager?.prepareStore?.updateLiveInfo(liveInfo)
                    voiceRoomManager?.prepareStore?.updateLiveStatus(LiveStatus.PUSHING)
                    showAlertUserLiveTips()
                    view.isEnabled = true
                }

                override fun onFailure(code: Int, desc: String) {
                    LOGGER.error(" create room failed, error: $code, message: $desc")
                    ErrorLocalized.onError(code)
                    view.isEnabled = true
                }
            })
    }

    private fun showAlertUserLiveTips() {
        try {
            val map = HashMap<String, Any>()
            map[TUIConstants.Privacy.PARAM_DIALOG_CONTEXT] = context
            TUICore.notifyEvent(
                TUIConstants.Privacy.EVENT_ROOM_STATE_CHANGED,
                TUIConstants.Privacy.EVENT_SUB_KEY_ROOM_STATE_START,
                map
            )
        } catch (e: Exception) {
            LOGGER.error("showAlertUserLiveTips exception:" + e.message)
        }
    }

    private fun onVoiceRoomLayoutChanged(layoutType: LayoutType) {
        imageKaraokeView?.visibility =
            if (layoutType == LayoutType.VOICE_ROOM) GONE else VISIBLE
    }

    private fun getAudienceList() {
        liveAudienceStore.fetchAudienceList(completionHandler {
            onError { code, desc ->
                LOGGER.error("fetchAudienceList,error:$code,message:$desc")
                ErrorLocalized.onError(code)
            }
        })
    }

    companion object {
        private val LOGGER = LiveKitLogger.getVoiceRoomLogger("AnchorPreviewView")
    }
}