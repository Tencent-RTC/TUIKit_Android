package com.tencent.uikit.app.main

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.addCallback
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import com.tencent.qcloud.tuikit.tuicallkit.TUICallKit
import com.tencent.uikit.app.R
import com.tencent.uikit.app.call.entry.CallEntryState
import com.tencent.uikit.app.home.HomeBranchFragment
import com.tencent.uikit.app.home.HomeTabBar
import com.tencent.uikit.app.login.LoginActivity
import io.trtc.tuikit.atomicxcore.api.call.CallParticipantStatus
import io.trtc.tuikit.atomicxcore.api.call.CallStore
import io.trtc.tuikit.atomicxcore.api.login.LoginStatus
import io.trtc.tuikit.atomicxcore.api.login.LoginStore
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.json.JSONObject

class MainActivity : BaseActivity() {
    private lateinit var tabBar: HomeTabBar
    private var selectedTab = 1
    val callEntryState by lazy { CallEntryState(this) }
    var isCallActive = false
        private set
    private val conversations = io.trtc.tuikit.atomicxcore.api.conversation.ConversationListStore.create()
    private var pendingLiveJoins = 0
    val liveJoinPending get() = pendingLiveJoins > 0
    fun beginLiveJoin(): Boolean {
        if (pendingLiveJoins > 0) return false
        pendingLiveJoins++
        tabBar.updateInteractionEnabled(false)
        return true
    }

    fun endLiveJoin() {
        pendingLiveJoins = (pendingLiveJoins - 1).coerceAtLeast(0)
        if (!isDestroyed) tabBar.updateInteractionEnabled(!liveJoinPending)
        io.trtc.tuikit.atomicx.common.event.EventManager.instance.notifyEvent("app.home.live", "join", emptyMap())
    }

    private var pendingAccountExit = false
    private var redirecting = false

    override fun onCreate(savedInstanceState: Bundle?) {
        delegate.localNightMode = androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO
        super.onCreate(savedInstanceState)
        if (isFinishing) return
        val callKit = TUICallKit.createInstance(this)
        callKit.enableMuteMode(callEntryState.muteModeEnabled)
        callKit.enableIncomingBanner(callEntryState.bannerOn)
        callKit.enableFloatWindow(callEntryState.showFloatButton)
        callKit.enableVirtualBackground(callEntryState.blurBackgroundOn)
        callKit.enableAITranscriber(callEntryState.captionOn)
        enableCallMultiDeviceAbility(callKit)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.setSoftInputMode(
            android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING or android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN
        )
        setContentView(R.layout.app_activity_trtc_main)
        selectedTab = savedInstanceState?.getInt("selectedTab", 1) ?: 1
        tabBar = findViewById(R.id.home_tab_bar)
        ViewCompat.setOnApplyWindowInsetsListener(tabBar) { view, insets ->
            view.setPadding(0, 0, 0, insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom)
            insets
        }
        tabBar.bind(this)
        conversations.loadConversations()
        lifecycleScope.launch {
            conversations.state.totalUnreadCount.collect { tabBar.updateUnreadCount(it) }
        }
        lifecycleScope.launch {
            combine(
                CallStore.shared.observerState.activeCall,
                CallStore.shared.observerState.selfInfo
            ) { call, self ->
                call.callId.isNotEmpty() && self.status != CallParticipantStatus.None
            }.distinctUntilChanged().collect { active ->
                isCallActive = active
                io.trtc.tuikit.atomicx.common.event.EventManager.instance.notifyEvent(
                    "app.home.live", "call", mapOf("active" to active)
                )
            }
        }
        selectTab(selectedTab)
        onBackPressedDispatcher.addCallback(this) {
            val branch = supportFragmentManager.findFragmentByTag("branch-$selectedTab") as? HomeBranchFragment
            if (branch?.pop() != true) {
                if (selectedTab != 1) selectTab(1) else finish()
            }
        }
        lifecycleScope.launch {
            LoginStore.shared.loginState.loginStatus.collect { status ->
                if (status == LoginStatus.UNLOGIN && !redirecting) {
                    val branch = supportFragmentManager.findFragmentByTag("branch-$selectedTab") as? HomeBranchFragment
                    if (branch?.childFragmentManager?.findFragmentById(
                            R.id.home_branch_content
                        ) is com.tencent.uikit.app.live.PusherHostFragment
                    ) pendingAccountExit = true
                    else navigateToLogin()
                }
            }
        }
    }

