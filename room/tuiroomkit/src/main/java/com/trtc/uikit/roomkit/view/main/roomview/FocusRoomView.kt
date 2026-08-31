package com.trtc.uikit.roomkit.view.main.roomview

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.graphics.Outline
import android.util.AttributeSet
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.constraintlayout.utils.widget.ImageFilterView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.trtc.uikit.roomkit.R
import com.trtc.uikit.roomkit.base.log.RoomKitLogger
import com.trtc.uikit.roomkit.base.utils.dpToPx
import io.trtc.tuikit.atomicx.common.imageloader.ImageLoader
import io.trtc.tuikit.atomicxcore.api.device.DeviceStatus
import io.trtc.tuikit.atomicxcore.api.login.LoginStore
import io.trtc.tuikit.atomicxcore.api.room.RoomParticipant
import io.trtc.tuikit.atomicxcore.api.view.FillMode
import io.trtc.tuikit.atomicxcore.api.view.RoomParticipantView
import io.trtc.tuikit.atomicxcore.api.view.VideoStreamType
import kotlin.math.abs

class FocusRoomView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    companion object {
        private const val PIP_SMALL_DP = 100
        private const val PIP_MIDDLE_DP = 180
        private const val PIP_MARGIN_DP = 5
        private const val PIP_CORNER_RADIUS_DP = 16
        private const val MAIN_AVATAR_SIZE_DP = 64
        private const val MAIN_AVATAR_RADIUS_DP = 32
        private const val PIP_AVATAR_SIZE_DP = 50
        private const val PIP_AVATAR_RADIUS_DP = 25
        private const val TOUCH_SLOP_DP = 4
        private const val SPEAKING_VOLUME_THRESHOLD = 25
        private const val MAIN_OVERLAY_MARGIN_DP = 8
        private const val PIP_OVERLAY_MARGIN_DP = 4
    }

    private val logger = RoomKitLogger.getLogger("FocusRoomView")

    private var boundMainUserId: String? = null
    private var boundPipUserId: String? = null
    private var mainSpeaking: Boolean = false
    private var pipSpeaking: Boolean = false
    private var mainParticipant: RoomParticipant? = null
    private var pipParticipant: RoomParticipant? = null
    private var latestSpeakingMap: Map<String, Int> = emptyMap()
    private var pipCameraOn: Boolean = false
    private var pipInitiallyPlaced: Boolean = false
    private var downRawX = 0f
    private var downRawY = 0f
    private var downLeftMargin = 0
    private var downTopMargin = 0
    private var isDragging = false

    private val mainView: RoomParticipantView = RoomParticipantView(context).apply {
        setFillMode(FillMode.FILL)
        setBackgroundColor(ContextCompat.getColor(context, R.color.roomkit_color_video_item))
    }

    private val mainAvatar: ImageFilterView = ImageFilterView(context).apply {
        setImageResource(R.drawable.roomkit_ic_default_avatar)
        scaleType = ImageView.ScaleType.CENTER_CROP
        round = dpToPx(MAIN_AVATAR_RADIUS_DP).toFloat()
        visibility = GONE
    }

    private val mainSpeakingBorder: View = View(context).apply {
        background = ContextCompat.getDrawable(context, R.drawable.roomkit_bg_video_cell_speaking)
        visibility = INVISIBLE
    }

    private val mainNameOverlay: RoomVideoNameOverlayView = RoomVideoNameOverlayView(context).apply {
        setStreamType(VideoStreamType.CAMERA)
    }

    private val pipView: RoomParticipantView = RoomParticipantView(context).apply {
        setFillMode(FillMode.FILL)
        setBackgroundColor(ContextCompat.getColor(context, R.color.roomkit_color_video_item))
    }

    private val pipAvatar: ImageFilterView = ImageFilterView(context).apply {
        setImageResource(R.drawable.roomkit_ic_default_avatar)
        scaleType = ImageView.ScaleType.CENTER_CROP
        round = dpToPx(PIP_AVATAR_RADIUS_DP).toFloat()
        visibility = GONE
    }

    private val pipSpeakingBorder: View = View(context).apply {
        background = ContextCompat.getDrawable(context, R.drawable.roomkit_bg_video_cell_speaking)
        visibility = INVISIBLE
    }

    private val pipNameOverlay: RoomVideoNameOverlayView = RoomVideoNameOverlayView(context).apply {
        setStreamType(VideoStreamType.CAMERA)
    }

    private val pipContainer: FrameLayout = FrameLayout(context).apply {
        clipToOutline = true
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                val radius = view.dpToPx(PIP_CORNER_RADIUS_DP).toFloat()
                outline.setRoundRect(0, 0, view.width, view.height, radius)
            }
        }
        visibility = GONE
    }


    private val touchSlopSquaredPx: Int by lazy {
        val slop = dpToPx(TOUCH_SLOP_DP)
        slop * slop
    }

    init {
        addView(
            mainView,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        )
        addView(
            mainAvatar,
            LayoutParams(dpToPx(MAIN_AVATAR_SIZE_DP), dpToPx(MAIN_AVATAR_SIZE_DP)).apply {
                gravity = Gravity.CENTER
            }
        )
        addView(
            mainSpeakingBorder,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        )
        addView(
            mainNameOverlay,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT).apply {
                val margin = dpToPx(MAIN_OVERLAY_MARGIN_DP)
                setMargins(margin, margin, margin, margin)
            }
        )

        pipContainer.addView(
            pipView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        pipContainer.addView(
            pipAvatar,
            FrameLayout.LayoutParams(
                dpToPx(PIP_AVATAR_SIZE_DP),
                dpToPx(PIP_AVATAR_SIZE_DP)
            ).apply {
                gravity = Gravity.CENTER
            }
        )
        pipContainer.addView(
            pipSpeakingBorder,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        pipContainer.addView(
            pipNameOverlay,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            ).apply {
                val margin = dpToPx(PIP_OVERLAY_MARGIN_DP)
                setMargins(margin, margin, margin, margin)
            }
        )
        addView(
            pipContainer,
            LayoutParams(dpToPx(PIP_SMALL_DP), dpToPx(PIP_SMALL_DP)).apply {
                gravity = Gravity.TOP or Gravity.START
            }
        )

        installPipDragHandler()
    }

    fun bind(participants: List<RoomParticipant>) {
        logger.info(
            "bind called: size=${participants.size}, " +
                    "userIDs=${participants.map { it.userID }}"
        )
        when (participants.size) {
            0 -> {
                logger.warn("bind called with empty list, ignored")
                return
            }

            1 -> bindSolo(participants[0])
            else -> bindPair(participants)
        }
    }

    fun release() {
        mainView.setActive(false)
        pipView.setActive(false)
        mainAvatar.visibility = GONE
        pipAvatar.visibility = GONE
        boundMainUserId = null
        boundPipUserId = null
        mainSpeakingBorder.visibility = INVISIBLE
        pipSpeakingBorder.visibility = INVISIBLE
        mainSpeaking = false
        pipSpeaking = false
        mainParticipant = null
        pipParticipant = null
        latestSpeakingMap = emptyMap()
        pipInitiallyPlaced = false
    }

    fun updateSpeakingStates(speakingMap: Map<String, Int>) {
        latestSpeakingMap = speakingMap
        applySpeakingStates()
    }

    private fun applySpeakingStates() {
        val nextMainSpeaking = isSpeaking(mainParticipant)
        if (nextMainSpeaking != mainSpeaking) {
            mainSpeaking = nextMainSpeaking
            mainSpeakingBorder.visibility = if (nextMainSpeaking) VISIBLE else INVISIBLE
        }

        val pipVisible = pipContainer.isVisible
        val nextPipSpeaking = pipVisible && isSpeaking(pipParticipant)
        if (nextPipSpeaking != pipSpeaking) {
            pipSpeaking = nextPipSpeaking
            pipSpeakingBorder.visibility = if (nextPipSpeaking) VISIBLE else INVISIBLE
        }
    }

    private fun isSpeaking(participant: RoomParticipant?): Boolean {
        if (participant == null) return false
        if (participant.microphoneStatus != DeviceStatus.ON) return false
        val volume = latestSpeakingMap[participant.userID] ?: 0
        return volume > SPEAKING_VOLUME_THRESHOLD
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (pipContainer.isVisible) {
            applyPipSize(pipCameraOn)
        }
    }

    private fun bindSolo(participant: RoomParticipant) {
        bindRenderer(mainView, participant, isMain = true)
        updateAvatar(mainAvatar, participant)
        mainNameOverlay.updateParticipant(participant)
        mainParticipant = participant
        if (boundPipUserId != null) {
            pipView.setActive(false)
            boundPipUserId = null
        }
        pipContainer.visibility = GONE
        pipAvatar.visibility = GONE
        pipParticipant = null
        pipSpeaking = false
        pipSpeakingBorder.visibility = INVISIBLE
        applySpeakingStates()
    }

    private fun bindPair(participants: List<RoomParticipant>) {
        val localUserId = LoginStore.shared.loginState.loginUserInfo.value?.userID
        val local = participants.firstOrNull { it.userID == localUserId }
        val remote = participants.firstOrNull { it.userID != localUserId }

        val (main, pip) = when {
            local != null && remote != null -> remote to local
            else -> participants[0] to participants[1]
        }

        logger.info(
            "bindPair: localUserId=$localUserId, " +
                    "main=${main.userID}(cam=${main.cameraStatus}), " +
                    "pip=${pip.userID}(cam=${pip.cameraStatus})"
        )

        bindRenderer(mainView, main, isMain = true)
        bindRenderer(pipView, pip, isMain = false)
        updateAvatar(mainAvatar, main)
        updateAvatar(pipAvatar, pip)
        mainNameOverlay.updateParticipant(main)
        pipNameOverlay.updateParticipant(pip)
        mainParticipant = main
        pipParticipant = pip
        pipCameraOn = pip.cameraStatus == DeviceStatus.ON
        applyPipSize(pipCameraOn)
        pipContainer.visibility = VISIBLE
        applySpeakingStates()
    }

    private fun applyPipSize(cameraOn: Boolean) {
        val isPortrait = resources.configuration.orientation != Configuration.ORIENTATION_LANDSCAPE
        val (widthDp, heightDp) = when {
            !cameraOn -> PIP_SMALL_DP to PIP_SMALL_DP
            isPortrait -> PIP_SMALL_DP to PIP_MIDDLE_DP
            else -> PIP_MIDDLE_DP to PIP_SMALL_DP
        }
        val newWidth = dpToPx(widthDp)
        val newHeight = dpToPx(heightDp)

        val lp = pipContainer.layoutParams as LayoutParams
        val sizeChanged = lp.width != newWidth || lp.height != newHeight
        if (sizeChanged) {
            lp.width = newWidth
            lp.height = newHeight
        }

        if (!pipInitiallyPlaced) {
            if (!placePipAtTopRight(lp, newWidth)) {
                pipContainer.layoutParams = lp
                pipContainer.post { placePipAtTopRightDeferred(newWidth) }
                return
            }
            pipInitiallyPlaced = true
        } else if (sizeChanged) {
            clampPipMargins(lp, newWidth, newHeight)
        }

        pipContainer.layoutParams = lp
    }

    private fun placePipAtTopRight(lp: LayoutParams, tileWidth: Int): Boolean {
        val parentWidth = width
        if (parentWidth <= 0) return false
        val margin = dpToPx(PIP_MARGIN_DP)
        lp.leftMargin = (parentWidth - tileWidth - margin).coerceAtLeast(margin)
        lp.topMargin = margin
        return true
    }

    private fun placePipAtTopRightDeferred(tileWidth: Int) {
        val lp = pipContainer.layoutParams as LayoutParams
        if (placePipAtTopRight(lp, tileWidth)) {
            pipContainer.layoutParams = lp
            pipInitiallyPlaced = true
        }
    }

    private fun clampPipMargins(lp: LayoutParams, tileWidth: Int, tileHeight: Int) {
        val parentWidth = width
        val parentHeight = height
        if (parentWidth <= 0 || parentHeight <= 0) return
        val margin = dpToPx(PIP_MARGIN_DP)
        val maxLeft = (parentWidth - tileWidth - margin).coerceAtLeast(margin)
        val maxTop = (parentHeight - tileHeight - margin).coerceAtLeast(margin)
        lp.leftMargin = lp.leftMargin.coerceIn(margin, maxLeft)
        lp.topMargin = lp.topMargin.coerceIn(margin, maxTop)
    }

    private fun bindRenderer(view: RoomParticipantView, participant: RoomParticipant, isMain: Boolean) {
        val boundId = if (isMain) boundMainUserId else boundPipUserId
        if (boundId == participant.userID) {
            view.updateParticipant(participant)
        } else {
            view.init(VideoStreamType.CAMERA, participant)
            if (isMain) boundMainUserId = participant.userID else boundPipUserId = participant.userID
        }
        view.setActive(true)
    }

    private fun updateAvatar(avatar: ImageFilterView, participant: RoomParticipant) {
        if (participant.cameraStatus == DeviceStatus.ON) {
            avatar.visibility = GONE
            return
        }
        if (participant.avatarURL.isEmpty()) {
            avatar.setImageResource(R.drawable.roomkit_ic_default_avatar)
        } else {
            ImageLoader.load(
                avatar.context,
                avatar,
                participant.avatarURL,
                R.drawable.roomkit_ic_default_avatar
            )
        }
        avatar.visibility = VISIBLE
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun installPipDragHandler() {
        pipContainer.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    val lp = view.layoutParams as LayoutParams
                    downLeftMargin = lp.leftMargin
                    downTopMargin = lp.topMargin
                    isDragging = false
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dxFromDown = event.rawX - downRawX
                    val dyFromDown = event.rawY - downRawY
                    if (!isDragging) {
                        val distSq = (dxFromDown * dxFromDown + dyFromDown * dyFromDown).toInt()
                        if (distSq < touchSlopSquaredPx) return@setOnTouchListener true
                        isDragging = true
                    }
                    moveTo(
                        (downLeftMargin + dxFromDown).toInt(),
                        (downTopMargin + dyFromDown).toInt()
                    )
                    true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (!isDragging &&
                        event.actionMasked == MotionEvent.ACTION_UP &&
                        abs(event.rawX - downRawX) < 1f &&
                        abs(event.rawY - downRawY) < 1f
                    ) {
                        view.performClick()
                    }
                    if (isDragging) snapPipToHorizontalEdge()
                    isDragging = false
                    true
                }

                else -> false
            }
        }
    }

    private fun moveTo(leftMargin: Int, topMargin: Int) {
        val parentWidth = width
        val parentHeight = height
        if (parentWidth <= 0 || parentHeight <= 0) return

        val lp = pipContainer.layoutParams as LayoutParams
        val margin = dpToPx(PIP_MARGIN_DP)
        val maxLeft = (parentWidth - lp.width - margin).coerceAtLeast(margin)
        val maxTop = (parentHeight - lp.height - margin).coerceAtLeast(margin)

        val clampedLeft = leftMargin.coerceIn(margin, maxLeft)
        val clampedTop = topMargin.coerceIn(margin, maxTop)
        if (lp.leftMargin == clampedLeft && lp.topMargin == clampedTop) return

        lp.leftMargin = clampedLeft
        lp.topMargin = clampedTop
        pipContainer.layoutParams = lp
    }

    private fun snapPipToHorizontalEdge() {
        val parentWidth = width
        if (parentWidth <= 0) return

        val lp = pipContainer.layoutParams as LayoutParams
        val margin = dpToPx(PIP_MARGIN_DP)
        val currentCenter = lp.leftMargin + lp.width / 2
        val targetLeft = if (currentCenter > parentWidth / 2) {
            (parentWidth - lp.width - margin).coerceAtLeast(margin)
        } else {
            margin
        }

        if (lp.leftMargin == targetLeft) return
        lp.leftMargin = targetLeft
        pipContainer.layoutParams = lp
    }
}
