package com.trtc.uikit.livekit.voicelounge.settings.theme

import android.graphics.Color
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.annotation.RawRes
import androidx.annotation.StringRes
import com.trtc.uikit.livekit.R
import com.trtc.uikit.livekit.voicelounge.blackAlpha
import com.trtc.uikit.livekit.voicelounge.rgb
import com.trtc.uikit.livekit.voicelounge.vlString
import com.trtc.uikit.livekit.voicelounge.whiteAlpha

internal data class VoiceLoungeSkin(
    val id: String,
    @StringRes val nameRes: Int,
    @ColorInt val deep: Int,
    @ColorInt val mid: Int,
    @ColorInt val light: Int,
) {
    val displayName: String get() = vlString(nameRes)

    val swatchColors: IntArray get() = intArrayOf(deep, mid, light)
    val swatchLocations: FloatArray get() = floatArrayOf(0f, 0.55f, 1f)

    val textSoft: Int get() = blendedWithWhite(mid, 0.75f)

    val textStrong: Int get() = blendedWithWhite(mid, 0.4f)

    companion object {
        val all: List<VoiceLoungeSkin> = listOf(
            make("blue", R.string.livekit_voiceroom_skin_blue, 0x0052D9, 0x4A9EFF, 0x7EE8FF),
            make("red", R.string.livekit_voiceroom_skin_red, 0xC9162B, 0xFF5B5B, 0xFFB199),
            make("green", R.string.livekit_voiceroom_skin_green, 0x06A35A, 0x3ED598, 0xA8F0C6),
            make("purple", R.string.livekit_voiceroom_skin_purple, 0x0052D9, 0x7B4DFF, 0xFF5B7A),
            make("gold", R.string.livekit_voiceroom_skin_gold, 0xA66A00, 0xF5C542, 0xFFE9A8),
        )

        const val DEFAULT_ID = "blue"

        val defaultSkin: VoiceLoungeSkin get() = all.firstOrNull { it.id == DEFAULT_ID } ?: all[0]

        fun skin(id: String): VoiceLoungeSkin? = all.firstOrNull { it.id == id }

        private fun make(id: String, @StringRes nameRes: Int, deep: Int, mid: Int, light: Int) =
            VoiceLoungeSkin(id, nameRes, opaque(deep), opaque(mid), opaque(light))

        private fun opaque(hex: Int): Int = Color.rgb((hex shr 16) and 0xFF, (hex shr 8) and 0xFF, hex and 0xFF)

        private fun blendedWithWhite(color: Int, ratio: Float): Int {
            val keep = 1f - ratio
            fun mix(channel: Int): Int = (channel * keep + 255f * ratio).toInt().coerceIn(0, 255)
            return Color.rgb(mix(Color.red(color)), mix(Color.green(color)), mix(Color.blue(color)))
        }
    }
}

