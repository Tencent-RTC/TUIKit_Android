package io.trtc.tuikit.chat.uikit.components.theme

import androidx.compose.ui.graphics.Color
import io.trtc.tuikit.atomicx.theme.tokens.ColorTokens

data class ColorScheme(
    // text & icon
    val textColorPrimary: Color,
    val textColorSecondary: Color,
    val textColorTertiary: Color,
    val textColorDisable: Color,
    val textColorButton: Color,
    val textColorButtonDisabled: Color,
    val textColorLink: Color,
    val textColorLinkHover: Color,
    val textColorLinkActive: Color,
    val textColorLinkDisabled: Color,
    val textColorAntiPrimary: Color,
    val textColorAntiSecondary: Color,
    val textColorWarning: Color,
    val textColorSuccess: Color,
    val textColorError: Color,
    // background
    val bgColorTopBar: Color,
    val bgColorOperate: Color,
    val bgColorDialog: Color,
    val bgColorDialogModule: Color,
    val bgColorEntryCard: Color,
    val bgColorFunction: Color,
    val bgColorBottomBar: Color,
    val bgColorInput: Color,
    val bgColorBubbleReciprocal: Color,
    val bgColorBubbleOwn: Color,
    val bgColorDefault: Color,
    val bgColorTagMask: Color,
    val bgColorElementMask: Color,
    val bgColorMask: Color,
    val bgColorMaskDisappeared: Color,
    val bgColorMaskBegin: Color,
    val bgColorAvatar: Color,
    // border
    val strokeColorPrimary: Color,
    val strokeColorSecondary: Color,
    val strokeColorModule: Color,
    // shadow
    val shadowColor: Color,
    // status
    val listColorDefault: Color,
    val listColorHover: Color,
    val listColorFocused: Color,
    // button
    val buttonColorPrimaryDefault: Color,
    val buttonColorPrimaryHover: Color,
    val buttonColorPrimaryActive: Color,
    val buttonColorPrimaryDisabled: Color,
    val buttonColorSecondaryDefault: Color,
    val buttonColorSecondaryHover: Color,
    val buttonColorSecondaryActive: Color,
    val buttonColorSecondaryDisabled: Color,
    val buttonColorAccept: Color,
    val buttonColorHangupDefault: Color,
    val buttonColorHangupDisabled: Color,
    val buttonColorHangupHover: Color,
    val buttonColorHangupActive: Color,
    val buttonColorOn: Color,
    val buttonColorOff: Color,
    // dropdown
    val dropdownColorDefault: Color,
    val dropdownColorHover: Color,
    val dropdownColorActive: Color,
    // scrollbar
    val scrollbarColorDefault: Color,
    val scrollbarColorHover: Color,
    // floating
    val floatingColorDefault: Color,
    val floatingColorOperate: Color,
    // checkbox
    val checkboxColorSelected: Color,
    // toast
    val toastColorWarning: Color,
    val toastColorSuccess: Color,
    val toastColorError: Color,
    val toastColorDefault: Color,
    // tag
    val tagColorLevel1: Color,
    val tagColorLevel2: Color,
    val tagColorLevel3: Color,
    val tagColorLevel4: Color,
    // switch
    val switchColorOff: Color,
    val switchColorOn: Color,
    val switchColorButton: Color,
    // slider
    val sliderColorFilled: Color,
    val sliderColorEmpty: Color,
    val sliderColorButton: Color,
    // tab
    val tabColorSelected: Color,
    val tabColorUnselected: Color,
    val tabColorOption: Color,
)

/**
 * Bridges the View UIKit token set (`@ColorInt Int`) onto the Compose one (`Color`).
 * The two declare the same 79 semantic slots under the same names, so this is a
 * straight one-to-one projection with no fallbacks.
 */
