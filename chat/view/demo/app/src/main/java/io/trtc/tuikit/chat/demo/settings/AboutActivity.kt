package io.trtc.tuikit.chat.demo.settings

import io.trtc.tuikit.chat.demo.common.BaseActivity

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.tencent.imsdk.v2.V2TIMManager
import com.tencent.mmkv.MMKV
import io.trtc.tuikit.atomicx.theme.ThemeStore
import io.trtc.tuikit.atomicx.theme.tokens.ColorTokens
import io.trtc.tuikit.chat.app.R
import io.trtc.tuikit.chat.demo.common.AppConstants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class AboutActivity : BaseActivity() {

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
    private lateinit var entryContainer: LinearLayout

    private lateinit var tvIcp: TextView
    private lateinit var tvCompany: TextView
    private lateinit var tvCopyright: TextView

    private val entries = mutableListOf<AboutEntry>()

    companion object {
        fun start(context: Context) {
            context.startActivity(Intent(context, AboutActivity::class.java))
        }

        private const val URL_PRIVACY =
            "https://privacy.qq.com/document/preview/1cfe904fb7004b8ab1193a55857f7272"
        private const val URL_USER_AGREEMENT =
            "https://web.sdk.qcloud.com/document/Tencent-IM-User-Agreement.html"
        private const val URL_SELF_INFORMATION_COLLECTION =
            "https://privacy.qq.com/document/preview/45ba982a1ce6493597a00f8c86b52a1e"
        private const val URL_THIRD_SHARED =
            "https://privacy.qq.com/document/preview/dea84ac4bb88454794928b77126e9246"
        private const val URL_CONTACT_US = "https://cloud.tencent.com/product/im"
        private const val URL_ICP_SEARCH = "https://beian.miit.gov.cn"

        private const val TAG = "AboutActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (isFinishing) {
            return
        }
        setContentView(R.layout.demo_activity_about)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isStatusBarContrastEnforced = false
            window.isNavigationBarContrastEnforced = false
        }

        rootContainer = findViewById(R.id.demo_aboutRootContainer)
        headerContainer = findViewById(R.id.demo_chatHeaderContainer)
        tvTitle = findViewById(R.id.demo_tvChatTitle)
        btnBack = findViewById(R.id.demo_btnBack)
        btnMore = findViewById(R.id.demo_btnMore)
        headerDivider = findViewById(R.id.demo_headerDivider)
        badgeContainer = findViewById(R.id.demo_badgeContainer)
        leftContainer = findViewById(R.id.demo_leftContainer)
        scrollView = findViewById(R.id.demo_aboutScrollView)
        entryContainer = findViewById(R.id.demo_aboutEntryContainer)
        tvIcp = findViewById(R.id.demo_tvAboutIcp)
        tvCompany = findViewById(R.id.demo_tvAboutCompany)
        tvCopyright = findViewById(R.id.demo_tvAboutCopyright)

        ViewCompat.setOnApplyWindowInsetsListener(rootContainer) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            headerContainer.updatePadding(top = systemBars.top)
            scrollView.updatePadding(bottom = systemBars.bottom)
            insets
        }

        leftContainer.contentDescription = btnBack.contentDescription
        leftContainer.setOnClickListener { finish() }
        btnMore.visibility = View.GONE
        badgeContainer.visibility = View.GONE
        tvTitle.text = getString(R.string.demo_about_im)

        buildEntries()
        setupFooter()
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
    }

    override fun onStop() {
        super.onStop()
        activityScope?.cancel()
        activityScope = null
    }

    private fun buildEntries() {
        val sdkVersion = V2TIMManager.getInstance().version.orEmpty()
        addEntry(
            title = getString(R.string.demo_about_sdk_version),
            value = sdkVersion,
            showArrow = false,
            onClick = null
        )
        addEntry(
            title = getString(R.string.demo_about_privacy),
            onClick = { openWeb(URL_PRIVACY) }
        )
        addEntry(
            title = getString(R.string.demo_about_user_agreement),
            onClick = { openWeb(URL_USER_AGREEMENT) }
        )
        addEntry(
            title = getString(R.string.demo_about_statement),
            onClick = { showStatementDialog() }
        )
        addEntry(
            title = getString(R.string.demo_about_self_information_collection),
            onClick = { openWeb(URL_SELF_INFORMATION_COLLECTION) }
        )
        addEntry(
            title = getString(R.string.demo_about_third_part_shared),
            onClick = { openWeb(URL_THIRD_SHARED) }
        )
        if (!isLocalUserSigLogin()) {
            addEntry(
                title = getString(R.string.demo_about_cancel_account),
                onClick = { CancelAccountActivity.start(this) }
            )
        }
        addEntry(
            title = getString(R.string.demo_about_contact_us),
            onClick = { openWeb(URL_CONTACT_US) }
        )
        entries.lastOrNull()?.setDividerVisible(false)
    }

    private fun addEntry(
        title: String,
        value: String = "",
        showArrow: Boolean = true,
        onClick: (() -> Unit)?
    ) {
        val entry = AboutEntry(
            context = this,
            title = title,
            value = value,
            showArrow = showArrow,
            onClick = onClick
        )
        entries.add(entry)
        entryContainer.addView(
            entry.view,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
    }

    private fun isLocalUserSigLogin(): Boolean {
        val loginType = MMKV.defaultMMKV()
            .decodeString(AppConstants.KEY_LOGIN_TYPE, "").orEmpty()
        return loginType == AppConstants.LOGIN_TYPE_LOCAL
    }

    private fun setupFooter() {
        tvIcp.text = getString(R.string.demo_about_icp_number)
        tvIcp.setOnClickListener { openWeb(URL_ICP_SEARCH) }
        tvCompany.text = getString(R.string.demo_about_company)
        tvCopyright.text = getString(R.string.demo_about_copyright)
    }

    private fun showStatementDialog() {
        AlertDialog.Builder(this, dialogThemeRes())
            .setTitle(getString(R.string.demo_about_statement))
            .setMessage(getString(R.string.demo_about_statement_content))
            .setPositiveButton(getString(R.string.demo_about_confirm)) { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun dialogThemeRes(): Int {
        val bg = themeStore.themeState.value.currentTheme.tokens.color.bgColorOperate
        return if (isColorLight(bg)) {
            androidx.appcompat.R.style.Theme_AppCompat_Light_Dialog_Alert
        } else {
            androidx.appcompat.R.style.Theme_AppCompat_Dialog_Alert
        }
    }

    private fun openWeb(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        } catch (e: Exception) {
            Log.w(TAG, "openWeb failed: $url, ${e.message}")
        }
    }

    private fun applyColors(colors: ColorTokens) {
        rootContainer.setBackgroundColor(colors.bgColorTopBar)
        scrollView.setBackgroundColor(colors.bgColorTopBar)
        headerContainer.setBackgroundColor(colors.bgColorOperate)
        tvTitle.setTextColor(colors.textColorPrimary)
        btnBack.imageTintList = ColorStateList.valueOf(colors.textColorSecondary)
        headerDivider.setBackgroundColor(colors.strokeColorPrimary)
        entryContainer.setBackgroundColor(colors.bgColorOperate)

        for (entry in entries) {
            entry.applyColors(colors)
        }

        tvIcp.setTextColor(colors.textColorTertiary)
        tvCompany.setTextColor(colors.textColorTertiary)
        tvCopyright.setTextColor(colors.textColorTertiary)
    }

    private class AboutEntry(
        context: Context,
        title: String,
        value: String,
        showArrow: Boolean,
        onClick: (() -> Unit)?
    ) {
        val view: LinearLayout
        private val tvTitle: TextView
        private val tvValue: TextView
        private val arrowView: ImageView?
        private val divider: View
        private val density = context.resources.displayMetrics.density

        init {
            val container = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutDirection = View.LAYOUT_DIRECTION_LOCALE
            }
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutDirection = View.LAYOUT_DIRECTION_LOCALE
                gravity = Gravity.CENTER_VERTICAL
                val padH = (16f * density).toInt()
                val padV = (12f * density).toInt()
                setPadding(padH, padV, padH, padV)
                minimumHeight = (48f * density).toInt()
                if (onClick != null) {
                    isClickable = true
                    isFocusable = true
                    setOnClickListener { onClick.invoke() }
                }
            }

            tvTitle = TextView(context).apply {
                text = title
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                maxLines = 1
                textAlignment = View.TEXT_ALIGNMENT_VIEW_START
            }
            row.addView(
                tvTitle,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )

            tvValue = TextView(context).apply {
                text = value
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                textAlignment = View.TEXT_ALIGNMENT_VIEW_END
                val padStart = (16f * density).toInt()
                val padEnd = (8f * density).toInt()
                setPaddingRelative(padStart, 0, padEnd, 0)
            }
            row.addView(
                tvValue,
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            )

            arrowView = if (showArrow) {
                ImageView(context).apply {
                    setImageResource(R.drawable.demo_ic_arrow_right)
                    layoutParams = LinearLayout.LayoutParams(
                        (7f * density).toInt(),
                        (12f * density).toInt()
                    )
                }.also { row.addView(it) }
            } else {
                null
            }

            container.addView(
                row,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )

            divider = View(context).also { line ->
                container.addView(
                    line,
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        Math.max(1, (0.5f * density).toInt())
                    )
                )
            }

            view = container
        }

        fun setDividerVisible(visible: Boolean) {
            divider.visibility = if (visible) View.VISIBLE else View.GONE
        }

        fun applyColors(colors: ColorTokens) {
            tvTitle.setTextColor(colors.textColorSecondary)
            tvValue.setTextColor(colors.textColorPrimary)
            arrowView?.setColorFilter(colors.textColorTertiary)
            divider.setBackgroundColor(colors.strokeColorPrimary)
            view.setBackgroundColor(colors.bgColorOperate)
        }
    }
}
