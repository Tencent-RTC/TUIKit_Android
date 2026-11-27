package io.trtc.tuikit.chat.uikit.components.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.trtc.tuikit.chat.uikit.components.theme.ColorScheme
import io.trtc.tuikit.chat.uikit.components.theme.LocalTheme

sealed class ButtonType {
    data object Filled : ButtonType()
    data object Outlined : ButtonType()
    data object NoBorder : ButtonType()
}

sealed class ButtonColorType {
    data object Primary : ButtonColorType()
    data object Secondary : ButtonColorType()
    data object Danger : ButtonColorType()
}

sealed class ButtonContent {
    data class TextOnly(val text: String) : ButtonContent()
    data class IconOnly(val painter: Painter) : ButtonContent()
    data class IconWithText(
        val text: String,
        val painter: Painter,
        val iconPosition: IconPosition = IconPosition.Start
    ) : ButtonContent()
}

sealed class IconPosition {
    data object Start : IconPosition()
    data object End : IconPosition()
}

enum class ButtonSize(
    val height: Dp,
    val horizontalPadding: Dp,
    val minWidth: Dp,
    val fontSize: Int,
    val iconSize: Dp
) {
    XS(height = 24.dp, horizontalPadding = 8.dp, minWidth = 48.dp, fontSize = 12, iconSize = 14.dp),
    S(height = 32.dp, horizontalPadding = 12.dp, minWidth = 64.dp, fontSize = 14, iconSize = 16.dp),
    M(height = 40.dp, horizontalPadding = 16.dp, minWidth = 80.dp, fontSize = 16, iconSize = 20.dp),
    L(height = 48.dp, horizontalPadding = 20.dp, minWidth = 96.dp, fontSize = 16, iconSize = 20.dp),
}

private data class ButtonColors(
    val backgroundColor: Color,
    val textColor: Color,
    val borderColor: Color
)

@Composable
fun IconButton(painter: Painter, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(
        modifier = modifier,
        buttonContent = ButtonContent.IconOnly(painter),
        type = ButtonType.Filled,
        onClick = onClick
    )
}

@Composable
fun FilledButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(
        modifier = modifier,
        buttonContent = ButtonContent.TextOnly(text = text),
        type = ButtonType.Filled,
        onClick = onClick
    )
}

@Composable
fun OutlinedButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(
        modifier = modifier,
        buttonContent = ButtonContent.TextOnly(text = text),
        type = ButtonType.Outlined,
        onClick = onClick
    )
}

