package com.example.atomicxcore.scenes.interactive

import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.example.atomicxcore.R
import com.example.atomicxcore.components.AudioEffectSettingView
import com.example.atomicxcore.components.BeautySettingView
import com.example.atomicxcore.components.DeviceSettingView
import com.example.atomicxcore.components.GiftPanelView
import com.example.atomicxcore.components.LocalizedManager
import com.example.atomicxcore.components.Role
import com.example.atomicxcore.components.SettingPanelController
import com.example.atomicxcore.components.TabbedSettingView
import com.example.atomicxcore.databinding.ActivityInteractiveBinding
import com.example.atomicxcore.utils.applyStatusBarTopPadding
import com.example.atomicxcore.utils.completionHandler
import com.example.atomicxcore.utils.liveInfoCompletionHandler
import com.example.atomicxcore.utils.PermissionHelper
import com.example.atomicxcore.utils.stopLiveCompletionHandler
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.trtc.tuikit.atomicxcore.api.device.AudioEffectStore
import io.trtc.tuikit.atomicxcore.api.barrage.BarrageStore
import io.trtc.tuikit.atomicxcore.api.device.BaseBeautyStore
import io.trtc.tuikit.atomicxcore.api.device.DeviceStore
import io.trtc.tuikit.atomicxcore.api.gift.Gift
import io.trtc.tuikit.atomicxcore.api.gift.GiftCategory
import io.trtc.tuikit.atomicxcore.api.gift.GiftListener
import io.trtc.tuikit.atomicxcore.api.gift.GiftStore
import io.trtc.tuikit.atomicxcore.api.live.LiveEndedReason
import io.trtc.tuikit.atomicxcore.api.live.LiveInfo
import io.trtc.tuikit.atomicxcore.api.live.LiveKickedOutReason
import io.trtc.tuikit.atomicxcore.api.live.LiveListListener
import io.trtc.tuikit.atomicxcore.api.live.LiveListStore
import io.trtc.tuikit.atomicxcore.api.live.LiveUserInfo
import io.trtc.tuikit.atomicxcore.api.live.SeatLayoutTemplate
import io.trtc.tuikit.atomicxcore.api.view.CoreViewType
import io.trtc.tuikit.atomicxcore.api.view.LiveCoreView
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Business scenario: Real-time interaction page
 *
 * Builds on BasicStreaming by adding real-time interaction features:
 * - Barrage chat (BarrageStore)
 * - Gift system (GiftStore)
 * - Like feature (LikeStore)
 * - Beauty effects (BaseBeautyStore) — anchor only
 * - Audio effects (AudioEffectStore) — anchor only
 *
 * Related APIs:
 * - LiveListStore.shared().createLive(liveInfo, completion) - Anchor creates live
 * - LiveListStore.shared().joinLive(liveID, completion) - Audience joins live
 * - LiveListStore.shared().endLive(completion) - Anchor ends live
 * - LiveListStore.shared().leaveLive(completion) - Audience leaves live
 * - LiveListStore.shared().addLiveListListener(listener) - Live event listener
 * - DeviceStore.shared().openLocalCamera(isFront, completion) - Open camera
 * - DeviceStore.shared().openLocalMicrophone(completion) - Open microphone
 * - LiveCoreView(context, CoreViewType) - Video rendering component
 * - BarrageStore.create(liveID) - Barrage management
 * - GiftStore.create(liveID) - Gift management
 * - LikeStore.create(liveID) - Like management
 * - BaseBeautyStore.shared - Beauty management (singleton)
 * - AudioEffectStore.shared - Audio effect management (singleton)
 *
 * Provides different operations based on role:
 * - Anchor: Push stream + barrage + likes (view) + beauty + audio effects + device settings + gift animation display
 * - Audience: Pull stream + barrage + gifts + likes + gift animation display
 */
class InteractiveActivity : AppCompatActivity() {

    private lateinit var binding: ActivityInteractiveBinding
    private lateinit var role: Role
    private lateinit var liveID: String

    private var isLiveActive = false
    private var liveCoreView: LiveCoreView? = null

    // Interactive Stores (initialized after entering live)
    private var barrageStore: BarrageStore? = null
    private var giftStore: GiftStore? = null

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

