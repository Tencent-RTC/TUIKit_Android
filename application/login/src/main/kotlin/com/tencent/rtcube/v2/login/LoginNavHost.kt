package com.tencent.rtcube.v2.login

import android.app.Activity
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.tencent.rtcube.v2.login.components.model.LoginResult
import com.tencent.rtcube.v2.login.debugauth.DebugAuthScreen
import com.tencent.rtcube.v2.login.debugauth.store.DebugAuthStore
import kotlinx.coroutines.flow.collectLatest

internal object LoginRoutes {
    const val DEBUG_AUTH = "debug_auth"
}

@Composable
internal fun LoginNavHost(
    navController: NavHostController,
    startMode: LoginMode,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val activity = LocalLoginActivity.current

    fun handleLoginSuccess(loginResult: LoginResult) {
        finishWithResult(activity, Result.success(loginResult))
        onFinish()
    }

    fun handleLoginError(error: Throwable) {
        finishWithResult(activity, Result.failure(error))
        onFinish()
    }

    NavHost(
        navController = navController,
        startDestination = LoginRoutes.DEBUG_AUTH,
        modifier = modifier
    ) {
        composable(LoginRoutes.DEBUG_AUTH) {
            val store = remember {
                DebugAuthStore()
            }
            val state by store.state.collectAsState()

            LaunchedEffect(store) {
                LoginEntry.switchConfig(LoginMode.DEBUG_AUTH)
                LoginEntry.markLoggedIn(LoginMode.DEBUG_AUTH)
                store.clearResult()
                store.resultFlow.collectLatest { result ->
                    result.onSuccess { loginResult ->
                        handleLoginSuccess(loginResult)
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