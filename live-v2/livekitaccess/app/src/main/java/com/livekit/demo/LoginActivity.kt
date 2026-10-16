package com.livekit.demo

import android.content.Intent
import android.os.Bundle
import android.text.InputFilter
import android.util.Log
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.livekit.demo.APPConfig.DEMO_CLICK_LIVE
import com.livekit.demo.APPConfig.DEMO_LOGIN_SUCCESS
import com.tencent.tcmediax.api.TCMediaXBase
import com.trtc.uikit.livekit.common.basic.reportAtomicMetrics
import com.trtc.uikit.livekit.common.theme.ThemeStore
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.login.LoginStore
import io.trtc.tuikit.atomicxcore.api.login.UserProfile
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class LoginActivity : AppCompatActivity() {
    private val TAG = "LoginActivity"

    private lateinit var etUserID: EditText
    private lateinit var btnLogin: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_login)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.login_root)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        ThemeStore.shared(applicationContext).followSystemTheme(false)

        APPConfig.init(applicationContext)

        etUserID = findViewById(R.id.et_user_id_input)
        btnLogin = findViewById(R.id.btn_login)

        etUserID.filters = arrayOf(
            InputFilter.LengthFilter(USER_ID_MAX_LENGTH),
            InputFilter { source, start, end, _, _, _ ->
                val accepted = StringBuilder()
                for (index in start until end) {
                    val char = source[index]
                    if (char.code < 128 && char.isLetterOrDigit()) {
                        accepted.append(char)
                    }
                }
                if (accepted.length == end - start) null else accepted.toString()
            }
        )
        etUserID.setText(APPConfig.LOGIN_USER_ID)

        btnLogin.setOnClickListener {
            val userID = etUserID.text?.toString()?.trim().orEmpty()
            if (userID.isEmpty()) {
                Toast.makeText(this, R.string.login_hint_user_id, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            login(userID)
        }
        initTencentEffect()
    }

    private fun login(loginUserID: String) {
        btnLogin.isEnabled = false
        LoginStore.shared.login(
            applicationContext,
            GenerateTestUserSig.SDKAPPID,
            loginUserID,
            GenerateTestUserSig.genTestUserSig(loginUserID),
            object : CompletionHandler {
                override fun onSuccess() {
                    Log.i(TAG, "LoginStore login success")
                    reportAtomicMetrics(DEMO_LOGIN_SUCCESS)
                    APPConfig.LOGIN_USER_ID = loginUserID
                    ensureSelfInfo(loginUserID)
                }

                override fun onFailure(code: Int, desc: String) {
                    Log.e(TAG, "LoginStore login failed, $code errorMessage:$desc")
                    runOnUiThread {
                        btnLogin.isEnabled = true
                        Toast.makeText(
                            this@LoginActivity,
                            "Login failed: $code $desc",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            })
    }

    private fun ensureSelfInfo(loginUserID: String) {
        lifecycleScope.launch {
            val loginUserInfo = withTimeoutOrNull(PROFILE_WAIT_TIMEOUT_MS) {
                LoginStore.shared.loginState.loginUserInfo.first {
                    it != null && it.userID.isNotEmpty()
                }
            }
            val nickname = loginUserInfo?.nickname
            val avatarURL = loginUserInfo?.avatarURL
            if (nickname.isNullOrEmpty() || avatarURL.isNullOrEmpty()) {
                setSelfInfo(loginUserID, nickname, avatarURL)
            } else {
                enterWaterfall()
            }
        }
    }

    private fun setSelfInfo(loginUserID: String, nickname: String?, avatarURL: String?) {
        val userProfile = UserProfile(
            userID = loginUserID,
            nickname = if (nickname.isNullOrEmpty()) getString(NICKNAME_RES_IDS.random()) else null,
            avatarURL = if (avatarURL.isNullOrEmpty()) AVATAR_URLS.random() else null
        )
        LoginStore.shared.setSelfInfo(
            userProfile,
            object : CompletionHandler {
                override fun onSuccess() {
                    Log.i(TAG, "LoginStore setSelfInfo success")
                    runOnUiThread { enterWaterfall() }
                }

                override fun onFailure(code: Int, desc: String) {
                    Log.e(TAG, "LoginStore setSelfInfo failed, $code errorMessage:$desc")
                    runOnUiThread { enterWaterfall() }
                }
            })
    }

    private fun enterWaterfall() {
        btnLogin.isEnabled = true
        reportAtomicMetrics(DEMO_CLICK_LIVE)
        startActivity(Intent(this@LoginActivity, WaterfallActivity::class.java))
        finish()
    }

    private fun initTencentEffect() {
        TCMediaXBase.getInstance().setLicense(
            this,
            GenerateTestUserSig.TENCENT_EFFECT_LICENSE_URL,
            GenerateTestUserSig.TENCENT_EFFECT_LICENSE_KEY
        ) { i: Int, s: String? ->
            Log.i(TAG, "TCMediaXBase setLicense callback, code: $i msg: $s")
        }
    }

    private companion object {
        const val PROFILE_WAIT_TIMEOUT_MS = 5000L
        const val USER_ID_MAX_LENGTH = 20

        val AVATAR_URLS = listOf(
            "https://liteav.sdk.qcloud.com/app/res/picture/voiceroom/avatar/user_avatar1.png",
            "https://liteav.sdk.qcloud.com/app/res/picture/voiceroom/avatar/user_avatar2.png",
            "https://liteav.sdk.qcloud.com/app/res/picture/voiceroom/avatar/user_avatar3.png",
            "https://liteav.sdk.qcloud.com/app/res/picture/voiceroom/avatar/user_avatar4.png",
            "https://liteav.sdk.qcloud.com/app/res/picture/voiceroom/avatar/user_avatar5.png"
        )

        val NICKNAME_RES_IDS = listOf(
            R.string.app_custom_name_1,
            R.string.app_custom_name_2,
            R.string.app_custom_name_3,
            R.string.app_custom_name_4,
            R.string.app_custom_name_5
        )
    }
}
