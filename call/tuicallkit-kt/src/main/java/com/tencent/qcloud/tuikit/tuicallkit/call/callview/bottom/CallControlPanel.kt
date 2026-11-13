package com.tencent.qcloud.tuikit.tuicallkit.call.callview.bottom

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.tencent.qcloud.tuikit.tuicallkit.R
import com.tencent.qcloud.tuikit.tuicallkit.call.CallKitConfig
import com.tencent.qcloud.tuikit.tuicallkit.call.CallLauncher
import com.tencent.qcloud.tuikit.tuicallkit.common.CallKitLogger
import com.tencent.qcloud.tuikit.tuicallkit.common.CallKitToast
import com.tencent.qcloud.tuikit.tuicallkit.common.dp
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.call.CallMediaType
import io.trtc.tuikit.atomicxcore.api.call.CallStore
import io.trtc.tuikit.atomicxcore.api.device.DeviceStatus
import io.trtc.tuikit.atomicxcore.api.device.DeviceStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

internal class CallControlPanel @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    internal var onSpeakerBubbleToggle: (() -> Unit)? = null
    internal var onMoreBubbleToggle: (() -> Unit)? = null
    internal var onExpand: (() -> Unit)? = null
    internal var onMicPermissionRequest: ((() -> Unit) -> Unit)? = null
    internal var onCameraPermissionRequest: ((() -> Unit) -> Unit)? = null

    val speakerAnchor: View?
        get() = cells[ButtonID.SPEAKER]?.button

    val moreAnchor: View?
        get() = cells[ButtonID.MORE]?.button

    private enum class ButtonID {
        MICROPHONE,
        SPEAKER,
        CAMERA,
        TRANSCRIPTION,
        MORE,
        HANGUP,
        FLIP
    }

    private companion object {
        const val CELL_WIDTH_DP = 72
        const val ROW_GAP_DP = 26
        const val COLLAPSED_BUTTON_SIZE_DP = 46
        const val BASE_BUTTON_SIZE_DP = 58
        const val BASE_ICON_PADDING_DP = 16
    }

    private class Cell(context: Context) {
        val root: View = LayoutInflater.from(context).inflate(R.layout.tuicallkit_control_cell, null)
        val button: ImageView = root.findViewById(R.id.control_button)
        val label: TextView = root.findViewById(R.id.control_label)

        fun setOn(isOn: Boolean) {
            button.setBackgroundResource(if (isOn) R.drawable.tuicallkit_bg_control_on else R.drawable.tuicallkit_bg_control_off)
            val tintRes = if (isOn) R.color.tuicallkit_control_foreground else R.color.tuicallkit_control_off_foreground
            button.setColorFilter(ContextCompat.getColor(button.context, tintRes))
        }

        fun setGhost() {
            button.setBackgroundResource(R.drawable.tuicallkit_bg_control_off)
            button.setColorFilter(ContextCompat.getColor(button.context, android.R.color.white))
        }
    }

    private val cells = mutableMapOf<ButtonID, Cell>()
    private var pendingMicTarget: Boolean? = null
    private var isTwoRowLayout = false
    private var isCollapsed = false
    private var barBackgroundVisible = false
    private var barContentBottomInset = 0
    private var subscribeJob: Job? = null

    private val scope: CoroutineScope
        get() = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val isVideo: Boolean
        get() = CallStore.shared.observerState.activeCall.value.mediaType == CallMediaType.Video

    private val isMulti: Boolean
        get() {
            val call = CallStore.shared.observerState.activeCall.value
            val participants = CallStore.shared.observerState.allParticipants.value
            return call.chatGroupId.isNotEmpty() || call.inviteeIds.size > 1 || participants.size > 2
        }

    private val isMicOn: Boolean
        get() = DeviceStore.shared().deviceState.microphoneStatus.value == DeviceStatus.ON

    private val isCameraOn: Boolean
        get() = DeviceStore.shared().deviceState.cameraStatus.value == DeviceStatus.ON

    private val currentRouteId: String
        get() = DeviceStore.shared().deviceState.currentSpeaker.value.deviceId

    private val isSpeakerOn: Boolean
        get() = currentRouteId == "speaker_1"

    private val isExternalRoute: Boolean
        get() = currentRouteId.isNotEmpty() && currentRouteId != "speaker_0" && currentRouteId != "speaker_1"

    init {
        rebuildLayout()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        subscribeDeviceState()
    }

    override fun onDetachedFromWindow() {
        subscribeJob?.cancel()
        subscribeJob = null
        super.onDetachedFromWindow()
    }

    fun setCollapsed(collapsed: Boolean) {
        if (collapsed == isCollapsed) {
            return
        }
        isCollapsed = collapsed
        rebuildLayout()
    }

    fun setBarBackground(visible: Boolean) {
        if (visible == barBackgroundVisible) {
            return
        }
        barBackgroundVisible = visible
        rebuildLayout()
    }

    fun setBarContentBottomInset(inset: Int) {
        if (inset == barContentBottomInset) {
            return
        }
        barContentBottomInset = inset
        if (barBackgroundVisible) {
            rebuildLayout()
        }
    }

    private fun subscribeDeviceState() {
        subscribeJob?.cancel()
        subscribeJob = scope.launch {
            launch {
                DeviceStore.shared().deviceState.microphoneStatus.collect { status ->
                    val target = pendingMicTarget
                    if (target != null && (status == DeviceStatus.ON) == target) {
                        pendingMicTarget = null
                    }
                    refreshMic()
                }
            }
            launch {
                DeviceStore.shared().deviceState.currentSpeaker.collect { refreshSpeaker() }
            }
            launch {
                DeviceStore.shared().deviceState.cameraStatus.collect { refreshCamera() }
            }
            launch {
                CallKitConfig.shared.transcriptionChanged.collect { refreshTranscription() }
            }
            launch {
                CallStore.shared.observerState.allParticipants.collect { rebuildIfNeeded() }
            }
        }
    }

    private fun rebuildIfNeeded() {
        if (!isCollapsed) {
            val twoRow = isVideo || isMulti
            if (twoRow != isTwoRowLayout) {
                rebuildLayout()
            }
        }
    }

    private fun rebuildLayout() {
        removeAllViews()
        cells.clear()
        installBarBackground()
        val content = if (isCollapsed) buildCollapsedRow() else buildRegularRows()
        val contentParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        if (barBackgroundVisible) {
            contentParams.topMargin = 14.dp(context)
            contentParams.bottomMargin = barContentBottomInset
        } else {
            contentParams.topMargin = if (isCollapsed) 14.dp(context) else 0
        }
        if (isCollapsed) {
            contentParams.marginStart = 24.dp(context)
            contentParams.marginEnd = 24.dp(context)
        }
        addView(content, contentParams)
        bindCellActions()
        applyButtonVisibility()
        refreshMic()
        refreshSpeaker()
        refreshCamera()
        refreshTranscription()
    }

    private fun installBarBackground() {
        background = if (barBackgroundVisible) {
            ContextCompat.getDrawable(context, R.drawable.tuicallkit_bg_control_bar)
        } else {
            null
        }
    }

    private fun buildRegularRows(): View {
        val twoRow = isVideo || isMulti
        isTwoRowLayout = twoRow
        addCell(ButtonID.MICROPHONE, R.drawable.tuicallkit_icon_mic)
        addCell(ButtonID.SPEAKER, R.drawable.tuicallkit_icon_speaker)
        addCell(ButtonID.TRANSCRIPTION, R.drawable.tuicallkit_icon_cc)
        addCell(ButtonID.HANGUP, R.drawable.tuicallkit_icon_reject)

        val column = LinearLayout(context)
        column.orientation = LinearLayout.VERTICAL

        if (twoRow) {
            addCell(ButtonID.CAMERA, R.drawable.tuicallkit_icon_cam)
            addCell(ButtonID.MORE, R.drawable.tuicallkit_icon_more, buttonSizeDp = 52, showsLabel = false)
            addCell(ButtonID.FLIP, R.drawable.tuicallkit_icon_flip, buttonSizeDp = 52, showsLabel = false)
            cells[ButtonID.MORE]?.setGhost()
            cells[ButtonID.FLIP]?.setGhost()
            cells[ButtonID.HANGUP]?.label?.isVisible = false
            column.addView(makeRow(listOf(ButtonID.MICROPHONE, ButtonID.SPEAKER, ButtonID.CAMERA, ButtonID.TRANSCRIPTION)))
            val gap = View(context)
            column.addView(gap, LinearLayout.LayoutParams(1, ROW_GAP_DP.dp(context)))
            column.addView(makeRow(listOf(ButtonID.MORE, ButtonID.HANGUP, ButtonID.FLIP)))
        } else {
            column.addView(makeRow(listOf(ButtonID.MICROPHONE, ButtonID.SPEAKER, ButtonID.TRANSCRIPTION, ButtonID.HANGUP)))
        }
        return column
    }

    private fun buildCollapsedRow(): View {
        val size = COLLAPSED_BUTTON_SIZE_DP
        addCell(ButtonID.MICROPHONE, R.drawable.tuicallkit_icon_mic, buttonSizeDp = size, showsLabel = false)
        addCell(ButtonID.SPEAKER, R.drawable.tuicallkit_icon_speaker, buttonSizeDp = size, showsLabel = false)
        addCell(ButtonID.CAMERA, R.drawable.tuicallkit_icon_cam, buttonSizeDp = size, showsLabel = false)
        addCell(ButtonID.TRANSCRIPTION, R.drawable.tuicallkit_icon_cc, buttonSizeDp = size, showsLabel = false)
        addCell(ButtonID.HANGUP, R.drawable.tuicallkit_icon_reject, buttonSizeDp = size, showsLabel = false)

        val expandButton = ImageView(context).apply {
            setImageResource(R.drawable.tuicallkit_icon_expand)
            setColorFilter(ContextCompat.getColor(context, android.R.color.white))
            val padding = 15.dp(context)
            setPadding(padding, padding, padding, padding)
            setOnClickListener { onExpand?.invoke() }
        }
        val arrowColumn = FrameLayout(context)
        val arrowSize = size.dp(context)
        arrowColumn.addView(expandButton, LayoutParams(arrowSize, arrowSize, Gravity.CENTER))

        val row = LinearLayout(context)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.addView(arrowColumn, LinearLayout.LayoutParams(20.dp(context), arrowSize))
        row.addView(
            makeRow(listOf(ButtonID.MICROPHONE, ButtonID.SPEAKER, ButtonID.CAMERA, ButtonID.TRANSCRIPTION, ButtonID.HANGUP)),
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )
        return row
    }

    private fun addCell(id: ButtonID, iconRes: Int, buttonSizeDp: Int = 58, showsLabel: Boolean = true) {
        val cell = Cell(context)
        val size = buttonSizeDp.dp(context)
        cell.button.layoutParams = LinearLayout.LayoutParams(size, size)
        // icon 随按钮等比缩放：以 58dp 按钮 + 16dp padding 为基准，比例保持不变（对齐 iOS）。
        val paddingPx = (buttonSizeDp * BASE_ICON_PADDING_DP.toFloat() / BASE_BUTTON_SIZE_DP).dp(context)
        cell.button.setPadding(paddingPx, paddingPx, paddingPx, paddingPx)
        cell.button.setImageResource(iconRes)
        cell.label.isVisible = showsLabel
        cells[id] = cell
    }

    private fun makeRow(ids: List<ButtonID>): LinearLayout {
        val row = LinearLayout(context)
        row.orientation = LinearLayout.HORIZONTAL
        for (id in ids) {
            val cell = cells[id] ?: continue
            val slot = FrameLayout(context)
            slot.addView(
                cell.root,
                LayoutParams(CELL_WIDTH_DP.dp(context), LayoutParams.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL)
            )
            row.addView(slot, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
        return row
    }

    private fun bindCellActions() {
        cells[ButtonID.MICROPHONE]?.button?.setOnClickListener { handleMicTap() }
        cells[ButtonID.SPEAKER]?.button?.setOnClickListener { onSpeakerBubbleToggle?.invoke() }
        cells[ButtonID.TRANSCRIPTION]?.button?.setOnClickListener { handleTranscriptionTap() }
        cells[ButtonID.HANGUP]?.button?.apply {
            setBackgroundResource(R.drawable.tuicallkit_bg_control_hangup)
            setPadding(0, 0, 0, 0)
            setOnClickListener { CallLauncher.hangup() }
        }
        cells[ButtonID.CAMERA]?.button?.setOnClickListener { handleCameraTap() }
        cells[ButtonID.MORE]?.button?.setOnClickListener { onMoreBubbleToggle?.invoke() }
        cells[ButtonID.FLIP]?.button?.setOnClickListener {
            DeviceStore.shared().switchCamera(!DeviceStore.shared().deviceState.isFrontCamera.value)
        }
        cells[ButtonID.TRANSCRIPTION]?.label?.text = context.getString(R.string.tuicallkit_call_cc)
        cells[ButtonID.HANGUP]?.label?.text = context.getString(R.string.tuicallkit_call_hangup)
    }

    private fun applyButtonVisibility() {
        // 底部字幕按钮永远可见——关掉也不隐藏，态同步 enableAITranscriber。
        cells[ButtonID.TRANSCRIPTION]?.root?.isVisible = true
    }

    private fun refreshMic() {
        // 乐观更新：点击后立即反映目标态；引擎回调或 state 到达后再与真实态对齐。
        val on = pendingMicTarget ?: isMicOn
        val cell = cells[ButtonID.MICROPHONE] ?: return
        cell.setOn(on)
        cell.button.setImageResource(if (on) R.drawable.tuicallkit_icon_mic else R.drawable.tuicallkit_icon_micoff)
        cell.label.text = context.getString(if (on) R.string.tuicallkit_call_mic_on else R.string.tuicallkit_call_mic_off)
    }

    private fun refreshSpeaker() {
        val cell = cells[ButtonID.SPEAKER] ?: return
        cell.setOn(true)
        cell.button.setImageResource(R.drawable.tuicallkit_icon_device)
        cell.button.clearColorFilter()
        cell.label.text = when {
            isSpeakerOn -> context.getString(R.string.tuicallkit_call_speaker_on)
            isExternalRoute -> {
                val routeName = DeviceStore.shared().deviceState.currentSpeaker.value.deviceName
                if (routeName.contains("bluetooth", ignoreCase = true)) {
                    context.getString(R.string.tuicallkit_call_bluetooth_on)
                } else {
                    routeName
                }
            }
            else -> context.getString(R.string.tuicallkit_call_earpiece_on)
        }
    }

    private fun refreshCamera() {
        val on = isCameraOn
        val cell = cells[ButtonID.CAMERA] ?: return
        cell.setOn(on)
        cell.button.setImageResource(if (on) R.drawable.tuicallkit_icon_cam else R.drawable.tuicallkit_icon_camoff)
        cell.label.text = context.getString(if (on) R.string.tuicallkit_call_camera_on else R.string.tuicallkit_call_camera_off)
    }

    private fun refreshTranscription() {
        cells[ButtonID.TRANSCRIPTION]?.setOn(CallKitConfig.shared.enableAITranscriber)
    }

    private fun handleMicTap() {
        val currentUiOn = pendingMicTarget ?: isMicOn
        val target = !currentUiOn
        pendingMicTarget = target
        refreshMic()
        val openHandler = object : io.trtc.tuikit.atomicxcore.api.CompletionHandler {
            override fun onSuccess() {}
            override fun onFailure(code: Int, desc: String) {
                CallKitLogger.getLogger("CallControlPanel")
                    .error("openLocalMicrophone failed code=$code desc=$desc")
                pendingMicTarget = null
                refreshMic()
            }
        }
        if (target) {
            onMicPermissionRequest?.invoke {
                DeviceStore.shared().openLocalMicrophone(openHandler)
            } ?: DeviceStore.shared().openLocalMicrophone(openHandler)
        } else {
            DeviceStore.shared().closeLocalMicrophone()
        }
    }

    private fun handleCameraTap() {
        if (isCameraOn) {
            DeviceStore.shared().closeLocalCamera()
        } else {
            val open = {
                DeviceStore.shared().openLocalCamera(DeviceStore.shared().deviceState.isFrontCamera.value, object : CompletionHandler {
                    override fun onSuccess() {}
                    override fun onFailure(code: Int, desc: String) {
                        CallKitToast.show(context.getString(R.string.tuicallkit_camera_open_failed, "$code $desc"))
                    }
                })
            }
            onCameraPermissionRequest?.invoke { open() } ?: open()
        }
    }

    private fun handleTranscriptionTap() {
        CallKitConfig.shared.updateAITranscriber(!CallKitConfig.shared.enableAITranscriber)
    }
}
