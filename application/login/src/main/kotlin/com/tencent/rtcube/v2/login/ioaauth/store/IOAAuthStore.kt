package com.tencent.rtcube.v2.login.ioaauth.store

import android.app.Activity
import android.util.Log
import com.tencent.itlogin.component.ITLoginAccountTypeTencent
import com.tencent.itlogin.component.ITLoginAuthListener
import com.tencent.itlogin.component.ITLoginBaseActivityManager
import com.tencent.itlogin.network.ITLoginError
import com.tencent.itlogin.sdk.ITLoginListener
import com.tencent.itlogin.sdk.ITLoginSDK
import com.tencent.rtcube.v2.login.LoginEntry
import com.tencent.rtcube.v2.login.LoginSubStore
import com.tencent.rtcube.v2.login.R
import com.tencent.rtcube.v2.login.components.model.LoginError
import com.tencent.rtcube.v2.login.components.model.LoginResult
import com.tencent.rtcube.v2.login.components.service.LoginManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class IOAAuthStore : LoginSubStore, ITLoginListener, ITLoginAuthListener {

    private val _state = MutableStateFlow(IOAAuthState())
    val state: StateFlow<IOAAuthState> = _state.asStateFlow()

    private val _resultFlow = MutableSharedFlow<Result<LoginResult>>(replay = 1)
    override val resultFlow: SharedFlow<Result<LoginResult>> = _resultFlow.asSharedFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val logoutChannel = Channel<Unit>(Channel.CONFLATED)
    private val loginChannel = Channel<String?>(Channel.CONFLATED)

    @Volatile
    private var sdkInitialized = false

    fun initSdk() {
        if (sdkInitialized) return
        ITLoginBaseActivityManager.getInstance().init(LoginEntry.appContext)
        ITLoginSDK.setIsPermissionGranted(true)
        ITLoginSDK.setLoginAccountType(ITLoginAccountTypeTencent)
        ITLoginBaseActivityManager.getInstance().addITLoginListener(this)
        ITLoginBaseActivityManager.getInstance().addITLoginAuthListener(this)
        sdkInitialized = true
    }

    fun startLogin() {
        scope.launch {
            if (ITLoginSDK.getLoginInfo() != null) {
                ITLoginSDK.logout()
                logoutChannel.receive()
            }

            // Wait for iOA SDK login result (SDK UI is triggered by onActivityResume)
            val ticket = loginChannel.receive() ?: return@launch

            _state.update { it.copy(isLoading = true) }
            LoginManager.loginByIOA(ticket)
                .onSuccess { loginResult ->
                    Log.i(TAG, "Backend IOA login success")
                    _state.update { it.copy(isLoading = false) }
                    _resultFlow.tryEmit(Result.success(loginResult))
                }
                .onFailure { error ->
                    Log.e(TAG, "Backend IOA login failed", error)
                    val msg = (error as? LoginError)?.message
                        ?: LoginEntry.appContext.getString(R.string.login_error_ioa_login_failed)
                    _state.update { it.copy(isLoading = false, toastMessage = msg) }
                }
        }
    }

    fun onActivityCreate(activity: Activity) {
        ITLoginBaseActivityManager.getInstance().onActivityCreate(activity)
    }

    fun onActivityResume(activity: Activity) {
        ITLoginBaseActivityManager.getInstance().onActivityResume(activity)
    }

    fun onActivityPause() {
        ITLoginBaseActivityManager.getInstance().onActivityPause()
    }

    fun onActivityDestroy() {
        ITLoginBaseActivityManager.getInstance().onActivityDestroy()
    }

    fun destroy() {
        ITLoginBaseActivityManager.getInstance().removeITLoginListener(this)
        ITLoginBaseActivityManager.getInstance().removeITLoginAuthListener(this)
        scope.cancel()
    }

    override fun onLoginSuccess() {
        loginChannel.trySend(ITLoginSDK.getLoginInfo()?.key)
    }

    override fun onLoginFailure(error: ITLoginError) {
        Log.e(TAG, "iOA SDK login failed: code=${error.key}, msg=${error.msg}")
        loginChannel.trySend(null)
        val msg = error.msg ?: LoginEntry.appContext.getString(R.string.login_error_ioa_login_failed)
        _resultFlow.tryEmit(Result.failure(LoginError.LoginFailed(-1, msg)))
    }

    override fun onLoginCancel() {
        Log.i(TAG, "iOA SDK login cancelled by user")
        loginChannel.trySend(null)
        _resultFlow.tryEmit(Result.failure(LoginError.Cancelled))
    }

    override fun onFinishLogout(isLogoutOK: Boolean) {
        Log.i(TAG, "iOA SDK logout finished: success=$isLogoutOK")
        logoutChannel.trySend(Unit)
    }

    override fun onAuthSuccess() {
        Log.i(TAG, "iOA auth refresh success")
    }

    override fun onAuthFailed(error: ITLoginError) {
        Log.e(TAG, "iOA auth refresh failed: code=${error.key}, msg=${error.msg}")
    }

    companion object {
        private const val TAG = "IOAAuthStore"

        fun logoutIfNeeded() {
            // Ensure SDK is initialized; auto-login path may skip startLogin().
            try {
                ITLoginBaseActivityManager.getInstance().init(LoginEntry.appContext)
                if (ITLoginSDK.getLoginInfo() != null) {
                    ITLoginSDK.logout()
                }
            } catch (t: Throwable) {
                Log.w(TAG, "logoutIfNeeded ignored: ${t.message}")
            }
        }
    }
}