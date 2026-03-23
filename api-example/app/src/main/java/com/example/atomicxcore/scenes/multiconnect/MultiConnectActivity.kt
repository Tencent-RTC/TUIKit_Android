package com.example.atomicxcore.scenes.multiconnect

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.transform.CircleCropTransformation
import com.example.atomicxcore.R
import com.example.atomicxcore.components.DeviceSettingView
import com.example.atomicxcore.components.LocalizedManager
import com.example.atomicxcore.components.Role
import com.example.atomicxcore.components.SettingPanelController
import com.example.atomicxcore.databinding.ActivityMultiConnectBinding
import com.example.atomicxcore.utils.applyStatusBarTopPadding
import com.example.atomicxcore.utils.completionHandler
import com.example.atomicxcore.utils.liveInfoCompletionHandler
import com.example.atomicxcore.utils.PermissionHelper
import com.example.atomicxcore.utils.stopLiveCompletionHandler
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.trtc.tuikit.atomicxcore.api.device.DeviceStatus
import io.trtc.tuikit.atomicxcore.api.device.DeviceStore
import io.trtc.tuikit.atomicxcore.api.live.CoGuestState
import io.trtc.tuikit.atomicxcore.api.live.CoGuestStore
import io.trtc.tuikit.atomicxcore.api.live.DeviceControlPolicy
import io.trtc.tuikit.atomicxcore.api.live.GuestListener
import io.trtc.tuikit.atomicxcore.api.live.HostListener
import io.trtc.tuikit.atomicxcore.api.live.LiveAudienceListener
import io.trtc.tuikit.atomicxcore.api.live.LiveAudienceStore
import io.trtc.tuikit.atomicxcore.api.live.LiveEndedReason
import io.trtc.tuikit.atomicxcore.api.live.LiveInfo
import io.trtc.tuikit.atomicxcore.api.live.LiveKickedOutReason
import io.trtc.tuikit.atomicxcore.api.live.LiveListListener
import io.trtc.tuikit.atomicxcore.api.live.LiveListStore
import io.trtc.tuikit.atomicxcore.api.live.LiveSeatListener
import io.trtc.tuikit.atomicxcore.api.live.LiveSeatState
import io.trtc.tuikit.atomicxcore.api.live.LiveSeatStore
import io.trtc.tuikit.atomicxcore.api.live.LiveUserInfo
import io.trtc.tuikit.atomicxcore.api.live.NoResponseReason
import io.trtc.tuikit.atomicxcore.api.live.SeatInfo
import io.trtc.tuikit.atomicxcore.api.live.SeatLayoutTemplate
import io.trtc.tuikit.atomicxcore.api.live.SeatUserInfo
import io.trtc.tuikit.atomicxcore.api.login.LoginStore
import io.trtc.tuikit.atomicxcore.api.view.CoreViewType
import io.trtc.tuikit.atomicxcore.api.view.LiveCoreView
import io.trtc.tuikit.atomicxcore.api.view.VideoViewAdapter
import io.trtc.tuikit.atomicxcore.api.view.ViewLayer
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Business scenario: Audience co-hosting page (CoGuest)
 *
 * Related APIs:
 * - LiveAudienceStore - Audience list management
 * - CoGuestStore - Audience co-hosting management (apply/invite/accept/reject/disconnect)
 * - LiveSeatStore - Seat management (remote camera/microphone control)
 * - VideoViewDelegate - Video area overlay layer delegate
 *
 * Anchor: Push stream + view audience list + invite co-hosting + handle co-hosting requests + manage co-guest devices
 * Audience: Pull stream + view audience list + apply for co-hosting + respond to invitations + manage own devices after connecting
 */
class MultiConnectActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMultiConnectBinding
    private lateinit var role: Role
    private lateinit var liveID: String

    private var isLiveActive = false
    private var isOnSeat = false
    private var isApplying = false
    private var liveCoreView: LiveCoreView? = null

    // Store instances (initialized after entering live)
    private var liveAudienceStore: LiveAudienceStore? = null
    private var coGuestStore: CoGuestStore? = null
    private var liveSeatStore: LiveSeatStore? = null

    /** Anchor broadcast permission launcher */
    private val anchorPermissionLauncher: ActivityResultLauncher<Array<String>> =
        PermissionHelper.registerPermissions(this) { allGranted, _ ->
            if (allGranted) {
                openCameraAndMicrophone()
            } else {
                Toast.makeText(this, getString(R.string.permission_camera_mic_denied),
                    Toast.LENGTH_LONG).show()
                finish()
            }
        }

    /** Audience co-hosting permission launcher */
    private val coGuestPermissionLauncher: ActivityResultLauncher<Array<String>> =
        PermissionHelper.registerPermissions(this) { allGranted, _ ->
            if (allGranted) {
                openCameraAndMicrophone()
            } else {
                Toast.makeText(this, getString(R.string.permission_camera_mic_denied_coguest),
                    Toast.LENGTH_LONG).show()
            }
        }

    /** Co-guest user overlay view references (key: userID) */
    private val overlayViews = mutableMapOf<String, CoGuestOverlayView>()

    // MARK: - Listeners

    private val liveListListener = object : LiveListListener() {
        override fun onLiveEnded(liveID: String, reason: LiveEndedReason, anchorID: String) {
            if (liveID == this@MultiConnectActivity.liveID && role == Role.AUDIENCE) {
                runOnUiThread {
                    isLiveActive = false
                    isOnSeat = false
                    Toast.makeText(this@MultiConnectActivity,
                        getString(R.string.basicStreaming_status_ended), Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
        }

        override fun onKickedOutOfLive(liveID: String, reason: LiveKickedOutReason, anchorID: String) {
            if (liveID == this@MultiConnectActivity.liveID) {
                runOnUiThread {
                    isLiveActive = false
                    isOnSeat = false
                    Toast.makeText(this@MultiConnectActivity,
                        getString(R.string.basicStreaming_status_ended), Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
        }
    }

    /** Anchor side: Listen for audience co-hosting requests */
    private val hostListener = object : HostListener() {
        override fun onGuestApplicationReceived(guestUser: LiveUserInfo) {
            runOnUiThread { showApplicationAlert(guestUser) }
        }

        override fun onHostInvitationResponded(isAccept: Boolean, guestUser: LiveUserInfo) {
            val name = if (guestUser.userName.isNullOrEmpty()) guestUser.userID else guestUser.userName
            runOnUiThread {
                if (isAccept) {
                    Toast.makeText(this@MultiConnectActivity,
                        getString(R.string.coGuest_event_inviteAccepted, name), Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@MultiConnectActivity,
                        getString(R.string.coGuest_event_inviteRejected, name), Toast.LENGTH_SHORT).show()
                }
            }
        }

        override fun onGuestApplicationCancelled(guestUser: LiveUserInfo) {
            val name = if (guestUser.userName.isNullOrEmpty()) guestUser.userID else guestUser.userName
            runOnUiThread {
                Toast.makeText(this@MultiConnectActivity,
                    getString(R.string.coGuest_event_applicationCancelled, name), Toast.LENGTH_SHORT).show()
            }
        }
    }

    /** Audience side: Listen for anchor's co-hosting invitations and request responses */
    private val guestListener = object : GuestListener() {
        override fun onHostInvitationReceived(hostUser: LiveUserInfo) {
            runOnUiThread { showInvitationAlert(hostUser) }
        }

        override fun onGuestApplicationResponded(isAccept: Boolean, hostUser: LiveUserInfo) {
            runOnUiThread {
                if (isAccept) {
                    isApplying = false
                } else {
                    isApplying = false
                    updateCoGuestButtonAppearance()
                    val name = if (hostUser.userName.isNullOrEmpty()) hostUser.userID else hostUser.userName
                    Toast.makeText(this@MultiConnectActivity,
                        getString(R.string.coGuest_event_applicationRejected, name), Toast.LENGTH_SHORT).show()
                }
            }
        }

        override fun onGuestApplicationNoResponse(reason: NoResponseReason) {
            runOnUiThread {
                isApplying = false
                updateCoGuestButtonAppearance()
                Toast.makeText(this@MultiConnectActivity,
                    getString(R.string.coGuest_event_applicationTimeout), Toast.LENGTH_SHORT).show()
            }
        }

        override fun onKickedOffSeat(seatIndex: Int, hostUser: LiveUserInfo) {
            runOnUiThread {
                isOnSeat = false
                isApplying = false
                DeviceStore.shared().closeLocalCamera()
                DeviceStore.shared().closeLocalMicrophone()
                updateCoGuestButtonAppearance()
                Toast.makeText(this@MultiConnectActivity,
                    getString(R.string.coGuest_event_kickedOff), Toast.LENGTH_SHORT).show()
            }
        }

        override fun onHostInvitationCancelled(hostUser: LiveUserInfo) {
            runOnUiThread {
                Toast.makeText(this@MultiConnectActivity,
                    getString(R.string.coGuest_event_invitationCancelled), Toast.LENGTH_SHORT).show()
            }
        }
    }

    /** Audience side: Listen for anchor's operations on local device */
    private val liveSeatListener = object : LiveSeatListener() {
        override fun onLocalCameraOpenedByAdmin(policy: DeviceControlPolicy) {
            runOnUiThread {
                showDeviceRequestAlert(
                    getString(R.string.coGuest_device_cameraRequest_title),
                    getString(R.string.coGuest_device_cameraRequest_message)
                ) {
                    PermissionHelper.requestCameraAndMicrophone(
                        this@MultiConnectActivity, coGuestPermissionLauncher
                    ) {
                        DeviceStore.shared().openLocalCamera(true, null)
                    }
                }
            }
        }

        override fun onLocalCameraClosedByAdmin() {
            runOnUiThread {
                Toast.makeText(this@MultiConnectActivity,
                    getString(R.string.coGuest_device_cameraClosed), Toast.LENGTH_SHORT).show()
            }
        }

        override fun onLocalMicrophoneOpenedByAdmin(policy: DeviceControlPolicy) {
            runOnUiThread {
                showDeviceRequestAlert(
                    getString(R.string.coGuest_device_micRequest_title),
                    getString(R.string.coGuest_device_micRequest_message)
                ) {
                    PermissionHelper.requestCameraAndMicrophone(
                        this@MultiConnectActivity, coGuestPermissionLauncher
                    ) {
                        liveSeatStore?.unmuteMicrophone(null)
                        DeviceStore.shared().openLocalMicrophone(null)
                    }
                }
            }
        }

        override fun onLocalMicrophoneClosedByAdmin() {
            runOnUiThread {
                Toast.makeText(this@MultiConnectActivity,
                    getString(R.string.coGuest_device_micClosed), Toast.LENGTH_SHORT).show()
            }
        }
    }

    /** Audience list change listener */
    private val audienceListener = object : LiveAudienceListener() {
        override fun onAudienceJoined(audience: LiveUserInfo) {
            runOnUiThread { refreshAudienceCount() }
        }

        override fun onAudienceLeft(audience: LiveUserInfo) {
            runOnUiThread { refreshAudienceCount() }
        }
    }

    /** VideoViewAdapter implementation */
    private val videoViewAdapter = object : VideoViewAdapter {
        override fun createCoGuestView(seatInfo: SeatInfo?, viewLayer: ViewLayer?): View? {
            if (seatInfo == null) return null
            return when (viewLayer) {
                ViewLayer.FOREGROUND -> {
                    val overlay = CoGuestOverlayView(this@MultiConnectActivity, seatInfo)
                    overlay.onTap = { info -> handleCoGuestViewTapped(info) }
                    overlayViews[seatInfo.userInfo.userID] = overlay
                    overlay
                }
                else -> null
            }
        }

        override fun createCoHostView(seatInfo: SeatInfo?, viewLayer: ViewLayer?): View? = null
        override fun createBattleView(seatInfo: SeatInfo?): View? = null
        override fun createBattleContainerView(): View? = null
    }

    // MARK: - Lifecycle

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocalizedManager.applyLanguage(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityMultiConnectBinding.inflate(layoutInflater)
        setContentView(binding.root)

        role = Role.fromName(intent.getStringExtra("role") ?: "ANCHOR")
        liveID = intent.getStringExtra("liveID") ?: ""

        setupUI()
        setupBindings()
        configureForRole()
    }

    override fun onDestroy() {
        super.onDestroy()
        LiveListStore.shared().removeLiveListListener(liveListListener)
        liveAudienceStore?.removeLiveAudienceListener(audienceListener)
        if (role == Role.ANCHOR) {
            coGuestStore?.removeHostListener(hostListener)
        } else {
            coGuestStore?.removeGuestListener(guestListener)
            liveSeatStore?.removeLiveSeatEventListener(liveSeatListener)
        }
        overlayViews.clear()
        cleanupLiveSession()
    }

    // MARK: - Setup

    private fun setupUI() {
        // Adjust for status bar to prevent toolbar overlapping in immersive mode
        binding.toolbar.applyStatusBarTopPadding()
        // Use custom TextView to display full room ID, avoiding truncation
        binding.tvLiveID.text = liveID
        binding.toolbar.setNavigationIcon(R.drawable.ic_arrow_back_white)
        binding.toolbar.setNavigationOnClickListener { onBackPressed() }

        val viewType = if (role == Role.ANCHOR) CoreViewType.PUSH_VIEW else CoreViewType.PLAY_VIEW
        liveCoreView = LiveCoreView(this, null, 0, viewType).apply {
            setLiveID(liveID)
            setVideoViewAdapter(this@MultiConnectActivity.videoViewAdapter)
        }
        binding.videoContainer.addView(liveCoreView)
    }

    private fun setupBindings() {
        LiveListStore.shared().addLiveListListener(liveListListener)
    }

    private fun configureForRole() {
        when (role) {
            Role.ANCHOR -> configureForAnchor()
            Role.AUDIENCE -> configureForAudience()
        }
    }

    // MARK: - Interactive Components

    private fun setupInteractiveComponents() {
        // Initialize Stores
        liveAudienceStore = LiveAudienceStore.create(liveID)
        coGuestStore = CoGuestStore.create(liveID)
        liveSeatStore = LiveSeatStore.create(liveID)

        // Online audience count label
        binding.audienceCountLabel.visibility = View.VISIBLE
        binding.audienceCountLabel.setOnClickListener { showAudienceListPanel() }

        // Co-hosting button
        binding.btnCoGuest.visibility = View.VISIBLE
        binding.btnCoGuest.setOnClickListener { onCoGuestButtonTapped() }
        updateCoGuestButtonAppearance()

        // Register listeners
        liveAudienceStore?.addLiveAudienceListener(audienceListener)
        if (role == Role.ANCHOR) {
            coGuestStore?.addHostListener(hostListener)
        } else {
            coGuestStore?.addGuestListener(guestListener)
            liveSeatStore?.addLiveSeatEventListener(liveSeatListener)
        }

        // Fetch initial audience list
        liveAudienceStore?.fetchAudienceList(null)

        // Subscribe to co-hosting state changes
        lifecycleScope.launch {
            coGuestStore?.coGuestState?.connected?.collectLatest { connectedList ->
                handleCoGuestConnectedUpdate(connectedList)
            }
        }

        // Subscribe to seat state changes
        lifecycleScope.launch {
            liveSeatStore?.liveSeatState?.seatList?.collectLatest { seatList ->
                handleSeatListUpdate(seatList)
            }
        }

        // Subscribe to audience count
        lifecycleScope.launch {
            liveAudienceStore?.liveAudienceState?.audienceCount?.collectLatest { count ->
                runOnUiThread { updateAudienceCountLabel(count) }
            }
        }
    }

    // MARK: - Anchor Configuration

    private fun configureForAnchor() {
        binding.toolbar.inflateMenu(R.menu.menu_live_settings)
        binding.toolbar.setOnMenuItemClickListener { item ->
            if (item.itemId == R.id.action_settings) {
                showDeviceSettingPanel()
                true
            } else false
        }

        // Request permissions then open camera and microphone
        PermissionHelper.requestCameraAndMicrophone(this, anchorPermissionLauncher) {
            openCameraAndMicrophone()
        }

        binding.btnStartLive.visibility = View.VISIBLE
        binding.btnStartLive.setOnClickListener { createLive() }
    }

    /** Open camera and microphone after permissions are granted */
    private fun openCameraAndMicrophone() {
        DeviceStore.shared().openLocalCamera(true, null)
        DeviceStore.shared().openLocalMicrophone(null)
    }

    // MARK: - Audience Configuration

    private fun configureForAudience() {
        joinLive()
    }

    // MARK: - Device Settings Panel

    private fun showDeviceSettingPanel() {
        val deviceView = DeviceSettingView(this)
        val panel = SettingPanelController.newInstance(getString(R.string.deviceSetting_title))
        panel.contentViewProvider = { deviceView }
        panel.show(this)
    }

    // MARK: - Co-Hosting Button Actions

    private fun onCoGuestButtonTapped() {
        if (role == Role.ANCHOR) {
            showAudienceListPanel()
        } else {
            when {
                isOnSeat -> disconnectCoGuest()
                isApplying -> cancelCoGuestApplication()
                else -> applyForCoGuest()
            }
        }
    }

    // MARK: - Audience List Panel

    private fun showAudienceListPanel() {
        val panelView = AudienceListPanelView(this, role, liveAudienceStore, coGuestStore)
        panelView.onInvite = { userID -> inviteAudienceToSeat(userID) }

        val panel = SettingPanelController.newInstance(
            getString(R.string.coGuest_audienceList_title), 400
        )
        panel.contentViewProvider = { panelView }
        panel.show(this)
    }

    // MARK: - Create / Join / End Live

    private fun createLive() {
        Toast.makeText(this, getString(R.string.basicStreaming_status_creating), Toast.LENGTH_SHORT).show()
        binding.btnStartLive.isEnabled = false

        val liveInfo = LiveInfo().apply {
            this.liveID = this@MultiConnectActivity.liveID
            this.seatTemplate = SeatLayoutTemplate.VideoDynamicGrid9Seats
        }
        liveInfo.keepOwnerOnSeat = true
        LiveListStore.shared().createLive(liveInfo, liveInfoCompletionHandler { createdInfo ->
            runOnUiThread {
                if (createdInfo != null) {
                    isLiveActive = true
                    liveCoreView?.setLiveID(createdInfo.liveID)
                    binding.btnStartLive.visibility = View.GONE
                    updateNavigationBarForLiveState()
                    setupInteractiveComponents()
                    Toast.makeText(this, getString(R.string.basicStreaming_status_created, createdInfo.liveID),
                        Toast.LENGTH_SHORT).show()
                } else {
                    binding.btnStartLive.isEnabled = true
                    Toast.makeText(this, getString(R.string.basicStreaming_status_failed, "Create failed"),
                        Toast.LENGTH_SHORT).show()
                    DeviceStore.shared().closeLocalCamera()
                    DeviceStore.shared().closeLocalMicrophone()
                }
            }
        })
    }

    private fun joinLive() {
        Toast.makeText(this, getString(R.string.basicStreaming_status_joining), Toast.LENGTH_SHORT).show()

        LiveListStore.shared().joinLive(liveID, liveInfoCompletionHandler { liveInfo ->
            runOnUiThread {
                if (liveInfo != null) {
                    isLiveActive = true
                    liveCoreView?.setLiveID(liveInfo.liveID)
                    setupInteractiveComponents()
                    Toast.makeText(this, getString(R.string.basicStreaming_status_joined, liveInfo.liveID),
                        Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, getString(R.string.basicStreaming_status_failed, "Join failed"),
                        Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
        })
    }

    private fun endLiveAndGoBack() {
        Toast.makeText(this, getString(R.string.basicStreaming_status_ending), Toast.LENGTH_SHORT).show()

        LiveListStore.shared().endLive(stopLiveCompletionHandler { code, message ->
            runOnUiThread {
                if (code == 0) {
                    DeviceStore.shared().closeLocalCamera()
                    DeviceStore.shared().closeLocalMicrophone()
                    isLiveActive = false
                    finish()
                } else {
                    Toast.makeText(this, getString(R.string.basicStreaming_status_failed, message),
                        Toast.LENGTH_SHORT).show()
                }
            }
        })
    }

    private fun leaveLiveAndGoBack() {
        LiveListStore.shared().leaveLive(completionHandler { code, message ->
            runOnUiThread {
                if (code == 0) {
                    isLiveActive = false
                    finish()
                } else {
                    Toast.makeText(this, getString(R.string.basicStreaming_status_failed, message),
                        Toast.LENGTH_SHORT).show()
                }
            }
        })
    }

    // MARK: - Audience Co-Hosting Operations

    private fun applyForCoGuest() {
        isApplying = true
        updateCoGuestButtonAppearance()
        Toast.makeText(this, getString(R.string.coGuest_status_applying), Toast.LENGTH_SHORT).show()

        coGuestStore?.applyForSeat(-1, 30, null, completionHandler { code, message ->
            if (code != 0) {
                runOnUiThread {
                    isApplying = false
                    updateCoGuestButtonAppearance()
                    Toast.makeText(this, getString(R.string.basicStreaming_status_failed, message),
                        Toast.LENGTH_SHORT).show()
                }
            }
        })
    }

    private fun cancelCoGuestApplication() {
        coGuestStore?.cancelApplication(completionHandler { code, _ ->
            runOnUiThread {
                if (code == 0) {
                    isApplying = false
                    updateCoGuestButtonAppearance()
                    Toast.makeText(this, getString(R.string.coGuest_status_cancelled), Toast.LENGTH_SHORT).show()
                }
            }
        })
    }

    private fun disconnectCoGuest() {
        coGuestStore?.disconnect(completionHandler { code, message ->
            runOnUiThread {
                if (code == 0) {
                    isOnSeat = false
                    isApplying = false
                    if (role == Role.AUDIENCE) {
                        DeviceStore.shared().closeLocalCamera()
                        DeviceStore.shared().closeLocalMicrophone()
                    }
                    updateCoGuestButtonAppearance()
                    Toast.makeText(this, getString(R.string.coGuest_status_disconnected), Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, getString(R.string.basicStreaming_status_failed, message),
                        Toast.LENGTH_SHORT).show()
                }
            }
        })
    }

    private fun inviteAudienceToSeat(userID: String) {
        coGuestStore?.inviteToSeat(userID, -1, 30, null, completionHandler { code, message ->
            runOnUiThread {
                if (code == 0) {
                    Toast.makeText(this, getString(R.string.coGuest_status_invited), Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, getString(R.string.basicStreaming_status_failed, message),
                        Toast.LENGTH_SHORT).show()
                }
            }
        })
    }

    // MARK: - Co-Hosting State Handling

    private fun handleCoGuestConnectedUpdate(connectedList: List<SeatUserInfo>) {
        val currentUserID = LoginStore.shared.loginState.loginUserInfo.value?.userID ?: ""
        val wasOnSeat = isOnSeat
        isOnSeat = connectedList.any { it.userID == currentUserID }

        if (isOnSeat && !wasOnSeat && role == Role.AUDIENCE) {
            isApplying = false
            // Audience joined seat; request permissions then open device
            PermissionHelper.requestCameraAndMicrophone(this, coGuestPermissionLauncher) {
                openCameraAndMicrophone()
            }
        } else if (!isOnSeat && wasOnSeat && role == Role.AUDIENCE) {
            DeviceStore.shared().closeLocalCamera()
            DeviceStore.shared().closeLocalMicrophone()
        }

        runOnUiThread { updateCoGuestButtonAppearance() }
    }

    private fun handleSeatListUpdate(seatList: List<SeatInfo>) {
        val activeUserIDs = mutableSetOf<String>()
        for (seatInfo in seatList) {
            if (seatInfo.userInfo.userID.isNullOrEmpty()) continue
            activeUserIDs.add(seatInfo.userInfo.userID)
            overlayViews[seatInfo.userInfo.userID]?.updateAVStatus(seatInfo)
        }
        overlayViews.keys.retainAll(activeUserIDs)
    }

    // MARK: - Device Management Dialogs

    private fun showSeatUserDeviceAlert(seatInfo: SeatInfo) {
        val userInfo = seatInfo.userInfo
        if (userInfo.userID.isNullOrEmpty()) return

        val userName = if (userInfo.userName.isNullOrEmpty()) userInfo.userID else userInfo.userName
        val items = mutableListOf<String>()
        val actions = mutableListOf<() -> Unit>()

        // Camera management
        if (userInfo.cameraStatus == DeviceStatus.ON) {
            items.add(getString(R.string.coGuest_manage_closeCamera))
            actions.add { liveSeatStore?.closeRemoteCamera(userInfo.userID, null) }
        } else {
            items.add(getString(R.string.coGuest_manage_openCamera))
            actions.add {
                liveSeatStore?.openRemoteCamera(userInfo.userID,
                    DeviceControlPolicy.UNLOCK_ONLY, null)
            }
        }

        // Microphone management
        if (userInfo.microphoneStatus == DeviceStatus.ON) {
            items.add(getString(R.string.coGuest_manage_closeMic))
            actions.add { liveSeatStore?.closeRemoteMicrophone(userInfo.userID, null) }
        } else {
            items.add(getString(R.string.coGuest_manage_openMic))
            actions.add {
                liveSeatStore?.openRemoteMicrophone(userInfo.userID,
                    DeviceControlPolicy.UNLOCK_ONLY, null)
            }
        }

        // Kick off seat
        items.add(getString(R.string.coGuest_manage_kickOff))
        actions.add { liveSeatStore?.kickUserOutOfSeat(userInfo.userID, null) }

        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.coGuest_manage_title, userName))
            .setItems(items.toTypedArray()) { _, which -> actions[which]() }
            .setNegativeButton(getString(R.string.common_cancel), null)
            .show()
    }

    private fun showSelfDeviceAlert() {
        val deviceState = DeviceStore.shared().deviceState
        val items = mutableListOf<String>()
        val actions = mutableListOf<() -> Unit>()

        if (deviceState.cameraStatus.value == DeviceStatus.ON) {
            items.add(getString(R.string.coGuest_selfManage_closeCamera))
            actions.add { DeviceStore.shared().closeLocalCamera() }
        } else {
            items.add(getString(R.string.coGuest_selfManage_openCamera))
            actions.add {
                PermissionHelper.requestCameraAndMicrophone(this, coGuestPermissionLauncher) {
                    DeviceStore.shared().openLocalCamera(true, null)
                }
            }
        }

        if (deviceState.microphoneStatus.value == DeviceStatus.ON) {
            items.add(getString(R.string.coGuest_selfManage_closeMic))
            actions.add { DeviceStore.shared().closeLocalMicrophone() }
        } else {
            items.add(getString(R.string.coGuest_selfManage_openMic))
            actions.add {
                PermissionHelper.requestCameraAndMicrophone(this, coGuestPermissionLauncher) {
                    DeviceStore.shared().openLocalMicrophone(null)
                }
            }
        }

        items.add(getString(R.string.coGuest_selfManage_disconnect))
        actions.add { disconnectCoGuest() }

        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.coGuest_selfManage_title))
            .setItems(items.toTypedArray()) { _, which -> actions[which]() }
            .setNegativeButton(getString(R.string.common_cancel), null)
            .show()
    }

    private fun handleCoGuestViewTapped(seatInfo: SeatInfo) {
        val currentUserID = LoginStore.shared.loginState.loginUserInfo.value?.userID ?: ""
        if (role == Role.ANCHOR) {
            if (seatInfo.userInfo.userID != currentUserID) {
                showSeatUserDeviceAlert(seatInfo)
            }
        } else {
            if (seatInfo.userInfo.userID == currentUserID) {
                showSelfDeviceAlert()
            }
        }
    }

    // MARK: - Dialogs

    private fun showApplicationAlert(guestUser: LiveUserInfo) {
        val name = if (guestUser.userName.isNullOrEmpty()) guestUser.userID else guestUser.userName
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.coGuest_application_title))
            .setMessage(getString(R.string.coGuest_application_message, name))
            .setPositiveButton(getString(R.string.coGuest_application_accept)) { _, _ ->
                coGuestStore?.acceptApplication(guestUser.userID, null)
            }
            .setNegativeButton(getString(R.string.coGuest_application_reject)) { _, _ ->
                coGuestStore?.rejectApplication(guestUser.userID, null)
            }
            .show()
    }

    private fun showInvitationAlert(hostUser: LiveUserInfo) {
        val name = if (hostUser.userName.isNullOrEmpty()) hostUser.userID else hostUser.userName
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.coGuest_invitation_title))
            .setMessage(getString(R.string.coGuest_invitation_message, name))
            .setPositiveButton(getString(R.string.coGuest_invitation_accept)) { _, _ ->
                coGuestStore?.acceptInvitation(hostUser.userID, null)
            }
            .setNegativeButton(getString(R.string.coGuest_invitation_reject)) { _, _ ->
                coGuestStore?.rejectInvitation(hostUser.userID, null)
            }
            .show()
    }

    private fun showDeviceRequestAlert(title: String, message: String, onAccept: () -> Unit) {
        MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(getString(R.string.common_confirm)) { _, _ -> onAccept() }
            .setNegativeButton(getString(R.string.common_cancel), null)
            .show()
    }

    // MARK: - UI Helpers

    private fun updateNavigationBarForLiveState() {
        if (role != Role.ANCHOR) return
        binding.toolbar.menu.clear()
        binding.toolbar.inflateMenu(R.menu.menu_live_end)
        binding.toolbar.inflateMenu(R.menu.menu_live_settings)
        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_end_live -> { showEndLiveConfirm(); true }
                R.id.action_settings -> { showDeviceSettingPanel(); true }
                else -> false
            }
        }
    }

    private fun showEndLiveConfirm() {
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.basicStreaming_endLive_confirm_title))
            .setMessage(getString(R.string.basicStreaming_endLive_confirm_message))
            .setPositiveButton(getString(R.string.common_confirm)) { _, _ -> endLiveAndGoBack() }
            .setNegativeButton(getString(R.string.common_cancel), null)
            .show()
    }

    private fun updateAudienceCountLabel(count: Int) {
        if (count > 0) {
            binding.audienceCountLabel.text = getString(R.string.coGuest_audienceCount, count)
            binding.audienceCountLabel.visibility = View.VISIBLE
        } else {
            binding.audienceCountLabel.visibility = View.GONE
        }
    }

    private fun refreshAudienceCount() {
        val count = liveAudienceStore?.liveAudienceState?.audienceCount?.value ?: 0
        updateAudienceCountLabel(count)
    }

    private fun updateCoGuestButtonAppearance() {
        if (role == Role.ANCHOR) {
            binding.btnCoGuest.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#CC4CAF50"))
        } else {
            when {
                isOnSeat -> binding.btnCoGuest.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#CCF44336"))
                isApplying -> binding.btnCoGuest.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#CCFF9800"))
                else -> binding.btnCoGuest.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#CC4CAF50"))
            }
        }
    }

    override fun onBackPressed() {
        when {
            role == Role.AUDIENCE && isOnSeat -> {
                coGuestStore?.disconnect(completionHandler { code, message ->
                    runOnUiThread {
                        if (code == 0) {
                            isOnSeat = false
                            leaveLiveAndGoBack()
                        } else {
                            Toast.makeText(this, getString(R.string.basicStreaming_status_failed, message),
                                Toast.LENGTH_SHORT).show()
                        }
                    }
                })
            }
            role == Role.AUDIENCE && isLiveActive -> {
                if (isApplying) coGuestStore?.cancelApplication(null)
                leaveLiveAndGoBack()
            }
            role == Role.ANCHOR && isLiveActive -> endLiveAndGoBack()
            role == Role.ANCHOR && !isLiveActive -> {
                DeviceStore.shared().closeLocalCamera()
                DeviceStore.shared().closeLocalMicrophone()
                super.onBackPressed()
            }
            else -> super.onBackPressed()
        }
    }

    // MARK: - Cleanup

    private fun cleanupLiveSession() {
        if (!isLiveActive) return
        if (isOnSeat) {
            coGuestStore?.disconnect(null)
        }
        when (role) {
            Role.ANCHOR -> {
                DeviceStore.shared().closeLocalCamera()
                DeviceStore.shared().closeLocalMicrophone()
                LiveListStore.shared().endLive(null)
            }
            Role.AUDIENCE -> {
                if (isOnSeat) {
                    DeviceStore.shared().closeLocalCamera()
                    DeviceStore.shared().closeLocalMicrophone()
                }
                LiveListStore.shared().leaveLive(null)
            }
        }
        isLiveActive = false
        isOnSeat = false
        overlayViews.clear()
    }
}

