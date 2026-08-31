package com.trtc.uikit.roomkit.view.main.roomview

import android.content.Context
import android.content.res.Configuration
import android.os.SystemClock
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import com.trtc.uikit.roomkit.R
import com.trtc.uikit.roomkit.base.log.RoomKitLogger
import com.trtc.uikit.roomkit.base.ui.BaseView
import io.trtc.tuikit.atomicxcore.api.device.DeviceStatus
import io.trtc.tuikit.atomicxcore.api.login.LoginStore
import io.trtc.tuikit.atomicxcore.api.room.RoomParticipant
import io.trtc.tuikit.atomicxcore.api.room.RoomParticipantStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Top-level dispatcher for the standard room video layout.
 *
 * Owns the [RoomParticipantStore] subscription and turns the raw state
 * (participant list + screen-share participant + speaking users) into a
 * [LayoutMode] via [resolveLayoutMode]. Based on the resolved mode it shows
 * exactly one of two child views and pushes the relevant data into it:
 */
class StandardRoomView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : BaseView(context, attrs, defStyleAttr) {

    companion object {
        private const val SPEAKING_VOLUME_THRESHOLD = 25
        private const val PIP_SWITCH_COOLDOWN_MS = 5_000L
    }

    internal enum class LayoutMode { EMPTY, FOCUS, GRID }

    private val logger = RoomKitLogger.getLogger("StandardRoomView")

    private val focusView: FocusRoomView
    private val gridView: GridRoomView
    private val screenSharePip: RoomPipView
    private var subscribeJob: Job? = null
    private var pendingUpdateJob: Job? = null
    private var participantStore: RoomParticipantStore? = null
    private var participants: List<RoomParticipant> = emptyList()
    private var screenShareParticipant: RoomParticipant? = null
    private var latestSpeakingMap: Map<String, Int> = emptyMap()
    private var currentMode: LayoutMode = LayoutMode.EMPTY
    private var currentPipUserId: String? = null
    private var lastPipSwitchTs: Long = 0L
    private var currentGridPageIndex: Int = 0
    private var isFirstUpdate = true

    var onOrientationSwitchClick: (() -> Unit)? = null
        set(value) {
            field = value
            gridView.onOrientationSwitchClick = value
        }

