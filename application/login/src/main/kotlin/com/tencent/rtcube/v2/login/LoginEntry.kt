package com.tencent.rtcube.v2.login

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import com.tencent.imsdk.v2.V2TIMManager
import com.tencent.rtcube.v2.login.LoginEntry.isAutoLoginEnabled
import com.tencent.rtcube.v2.login.components.model.LoginError
import com.tencent.rtcube.v2.login.components.model.LoginResult
import com.tencent.rtcube.v2.login.components.model.UserModel
import com.tencent.rtcube.v2.login.components.service.BlackListResult
import com.tencent.rtcube.v2.login.components.service.LoginManager
import com.tencent.rtcube.v2.login.components.service.LoginService
import com.tencent.rtcube.v2.login.components.service.TUILoginListenerHandler
import com.tencent.rtcube.v2.login.debugauth.store.DebugAuthStore
import com.tencent.rtcube.v2.login.hiddenconfig.HiddenConfigCredentials
import com.tencent.rtcube.v2.login.ioaauth.store.IOAAuthStore
import com.tencent.rtcube.v2.login.tokenauth.store.TokenAuthStore
import com.tencent.rtcube.v2.login.tokenauth.utils.TokenCacheManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Login module public API
 */
object LoginEntry {
    private const val TAG = "LoginEntry"
    private const val PREF_RTCUBE_LOGIN = "rtcube_login"
    private const val KEY_LOGGED_IN_MODE = "loggedInMode"
    private const val KEY_AUTO_LOGIN_ENABLED = "autoLoginEnabled"

    lateinit var appContext: Context
        private set

    /** current config */
    var config: LoginConfig = LoginConfig.default
        private set

    val currentUser: kotlinx.coroutines.flow.StateFlow<UserModel?>
        get() = LoginManager.currentUserFlow

    private var entryScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var isInitialized = false
    private var primaryConfig: LoginConfig = LoginConfig.default
    private var debugConfig: LoginConfig? = null
    private var loginStatusPrefs: SharedPreferences? = null

    private val pendingActions = mutableListOf<() -> Unit>()

    /** Currently active HiddenConfig credentials
     * (non-null after switching SDKAppID, or null when "restore to default" or no switch has been made)
     */
    private val _hiddenCredentialsFlow = kotlinx.coroutines.flow.MutableStateFlow<HiddenConfigCredentials?>(null)
    val hiddenCredentials: kotlinx.coroutines.flow.StateFlow<HiddenConfigCredentials?>
        get() = _hiddenCredentialsFlow

    /** Token / UserSig expiration.*/
    private val _tokenExpiredFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val tokenExpiredFlow: SharedFlow<Unit> = _tokenExpiredFlow.asSharedFlow()
    fun onUserTokenExpired() {
        _tokenExpiredFlow.tryEmit(Unit)
    }

    private val _kickedOfflineFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val kickedOfflineFlow: SharedFlow<Unit> = _kickedOfflineFlow.asSharedFlow()
    fun onKickedOffline() {
        _kickedOfflineFlow.tryEmit(Unit)
    }

    var userSigGenerator: ((identifier: String, sdkAppId: Int, secretKey: String) -> String)? = null

    /**
     * Default value of [isAutoLoginEnabled] when the user has never toggled the switch.
     */
    var initialAutoLoginEnabled: Boolean = false

    var isAutoLoginEnabled: Boolean
        get() = loginStatusPrefs?.getBoolean(KEY_AUTO_LOGIN_ENABLED, initialAutoLoginEnabled)
            ?: initialAutoLoginEnabled
        set(value) {
            loginStatusPrefs?.edit()?.putBoolean(KEY_AUTO_LOGIN_ENABLED, value)?.apply()
        }

    /** Current environment. Defaults to PRODUCTION on every app launch. */
    var currentEnvironment: ServerEnvironment = ServerEnvironment.PRODUCTION