internal data class VoiceLoungeBackground(
    val id: String,
    @StringRes val nameRes: Int,
    @DrawableRes val imageRes: Int,
    @DrawableRes val previewRes: Int,
    @RawRes val gifRes: Int?,
) {
    val displayName: String get() = vlString(nameRes)

    companion object {
        val all: List<VoiceLoungeBackground> = listOf(
            VoiceLoungeBackground(
                "sakura", R.string.livekit_voiceroom_bg_sakura, R.drawable.livekit_voicebg_sakura,
                R.drawable.livekit_voicebg_sakura_preview, R.raw.livekit_voicebg_sakura,
            ),
            VoiceLoungeBackground(
                "cyberpunk", R.string.livekit_voiceroom_bg_cyberpunk, R.drawable.livekit_voicebg_cyberpunk,
                R.drawable.livekit_voicebg_cyberpunk_preview, R.raw.livekit_voicebg_cyberpunk,
            ),
            VoiceLoungeBackground(
                "aurora", R.string.livekit_voiceroom_bg_aurora, R.drawable.livekit_voicebg_aurora,
                R.drawable.livekit_voicebg_aurora_preview, R.raw.livekit_voicebg_aurora,
            ),
            VoiceLoungeBackground(
                "moonlit", R.string.livekit_voiceroom_bg_moonlit, R.drawable.livekit_voicebg_moonlit,
                R.drawable.livekit_voicebg_moonlit_preview, R.raw.livekit_voicebg_moonlit,
            ),
            VoiceLoungeBackground(
                "mountain", R.string.livekit_voiceroom_bg_mountain, R.drawable.livekit_voicebg_mountain,
                R.drawable.livekit_voicebg_mountain_preview, R.raw.livekit_voicebg_mountain,
            ),
            VoiceLoungeBackground(
                "sea", R.string.livekit_voiceroom_bg_sea, R.drawable.livekit_voicebg_sea,
                R.drawable.livekit_voicebg_sea_preview, R.raw.livekit_voicebg_sea,
            ),
            VoiceLoungeBackground(
                "space", R.string.livekit_voiceroom_bg_space, R.drawable.livekit_voicebg_space,
                R.drawable.livekit_voicebg_space_preview, null,
            ),
            VoiceLoungeBackground(
                "travel", R.string.livekit_voiceroom_bg_travel, R.drawable.livekit_voicebg_travel,
                R.drawable.livekit_voicebg_travel_preview, null,
            ),
            VoiceLoungeBackground(
                "party", R.string.livekit_voiceroom_bg_party, R.drawable.livekit_voicebg_party,
                R.drawable.livekit_voicebg_party_preview, R.raw.livekit_voicebg_party,
            ),
        )

        const val DEFAULT_ID = "moonlit"

        val defaultBackground: VoiceLoungeBackground get() = all.firstOrNull { it.id == DEFAULT_ID } ?: all[0]

        fun background(id: String): VoiceLoungeBackground? = all.firstOrNull { it.id == id }
    }
}

internal object VoiceLoungeColor {
    val overlayHair = whiteAlpha(0.06f)
    val overlaySoft = whiteAlpha(0.08f)
    val overlayBase = whiteAlpha(0.10f)
    val overlayMarked = whiteAlpha(0.14f)

    val card = blackAlpha(0.28f)

    val transcriberCard = rgb(1.0f, 1.0f, 1.0f, 0.12f)

    val transcriberNameGradient = intArrayOf(rgb(1.0f, 0.5f, 0.62f), rgb(0.7f, 0.62f, 1.0f))

    val brandCyan = rgb(0.49f, 0.91f, 1f)
    val brandBlue = rgb(0.29f, 0.62f, 1f)
    val nickname = rgb(0.471f, 0.847f, 1f)
    val audienceGiftValue = rgb(1f, 0.886f, 0.604f)

    val rose = rgb(1.0f, 0.325f, 0.439f)
    val statHighlight = rgb(0.557f, 0.812f, 1f)

    val prepareDeep = rgb(0.188f, 0.506f, 0.812f)
    val prepareMid = rgb(0.478f, 0.663f, 0.839f)
    val prepareLight = rgb(0.725f, 0.8f, 0.875f)
    val prepareIconBackground = rgb(0.078f, 0.078f, 0.086f, 0.5f)
    val prepareIconBorder = whiteAlpha(0.102f)

    val dialogBackground = rgb(0.094f, 0.086f, 0.125f)
    val floatingBar = rgb(0.08f, 0.08f, 0.08f, 0.92f)
    val floatingBarIcon = whiteAlpha(0.7f)

    val avatarPlaceholders: List<Int> = listOf(
        rgb(0.42f, 0.49f, 0.9f),
        rgb(0.9f, 0.45f, 0.55f),
        rgb(0.35f, 0.73f, 0.6f),
        rgb(0.78f, 0.55f, 0.9f),
        rgb(0.95f, 0.68f, 0.4f),
    )
}
