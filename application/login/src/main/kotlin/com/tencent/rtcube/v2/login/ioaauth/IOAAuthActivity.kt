package com.tencent.rtcube.v2.login.ioaauth

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import android.window.OnBackInvokedDispatcher
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.tencent.rtcube.v2.login.R
import com.tencent.rtcube.v2.login.components.model.LoginError
import com.tencent.rtcube.v2.login.components.model.LoginResult
import com.tencent.rtcube.v2.login.ioaauth.store.IOAAuthStore
import kotlinx.coroutines.launch

internal class IOAAuthActivity : ComponentActivity() {

    private lateinit var store: IOAAuthStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Treat system back as user-cancel (Android 13+).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_DEFAULT
            ) { finishCancelled() }
        }

        store = IOAAuthStore().apply {
            initSdk()
            onActivityCreate(this@IOAAuthActivity)
        }

        lifecycleScope.launch {
            store.resultFlow.collect { result ->
                result
                    .onSuccess(::finishSuccess)
                    .onFailure { error ->
                        if (error is LoginError.Cancelled) finishCancelled()
                        else finishError(error.message ?: "iOA login failed")
                    }
            }
        }

        setContent { LoadingContent(store) }
        store.startLogin()
    }

    override fun onResume() {
        super.onResume()
        store.onActivityResume(this)
    }

    override fun onPause() {
        store.onActivityPause()
        super.onPause()
    }

    override fun onDestroy() {
        store.onActivityDestroy()
        store.destroy()
        super.onDestroy()
    }

    @Deprecated("Deprecated in Java")
    @Suppress("MissingSuperCall")
    override fun onBackPressed() {
        // Android 12 and below: align with the Tiramisu+ back callback above.
        finishCancelled()
    }

    private fun finishSuccess(loginResult: LoginResult) {
        setResult(Activity.RESULT_OK, Intent().putExtra(IOAAuthContract.EXTRA_LOGIN_RESULT, loginResult))
        finish()
    }

    private fun finishCancelled() {
        setResult(Activity.RESULT_CANCELED)
        finish()
    }

    private fun finishError(message: String) {
        setResult(Activity.RESULT_OK, Intent().putExtra(IOAAuthContract.EXTRA_ERROR_MESSAGE, message))
        finish()
    }
}

@Composable
private fun LoadingContent(store: IOAAuthStore) {
    val context = LocalContext.current
    val state by store.state.collectAsState()

    LaunchedEffect(state.toastMessage) {
        if (state.toastMessage.isNotEmpty()) {
            Toast.makeText(context, state.toastMessage, Toast.LENGTH_SHORT).show()
        }
    }

    if (!state.isLoading) return

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colorResource(R.color.login_loading_bg)),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(
                modifier = Modifier
                    .height(20.dp)
                    .padding(end = 8.dp),
                color = Color.White,
                strokeWidth = 2.dp
            )
            Text(
                text = stringResource(R.string.login_ioa_loading),
                color = Color.White,
                fontSize = 14.sp
            )
        }
    }
}
