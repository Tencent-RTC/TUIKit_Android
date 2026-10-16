package com.trtc.uikit.livekit.common.theme

import androidx.annotation.ColorRes
import com.trtc.uikit.livekit.R

/** Product-level text roles shared by Live surfaces. */
internal object LiveTheme {
    enum class TextRole(val sizeSp: Float, val semibold: Boolean) {
        SHEET_TITLE(15f, true),
        DIALOG_TITLE(16f, true),
        PAGE_TITLE(21f, true),
        SECTION_TITLE(12f, true),
        ITEM_TITLE(13f, false),
        LIST_TITLE(15f, false),
        BODY(14f, false),
        PROMINENT(17f, true),
        CAPTION(12f, false),
        METADATA(10f, false),
        CONTROL(11f, false),
        ACTION(13f, true),
    }

    enum class TextTone(@ColorRes val colorRes: Int) {
        PRIMARY(R.color.livekit_text_primary),
        SECONDARY(R.color.livekit_text_secondary),
        TERTIARY(R.color.livekit_text_tertiary),
        DISABLED(R.color.livekit_text_disabled),
    }
}
