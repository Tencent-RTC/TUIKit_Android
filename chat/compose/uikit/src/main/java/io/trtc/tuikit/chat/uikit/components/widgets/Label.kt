package io.trtc.tuikit.chat.uikit.components.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.trtc.tuikit.chat.uikit.components.theme.Font
import io.trtc.tuikit.chat.uikit.components.theme.LocalTheme


enum class LabelSize {
    Small,
    Medium,
    Large
}

enum class LabelIconPosition {
    Start,
    End
}

@Composable
fun TitleLabel(
    text: String,
    modifier: Modifier = Modifier,
    size: LabelSize = LabelSize.Medium,
) {
    val colors = LocalTheme.current.colors
    val fonts = LocalTheme.current.fonts

    val font = when (size) {
        LabelSize.Small -> fonts.caption2Bold
        LabelSize.Medium -> fonts.caption1Bold
        LabelSize.Large -> fonts.title3Bold
    }

    Label(
        text = text,
        font = font,
        textColor = colors.textColorPrimary,
        backgroundColor = Color.Transparent,
        modifier = modifier
    )
}


@Composable
fun SubTitleLabel(
    text: String,
    modifier: Modifier = Modifier,
    size: LabelSize = LabelSize.Medium,
    icon: Int? = null,
    iconPosition: LabelIconPosition = LabelIconPosition.Start,
) {
    val colors = LocalTheme.current.colors
    val fonts = LocalTheme.current.fonts

    val font = when (size) {
        LabelSize.Small -> fonts.caption3Regular
        LabelSize.Medium -> fonts.caption2Regular
        LabelSize.Large -> fonts.caption1Regular
    }

    Label(
        text = text,
        font = font,
        textColor = colors.textColorSecondary,
        backgroundColor = Color.Transparent,
        icon = icon,
        iconPosition = iconPosition,
        modifier = modifier
    )
}

// MARK: - Item Label

@Composable
fun ItemLabel(
    text: String,
    modifier: Modifier = Modifier,
    size: LabelSize = LabelSize.Medium,
    icon: Int? = null,
    iconPosition: LabelIconPosition = LabelIconPosition.Start,
) {
    val colors = LocalTheme.current.colors
    val fonts = LocalTheme.current.fonts

    val font = when (size) {
        LabelSize.Small -> fonts.caption2Regular
        LabelSize.Medium -> fonts.caption1Regular
        LabelSize.Large -> fonts.body4Regular
    }

    Label(
        text = text,
        font = font,
        textColor = colors.textColorPrimary,
        backgroundColor = Color.Transparent,
        icon = icon,
        iconPosition = iconPosition,
        modifier = modifier
    )
}

// MARK: - Danger Label

@Composable
fun DangerLabel(
    text: String,
    modifier: Modifier = Modifier,
    size: LabelSize = LabelSize.Medium,
    icon: Int? = null,
    iconPosition: LabelIconPosition = LabelIconPosition.Start,
) {
    val colors = LocalTheme.current.colors
    val fonts = LocalTheme.current.fonts

    val font = when (size) {
        LabelSize.Small -> fonts.caption2Regular
        LabelSize.Medium -> fonts.caption1Regular
        LabelSize.Large -> fonts.body4Regular
    }

    Label(
        text = text,
        font = font,
        textColor = colors.textColorError,
        backgroundColor = Color.Transparent,
        icon = icon,
        iconPosition = iconPosition,
        modifier = modifier
    )
}

// MARK: - Custom Label

@Composable
fun CustomLabel(
    text: String,
    font: Font,
    textColor: Color,
    modifier: Modifier = Modifier,
    backgroundColor: Color = Color.Transparent,
    lineLimit: Int = 1,
    icon: Int? = null,
    iconPosition: LabelIconPosition = LabelIconPosition.Start,
) {
    Label(
        text = text,
        font = font,
        textColor = textColor,
        backgroundColor = backgroundColor,
        lineLimit = lineLimit,
        icon = icon,
        iconPosition = iconPosition,
        modifier = modifier
    )
}

// MARK: - Base Label

@Composable
private fun Label(
    text: String,
    font: Font,
    textColor: Color,
    backgroundColor: Color,
    modifier: Modifier = Modifier,
    lineLimit: Int = 1,
    icon: Int? = null,
    iconPosition: LabelIconPosition = LabelIconPosition.Start,
) {
    val radius = LocalTheme.current.radius
    val spacings = LocalTheme.current.spacings
    val content = @Composable {
        if (icon != null) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(spacings.iconTextSpacing),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (iconPosition == LabelIconPosition.Start) {
                    Icon(
                        painter = painterResource(id = icon),
                        contentDescription = null,
                        tint = textColor,
                        modifier = Modifier.size(14.dp)
                    )
                }

                Text(
                    text = text,
                    style = TextStyle(
                        fontSize = font.size,
                        fontWeight = font.weight,
                        color = textColor
                    ),
                    maxLines = lineLimit,
                    overflow = TextOverflow.Ellipsis
                )

                if (iconPosition == LabelIconPosition.End) {
                    Icon(
                        painter = painterResource(id = icon),
                        contentDescription = null,
                        tint = textColor,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        } else {
            Text(
                text = text,
                style = TextStyle(
                    fontSize = font.size,
                    fontWeight = font.weight,
                    color = textColor
                ),
                maxLines = lineLimit,
                overflow = TextOverflow.Ellipsis
            )
        }
    }

    if (backgroundColor != Color.Transparent) {
        Box(
            modifier = modifier
                .clip(RoundedCornerShape(radius.tipsRadius))
                .background(backgroundColor)
                .padding(horizontal = spacings.iconTextSpacing),
            contentAlignment = Alignment.Center
        ) {
            content()
        }
    } else {
        Box(
            modifier = modifier.padding(horizontal = spacings.iconTextSpacing),
            contentAlignment = Alignment.Center
        ) {
            content()
        }
    }
}