    private fun enableCallMultiDeviceAbility(callKit: TUICallKit) {
        val request = JSONObject()
            .put("api", "enableMultiDeviceAbility")
            .put("params", JSONObject().put("enabled", true))
        callKit.callExperimentalAPI(request.toString())
    }

    fun selectTab(tab: Int) {
        if (supportFragmentManager.isStateSaved || liveJoinPending || tab !in 0..4) return
        currentFocus?.clearFocus()
        WindowCompat.getInsetsController(window, window.decorView).hide(WindowInsetsCompat.Type.ime())
        selectedTab = tab
        tabBar.updateSelection(tab)
        supportFragmentManager.beginTransaction().apply {
            supportFragmentManager.fragments.filterIsInstance<HomeBranchFragment>().forEach {
                hide(it)
                setMaxLifecycle(it, Lifecycle.State.STARTED)
            }
            val key = "branch-$tab"
            val branch = supportFragmentManager.findFragmentByTag(key) ?: HomeBranchFragment().apply {
                arguments = Bundle().apply { putInt("tab", tab) }
            }.also { add(R.id.home_content, it, key) }
            show(branch)
            setMaxLifecycle(branch, Lifecycle.State.RESUMED)
        }.commitNow()
        setHomeImmersive(
            (supportFragmentManager.findFragmentByTag(
                "branch-$tab"
            ) as? HomeBranchFragment)?.childFragmentManager?.findFragmentById(
                R.id.home_branch_content
            )?.arguments?.getBoolean("fullScreen") == true
        )
    }

    private fun navigateToLogin() {
        if (redirecting) return
        redirecting = true
        startActivity(Intent(this, LoginActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        })
        finish()
    }

    fun finishPusher() {
        if (pendingAccountExit) navigateToLogin()
        else (supportFragmentManager.findFragmentByTag("branch-$selectedTab") as? HomeBranchFragment)?.pop()
    }

    fun resetBranch() {
        (supportFragmentManager.findFragmentByTag("branch-$selectedTab") as? HomeBranchFragment)?.reset()
    }

    fun openMeeting(intent: Intent) {
        val branch = supportFragmentManager.findFragmentByTag("branch-$selectedTab") as? HomeBranchFragment
        branch?.reset()
        startActivity(intent)
    }

    fun pushPage(page: Fragment, fullScreen: Boolean = false) {
        if (!liveJoinPending) (supportFragmentManager.findFragmentByTag(
            "branch-$selectedTab"
        ) as? HomeBranchFragment)?.push(page, fullScreen)
    }

    fun setHomeImmersive(immersive: Boolean) {
        val branch = supportFragmentManager.findFragmentByTag("branch-$selectedTab") as? HomeBranchFragment
        val background = (branch?.childFragmentManager?.findFragmentById(
            R.id.home_branch_content
        )?.view?.background as? android.graphics.drawable.ColorDrawable)?.color
        val color = if (selectedTab == 1) androidx.core.content.ContextCompat.getColor(this, R.color.home_dark)
        else background ?: androidx.core.content.ContextCompat.getColor(
            this, if (selectedTab == 0) com.trtc.uikit.roomkit.R.color.roomkit_bg_canvas else R.color.home_light
        )
        tabBar.setBackgroundColor(color)
        window.navigationBarColor = color
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightNavigationBars = selectedTab != 1
        tabBar.visibility = if (immersive) View.GONE else View.VISIBLE
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = selectedTab != 1
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("selectedTab", selectedTab)
        super.onSaveInstanceState(outState)
    }
}
