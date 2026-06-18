package com.tencent.rtcube.v2.login

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Lightweight in-module web container.
 *
 * Hosts user agreement / privacy policy pages so input state on the login
 * screen is preserved when the user returns.
 */
class LoginWebViewActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val title = intent.getStringExtra(extraTitle)
            ?.takeIf { it.isNotBlank() }
            ?: getString(R.string.login_privacy_user_agreement)
        val url = intent.getStringExtra(extraUrl).orEmpty()

        if (url.isBlank()) {
            Toast.makeText(
                this,
                getString(R.string.login_error_agreement_unavailable),
                Toast.LENGTH_SHORT,
            ).show()
            finish()
            return
        }

        setContent {
            LoginWebViewScreen(
                title = title,
                url = url,
                onBack = { finish() },
            )
        }
    }

    companion object {
        private const val extraTitle = "extra_title"
        private const val extraUrl = "extra_url"

        fun createIntent(
            context: Context,
            title: String,
            url: String,
        ): Intent {
            return Intent(context, LoginWebViewActivity::class.java).apply {
                putExtra(extraTitle, title)
                putExtra(extraUrl, url)
            }
        }
    }
}

/**
 * Agreement page screen: custom top bar + WebView.
 */
@Composable
private fun LoginWebViewScreen(
    title: String,
    url: String,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    // Keep WebView across recompositions to avoid rebuilding it.
    val webView = remember {
        createAgreementWebView(context).also { it.loadUrl(url) }
    }

    // Go back inside WebView first; exit the page when the stack is empty.
    BackHandler {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            onBack()
        }
    }

    DisposableEffect(webView) {
        onDispose {
            (webView.parent as? ViewGroup)?.removeView(webView)
            webView.stopLoading()
            webView.destroy()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colorResource(R.color.login_color_white))
            .statusBarsPadding()
    ) {
        TopBar(title = title, onBack = onBack)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(colorResource(R.color.login_color_divider))
        )

        AndroidView(
            factory = { webView },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun TopBar(
    title: String,
    onBack: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 16.dp)
    ) {
        Text(
            text = stringResource(R.string.login_back),
            color = colorResource(R.color.login_color_blue),
            fontSize = 16.sp,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .clickable(onClick = onBack)
        )

        Text(
            text = title,
            color = colorResource(R.color.login_main_text),
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 56.dp)
        )
    }
}

@SuppressLint("SetJavaScriptEnabled")
private fun createAgreementWebView(context: Context): WebView {
    return WebView(context).apply {
        setLayerType(View.LAYER_TYPE_HARDWARE, null)
        overScrollMode = View.OVER_SCROLL_NEVER

        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            useWideViewPort = true
            loadWithOverviewMode = true
            // Agreement content is stable: prefer cache, fall back to network for faster reopen.
            cacheMode = WebSettings.LOAD_CACHE_ELSE_NETWORK
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            allowFileAccess = true
            allowContentAccess = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            textZoom = 100
        }

        webViewClient = object : WebViewClient() {
            override fun onReceivedError(
                view: WebView?,
                errorCode: Int,
                description: String?,
                failingUrl: String?
            ) {
                super.onReceivedError(view, errorCode, description, failingUrl)
                Toast.makeText(
                    context,
                    context.getString(R.string.login_error_agreement_unavailable),
                    Toast.LENGTH_SHORT
                ).show()
            }

            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                // Allow in-WebView navigation; intercept external schemes.
                val requestUrl = request?.url?.toString() ?: return false
                return !requestUrl.startsWith("http://") && !requestUrl.startsWith("https://")
            }
        }
    }
}
