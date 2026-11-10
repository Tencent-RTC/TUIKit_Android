package com.trtc.uikit.roomkit.inroom.views

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Space
import com.trtc.uikit.roomkit.R
import com.trtc.uikit.roomkit.base.popup.RoomPopup
import com.trtc.uikit.roomkit.base.util.applyBottomWindowInsets
import com.trtc.uikit.roomkit.base.util.dp
import com.trtc.uikit.roomkit.inroom.features.device.camerabutton.CameraButton
import com.trtc.uikit.roomkit.inroom.features.device.micbutton.MicButton
import com.trtc.uikit.roomkit.inroom.features.device.speakerbutton.SpeakerButton
import com.trtc.uikit.roomkit.inroom.features.participants.participantbutton.ParticipantButton
import com.trtc.uikit.roomkit.inroom.features.recording.cloudrecordingbutton.CloudRecordingButton
import com.trtc.uikit.roomkit.inroom.features.screenshare.screensharebutton.ScreenShareButton
import com.trtc.uikit.roomkit.inroom.features.setting.SettingsButton
import io.trtc.tuikit.atomicxcore.api.room.RoomParticipantStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

interface TitleDisplaying {
    var showsTitle: Boolean
}

class MeetingToolBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    private val container: LinearLayout
    private val micButton = MicButton(context)
    private val camButton = CameraButton(context)
    private val speakerButton = SpeakerButton(context)
    private val participantButton = ParticipantButton(context)
    private val screenShareButton = ScreenShareButton(context)
    private val recordingButton = CloudRecordingButton(context)
    private val settingsButton = SettingsButton(context)
    private val moreButton: View

    private var featureButtons: List<View> = emptyList()
    private var roomID = ""
    private var morePopup: RoomPopup? = null
    private var availabilityJob: Job? = null

    private val needsMoreButton: Boolean
        get() = featureButtons.size > MAX_BUTTON_COUNT

    private val overflowButtons: List<View>
        get() = if (needsMoreButton) featureButtons.drop(MAX_BUTTON_COUNT - 1) else emptyList()

    init {
        LayoutInflater.from(context).inflate(R.layout.roomkit_meeting_tool_bar, this, true)
        clipChildren = false
        container = findViewById(R.id.toolbar_container)
        container.clipChildren = false
        moreButton = LayoutInflater.from(context).inflate(R.layout.roomkit_more_button, container, false)
        setupMoreButton()
        applyBottomWindowInsets()
    }

    fun init(roomID: String) {
        this.roomID = roomID
        micButton.init(roomID)
        camButton.init(roomID)
        participantButton.init(roomID)
        screenShareButton.bind(roomID)
        recordingButton.bind(roomID)
        featureButtons = buildFeatureButtons()
        applyButtons()
        observeFeatureAvailability()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupMoreButton() {
        moreButton.setOnClickListener { showMoreMenu() }
        moreButton.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> view.animate()
                    .setDuration(PRESSED_DURATION.toLong()).scaleX(PRESSED_SCALE).scaleY(PRESSED_SCALE)
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> view.animate()
                    .setDuration(PRESSED_DURATION.toLong()).scaleX(1f).scaleY(1f)
            }
            false
        }
    }

    private fun buildFeatureButtons(): List<View> {
        val buttons = mutableListOf<View>(
            micButton,
            camButton,
            speakerButton,
            participantButton,
            screenShareButton,
        )
        if (recordingButton.isAvailable()) {
            buttons.add(recordingButton)
        }
        buttons.add(settingsButton)
        return buttons
    }

    private fun observeFeatureAvailability() {
        availabilityJob?.cancel()
        availabilityJob = CoroutineScope(Dispatchers.Main).launch {
            RoomParticipantStore.create(roomID).state.localParticipant.collect {
                val buttons = buildFeatureButtons()
                if (buttons.size == featureButtons.size) return@collect
                featureButtons = buttons
                applyButtons()
            }
        }
    }

    private fun applyButtons() {
        dismissMoreMenu()
        container.removeAllViews()
        featureButtons.forEach {
            (it as? TitleDisplaying)?.showsTitle = false
            applyMenuIconSize(it, menu = false)
        }
        val visible = if (needsMoreButton) featureButtons.take(MAX_BUTTON_COUNT - 1) else featureButtons
        val buttons = visible + if (needsMoreButton) listOf(moreButton) else emptyList()
        buttons.forEachIndexed { index, view ->
            (view.parent as? ViewGroup)?.takeIf { it !== container }?.removeView(view)
            container.addView(
                view,
                LinearLayout.LayoutParams(context.dp(BUTTON_SIZE_DP), context.dp(BUTTON_SIZE_DP)).apply {
                    if (index < buttons.lastIndex) marginEnd = context.dp(BUTTON_SPACING_DP)
                },
            )
        }
    }

    private fun showMoreMenu() {
        if (roomID.isBlank() || morePopup != null) return
        val overflow = overflowButtons
        if (overflow.isEmpty()) return

        val content = LayoutInflater.from(context).inflate(R.layout.roomkit_more_menu, this, false) as LinearLayout
        overflow.forEach { view ->
            (view as? TitleDisplaying)?.showsTitle = true
            applyMenuIconSize(view, menu = true)
            (view.parent as? ViewGroup)?.removeView(view)
            val cell = FrameLayout(context).apply {
                setPadding(context.dp(2), context.dp(10), context.dp(2), context.dp(10))
            }
            cell.addView(
                view,
                LayoutParams(
                    LayoutParams.WRAP_CONTENT,
                    LayoutParams.WRAP_CONTENT,
                ).apply { gravity = Gravity.START },
            )
            content.addView(cell, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
        repeat((MENU_COLUMN_COUNT - overflow.size).coerceAtLeast(0)) {
            content.addView(Space(context), LinearLayout.LayoutParams(0, 1, 1f))
        }

        val popup = RoomPopup(context)
        popup.setContentView(content, RoomPopup.ContentHeight.AUTO)
        popup.onDismiss = { morePopup = null }
        screenShareButton.onRequestDismiss = { dismissMoreMenu() }
        recordingButton.onRequestDismiss = { dismissMoreMenu() }
        settingsButton.onRequestDismiss = { dismissMoreMenu() }
        morePopup = popup
        popup.show()
    }

    private fun applyMenuIconSize(view: View, menu: Boolean) {
        val root = (view as? ViewGroup)?.getChildAt(0) as? ViewGroup ?: return
        val iconBox = root.getChildAt(0) ?: return
        val box = context.dp(if (menu) MENU_ICON_BOX_DP else BUTTON_SIZE_DP)
        iconBox.layoutParams = iconBox.layoutParams.apply { width = box; height = box }
        val icon = view.findViewById<View>(R.id.icon) ?: return
        val size = context.dp(if (menu) MENU_ICON_DP else TOOLBAR_ICON_DP)
        icon.layoutParams = icon.layoutParams.apply { width = size; height = size }
    }

    private fun dismissMoreMenu() {
        morePopup?.dismiss()
        morePopup = null
    }

    override fun onDetachedFromWindow() {
        availabilityJob?.cancel()
        availabilityJob = null
        dismissMoreMenu()
        super.onDetachedFromWindow()
    }

    private companion object {
        const val MAX_BUTTON_COUNT = 6
        const val BUTTON_SIZE_DP = 44
        const val BUTTON_SPACING_DP = 12
        const val TOOLBAR_ICON_DP = 20
        const val MENU_COLUMN_COUNT = 4
        const val MENU_ICON_BOX_DP = 46
        const val MENU_ICON_DP = 21
        const val PRESSED_SCALE = 0.92f
        const val PRESSED_DURATION = 80
    }
}
