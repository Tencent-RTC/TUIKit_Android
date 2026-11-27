package io.trtc.tuikit.chat.demo.login

import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.Spannable
import android.text.SpannableString
import android.text.TextPaint
import android.text.TextWatcher
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.tencent.mmkv.MMKV
import io.trtc.tuikit.atomicx.theme.tokens.ColorTokens
import io.trtc.tuikit.chat.app.R
import io.trtc.tuikit.chat.demo.common.AppConstants

class LoginActivity : BaseLoginActivity() {

    private lateinit var loginRoot: LinearLayout
    private lateinit var loginPanel: LinearLayout
    private lateinit var topSwitchers: LinearLayout
    private lateinit var areaCodeRow: LinearLayout
    private lateinit var tvAreaLabel: TextView
    private lateinit var tvAreaName: TextView
    private lateinit var tvZone: TextView
    private lateinit var tvPhoneLabel: TextView
    private lateinit var tvCodeLabel: TextView
    private lateinit var editPhone: EditText
    private lateinit var editSms: EditText
    private lateinit var btnGetSms: TextView
    private lateinit var btnLogin: TextView
    private lateinit var tvSwitchLocal: TextView
    private lateinit var tvHomepage: TextView
    private lateinit var dividerArea: View
    private lateinit var dividerPhone: View
    private lateinit var dividerCode: View

    private var zone = "86"
    private var sessionId = ""
    private var captchaPopup: PopupWindow? = null
    private var captchaWebView: WebView? = null
    private var loadingDialog: Dialog? = null
    private var privacyDialog: Dialog? = null
    private var privacyAgreed = false

    private val countdownHandler = Handler(Looper.getMainLooper())
    private var countdownSeconds = 0
    private val countdownRunnable = object : Runnable {
        override fun run() {
            countdownSeconds--
            if (countdownSeconds <= 0) {
                resetGetSmsButton()
            } else {
                btnGetSms.text = getString(R.string.demo_login_resend_seconds, countdownSeconds)
                countdownHandler.postDelayed(this, 1000)
            }
        }
    }

    private val areaCodeLauncher =
        registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val name = result.data?.getStringExtra("areaCodeName")
                val code = result.data?.getStringExtra("areaCode")
                if (!name.isNullOrEmpty()) {
                    tvAreaName.text = name
                }
                if (!code.isNullOrEmpty()) {
                    zone = code
                    tvZone.text = "+$code"
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        privacyAgreed = savedInstanceState?.getBoolean(STATE_PRIVACY_AGREED, false) ?: false

        val mmkv = MMKV.defaultMMKV()
        val type = mmkv.decodeString(AppConstants.KEY_LOGIN_TYPE, "").orEmpty()
        val savedUser = mmkv.decodeString(AppConstants.KEY_LOGIN_USER, "").orEmpty()
        val token = mmkv.decodeString(AppConstants.KEY_LOGIN_TOKEN, "").orEmpty()

        if (type == AppConstants.LOGIN_TYPE_LOCAL && LocalLoginEntry.available && savedUser.isNotEmpty()) {
            LocalLoginEntry.start(this)
            finish()
            return
        }

        setContentView(R.layout.demo_activity_login)
        bindViews()
        setupCommonLoginViews()
        applyWindowInsets()
        applyThemeColors(themeStore.themeState.value.currentTheme.tokens.color)
        setupInteractions()
        prefillLastLoginPhone()

        if (type == AppConstants.LOGIN_TYPE_PHONE && savedUser.isNotEmpty() && token.isNotEmpty()) {
            autoLoginByToken(savedUser, token)
        } else if (!privacyAgreed) {
            showPrivacyDialog()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_PRIVACY_AGREED, privacyAgreed)
    }