@Composable
fun Button(
    buttonContent: ButtonContent,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    type: ButtonType = ButtonType.Filled,
    colorType: ButtonColorType = ButtonColorType.Primary,
    size: ButtonSize = ButtonSize.L,
    onClick: () -> Unit,
) {
    val colors: ColorScheme = LocalTheme.current.colors
    val fonts = LocalTheme.current.fonts
    val radius = LocalTheme.current.radius
    val spacings = LocalTheme.current.spacings
    val shape = RoundedCornerShape(radius.roundRadius)
    val fontSize = when (size) {
        ButtonSize.XS -> fonts.caption3Bold.size
        ButtonSize.S -> fonts.caption2Bold.size
        ButtonSize.M -> fonts.caption1Bold.size
        ButtonSize.L -> fonts.caption1Bold.size
    }
    val lineHeight = (fontSize.value * 1.5f).sp
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isPressed by interactionSource.collectIsPressedAsState()

    val (backgroundColor, textColor, borderColor) = when {
        !enabled -> getDisabledColors(type, colorType, colors)
        isPressed -> getActiveColors(type, colorType, colors)
        isHovered -> getHoverColors(type, colorType, colors)
        else -> getDefaultColors(type, colorType, colors)
    }

    val minWidth = when (buttonContent) {
        is ButtonContent.IconOnly -> size.height
        is ButtonContent.IconWithText -> size.minWidth
        is ButtonContent.TextOnly -> size.minWidth
    }

    Box(
        modifier = modifier
            .height(size.height)
            .widthIn(min = minWidth)
            .clip(shape)
            .background(color = backgroundColor, shape = shape)
            .then(
                if (type == ButtonType.Outlined) {
                    Modifier.border(width = 1.dp, color = borderColor, shape = shape)
                } else {
                    Modifier
                }
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
                enabled = enabled,
                role = Role.Button
            )
            .then(
                if (buttonContent !is ButtonContent.IconOnly) {
                    Modifier.padding(horizontal = size.horizontalPadding)
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        when (buttonContent) {
            is ButtonContent.TextOnly -> {
                Text(
                    text = buttonContent.text,
                    style = TextStyle(
                        fontSize = fontSize,
                        fontWeight = FontWeight.Medium,
                        lineHeight = lineHeight,
                        color = textColor
                    ), maxLines = 1
                )
            }

            is ButtonContent.IconOnly -> {
                Icon(
                    painter = buttonContent.painter,
                    contentDescription = null,
                    modifier = Modifier.size(size.iconSize),
                    tint = textColor
                )
            }

            is ButtonContent.IconWithText -> {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(spacings.iconTextSpacing),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val iconPainter = buttonContent.painter
                    val text = buttonContent.text
                    val iconPosition = buttonContent.iconPosition
                    when (iconPosition) {
                        IconPosition.Start -> {
                            Icon(
                                painter = iconPainter,
                                contentDescription = null,
                                modifier = Modifier.size(size.iconSize),
                                tint = textColor
                            )
                            Text(
                                text = text,
                                style = TextStyle(
                                    fontSize = fontSize,
                                    fontWeight = FontWeight.Medium,
                                    lineHeight = lineHeight,
                                    color = textColor
                                ), maxLines = 1
                            )
                        }

                        IconPosition.End -> {
                            Text(
                                text = text,
                                style = TextStyle(
                                    fontSize = fontSize,
                                    fontWeight = FontWeight.Medium,
                                    lineHeight = lineHeight,
                                    color = textColor
                                ), maxLines = 1
                            )
                            Icon(
                                painter = iconPainter,
                                contentDescription = null,
                                modifier = Modifier.size(size.iconSize),
                                tint = textColor
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun getDefaultColors(
    type: ButtonType,
    colorType: ButtonColorType,
    colors: ColorScheme
): ButtonColors {
    return when (type) {
        ButtonType.Filled -> when (colorType) {
            ButtonColorType.Primary -> ButtonColors(
                colors.buttonColorPrimaryDefault,
                colors.textColorButton,
                Color.Transparent
            )

            ButtonColorType.Secondary -> ButtonColors(
                colors.buttonColorSecondaryDefault,
                colors.textColorPrimary,
                Color.Transparent
            )

            ButtonColorType.Danger -> ButtonColors(
                colors.buttonColorHangupDefault,
                colors.textColorButton,
                Color.Transparent
            )
        }

        ButtonType.Outlined -> when (colorType) {
            ButtonColorType.Primary -> ButtonColors(
                Color.Transparent,
                colors.buttonColorPrimaryDefault,
                colors.buttonColorPrimaryDefault
            )

            ButtonColorType.Secondary -> ButtonColors(
                Color.Transparent,
                colors.textColorPrimary,
                colors.strokeColorPrimary
            )

            ButtonColorType.Danger -> ButtonColors(
                Color.Transparent,
                colors.buttonColorHangupDefault,
                colors.buttonColorHangupDefault
            )
        }

        ButtonType.NoBorder -> when (colorType) {
            ButtonColorType.Primary -> ButtonColors(
                Color.Transparent,
                colors.buttonColorPrimaryDefault,
                Color.Transparent
            )

            ButtonColorType.Secondary -> ButtonColors(
                Color.Transparent,
                colors.textColorPrimary,
                Color.Transparent
            )

            ButtonColorType.Danger -> ButtonColors(
                Color.Transparent,
                colors.buttonColorHangupDefault,
                Color.Transparent
            )
        }
    }
}

@Composable
private fun getHoverColors(
    type: ButtonType,
    colorType: ButtonColorType,
    colors: ColorScheme
): ButtonColors {
    return when (type) {
        ButtonType.Filled -> when (colorType) {
            ButtonColorType.Primary -> ButtonColors(
                colors.buttonColorPrimaryHover,
                colors.textColorButton,
                Color.Transparent
            )

            ButtonColorType.Secondary -> ButtonColors(
                colors.buttonColorSecondaryHover,
                colors.textColorSecondary,
                Color.Transparent
            )

            ButtonColorType.Danger -> ButtonColors(
                colors.buttonColorHangupHover,
                colors.textColorButton,
                Color.Transparent
            )
        }

        ButtonType.Outlined -> when (colorType) {
            ButtonColorType.Primary -> ButtonColors(
                Color.Transparent,
                colors.buttonColorPrimaryHover,
                colors.buttonColorPrimaryHover
            )

            ButtonColorType.Secondary -> ButtonColors(
                Color.Transparent,
                colors.textColorSecondary,
                colors.strokeColorSecondary
            )

            ButtonColorType.Danger -> ButtonColors(
                Color.Transparent,
                colors.buttonColorHangupHover,
                colors.buttonColorHangupHover
            )
        }

        ButtonType.NoBorder -> when (colorType) {
            ButtonColorType.Primary -> ButtonColors(
                Color.Transparent,
                colors.buttonColorPrimaryHover,
                Color.Transparent
            )

            ButtonColorType.Secondary -> ButtonColors(
                Color.Transparent,
                colors.textColorSecondary,
                Color.Transparent
            )

            ButtonColorType.Danger -> ButtonColors(
                Color.Transparent,
                colors.buttonColorHangupHover,
                Color.Transparent
            )
        }
    }
}

@Composable
private fun getActiveColors(
    type: ButtonType,
    colorType: ButtonColorType,
    colors: ColorScheme
): ButtonColors {
    return when (type) {
        ButtonType.Filled -> when (colorType) {
            ButtonColorType.Primary -> ButtonColors(
                colors.buttonColorPrimaryActive,
                colors.textColorButton,
                Color.Transparent
            )

            ButtonColorType.Secondary -> ButtonColors(
                colors.buttonColorSecondaryActive,
                colors.textColorTertiary,
                Color.Transparent
            )

            ButtonColorType.Danger -> ButtonColors(
                colors.buttonColorHangupActive,
                colors.textColorButton,
                Color.Transparent
            )
        }

        ButtonType.Outlined -> when (colorType) {
            ButtonColorType.Primary -> ButtonColors(
                Color.Transparent,
                colors.buttonColorPrimaryActive,
                colors.buttonColorPrimaryActive
            )

            ButtonColorType.Secondary -> ButtonColors(
                Color.Transparent,
                colors.textColorTertiary,
                colors.strokeColorModule
            )

            ButtonColorType.Danger -> ButtonColors(
                Color.Transparent,
                colors.buttonColorHangupActive,
                colors.buttonColorHangupActive
            )
        }

        ButtonType.NoBorder -> when (colorType) {
            ButtonColorType.Primary -> ButtonColors(
                Color.Transparent,
                colors.buttonColorPrimaryActive,
                Color.Transparent
            )

            ButtonColorType.Secondary -> ButtonColors(
                Color.Transparent,
                colors.textColorTertiary,
                Color.Transparent
            )

            ButtonColorType.Danger -> ButtonColors(
                Color.Transparent,
                colors.buttonColorHangupActive,
                Color.Transparent
            )
        }
    }
}

@Composable
private fun getDisabledColors(
    type: ButtonType,
    colorType: ButtonColorType,
    colors: ColorScheme
): ButtonColors {
    return when (type) {
        ButtonType.Filled -> when (colorType) {
            ButtonColorType.Primary -> ButtonColors(
                colors.buttonColorPrimaryDisabled,
                colors.textColorButtonDisabled,
                Color.Transparent
            )

            ButtonColorType.Secondary -> ButtonColors(
                colors.buttonColorSecondaryDisabled,
                colors.textColorDisable,
                Color.Transparent
            )

            ButtonColorType.Danger -> ButtonColors(
                colors.buttonColorHangupDisabled,
                colors.textColorButtonDisabled,
                Color.Transparent
            )
        }

        ButtonType.Outlined -> when (colorType) {
            ButtonColorType.Primary -> ButtonColors(
                Color.Transparent,
                colors.buttonColorPrimaryDisabled,
                colors.buttonColorPrimaryDisabled
            )

            ButtonColorType.Secondary -> ButtonColors(
                Color.Transparent,
                colors.textColorDisable,
                colors.strokeColorSecondary
            )

            ButtonColorType.Danger -> ButtonColors(
                Color.Transparent,
                colors.buttonColorHangupDisabled,
                colors.buttonColorHangupDisabled
            )
        }

        ButtonType.NoBorder -> when (colorType) {
            ButtonColorType.Primary -> ButtonColors(
                Color.Transparent,
                colors.buttonColorPrimaryDisabled,
                Color.Transparent
            )

            ButtonColorType.Secondary -> ButtonColors(
                Color.Transparent,
                colors.textColorDisable,
                Color.Transparent
            )

            ButtonColorType.Danger -> ButtonColors(
                Color.Transparent,
                colors.buttonColorHangupDisabled,
                Color.Transparent
            )
        }
    }
}