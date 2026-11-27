package io.trtc.tuikit.chat.demo.settings

import io.trtc.tuikit.chat.demo.common.BaseActivity

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import io.trtc.tuikit.atomicx.theme.ThemeStore
import io.trtc.tuikit.atomicx.theme.tokens.ColorTokens
import io.trtc.tuikit.chat.app.R
import io.trtc.tuikit.chat.uikit.components.ai.tts.VoiceMessageConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class VoiceMessageSettingActivity : BaseActivity() {

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
    private lateinit var settingGroup: LinearLayout
    private lateinit var groupDivider: View
    private lateinit var itemClone: View
    private lateinit var itemSelect: View

    companion object {
        fun start(context: Context) {
            context.startActivity(Intent(context, VoiceMessageSettingActivity::class.java))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (isFinishing) {
            return
        }
        setContentView(R.layout.demo_activity_voice_message_setting)

        rootContainer = findViewById(R.id.demo_voiceSettingRoot)
        headerContainer = findViewById(R.id.demo_chatHeaderContainer)
        tvTitle = findViewById(R.id.demo_tvChatTitle)
        btnBack = findViewById(R.id.demo_btnBack)
        btnMore = findViewById(R.id.demo_btnMore)
        headerDivider = findViewById(R.id.demo_headerDivider)
        badgeContainer = findViewById(R.id.demo_badgeContainer)
        leftContainer = findViewById(R.id.demo_leftContainer)
        scrollView = findViewById(R.id.demo_voiceSettingScroll)
        settingGroup = findViewById(R.id.demo_voiceSettingGroup)
        groupDivider = findViewById(R.id.demo_voiceSettingDivider)
        itemClone = findViewById(R.id.demo_itemVoiceClone)
        itemSelect = findViewById(R.id.demo_itemVoiceSelect)

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
        tvTitle.text = getString(R.string.demo_voice_message_settings)

        setupEntry(itemClone, getString(R.string.demo_voice_clone), "") {
            VoiceCloneActivity.start(this)
        }
        setupEntry(itemSelect, getString(R.string.demo_voice_select), selectedVoiceDisplayName()) {
            VoiceSelectActivity.start(this)
        }

        applyColors(themeStore.themeState.value.currentTheme.tokens.color)
    }

    override fun onResume() {
        super.onResume()
        // Cloning auto-selects a new voice and the selection page can change it,
        // so refresh the displayed value on return.
        itemSelect.findViewById<TextView>(R.id.demo_tvSettingsValue).text = selectedVoiceDisplayName()
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

    private fun selectedVoiceDisplayName(): String {
        val name = VoiceMessageConfig.getSelectedVoiceName(this)
        return name.ifEmpty { getString(io.trtc.tuikit.chat.uikit.R.string.voice_message_voice_default) }
    }

    private fun setupEntry(view: View, title: String, value: String, onClick: () -> Unit) {
        view.findViewById<TextView>(R.id.demo_tvSettingsTitle).text = title
        view.findViewById<TextView>(R.id.demo_tvSettingsValue).text = value
        view.setOnClickListener { onClick() }
    }

    private fun applyColors(colors: ColorTokens) {
        rootContainer.setBackgroundColor(colors.bgColorTopBar)
        scrollView.setBackgroundColor(colors.bgColorTopBar)
        headerContainer.setBackgroundColor(colors.bgColorOperate)
        tvTitle.setTextColor(colors.textColorPrimary)
        btnBack.imageTintList = ColorStateList.valueOf(colors.textColorSecondary)
        headerDivider.setBackgroundColor(colors.strokeColorPrimary)

        settingGroup.setBackgroundColor(colors.bgColorOperate)
        groupDivider.setBackgroundColor(colors.strokeColorPrimary)

        for (item in listOf(itemClone, itemSelect)) {
            item.findViewById<TextView>(R.id.demo_tvSettingsTitle).setTextColor(colors.textColorSecondary)
            item.findViewById<TextView>(R.id.demo_tvSettingsValue).setTextColor(colors.textColorPrimary)
            item.findViewById<ImageView>(R.id.demo_ivArrow).setColorFilter(colors.textColorTertiary)
        }
    }
}
