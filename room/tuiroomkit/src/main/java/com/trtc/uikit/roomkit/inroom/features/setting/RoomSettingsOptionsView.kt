package com.trtc.uikit.roomkit.inroom.features.setting

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.trtc.uikit.roomkit.R
import io.trtc.tuikit.atomicxcore.api.device.DeviceStore
import io.trtc.tuikit.atomicxcore.api.device.VideoQuality
import java.util.Locale

class RoomSettingsOptionsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    private val mode: Mode,
) : FrameLayout(context, attrs, defStyleAttr) {

    enum class Mode { RESOLUTION, LANGUAGE }

    var navigationHost: RoomSettingsView.NavigationHost? = null

    private val navBar: RoomNavigationBar
    private val section: RoomSettingsSection
    private val deviceStore = DeviceStore.shared()

    init {
        LayoutInflater.from(context).inflate(R.layout.roomkit_settings_options_view, this, true)
        navBar = findViewById(R.id.options_nav_bar)
        section = findViewById(R.id.options_section)

        navBar.title = context.getString(
            if (mode == Mode.RESOLUTION) R.string.roomkit_settings_resolution else R.string.roomkit_settings_language
        )
        navBar.setOnBackClickListener { navigationHost?.finishCurrent() }

        buildRows()
        refreshSelected()
    }

    private fun buildRows() {
        when (mode) {
            Mode.RESOLUTION -> RESOLUTION_OPTIONS.forEach { quality ->
                val row = RoomSettingsRow.navigation(context).apply {
                    title = context.getString(qualityDisplayName(quality))
                    onTap = { selectResolution(quality) }
                }
                row.tag = quality
                section.addRow(row)
            }
            Mode.LANGUAGE -> LanguageOption.entries.forEach { option ->
                val row = RoomSettingsRow.navigation(context).apply {
                    title = context.getString(option.displayNameRes)
                    onTap = { selectLanguage(option) }
                }
                row.tag = option
                section.addRow(row)
            }
        }
    }

    private fun selectResolution(quality: VideoQuality) {
        deviceStore.updateVideoQuality(quality)
        refreshSelected()
    }

    private fun selectLanguage(option: LanguageOption) {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(option.tag))
        refreshSelected()
    }

    private fun refreshSelected() {
        for (index in 0 until section.childCount) {
            val child = section.getChildAt(index)
            if (child !is RoomSettingsRow) continue
            child.isChecked = when (mode) {
                Mode.RESOLUTION -> child.tag == deviceStore.deviceState.localVideoQuality.value
                Mode.LANGUAGE -> child.tag == currentLanguageOption()
            }
        }
    }

    enum class LanguageOption(val tag: String, val displayNameRes: Int) {
        ZH_HANS("zh-Hans", R.string.roomkit_settings_language_zh_cn),
        EN_US("en-US", R.string.roomkit_settings_language_en_us),
    }

    companion object {
        fun currentLanguageOption(): LanguageOption {
            val tag = currentLocaleTag()
            return LanguageOption.entries.firstOrNull { tag.startsWith(it.tag) }
                ?: if (tag.startsWith("zh")) LanguageOption.ZH_HANS else LanguageOption.EN_US
        }

        private fun currentLocaleTag(): String {
            val locales = AppCompatDelegate.getApplicationLocales()
            return if (locales.isEmpty) {
                Locale.getDefault().toLanguageTag()
            } else {
                locales[0]?.toLanguageTag().orEmpty()
            }
        }

        private val RESOLUTION_OPTIONS = listOf(
            VideoQuality.QUALITY_1080P,
            VideoQuality.QUALITY_720P,
            VideoQuality.QUALITY_540P,
            VideoQuality.QUALITY_360P,
        )
        private fun qualityDisplayName(quality: VideoQuality): Int = when (quality) {
            VideoQuality.QUALITY_1080P -> R.string.roomkit_settings_quality1080_p
            VideoQuality.QUALITY_720P -> R.string.roomkit_settings_quality720_p
            VideoQuality.QUALITY_540P -> R.string.roomkit_settings_quality540_p
            else -> R.string.roomkit_settings_quality360_p
        }
    }
}
