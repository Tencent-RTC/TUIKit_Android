package io.trtc.tuikit.chat.uikit.components.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.trtc.tuikit.chat.uikit.components.theme.LocalTheme
import kotlin.math.max


@Composable
fun PageHeader(
    text: String,
    leftContent: @Composable () -> Unit = {},
    editContent: @Composable () -> Unit = {}
) {
    val colors = LocalTheme.current.colors
    val density = LocalDensity.current
    var leftActionWidthPx by remember { mutableIntStateOf(0) }
    var rightActionWidthPx by remember { mutableIntStateOf(0) }
    val titleHorizontalInset = with(density) { max(leftActionWidthPx, rightActionWidthPx).toDp() }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(color = colors.bgColorOperate)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            text = text,
            fontSize = 17.sp,
            color = colors.textColorPrimary,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = titleHorizontalInset)
                .align(Alignment.Center)
        )

        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .onGloballyPositioned { leftActionWidthPx = it.size.width }
        ) {
            leftContent()
        }

        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .onGloballyPositioned { rightActionWidthPx = it.size.width }
        ) {
            editContent()
        }
    }
}
