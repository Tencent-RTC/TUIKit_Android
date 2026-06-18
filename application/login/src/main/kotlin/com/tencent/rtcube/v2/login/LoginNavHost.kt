package com.tencent.rtcube.v2.login

import android.app.Activity
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.tencent.rtcube.v2.login.components.model.LoginError
import com.tencent.rtcube.v2.login.components.model.LoginResult
import com.tencent.rtcube.v2.login.components.views.PrivacyBottomDialog
import com.tencent.rtcube.v2.login.debugauth.DebugAuthScreen
import com.tencent.rtcube.v2.login.debugauth.store.DebugAuthStore
import com.tencent.rtcube.v2.login.devmenu.DevMenuScreen
import com.tencent.rtcube.v2.login.emailverify.EmailInviteCodeScreen
import com.tencent.rtcube.v2.login.emailverify.EmailVerifyScreen
import com.tencent.rtcube.v2.login.emailverify.store.EmailInviteCodeStore
import com.tencent.rtcube.v2.login.emailverify.store.EmailVerifyStore
import com.tencent.rtcube.v2.login.hiddenconfig.HiddenConfigScreen
import com.tencent.rtcube.v2.login.hiddenconfig.QRCodeScanScreen
import com.tencent.rtcube.v2.login.hiddenconfig.store.HiddenConfigStore
import com.tencent.rtcube.v2.login.invitecode.InviteCodeScreen
import com.tencent.rtcube.v2.login.invitecode.store.InviteCodeStore
import com.tencent.rtcube.v2.login.ioaauth.IOAAuthContract
import com.tencent.rtcube.v2.login.phoneverify.PhoneVerifyScreen
import com.tencent.rtcube.v2.login.phoneverify.store.PhoneVerifyStore
import com.tencent.rtcube.v2.login.profile.ProfileScreen
import com.tencent.rtcube.v2.login.profile.store.ProfileStore
import kotlinx.coroutines.flow.collectLatest

internal object LoginRoutes {
    const val DEV_MENU = "dev_menu"
    const val PHONE_VERIFY = "phone_verify"
    const val EMAIL_VERIFY = "email_verify"
    const val EMAIL_INVITE_CODE = "email_invite_code"
    const val IOA_AUTH = "ioa_auth"
    const val INVITE_CODE = "invite_code"
    const val DEBUG_AUTH = "debug_auth"
    const val PROFILE = "profile"
    const val HIDDEN_CONFIG = "hidden_config"
    const val QR_SCAN = "qr_scan"
    const val QR_SCAN_RESULT_KEY = "qr_scan_result"
}