    private fun bindViews() {
        loginRoot = findViewById(R.id.demo_loginRoot)
        loginPanel = findViewById(R.id.demo_loginPanel)
        topSwitchers = findViewById(R.id.demo_topSwitchers)
        areaCodeRow = findViewById(R.id.demo_areaCodeRow)
        tvAreaLabel = findViewById(R.id.demo_tvAreaLabel)
        tvAreaName = findViewById(R.id.demo_tvAreaName)
        tvZone = findViewById(R.id.demo_tvZone)
        tvPhoneLabel = findViewById(R.id.demo_tvPhoneLabel)
        tvCodeLabel = findViewById(R.id.demo_tvCodeLabel)
        editPhone = findViewById(R.id.demo_editPhone)
        editSms = findViewById(R.id.demo_editSms)
        btnGetSms = findViewById(R.id.demo_btnGetSms)
        btnLogin = findViewById(R.id.demo_btnLogin)
        tvSwitchLocal = findViewById(R.id.demo_tvSwitchLocal)
        tvHomepage = findViewById(R.id.demo_tvHomepage)
        dividerArea = findViewById(R.id.demo_dividerArea)
        dividerPhone = findViewById(R.id.demo_dividerPhone)
        dividerCode = findViewById(R.id.demo_dividerCode)
    }

