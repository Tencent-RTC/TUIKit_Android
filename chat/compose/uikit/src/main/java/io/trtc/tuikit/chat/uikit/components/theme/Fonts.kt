package io.trtc.tuikit.chat.uikit.components.theme

import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import io.trtc.tuikit.atomicx.theme.tokens.FontTokens
import io.trtc.tuikit.atomicx.theme.tokens.Font as TokenFont

data class Font(val size: TextUnit, val weight: FontWeight)

val DefaultFontScheme = FontScheme()

internal fun FontTokens.toFontScheme(): FontScheme {
    fun of(token: TokenFont, weight: FontWeight) = Font(size = token.size.sp, weight = weight)
    return FontScheme(
        title1Bold = of(bold40, FontWeight.Bold),
        title2Bold = of(bold36, FontWeight.Bold),
        title3Bold = of(bold34, FontWeight.Bold),
        title4Bold = of(bold32, FontWeight.Bold),
        body1Bold = of(bold28, FontWeight.Bold),
        body2Bold = of(bold24, FontWeight.Bold),
        body3Bold = of(bold20, FontWeight.Bold),
        body4Bold = of(bold18, FontWeight.Bold),
        caption1Bold = of(bold16, FontWeight.Bold),
        caption2Bold = of(bold14, FontWeight.Bold),
        caption3Bold = of(bold12, FontWeight.Bold),
        caption4Bold = of(bold10, FontWeight.Bold),

        title1Medium = of(regular40, FontWeight.Medium),
        title2Medium = of(regular36, FontWeight.Medium),
        title3Medium = of(regular34, FontWeight.Medium),
        title4Medium = of(regular32, FontWeight.Medium),
        body1Medium = of(regular28, FontWeight.Medium),
        body2Medium = of(regular24, FontWeight.Medium),
        body3Medium = of(regular20, FontWeight.Medium),
        body4Medium = of(regular18, FontWeight.Medium),
        caption1Medium = of(regular16, FontWeight.Medium),
        caption2Medium = of(regular14, FontWeight.Medium),
        caption3Medium = of(regular12, FontWeight.Medium),
        caption4Medium = of(regular10, FontWeight.Medium),

        title1Regular = of(regular40, FontWeight.Normal),
        title2Regular = of(regular36, FontWeight.Normal),
        title3Regular = of(regular34, FontWeight.Normal),
        title4Regular = of(regular32, FontWeight.Normal),
        body1Regular = of(regular28, FontWeight.Normal),
        body2Regular = of(regular24, FontWeight.Normal),
        body3Regular = of(regular20, FontWeight.Normal),
        body4Regular = of(regular18, FontWeight.Normal),
        caption1Regular = of(regular16, FontWeight.Normal),
        caption2Regular = of(regular14, FontWeight.Normal),
        caption3Regular = of(regular12, FontWeight.Normal),
        caption4Regular = of(regular10, FontWeight.Normal),
    )
}

data class FontScheme(
    val title1Bold: Font = Fonts.Bold40,
    val title2Bold: Font = Fonts.Bold36,
    val title3Bold: Font = Fonts.Bold34,
    val title4Bold: Font = Fonts.Bold32,
    val body1Bold: Font = Fonts.Bold28,
    val body2Bold: Font = Fonts.Bold24,
    val body3Bold: Font = Fonts.Bold20,
    val body4Bold: Font = Fonts.Bold18,
    val caption1Bold: Font = Fonts.Bold16,
    val caption2Bold: Font = Fonts.Bold14,
    val caption3Bold: Font = Fonts.Bold12,
    val caption4Bold: Font = Fonts.Bold10,

    val title1Medium: Font = Fonts.Medium40,
    val title2Medium: Font = Fonts.Medium36,
    val title3Medium: Font = Fonts.Medium34,
    val title4Medium: Font = Fonts.Medium32,
    val body1Medium: Font = Fonts.Medium28,
    val body2Medium: Font = Fonts.Medium24,
    val body3Medium: Font = Fonts.Medium20,
    val body4Medium: Font = Fonts.Medium18,
    val caption1Medium: Font = Fonts.Medium16,
    val caption2Medium: Font = Fonts.Medium14,
    val caption3Medium: Font = Fonts.Medium12,
    val caption4Medium: Font = Fonts.Medium10,

    val title1Regular: Font = Fonts.Regular40,
    val title2Regular: Font = Fonts.Regular36,
    val title3Regular: Font = Fonts.Regular34,
    val title4Regular: Font = Fonts.Regular32,
    val body1Regular: Font = Fonts.Regular28,
    val body2Regular: Font = Fonts.Regular24,
    val body3Regular: Font = Fonts.Regular20,
    val body4Regular: Font = Fonts.Regular18,
    val caption1Regular: Font = Fonts.Regular16,
    val caption2Regular: Font = Fonts.Regular14,
    val caption3Regular: Font = Fonts.Regular12,
    val caption4Regular: Font = Fonts.Regular10,
)


