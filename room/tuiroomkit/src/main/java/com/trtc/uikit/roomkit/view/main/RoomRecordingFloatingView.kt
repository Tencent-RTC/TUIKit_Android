package com.trtc.uikit.roomkit.view.main

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import com.trtc.uikit.roomkit.R
import com.trtc.uikit.roomkit.aitranscription.repository.AITranscriberRepository
import com.trtc.uikit.roomkit.base.error.ErrorLocalized
import com.trtc.uikit.roomkit.base.log.RoomKitLogger
import com.trtc.uikit.roomkit.base.ui.BaseView
import com.trtc.uikit.roomkit.base.ui.RoomAlertDialog
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.room.ParticipantRole
import io.trtc.tuikit.atomicxcore.api.room.RecordingStatus
import io.trtc.tuikit.atomicxcore.api.room.RoomParticipantStore
import io.trtc.tuikit.atomicxcore.api.room.RoomStore
import io.trtc.tuikit.atomicxcore.api.room.RoomType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Recording status view shown while the room is being cloud-recorded.
 *
 * Tapping the pill (owner/admin only) replaces it in place with a popup card containing
 * a Stop action; tapping outside the popup or the Stop button dismisses it and restores
 * the pill. General users see no chevron and the pill is non-interactive.
 */
class RoomRecordingFloatingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : BaseView(context, attrs, defStyleAttr) {

    private val logger = RoomKitLogger.getLogger("RoomRecordingFloatingView")

    private val scope = CoroutineScope(Dispatchers.Main)
    private var subscribeJob: Job? = null

    private val roomStore = RoomStore.shared()
    private var participantStore: RoomParticipantStore? = null

    private val llRecordingFloating: LinearLayout by lazy { findViewById(R.id.ll_recording_floating) }
    private val ivArrow: ImageView by lazy { findViewById(R.id.iv_recording_arrow) }
    private val vRecordingDot: View by lazy { findViewById(R.id.v_recording_dot) }

    private var canManage = false
    private var stopPopup: PopupWindow? = null
    private var stopConfirmDialog: RoomAlertDialog? = null

    private val blinkingDots = mutableListOf<View>()
    private val blinkAnimator: ValueAnimator =
        ValueAnimator.ofFloat(1f, 0.3f).apply {
            duration = 600
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { anim ->
                val alpha = anim.animatedValue as Float
                blinkingDots.forEach { it.alpha = alpha }
            }
        }

    init {
        LayoutInflater.from(context).inflate(R.layout.roomkit_view_recording_floating, this)
        llRecordingFloating.setOnClickListener { handleTap() }
        blinkingDots.add(vRecordingDot)
    }

    fun init(roomID: String, roomType: RoomType) {
        super.init(roomID)
    }

    override fun initStore(roomID: String) {
        participantStore = RoomParticipantStore.create(roomID)
    }

    override fun addObserver() {
        val participantStore = participantStore ?: return
        subscribeJob?.cancel()
        subscribeJob = scope.launch {
            launch {
                roomStore.state.currentRoom
                    .map {
                        it?.recordingInfo?.status ?: RecordingStatus.NONE
                    }
                    .distinctUntilChanged()
                    .collect { status -> updateRecordingStatus(status) }
            }
            launch {
                participantStore.state.localParticipant
                    .map { it?.role ?: ParticipantRole.GENERAL_USER }
                    .distinctUntilChanged()
                    .collect { role -> updateRole(role) }
            }
        }
    }

    override fun removeObserver() {
        subscribeJob?.cancel()
        subscribeJob = null
        dismissStopPopup()
        dismissStopConfirmDialog()
        stopBlink()
    }

    private fun updateRecordingStatus(status: RecordingStatus) {
        val isRecording = status == RecordingStatus.RECORDING
        visibility = if (isRecording) VISIBLE else GONE
        if (isRecording) {
            startBlink()
        } else {
            stopBlink()
            dismissStopPopup()
        }
    }

    private fun startBlink() {
        if (!blinkAnimator.isStarted) {
            blinkAnimator.start()
        }
    }

    private fun stopBlink() {
        if (blinkAnimator.isStarted) {
            blinkAnimator.cancel()
        }
        blinkingDots.forEach { it.alpha = 1f }
    }

    private fun updateRole(role: ParticipantRole) {
        canManage = role == ParticipantRole.OWNER || role == ParticipantRole.ADMIN
        ivArrow.visibility = if (canManage) VISIBLE else GONE
        if (!canManage) {
            dismissStopPopup()
            dismissStopConfirmDialog()
        }
    }

    private fun dismissStopConfirmDialog() {
        stopConfirmDialog?.takeIf { it.isShowing }?.dismiss()
        stopConfirmDialog = null
    }

    private fun handleTap() {
        if (!canManage) return
        if (stopPopup?.isShowing == true) dismissStopPopup() else showStopPopup()
    }

    private fun showStopPopup() {
        val content = LayoutInflater.from(context)
            .inflate(R.layout.roomkit_view_recording_card, null)
        content.findViewById<View>(R.id.ll_recording_stop).setOnClickListener {
            dismissStopPopup()
            showStopConfirmDialog()
        }
        content.findViewById<View>(R.id.ll_recording_header).setOnClickListener {
            dismissStopPopup()
        }
        val cardDot = content.findViewById<View>(R.id.v_card_recording_dot)
        blinkingDots.add(cardDot)
        val popup = PopupWindow(
            content,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            setBackgroundDrawable(ColorDrawable(0))
            isOutsideTouchable = true
            isFocusable = true
            setOnDismissListener {
                llRecordingFloating.visibility = VISIBLE
                blinkingDots.remove(cardDot)
            }
        }
        llRecordingFloating.visibility = INVISIBLE
        popup.showAsDropDown(llRecordingFloating, 0, -llRecordingFloating.height)
        stopPopup = popup
    }

    private fun dismissStopPopup() {
        stopPopup?.takeIf { it.isShowing }?.dismiss()
        stopPopup = null
    }

    private fun showStopConfirmDialog() {
        dismissStopConfirmDialog()
        stopConfirmDialog = RoomAlertDialog.Builder(context)
            .setTitle(R.string.roomkit_cloud_record_stop_title)
            .setMessage(R.string.roomkit_cloud_record_stop_tips)
            .setWarning(true)
            .setNegativeButton(R.string.roomkit_cancel)
            .setPositiveButton(R.string.roomkit_cloud_record_stop) { stopRecording() }
            .show()
    }

    private fun stopRecording() {
        roomStore.stopRecording(object : CompletionHandler {
            override fun onSuccess() {}

            override fun onFailure(code: Int, desc: String) {
                logger.error("stopRecording failed:code=$code,desc=$desc")
                ErrorLocalized.showError(context, code)
            }
        })
    }
}
