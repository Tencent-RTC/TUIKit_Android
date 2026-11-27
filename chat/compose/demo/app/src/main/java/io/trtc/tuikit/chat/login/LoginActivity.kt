package io.trtc.tuikit.chat.login

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.tencent.mmkv.MMKV
import io.trtc.tuikit.chat.uikit.components.widgets.Toast
import io.trtc.tuikit.chat.uikit.components.theme.LocalTheme
import io.trtc.tuikit.chat.BaseActivity
import io.trtc.tuikit.chat.R
import io.trtc.tuikit.chat.common.AppConstants
import kotlinx.coroutines.delay

private const val TAG = "LoginActivity"
private const val URL_HOMEPAGE = "https://cloud.tencent.com/product/im"
private const val STATE_PRIVACY_AGREED = "state_privacy_agreed"

private val HERO_HEIGHT = 248.dp
private val PANEL_OVERLAP = 24.dp

class LoginActivity : BaseActivity() {

    override val requiresLogin: Boolean = false

    private var zone by mutableStateOf("86")
    private var areaName by mutableStateOf<String?>(null)
    private var phone by mutableStateOf("")
    private var smsCode by mutableStateOf("")
    private var sessionId = ""
    private var countdownSeconds by mutableIntStateOf(0)
    private var loading by mutableStateOf(false)
    private var showPrivacy by mutableStateOf(false)
    private var privacyAgreed = false
    private var captchaAppId by mutableStateOf<String?>(null)

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

        phone = mmkv.decodeString(AppConstants.KEY_LAST_LOGIN_PHONE, "").orEmpty()

        setContent {
            LoginScreen()
        }