    init {
        LayoutInflater.from(context).inflate(R.layout.roomkit_standard_room_view, this)
        focusView = findViewById(R.id.view_focus_room)
        gridView = findViewById(R.id.view_grid_room)
        screenSharePip = findViewById(R.id.view_screen_share_pip)

        // Track the grid's visible page so the screen-share PIP can hide
        // itself when the user swipes off page 0 (the full-screen share) to
        // the paged camera grid on page 1+.
        gridView.onPageIndexChanged = { pageIndex ->
            if (currentGridPageIndex != pageIndex) {
                logger.info("grid page changed: $currentGridPageIndex -> $pageIndex")
                currentGridPageIndex = pageIndex
                updateScreenSharePip()
            }
        }

        gridView.onVisibleItemsUpdated = {
            if (isPipShown() && currentPipUserId != null) {
                screenSharePip.reclaimStream()
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        screenSharePip.release()
        currentPipUserId = null
        lastPipSwitchTs = 0L
        updateScreenSharePip()
    }

    public override fun init(roomID: String) {
        super.init(roomID)
    }

    override fun initStore(roomID: String) {
        participantStore = RoomParticipantStore.create(roomID)
    }

    override fun addObserver() {
        val store = participantStore ?: return

        subscribeJob?.cancel()
        subscribeJob = CoroutineScope(Dispatchers.Main).launch {
            launch {
                store.state.participantList.collect { list ->
                    logger.info("participantList changed, size=${list.size}")
                    participants = list
                    scheduleUpdate()
                }
            }

            launch {
                store.state.participantWithScreen.collect { screenParticipant ->
                    logger.info("participantWithScreen changed: userID=${screenParticipant?.userID}")
                    screenShareParticipant = screenParticipant
                    scheduleUpdate()
                }
            }

            launch {
                store.state.speakingUsers.collect { speakingMap ->
                    latestSpeakingMap = speakingMap
                    dispatchSpeakingUpdate()
                }
            }
        }
    }

    override fun removeObserver() {
        focusView.release()
        gridView.release()
        screenSharePip.release()
        pendingUpdateJob?.cancel()
        pendingUpdateJob = null
        subscribeJob?.cancel()
        subscribeJob = null
        isFirstUpdate = true
        currentMode = LayoutMode.EMPTY
        currentPipUserId = null
        lastPipSwitchTs = 0L
        currentGridPageIndex = 0
    }

    /**
     * Schedule a full rebuild. Immediate on first update or when the resolved
     * mode changes; otherwise 250ms-debounced to coalesce bursts of updates.
     *
     * Screen-share start/end always hits the fast path because it either
     * flips the top-level mode (1-2 participants: FOCUS <-> GRID) or flips
     * the grid's first tile between camera and SCREEN — both cases would be
     * visibly laggy with a 250ms debounce.
     */
    private fun scheduleUpdate() {
        val hasData = participants.isNotEmpty() || screenShareParticipant != null
        val nextMode = resolveLayoutMode(participants, screenShareParticipant)
        val modeChanged = nextMode != currentMode

        if ((isFirstUpdate && hasData) || modeChanged) {
            isFirstUpdate = false
            pendingUpdateJob?.cancel()
            pendingUpdateJob = null
            applyUpdate(nextMode)
        } else {
            pendingUpdateJob?.cancel()
            pendingUpdateJob = CoroutineScope(Dispatchers.Main).launch {
                delay(250)
                applyUpdate(resolveLayoutMode(participants, screenShareParticipant))
            }
        }
    }

    private fun resolveLayoutMode(
        participants: List<RoomParticipant>,
        screenShareParticipant: RoomParticipant?
    ): LayoutMode {
        if (screenShareParticipant != null) return LayoutMode.GRID
        return when (participants.size) {
            0 -> LayoutMode.EMPTY
            1, 2 -> LayoutMode.FOCUS
            else -> LayoutMode.GRID
        }
    }

    private fun applyUpdate(nextMode: LayoutMode) {
        val modeChanged = nextMode != currentMode
        if (modeChanged) {
            logger.info("mode changed: $currentMode -> $nextMode")
            notifyPreviousModeHidden(currentMode)
            currentMode = nextMode
            updateChildVisibility(nextMode)
            if (nextMode == LayoutMode.GRID) {
                currentGridPageIndex = 0
            }
        }
        dispatchBind(nextMode)
        updateScreenSharePip()
    }

    private fun updateChildVisibility(mode: LayoutMode) {
        focusView.visibility = if (mode == LayoutMode.FOCUS) VISIBLE else GONE
        gridView.visibility = if (mode == LayoutMode.GRID) VISIBLE else GONE
    }

    private fun notifyPreviousModeHidden(prevMode: LayoutMode) {
        when (prevMode) {
            LayoutMode.FOCUS -> focusView.release()
            LayoutMode.GRID -> gridView.release()
            LayoutMode.EMPTY -> Unit
        }
    }

    private fun dispatchBind(mode: LayoutMode) {
        when (mode) {
            LayoutMode.EMPTY -> Unit
            LayoutMode.FOCUS -> {
                focusView.bind(participants)
                focusView.updateSpeakingStates(latestSpeakingMap)
            }

            LayoutMode.GRID -> {
                gridView.bind(participants, screenShareParticipant)
                gridView.updateSpeakingStates(latestSpeakingMap)
            }
        }
    }

    private fun dispatchSpeakingUpdate() {
        when (currentMode) {
            LayoutMode.FOCUS -> focusView.updateSpeakingStates(latestSpeakingMap)
            LayoutMode.GRID -> {
                gridView.updateSpeakingStates(latestSpeakingMap)
                val pipVisible = isPipShown()
                val prevId = currentPipUserId
                val inPipSwitchCooldown = pipVisible && prevId != null && isValidPipCandidate(prevId)
                        && SystemClock.uptimeMillis() - lastPipSwitchTs < PIP_SWITCH_COOLDOWN_MS
                if (!inPipSwitchCooldown) {
                    val nextPipUserId = if (pipVisible) {
                        resolveScreenSharePipParticipant()?.userID
                    } else {
                        null
                    }
                    if (nextPipUserId != currentPipUserId) {
                        updateScreenSharePip()
                    }
                }
                screenSharePip.updateSpeakingStates(latestSpeakingMap)
            }

            else -> Unit
        }
    }

    private fun isPipShown(): Boolean {
        return currentMode == LayoutMode.GRID
                && screenShareParticipant != null
                && currentGridPageIndex == 0
    }

    private fun updateScreenSharePip() {
        val sharing = currentMode == LayoutMode.GRID && screenShareParticipant != null

        if (!sharing) {
            releasePipBinding()
            return
        }

        if (currentGridPageIndex != 0) {
            if (currentPipUserId != null) screenSharePip.hide()
            return
        }

        val next = resolveScreenSharePipParticipant()
        if (next == null) {
            releasePipBinding()
            return
        }
        if (isInPipSwitchCooldown(next)) return
        bindPip(next)
    }

    private fun releasePipBinding() {
        if (screenSharePip.visibility != View.VISIBLE && currentPipUserId == null) return
        screenSharePip.release()
        currentPipUserId = null
        lastPipSwitchTs = 0L
    }

    private fun isInPipSwitchCooldown(next: RoomParticipant): Boolean {
        val prevId = currentPipUserId ?: return false
        if (next.userID == prevId) return false
        if (!isValidPipCandidate(prevId)) return false
        return SystemClock.uptimeMillis() - lastPipSwitchTs < PIP_SWITCH_COOLDOWN_MS
    }

    private fun isValidPipCandidate(userID: String): Boolean {
        return participants.any { it.userID == userID }
    }

    private fun bindPip(next: RoomParticipant) {
        val sameUser = next.userID == currentPipUserId
        screenSharePip.bind(next)
        if (!sameUser) lastPipSwitchTs = SystemClock.uptimeMillis()
        currentPipUserId = next.userID
    }

    private fun resolveScreenSharePipParticipant(): RoomParticipant? {
        if (participants.isEmpty()) return null

        // Rule 1: pick the loudest active speaker (must be above the threshold
        // AND actively unmuted — matches RoomPipView's own "is speaking" check).
        val loudest = participants
            .filter { it.microphoneStatus == DeviceStatus.ON }
            .mapNotNull { participant ->
                val volume = latestSpeakingMap[participant.userID] ?: 0
                if (volume > SPEAKING_VOLUME_THRESHOLD) participant to volume else null
            }
            .maxByOrNull { (_, volume) -> volume }
            ?.first
        if (loudest != null) return loudest

        // Rule 2: keep the previously bound participant if they're still around.
        val previouslyBound = currentPipUserId?.let { prevId ->
            participants.firstOrNull { it.userID == prevId }
        }
        if (previouslyBound != null) return previouslyBound

        // Rule 3: fall back to the local user.
        val localUserId = LoginStore.shared.loginState.loginUserInfo.value?.userID
        return participants.firstOrNull { it.userID == localUserId }
    }
}