    /** Whether TEST env has been applied in this process (locks TEST→PROD until restart). */
    var hasAppliedTestEnvironment: Boolean = false
        private set

    /** Environment switch（only for Dev）*/
    var onEnvironmentChanged: ((ServerEnvironment) -> Unit)? = null

    /**
     * Privacy agreement handler
     * linkType value（from AgreementNavigator ）：
     *   - "privacy"
     *   - "privacySummary"
     *   - "agreement"
     *   - "termsOfService"
     */
    var privacyLinkHandler: ((linkType: String, context: Context) -> Unit)? = null
    var privacyAlertDialogHandler: (() -> Unit)? = null

    private val _privacyAgreementAcceptedFlow = kotlinx.coroutines.flow.MutableStateFlow(false)
    val privacyAgreementAcceptedFlow: kotlinx.coroutines.flow.StateFlow<Boolean>
        get() = _privacyAgreementAcceptedFlow

    fun markPrivacyAgreementAccepted() {
        _privacyAgreementAcceptedFlow.value = true
    }

    /**
     * Whether the user has ever logged in successfully (persistent flag, does not check token expiry).
     * Only cleared when user explicitly logs out or token auto-login completely fails.
     */
    val hasLoggedIn: Boolean
        get() = loggedInMode != null

    val loggedInMode: LoginMode?
        get() {
            val name = loginStatusPrefs?.getString(KEY_LOGGED_IN_MODE, null) ?: return null
            return runCatching { LoginMode.valueOf(name) }.getOrNull()
        }

    /**
     * when login success, persist login mode only when auto-login is enabled;
     * otherwise remove the stored mode so that next launch won't auto-login.
     */
    fun markLoggedIn(mode: LoginMode) {
        loginStatusPrefs?.edit()?.apply {
            if (isAutoLoginEnabled) putString(KEY_LOGGED_IN_MODE, mode.name) else remove(KEY_LOGGED_IN_MODE)
        }?.apply()
    }

    /** Called on logout or token auto-login failure  */
    private fun clearLoggedIn() {
        loginStatusPrefs?.edit()?.remove(KEY_LOGGED_IN_MODE)?.apply()
    }

    /**
     * Login entrance, unified initialization of the login module (supports repeated calls)
     * parameters see [LoginConfig]
     */
    fun initialize(
        application: Application,
        baseUrl: String = "",
        testBaseUrl: String = "",
        apaasAppId: String = "",
        sdkAppId: Int = 0,
        secretKey: String = "",
        debugSdkAppId: Int = 0,
        debugSecretKey: String = "",
    ) {
        Log.i(TAG, "initialize, sdkAppId: $sdkAppId, debugSdkAppId: $debugSdkAppId")
        // register TUILogin listener (kicked offline / UserSig expired → notifyTokenExpired)
        TUILoginListenerHandler.register()

        // register LoginService to TUICore, for other modules to call via TUICore.callService
        LoginService.register()

        appContext = application.applicationContext
        TokenCacheManager.init(application)
        loginStatusPrefs = appContext.getSharedPreferences(PREF_RTCUBE_LOGIN, Context.MODE_PRIVATE)

        val newConfig = LoginConfig(
            httpBaseUrl = baseUrl,
            testBaseUrl = testBaseUrl,
            apaasAppId = apaasAppId,
            sdkAppId = sdkAppId,
            secretKey = secretKey,
            debugSdkAppId = debugSdkAppId,
            debugSecretKey = debugSecretKey,
        )

        if (debugSdkAppId != 0) {
            debugConfig = LoginConfig(
                httpBaseUrl = baseUrl,
                testBaseUrl = testBaseUrl,
                apaasAppId = apaasAppId,
                sdkAppId = debugSdkAppId,
                secretKey = debugSecretKey,
                debugSdkAppId = debugSdkAppId,
                debugSecretKey = debugSecretKey,
            )
        }

        val needsLogout = hasLoggedIn && config.sdkAppId != newConfig.sdkAppId && config != LoginConfig.default

        primaryConfig = newConfig
        applyConfig(newConfig)
        if (needsLogout) {
            logout {
                isInitialized = true
                flushPendingActions()
            }
        } else {
            isInitialized = true
            flushPendingActions()
        }
    }