internal fun ColorTokens.toColorScheme(): ColorScheme = ColorScheme(
    textColorPrimary = Color(textColorPrimary),
    textColorSecondary = Color(textColorSecondary),
    textColorTertiary = Color(textColorTertiary),
    textColorDisable = Color(textColorDisable),
    textColorButton = Color(textColorButton),
    textColorButtonDisabled = Color(textColorButtonDisabled),
    textColorLink = Color(textColorLink),
    textColorLinkHover = Color(textColorLinkHover),
    textColorLinkActive = Color(textColorLinkActive),
    textColorLinkDisabled = Color(textColorLinkDisabled),
    textColorAntiPrimary = Color(textColorAntiPrimary),
    textColorAntiSecondary = Color(textColorAntiSecondary),
    textColorWarning = Color(textColorWarning),
    textColorSuccess = Color(textColorSuccess),
    textColorError = Color(textColorError),

    bgColorTopBar = Color(bgColorTopBar),
    bgColorOperate = Color(bgColorOperate),
    bgColorDialog = Color(bgColorDialog),
    bgColorDialogModule = Color(bgColorDialogModule),
    bgColorEntryCard = Color(bgColorEntryCard),
    bgColorFunction = Color(bgColorFunction),
    bgColorBottomBar = Color(bgColorBottomBar),
    bgColorInput = Color(bgColorInput),
    bgColorBubbleReciprocal = Color(bgColorBubbleReciprocal),
    bgColorBubbleOwn = Color(bgColorBubbleOwn),
    bgColorDefault = Color(bgColorDefault),
    bgColorTagMask = Color(bgColorTagMask),
    bgColorElementMask = Color(bgColorElementMask),
    bgColorMask = Color(bgColorMask),
    bgColorMaskDisappeared = Color(bgColorMaskDisappeared),
    bgColorMaskBegin = Color(bgColorMaskBegin),
    bgColorAvatar = Color(bgColorAvatar),

    strokeColorPrimary = Color(strokeColorPrimary),
    strokeColorSecondary = Color(strokeColorSecondary),
    strokeColorModule = Color(strokeColorModule),

    shadowColor = Color(shadowColor),

    listColorDefault = Color(listColorDefault),
    listColorHover = Color(listColorHover),
    listColorFocused = Color(listColorFocused),

    buttonColorPrimaryDefault = Color(buttonColorPrimaryDefault),
    buttonColorPrimaryHover = Color(buttonColorPrimaryHover),
    buttonColorPrimaryActive = Color(buttonColorPrimaryActive),
    buttonColorPrimaryDisabled = Color(buttonColorPrimaryDisabled),
    buttonColorSecondaryDefault = Color(buttonColorSecondaryDefault),
    buttonColorSecondaryHover = Color(buttonColorSecondaryHover),
    buttonColorSecondaryActive = Color(buttonColorSecondaryActive),
    buttonColorSecondaryDisabled = Color(buttonColorSecondaryDisabled),
    buttonColorAccept = Color(buttonColorAccept),
    buttonColorHangupDefault = Color(buttonColorHangupDefault),
    buttonColorHangupDisabled = Color(buttonColorHangupDisabled),
    buttonColorHangupHover = Color(buttonColorHangupHover),
    buttonColorHangupActive = Color(buttonColorHangupActive),
    buttonColorOn = Color(buttonColorOn),
    buttonColorOff = Color(buttonColorOff),

    dropdownColorDefault = Color(dropdownColorDefault),
    dropdownColorHover = Color(dropdownColorHover),
    dropdownColorActive = Color(dropdownColorActive),

    scrollbarColorDefault = Color(scrollbarColorDefault),
    scrollbarColorHover = Color(scrollbarColorHover),

    floatingColorDefault = Color(floatingColorDefault),
    floatingColorOperate = Color(floatingColorOperate),

    checkboxColorSelected = Color(checkboxColorSelected),

    toastColorWarning = Color(toastColorWarning),
    toastColorSuccess = Color(toastColorSuccess),
    toastColorError = Color(toastColorError),
    toastColorDefault = Color(toastColorDefault),

    tagColorLevel1 = Color(tagColorLevel1),
    tagColorLevel2 = Color(tagColorLevel2),
    tagColorLevel3 = Color(tagColorLevel3),
    tagColorLevel4 = Color(tagColorLevel4),

    switchColorOff = Color(switchColorOff),
    switchColorOn = Color(switchColorOn),
    switchColorButton = Color(switchColorButton),

    sliderColorFilled = Color(sliderColorFilled),
    sliderColorEmpty = Color(sliderColorEmpty),
    sliderColorButton = Color(sliderColorButton),

    tabColorSelected = Color(tabColorSelected),
    tabColorUnselected = Color(tabColorUnselected),
    tabColorOption = Color(tabColorOption),
)