// MARK: - CoGuestOverlayView

/**
 * Co-guest user video overlay view
 *
 * Displays the user's avatar (when camera is off), microphone status icon, and bottom nickname label.
 * Supports tap gesture to trigger management actions.
 */
class CoGuestOverlayView(
    context: Context,
    private var seatInfo: SeatInfo
) : FrameLayout(context) {

    var onTap: ((SeatInfo) -> Unit)? = null

    private val avatarContainer: FrameLayout
    private val avatarImageView: ImageView
    private val avatarLabel: TextView
    private val micIconView: ImageView
    private val nameLabel: TextView

    init {
        val dp = resources.displayMetrics.density
        isClickable = true
        setBackgroundColor(Color.TRANSPARENT)

        // Avatar container (shown when camera is off)
        avatarContainer = FrameLayout(context).apply {
            setBackgroundColor(Color.DKGRAY)
            visibility = GONE
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        }
        addView(avatarContainer)

        avatarImageView = ImageView(context).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            clipToOutline = true
            outlineProvider = object : android.view.ViewOutlineProvider() {
                override fun getOutline(view: View, outline: android.graphics.Outline) {
                    outline.setOval(0, 0, view.width, view.height)
                }
            }
            setBackgroundColor(Color.parseColor("#1AFFFFFF"))
            layoutParams = LayoutParams((60 * dp).toInt(), (60 * dp).toInt(), Gravity.CENTER)
        }
        avatarContainer.addView(avatarImageView)

        avatarLabel = TextView(context).apply {
            textSize = 24f
            setTextColor(Color.WHITE)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER)
        }
        avatarContainer.addView(avatarLabel)

        // Microphone status icon (top-right)
        micIconView = ImageView(context).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            layoutParams = LayoutParams((18 * dp).toInt(), (18 * dp).toInt(), Gravity.END).apply {
                topMargin = (4 * dp).toInt()
                marginEnd = (4 * dp).toInt()
            }
        }
        addView(micIconView)

        // Bottom nickname label
        nameLabel = TextView(context).apply {
            textSize = 11f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#80000000"))
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, (20 * dp).toInt(), Gravity.BOTTOM).apply {
                marginStart = (4 * dp).toInt()
                marginEnd = (4 * dp).toInt()
                bottomMargin = (4 * dp).toInt()
            }
        }
        addView(nameLabel)

        // Set user info
        val userInfo = seatInfo.userInfo
        nameLabel.text = if (userInfo.userName.isNullOrEmpty()) userInfo.userID else userInfo.userName
        val currentUserID = LoginStore.shared.loginState.loginUserInfo.value?.userID ?: ""
        nameLabel.visibility = if (userInfo.userID == currentUserID) GONE else VISIBLE

        loadAvatar()
        updateAVStatus(seatInfo)

        setOnClickListener { onTap?.invoke(seatInfo) }
    }

    private fun loadAvatar() {
        val userInfo = seatInfo.userInfo
        if (!userInfo.avatarURL.isNullOrEmpty()) {
            avatarImageView.load(userInfo.avatarURL) {
                transformations(CircleCropTransformation())
                placeholder(R.drawable.ic_person)
            }
            avatarLabel.visibility = GONE
        } else {
            avatarImageView.setImageDrawable(null)
            avatarLabel.visibility = VISIBLE
            avatarLabel.text = (userInfo.userID ?: "").take(1).uppercase()
        }
    }

    fun updateAVStatus(updatedSeatInfo: SeatInfo) {
        seatInfo = updatedSeatInfo
        val userInfo = seatInfo.userInfo

        // Show avatar when camera is off
        avatarContainer.visibility =
            if (userInfo.cameraStatus == DeviceStatus.ON) GONE else VISIBLE

        // Microphone status icon
        if (userInfo.microphoneStatus == DeviceStatus.ON) {
            micIconView.setImageResource(R.drawable.ic_mic)
            micIconView.setColorFilter(Color.WHITE)
        } else {
            micIconView.setImageResource(R.drawable.ic_mic_off)
            micIconView.setColorFilter(Color.RED)
        }
    }
}

