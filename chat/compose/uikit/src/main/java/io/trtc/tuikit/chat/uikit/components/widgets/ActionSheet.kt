package io.trtc.tuikit.chat.uikit.components.widgets

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.WindowManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import io.trtc.tuikit.chat.uikit.compose.R
import io.trtc.tuikit.chat.uikit.components.theme.LocalTheme

data class ActionItem(
    val text: String,
    val isDestructive: Boolean = false,
    val isEnabled: Boolean = true,
    val value: Any? = null,
)

private const val CORNER_RADIUS_DP = 14
private const val OPTION_VERTICAL_PADDING_DP = 16
private const val HORIZONTAL_MARGIN_DP = 16
private const val TABLET_SMALLEST_WIDTH_DP = 600
private const val GAP_DP = 6
private const val BOTTOM_PADDING_DP = 8
private const val DIVIDER_HEIGHT_DP = 1
private const val OPTION_TEXT_SIZE_SP = 17
private const val CANCEL_TEXT_SIZE_SP = 17
private const val MAX_HEIGHT_RATIO = 0.8f
private const val CANCEL_BUTTON_ESTIMATED_HEIGHT_DP = 70
private const val ENTER_DURATION_MS = 300
private const val EXIT_DURATION_MS = 200
private const val DIM_AMOUNT = 0.4f

private val EnterEasing = CubicBezierEasing(0.2f, 1f, 0.3f, 1f)
private val ExitEasing = CubicBezierEasing(0.4f, 0f, 1f, 1f)

@Composable
fun ActionSheet(
    isVisible: Boolean,
    options: List<ActionItem>,
    onDismiss: () -> Unit,
    onActionSelected: (ActionItem) -> Unit,
) {
    val colors = LocalTheme.current.colors
    val context = LocalContext.current
    val density = LocalDensity.current
    val metrics = context.resources.displayMetrics

    val isTablet = context.resources.configuration.smallestScreenWidthDp >= TABLET_SMALLEST_WIDTH_DP
    val contentWidth = with(density) {
        val widthPx = if (isTablet) {
            metrics.widthPixels / 2f
        } else {
            metrics.widthPixels - HORIZONTAL_MARGIN_DP.dp.toPx() * 2f
        }
        widthPx.toDp()
    }
    val maxOptionsHeight = with(density) {
        val screenHeightPx = metrics.heightPixels.toFloat()
        val cancelButtonHeightPx = CANCEL_BUTTON_ESTIMATED_HEIGHT_DP.dp.toPx()
        val bottomPaddingPx = BOTTOM_PADDING_DP.dp.toPx()
        (screenHeightPx * MAX_HEIGHT_RATIO - cancelButtonHeightPx - bottomPaddingPx).toDp()
    }

    var dialogShown by remember { mutableStateOf(false) }
    var sheetVisible by remember { mutableStateOf(false) }
    var isDismissing by remember { mutableStateOf(false) }
    var pendingAction by remember { mutableStateOf<ActionItem?>(null) }

    val currentOnDismiss by rememberUpdatedState(onDismiss)
    val currentOnActionSelected by rememberUpdatedState(onActionSelected)

    val requestDismiss: (ActionItem?) -> Unit = { action ->
        if (!isDismissing) {
            isDismissing = true
            pendingAction = action
            sheetVisible = false
        }
    }

    LaunchedEffect(isVisible, context) {
        if (isVisible) {
            val activity = context.findHostActivity()
            if (activity?.isFinishing == true || activity?.isDestroyed == true) {
                return@LaunchedEffect
            }
            dialogShown = true
            isDismissing = false
            pendingAction = null
            sheetVisible = true
        } else if (dialogShown && !isDismissing) {
            isDismissing = true
            sheetVisible = false
        }
    }

    if (!dialogShown) {
        return
    }

    Dialog(
        onDismissRequest = { requestDismiss(null) },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
        )
    ) {
        val view = LocalView.current
        val window = (view.parent as? DialogWindowProvider)?.window
        window?.setWindowAnimations(0)
        SideEffect {
            window?.setWindowAnimations(0)
            window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            window?.setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
            )
            window?.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            window?.setDimAmount(DIM_AMOUNT)
            window?.decorView?.setPadding(0, 0, 0, 0)
        }

        val offsetFraction = remember { Animatable(1f) }
        val latestPendingAction by rememberUpdatedState(pendingAction)

        LaunchedEffect(sheetVisible) {
            if (sheetVisible) {
                offsetFraction.snapTo(1f)
                offsetFraction.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(durationMillis = ENTER_DURATION_MS, easing = EnterEasing),
                )
            } else {
                offsetFraction.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = EXIT_DURATION_MS, easing = ExitEasing),
                )
                val action = latestPendingAction
                pendingAction = null
                dialogShown = false
                isDismissing = false
                if (action != null) {
                    currentOnActionSelected(action)
                }
                currentOnDismiss()
            }
        }

        val scrollState = rememberScrollState()
        val sheetShape = RoundedCornerShape(CORNER_RADIUS_DP.dp)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(indication = null, interactionSource = null) { requestDismiss(null) }
        ) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .width(contentWidth)
                    .padding(bottom = BOTTOM_PADDING_DP.dp)
                    .graphicsLayer {
                        val contentHeight = size.height
                        translationY = if (contentHeight > 0f) {
                            offsetFraction.value * contentHeight
                        } else {
                            10000f
                        }
                    }
                    .clickable(indication = null, interactionSource = null) {}
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = maxOptionsHeight)
                        .clip(sheetShape)
                        .background(colors.bgColorDialog)
                        .verticalScroll(scrollState)
                ) {
                    options.forEachIndexed { index, option ->
                        ActionSheetItem(
                            option = option,
                            onClick = {
                                if (option.isEnabled) {
                                    requestDismiss(option)
                                }
                            }
                        )
                        if (index < options.size - 1) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(DIVIDER_HEIGHT_DP.dp)
                                    .background(colors.strokeColorPrimary)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(GAP_DP.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(sheetShape)
                        .background(colors.bgColorDialog)
                        .clickable(indication = null, interactionSource = null) { requestDismiss(null) },
                    contentAlignment = Alignment.Center
                ) {
                    BasicText(
                        text = stringResource(R.string.base_component_cancel),
                        style = TextStyle(
                            fontSize = CANCEL_TEXT_SIZE_SP.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.buttonColorPrimaryDefault,
                            textAlign = TextAlign.Center,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = OPTION_VERTICAL_PADDING_DP.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ActionSheetItem(
    option: ActionItem,
    onClick: () -> Unit
) {
    val colors = LocalTheme.current.colors
    val textColor = when {
        option.isDestructive -> colors.textColorError
        !option.isEnabled -> colors.textColorDisable
        else -> colors.buttonColorPrimaryDefault
    }

    BasicText(
        text = option.text,
        style = TextStyle(
            fontSize = OPTION_TEXT_SIZE_SP.sp,
            fontWeight = FontWeight.W400,
            color = textColor,
            textAlign = TextAlign.Center,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                enabled = option.isEnabled,
                indication = null,
                interactionSource = null,
            ) { onClick() }
            .background(color = colors.bgColorDialog)
            .padding(vertical = OPTION_VERTICAL_PADDING_DP.dp)
    )
}

private tailrec fun Context.findHostActivity(): Activity? {
    return when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findHostActivity()
        else -> null
    }
}