    private fun setupInteractions() {
        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                btnLogin.isEnabled = editPhone.text.isNotBlank() && editSms.text.isNotBlank()
            }
            override fun afterTextChanged(s: Editable?) {}
        }
        editPhone.addTextChangedListener(watcher)
        editSms.addTextChangedListener(watcher)

        areaCodeRow.setOnClickListener {
            areaCodeLauncher.launch(
                android.content.Intent(this, TelephoneAreaCodeSelectorActivity::class.java)
            )
        }
        btnGetSms.setOnClickListener { startImageVerification() }
        btnLogin.setOnClickListener { doLogin() }
        tvHomepage.setOnClickListener { openWeb(URL_HOMEPAGE) }

        if (LocalLoginEntry.available) {
            tvSwitchLocal.visibility = View.VISIBLE
            tvSwitchLocal.setOnClickListener { LocalLoginEntry.start(this) }
        } else {
            tvSwitchLocal.visibility = View.GONE
        }
    }

    private fun prefillLastLoginPhone() {
        val lastPhone = MMKV.defaultMMKV().decodeString(AppConstants.KEY_LAST_LOGIN_PHONE, "").orEmpty()
        if (lastPhone.isNotEmpty()) {
            editPhone.setText(lastPhone)
            editPhone.setSelection(editPhone.text.length)
        }
    }

    override fun applyThemeColors(colors: ColorTokens) {
        if (!contentReady) {
            return
        }
        loginRoot.setBackgroundColor(colors.bgColorOperate)
        updateBackgroundPreservingPadding(loginPanel, roundedTopBackground(colors.bgColorOperate))
        for (label in listOf(tvAreaLabel, tvPhoneLabel, tvCodeLabel)) {
            label.setTextColor(colors.textColorPrimary)
        }
        tvAreaName.setTextColor(colors.textColorPrimary)
        tvZone.setTextColor(colors.textColorPrimary)
        editPhone.setTextColor(colors.textColorPrimary)
        editPhone.setHintTextColor(colors.textColorTertiary)
        editSms.setTextColor(colors.textColorPrimary)
        editSms.setHintTextColor(colors.textColorTertiary)
        for (divider in listOf(dividerArea, dividerPhone, dividerCode)) {
            divider.setBackgroundColor(colors.strokeColorPrimary)
        }
        updateBackgroundPreservingPadding(btnLogin, createButtonBackground(colors))
        btnLogin.setTextColor(createButtonTextColors(colors))
        updateBackgroundPreservingPadding(btnGetSms, createButtonBackground(colors))
        btnGetSms.setTextColor(createButtonTextColors(colors))
        tvSwitchLocal.setTextColor(colors.textColorLink)
        tvHomepage.setTextColor(colors.textColorLink)
    }

    private fun roundedTopBackground(fillColor: Int): GradientDrawable {
        val radius = dpToPx(20).toFloat()
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(fillColor)
            cornerRadii = floatArrayOf(radius, radius, radius, radius, 0f, 0f, 0f, 0f)
        }
    }

    override fun appearanceLightStatusBarsOverride(): Boolean = false

    private fun applyWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(loginRoot) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
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
                maxOf(bars.bottom, ime.bottom)
            )
            if (ime.bottom > bars.bottom) {
                currentFocus?.let { focused ->
                    focused.post {
                        focused.requestRectangleOnScreen(
                            Rect(0, 0, focused.width, focused.height),
                            false
                        )
                    }
                }
            }
            insets
        }
        ViewCompat.requestApplyInsets(loginRoot)
    }

    private fun startImageVerification() {
        val phone = editPhone.text.toString().trim()
        if (phone.isEmpty()) {
            Toast.makeText(this, getString(R.string.demo_login_input_phone), Toast.LENGTH_SHORT).show()
            return
        }
        if (countdownSeconds > 0) {
            return
        }
        AuthApi.getCaptchaAppId(object : AuthApi.RequestCallback<String> {
            override fun onError(code: Int, message: String?) {
                if (!isActive()) return
                Toast.makeText(this@LoginActivity, message ?: getString(R.string.demo_login_network_error), Toast.LENGTH_SHORT).show()
            }

            override fun onSuccess(result: String) {
                showCaptcha(result)
            }
        })
    }

    private fun isActive(): Boolean = !isFinishing && !isDestroyed

    private fun showCaptcha(captchaAppId: String) {
        if (!isActive()) return
        val popupView = LayoutInflater.from(this)
            .inflate(R.layout.demo_login_image_verification_popup, null)
        val popup = PopupWindow(
            popupView,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT
        )
        popup.isClippingEnabled = false
        // Focusable so the popup window receives the key events and consumes BACK to
        // close itself, instead of letting BACK fall through and finish the activity.
        popup.isFocusable = true
        popup.setOnDismissListener { dismissCaptcha() }
        captchaPopup = popup

        val webView = popupView.findViewById<WebView>(R.id.demo_imageVerificationWebView)
        captchaWebView = webView
        webView.setBackgroundColor(Color.TRANSPARENT)
        webView.settings.useWideViewPort = true
        webView.settings.loadWithOverviewMode = true
        webView.settings.cacheMode = WebSettings.LOAD_NO_CACHE
        webView.settings.javaScriptEnabled = true
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                view.loadUrl(url)
                return true
            }
        }
        webView.addJavascriptInterface(
            ImageVerificationJsBridge(object : ImageVerificationJsBridge.Callback {
                override fun onVerifySuccess(ticket: String, randStr: String) {
                    runOnUiThread {
                        if (!isActive()) return@runOnUiThread
                        dismissCaptcha()
                        requestSms(captchaAppId, ticket, randStr)
                    }
                }

                override fun onVerifyError(errorCode: Int, errorMsg: String?) {
                    runOnUiThread { dismissCaptcha() }
                }
            }),
            "imageVerificationJsBridge"
        )

        val lang = AuthApi.captchaUrlLanguage()
        webView.loadUrl(
            "file:///android_asset/html/verification.html?language=$lang&captchaAppId=$captchaAppId"
        )
        popup.showAtLocation(findViewById(android.R.id.content), Gravity.CENTER or Gravity.TOP, 0, 0)
    }

    private fun dismissCaptcha() {
        val popup = captchaPopup
        captchaPopup = null
        popup?.setOnDismissListener(null)
        popup?.takeIf { it.isShowing }?.dismiss()
        captchaWebView?.destroy()
        captchaWebView = null
    }

    private fun requestSms(captchaAppId: String, ticket: String, randStr: String) {
        val phone = editPhone.text.toString().trim()
        val fullPhone = zone + phone
        AuthApi.getSms(captchaAppId, ticket, randStr, fullPhone, object : AuthApi.RequestCallback<String> {
            override fun onError(code: Int, message: String?) {
                if (!isActive()) return
                Toast.makeText(this@LoginActivity, message ?: getString(R.string.demo_login_network_error), Toast.LENGTH_SHORT).show()
            }

            override fun onSuccess(result: String) {
                if (!isActive()) return
                sessionId = result
                Toast.makeText(this@LoginActivity, getString(R.string.demo_login_send_verify_code), Toast.LENGTH_SHORT).show()
                startCountdown()
            }
        })
    }

    private fun startCountdown() {
        countdownSeconds = 60
        btnGetSms.isEnabled = false
        btnGetSms.text = getString(R.string.demo_login_resend_seconds, countdownSeconds)
        countdownHandler.postDelayed(countdownRunnable, 1000)
    }

    private fun resetGetSmsButton() {
        countdownHandler.removeCallbacks(countdownRunnable)
        countdownSeconds = 0
        btnGetSms.isEnabled = true
        btnGetSms.text = getString(R.string.demo_login_get_verification_code)
    }

    private fun doLogin() {
        val phone = editPhone.text.toString().trim()
        val sms = editSms.text.toString().trim()
        if (phone.isEmpty() || sms.isEmpty()) {
            Toast.makeText(this, getString(R.string.demo_login_input_phone_code), Toast.LENGTH_SHORT).show()
            return
        }
        btnLogin.isEnabled = false
        showLoading()
        val fullPhone = zone + phone
        AuthApi.loginByCode(fullPhone, sms, sessionId, object : AuthApi.RequestCallback<AuthApi.LoginResult> {
            override fun onError(code: Int, message: String?) {
                if (!isActive()) return
                hideLoading()
                btnLogin.isEnabled = true
                Toast.makeText(this@LoginActivity, message ?: getString(R.string.demo_login_network_error), Toast.LENGTH_SHORT).show()
            }

            override fun onSuccess(result: AuthApi.LoginResult) {
                if (!isActive()) return
                hideLoading()
                performLogin(
                    result.sdkAppId, result.userId, result.userSig,
                    AppConstants.LOGIN_TYPE_PHONE, result.token,
                    onSuccess = {
                        MMKV.defaultMMKV().encode(AppConstants.KEY_LAST_LOGIN_PHONE, phone)
                    }
                ) { _, desc ->
                    btnLogin.isEnabled = true
                    Toast.makeText(this@LoginActivity, getString(R.string.demo_login_failed, desc), Toast.LENGTH_SHORT).show()
                }
            }
        })
    }

    private fun autoLoginByToken(userId: String, token: String) {
        showLoading()
        AuthApi.loginByToken(userId, token, object : AuthApi.RequestCallback<AuthApi.LoginResult> {
            override fun onError(code: Int, message: String?) {
                if (!isActive()) return
                hideLoading()
                fallbackToManualLogin()
            }

            override fun onSuccess(result: AuthApi.LoginResult) {
                if (!isActive()) return
                hideLoading()
                performLogin(
                    result.sdkAppId, result.userId, result.userSig,
                    AppConstants.LOGIN_TYPE_PHONE, result.token
                ) { _, _ -> fallbackToManualLogin() }
            }
        })
    }

    private fun fallbackToManualLogin() {
        clearLoginState()
        showPrivacyDialog()
    }

    private fun clearLoginState() {
        MMKV.defaultMMKV().encode(AppConstants.KEY_LOGIN_USER, "")
        MMKV.defaultMMKV().encode(AppConstants.KEY_LOGIN_USER_SIG, "")
        MMKV.defaultMMKV().encode(AppConstants.KEY_LOGIN_TOKEN, "")
        MMKV.defaultMMKV().encode(AppConstants.KEY_LOGIN_TYPE, "")
    }

    private fun showLoading() {
        if (!isActive()) return
        if (loadingDialog == null) {
            val padding = dpToPx(24)
            val container = FrameLayout(this).apply {
                setPadding(padding, padding, padding, padding)
                addView(ProgressBar(this@LoginActivity))
            }
            loadingDialog = Dialog(this).apply {
                setContentView(container)
                setCancelable(false)
                setCanceledOnTouchOutside(false)
                window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            }
        }
        loadingDialog?.show()
    }

    private fun hideLoading() {
        loadingDialog?.takeIf { it.isShowing }?.dismiss()
    }

    private fun showPrivacyDialog() {
        if (!isActive()) return
        privacyDialog = LoginPrivacyDialog(
            activity = this,
            colors = themeStore.themeState.value.currentTheme.tokens.color,
            onAgree = { privacyAgreed = true },
            onRefuse = { finish() },
            openWeb = ::openWeb
        ).show()
    }

    private fun openWeb(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        } catch (e: Exception) {
            Log.w(TAG, "openWeb failed: $url, ${e.message}")
            Toast.makeText(this, getString(R.string.demo_open_browser_failed), Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        countdownHandler.removeCallbacks(countdownRunnable)
        dismissCaptcha()
        hideLoading()
        privacyDialog?.dismiss()
        privacyDialog = null
    }

    companion object {
        private const val TAG = "LoginActivity"
        private const val STATE_PRIVACY_AGREED = "state_privacy_agreed"

        private const val URL_HOMEPAGE = "https://cloud.tencent.com/product/im"
    }
}