@Composable
internal fun LoginNavHost(
    navController: NavHostController,
    startMode: LoginMode,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val activity = LocalLoginActivity.current
    val viewModel: LoginViewModel = viewModel()

    val startDestination = remember(startMode) {
        when (startMode) {
            LoginMode.PHONE_VERIFY -> LoginRoutes.PHONE_VERIFY
            LoginMode.EMAIL_VERIFY -> LoginRoutes.EMAIL_VERIFY
            LoginMode.IOA_AUTH -> LoginRoutes.IOA_AUTH
            LoginMode.INVITE_CODE -> LoginRoutes.INVITE_CODE
            LoginMode.DEBUG_AUTH -> LoginRoutes.DEBUG_AUTH
            LoginMode.MENU -> LoginRoutes.DEV_MENU
            LoginMode.TOKEN_AUTH -> LoginRoutes.PHONE_VERIFY // TOKEN_AUTH have no UI
        }
    }

    fun handleLoginModeChange(mode: LoginMode) {
        LoginEntry.switchConfig(mode)
        LoginEntry.markLoggedIn(mode)
    }

    fun handleLoginSuccess(loginResult: LoginResult, isDebugMode: Boolean, loginMode: LoginMode? = null) {
        val resultWithMode = if (loginMode != null && loginResult.loginMode == null) {
            loginResult.copy(loginMode = loginMode)
        } else {
            loginResult
        }

        if (isDebugMode) {
            finishWithResult(activity, Result.success(resultWithMode))
            onFinish()
            return
        }

        if (resultWithMode.userModel.name.isEmpty() || resultWithMode.userModel.avatar.isEmpty()) {
            val store = ProfileStore(resultWithMode.userModel)
            viewModel.profileStore = store
            store.onProfileCompleted = { updatedUser ->
                val updatedResult = resultWithMode.copy(userModel = updatedUser)
                finishWithResult(activity, Result.success(updatedResult))
                onFinish()
            }
            navController.navigate(LoginRoutes.PROFILE) {
                popUpTo(navController.graph.startDestinationId) { inclusive = true }
            }
        } else {
            finishWithResult(activity, Result.success(resultWithMode))
            onFinish()
        }
    }

    fun handleLoginError(error: Throwable) {
        finishWithResult(activity, Result.failure(error))
        onFinish()
    }

    BackHandler {
        if (navController.previousBackStackEntry != null) {
            navController.popBackStack()
        } else {
            finishWithResult(activity, Result.failure(LoginError.Cancelled))
            onFinish()
        }
    }

    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier
    ) {
        composable(LoginRoutes.DEV_MENU) {
            if (!BuildConfig.RTCUBE_FULL) {
                val store = remember {
                    viewModel.debugAuthStore ?: DebugAuthStore().also {
                        viewModel.debugAuthStore = it
                    }
                }
                val state by store.state.collectAsState()

                LaunchedEffect(store) {
                    handleLoginModeChange(LoginMode.DEBUG_AUTH)
                    store.clearResult()
                    store.resultFlow.collectLatest { result ->
                        result.onSuccess { loginResult ->
                            handleLoginSuccess(loginResult, isDebugMode = true)
                        }.onFailure { error ->
                            handleLoginError(error)
                        }
                    }
                }

                DebugAuthScreen(
                    state = state,
                    onUserIdChange = store::updateUserId,
                    onLogin = store::login,
                )
                return@composable
            }

            val versionName = remember {
                runCatching {
                    activity.packageManager.getPackageInfo(activity.packageName, 0).versionName
                }.getOrDefault("unknown")
            }
            var showIOATerms by rememberSaveable { mutableStateOf(false) }
            var isAutoLoginEnabled by rememberSaveable { mutableStateOf(LoginEntry.isAutoLoginEnabled) }
            var currentEnvironment by rememberSaveable { mutableStateOf(LoginEntry.currentEnvironment) }

            DevMenuScreen(
                versionName = versionName ?: "unknown",
                isAutoLoginEnabled = isAutoLoginEnabled,
                currentEnvironment = currentEnvironment,
                onTriggerHiddenConfig = { navController.navigate(LoginRoutes.HIDDEN_CONFIG) },
                onAutoLoginToggle = {
                    val newValue = !LoginEntry.isAutoLoginEnabled
                    LoginEntry.isAutoLoginEnabled = newValue
                    isAutoLoginEnabled = newValue
                },
                onEnvironmentToggle = {
                    val newEnv = if (currentEnvironment == ServerEnvironment.PRODUCTION) {
                        ServerEnvironment.TEST
                    } else {
                        ServerEnvironment.PRODUCTION
                    }
                    if (newEnv == ServerEnvironment.PRODUCTION && LoginEntry.hasAppliedTestEnvironment) {
                        Toast.makeText(activity, activity.getString(R.string.login_menu_env_changed_restart), Toast.LENGTH_LONG)
                            .show()
                        return@DevMenuScreen
                    }
                    LoginEntry.currentEnvironment = newEnv
                    currentEnvironment = LoginEntry.currentEnvironment
                },
                onPhoneLogin = {
                    handleLoginModeChange(LoginMode.PHONE_VERIFY)
                    navController.navigate(LoginRoutes.PHONE_VERIFY)
                },
                onEmailLogin = {
                    handleLoginModeChange(LoginMode.EMAIL_VERIFY)
                    navController.navigate(LoginRoutes.EMAIL_VERIFY)
                },
                onIOALogin = { showIOATerms = true },
                onInviteLogin = {
                    handleLoginModeChange(LoginMode.INVITE_CODE)
                    navController.navigate(LoginRoutes.INVITE_CODE)
                },
                onDebugLogin = {
                    handleLoginModeChange(LoginMode.DEBUG_AUTH)
                    navController.navigate(LoginRoutes.DEBUG_AUTH)
                }
            )

            if (showIOATerms) {
                PrivacyBottomDialog(
                    onAgree = {
                        showIOATerms = false
                        handleLoginModeChange(LoginMode.IOA_AUTH)
                        navController.navigate(LoginRoutes.IOA_AUTH)
                    },
                    onDismiss = { showIOATerms = false }
                )
            }
        }

        composable(LoginRoutes.PHONE_VERIFY) {
            val store = remember {
                viewModel.phoneVerifyStore ?: PhoneVerifyStore().also {
                    viewModel.phoneVerifyStore = it
                }
            }
            val state by store.state.collectAsState()

            LaunchedEffect(store) {
                store.resultFlow.collectLatest { result ->
                    result.onSuccess { loginResult ->
                        handleLoginSuccess(loginResult, isDebugMode = false, loginMode = LoginMode.PHONE_VERIFY)
                    }.onFailure { error ->
                        handleLoginError(error)
                    }
                }
            }

            PhoneVerifyScreen(
                state = state,
                onPhoneChange = store::updatePhoneNumber,
                onCodeChange = store::updateVerifyCode,
                onGetCode = { store.sendVerifyCode(activity) },
                onLogin = store::login,
                onIOALogin = {
                    handleLoginModeChange(LoginMode.IOA_AUTH)
                    navController.navigate(LoginRoutes.IOA_AUTH)
                },
                onTriggerHiddenConfig = { navController.navigate(LoginRoutes.HIDDEN_CONFIG) },
            )
        }

        composable(LoginRoutes.EMAIL_VERIFY) {
            val store = remember {
                viewModel.emailVerifyStore ?: EmailVerifyStore().also {
                    viewModel.emailVerifyStore = it
                }
            }
            val state by store.state.collectAsState()

            LaunchedEffect(store) {
                store.resultFlow.collectLatest { result ->
                    result.onSuccess { loginResult ->
                        handleLoginSuccess(loginResult, isDebugMode = false, loginMode = LoginMode.EMAIL_VERIFY)
                    }.onFailure { error ->
                        handleLoginError(error)
                    }
                }
            }

            var showIOATerms by rememberSaveable { mutableStateOf(false) }

            EmailVerifyScreen(
                state = state,
                onEmailChange = store::updateEmail,
                onContinue = {
                    store.getInviteCodeAndNavigate {
                        navController.navigate(LoginRoutes.EMAIL_INVITE_CODE)
                    }
                },
                onEnterInviteCode = {
                    handleLoginModeChange(LoginMode.INVITE_CODE)
                    navController.navigate(LoginRoutes.INVITE_CODE)
                },
                onIOALogin = { showIOATerms = true },
                onTriggerHiddenConfig = { navController.navigate(LoginRoutes.HIDDEN_CONFIG) },
            )

            if (showIOATerms) {
                PrivacyBottomDialog(
                    onAgree = {
                        showIOATerms = false
                        handleLoginModeChange(LoginMode.IOA_AUTH)
                        navController.navigate(LoginRoutes.IOA_AUTH)
                    },
                    onDismiss = { showIOATerms = false }
                )
            }
        }

        composable(LoginRoutes.EMAIL_INVITE_CODE) {
            val emailVerifyStore = remember {
                viewModel.emailVerifyStore ?: EmailVerifyStore().also {
                    viewModel.emailVerifyStore = it
                }
            }
            val email = emailVerifyStore.state.collectAsState().value.email

            val store = remember(email) {
                viewModel.emailInviteCodeStore?.takeIf { it.state.value.email == email }
                    ?: EmailInviteCodeStore(email).also {
                        viewModel.emailInviteCodeStore?.destroy()
                        viewModel.emailInviteCodeStore = it
                    }
            }
            val state by store.state.collectAsState()

            LaunchedEffect(store) {
                store.resultFlow.collectLatest { result ->
                    result.onSuccess { loginResult ->
                        handleLoginSuccess(loginResult, isDebugMode = false, loginMode = LoginMode.EMAIL_VERIFY)
                    }.onFailure { error ->
                        handleLoginError(error)
                    }
                }
            }

            EmailInviteCodeScreen(
                state = state,
                onCodeChange = store::updateInviteCode,
                onSubmit = store::submitInviteCode,
                onResend = store::resendInviteCode,
                onBack = { navController.popBackStack() },
                onAgreeTermsChange = store::toggleAgreeTerms,
                onMarketingChange = store::toggleMarketing,
                onDismissTermsTooltip = store::hideTermsTooltip
            )
        }

        composable(LoginRoutes.IOA_AUTH) {
            val launched = rememberSaveable { mutableStateOf(false) }
            val launcher = rememberLauncherForActivityResult(IOAAuthContract()) { result ->
                result.onSuccess { loginResult ->
                    handleLoginSuccess(loginResult, isDebugMode = false, loginMode = LoginMode.IOA_AUTH)
                }.onFailure { error ->
                    if (error is LoginError.Cancelled && navController.previousBackStackEntry != null) {
                        navController.popBackStack()
                    } else {
                        handleLoginError(error)
                    }
                }
            }

            LaunchedEffect(Unit) {
                if (!launched.value) {
                    launched.value = true
                    launcher.launch(Unit)
                }
            }
        }

        composable(LoginRoutes.INVITE_CODE) {
            val store = remember {
                viewModel.inviteCodeStore ?: InviteCodeStore().also {
                    viewModel.inviteCodeStore = it
                }
            }
            val state by store.state.collectAsState()

            LaunchedEffect(store) {
                store.resultFlow.collectLatest { result ->
                    result.onSuccess { loginResult ->
                        handleLoginSuccess(loginResult, isDebugMode = false, loginMode = LoginMode.INVITE_CODE)
                    }.onFailure { error ->
                        handleLoginError(error)
                    }
                }
            }

            InviteCodeScreen(
                state = state,
                onCodeChange = store::updateInviteCode,
                onLogin = store::login,
                onAgreeTermsChange = store::updateAgreeTerms,
                onDismissTermsTooltip = store::dismissTermsTooltip,
                onBack = { navController.popBackStack() }
            )
        }

        composable(LoginRoutes.DEBUG_AUTH) {
            val store = remember {
                viewModel.debugAuthStore ?: DebugAuthStore().also {
                    viewModel.debugAuthStore = it
                }
            }
            val state by store.state.collectAsState()

            LaunchedEffect(store) {
                store.clearResult()
                store.resultFlow.collectLatest { result ->
                    result.onSuccess { loginResult ->
                        handleLoginSuccess(loginResult, isDebugMode = true)
                    }.onFailure { error ->
                        handleLoginError(error)
                    }
                }
            }

            DebugAuthScreen(
                state = state,
                onUserIdChange = store::updateUserId,
                onLogin = store::login,
            )
        }

        composable(LoginRoutes.HIDDEN_CONFIG) { backStackEntry ->
            val store = remember {
                viewModel.hiddenConfigStore ?: HiddenConfigStore().also {
                    viewModel.hiddenConfigStore = it
                }
            }
            val state by store.state.collectAsState()

            LaunchedEffect(store) {
                store.resultFlow.collectLatest { result ->
                    result.onSuccess { loginResult ->
                        handleLoginSuccess(loginResult, isDebugMode = true)
                    }.onFailure { error ->
                        handleLoginError(error)
                    }
                }
            }

            val savedStateHandle = backStackEntry.savedStateHandle
            val qrResultFlow = remember(savedStateHandle) {
                savedStateHandle.getStateFlow<String?>(LoginRoutes.QR_SCAN_RESULT_KEY, null)
            }
            val qrResult by qrResultFlow.collectAsState()
            LaunchedEffect(qrResult) {
                val value = qrResult
                if (!value.isNullOrEmpty()) {
                    store.handleQrCodeResult(value)
                    savedStateHandle[LoginRoutes.QR_SCAN_RESULT_KEY] = null
                }
            }

            HiddenConfigScreen(
                state = state,
                isLoading = state.isLoading,
                onSdkAppIdChange = store::updateSdkAppId,
                onUserIdChange = store::updateUserId,
                onUserSigChange = store::updateUserSig,
                onConfirm = {
                    if (state.isLoading) return@HiddenConfigScreen
                    val credentials = store.buildCredentials()
                    LoginEntry.switchSDKAppID(credentials)
                    store.loginWithCredentials(credentials)
                },
                onRestoreDefault = {
                    store.restoreDefault()
                    LoginEntry.resetSDKAppID()
                    navController.popBackStack()
                },
                onBack = {
                    store.restoreDefault()
                    navController.popBackStack()
                },
                onScanQRCode = { navController.navigate(LoginRoutes.QR_SCAN) },
            )
        }

        composable(LoginRoutes.QR_SCAN) {
            val handled = remember { java.util.concurrent.atomic.AtomicBoolean(false) }
            QRCodeScanScreen(
                onResult = { result ->
                    if (handled.compareAndSet(false, true)) {
                        navController.previousBackStackEntry?.savedStateHandle?.set(LoginRoutes.QR_SCAN_RESULT_KEY, result)
                        navController.popBackStack()
                    }
                },
                onCancel = {
                    if (handled.compareAndSet(false, true)) {
                        navController.popBackStack()
                    }
                }
            )
        }

        composable(LoginRoutes.PROFILE) {
            val store = viewModel.profileStore
            if (store != null) {
                val state by store.state.collectAsState()

                ProfileScreen(
                    state = state,
                    onNicknameChange = store::updateNickname,
                    onAvatarClick = store::showAvatarDialog,
                    onAvatarSelect = store::selectAvatarInDialog,
                    onAvatarConfirm = store::dismissAvatarDialog,
                    onAvatarDialogDismiss = store::dismissAvatarDialog,
                    onRegister = store::updateProfile,
                    onToastShown = store::clearToast
                )
            }
        }
    }
}

private fun finishWithResult(activity: Activity, result: Result<LoginResult>) {
    result.onSuccess { loginResult ->
        val data = Intent().apply {
            putExtra(LoginActivity.RESULT_LOGIN_RESULT, loginResult)
        }
        activity.setResult(Activity.RESULT_OK, data)
    }.onFailure {
        activity.setResult(Activity.RESULT_CANCELED)
    }
}
