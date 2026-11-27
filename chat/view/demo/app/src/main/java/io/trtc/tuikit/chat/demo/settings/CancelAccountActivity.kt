package io.trtc.tuikit.chat.demo.settings

import io.trtc.tuikit.chat.demo.common.AppConstants
import io.trtc.tuikit.chat.demo.common.BaseActivity

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.tencent.mmkv.MMKV
import io.trtc.tuikit.atomicx.theme.ThemeStore
import io.trtc.tuikit.atomicx.theme.tokens.ColorTokens
import io.trtc.tuikit.atomicx.widget.basicwidget.alertdialog.AtomicAlertDialog
import io.trtc.tuikit.atomicx.widget.basicwidget.alertdialog.cancelButton
import io.trtc.tuikit.atomicx.widget.basicwidget.alertdialog.confirmButton
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.login.LoginStore
import io.trtc.tuikit.atomicxcore.api.login.UserProfile
import io.trtc.tuikit.chat.app.R
import io.trtc.tuikit.chat.demo.login.AuthApi
import io.trtc.tuikit.chat.demo.login.LoginActivity
import io.trtc.tuikit.chat.uikit.components.widgets.Avatar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class CancelAccountActivity : BaseActivity() {

    private val themeStore by lazy { ThemeStore.shared(this) }
    private var activityScope: CoroutineScope? = null

    private lateinit var rootContainer: LinearLayout
    private lateinit var headerContainer: LinearLayout
    private lateinit var tvTitle: TextView
    private lateinit var btnBack: ImageView
    private lateinit var btnMore: ImageView
    private lateinit var headerDivider: View
    private lateinit var badgeContainer: View
    private lateinit var leftContainer: LinearLayout
    private lateinit var scrollView: ScrollView

    private lateinit var avatar: Avatar
    private lateinit var tvTip: TextView
    private lateinit var btnCancelAccount: TextView

    private var cachedUserId: String = ""

    companion object {
        fun start(context: Context) {
            context.startActivity(Intent(context, CancelAccountActivity::class.java))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (isFinishing) {
            return
        }
        setContentView(R.layout.demo_activity_cancel_account)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isStatusBarContrastEnforced = false
            window.isNavigationBarContrastEnforced = false
        }

        rootContainer = findViewById(R.id.demo_cancelAccountRootContainer)
        headerContainer = findViewById(R.id.demo_chatHeaderContainer)
        tvTitle = findViewById(R.id.demo_tvChatTitle)
        btnBack = findViewById(R.id.demo_btnBack)
        btnMore = findViewById(R.id.demo_btnMore)
        headerDivider = findViewById(R.id.demo_headerDivider)
        badgeContainer = findViewById(R.id.demo_badgeContainer)
        leftContainer = findViewById(R.id.demo_leftContainer)
        scrollView = findViewById(R.id.demo_cancelAccountScrollView)

        avatar = findViewById<Avatar>(R.id.demo_cancelUserAvatar).apply {
            setSize(Avatar.AvatarSize.XL)
        }
        tvTip = findViewById(R.id.demo_tvCancelAccountTip)
        btnCancelAccount = findViewById(R.id.demo_btnCancelAccount)

        ViewCompat.setOnApplyWindowInsetsListener(rootContainer) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(top = systemBars.top)
            scrollView.updatePadding(bottom = systemBars.bottom)
            insets
        }

        leftContainer.contentDescription = btnBack.contentDescription
        leftContainer.setOnClickListener { finish() }
        btnMore.visibility = View.GONE
        badgeContainer.visibility = View.GONE
        tvTitle.text = getString(R.string.demo_about_cancel_account)

        btnCancelAccount.setOnClickListener { showCancelAccountConfirm() }

        applyColors(themeStore.themeState.value.currentTheme.tokens.color)
    }

    override fun onStart() {
        super.onStart()
        activityScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
        activityScope?.launch {
            themeStore.themeState.collectLatest { state ->
                applyColors(state.currentTheme.tokens.color)
            }
        }
        activityScope?.launch {
            LoginStore.shared.loginState.loginUserInfo.collectLatest { profile ->
                updateUserProfile(profile)
            }
        }
    }

    override fun onStop() {
        super.onStop()
        activityScope?.cancel()
        activityScope = null
    }

    private fun updateUserProfile(profile: UserProfile?) {
        cachedUserId = profile?.userID.orEmpty()
        val displayName = if (!profile?.nickname.isNullOrEmpty()) {
            profile?.nickname.orEmpty()
        } else {
            cachedUserId
        }
        avatar.setContent(
            Avatar.AvatarContent.Image(url = profile?.avatarURL, fallbackName = displayName)
        )
        tvTip.text = getString(R.string.demo_about_cancel_account_tip, cachedUserId)
    }

    private fun showCancelAccountConfirm() {
        AtomicAlertDialog(this).apply {
            init {
                title = getString(R.string.demo_about_cancel_account_alert_tip)
                confirmButton(
                    getString(R.string.demo_about_confirm),
                    type = AtomicAlertDialog.TextColorPreset.RED
                ) { _ ->
                    cancelAccount()
                }
                cancelButton(getString(R.string.demo_about_cancel))
            }
            show()
        }
    }

    private fun cancelAccount() {
        val userId = MMKV.defaultMMKV().decodeString(AppConstants.KEY_LOGIN_USER, "").orEmpty()
            .ifEmpty { cachedUserId }
        val token = MMKV.defaultMMKV().decodeString(AppConstants.KEY_LOGIN_TOKEN, "").orEmpty()
        if (token.isEmpty()) {
            Toast.makeText(
                this,
                getString(R.string.demo_about_cancel_account_error_msg),
                Toast.LENGTH_LONG
            ).show()
            return
        }
        AuthApi.deleteAccount(userId, token, object : AuthApi.RequestCallback<Unit> {
            override fun onError(code: Int, message: String?) {
                if (isFinishing || isDestroyed) return
                Toast.makeText(
                    this@CancelAccountActivity,
                    "$code , ${getString(R.string.demo_about_cancel_account_error_msg)}",
                    Toast.LENGTH_LONG
                ).show()
            }

            override fun onSuccess(result: Unit) {
                if (isFinishing || isDestroyed) return
                LoginStore.shared.logout(object : CompletionHandler {
                    override fun onSuccess() {
                        onCancelAccountFinished()
                    }

                    override fun onFailure(code: Int, desc: String) {
                        onCancelAccountFinished()
                    }
                })
            }
        })
    }

    private fun onCancelAccountFinished() {
        if (isFinishing || isDestroyed) return
        MMKV.defaultMMKV().encode(AppConstants.KEY_LOGIN_USER, "")
        MMKV.defaultMMKV().encode(AppConstants.KEY_LOGIN_USER_SIG, "")
        MMKV.defaultMMKV().encode(AppConstants.KEY_LOGIN_TOKEN, "")
        MMKV.defaultMMKV().encode(AppConstants.KEY_LOGIN_TYPE, "")
        Toast.makeText(
            this,
            getString(R.string.demo_about_cancel_account_successful),
            Toast.LENGTH_LONG
        ).show()
        val intent = Intent(this, LoginActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        startActivity(intent)
        finish()
    }

    private fun applyColors(colors: ColorTokens) {
        rootContainer.setBackgroundColor(colors.bgColorOperate)
        scrollView.setBackgroundColor(colors.bgColorOperate)
        headerContainer.setBackgroundColor(colors.bgColorOperate)
        tvTitle.setTextColor(colors.textColorPrimary)
        btnBack.imageTintList = ColorStateList.valueOf(colors.textColorSecondary)
        headerDivider.setBackgroundColor(colors.strokeColorPrimary)

        tvTip.setTextColor(colors.textColorSecondary)

        btnCancelAccount.setTextColor(colors.textColorError)
        val border = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(colors.bgColorOperate)
            setStroke((1f * resources.displayMetrics.density).toInt(), colors.textColorError)
            cornerRadius = 8f * resources.displayMetrics.density
        }
        btnCancelAccount.background = border
    }
}