        if (type == AppConstants.LOGIN_TYPE_PHONE && savedUser.isNotEmpty() && token.isNotEmpty()) {
            autoLoginByToken(savedUser, token)
        } else if (!privacyAgreed) {
            showPrivacy = true
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_PRIVACY_AGREED, privacyAgreed)
    }

    @Composable
    private fun LoginScreen() {
        val colors = LocalTheme.current.colors

        SetLoginSystemBarAppearance()

        val areaCodeLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == RESULT_OK) {
                val name = result.data?.getStringExtra("areaCodeName")
                val code = result.data?.getStringExtra("areaCode")
                if (!name.isNullOrEmpty()) {
                    areaName = name
                }
                if (!code.isNullOrEmpty()) {
                    zone = code
                }
            }
        }

        LaunchedEffect(countdownSeconds > 0) {
            while (countdownSeconds > 0) {
                delay(1000)
                countdownSeconds--
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.bgColorOperate)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(HERO_HEIGHT)
            ) {
                Image(
                    painter = painterResource(R.drawable.demo_login_hero_bg),
                    contentDescription = null,
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier.fillMaxSize()
                )
                LoginTopSwitchers(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .statusBarsPadding()
                        .padding(top = 8.dp, end = 8.dp)
                )
                Row(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        painter = painterResource(R.drawable.demo_login_brand_logo),
                        contentDescription = stringResource(R.string.app_name),
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .width(84.dp)
                            .height(44.dp)
                    )
                    Column(modifier = Modifier.padding(start = 12.dp)) {
                        Text(
                            text = stringResource(R.string.compose_demo_login_hero_title),
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = stringResource(R.string.compose_demo_login_hero_subtitle),
                            fontSize = 13.sp,
                            color = Color(0xCCFFFFFF),
                            modifier = Modifier.padding(top = 6.dp, end = 24.dp)
                        )
                    }
                }
            }

            LoginPanel(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = HERO_HEIGHT - PANEL_OVERLAP),
                onAreaCodeClick = {
                    areaCodeLauncher.launch(
                        Intent(this@LoginActivity, TelephoneAreaCodeSelectorActivity::class.java)
                    )
                },
                onGetSmsClick = { startImageVerification() },
                onLoginClick = { doLogin() },
                onSwitchLocalClick = { LocalLoginEntry.start(this@LoginActivity) },
                onHomepageClick = { openWeb(URL_HOMEPAGE) }
            )
        }

        if (showPrivacy) {
            LoginPrivacyDialog(
                onAgree = {
                    privacyAgreed = true
                    showPrivacy = false
                },
                onRefuse = { finish() }
            )
        }

        captchaAppId?.let { appId ->
            CaptchaDialog(
                captchaAppId = appId,
                onVerifySuccess = { ticket, randStr ->
                    captchaAppId = null
                    requestSms(appId, ticket, randStr)
                },
                onDismiss = { captchaAppId = null }
            )
        }

        if (loading) {
            Dialog(
                onDismissRequest = {},
                properties = DialogProperties(
                    dismissOnBackPress = false,
                    dismissOnClickOutside = false
                )
            ) {
                CircularProgressIndicator(color = colors.buttonColorPrimaryDefault)
            }
        }
    }

    @Composable
    private fun LoginPanel(
        modifier: Modifier = Modifier,
        onAreaCodeClick: () -> Unit,
        onGetSmsClick: () -> Unit,
        onLoginClick: () -> Unit,
        onSwitchLocalClick: () -> Unit,
        onHomepageClick: () -> Unit
    ) {
        val colors = LocalTheme.current.colors
        val density = LocalDensity.current
        val bottomInset = with(density) {
            maxOf(
                WindowInsets.navigationBars.getBottom(density),
                WindowInsets.ime.getBottom(density)
            ).toDp()
        }

        BoxWithConstraints(
            modifier = modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                .background(colors.bgColorOperate)
        ) {
            // Reproduces the View version's ScrollView(fillViewport=true) plus weighted
            // spacer: heightIn(min) makes the content at least one viewport tall so
            // SpaceBetween pins the footer to the bottom, and once the form outgrows the
            // viewport the extra space collapses and the footer scrolls with the form.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .heightIn(min = maxHeight)
                    .padding(start = 28.dp, end = 28.dp, top = 32.dp)
                    .padding(bottom = 24.dp + bottomInset),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(R.string.compose_demo_login_country_or_region_label),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textColorPrimary
                    )

                    Row(
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .fillMaxWidth()
                            .height(52.dp)
                            .clickable(onClick = onAreaCodeClick),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = areaName ?: stringResource(R.string.compose_demo_login_chinese_mainland),
                            fontSize = 16.sp,
                            color = colors.textColorPrimary,
                            modifier = Modifier.weight(1f)
                        )
                        // Matches the View demo: the arrow drawable is white and untinted,
                        // so it stays invisible on the light panel and only shows in dark mode.
                        Image(
                            painter = painterResource(R.drawable.demo_ic_login_arrow_down),
                            contentDescription = null,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                    PanelDivider()

                    Text(
                        text = stringResource(R.string.compose_demo_login_phone_number_label),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textColorPrimary,
                        modifier = Modifier.padding(top = 16.dp)
                    )

                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Row(
                            modifier = Modifier
                                .padding(top = 4.dp)
                                .fillMaxWidth()
                                .height(52.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "+$zone",
                                fontSize = 16.sp,
                                color = colors.textColorPrimary
                            )
                            Box(
                                modifier = Modifier
                                    .padding(horizontal = 14.dp)
                                    .width(1.dp)
                                    .height(18.dp)
                                    .background(Color(0x33888888))
                            )
                            LoginTextField(
                                value = phone,
                                onValueChange = { input ->
                                    val digits = input.filter(Char::isDigit)
                                    if (digits.length <= 11) {
                                        phone = digits
                                    }
                                },
                                hint = stringResource(R.string.compose_demo_login_input_phone_hint),
                                keyboardType = KeyboardType.Number,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    PanelDivider()

                    Text(
                        text = stringResource(R.string.compose_demo_login_verification_code_label),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textColorPrimary,
                        modifier = Modifier.padding(top = 16.dp)
                    )

                    Row(
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .fillMaxWidth()
                            .height(52.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        LoginTextField(
                            value = smsCode,
                            onValueChange = { input ->
                                val digits = input.filter(Char::isDigit)
                                if (digits.length <= 6) {
                                    smsCode = digits
                                }
                            },
                            hint = stringResource(R.string.compose_demo_login_input_verification_hint),
                            keyboardType = KeyboardType.Number,
                            modifier = Modifier.weight(1f)
                        )
                        SmsCodeButton(
                            countdownSeconds = countdownSeconds,
                            onClick = onGetSmsClick,
                            modifier = Modifier.padding(start = 12.dp)
                        )
                    }
                    PanelDivider()

                    PrimaryButton(
                        text = stringResource(R.string.compose_demo_login),
                        enabled = phone.isNotBlank() && smsCode.isNotBlank() && !loading,
                        onClick = onLoginClick,
                        fontWeight = FontWeight.Bold,
                        horizontalPadding = 18.dp,
                        modifier = Modifier
                            .padding(top = 32.dp)
                            .fillMaxWidth()
                            .height(50.dp)
                    )

                    if (LocalLoginEntry.available) {
                        Text(
                            text = stringResource(R.string.compose_demo_login_switch_local),
                            fontSize = 14.sp,
                            color = colors.textColorLink,
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .padding(top = 20.dp)
                                .clickable(onClick = onSwitchLocalClick)
                                .padding(8.dp)
                        )
                    }
                }

                Text(
                    text = stringResource(R.string.compose_demo_login_official_website),
                    fontSize = 14.sp,
                    color = colors.textColorLink,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(top = 36.dp)
                        .clickable(onClick = onHomepageClick)
                        .padding(8.dp)
                )
            }
        }
    }

    @Composable
    private fun PanelDivider() {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(LocalTheme.current.colors.strokeColorPrimary)
        )
    }

    @Composable
    private fun LoginTextField(
        value: String,
        onValueChange: (String) -> Unit,
        hint: String,
        keyboardType: KeyboardType,
        modifier: Modifier = Modifier
    ) {
        val colors = LocalTheme.current.colors
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = modifier,
            textStyle = TextStyle(
                fontSize = 16.sp,
                color = colors.textColorPrimary
            ),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            cursorBrush = SolidColor(colors.textColorLink),
            decorationBox = { innerTextField ->
                Box {
                    if (value.isEmpty()) {
                        Text(
                            text = hint,
                            fontSize = 16.sp,
                            color = colors.textColorTertiary
                        )
                    }
                    innerTextField()
                }
            }
        )
    }

    @Composable
    private fun SmsCodeButton(
        countdownSeconds: Int,
        onClick: () -> Unit,
        modifier: Modifier = Modifier
    ) {
        val enabled = countdownSeconds <= 0
        PrimaryButton(
            text = if (enabled) {
                stringResource(R.string.compose_demo_login_get_verification_code)
            } else {
                stringResource(R.string.compose_demo_login_resend_seconds, countdownSeconds)
            },
            enabled = enabled,
            onClick = onClick,
            fontSize = 12.sp,
            horizontalPadding = 14.dp,
            modifier = modifier
                .widthIn(min = 104.dp)
                .height(34.dp)
        )
    }

    @Composable
    private fun CaptchaDialog(
        captchaAppId: String,
        onVerifySuccess: (String, String) -> Unit,
        onDismiss: () -> Unit
    ) {
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0x80000000))
            ) {
                AndroidView(
                    factory = { context ->
                        WebView(context).apply {
                            setBackgroundColor(android.graphics.Color.TRANSPARENT)
                            settings.useWideViewPort = true
                            settings.loadWithOverviewMode = true
                            settings.cacheMode = WebSettings.LOAD_NO_CACHE
                            settings.javaScriptEnabled = true
                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                                    view.loadUrl(url)
                                    return true
                                }
                            }
                            addJavascriptInterface(
                                ImageVerificationJsBridge(object : ImageVerificationJsBridge.Callback {
                                    override fun onVerifySuccess(ticket: String, randStr: String) {
                                        runOnUiThread { onVerifySuccess(ticket, randStr) }
                                    }

                                    override fun onVerifyError(errorCode: Int, errorMsg: String?) {
                                        runOnUiThread { onDismiss() }
                                    }
                                }),
                                "imageVerificationJsBridge"
                            )
                            val lang = AuthApi.captchaUrlLanguage()
                            loadUrl(
                                "file:///android_asset/html/verification.html?language=$lang&captchaAppId=$captchaAppId"
                            )
                        }
                    },
                    onRelease = { it.destroy() },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }

    private fun startImageVerification() {
        val currentPhone = phone.trim()
        if (currentPhone.isEmpty()) {
            Toast.error(this, getString(R.string.compose_demo_login_input_phone))
            return
        }
        if (countdownSeconds > 0) {
            return
        }
        AuthApi.getCaptchaAppId(object : AuthApi.RequestCallback<String> {
            override fun onError(code: Int, message: String?) {
                if (!isActive()) return
                Toast.error(
                    this@LoginActivity,
                    message ?: getString(R.string.compose_demo_login_network_error)
                )
            }

            override fun onSuccess(result: String) {
                if (!isActive()) return
                captchaAppId = result
            }
        })
    }

    private fun requestSms(captchaAppId: String, ticket: String, randStr: String) {
        val fullPhone = zone + phone.trim()
        AuthApi.getSms(captchaAppId, ticket, randStr, fullPhone, object : AuthApi.RequestCallback<String> {
            override fun onError(code: Int, message: String?) {
                if (!isActive()) return
                Toast.error(
                    this@LoginActivity,
                    message ?: getString(R.string.compose_demo_login_network_error)
                )
            }

            override fun onSuccess(result: String) {
                if (!isActive()) return
                sessionId = result
                Toast.success(this@LoginActivity, getString(R.string.compose_demo_login_send_verify_code))
                countdownSeconds = 60
            }
        })
    }

    private fun doLogin() {
        val currentPhone = phone.trim()
        val code = smsCode.trim()
        if (currentPhone.isEmpty() || code.isEmpty()) {
            Toast.error(this, getString(R.string.compose_demo_login_input_phone_code))
            return
        }
        loading = true
        val fullPhone = zone + currentPhone
        AuthApi.loginByCode(fullPhone, code, sessionId, object : AuthApi.RequestCallback<AuthApi.LoginResult> {
            override fun onError(code2: Int, message: String?) {
                if (!isActive()) return
                loading = false
                Toast.error(
                    this@LoginActivity,
                    message ?: getString(R.string.compose_demo_login_network_error)
                )
            }

            override fun onSuccess(result: AuthApi.LoginResult) {
                if (!isActive()) return
                loading = false
                performLogin(
                    this@LoginActivity,
                    result.sdkAppId, result.userId, result.userSig,
                    AppConstants.LOGIN_TYPE_PHONE, result.token,
                    onSuccess = {
                        MMKV.defaultMMKV().encode(AppConstants.KEY_LAST_LOGIN_PHONE, currentPhone)
                    }
                ) { _, desc ->
                    Toast.error(
                        this@LoginActivity,
                        getString(R.string.compose_demo_login_failed, desc)
                    )
                }
            }
        })
    }

    private fun autoLoginByToken(userId: String, token: String) {
        loading = true
        AuthApi.loginByToken(userId, token, object : AuthApi.RequestCallback<AuthApi.LoginResult> {
            override fun onError(code: Int, message: String?) {
                if (!isActive()) return
                loading = false
                fallbackToManualLogin()
            }

            override fun onSuccess(result: AuthApi.LoginResult) {
                if (!isActive()) return
                loading = false
                performLogin(
                    this@LoginActivity,
                    result.sdkAppId, result.userId, result.userSig,
                    AppConstants.LOGIN_TYPE_PHONE, result.token
                ) { _, _ -> fallbackToManualLogin() }
            }
        })
    }

    private fun fallbackToManualLogin() {
        val mmkv = MMKV.defaultMMKV()
        mmkv.encode(AppConstants.KEY_LOGIN_USER, "")
        mmkv.encode(AppConstants.KEY_LOGIN_TOKEN, "")
        mmkv.encode(AppConstants.KEY_LOGIN_TYPE, "")
        showPrivacy = true
    }

    private fun openWeb(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        } catch (e: Exception) {
            Log.w(TAG, "openWeb failed: $url, ${e.message}")
            Toast.error(this, getString(R.string.compose_demo_open_browser_failed))
        }
    }

    private fun isActive(): Boolean = !isFinishing && !isDestroyed
}
