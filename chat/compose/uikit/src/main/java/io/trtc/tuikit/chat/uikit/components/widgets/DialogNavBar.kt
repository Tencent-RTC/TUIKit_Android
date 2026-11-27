package io.trtc.tuikit.chat.uikit.components.widgets

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.trtc.tuikit.chat.uikit.compose.R
import io.trtc.tuikit.chat.uikit.components.theme.LocalTheme
import kotlin.math.max

enum class DialogNavBarMode {
    BackTitle,
    CancelTitleConfirm
}

@Composable
fun DialogNavBar(
    mode: DialogNavBarMode,
    title: String,
    onLeadingClick: () -> Unit,
    modifier: Modifier = Modifier,
    onConfirmClick: (() -> Unit)? = null,
    showConfirm: Boolean = false,
    confirmEnabled: Boolean = true,
    confirmText: String? = null,
    cancelText: String? = null,
    horizontalPadding: Dp = when (mode) {
        DialogNavBarMode.BackTitle -> 16.dp
        DialogNavBarMode.CancelTitleConfirm -> 10.dp
    },
    height: Dp = 56.dp
) {
    val colors = LocalTheme.current.colors
    val density = LocalDensity.current
    val resolvedCancelText = cancelText ?: stringResource(R.string.base_component_cancel)
    val resolvedConfirmText = confirmText ?: stringResource(R.string.base_component_confirm)
    val showConfirmButton = when (mode) {
        DialogNavBarMode.BackTitle -> showConfirm
        DialogNavBarMode.CancelTitleConfirm -> showConfirm || onConfirmClick != null
    }

    var leadingWidthPx by remember { mutableIntStateOf(0) }
    var trailingWidthPx by remember { mutableIntStateOf(0) }
    val titleHorizontalInset = with(density) { max(leadingWidthPx, trailingWidthPx).toDp() }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .background(colors.bgColorOperate)
            .padding(horizontal = horizontalPadding)
    ) {
        BasicText(
            text = title,
            style = TextStyle(
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textColorPrimary,
                textAlign = TextAlign.Center
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = titleHorizontalInset)
                .align(Alignment.Center)
        )

        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxHeight()
                .clickable(
                    indication = null,
                    interactionSource = null,
                    onClick = onLeadingClick
                )
                .onGloballyPositioned { leadingWidthPx = it.size.width },
            contentAlignment = Alignment.CenterStart
        ) {
            when (mode) {
                DialogNavBarMode.BackTitle -> {
                    Image(
                        painter = painterResource(R.drawable.uikit_ic_back),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        colorFilter = ColorFilter.tint(colors.textColorSecondary)
                    )
                }

                DialogNavBarMode.CancelTitleConfirm -> {
                    BasicText(
                        text = resolvedCancelText,
                        style = TextStyle(
                            fontSize = 16.sp,
                            color = colors.textColorLink
                        )
                    )
                }
            }
        }

        if (showConfirmButton) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .clickable(
                        enabled = confirmEnabled,
                        indication = null,
                        interactionSource = null,
                        onClick = { onConfirmClick?.invoke() }
                    )
                    .onGloballyPositioned { trailingWidthPx = it.size.width },
                contentAlignment = Alignment.CenterEnd
            ) {
                BasicText(
                    text = resolvedConfirmText,
                    style = TextStyle(
                        fontSize = 16.sp,
                        color = if (confirmEnabled) colors.textColorLink else colors.textColorDisable
                    )
                )
            }
        }
    }
}
