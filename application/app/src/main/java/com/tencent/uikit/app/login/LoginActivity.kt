package com.tencent.uikit.app.login

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.widget.SwitchCompat
import androidx.lifecycle.lifecycleScope
import com.tencent.imsdk.v2.V2TIMManager
import com.tencent.imsdk.v2.V2TIMValueCallback
import com.tencent.mmkv.MMKV
import com.tencent.qcloud.tuikit.debug.GenerateTestUserSig
import com.tencent.uikit.app.R
import com.tencent.uikit.app.common.utils.KeyMetrics
import com.tencent.uikit.app.common.utils.DEMO_LOGIN_SUCCESS
import com.tencent.uikit.app.main.BaseActivity
import com.tencent.uikit.app.main.MainActivity
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.login.LoginStore
import kotlinx.coroutines.launch

class LoginActivity : BaseActivity() {
    companion object {
        private const val TAG = "LoginActivity"
    }

    private lateinit var editUserId: EditText
    private lateinit var scEnableTestEnv: SwitchCompat
    private lateinit var scAutoLogin: SwitchCompat
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!isTaskRoot
            && intent.hasCategory(Intent.CATEGORY_LAUNCHER)
            && intent.action != null
            && intent.action.equals(Intent.ACTION_MAIN)
        ) {
            finish()
            return
        }
        setContentView(R.layout.app_activity_login)
        initView()

        val userID = MMKV.defaultMMKV().getString("userId", "")
        val autoLogin = MMKV.defaultMMKV().getBoolean("autoLogin", false)
        if (!userID.isNullOrEmpty() && autoLogin) {
            login(userID)
        }
    }

    private fun initView() {
        editUserId = findViewById(R.id.et_userId)
        scAutoLogin = findViewById(R.id.sc_auto_login)
        scEnableTestEnv = findViewById(R.id.sc_enable_test_env)
        editUserId.setText(MMKV.defaultMMKV().getString("userId", ""))
        findViewById<View>(R.id.btn_login).setOnClickListener {
            val userId = editUserId.text.toString().trim()
            login(userId)
        }
        scEnableTestEnv.setOnCheckedChangeListener { buttonView, isChecked ->
            switchTestEnv(isChecked)
        }
        scAutoLogin.isChecked = MMKV.defaultMMKV().getBoolean("autoLogin", false)
        scAutoLogin.setOnCheckedChangeListener { buttonView, isChecked ->
            MMKV.defaultMMKV().putBoolean("autoLogin", isChecked)
        }
    }

    private fun login(userId: String) {
        if (userId.isEmpty()) {
            Toast.makeText(this,
                getString(R.string.app_user_id_is_empty), Toast.LENGTH_SHORT).show()
            return
        }
        val context = this
        val userSig = GenerateTestUserSig.genTestUserSig(userId)
        LoginStore.shared.login(this, GenerateTestUserSig.SDKAPPID, userId, userSig, object : CompletionHandler {
            override fun onSuccess() {
                Log.i(TAG, "login onSuccess")
                MMKV.defaultMMKV().putString("userId", userId)
                getUserInfo()

                KeyMetrics.reportAtomicMetrics(DEMO_LOGIN_SUCCESS)
            }

            override fun onFailure(code: Int, desc: String) {
                Toast.makeText(context,
                    getString(R.string.app_toast_login_fail, code, desc), Toast.LENGTH_SHORT).show()
                Log.e(TAG, "login fail errorCode: $code errorMessage:$desc")
            }
        })
    }

    private fun getUserInfo() {
        lifecycleScope.launch {
            LoginStore.shared.loginState.loginUserInfo.collect { loginUserInfo ->
                loginUserInfo?.let {
                    if (it.userID.isEmpty()) {
                        return@collect
                    }
                    if (it.nickname.isNullOrEmpty() || it.avatarURL.isNullOrEmpty()) {
                        val intent = Intent(this@LoginActivity, RegisterActivity::class.java)
                        startActivity(intent)
                        finish()
                    } else {
                        val intent = Intent(this@LoginActivity, MainActivity::class.java)
                        startActivity(intent)
                        finish()
                    }
                }
            }
        }
    }

    private fun switchTestEnv(enableTestEnv: Boolean) {
        V2TIMManager.getInstance().callExperimentalAPI(
            "setTestEnvironment", enableTestEnv, object : V2TIMValueCallback<Any?> {
                override fun onSuccess(o: Any?) {
                    Log.i(TAG, "V2TIMManager setNetEnv success. enableTestEnv：$enableTestEnv")
                }

                override fun onError(code: Int, desc: String?) {
                    Log.i(TAG, "V2TIMManager setNetEnv fail. enableTestEnv：$enableTestEnv")
                }
            })
    }
}