private class LoginPrivacyDialog(
    private val activity: LoginActivity,
    private val colors: ColorTokens,
    private val onAgree: () -> Unit,
    private val onRefuse: () -> Unit,
    private val openWeb: (String) -> Unit
) {
    fun show(): Dialog {
        val view = LayoutInflater.from(activity).inflate(R.layout.demo_login_privacy_dialog, null)
        val root = view.findViewById<LinearLayout>(R.id.demo_privacyDialogRoot)
        val content = view.findViewById<TextView>(R.id.demo_privacyContent)
        val agree = view.findViewById<TextView>(R.id.demo_privacyAgree)
        val refuse = view.findViewById<TextView>(R.id.demo_privacyRefuse)
        val dialogWidth = (activity.resources.displayMetrics.widthPixels * DIALOG_WIDTH_RATIO).toInt()

        root.layoutParams = ViewGroup.LayoutParams(dialogWidth, ViewGroup.LayoutParams.WRAP_CONTENT)
        content.width = (dialogWidth - root.paddingLeft - root.paddingRight).coerceAtLeast(1)
        content.text = buildPrivacySpannable(colors.textColorLink)
        content.movementMethod = LinkMovementMethod.getInstance()
        content.setHorizontallyScrolling(false)
        content.fontFeatureSettings = NO_CJK_PUNCTUATION_SQUEEZE
        content.setTextColor(colors.textColorPrimary)
        updateBackgroundPreservingPadding(root, roundedDialogBackground(colors.bgColorOperate))
        updateBackgroundPreservingPadding(agree, createButtonBackground(colors))
        agree.setTextColor(colors.textColorButton)
        refuse.setTextColor(colors.textColorSecondary)

        val dialog = AlertDialog.Builder(activity)
            .setView(view)
            .setCancelable(false)
            .create()
        dialog.setCanceledOnTouchOutside(false)
        dialog.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_DOWN) {
                onRefuse()
            }
            false
        }
        agree.setOnClickListener {
            onAgree()
            dialog.dismiss()
        }
        refuse.setOnClickListener { onRefuse() }
        dialog.show()
        dialog.window?.decorView?.setPadding(0, 0, 0, 0)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        val layoutParams = dialog.window?.attributes
        layoutParams?.width = dialogWidth
        layoutParams?.height = WindowManager.LayoutParams.WRAP_CONTENT
        dialog.window?.attributes = layoutParams
        return dialog
    }

    private fun buildPrivacySpannable(linkColor: Int): CharSequence {
        val spannable = SpannableString(activity.getString(R.string.demo_login_privacy_dialog_tips))
        applyPrivacySpan(
            spannable,
            activity.getString(R.string.demo_login_user_agreement),
            URL_USER_AGREEMENT,
            linkColor
        )
        applyPrivacySpan(
            spannable,
            activity.getString(R.string.demo_login_privacy_policy_summary),
            URL_PRIVACY_SUMMARY,
            linkColor
        )
        applyPrivacySpan(
            spannable,
            activity.getString(R.string.demo_login_privacy_policy),
            URL_PRIVACY,
            linkColor
        )
        applyPrivacySpan(
            spannable,
            activity.getString(R.string.demo_login_self_information_collection),
            URL_SELF_INFO,
            linkColor
        )
        applyPrivacySpan(
            spannable,
            activity.getString(R.string.demo_login_third_part_shared),
            URL_THIRD_SHARED,
            linkColor
        )
        return spannable
    }

    private fun applyPrivacySpan(
        spannable: SpannableString,
        phrase: String,
        url: String,
        linkColor: Int
    ) {
        val index = spannable.toString().lastIndexOf(phrase)
        if (index < 0) return
        val end = index + phrase.length
        spannable.setSpan(object : ClickableSpan() {
            override fun onClick(widget: View) {
                openWeb(url)
            }

            override fun updateDrawState(ds: TextPaint) {
                ds.color = linkColor
                ds.isUnderlineText = false
            }
        }, index, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

    private fun roundedDialogBackground(fillColor: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(fillColor)
            cornerRadius = dpToPx(16).toFloat()
        }
    }

    private fun createButtonBackground(colors: ColorTokens): StateListDrawable {
        return StateListDrawable().apply {
            addState(
                intArrayOf(-android.R.attr.state_enabled),
                createButtonShape(colors.buttonColorPrimaryDisabled)
            )
            addState(
                intArrayOf(android.R.attr.state_pressed),
                createButtonShape(colors.buttonColorPrimaryActive)
            )
            addState(intArrayOf(), createButtonShape(colors.buttonColorPrimaryDefault))
        }
    }

    private fun createButtonShape(fillColor: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(fillColor)
            cornerRadius = dpToPx(14).toFloat()
        }
    }

    private fun updateBackgroundPreservingPadding(view: View, background: Drawable) {
        val paddingStart = view.paddingStart
        val paddingTop = view.paddingTop
        val paddingEnd = view.paddingEnd
        val paddingBottom = view.paddingBottom
        view.background = background
        view.setPaddingRelative(paddingStart, paddingTop, paddingEnd, paddingBottom)
    }

    private fun dpToPx(value: Int): Int {
        return (value * activity.resources.displayMetrics.density).toInt()
    }

    private companion object {
        private const val DIALOG_WIDTH_RATIO = 0.8f

        // Android enables the OpenType `chws` feature by default, which half-widths adjacent
        // CJK punctuation. Being contextual, it survives measurement (runs split on
        // MetricAffectingSpan only) but dies during drawing, where the link ClickableSpans split
        // the runs too. Link-dense lines then measure narrower than they paint and spill past
        // the dialog padding. Disabling it keeps both passes on the same metrics.
        private const val NO_CJK_PUNCTUATION_SQUEEZE = "\"chws\" 0"

        private const val URL_PRIVACY =
            "https://privacy.qq.com/document/preview/1cfe904fb7004b8ab1193a55857f7272"
        private const val URL_PRIVACY_SUMMARY =
            "https://privacy.qq.com/document/preview/c63a48325d0e4a35b93f675205a65a77"
        private const val URL_USER_AGREEMENT =
            "https://web.sdk.qcloud.com/document/Tencent-IM-User-Agreement.html"
        private const val URL_SELF_INFO =
            "https://privacy.qq.com/document/preview/45ba982a1ce6493597a00f8c86b52a1e"
        private const val URL_THIRD_SHARED =
            "https://privacy.qq.com/document/preview/dea84ac4bb88454794928b77126e9246"
    }
}