    private fun applyConfig(newConfig: LoginConfig) {
        Log.d("LoginEntry", "Environment: set sdkappid: ${newConfig.sdkAppId}")
        config = newConfig
    }

    fun switchConfig(mode: LoginMode) {
        Log.i(TAG, "switchConfig, LoginMode: $mode, env: ${currentEnvironment.name}")
        var targetConfig: LoginConfig = when (mode) {
            LoginMode.DEBUG_AUTH -> debugConfig ?: return
            else -> primaryConfig
        }
        if (currentEnvironment == ServerEnvironment.TEST && targetConfig.testBaseUrl.isNotEmpty()) {
            targetConfig = targetConfig.copy(httpBaseUrl = targetConfig.testBaseUrl)
        }
        if (_hiddenCredentialsFlow.value != null) {
            _hiddenCredentialsFlow.value = null
        }
        if (targetConfig == config) {
            return
        }
        applyConfig(targetConfig)
        Log.d(TAG, "Environment: set env: ${currentEnvironment.name}")
        V2TIMManager.getInstance().unInitSDK()
        fireEnvironmentChangedIfNeeded()
    }

    fun registerLauncher(activity: ComponentActivity, callback: (Result<LoginResult>) -> Unit): ActivityResultLauncher<Intent> {
        return activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                @Suppress("DEPRECATION")
                val loginResult = result.data?.getSerializableExtra(LoginActivity.RESULT_LOGIN_RESULT) as? LoginResult
                Log.i(TAG,
                    "login success,sdkAppId: ${loginResult?.userModel?.sdkAppId}, userId: ${loginResult?.userModel?.userId}," +
                            " loginMode:${loginResult?.loginMode}"
                )
                if (loginResult != null) {
                    callback(Result.success(loginResult))
                } else {
                    callback(Result.failure(LoginError.Unknown(appContext.getString(R.string.login_error_result_parse_failed))))
                }
            } else {
                Log.i(TAG, "registerLauncher: $result")
                callback(Result.failure(LoginError.Cancelled))
            }
        }
    }

    /**
     * login start
     */
    fun launch(
        mode: LoginMode,
        launcher: ActivityResultLauncher<Intent>,
        activity: ComponentActivity,
    ) {
        runWhenInitialized {
            Log.i(TAG, "launch: $mode")
            switchConfig(mode)

            if (mode != LoginMode.MENU) {
                markLoggedIn(mode)
            }

            val intent = Intent(activity, LoginActivity::class.java).apply {
                putExtra(LoginActivity.EXTRA_LOGIN_MODE, mode.name)
            }
            launcher.launch(intent)
        }
    }

    /**
     * auto login start
     */
    fun performTokenAuth(callback: (Result<LoginResult>) -> Unit) {
        runWhenInitialized {
            Log.i(TAG, "performTokenAuth: $loggedInMode")
            val mode = loggedInMode
            if (mode != null) {
                switchConfig(mode)
            }

            // Debug login
            if (mode == LoginMode.DEBUG_AUTH) {
                val debugStore = DebugAuthStore()
                entryScope.launch {
                    val userId = TokenCacheManager.getUserId().orEmpty()
                    Log.i(TAG, "performTokenAuth, userId: $userId")
                    if (userId.isEmpty()) {
                        clearLoggedIn()
                        callback(Result.failure(LoginError.TokenExpired))
                        return@launch
                    }
                    debugStore.loginWithUserId(userId)
                    val result = debugStore.resultFlow.first()
                    result.onSuccess {
                        markLoggedIn(LoginMode.DEBUG_AUTH)
                    }.onFailure {
                        clearLoggedIn()
                        TokenCacheManager.clear()
                        LoginManager.clearLoginInfo()
                    }
                    callback(result)
                }
                return@runWhenInitialized
            }

            // auto login via token
            entryScope.launch {
                val store = TokenAuthStore(entryScope)
                store.performAutoLogin()
                val result = store.resultFlow.first()
                result.onSuccess {
                    markLoggedIn(mode ?: LoginMode.TOKEN_AUTH)
                }.onFailure {
                    clearLoggedIn()
                    TokenCacheManager.clear()
                    LoginManager.clearLoginInfo()
                }
                callback(result)
            }
        }
    }

    fun logout(callback: ((Result<Unit>) -> Unit)? = null) {
        if (currentUser.value?.isLoginByMOA() == true) {
            IOAAuthStore.logoutIfNeeded()
        }
        clearLoggedIn()
        _hiddenCredentialsFlow.value = null
        LoginManager.clearLoginInfo()
        entryScope.launch {
            val result = LoginManager.logout()
            callback?.invoke(result)
        }
    }

    /**
     * delete account
     */
    fun logoff(callback: (Result<Unit>) -> Unit) {
        entryScope.launch {
            val result = LoginManager.logoff()
            result.onSuccess {
                clearLoggedIn()
                _hiddenCredentialsFlow.value = null
            }
            callback(result)
        }
    }

    /**
     * get module blacklist, return banned module and feature
     */
    fun getUserModuleBlackList(
        callback: (Result<BlackListResult>) -> Unit,
    ) {
        val userId = LoginManager.currentUser?.userId.orEmpty()
        entryScope.launch {
            val result = LoginManager.getUserModuleBlackList(userId)
            callback(result)
        }
    }

    fun release() {
        entryScope.cancel()
        pendingActions.clear()
    }

    private fun runWhenInitialized(action: () -> Unit) {
        if (isInitialized) {
            action()
        } else {
            pendingActions += action
        }
    }

    private fun flushPendingActions() {
        val actions = pendingActions.toList()
        pendingActions.clear()
        actions.forEach { it.invoke() }
    }

    fun switchSDKAppID(credentials: HiddenConfigCredentials) {
        val sdkAppIdInt = credentials.sdkAppId.toIntOrNull()
        if (sdkAppIdInt == null) {
            Log.w(TAG, "switchSDKAppID failed: invalid sdkAppId '${credentials.sdkAppId}'")
            return
        }
        _hiddenCredentialsFlow.value = credentials

        val newConfig = config.copy(sdkAppId = sdkAppIdInt)
        applyConfig(newConfig)

        V2TIMManager.getInstance().unInitSDK()
        Log.i(TAG, "switchSDKAppID: sdkAppId=${credentials.sdkAppId}, userId=${credentials.userId}")
        fireEnvironmentChangedIfNeeded()
    }

    fun resetSDKAppID() {
        _hiddenCredentialsFlow.value = null
        applyConfig(primaryConfig)
        V2TIMManager.getInstance().unInitSDK()
        Log.i(TAG, "resetSDKAppID: restored primary sdkAppId=${primaryConfig.sdkAppId}, env=${currentEnvironment.name}")
        fireEnvironmentChangedIfNeeded()
    }

    private fun fireEnvironmentChangedIfNeeded() {
        when (currentEnvironment) {
            ServerEnvironment.TEST -> {
                hasAppliedTestEnvironment = true
                onEnvironmentChanged?.invoke(ServerEnvironment.TEST)
            }

            ServerEnvironment.PRODUCTION -> {
                if (hasAppliedTestEnvironment) {
                    val ctx = if (::appContext.isInitialized) appContext else return
                    Handler(Looper.getMainLooper()).post {
                        Toast.makeText(ctx, ctx.getString(R.string.login_menu_env_changed_restart), Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }
}
