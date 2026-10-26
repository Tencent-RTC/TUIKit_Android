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
import androidx.core.view.isGone
import androidx.core.view.isVisible
import com.trtc.uikit.roomkit.base.log.RoomKitLogger
import com.trtc.uikit.roomkit.base.utils.dpToPx
import com.trtc.uikit.roomkit.base.utils.getScreenHeight
import com.trtc.uikit.roomkit.base.utils.getScreenWidth
import io.trtc.tuikit.atomicxcore.api.device.DeviceStatus
import io.trtc.tuikit.atomicxcore.api.room.RoomParticipant
import io.trtc.tuikit.atomicxcore.api.view.VideoStreamType

/**
 * Draggable "picture-in-picture" tile that renders a single participant's
 * camera stream on a rounded, floating surface. Used both as the PIP tile in
 * [FocusRoomView] (2-person layout) and as the "current speaker" tile that
 * overlays the grid while someone is sharing their screen.
 */
class RoomPipView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    companion object {
        private const val TILE_SMALL_DP = 100
        private const val TILE_MIDDLE_DP = 180
        private const val EDGE_MARGIN_DP = 5
        private const val CORNER_RADIUS_DP = 16
        private const val AVATAR_SIZE_DP = 50
        private const val AVATAR_RADIUS_DP = 25
        private const val OVERLAY_MARGIN_DP = 4
        private const val TOUCH_SLOP_DP = 4
        private const val SPEAKING_VOLUME_THRESHOLD = 25
    }

    private val logger = RoomKitLogger.getLogger("RoomPipView")

    private var boundUserId: String? = null
    private var currentParticipant: RoomParticipant? = null
    private var speaking = false
    private var latestSpeakingMap: Map<String, Int> = emptyMap()
    private var cameraOn: Boolean = false
    private var initiallyPlaced: Boolean = false
    private var downRawX = 0f
    private var downRawY = 0f
    private var downLeftMargin = 0
    private var downTopMargin = 0
    private var isDragging = false

    private val cell: RoomViewVideoStreamCell = RoomViewVideoStreamCell(context).apply {
        setAvatarSize(AVATAR_SIZE_DP, AVATAR_RADIUS_DP)
        setOverlayMargin(OVERLAY_MARGIN_DP)
    }

    private val touchSlopSquaredPx: Int by lazy {
        val slop = dpToPx(TOUCH_SLOP_DP)
        slop * slop
    }

    init {
        addView(
            cell,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        )

        clipToOutline = true
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                val radius = view.dpToPx(CORNER_RADIUS_DP).toFloat()
                outline.setRoundRect(0, 0, view.width, view.height, radius)
            }
        }
        visibility = GONE
        installDragHandler()
    }

    fun bind(participant: RoomParticipant) {
        val sameUser = boundUserId == participant.userID
        val camNowOn = participant.cameraStatus == DeviceStatus.ON

        if (sameUser) {
            currentParticipant = participant
            cell.updateParticipant(participant)
            if (cameraOn != camNowOn) {
                cameraOn = camNowOn
                applyTileSize(camNowOn)
            }
            if (visibility != VISIBLE) {
                cell.setActive(true)
                visibility = VISIBLE
            }
            applySpeakingState()
            return
        }

        logger.info("bind: userID=${participant.userID}, cam=${participant.cameraStatus}")
        if (boundUserId != null) {
            cell.setActive(false)
            cell.resetBoundStream()
        }
        cell.bind(participant, VideoStreamType.CAMERA)
        cell.setActive(true)
        boundUserId = participant.userID
        currentParticipant = participant
        cameraOn = camNowOn
        applyTileSize(cameraOn)
        visibility = VISIBLE
        applySpeakingState()
    }

    fun reclaimStream() {
        if (currentParticipant == null) return
        if (visibility != VISIBLE) return
        cell.setActive(true)
    }

    fun hide() {
        if (isGone) return
        cell.setActive(false)
        cell.updateSpeakingState(false)
        speaking = false
        visibility = GONE
    }

    fun release() {
        cell.setActive(false)
        cell.updateSpeakingState(false)
        cell.resetBoundStream()
        boundUserId = null
        currentParticipant = null
        speaking = false
        latestSpeakingMap = emptyMap()
        initiallyPlaced = false
        visibility = GONE
    }

    fun updateSpeakingStates(speakingMap: Map<String, Int>) {
        latestSpeakingMap = speakingMap
        applySpeakingState()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (isVisible) {
            applyTileSize(cameraOn)
        }
    }

    private fun applySpeakingState() {
        val next = isSpeaking(currentParticipant)
        if (next != speaking) {
            speaking = next
            cell.updateSpeakingState(next)
        }
    }

    private fun isSpeaking(participant: RoomParticipant?): Boolean {
        if (participant == null) return false
        if (participant.microphoneStatus != DeviceStatus.ON) return false
        val volume = latestSpeakingMap[participant.userID] ?: 0
        return volume > SPEAKING_VOLUME_THRESHOLD
    }

    private fun applyTileSize(cameraOn: Boolean) {
        val isPortrait = resources.configuration.orientation != Configuration.ORIENTATION_LANDSCAPE
        val (widthDp, heightDp) = when {
            !cameraOn -> TILE_SMALL_DP to TILE_SMALL_DP
            isPortrait -> TILE_SMALL_DP to TILE_MIDDLE_DP
            else -> TILE_MIDDLE_DP to TILE_SMALL_DP
        }
        val newWidth = dpToPx(widthDp)
        val newHeight = dpToPx(heightDp)

        val lp = layoutParams as FrameLayout.LayoutParams
        val sizeChanged = lp.width != newWidth || lp.height != newHeight
        if (sizeChanged) {
            lp.width = newWidth
            lp.height = newHeight
        }
        lp.gravity = Gravity.TOP or Gravity.START

        if (!initiallyPlaced) {
            if (!placeAtTopRight(lp, newWidth)) {
                layoutParams = lp
                post { placeAtTopRightDeferred(newWidth) }
                return
            }
            initiallyPlaced = true
        } else if (sizeChanged) {
            clampMargins(lp, newWidth, newHeight)
        }

        layoutParams = lp
    }

    private fun placeAtTopRight(lp: FrameLayout.LayoutParams, tileWidth: Int): Boolean {
        val screenWidth = getScreenWidth(context)
        if (screenWidth <= 0) return false
        val margin = dpToPx(EDGE_MARGIN_DP)
        lp.leftMargin = (screenWidth - tileWidth - margin).coerceAtLeast(margin)
        lp.topMargin = margin
        return true
    }

    private fun placeAtTopRightDeferred(tileWidth: Int) {
        val lp = layoutParams as FrameLayout.LayoutParams
        if (placeAtTopRight(lp, tileWidth)) {
            layoutParams = lp
            initiallyPlaced = true
        }
    }

    private fun clampMargins(lp: FrameLayout.LayoutParams, tileWidth: Int, tileHeight: Int) {
        val effectiveWidth = getScreenWidth(context)
        val effectiveHeight = getScreenHeight(context)
        if (effectiveWidth <= 0 || effectiveHeight <= 0) return
        val margin = dpToPx(EDGE_MARGIN_DP)
        val maxLeft = (effectiveWidth - tileWidth - margin).coerceAtLeast(margin)
        val maxTop = (effectiveHeight - tileHeight - margin).coerceAtLeast(margin)
        lp.leftMargin = lp.leftMargin.coerceIn(margin, maxLeft)
        lp.topMargin = lp.topMargin.coerceIn(margin, maxTop)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun installDragHandler() {
        setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    val lp = view.layoutParams as FrameLayout.LayoutParams
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
                    if (!isDragging && event.actionMasked == MotionEvent.ACTION_UP) {
                        view.performClick()
                    }
                    if (isDragging) snapToHorizontalEdge()
                    isDragging = false
                    true
                }

                else -> false
            }
        }
    }

    private fun moveTo(leftMargin: Int, topMargin: Int) {
        val parent = parent as? View ?: return
        val parentWidth = parent.width
        val parentHeight = parent.height
        if (parentWidth <= 0 || parentHeight <= 0) return

        val lp = layoutParams as FrameLayout.LayoutParams
        val margin = dpToPx(EDGE_MARGIN_DP)
        val maxLeft = (parentWidth - lp.width - margin).coerceAtLeast(margin)
        val maxTop = (parentHeight - lp.height - margin).coerceAtLeast(margin)

        val clampedLeft = leftMargin.coerceIn(margin, maxLeft)
        val clampedTop = topMargin.coerceIn(margin, maxTop)
        if (lp.leftMargin == clampedLeft && lp.topMargin == clampedTop) return

        lp.leftMargin = clampedLeft
        lp.topMargin = clampedTop
        layoutParams = lp
    }

    private fun snapToHorizontalEdge() {
        val parent = parent as? View ?: return
        val parentWidth = parent.width
        if (parentWidth <= 0) return

        val lp = layoutParams as FrameLayout.LayoutParams
        val margin = dpToPx(EDGE_MARGIN_DP)
        val currentCenter = lp.leftMargin + lp.width / 2
        val targetLeft = if (currentCenter > parentWidth / 2) {
            (parentWidth - lp.width - margin).coerceAtLeast(margin)
        } else {
            margin
        }

        if (lp.leftMargin == targetLeft) return
        lp.leftMargin = targetLeft
        layoutParams = lp
    }
}