    /** Live event listener */
    private val liveListListener = object : LiveListListener() {
        override fun onLiveEnded(liveID: String, reason: LiveEndedReason, anchorID: String) {
            if (liveID == this@InteractiveActivity.liveID && role == Role.AUDIENCE) {
                runOnUiThread {
                    isLiveActive = false
                    Toast.makeText(this@InteractiveActivity,
                        getString(R.string.basicStreaming_status_ended), Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
        }

        override fun onKickedOutOfLive(liveID: String, reason: LiveKickedOutReason, anchorID: String) {
            if (liveID == this@InteractiveActivity.liveID) {
                runOnUiThread {
                    isLiveActive = false
                    Toast.makeText(this@InteractiveActivity,
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

    // MARK: - Lifecycle

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocalizedManager.applyLanguage(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityInteractiveBinding.inflate(layoutInflater)
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
        binding.barrageView.release()
        binding.likeButton.release()
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

        // Create LiveCoreView
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

    // MARK: - Interactive Component Initialization

    /** Initialize interactive components after live starts */
    private fun setupInteractiveComponents() {
        // Show bottom interaction area
        binding.bottomInteractionArea.visibility = View.VISIBLE

        // Initialize barrage
        binding.barrageView.initialize(liveID)
        barrageStore = BarrageStore.create(liveID)

        // Initialize likes
        binding.likeButton.initialize(liveID)

        // Initialize gifts
        giftStore = GiftStore.create(liveID)
        giftStore?.addGiftListener(giftListener)

        // Show gift entry button for audience
        if (role == Role.AUDIENCE) {
            binding.btnGift.visibility = View.VISIBLE
            binding.btnGift.setOnClickListener { showGiftPanel() }
        }

        // Subscribe to barrage message updates
        lifecycleScope.launch {
            barrageStore?.barrageState?.messageList?.collectLatest { messages ->
                binding.barrageView.updateMessages(messages)
            }
        }

        // Subscribe to gift list updates (for gift panel)
        giftStore?.refreshUsableGifts(null)
    }

    // MARK: - Anchor Configuration

    private fun configureForAnchor() {
        // Device settings button
        binding.toolbar.inflateMenu(R.menu.menu_live_settings)
        binding.toolbar.setOnMenuItemClickListener { item ->
            if (item.itemId == R.id.action_settings) {
                showTabbedSettingPanel()
                true
            } else false
        }

        // Request permissions then open camera and microphone
        PermissionHelper.requestCameraAndMicrophone(this, permissionLauncher) {
            openCameraAndMicrophone()
        }

        // Show start live button
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

    // MARK: - Settings Panel

    /** Anchor's settings panel - Tab switching: Device / Beauty / Audio Effects */
    private fun showTabbedSettingPanel() {
        val tabbedView = TabbedSettingView(this)
        tabbedView.setTabs(listOf(
            TabbedSettingView.Tab(
                getString(R.string.deviceSetting_title),
                DeviceSettingView(this)
            ),
            TabbedSettingView.Tab(
                getString(R.string.interactive_beauty_title),
                BeautySettingView(this)
            ),
            TabbedSettingView.Tab(
                getString(R.string.interactive_audioEffect_title),
                AudioEffectSettingView(this)
            )
        ))

        val panel = SettingPanelController.newInstance(getString(R.string.interactive_settings_title))
        panel.contentViewProvider = { tabbedView }
        panel.show(this)
    }

    /** Audience's gift panel */
    private fun showGiftPanel() {
        val giftPanel = GiftPanelView(this)
        giftPanel.initialize(liveID)

        // Subscribe to gift list updates
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

        val panel = SettingPanelController.newInstance(
            getString(R.string.interactive_gift_title), 380, darkMode = true
        )
        panel.contentViewProvider = { giftPanel }
        panel.show(this)
    }

    // MARK: - Anchor: Create Live

    private fun createLive() {
        Toast.makeText(this, getString(R.string.basicStreaming_status_creating), Toast.LENGTH_SHORT).show()
        binding.btnStartLive.isEnabled = false

        val liveInfo = LiveInfo().apply {
            this.liveID = this@InteractiveActivity.liveID
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

    // MARK: - Audience: Join Live

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

    // MARK: - Anchor: End Live

    private fun endLiveAndGoBack() {
        Toast.makeText(this, getString(R.string.basicStreaming_status_ending), Toast.LENGTH_SHORT).show()

        LiveListStore.shared().endLive(stopLiveCompletionHandler { code, message ->
            runOnUiThread {
                if (code == 0) {
                    DeviceStore.shared().closeLocalCamera()
                    DeviceStore.shared().closeLocalMicrophone()
                    BaseBeautyStore.shared().reset()
                    AudioEffectStore.shared().reset()
                    isLiveActive = false
                    finish()
                } else {
                    Toast.makeText(this, getString(R.string.basicStreaming_status_failed, message),
                        Toast.LENGTH_SHORT).show()
                }
            }
        })
    }

    // MARK: - Audience: Leave Live

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

    // MARK: - UI Helpers

    private fun updateNavigationBarForLiveState() {
        if (role != Role.ANCHOR) return
        binding.toolbar.menu.clear()
        binding.toolbar.inflateMenu(R.menu.menu_live_end)
        binding.toolbar.inflateMenu(R.menu.menu_live_settings)
        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_end_live -> {
                    showEndLiveConfirm()
                    true
                }
                R.id.action_settings -> {
                    showTabbedSettingPanel()
                    true
                }
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
        when (role) {
            Role.ANCHOR -> {
                DeviceStore.shared().closeLocalCamera()
                DeviceStore.shared().closeLocalMicrophone()
                BaseBeautyStore.shared().reset()
                AudioEffectStore.shared().reset()
                LiveListStore.shared().endLive(null)
            }
            Role.AUDIENCE -> {
                LiveListStore.shared().leaveLive(null)
            }
        }
        isLiveActive = false
    }
}
