package io.trtc.tuikit.chat.uikit.components.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.trtc.tuikit.chat.uikit.components.theme.LocalTheme

sealed class BadgeType {
    data object Text : BadgeType()
    data object Dot : BadgeType()
}

@Composable
fun Badge(modifier: Modifier = Modifier, text: String? = null, type: BadgeType = BadgeType.Text) {
    val colors = LocalTheme.current.colors
    if (text.isNullOrEmpty() || type == BadgeType.Dot) {
        Box(
            modifier = modifier
                .size(8.dp)
                .background(color = colors.textColorError, shape = CircleShape)
        )
    } else {
        BasicText(
            modifier = modifier
                .height(16.dp)
                .background(color = colors.textColorError, shape = RoundedCornerShape(8.dp))
                .padding(horizontal = 5.dp)
                .clip(CircleShape)
                .wrapContentSize(),
            text = text,
            style = TextStyle(
                color = colors.textColorButton,
                textAlign = TextAlign.Center,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            ),
            maxLines = 1
        )
    }
}
