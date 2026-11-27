package io.trtc.tuikit.chat.uikit.components.messagelist.ui.widgets

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import io.trtc.tuikit.chat.uikit.components.messagelist.ui.MessageItemDisplayPolicy
import io.trtc.tuikit.chat.uikit.components.theme.LocalTheme

@Composable
fun MessageCheckBox(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    checked: Boolean
) {
    val colors = LocalTheme.current.colors
    val strokeColor = colors.strokeColorPrimary
    val checkedColor = colors.buttonColorPrimaryDefault
    Canvas(
        modifier = modifier
            .size(MessageItemDisplayPolicy.MULTI_SELECT_CHECKBOX_SIZE_DP.dp)
            .alpha(if (enabled) 1f else 0.4f)
    ) {
        val width = size.width
        val radius = width / 2f
        val center = Offset(width / 2f, width / 2f)
        if (checked) {
            drawCircle(color = checkedColor, radius = radius, center = center)
            val checkPath = Path().apply {
                moveTo(width * 0.28f, width * 0.53f)
                lineTo(width * 0.44f, width * 0.68f)
                lineTo(width * 0.73f, width * 0.33f)
            }
            drawPath(
                path = checkPath,
                color = Color.White,
                style = Stroke(
                    width = width * 0.09f,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
        } else {
            val ringStroke = 1.dp.toPx()
            drawCircle(
                color = strokeColor,
                radius = radius - ringStroke / 2f,
                center = center,
                style = Stroke(width = ringStroke)
            )
        }
    }
}
