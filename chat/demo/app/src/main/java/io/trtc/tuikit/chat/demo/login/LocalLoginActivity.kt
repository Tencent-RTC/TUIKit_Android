package io.trtc.tuikit.chat.demo.login

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.tencent.mmkv.MMKV
import io.trtc.tuikit.atomicx.theme.tokens.ColorTokens
import io.trtc.tuikit.chat.app.R
import io.trtc.tuikit.chat.demo.common.AppConstants
import io.trtc.tuikit.chat.demo.signature.GenerateTestUserSig

class LocalLoginActivity : BaseLoginActivity() {

    private lateinit var loginRoot: LinearLayout
    private lateinit var loginPanel: FrameLayout
    private lateinit var topSwitchers: LinearLayout
    private lateinit var tvUserIdLabel: TextView
    private lateinit var editUserId: EditText
    private lateinit var editDivider: View
    private lateinit var btnLogin: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val loginUser = MMKV.defaultMMKV().decodeString(AppConstants.KEY_LOGIN_USER, "")
        val loginType = MMKV.defaultMMKV().decodeString(AppConstants.KEY_LOGIN_TYPE, "")
        if (!loginUser.isNullOrEmpty() && loginType == AppConstants.LOGIN_TYPE_LOCAL) {
            login(loginUser)
            return
        }

        setContentView(R.layout.demo_activity_local_login)
        bindViews()
        setupCommonLoginViews()
        applyWindowInsets()
        applyThemeColors(themeStore.themeState.value.currentTheme.tokens.color)
        setupLoginInteractions()
        prefillLastLocalUserId()
    }

    private fun prefillLastLocalUserId() {
        val lastUserId = MMKV.defaultMMKV().decodeString(AppConstants.KEY_LAST_LOCAL_USER_ID, "").orEmpty()
        if (lastUserId.isNotEmpty()) {
            editUserId.setText(lastUserId)
            editUserId.setSelection(editUserId.text.length)
        }
    }

    private fun bindViews() {
        loginRoot = findViewById(R.id.demo_loginRoot)
        loginPanel = findViewById(R.id.demo_loginPanel)
        topSwitchers = findViewById(R.id.demo_topSwitchers)
        tvUserIdLabel = findViewById(R.id.demo_tvUserIdLabel)
        editUserId = findViewById(R.id.demo_editUserID)
        editDivider = findViewById(R.id.demo_editDivider)
        btnLogin = findViewById(R.id.demo_btnLogin)
    }

    private fun setupLoginInteractions() {
        updateLoginButtonState(editUserId.text)
        editUserId.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                updateLoginButtonState(s)
            }
            override fun afterTextChanged(s: Editable?) {}
        })
        editUserId.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE && btnLogin.isEnabled) {
                btnLogin.performClick()
                true
            } else {
                false
            }
        }
        btnLogin.setOnClickListener {
            val userId = editUserId.text.toString().trim()
            if (userId.isNotEmpty()) {
                login(userId)
            }
        }
    }

    private fun updateLoginButtonState(input: CharSequence?) {
        btnLogin.isEnabled = input?.toString()?.trim()?.isNotEmpty() == true
    }

    override fun appearanceLightStatusBarsOverride(): Boolean = false

    private fun applyWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(loginRoot) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            topSwitchers.setPadding(
                topSwitchers.paddingLeft,
                bars.top,
                topSwitchers.paddingRight,
                topSwitchers.paddingBottom
            )
            loginPanel.setPadding(
                loginPanel.paddingLeft,
                loginPanel.paddingTop,
                loginPanel.paddingRight,
                bars.bottom
            )
            insets
        }
        ViewCompat.requestApplyInsets(loginRoot)
    }

    override fun applyThemeColors(colors: ColorTokens) {
        if (!contentReady) {
            return
        }
        loginRoot.setBackgroundColor(colors.bgColorOperate)
        loginPanel.setBackgroundColor(colors.bgColorOperate)
        tvUserIdLabel.setTextColor(colors.textColorPrimary)
        editUserId.setTextColor(colors.textColorPrimary)
        editUserId.setHintTextColor(colors.textColorTertiary)
        editDivider.setBackgroundColor(colors.strokeColorPrimary)
        updateBackgroundPreservingPadding(btnLogin, createButtonBackground(colors))
        btnLogin.setTextColor(createButtonTextColors(colors))
    }

    private fun login(userID: String) {
        val sdkAppId = GenerateTestUserSig.SDKAPPID
        val userSig = GenerateTestUserSig.genTestUserSig(userID)
        performLogin(
            sdkAppId, userID, userSig, AppConstants.LOGIN_TYPE_LOCAL,
            onSuccess = {
                MMKV.defaultMMKV().encode(AppConstants.KEY_LAST_LOCAL_USER_ID, userID)
            }
        )
    }
}
