package com.tencent.rtcube.v2

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.tencent.rtcube.assembly.AppAssembly
import com.tencent.rtcube.assembly.AppTarget
import com.tencent.rtcube.assembly.ModuleEnvironment
import com.tencent.rtcube.v2.debug.GenerateTestUserSig
import com.tencent.rtcube.v2.launchanim.LaunchAnimationCoordinator
import com.tencent.rtcube.v2.launchanim.LaunchAnimationOverlay
import com.tencent.rtcube.v2.login.LoginEntry
import com.tencent.rtcube.v2.login.LoginMode
import com.tencent.rtcube.v2.main.ModuleRegistry
import com.tencent.rtcube.v2.main.domestic.DomesticEntranceScreen
import com.tencent.rtcube.v2.main.overseas.OverSeasEntranceScreen
import com.tencent.rtcube.v2.mine.MineEntry
import com.tencent.rtcube.v2.utils.KeyMetrics
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    // Whether an automatic token login attempt has been triggered after entering the Activity (only once per process, not reset).
    private var hasAttemptedAutoLogin = false

    // Whether the login page is currently showing; prevents launching it multiple times
    private var isLoginLaunched = false

    private lateinit var loginLauncher: ActivityResultLauncher<Intent>

    private val loginMode: LoginMode
        get() = when (AppTargetResolver.getAppTarget()) {
            AppTarget.LAB -> LoginMode.MENU
            AppTarget.OVERSEAS -> LoginMode.EMAIL_VERIFY
            else -> LoginMode.PHONE_VERIFY
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (!isTaskRoot
            && intent.hasCategory(Intent.CATEGORY_LAUNCHER)
            && intent.action != null
            && intent.action.equals(Intent.ACTION_MAIN)
        ) {
            finish()
            return
        }
        loginLauncher = LoginEntry.registerLauncher(this) { result ->
            isLoginLaunched = false
            result.onSuccess { loginResult ->
                Log.d(TAG, "Login success: ${loginResult.userModel.userId}")
                KeyMetrics.reportAtomicMetrics(KeyMetrics.DEMO_LOGIN_SUCCESS)
                showMain()
            }.onFailure { error ->
                Log.e(TAG, "Login failed/cancelled: $error")
                Toast.makeText(this, error.localizedMessage ?: "Login failed", Toast.LENGTH_SHORT).show()
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    LoginEntry.tokenExpiredFlow.collectLatest {
                        Log.d(TAG, "Token expired notification received")
                        if (hasAttemptedAutoLogin) {
                            LaunchAnimationCoordinator.reLaunchAnimation()
                            launchLogin()
                        }
                    }
                }
                launch {
                    LoginEntry.kickedOfflineFlow.collectLatest {
                        Log.d(TAG, "Kicked offline notification received")
                        if (hasAttemptedAutoLogin) {
                            launchLogin()
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        attemptAutoLoginIfNeeded()
    }

    private fun attemptAutoLoginIfNeeded() {
        if (hasAttemptedAutoLogin) return
        hasAttemptedAutoLogin = true

        if (!LoginEntry.hasLoggedIn) {
            Log.d(TAG, "First launch, no valid token, launching login page")
            launchLogin()
            return
        }

        // auto login
        LoginEntry.performTokenAuth { result ->
            result.onSuccess { loginResult ->
                Log.d(TAG, "Token auto-login success: ${loginResult.userModel.userId}")
                KeyMetrics.reportAtomicMetrics(KeyMetrics.DEMO_LOGIN_SUCCESS)
                showMain()
            }.onFailure { error ->
                Log.e(TAG, "Token auto-login failed: $error")
                if (!isLoginLaunched) {
                    launchLogin()
                }
            }
        }
    }

    private fun launchLogin() {
        if (isLoginLaunched) {
            Log.d(TAG, "Login page already launched, skip")
            return
        }
        isLoginLaunched = true
        LoginEntry.launch(loginMode, loginLauncher, this)
    }

    private fun showMain() {
        val target = AppTargetResolver.getAppTarget()

        val appEnvironment = ModuleEnvironment(
            context = applicationContext,
            appTarget = target,
            liveLicenseKey = GenerateTestUserSig.LIVE_LICENSE_KEY,
            liveLicenseUrl = GenerateTestUserSig.LIVE_LICENSE_URL,
            karaokeLicenseKey = GenerateTestUserSig.KARAOKE_LICENSE_KEY,
            karaokeLicenseUrl = GenerateTestUserSig.KARAOKE_LICENSE_URL,
            getCurrentUserModel = { LoginEntry.currentUser.value },
        )

        // must be called before ModuleRegistry
        AppConfig.init(this)

        ModuleRegistry.reset()
        AppAssembly.allModuleProviders(applicationContext, target).forEach { provider ->
            provider.setup(appEnvironment)
            ModuleRegistry.register(provider)
        }

        val willPlayLaunchAnimation = target != AppTarget.LAB &&
                LaunchAnimationCoordinator.shouldPlayOnEnterHome(this)

        setContent {
            val context = LocalContext.current
            var showMainContent by remember { mutableStateOf(!willPlayLaunchAnimation) }

            LaunchedEffect(showMainContent) {
                if (showMainContent) {
                    AppConfig.performInitialRiskCheckOnce(this@MainActivity, LoginEntry.currentUser.value)
                }
            }

            Box(modifier = Modifier.fillMaxSize()) {
                if (showMainContent) {
                    if (target == AppTarget.OVERSEAS) {
                        OverSeasEntranceScreen(
                            onAvatarClick = { MineEntry.startMineActivity(context = context, onLogout = { launchLogin() }) },
                            onTrackAnalytics = { event -> trackAnalytics(event) }
                        )
                    } else {
                        DomesticEntranceScreen(
                            onAvatarClick = { MineEntry.startMineActivity(context = context, onLogout = { launchLogin() }) },
                            onTriggerFaceAuthDialog = { onFaceAuthDialogShowed ->
                                AppConfig.performRiskCheck(
                                    activity = this@MainActivity,
                                    userModel = LoginEntry.currentUser.value,
                                    onFaceAuthDialogShowed = onFaceAuthDialogShowed,
                                )
                            },
                            onTrackAnalytics = { event -> trackAnalytics(event) },
                        )
                    }
                }

                if (willPlayLaunchAnimation) {
                    LaunchAnimationOverlay(
                        onAnimationFinished = { showMainContent = true }
                    )
                }
            }
        }
    }

    private fun trackAnalytics(moduleName: String) {
        AppConfig.trackAnalytics(moduleName)
        reportKeyMetrics(moduleName)
    }

    private fun reportKeyMetrics(moduleName: String) {
        val event = when (moduleName) {
            "call" -> KeyMetrics.DEMO_CLICK_CALL
            "live" -> KeyMetrics.DEMO_CLICK_LIVE
            "conference" -> KeyMetrics.DEMO_CLICK_ROOM
            else -> return
        }
        KeyMetrics.reportAtomicMetrics(event)
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}
