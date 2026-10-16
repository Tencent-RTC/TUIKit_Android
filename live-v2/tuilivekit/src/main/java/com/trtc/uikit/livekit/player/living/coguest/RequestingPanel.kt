package com.trtc.uikit.livekit.player.living.coguest

import android.annotation.SuppressLint
import android.content.Context
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.trtc.uikit.livekit.R
import com.trtc.uikit.livekit.common.basic.LiveKitLogger
import com.trtc.uikit.livekit.common.theme.BottomSheetView
import com.trtc.uikit.livekit.common.basic.setDebounceClickListener
import com.trtc.uikit.livekit.common.theme.avatar.AtomicAvatar
import com.trtc.uikit.livekit.common.theme.toast.LiveToast
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.live.CoGuestStore
import io.trtc.tuikit.atomicxcore.api.live.LiveInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@SuppressLint("InflateParams")
class RequestingPanel(
    context: Context,
    private val liveInfo: LiveInfo,
    private val uiState: MutableStateFlow<CoGuestStatus>,
    private val openCameraAfterAccept: MutableStateFlow<Boolean>,
    private val updateUiState: (CoGuestStatus) -> Unit,
) : BottomSheetView(context) {

    companion object {
        private val APPLY_ERROR_MESSAGES = mapOf(
            100004 to R.string.livekit_coguest_apply_room_not_exist,
            100005 to R.string.livekit_coguest_apply_not_room_member,
            100200 to R.string.livekit_coguest_apply_seat_locked,
            100202 to R.string.livekit_coguest_apply_already_requesting,
            100203 to R.string.livekit_coguest_apply_already_on_seat,
            100205 to R.string.livekit_coguest_apply_seat_full,
            100210 to R.string.livekit_coguest_apply_seat_occupied,
            100211 to R.string.livekit_coguest_apply_not_supported,
        )
    }

    private val logger = LiveKitLogger.getComponentLogger("RequestingPanel")
    private val root: FrameLayout = LayoutInflater.from(context)
        .inflate(R.layout.livekit_coguest_apply_dialog, contentView, false) as FrameLayout
    private val avatar: AtomicAvatar = root.findViewById(R.id.avatar_view)
    private val name: TextView = root.findViewById(R.id.tv_name)
    private val actionsView: LinearLayout = root.findViewById(R.id.actions_view)
    private val voiceButton: TextView = root.findViewById(R.id.btn_apply_voice)
    private val videoButton: TextView = root.findViewById(R.id.btn_apply_video)
    private val cancelApplyButton: TextView = root.findViewById(R.id.btn_cancel_apply)

    private var settingsPanel: SettingsPanel? = null

    private var dialogScope: CoroutineScope? = null
    private var uiStateJob: Job? = null

    init {
        cancelApplyButton.setDebounceClickListener { doCancelApplication() }

        voiceButton.setDebounceClickListener {
            if (uiState.value == CoGuestStatus.IDLE) doApplyForSeat(false)
        }
        videoButton.setDebounceClickListener {
            if (uiState.value == CoGuestStatus.IDLE) showVideoDetailDialog()
        }

        contentView.addView(root)
        bindLiveInfo()
    }

    private fun bindLiveInfo() {
        val owner = liveInfo.liveOwner
        avatar.setContent(
            AtomicAvatar.AvatarContent.URL(
                url = owner.avatarURL,
                placeImage = R.drawable.livekit_livelist_default_avatar
            )
        )
        val displayName = when {
            !TextUtils.isEmpty(owner.userName) -> owner.userName
            !TextUtils.isEmpty(owner.userID) -> owner.userID
            else -> ""
        }
        name.text = if (displayName.length > 15) displayName.take(15) + "…" else displayName
    }

    fun setUiState(state: CoGuestStatus) {
        when (state) {
            CoGuestStatus.IDLE -> {
                actionsView.visibility = View.VISIBLE
                cancelApplyButton.visibility = View.GONE
            }
            CoGuestStatus.REQUESTING -> {
                actionsView.visibility = View.GONE
                cancelApplyButton.visibility = View.VISIBLE
            }
            CoGuestStatus.COGUESTING -> {
                dismissAll()
            }
        }
    }

    override fun show() {
        super.show()
        startObserveUiState()
    }

    override fun dismiss() {
        stopObserveUiState()
        settingsPanel?.dismiss()
        super.dismiss()
    }

    fun dismissAll() {
        dismiss()
    }

    private fun startObserveUiState() {
        stopObserveUiState()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        dialogScope = scope
        uiStateJob = scope.launch {
            uiState.collectLatest { setUiState(it) }
        }
    }

    private fun stopObserveUiState() {
        uiStateJob?.cancel()
        uiStateJob = null
        dialogScope?.cancel()
        dialogScope = null
    }

    private fun doApplyForSeat(requestVideo: Boolean) {
        openCameraAfterAccept.value = requestVideo
        CoGuestStore.create(liveInfo.liveID).applyForSeat(
            -1,
            0,
            requestVideo.toString(),
            object : CompletionHandler {
                override fun onSuccess() {
                    if (!isShowing) show()
                }

                override fun onFailure(code: Int, desc: String) {
                    logger.error("applyForSeat failed: code=$code, desc=$desc")
                    val messageRes = APPLY_ERROR_MESSAGES[code]
                        ?: R.string.livekit_coguest_apply_failed
                    LiveToast.show(context, context.getString(messageRes), LiveToast.Style.WARNING)
                    openCameraAfterAccept.value = false
                    updateUiState(CoGuestStatus.IDLE)
                }
            }
        )
        updateUiState(CoGuestStatus.REQUESTING)
    }

    private fun doCancelApplication() {
        val activeScope = dialogScope
        CoGuestStore.create(liveInfo.liveID).cancelApplication(object : CompletionHandler {
            override fun onSuccess() {
                openCameraAfterAccept.value = false
            }

            override fun onFailure(code: Int, desc: String) {
                logger.error("cancel application failed: code=$code, desc=$desc")
                activeScope?.launch {
                    if (dialogScope !== activeScope || !isShowing) return@launch
                    LiveToast.show(context, context.getString(R.string.livekit_coguest_cancel_failed), LiveToast.Style.WARNING)
                }
            }
        })
        updateUiState(CoGuestStatus.IDLE)
    }

    private fun showVideoDetailDialog() {
        val dialog = settingsPanel ?: SettingsPanel(context, SettingsPanel.Mode.PREVIEW).apply {
            onDismissRequest = {
            }
            onRequestCoGuest = {
                doApplyForSeat(true)
                dismiss()
            }
        }.also { settingsPanel = it }
        dialog.show()
    }
}
