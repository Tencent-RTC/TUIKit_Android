package io.trtc.tuikit.chat.uikit.components.messageinput.ui
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.inputmethod.EditorInfo
import android.widget.FrameLayout
import android.widget.LinearLayout
import io.trtc.tuikit.chat.uikit.components.audiorecorder.AudioRecorderResultCode
import io.trtc.tuikit.chat.uikit.components.common.EventBus
import io.trtc.tuikit.chat.uikit.components.messageinput.state.InputCoordinator
import io.trtc.tuikit.chat.uikit.components.messageinput.state.InputMode
import io.trtc.tuikit.chat.uikit.components.messageinput.state.InputModeEvent
import io.trtc.tuikit.chat.uikit.components.messageinput.state.InputSurfaceState
import io.trtc.tuikit.chat.uikit.components.messageinput.state.PanelEvent
import io.trtc.tuikit.chat.uikit.components.messageinput.state.PanelState
import io.trtc.tuikit.chat.uikit.components.messageinput.viewmodel.MessageInputViewModel

internal object MessageInputListenerBinder {
    fun bind(
        btnMore: FrameLayout,
        btnSend: FrameLayout,
        btnAudioRecord: FrameLayout,
        btnEmoji: FrameLayout,
        btnPressToTalk: AudioRecorderView,
        editText: AtomicEditText,
        inputContainer: LinearLayout,
        coordinatorProvider: () -> InputCoordinator,
        viewModelProvider: () -> MessageInputViewModel?,
        isLongPressToTalkEnabled: () -> Boolean,
        sendCurrentText: () -> Unit,
        showVoiceTranscriptionDraft: (audioPath: String, audioDurationSecond: Int, text: String?) -> Unit,
        logDebug: (String) -> Unit
    ) {
        btnMore.setOnClickListener {
            val coordinator = coordinatorProvider()
            logDebug("CLICK: btnMore | currentPanel=${coordinator.state.value.panel}")
            coordinator.dispatch(PanelEvent.RequestMorePanel)
            postInputInteractEvent()
        }

        btnSend.setOnClickListener {
            sendCurrentText()
        }

        btnAudioRecord.setOnClickListener {
            val coordinator = coordinatorProvider()
            val current = coordinator.state.value
            logDebug("CLICK: btnAudioRecord | currentMode=${current.inputMode}")
            if (current.inputMode == InputMode.VOICE) {
                coordinator.dispatch(InputModeEvent.SwitchToText)
            } else {
                coordinator.dispatch(InputModeEvent.SwitchToVoice)
            }
            postInputInteractEvent()
        }

        btnEmoji.setOnClickListener {
            val coordinator = coordinatorProvider()
            val current = coordinator.state.value
            logDebug("CLICK: btnEmoji | currentPanel=${current.panel}")
            if (current.panel == PanelState.EMOJI_PANEL) {
                coordinator.dispatch(PanelEvent.RequestKeyboard)
            } else {
                coordinator.dispatch(PanelEvent.RequestEmojiPanel)
            }
            postInputInteractEvent()
        }

        btnPressToTalk.setOnClickListener {
            val coordinator = coordinatorProvider()
            logDebug("CLICK: btnPressToTalk | currentMode=${coordinator.state.value.inputMode}")
            coordinator.dispatch(InputModeEvent.SwitchToText)
            postInputInteractEvent()
        }

        btnPressToTalk.setOnResultListener { result, releaseAction ->
            if (!result.isSuccess) return@setOnResultListener
            val audioPath = result.filePath ?: return@setOnResultListener
            val audioDurationSecond = (result.durationMs / 1000).coerceAtLeast(1)
            if (result.resultCode != AudioRecorderResultCode.SUCCESS &&
                result.resultCode != AudioRecorderResultCode.SUCCESS_EXCEED_MAX_DURATION
            ) {
                return@setOnResultListener
            }
            when (releaseAction) {
                AudioRecorderReleaseAction.SEND_AUDIO -> {
                    viewModelProvider()?.sendAudioMessage(audioPath, audioDurationSecond)
                }
                AudioRecorderReleaseAction.TRANSCRIBE -> {
                    showVoiceTranscriptionDraft(audioPath, audioDurationSecond, null)
                    coordinatorProvider().dispatch(InputModeEvent.SwitchToTextCollapsed)
                    viewModelProvider()?.convertLocalAudioToText(btnPressToTalk.context, audioPath) { text ->
                        showVoiceTranscriptionDraft(audioPath, audioDurationSecond, text)
                    }
                }
                AudioRecorderReleaseAction.CANCEL -> {
                    coordinatorProvider().dispatch(InputModeEvent.SwitchToTextCollapsed)
                }
                else -> Unit
            }
        }

        editText.setOnFocusChangeListener { _, hasFocus ->
            val coordinator = coordinatorProvider()
            val currentSurface = coordinator.state.value.surface
            logDebug("FOCUS: editText hasFocus=$hasFocus | currentSurface=$currentSurface")
            if (hasFocus && currentSurface != InputSurfaceState.KEYBOARD) {
                coordinator.dispatch(PanelEvent.RequestKeyboard)
            }
            if (hasFocus) {
                postInputInteractEvent()
            }
        }

        editText.setOnClickListener {
            val coordinator = coordinatorProvider()
            logDebug("CLICK: editText | currentSurface=${coordinator.state.value.surface}")
            if (coordinator.state.value.surface != InputSurfaceState.KEYBOARD) {
                coordinator.dispatch(PanelEvent.RequestKeyboard)
            }
            postInputInteractEvent()
        }

        editText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendCurrentText()
                true
            } else {
                false
            }
        }

        inputContainer.setOnClickListener {
            val coordinator = coordinatorProvider()
            logDebug("CLICK: inputContainer | currentSurface=${coordinator.state.value.surface}")
            if (coordinator.state.value.surface != InputSurfaceState.KEYBOARD) {
                coordinator.dispatch(PanelEvent.RequestKeyboard)
            }
            postInputInteractEvent()
        }

        TextInputVoiceRecordingTouchBridge(
            editText = editText,
            inputContainer = inputContainer,
            btnPressToTalk = btnPressToTalk,
            isLongPressToTalkEnabled = isLongPressToTalkEnabled,
            hasInputText = { editText.text?.isNotEmpty() == true },
            coordinatorProvider = coordinatorProvider,
            logDebug = logDebug,
        ).attach()
    }

    fun postInputInteractEvent() {
        EventBus.post(
            mapOf(
                "source" to "MessageInput",
                "event" to "onInputInteract"
            )
        )
    }

    private class TextInputVoiceRecordingTouchBridge(
        private val editText: AtomicEditText,
        private val inputContainer: LinearLayout,
        private val btnPressToTalk: AudioRecorderView,
        private val isLongPressToTalkEnabled: () -> Boolean,
        private val hasInputText: () -> Boolean,
        private val coordinatorProvider: () -> InputCoordinator,
        private val logDebug: (String) -> Unit,
    ) {
        private var activeView: View? = null
        private var isArmed = false
        private var isRecording = false

        private val startRecordingRunnable = Runnable {
            if (!isArmed || !canArmRecording()) {
                return@Runnable
            }
            isArmed = false
            val coordinator = coordinatorProvider()
            logDebug("LONG_PRESS: text input starts recording | surface=${coordinator.state.value.surface}")
            coordinator.dispatch(PanelEvent.RequestCollapse)
            editText.clearFocus()
            isRecording = btnPressToTalk.startRecordingFromLongPress(inputContainer)
            if (isRecording) {
                disallowAncestorsIntercept(activeView, true)
                postInputInteractEvent()
            }
        }

        fun attach() {
            val listener = View.OnTouchListener { view, event -> handleTouch(view, event) }
            editText.setOnTouchListener(listener)
            inputContainer.setOnTouchListener(listener)
        }

        private fun handleTouch(view: View, event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    if (!canArmRecording()) {
                        return false
                    }
                    activeView = view
                    isArmed = true
                    isRecording = false
                    view.postDelayed(startRecordingRunnable, ViewConfiguration.getLongPressTimeout().toLong())
                    disallowAncestorsIntercept(view, true)
                    return true
                }

                MotionEvent.ACTION_MOVE -> {
                    if (isRecording) {
                        btnPressToTalk.updateRecordingTouch(event.rawX, event.rawY)
                        return true
                    }
                    return isArmed
                }

                MotionEvent.ACTION_UP -> {
                    view.removeCallbacks(startRecordingRunnable)
                    disallowAncestorsIntercept(view, false)
                    if (isRecording) {
                        btnPressToTalk.finishRecordingTouch(event.rawX, event.rawY)
                        reset()
                        return true
                    }
                    if (isArmed) {
                        reset()
                        requestKeyboard()
                        return true
                    }
                    reset()
                    return false
                }

                MotionEvent.ACTION_CANCEL -> {
                    view.removeCallbacks(startRecordingRunnable)
                    disallowAncestorsIntercept(view, false)
                    if (isRecording) {
                        btnPressToTalk.cancelRecording()
                    }
                    reset()
                    return true
                }
            }
            return false
        }

        private fun requestKeyboard() {
            val coordinator = coordinatorProvider()
            logDebug("CLICK: text input bridge | currentSurface=${coordinator.state.value.surface}")
            editText.requestFocus()
            coordinator.dispatch(PanelEvent.RequestKeyboard)
            postInputInteractEvent()
        }

        private fun canArmRecording(): Boolean {
            val state = coordinatorProvider().state.value
            return MessageInputLongPressRecordingPolicy.shouldArmRecording(
                inputMode = state.inputMode,
                surface = state.surface,
                isLongPressToTalkEnabled = isLongPressToTalkEnabled(),
                hasInputText = hasInputText(),
            )
        }

        private fun reset() {
            activeView = null
            isArmed = false
            isRecording = false
        }

        private fun disallowAncestorsIntercept(view: View?, disallow: Boolean) {
            var parent = view?.parent
            while (parent != null) {
                parent.requestDisallowInterceptTouchEvent(disallow)
                parent = parent.parent
            }
        }
    }
}
