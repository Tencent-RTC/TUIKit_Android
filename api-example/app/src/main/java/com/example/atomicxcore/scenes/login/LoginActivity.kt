package com.example.atomicxcore.scenes.login

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.atomicxcore.R
import com.example.atomicxcore.components.LocalizedManager
import com.example.atomicxcore.databinding.ActivityLoginBinding
import com.example.atomicxcore.debug.GenerateTestUserSig
import com.example.atomicxcore.debug.SDKAPPID
import com.example.atomicxcore.scenes.featurelist.FeatureListActivity
import com.example.atomicxcore.utils.completionHandler
import com.example.atomicxcore.utils.PermissionHelper
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.trtc.tuikit.atomicxcore.api.login.LoginListener
import io.trtc.tuikit.atomicxcore.api.login.LoginStatus
import io.trtc.tuikit.atomicxcore.api.login.LoginStore
import kotlinx.coroutines.launch

/**
 * Business scenario: User login page
 *
 * Related APIs:
 * - LoginStore.shared.login(context, sdkAppID, userID, userSig, completion) - SDK login
 * - LoginStore.shared.loginState.loginStatus - Login status observation (StateFlow)
 * - LoginStore.shared.addLoginListener(LoginListener) - Login event listener
 *
 * Only User ID input is required; UserSig is generated locally (for debugging only)
 * Corresponds to LoginViewController on iOS
 */
class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding

    /** Key for locally cached user ID */
    private val cachedUserIDKey = "CachedLoginUserID"

    /** Login event listener */
    private val loginListener = object : LoginListener() {
        override fun onKickedOffline() {
            runOnUiThread {
                showAlert(
                    getString(R.string.common_warning),
                    getString(R.string.login_error_kickedOffline)
                )
            }
        }

        override fun onLoginExpired() {
            runOnUiThread {
                showAlert(
                    getString(R.string.common_warning),
                    getString(R.string.login_error_loginExpired)
                )
            }
        }
    }

    // MARK: - Lifecycle

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocalizedManager.applyLanguage(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupActions()
        setupBindings()
        restoreCachedUserID()
    }

    override fun onDestroy() {
        super.onDestroy()
        LoginStore.shared.removeLoginListener(loginListener)
    }

    // MARK: - Setup

    private fun setupActions() {
        // Login button
        binding.btnLogin.setOnClickListener {
            dismissKeyboard()
            onLoginTapped()
        }

        // Language switch button
        binding.btnLanguage.setOnClickListener {
            LocalizedManager.showLanguageSwitchDialog(this)
        }

        // Tap blank area to dismiss keyboard
        binding.root.setOnClickListener {
            dismissKeyboard()
        }
    }

    private fun setupBindings() {
        // Register login event listener
        LoginStore.shared.addLoginListener(loginListener)

        // Observe login status changes (StateFlow)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                LoginStore.shared.loginState.loginStatus.collect { status ->
                    updateLoginStatus(status)
                }
            }
        }
    }

    // MARK: - Actions

    private fun onLoginTapped() {
        val userID = binding.etUserID.text?.toString()?.trim() ?: ""
        if (userID.isEmpty()) {
            showAlert(getString(R.string.common_error), getString(R.string.login_error_emptyUserID))
            return
        }

        // Check network connectivity before login
        if (!PermissionHelper.isNetworkAvailable(this)) {
            showAlert(getString(R.string.common_warning), getString(R.string.permission_network_unavailable))
            return
        }

        performLogin(userID)
    }

    // MARK: - Login Logic

    private fun performLogin(userID: String) {
        setLoading(true)

        // Auto-generate UserSig (debug mode only)
        val userSig = GenerateTestUserSig.genTestUserSig(userID)

        LoginStore.shared.login(
            this,
            SDKAPPID.toInt(),
            userID,
            userSig,
            completionHandler { code, message ->
                runOnUiThread {
                    if (code == 0) {
                        // Login succeeded; cache user ID for auto-fill on next cold start
                        getSharedPreferences("login_prefs", MODE_PRIVATE)
                            .edit()
                            .putString(cachedUserIDKey, userID)
                            .apply()

                        // Delay 2 seconds then check profile and navigate (aligned with iOS behavior)
                        binding.root.postDelayed({
                            setLoading(false)
                            checkProfileAndNavigate()
                        }, 2000)
                    } else {
                        setLoading(false)
                        Toast.makeText(
                            this,
                            getString(R.string.login_error_loginFailed, message),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        )
    }

    // MARK: - Status Handling

    private fun updateLoginStatus(status: LoginStatus) {
        when (status) {
            LoginStatus.UNLOGIN -> {
                Toast.makeText(this, getString(R.string.login_status_notLoggedIn), Toast.LENGTH_SHORT).show()
            }
            LoginStatus.LOGINED -> {
                Toast.makeText(this, getString(R.string.login_status_loggedIn), Toast.LENGTH_SHORT).show()
            }
        }
    }

    // MARK: - Navigation

    /**
     * After login succeeds, check whether nickname is empty to decide
     * whether to navigate to profile setup or feature list.
     * Corresponds to checkProfileAndNavigate on iOS
     */
    private fun checkProfileAndNavigate() {
        val userInfo = LoginStore.shared.loginState.loginUserInfo.value
        val nickname = userInfo?.nickname ?: ""

        if (nickname.isEmpty()) {
            // Nickname is empty → navigate to profile setup page
            startActivity(Intent(this, ProfileSetupActivity::class.java))
        } else {
            // Nickname is set → go directly to feature list
            startActivity(Intent(this, FeatureListActivity::class.java))
        }
        finish()
    }

    // MARK: - UI Helpers

    private fun setLoading(loading: Boolean) {
        binding.btnLogin.isEnabled = !loading
        binding.btnLogin.text = if (loading) "" else getString(R.string.login_button)
        binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
    }

    private fun showAlert(title: String, message: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(getString(R.string.common_confirm), null)
            .show()
    }

    private fun dismissKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        currentFocus?.let { imm.hideSoftInputFromWindow(it.windowToken, 0) }
    }

    /**
     * Restore last logged-in user ID from local cache and auto-fill the input field.
     * If no cache exists, generate a random User ID and cache it to avoid
     * multiple devices using the same ID.
     * Corresponds to restoreCachedUserID on iOS
     */
    private fun restoreCachedUserID() {
        val prefs = getSharedPreferences("login_prefs", MODE_PRIVATE)
        val cachedUserID = prefs.getString(cachedUserIDKey, null)
        if (!cachedUserID.isNullOrEmpty()) {
            binding.etUserID.setText(cachedUserID)
        } else {
            val randomUserID = generateRandomUserID()
            binding.etUserID.setText(randomUserID)
            prefs.edit().putString(cachedUserIDKey, randomUserID).apply()
        }
    }

    /**
     * Generate a random numeric User ID (9-digit random number).
     * This ID also serves as the anchor's room ID.
     */
    private fun generateRandomUserID(): String {
        return (100_000_000..999_999_999).random().toString()
    }
}