data object Fonts {
    val Bold40 = Font(size = 40.sp, weight = FontWeight.Bold)
    val Bold36 = Font(size = 36.sp, weight = FontWeight.Bold)
    val Bold34 = Font(size = 34.sp, weight = FontWeight.Bold)
    val Bold32 = Font(size = 32.sp, weight = FontWeight.Bold)
    val Bold28 = Font(size = 28.sp, weight = FontWeight.Bold)
    val Bold24 = Font(size = 24.sp, weight = FontWeight.Bold)
    val Bold20 = Font(size = 20.sp, weight = FontWeight.Bold)
    val Bold18 = Font(size = 18.sp, weight = FontWeight.Bold)
    val Bold16 = Font(size = 16.sp, weight = FontWeight.Bold)
    val Bold14 = Font(size = 14.sp, weight = FontWeight.Bold)
    val Bold12 = Font(size = 12.sp, weight = FontWeight.Bold)
    val Bold10 = Font(size = 10.sp, weight = FontWeight.Bold)

    val Medium40 = Font(size = 40.sp, weight = FontWeight.Medium)
    val Medium36 = Font(size = 36.sp, weight = FontWeight.Medium)
    val Medium34 = Font(size = 34.sp, weight = FontWeight.Medium)
    val Medium32 = Font(size = 32.sp, weight = FontWeight.Medium)
    val Medium28 = Font(size = 28.sp, weight = FontWeight.Medium)
    val Medium24 = Font(size = 24.sp, weight = FontWeight.Medium)
    val Medium20 = Font(size = 20.sp, weight = FontWeight.Medium)
    val Medium18 = Font(size = 18.sp, weight = FontWeight.Medium)
    val Medium16 = Font(size = 16.sp, weight = FontWeight.Medium)
    val Medium14 = Font(size = 14.sp, weight = FontWeight.Medium)
    val Medium12 = Font(size = 12.sp, weight = FontWeight.Medium)
    val Medium10 = Font(size = 10.sp, weight = FontWeight.Medium)

    val Regular40 = Font(size = 40.sp, weight = FontWeight.Normal)
    val Regular36 = Font(size = 36.sp, weight = FontWeight.Normal)
    val Regular34 = Font(size = 34.sp, weight = FontWeight.Normal)
    val Regular32 = Font(size = 32.sp, weight = FontWeight.Normal)
    val Regular28 = Font(size = 28.sp, weight = FontWeight.Normal)
    val Regular24 = Font(size = 24.sp, weight = FontWeight.Normal)
    val Regular20 = Font(size = 20.sp, weight = FontWeight.Normal)
    val Regular18 = Font(size = 18.sp, weight = FontWeight.Normal)
    val Regular16 = Font(size = 16.sp, weight = FontWeight.Normal)
    val Regular14 = Font(size = 14.sp, weight = FontWeight.Normal)
    val Regular12 = Font(size = 12.sp, weight = FontWeight.Normal)
    val Regular10 = Font(size = 10.sp, weight = FontWeight.Normal)
}

