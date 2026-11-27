package io.trtc.tuikit.chat.uikit.components.messageinput.ui

internal data class VoiceTranscriptionOverlayLayout(
    val bubbleTop: Int,
    val chipRowTop: Int,
    val cancelButtonTop: Int,
    val sendAudioButtonTop: Int,
    val sendTextButtonTop: Int,
    val labelTop: Int,
    val waveformTop: Int,
) {
    // Shifts every position into the host coordinate space. The layout is computed inside the
    // display frame (between the system bars), matching the View overlay's PopupWindow, while
    // the Compose host spans the whole window.
    fun offsetBy(dy: Int): VoiceTranscriptionOverlayLayout {
        if (dy == 0) return this
        return VoiceTranscriptionOverlayLayout(
            bubbleTop = bubbleTop + dy,
            chipRowTop = chipRowTop + dy,
            cancelButtonTop = cancelButtonTop + dy,
            sendAudioButtonTop = sendAudioButtonTop + dy,
            sendTextButtonTop = sendTextButtonTop + dy,
            labelTop = labelTop + dy,
            waveformTop = waveformTop + dy,
        )
    }
}

internal object VoiceTranscriptionOverlayLayoutPolicy {
    const val CHIP_ROW_HEIGHT_DP = 32
    const val DESIGN_BUBBLE_TOP_DP = 549
    const val WAVEFORM_HEIGHT_DP = 40
    const val WAVEFORM_BOTTOM_DP = 41

    fun calculate(
        rootHeightPx: Int,
        bubbleHeightPx: Int,
        keyboardHeightPx: Int,
        showChipRow: Boolean,
        density: Float,
    ): VoiceTranscriptionOverlayLayout {
        val safeRootHeight = rootHeightPx.coerceAtLeast(1)
        val iconButtonSize = dp(48, density)
        val sendTextButtonSize = dp(80, density)
        val labelHeight = dp(20, density)
        val waveformHeight = dp(WAVEFORM_HEIGHT_DP, density)
        val waveformBottom = dp(WAVEFORM_BOTTOM_DP, density)
        val labelToWaveformGap = dp(26, density)
        val iconToLabelGap = dp(4, density)
        val sendTextToWaveformGap = dp(24, density)
        val bubbleToActionGap = dp(12, density)
        val bubbleToKeyboardGap = dp(24, density)
        val bubbleToChipGap = if (showChipRow) dp(8, density) else 0
        val chipBlock = if (showChipRow) dp(CHIP_ROW_HEIGHT_DP, density) + bubbleToChipGap else 0

        val waveformTop = (safeRootHeight - waveformBottom - waveformHeight).coerceAtLeast(0)
        val labelTop = (waveformTop - labelToWaveformGap - labelHeight).coerceAtLeast(0)
        val iconButtonTop = (labelTop - iconToLabelGap - iconButtonSize).coerceAtLeast(0)
        val sendTextButtonTop = (waveformTop - sendTextToWaveformGap - sendTextButtonSize).coerceAtLeast(0)
        val firstActionTop = minOf(iconButtonTop, sendTextButtonTop)

        val designBubbleTop = dp(DESIGN_BUBBLE_TOP_DP, density)
        val maxBubbleTopBeforeActions =
            (firstActionTop - bubbleToActionGap - chipBlock - bubbleHeightPx).coerceAtLeast(0)
        val maxBubbleTopBeforeKeyboard = if (keyboardHeightPx > 0) {
            (safeRootHeight - keyboardHeightPx - bubbleToKeyboardGap - chipBlock - bubbleHeightPx)
                .coerceAtLeast(0)
        } else {
            Int.MAX_VALUE
        }

        val bubbleTop = minOf(designBubbleTop, maxBubbleTopBeforeActions, maxBubbleTopBeforeKeyboard)
        return VoiceTranscriptionOverlayLayout(
            bubbleTop = bubbleTop,
            chipRowTop = bubbleTop + bubbleHeightPx + bubbleToChipGap,
            cancelButtonTop = iconButtonTop,
            sendAudioButtonTop = iconButtonTop,
            sendTextButtonTop = sendTextButtonTop,
            labelTop = labelTop,
            waveformTop = waveformTop,
        )
    }

    private fun dp(value: Int, density: Float): Int {
        return (value * density).toInt()
    }
}
