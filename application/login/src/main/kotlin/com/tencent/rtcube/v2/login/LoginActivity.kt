package com.tencent.rtcube.v2.login

import android.os.Build
import android.os.Bundle
import android.window.OnBackInvokedDispatcher
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController

class LoginActivity : ComponentActivity() {

    companion object {
        const val EXTRA_LOGIN_MODE = "login_mode"
        const val RESULT_LOGIN_RESULT = "result_login_result"
    }

    private var navController: NavController? = null

    private var loginScreenShownNotified = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Disable system back gesture (Android 13+).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT) {
                handleBackPress()
            }
        }

        val modeName = intent.getStringExtra(EXTRA_LOGIN_MODE) ?: LoginMode.DEBUG_AUTH.name
        val mode = LoginMode.valueOf(modeName)

        setContent {
            val nav = rememberNavController()
            navController = nav

            CompositionLocalProvider(
                LocalLoginActivity provides this
            ) {
                LoginNavHost(
                    navController = nav,
                    startMode = mode,
                    onFinish = { finish() }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (!loginScreenShownNotified) {
            loginScreenShownNotified = true
            LoginEntry.privacyAlertDialogHandler?.invoke()
        }
    }

    @Deprecated("Deprecated in Java")
    @Suppress("MissingSuperCall")
    override fun onBackPressed() {
        // Android 12 and below.
        handleBackPress()
    }

    private fun handleBackPress() {
        val nav = navController
        if (nav != null && nav.previousBackStackEntry != null) {
            nav.popBackStack()
        }
    }
}

val LocalLoginActivity = compositionLocalOf<LoginActivity> {
    error("No LoginActivity provided")
}