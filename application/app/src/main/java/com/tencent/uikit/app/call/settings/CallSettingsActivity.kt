package com.tencent.uikit.app.call.settings

import android.os.Bundle
import android.widget.ImageView
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.tencent.uikit.app.R
import com.tencent.uikit.app.call.common.WindowInsetsHelper
import com.tencent.uikit.app.main.BaseActivity
import io.trtc.tuikit.atomicxcore.api.login.LoginStore
import kotlinx.coroutines.launch

class CallSettingsActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.callaccess_settings_activity)
        WindowInsetsHelper.applyStatusBarInset(findViewById(R.id.header))
        findViewById<ImageView>(R.id.back_icon).setOnClickListener { finish() }
        val settingsView = findViewById<CallSettingsView>(R.id.settings_view)
        lifecycleScope.launch {
            LoginStore.shared.loginState.loginUserInfo.collect { profile ->
                settingsView.updateProfile(profile)
            }
        }
    }
}
