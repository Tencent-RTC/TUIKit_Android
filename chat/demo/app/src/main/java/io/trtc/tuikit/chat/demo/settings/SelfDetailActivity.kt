package io.trtc.tuikit.chat.demo.settings

import io.trtc.tuikit.chat.demo.common.BaseActivity

import android.app.DatePickerDialog
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import io.trtc.tuikit.chat.uikit.components.chatsetting.ui.TextInputDialog
import io.trtc.tuikit.atomicx.theme.ThemeStore
import io.trtc.tuikit.atomicx.theme.tokens.ColorTokens
import io.trtc.tuikit.chat.uikit.components.widgets.ActionItem
import io.trtc.tuikit.chat.uikit.components.widgets.ActionSheet
import io.trtc.tuikit.chat.uikit.components.widgets.Avatar
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.login.Gender
import io.trtc.tuikit.atomicxcore.api.login.LoginStore
import io.trtc.tuikit.atomicxcore.api.login.UserProfile
import io.trtc.tuikit.chat.app.R
import io.trtc.tuikit.chat.uikit.components.widgets.AvatarPickerDialog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class SelfDetailActivity : BaseActivity() {

    private val themeStore by lazy { ThemeStore.shared(this) }
    private var activityScope: CoroutineScope? = null

    private lateinit var rootContainer: LinearLayout
    private lateinit var headerContainer: LinearLayout
    private lateinit var tvTitle: TextView
    private lateinit var btnBack: ImageView
    private lateinit var btnMore: ImageView
    private lateinit var headerDivider: View
    private lateinit var badgeContainer: FrameLayout
    private lateinit var leftContainer: LinearLayout
    private lateinit var scrollView: ScrollView
    private lateinit var contentColumn: LinearLayout

    private lateinit var avatar: Avatar
    private lateinit var tvDisplayName: TextView
    private lateinit var entryContainer: LinearLayout

    private lateinit var accountItem: SettingsEntry
    private lateinit var nicknameItem: SettingsEntry
    private lateinit var statusItem: SettingsEntry
    private lateinit var genderItem: SettingsEntry
    private lateinit var birthdayItem: SettingsEntry

    private var cachedUserID: String = ""
    private var cachedNickname: String = ""
    private var cachedSignature: String = ""
    private var cachedGender: Gender = Gender.UNKNOWN
    private var cachedBirthday: Long? = null
    private var cachedAvatarUrl: String? = null

    companion object {
        fun start(context: Context) {
            context.startActivity(Intent(context, SelfDetailActivity::class.java))
        }

        private const val DEFAULT_BIRTHDAY_TEXT = "1970-01-01"
        private const val USER_AVATAR_URL_TEMPLATE =
            "https://im.sdk.qcloud.com/download/tuikit-resource/avatar/avatar_%s.png"
        private const val USER_AVATAR_URL_COUNT = 26
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (isFinishing) {
            return
        }
        setContentView(R.layout.demo_activity_self_detail)

        rootContainer = findViewById(R.id.demo_selfDetailRootContainer)
        headerContainer = findViewById(R.id.demo_chatHeaderContainer)
        tvTitle = findViewById(R.id.demo_tvChatTitle)
        btnBack = findViewById(R.id.demo_btnBack)
        btnMore = findViewById(R.id.demo_btnMore)
        headerDivider = findViewById(R.id.demo_headerDivider)
        badgeContainer = findViewById(R.id.demo_badgeContainer)
        leftContainer = findViewById(R.id.demo_leftContainer)
        scrollView = findViewById(R.id.demo_selfDetailScrollView)
        contentColumn = findViewById(R.id.demo_selfDetailContent)

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
        tvTitle.text = getString(R.string.demo_settings_self_detail_title)

        buildBody()
        applyColors(themeStore.themeState.value.currentTheme.tokens.color)

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

    override fun onDestroy() {
        super.onDestroy()
        activityScope?.cancel()
        activityScope = null
    }

    private fun buildBody() {
        val density = resources.displayMetrics.density
        val dp16 = (16f * density).toInt()
        val dp36 = (36f * density).toInt()

        avatar = Avatar(this).apply {
            setSize(Avatar.AvatarSize.XXL)
            setOnAvatarClickListener { showAvatarPicker() }
        }
        contentColumn.addView(
            avatar,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = dp16
            }
        )

        tvDisplayName = TextView(this).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            gravity = Gravity.CENTER
            maxLines = 3
            setPadding(dp16, dp16, dp16, 0)
        }
        contentColumn.addView(
            tvDisplayName,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        entryContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_LOCALE
        }
        contentColumn.addView(
            entryContainer,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp36
            }
        )

        accountItem = SettingsEntry(
            context = this,
            title = getString(R.string.demo_settings_self_detail_account),
            showArrow = false,
            showDivider = true
        )
        nicknameItem = SettingsEntry(
            context = this,
            title = getString(R.string.demo_settings_self_detail_nickname),
            showArrow = true,
            showDivider = true,
            onClick = { showNicknameEditor() }
        )
        statusItem = SettingsEntry(
            context = this,
            title = getString(R.string.demo_settings_self_detail_status),
            showArrow = true,
            showDivider = true,
            onClick = { showStatusEditor() }
        )
        genderItem = SettingsEntry(
            context = this,
            title = getString(R.string.demo_settings_self_detail_gender),
            showArrow = true,
            showDivider = true,
            onClick = { showGenderSelector() }
        )
        birthdayItem = SettingsEntry(
            context = this,
            title = getString(R.string.demo_settings_self_detail_birthday),
            showArrow = true,
            showDivider = true,
            onClick = { showBirthdayPicker() }
        )

        entryContainer.addView(accountItem.view, entryLayoutParams())
        entryContainer.addView(nicknameItem.view, entryLayoutParams())
        entryContainer.addView(statusItem.view, entryLayoutParams())
        entryContainer.addView(genderItem.view, entryLayoutParams())
        entryContainer.addView(birthdayItem.view, entryLayoutParams())
    }

    private fun entryLayoutParams(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

    private fun applyColors(colors: ColorTokens) {
        rootContainer.setBackgroundColor(colors.bgColorOperate)
        headerContainer.setBackgroundColor(colors.bgColorOperate)
        tvTitle.setTextColor(colors.textColorPrimary)
        btnBack.imageTintList = ColorStateList.valueOf(colors.textColorSecondary)
        headerDivider.setBackgroundColor(colors.strokeColorPrimary)

        contentColumn.setBackgroundColor(colors.bgColorOperate)
        tvDisplayName.setTextColor(colors.textColorPrimary)

        accountItem.applyColors(colors)
        nicknameItem.applyColors(colors)
        statusItem.applyColors(colors)
        genderItem.applyColors(colors)
        birthdayItem.applyColors(colors)
    }

    private fun updateUserProfile(profile: UserProfile?) {
        cachedUserID = profile?.userID.orEmpty()
        cachedNickname = profile?.nickname.orEmpty()
        cachedSignature = profile?.selfSignature.orEmpty()
        cachedGender = profile?.gender ?: Gender.UNKNOWN
        cachedBirthday = profile?.birthday
        cachedAvatarUrl = profile?.avatarURL

        val displayName = if (cachedNickname.isNotEmpty()) cachedNickname else cachedUserID
        tvDisplayName.text = displayName
        avatar.setContent(
            Avatar.AvatarContent.Image(url = cachedAvatarUrl, fallbackName = displayName)
        )
        accountItem.setValue(cachedUserID)
        nicknameItem.setValue(cachedNickname)
        statusItem.setValue(cachedSignature)
        genderItem.setValue(genderDisplayText(cachedGender))
        birthdayItem.setValue(birthdayDisplayText(cachedBirthday))
    }

    private fun genderDisplayText(gender: Gender?): String = when (gender) {
        Gender.MALE -> getString(R.string.demo_settings_self_detail_gender_male)
        Gender.FEMALE -> getString(R.string.demo_settings_self_detail_gender_female)
        else -> getString(R.string.demo_settings_self_detail_gender_secret)
    }

    private fun birthdayDisplayText(birthday: Long?): String {
        if (birthday == null || birthday <= 0L) {
            return DEFAULT_BIRTHDAY_TEXT
        }
        val raw = birthday.toString()
        return try {
            "${raw.substring(0, 4)}-${raw.substring(4, 6)}-${raw.substring(6, 8)}"
        } catch (_: Exception) {
            DEFAULT_BIRTHDAY_TEXT
        }
    }

    private fun showAvatarPicker() {
        val urls = buildAvatarUrls()
        AvatarPickerDialog(
            context = this,
            title = getString(R.string.demo_settings_self_detail_pick_avatar_title),
            imageUrls = urls,
            onImageSelected = { _, url ->
                val profile = UserProfile(avatarURL = url)
                LoginStore.shared.setSelfInfo(profile, noopCompletion())
            }
        ).show()
    }

    private fun showNicknameEditor() {
        TextInputDialog(
            context = this,
            title = getString(R.string.demo_settings_self_detail_edit_nickname_title),
            initialText = cachedNickname,
            onConfirm = { text ->
                if (text.isNotBlank()) {
                    val profile = UserProfile(nickname = text)
                    LoginStore.shared.setSelfInfo(profile, noopCompletion())
                }
            }
        ).show()
    }

    private fun showStatusEditor() {
        TextInputDialog(
            context = this,
            title = getString(R.string.demo_settings_self_detail_edit_status_title),
            initialText = cachedSignature,
            onConfirm = { text ->
                val profile = UserProfile(selfSignature = text)
                LoginStore.shared.setSelfInfo(profile, noopCompletion())
            }
        ).show()
    }

    private fun showGenderSelector() {
        val options = listOf(
            ActionItem(
                text = getString(R.string.demo_settings_self_detail_gender_male),
                value = Gender.MALE
            ),
            ActionItem(
                text = getString(R.string.demo_settings_self_detail_gender_female),
                value = Gender.FEMALE
            ),
            ActionItem(
                text = getString(R.string.demo_settings_self_detail_gender_secret),
                value = Gender.UNKNOWN
            )
        )
        ActionSheet.show(this, options) { selected ->
            val gender = selected.value as? Gender ?: Gender.UNKNOWN
            val profile = UserProfile(gender = gender)
            LoginStore.shared.setSelfInfo(profile, noopCompletion())
        }
    }

    private fun showBirthdayPicker() {
        val calendar = Calendar.getInstance()
        val existing = cachedBirthday
        if (existing != null && existing > 0L) {
            val raw = existing.toString()
            try {
                val year = raw.substring(0, 4).toInt()
                val month = raw.substring(4, 6).toInt() - 1
                val day = raw.substring(6, 8).toInt()
                calendar.set(year, month, day)
            } catch (_: Exception) {
            }
        }

        val picker = DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                val cal = Calendar.getInstance()
                cal.set(year, month, dayOfMonth)
                val birthdayStr = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
                    .format(Date(cal.timeInMillis))
                val profile = UserProfile(birthday = birthdayStr.toLong())
                LoginStore.shared.setSelfInfo(profile, noopCompletion())
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )
        picker.show()
    }

    private fun buildAvatarUrls(): List<String> {
        val list = mutableListOf<String>()
        for (i in 1..USER_AVATAR_URL_COUNT) {
            list.add(String.format(USER_AVATAR_URL_TEMPLATE, i))
        }
        return list
    }

    private fun noopCompletion(): CompletionHandler = object : CompletionHandler {
        override fun onSuccess() {}
        override fun onFailure(code: Int, desc: String) {}
    }

    private class SettingsEntry(
        context: Context,
        title: String,
        private val showArrow: Boolean,
        private val showDivider: Boolean,
        private val onClick: (() -> Unit)? = null
    ) {
        val view: LinearLayout
        private val tvTitle: TextView
        private val tvValue: TextView
        private val arrowView: ImageView?
        private val divider: View?
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

            divider = if (showDivider) {
                View(context).also { view ->
                    container.addView(
                        view,
                        LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            Math.max(1, (0.5f * density).toInt())
                        )
                    )
                }
            } else {
                null
            }

            view = container
        }

        fun setValue(value: String) {
            tvValue.text = value
        }

        fun applyColors(colors: ColorTokens) {
            tvTitle.setTextColor(colors.textColorSecondary)
            tvValue.setTextColor(colors.textColorPrimary)
            arrowView?.setColorFilter(colors.textColorTertiary)
            divider?.setBackgroundColor(colors.strokeColorSecondary)
            view.setBackgroundColor(colors.bgColorOperate)
        }
    }
}
