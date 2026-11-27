package io.trtc.tuikit.chat.uikit.components.messagelist.ui.widgets

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.trtc.tuikit.chat.uikit.compose.R
import io.trtc.tuikit.chat.uikit.components.theme.LocalTheme
import io.trtc.tuikit.chat.uikit.components.emojipicker.emojiSizeForTextSize
import io.trtc.tuikit.chat.uikit.components.emojipicker.rememberEmojiText

@Composable
fun TranslationBubble(
    modifier: Modifier = Modifier,
    isSelf: Boolean,
    isLoading: Boolean,
    translatedText: String?,
    showMenu: Boolean = false,
    onLongPress: () -> Unit = {},
    onDismissMenu: () -> Unit = {},
    onHide: () -> Unit = {},
    onForward: () -> Unit = {},
    onCopy: () -> Unit = {}
) {
    val colors = LocalTheme.current.colors
    val textColor = if (isSelf) colors.textColorAntiPrimary else colors.textColorPrimary
    val secondaryTextColor =
        if (isSelf) colors.textColorAntiPrimary.copy(alpha = 0.6f) else colors.textColorSecondary

    AuxiliaryTextBubble(
        modifier = modifier,
        isSelf = isSelf,
        isLoading = isLoading,
        showMenu = showMenu,
        topSpacing = 6.dp,
        contentPadding = PaddingValues(horizontal = 9.dp, vertical = 6.dp),
        onLongPress = onLongPress,
        onDismissMenu = onDismissMenu,
        onHide = onHide,
        onForward = onForward,
        onCopy = onCopy
    ) {
        Column {
            val (annotatedString, inlineContent) = rememberEmojiText(translatedText ?: "", emojiSize = emojiSizeForTextSize(14.sp))
            Text(
                modifier = Modifier.padding(top = 3.dp),
                text = annotatedString,
                inlineContent = inlineContent,
                fontSize = 14.sp,
                lineHeight = 18.2.sp,
                color = textColor
            )
            Text(
                modifier = Modifier.padding(top = 4.dp),
                text = stringResource(R.string.message_list_translate_default_tips),
                fontSize = 12.sp,
                color = secondaryTextColor
            )
        }
    }
}