// MARK: - AudienceListPanelView

/**
 * Audience list panel
 *
 * Displays online audience; anchor side includes invite button.
 */
class AudienceListPanelView(
    context: Context,
    private val role: Role,
    private val audienceStore: LiveAudienceStore?,
    private val coGuestStore: CoGuestStore?
) : FrameLayout(context) {

    var onInvite: ((String) -> Unit)? = null

    private val audienceList = mutableListOf<LiveUserInfo>()
    private val connectedUserIDs = mutableSetOf<String>()
    private val invitedUserIDs = mutableSetOf<String>()
    private val recyclerView: RecyclerView
    private val emptyLabel: TextView
    private val adapter = AudienceAdapter()

    init {
        val dp = resources.displayMetrics.density

        recyclerView = RecyclerView(context).apply {
            layoutManager = LinearLayoutManager(context)
            adapter = this@AudienceListPanelView.adapter
            setBackgroundColor(Color.TRANSPARENT)
            overScrollMode = OVER_SCROLL_NEVER
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        }
        addView(recyclerView)

        emptyLabel = TextView(context).apply {
            text = context.getString(R.string.coGuest_audienceList_empty)
            textSize = 15f
            setTextColor(Color.GRAY)
            gravity = Gravity.CENTER
            visibility = GONE
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER)
        }
        addView(emptyLabel)

        // Refresh data
        audienceStore?.fetchAudienceList(completionHandler { code, _ ->
            post {
                if (code == 0) refreshData()
            }
        })
    }

    private fun refreshData() {
        audienceList.clear()
        audienceList.addAll(audienceStore?.liveAudienceState?.audienceList?.value ?: emptyList())
        connectedUserIDs.clear()
        connectedUserIDs.addAll(coGuestStore?.coGuestState?.connected?.value?.map { it.userID } ?: emptyList())
        invitedUserIDs.clear()
        invitedUserIDs.addAll(coGuestStore?.coGuestState?.invitees?.value?.map { it.userID } ?: emptyList())
        emptyLabel.visibility = if (audienceList.isEmpty()) VISIBLE else GONE
        adapter.notifyDataSetChanged()
    }

    private inner class AudienceAdapter : RecyclerView.Adapter<AudienceAdapter.ViewHolder>() {
        inner class ViewHolder(val container: LinearLayout) : RecyclerView.ViewHolder(container) {
            val avatarView: ImageView = container.findViewWithTag("avatar")
            val avatarLabel: TextView = container.findViewWithTag("avatarLabel")
            val nameLabel: TextView = container.findViewWithTag("name")
            val inviteBtn: MaterialButton = container.findViewWithTag("invite")
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val dp = parent.resources.displayMetrics.density
            val dp16 = (16 * dp).toInt()
            val dp12 = (12 * dp).toInt()
            val dp36 = (36 * dp).toInt()
            val dp56 = (56 * dp).toInt()
            val dp28 = (28 * dp).toInt()

            val row = LinearLayout(parent.context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, dp56)
                setPadding(dp16, 0, dp16, 0)
            }

            val avatarView = ImageView(parent.context).apply {
                tag = "avatar"
                layoutParams = LinearLayout.LayoutParams(dp36, dp36)
                scaleType = ImageView.ScaleType.CENTER_CROP
                clipToOutline = true
                outlineProvider = object : android.view.ViewOutlineProvider() {
                    override fun getOutline(view: View, outline: android.graphics.Outline) {
                        outline.setOval(0, 0, view.width, view.height)
                    }
                }
                setBackgroundColor(Color.parseColor("#E0E0E0"))
            }
            row.addView(avatarView)

            val avatarLabel = TextView(parent.context).apply {
                tag = "avatarLabel"
                textSize = 14f
                setTextColor(Color.WHITE)
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(dp36, dp36).apply {
                    marginStart = -dp36
                }
            }
            row.addView(avatarLabel)

            val nameLabel = TextView(parent.context).apply {
                tag = "name"
                textSize = 15f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = dp12
                }
            }
            row.addView(nameLabel)

            val inviteBtn = MaterialButton(parent.context).apply {
                tag = "invite"
                textSize = 13f
                cornerRadius = (14 * dp).toInt()
                insetTop = 0
                insetBottom = 0
                minWidth = 0
                minimumWidth = 0
                setPadding((12 * dp).toInt(), 0, (12 * dp).toInt(), 0)
                minHeight = dp28
                minimumHeight = dp28
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp28)
            }
            row.addView(inviteBtn)

            return ViewHolder(row)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val user = audienceList[position]
            holder.nameLabel.text = if (user.userName.isNullOrEmpty()) user.userID else user.userName
            holder.avatarLabel.text = (user.userID ?: "").take(1).uppercase()

            if (!user.avatarURL.isNullOrEmpty()) {
                holder.avatarView.load(user.avatarURL) {
                    transformations(CircleCropTransformation())
                    placeholder(R.drawable.ic_person)
                }
                holder.avatarLabel.visibility = GONE
            } else {
                holder.avatarView.setImageDrawable(null)
                holder.avatarLabel.visibility = VISIBLE
            }

            val isConnected = connectedUserIDs.contains(user.userID)
            val isInvited = invitedUserIDs.contains(user.userID)

            if (role == Role.ANCHOR) {
                holder.inviteBtn.visibility = VISIBLE
                when {
                    isConnected -> {
                        holder.inviteBtn.text = context.getString(R.string.coGuest_audienceList_connected)
                        holder.inviteBtn.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#E0E0E0"))
                        holder.inviteBtn.setTextColor(Color.GRAY)
                        holder.inviteBtn.isEnabled = false
                    }
                    isInvited -> {
                        holder.inviteBtn.text = context.getString(R.string.coGuest_audienceList_inviting)
                        holder.inviteBtn.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#26FF9800"))
                        holder.inviteBtn.setTextColor(Color.parseColor("#FF9800"))
                        holder.inviteBtn.isEnabled = false
                    }
                    else -> {
                        holder.inviteBtn.text = context.getString(R.string.coGuest_audienceList_invite)
                        holder.inviteBtn.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#262196F3"))
                        holder.inviteBtn.setTextColor(Color.parseColor("#2196F3"))
                        holder.inviteBtn.isEnabled = true
                    }
                }
                holder.inviteBtn.setOnClickListener { onInvite?.invoke(user.userID) }
            } else {
                holder.inviteBtn.visibility = GONE
            }
        }

        override fun getItemCount() = audienceList.size
    }
}
