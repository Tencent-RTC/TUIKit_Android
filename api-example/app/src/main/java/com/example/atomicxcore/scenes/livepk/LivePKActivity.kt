package com.example.atomicxcore.scenes.livepk

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.example.atomicxcore.R
import com.example.atomicxcore.components.CoHostUserListView
import com.example.atomicxcore.components.DeviceSettingView
import com.example.atomicxcore.components.GiftPanelView
import com.example.atomicxcore.components.LocalizedManager
import com.example.atomicxcore.components.Role
import com.example.atomicxcore.components.SettingPanelController
import com.example.atomicxcore.databinding.ActivityLivePkBinding
import com.example.atomicxcore.utils.applyStatusBarTopPadding
import com.example.atomicxcore.utils.completionHandler
import com.example.atomicxcore.utils.liveInfoCompletionHandler
import com.example.atomicxcore.utils.PermissionHelper
import com.example.atomicxcore.utils.stopLiveCompletionHandler
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.trtc.tuikit.atomicxcore.api.barrage.BarrageStore
import io.trtc.tuikit.atomicxcore.api.device.DeviceStore
import io.trtc.tuikit.atomicxcore.api.gift.Gift
import io.trtc.tuikit.atomicxcore.api.gift.GiftCategory
import io.trtc.tuikit.atomicxcore.api.gift.GiftListener
import io.trtc.tuikit.atomicxcore.api.gift.GiftStore
import io.trtc.tuikit.atomicxcore.api.live.BattleConfig
import io.trtc.tuikit.atomicxcore.api.live.BattleEndedReason
import io.trtc.tuikit.atomicxcore.api.live.BattleInfo
import io.trtc.tuikit.atomicxcore.api.live.BattleListener
import io.trtc.tuikit.atomicxcore.api.live.BattleRequestCallback
import io.trtc.tuikit.atomicxcore.api.live.BattleState
import io.trtc.tuikit.atomicxcore.api.live.BattleStore
import io.trtc.tuikit.atomicxcore.api.live.CoHostLayoutTemplate
import io.trtc.tuikit.atomicxcore.api.live.CoHostListener
import io.trtc.tuikit.atomicxcore.api.live.CoHostStatus
import io.trtc.tuikit.atomicxcore.api.live.CoHostStore
import io.trtc.tuikit.atomicxcore.api.live.LiveEndedReason
import io.trtc.tuikit.atomicxcore.api.live.LiveInfo
import io.trtc.tuikit.atomicxcore.api.live.LiveKickedOutReason
import io.trtc.tuikit.atomicxcore.api.live.LiveListListener
import io.trtc.tuikit.atomicxcore.api.live.LiveListStore
import io.trtc.tuikit.atomicxcore.api.live.LiveUserInfo
import io.trtc.tuikit.atomicxcore.api.live.SeatLayoutTemplate
import io.trtc.tuikit.atomicxcore.api.live.SeatUserInfo
import io.trtc.tuikit.atomicxcore.api.login.LoginStore
import io.trtc.tuikit.atomicxcore.api.view.CoreViewType
import io.trtc.tuikit.atomicxcore.api.view.LiveCoreView
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Business scenario: Live PK battle page
 *
 * Related APIs:
 * - CoHostStore - Cross-room connection management (initiate/accept/reject/exit connection)
 * - BattleStore - PK battle management (initiate/accept/reject/exit PK)
 * - BattleStore.battleState - PK state (currentBattleInfo, battleUsers, battleScore)
 *
 * Anchor: Push stream + barrage + likes + gift animation + initiate connection + initiate PK + PK score display
 * Audience: Pull stream + barrage + gifts + likes + PK status display
 */
class LivePKActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLivePkBinding
    private lateinit var role: Role
    private lateinit var liveID: String

    private var isLiveActive = false
    private var isCoHostConnected = false
    private var isBattling = false
    private var currentBattleID: String? = null
    private var connectedHostLiveID: String? = null
    private var liveCoreView: LiveCoreView? = null

    /** Permission request launcher */
    private val permissionLauncher: ActivityResultLauncher<Array<String>> =
        PermissionHelper.registerPermissions(this) { allGranted, _ ->
            if (allGranted) {
                openCameraAndMicrophone()
            } else {
                Toast.makeText(this, getString(R.string.permission_camera_mic_denied),
                    Toast.LENGTH_LONG).show()
                finish()
            }
        }

    // Store instances (initialized after entering live)
    private var coHostStore: CoHostStore? = null
    private var battleStore: BattleStore? = null
    private var giftStore: GiftStore? = null

    // PK score data
    private data class ScoreEntry(val userID: String, val userName: String, var score: Int, val isMe: Boolean)
    private val battleScoreEntries = mutableListOf<ScoreEntry>()
    private val scoreLabels = mutableListOf<TextView>()

    // PK countdown
    private val handler = Handler(Looper.getMainLooper())
    private var pkEndTime: Long = 0
    private var pkTimerRunnable: Runnable? = null

    // MARK: - Listeners

    private val liveListListener = object : LiveListListener() {
        override fun onLiveEnded(liveID: String, reason: LiveEndedReason, anchorID: String) {
            if (liveID == this@LivePKActivity.liveID && role == Role.AUDIENCE) {
                runOnUiThread {
                    isLiveActive = false
                    stopPKTimer()
                    Toast.makeText(this@LivePKActivity,
                        getString(R.string.basicStreaming_status_ended), Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
        }

        override fun onKickedOutOfLive(liveID: String, reason: LiveKickedOutReason, anchorID: String) {
            if (liveID == this@LivePKActivity.liveID) {
                runOnUiThread {
                    isLiveActive = false
                    stopPKTimer()
                    Toast.makeText(this@LivePKActivity,
                        getString(R.string.basicStreaming_status_ended), Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
        }
    }

    /** Gift event listener - play gift animation */
    private val giftListener = object : GiftListener() {
        override fun onReceiveGift(liveID: String, gift: Gift, count: Int, sender: LiveUserInfo) {
            runOnUiThread {
                binding.giftAnimationView.playGiftAnimation(gift, count, sender)
            }
        }
    }

    /** CoHost connection event listener */
    private val coHostListener = object : CoHostListener() {
        override fun onCoHostRequestReceived(inviter: SeatUserInfo, extensionInfo: String) {
            runOnUiThread { showCoHostRequestAlert(inviter) }
        }

        override fun onCoHostRequestAccepted(invitee: SeatUserInfo) {
            runOnUiThread {
                isCoHostConnected = true
                connectedHostLiveID = invitee.liveID
                updateCoHostButtonState()
                updateConnectionStatus()
                val name = if (invitee.userName.isNullOrEmpty()) invitee.userID else invitee.userName
                Toast.makeText(this@LivePKActivity,
                    getString(R.string.livePK_coHost_request_accepted, name), Toast.LENGTH_SHORT).show()
            }
        }

        override fun onCoHostRequestRejected(invitee: SeatUserInfo) {
            val name = if (invitee.userName.isNullOrEmpty()) invitee.userID else invitee.userName
            runOnUiThread {
                Toast.makeText(this@LivePKActivity,
                    getString(R.string.livePK_coHost_request_rejected, name), Toast.LENGTH_SHORT).show()
            }
        }

        override fun onCoHostRequestTimeout(inviter: SeatUserInfo, invitee: SeatUserInfo) {
            runOnUiThread {
                Toast.makeText(this@LivePKActivity,
                    getString(R.string.livePK_coHost_request_timeout), Toast.LENGTH_SHORT).show()
            }
        }

        override fun onCoHostRequestCancelled(inviter: SeatUserInfo, invitee: SeatUserInfo?) {
            runOnUiThread {
                Toast.makeText(this@LivePKActivity,
                    getString(R.string.livePK_coHost_request_cancelled), Toast.LENGTH_SHORT).show()
            }
        }

        override fun onCoHostUserJoined(userInfo: SeatUserInfo) {
            runOnUiThread {
                isCoHostConnected = true
                connectedHostLiveID = userInfo.liveID
                updateCoHostButtonState()
                updateConnectionStatus()
                Toast.makeText(this@LivePKActivity,
                    getString(R.string.livePK_coHost_connected), Toast.LENGTH_SHORT).show()
            }
        }

        override fun onCoHostUserLeft(userInfo: SeatUserInfo) {
            runOnUiThread {
                isCoHostConnected = false
                connectedHostLiveID = null
                updateCoHostButtonState()
                updateConnectionStatus()
                val name = if (userInfo.userName.isNullOrEmpty()) userInfo.userID else userInfo.userName
                Toast.makeText(this@LivePKActivity,
                    getString(R.string.livePK_coHost_userLeft, name), Toast.LENGTH_SHORT).show()
                if (isBattling) {
                    handleBattleEnded()
                }
            }
        }
    }

    /** PK battle event listener */
    private val battleListener = object : BattleListener() {
        override fun onBattleStarted(battleInfo: BattleInfo, inviter: SeatUserInfo, invitees: List<SeatUserInfo>) {
            val battleUsers = mutableListOf<SeatUserInfo>()
            battleUsers.add(inviter)
            battleUsers.addAll(invitees)
            runOnUiThread { handleBattleStarted(battleInfo, battleUsers) }
        }

        override fun onBattleEnded(battleInfo: BattleInfo, reason: BattleEndedReason?) {
            runOnUiThread {
                handleBattleEnded()
                Toast.makeText(this@LivePKActivity,
                    getString(R.string.livePK_battle_ended), Toast.LENGTH_SHORT).show()
            }
        }

        override fun onBattleRequestReceived(battleID: String, inviter: SeatUserInfo, invitee: SeatUserInfo) {
            runOnUiThread { showBattleRequestAlert(battleID, inviter) }
        }

        override fun onBattleRequestAccept(battleID: String, inviter: SeatUserInfo, invitee: SeatUserInfo) {}
        override fun onBattleRequestReject(battleID: String, inviter: SeatUserInfo, invitee: SeatUserInfo) {}
        override fun onBattleRequestTimeout(battleID: String, inviter: SeatUserInfo, invitee: SeatUserInfo) {}

        override fun onUserJoinBattle(battleID: String, battleUser: SeatUserInfo) {}

        override fun onUserExitBattle(battleID: String, battleUser: SeatUserInfo) {}
    }

    // MARK: - Lifecycle

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocalizedManager.applyLanguage(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityLivePkBinding.inflate(layoutInflater)
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
        giftStore?.removeGiftListener(giftListener)
        coHostStore?.removeCoHostListener(coHostListener)
        battleStore?.removeBattleListener(battleListener)
        binding.barrageView.release()
        binding.likeButton.release()
        stopPKTimer()
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
        binding.bottomInteractionArea.visibility = View.VISIBLE

        // Initialize barrage and likes
        binding.barrageView.initialize(liveID)
        binding.likeButton.initialize(liveID)

        // Initialize gifts
        giftStore = GiftStore.create(liveID)
        giftStore?.addGiftListener(giftListener)

        // Initialize CoHost and Battle Stores
        coHostStore = CoHostStore.create(liveID)
        battleStore = BattleStore.create(liveID)
        coHostStore?.addCoHostListener(coHostListener)
        battleStore?.addBattleListener(battleListener)

        // Show gift entry for audience
        if (role == Role.AUDIENCE) {
            binding.btnGift.visibility = View.VISIBLE
            binding.btnGift.setOnClickListener { showGiftPanel() }
        }

        // Show connection and PK buttons for anchor
        if (role == Role.ANCHOR) {
            binding.btnCoHost.visibility = View.VISIBLE
            binding.btnCoHost.setOnClickListener { onCoHostButtonTapped() }
            binding.btnBattle.visibility = View.VISIBLE
            binding.btnBattle.setOnClickListener { onBattleButtonTapped() }
        }

        // Subscribe to barrage messages
        val barrageStore = io.trtc.tuikit.atomicxcore.api.barrage.BarrageStore.create(liveID)
        lifecycleScope.launch {
            barrageStore.barrageState.messageList.collectLatest { messages ->
                binding.barrageView.updateMessages(messages)
            }
        }

        // Subscribe to PK state (score updates)
        lifecycleScope.launch {
            battleStore?.battleState?.currentBattleInfo?.collectLatest { battleInfo ->
                val state = battleStore?.battleState ?: return@collectLatest
                handleBattleStateUpdate(state)
            }
        }
        lifecycleScope.launch {
            battleStore?.battleState?.battleScore?.collectLatest { _ ->
                val state = battleStore?.battleState ?: return@collectLatest
                if (isBattling) updateBattleScores(state)
            }
        }

        // Audience side: subscribe to connection state changes
        if (role == Role.AUDIENCE) {
            lifecycleScope.launch {
                coHostStore?.coHostState?.coHostStatus?.collectLatest { status ->
                    val wasConnected = isCoHostConnected
                    isCoHostConnected = status == CoHostStatus.CONNECTED
                    if (wasConnected != isCoHostConnected) {
                        runOnUiThread { updateConnectionStatus() }
                    }
                }
            }
        }

        giftStore?.refreshUsableGifts(null)
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
        PermissionHelper.requestCameraAndMicrophone(this, permissionLauncher) {
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

    // MARK: - Panels

    private fun showDeviceSettingPanel() {
        val deviceView = DeviceSettingView(this)
        val panel = SettingPanelController.newInstance(getString(R.string.deviceSetting_title))
        panel.contentViewProvider = { deviceView }
        panel.show(this)
    }

    private fun showGiftPanel() {
        val giftPanel = GiftPanelView(this)
        giftPanel.initialize(liveID)

        lifecycleScope.launch {
            giftStore?.giftState?.usableGifts?.collectLatest { categories ->
                giftPanel.updateGifts(categories.flatMap { it.giftList })
            }
        }

        giftPanel.onSendGiftResult = { code, message ->
            if (code != 0) {
                Toast.makeText(this, getString(R.string.basicStreaming_status_failed, message),
                    Toast.LENGTH_SHORT).show()
            }
        }

        val panel = SettingPanelController.newInstance(getString(R.string.interactive_gift_title), 380, darkMode = true)
        panel.contentViewProvider = { giftPanel }
        panel.show(this)
    }

    // MARK: - Create / Join / End Live

    private fun createLive() {
        Toast.makeText(this, getString(R.string.basicStreaming_status_creating), Toast.LENGTH_SHORT).show()
        binding.btnStartLive.isEnabled = false

        val liveInfo = LiveInfo().apply {
            this.liveID = this@LivePKActivity.liveID
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

        // Exit PK and connection first
        if (isBattling && currentBattleID != null) {
            battleStore?.exitBattle(currentBattleID!!, completionHandler { _, _ ->
                runOnUiThread { handleBattleEnded() }
            })
        }
        if (isCoHostConnected) {
            coHostStore?.exitHostConnection(completionHandler { _, _ ->
                runOnUiThread {
                    isCoHostConnected = false
                    connectedHostLiveID = null
                }
            })
        }

        LiveListStore.shared().endLive(stopLiveCompletionHandler { code, message ->
            runOnUiThread {
                if (code == 0) {
                    DeviceStore.shared().closeLocalCamera()
                    DeviceStore.shared().closeLocalMicrophone()
                    isLiveActive = false
                    stopPKTimer()
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
                    stopPKTimer()
                    finish()
                } else {
                    Toast.makeText(this, getString(R.string.basicStreaming_status_failed, message),
                        Toast.LENGTH_SHORT).show()
                }
            }
        })
    }

    // MARK: - Anchor Connection Operations

    private fun onCoHostButtonTapped() {
        if (isCoHostConnected) {
            confirmExitCoHost()
        } else {
            showHostSelectionPanel()
        }
    }

    private fun showHostSelectionPanel() {
        val userListView = CoHostUserListView(this)
        userListView.initialize(liveID)
        userListView.onSelectHost = { liveInfo ->
            requestCoHostConnection(liveInfo.liveID)
        }
        userListView.onEmptyList = {
            Toast.makeText(this, getString(R.string.livePK_coHost_emptyList), Toast.LENGTH_SHORT).show()
        }
        userListView.onLoadError = { error ->
            Toast.makeText(this, getString(R.string.basicStreaming_status_failed, error), Toast.LENGTH_SHORT).show()
        }

        val panel = SettingPanelController.newInstance(getString(R.string.livePK_coHost_selectHost), 400, darkMode = true)
        panel.contentViewProvider = { userListView }
        panel.show(this)
    }

    private fun requestCoHostConnection(targetLiveID: String) {
        Toast.makeText(this, getString(R.string.livePK_coHost_connecting), Toast.LENGTH_SHORT).show()

        coHostStore?.requestHostConnection(
            targetLiveID,
            CoHostLayoutTemplate.HOST_DYNAMIC_GRID,
            30, "",
            completionHandler { code, message ->
                if (code != 0) {
                    runOnUiThread {
                        Toast.makeText(this, getString(R.string.basicStreaming_status_failed, message),
                            Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
    }

    private fun confirmExitCoHost() {
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.livePK_coHost_disconnect))
            .setMessage(getString(R.string.livePK_coHost_confirm_disconnect))
            .setPositiveButton(getString(R.string.common_confirm)) { _, _ -> exitCoHost() }
            .setNegativeButton(getString(R.string.common_cancel), null)
            .show()
    }

    private fun exitCoHost() {
        coHostStore?.exitHostConnection(completionHandler { code, message ->
            runOnUiThread {
                if (code == 0) {
                    isCoHostConnected = false
                    connectedHostLiveID = null
                    updateCoHostButtonState()
                    updateConnectionStatus()
                    Toast.makeText(this, getString(R.string.livePK_coHost_disconnected), Toast.LENGTH_SHORT).show()

                    if (isBattling && currentBattleID != null) {
                        battleStore?.exitBattle(currentBattleID!!, completionHandler { _, _ ->
                            runOnUiThread { handleBattleEnded() }
                        })
                    }
                } else {
                    Toast.makeText(this, getString(R.string.basicStreaming_status_failed, message),
                        Toast.LENGTH_SHORT).show()
                }
            }
        })
    }

    // MARK: - PK Battle Operations

    private fun onBattleButtonTapped() {
        if (isBattling) {
            confirmEndBattle()
        } else {
            startBattle()
        }
    }

    private fun startBattle() {
        if (!isCoHostConnected) return

        val currentUserID = LoginStore.shared.loginState.loginUserInfo.value?.userID ?: ""
        val connectedUsers = coHostStore?.coHostState?.connected?.value ?: emptyList()
        val userIDList = connectedUsers.map { it.userID }.filter { it != currentUserID }

        if (userIDList.isEmpty()) {
            Toast.makeText(this, getString(R.string.livePK_coHost_emptyList), Toast.LENGTH_SHORT).show()
            return
        }

        Toast.makeText(this, getString(R.string.livePK_battle_requesting), Toast.LENGTH_SHORT).show()

        val config = BattleConfig(30, true, "")
        battleStore?.requestBattle(config, userIDList, 10, object : BattleRequestCallback {
            override fun onSuccess(battleInfo: BattleInfo, resultMap: Map<String, Int>) {
                // PK request sent; waiting for response
            }
            override fun onError(code: Int, desc: String) {
                runOnUiThread {
                    Toast.makeText(this@LivePKActivity,
                        getString(R.string.basicStreaming_status_failed, desc), Toast.LENGTH_SHORT).show()
                }
            }
        })
    }

    private fun confirmEndBattle() {
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.livePK_battle_end))
            .setMessage(getString(R.string.livePK_battle_confirm_end))
            .setPositiveButton(getString(R.string.common_confirm)) { _, _ -> exitBattle() }
            .setNegativeButton(getString(R.string.common_cancel), null)
            .show()
    }

    private fun exitBattle() {
        val battleID = currentBattleID ?: return
        battleStore?.exitBattle(battleID, completionHandler { code, message ->
            runOnUiThread {
                if (code == 0) {
                    handleBattleEnded()
                    Toast.makeText(this, getString(R.string.livePK_battle_ended), Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, getString(R.string.basicStreaming_status_failed, message),
                        Toast.LENGTH_SHORT).show()
                }
            }
        })
    }

    // MARK: - Connection Request Dialog

    private fun showCoHostRequestAlert(inviter: SeatUserInfo) {
        val name = if (inviter.userName.isNullOrEmpty()) inviter.userID else inviter.userName
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.livePK_coHost_connect))
            .setMessage(getString(R.string.livePK_coHost_request_received, name))
            .setPositiveButton(getString(R.string.coGuest_application_accept)) { _, _ ->
                coHostStore?.acceptHostConnection(inviter.liveID, completionHandler { code, message ->
                    if (code != 0) {
                        runOnUiThread {
                            Toast.makeText(this, getString(R.string.basicStreaming_status_failed, message),
                                Toast.LENGTH_SHORT).show()
                        }
                    }
                })
            }
            .setNegativeButton(getString(R.string.coGuest_application_reject)) { _, _ ->
                coHostStore?.rejectHostConnection(inviter.liveID, completionHandler { _, _ -> })
            }
            .show()
    }

    // MARK: - PK Request Dialog

    private fun showBattleRequestAlert(battleID: String, inviter: SeatUserInfo) {
        val name = if (inviter.userName.isNullOrEmpty()) inviter.userID else inviter.userName
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.livePK_battle_title))
            .setMessage(getString(R.string.livePK_battle_request_received, name))
            .setPositiveButton(getString(R.string.coGuest_application_accept)) { _, _ ->
                battleStore?.acceptBattle(battleID, completionHandler { code, message ->
                    if (code != 0) {
                        runOnUiThread {
                            Toast.makeText(this, getString(R.string.basicStreaming_status_failed, message),
                                Toast.LENGTH_SHORT).show()
                        }
                    }
                })
            }
            .setNegativeButton(getString(R.string.coGuest_application_reject)) { _, _ ->
                battleStore?.rejectBattle(battleID, completionHandler { _, _ -> })
            }
            .show()
    }

    // MARK: - PK State Handling

    private fun handleBattleStateUpdate(state: BattleState) {
        val battleInfo = state.currentBattleInfo.value
        if (battleInfo != null && battleInfo.battleID.isNotEmpty()) {
            val battleUsers = state.battleUsers.value
            if (!isBattling) {
                handleBattleStarted(battleInfo, battleUsers)
            } else if (battleUsers.size != battleScoreEntries.size) {
                rebuildScoreViews(battleUsers)
            }
            updateBattleScores(state)
        } else if (isBattling) {
            handleBattleEnded()
        }
    }

    private fun handleBattleStarted(battleInfo: BattleInfo, battleUsers: List<SeatUserInfo>) {
        isBattling = true
        currentBattleID = battleInfo.battleID

        // Calculate PK end time
        pkEndTime = when {
            battleInfo.endTime > 0 -> battleInfo.endTime
            battleInfo.startTime > 0 && battleInfo.config.duration > 0 ->
                battleInfo.startTime + battleInfo.config.duration
            battleInfo.config.duration > 0 ->
                System.currentTimeMillis() / 1000 + battleInfo.config.duration
            else -> 0
        }

        rebuildScoreViews(battleUsers)
        binding.pkScoreView.visibility = View.VISIBLE

        // Update PK button
        if (role == Role.ANCHOR) {
            binding.btnBattle.alpha = 0.6f
            binding.btnBattle.backgroundTintList = ColorStateList.valueOf(Color.GRAY)
        }

        startPKTimer()
        Toast.makeText(this, getString(R.string.livePK_battle_started), Toast.LENGTH_SHORT).show()
    }

    private fun handleBattleEnded() {
        if (!isBattling) return
        isBattling = false
        stopPKTimer()

        showBattleResult()

        if (role == Role.ANCHOR) {
            binding.btnBattle.alpha = 1f
            binding.btnBattle.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#FFF44336"))
            updateCoHostButtonState()
        }

        currentBattleID = null
    }

    private fun rebuildScoreViews(battleUsers: List<SeatUserInfo>) {
        binding.scoreContainer.removeAllViews()
        battleScoreEntries.clear()
        scoreLabels.clear()

        val dp = resources.displayMetrics.density
        val currentUserID = LoginStore.shared.loginState.loginUserInfo.value?.userID ?: ""
        val userColors = intArrayOf(
            Color.parseColor("#2196F3"), Color.parseColor("#F44336"),
            Color.parseColor("#4CAF50"), Color.parseColor("#9C27B0"),
            Color.parseColor("#FF9800")
        )

        for ((index, user) in battleUsers.withIndex()) {
            val isMe = user.userID == currentUserID
            val color = userColors[index % userColors.size]

            // Separator
            if (index > 0) {
                val separator = TextView(this).apply {
                    text = ":"
                    textSize = 16f
                    setTextColor(Color.parseColor("#99FFFFFF"))
                    setTypeface(typeface, Typeface.BOLD)
                    gravity = Gravity.CENTER
                    layoutParams = LinearLayout.LayoutParams(
                        (12 * dp).toInt(), LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                }
                binding.scoreContainer.addView(separator)
            }

            val container = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            val nameLabel = TextView(this).apply {
                text = if (isMe) getString(R.string.livePK_battle_me) else user.userName
                textSize = 10f
                setTextColor(Color.argb(204, Color.red(color), Color.green(color), Color.blue(color)))
                gravity = Gravity.CENTER
                maxLines = 1
            }
            container.addView(nameLabel)

            val scoreLabel = TextView(this).apply {
                text = "0"
                textSize = 24f
                setTextColor(color)
                setTypeface(Typeface.MONOSPACE, Typeface.BOLD)
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = (2 * dp).toInt() }
            }
            container.addView(scoreLabel)
            scoreLabels.add(scoreLabel)

            binding.scoreContainer.addView(container)
            battleScoreEntries.add(ScoreEntry(user.userID, user.userName ?: "", 0, isMe))
        }
    }

    private fun updateBattleScores(state: BattleState) {
        val scoreMap = state.battleScore.value
        for ((index, entry) in battleScoreEntries.withIndex()) {
            val score = scoreMap[entry.userID] ?: 0
            battleScoreEntries[index] = entry.copy(score = score)
            if (index < scoreLabels.size) {
                scoreLabels[index].text = "$score"
            }
        }
    }

    private fun showBattleResult() {
        val myScore = battleScoreEntries.find { it.isMe }?.score ?: 0
        val maxScore = battleScoreEntries.maxOfOrNull { it.score } ?: 0
        val maxCount = battleScoreEntries.count { it.score == maxScore }

        when {
            maxCount == battleScoreEntries.size -> {
                binding.pkStatusLabel.text = getString(R.string.livePK_battle_draw)
                binding.pkStatusLabel.setTextColor(Color.WHITE)
            }
            myScore == maxScore -> {
                binding.pkStatusLabel.text = getString(R.string.livePK_battle_win)
                binding.pkStatusLabel.setTextColor(Color.parseColor("#FFFFEB3B"))
            }
            else -> {
                binding.pkStatusLabel.text = getString(R.string.livePK_battle_lose)
                binding.pkStatusLabel.setTextColor(Color.GRAY)
            }
        }

        // Highlight winner
        for ((index, entry) in battleScoreEntries.withIndex()) {
            if (entry.score == maxScore && maxCount < battleScoreEntries.size) {
                if (index < scoreLabels.size) {
                    scoreLabels[index].setTextColor(Color.parseColor("#FFFFEB3B"))
                }
            }
        }

        binding.pkScoreView.visibility = View.VISIBLE

        // Hide after 3 seconds
        handler.postDelayed({
            if (!isBattling) {
                binding.pkScoreView.visibility = View.GONE
                binding.pkStatusLabel.text = getString(R.string.livePK_status_battling)
                binding.pkStatusLabel.setTextColor(Color.parseColor("#FFFFEB3B"))
            }
        }, 3000)
    }

    // MARK: - PK Countdown

    private fun startPKTimer() {
        stopPKTimer()
        updatePKTimerDisplay()
        pkTimerRunnable = object : Runnable {
            override fun run() {
                updatePKTimerDisplay()
                handler.postDelayed(this, 1000)
            }
        }
        handler.postDelayed(pkTimerRunnable!!, 1000)
    }

    private fun stopPKTimer() {
        pkTimerRunnable?.let { handler.removeCallbacks(it) }
        pkTimerRunnable = null
        binding.pkTimerLabel.text = ""
    }

    private fun updatePKTimerDisplay() {
        if (pkEndTime <= 0) {
            binding.pkTimerLabel.text = ""
            return
        }

        val now = System.currentTimeMillis() / 1000
        if (now >= pkEndTime) {
            stopPKTimer()
            binding.pkTimerLabel.text = "00:00"
            return
        }

        val remaining = pkEndTime - now
        val minutes = remaining / 60
        val seconds = remaining % 60
        binding.pkTimerLabel.text = String.format("%02d:%02d", minutes, seconds)
    }

    // MARK: - UI State Updates

    private fun updateCoHostButtonState() {
        if (isCoHostConnected) {
            binding.btnCoHost.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#FF4CAF50"))
            binding.btnBattle.isEnabled = true
            binding.btnBattle.alpha = 1f
        } else {
            binding.btnCoHost.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#FFFF9800"))
            binding.btnBattle.isEnabled = false
            binding.btnBattle.alpha = 0.5f
        }
    }

    private fun updateConnectionStatus() {
        when {
            isBattling -> {
                binding.connectionStatusLabel.text = getString(R.string.livePK_status_battling)
                binding.connectionStatusLabel.setBackgroundColor(Color.parseColor("#B3F44336"))
                binding.connectionStatusLabel.visibility = View.VISIBLE
            }
            isCoHostConnected -> {
                binding.connectionStatusLabel.text = getString(R.string.livePK_status_coHostConnected)
                binding.connectionStatusLabel.setBackgroundColor(Color.parseColor("#B34CAF50"))
                binding.connectionStatusLabel.visibility = View.VISIBLE
            }
            else -> {
                binding.connectionStatusLabel.visibility = View.GONE
            }
        }
    }

    // MARK: - Navigation

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

    override fun onBackPressed() {
        when {
            role == Role.ANCHOR && isLiveActive -> endLiveAndGoBack()
            role == Role.ANCHOR && !isLiveActive -> {
                DeviceStore.shared().closeLocalCamera()
                DeviceStore.shared().closeLocalMicrophone()
                super.onBackPressed()
            }
            role == Role.AUDIENCE && isLiveActive -> leaveLiveAndGoBack()
            else -> super.onBackPressed()
        }
    }

    // MARK: - Cleanup

    private fun cleanupLiveSession() {
        if (!isLiveActive) return
        stopPKTimer()

        when (role) {
            Role.ANCHOR -> {
                if (isBattling && currentBattleID != null) {
                    battleStore?.exitBattle(currentBattleID!!, null)
                }
                if (isCoHostConnected) {
                    coHostStore?.exitHostConnection(null)
                }
                DeviceStore.shared().closeLocalCamera()
                DeviceStore.shared().closeLocalMicrophone()
                LiveListStore.shared().endLive(null)
            }
            Role.AUDIENCE -> {
                LiveListStore.shared().leaveLive(null)
            }
        }
        isLiveActive = false
    }
}
