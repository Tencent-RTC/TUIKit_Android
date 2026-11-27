package io.trtc.tuikit.chat.uikit.components.widgets

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.View
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import io.trtc.tuikit.chat.uikit.compose.R
import io.trtc.tuikit.chat.uikit.components.theme.LocalTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


enum class ToastType {
    Text,
    Info,
    Help,
    Loading,
    Success,
    Warning,
    Error
}

object Toast {
    const val DURATION = 3000L
    private val coroutineScope = CoroutineScope(Dispatchers.Main)
    private var currentToastView: ComposeView? = null
    private var currentToast: android.widget.Toast? = null
    private var hideJob: Job? = null

    fun info(context: Context, message: String) {
        show(
            context = context,
            message = message,
            type = ToastType.Info,
        )
    }

    fun help(context: Context, message: String) {
        show(
            context = context,
            message = message,
            type = ToastType.Help,
        )
    }

    fun success(context: Context, message: String) {
        show(
            context = context,
            message = message,
            type = ToastType.Success,
        )
    }

    fun warning(context: Context, message: String) {
        show(
            context = context,
            message = message,
            type = ToastType.Warning,
        )
    }

    fun error(context: Context, message: String) {
        show(
            context = context,
            message = message,
            type = ToastType.Error,
        )
    }

    fun loading(context: Context, message: String) {
        show(
            context = context,
            message = message,
            type = ToastType.Loading,
        )
    }

    fun simple(context: Context, message: String) {
        show(
            context = context,
            message = message,
            type = ToastType.Info,
        )
    }

    fun info(view: View, message: String) {
        show(
            view = view,
            message = message,
            type = ToastType.Info,
        )
    }

    fun help(view: View, message: String) {
        show(
            view = view,
            message = message,
            type = ToastType.Help,
        )
    }

    fun success(view: View, message: String) {
        show(
            view = view,
            message = message,
            type = ToastType.Success,
        )
    }

    fun warning(view: View, message: String) {
        show(
            view = view,
            message = message,
            type = ToastType.Warning,
        )
    }

    fun error(view: View, message: String) {
        show(
            view = view,
            message = message,
            type = ToastType.Error,
        )
    }

    fun loading(view: View, message: String) {
        show(
            view = view,
            message = message,
            type = ToastType.Loading,
        )
    }

    fun simple(view: View, message: String) {
        show(
            view = view,
            message = message,
            type = ToastType.Info,
        )
    }

    private fun show(
        context: Context,
        message: String,
        type: ToastType,
        duration: Long = DURATION,
        onDismiss: (() -> Unit)? = null
    ) {
        coroutineScope.launch {
            showInternal(context, message, type, duration, onDismiss)
        }
    }

    private fun show(
        view: View,
        message: String,
        type: ToastType,
        duration: Long = DURATION,
        onDismiss: (() -> Unit)? = null
    ) {
        coroutineScope.launch {
            showInternal(view, message, type, duration, onDismiss)
        }
    }

    private fun showInternal(
        context: Context,
        message: String,
        type: ToastType,
        duration: Long,
        onDismiss: (() -> Unit)?
    ) {
        hideInternal()

        val activity = context.findActivity()
        val container = activity?.window?.decorView as? android.view.ViewGroup
        if (activity != null && !activity.isFinishing && !activity.isDestroyed && container != null) {
            showWithView(activity, container, message, type, duration, onDismiss)
        } else {
            showWithToast(context, message, duration, onDismiss)
        }
    }

    private fun showInternal(
        view: View,
        message: String,
        type: ToastType,
        duration: Long,
        onDismiss: (() -> Unit)?
    ) {
        hideInternal()

        val activity = view.context.findActivity()
        val container = if (view.isAttachedToWindow) {
            view.rootView as? android.view.ViewGroup
        } else {
            null
        }
        if (activity != null && !activity.isFinishing && !activity.isDestroyed && container != null) {
            showWithView(activity, container, message, type, duration, onDismiss)
        } else {
            showWithToast(view.context, message, duration, onDismiss)
        }
    }

    private fun showWithToast(
        context: Context,
        message: String,
        duration: Long,
        onDismiss: (() -> Unit)?
    ) {
        val toastDuration = if (duration > 2000) {
            android.widget.Toast.LENGTH_LONG
        } else {
            android.widget.Toast.LENGTH_SHORT
        }
        currentToast = android.widget.Toast.makeText(context, message, toastDuration).also {
            it.show()
        }

        if (duration > 0) {
            hideJob = coroutineScope.launch {
                delay(duration)
                hideInternal()
                onDismiss?.invoke()
            }
        }
    }

    private fun showWithView(
        activity: Activity,
        container: android.view.ViewGroup,
        message: String,
        type: ToastType,
        duration: Long,
        onDismiss: (() -> Unit)?
    ) {
        val composeView = ComposeView(activity).apply {
            setViewTreeLifecycleOwner(activity as LifecycleOwner?)
            setViewTreeSavedStateRegistryOwner(activity as SavedStateRegistryOwner?)

            setContent {
                var appeared by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) {
                    appeared = true
                }
                val alpha by animateFloatAsState(
                    targetValue = if (appeared) 1f else 0f,
                    animationSpec = tween(durationMillis = 150),
                    label = "toastAlpha"
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .alpha(alpha),
                    contentAlignment = Alignment.Center
                ) {
                    ToastContent(
                        message = message,
                        type = type,
                    )
                }
            }
        }

        try {
            container.addView(
                composeView,
                android.view.ViewGroup.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
            currentToastView = composeView
        } catch (e: Exception) {
            android.util.Log.e("Toast", "addView failed, fallback to system toast", e)
            showWithToast(activity, message, duration, onDismiss)
            return
        }

        if (duration > 0) {
            hideJob = coroutineScope.launch {
                delay(duration)
                hide()
                onDismiss?.invoke()
            }
        }
    }

    fun hide() {
        coroutineScope.launch {
            hideInternal()
        }
    }

    private fun hideInternal() {
        try {
            hideJob?.cancel()
            hideJob = null

            currentToastView?.let { view ->
                (view.parent as? android.view.ViewGroup)?.removeView(view)
                view.disposeComposition()
            }
            currentToastView = null

            currentToast?.cancel()
            currentToast = null
        } catch (_: Exception) {
        }
    }

    private fun Context.findActivity(): Activity? {
        var context = this
        while (context is ContextWrapper) {
            if (context is Activity) return context
            context = context.baseContext
        }
        return null
    }
}

@Composable
private fun ToastContent(
    message: String,
    type: ToastType,
) {

    val horizontalPadding = 16.dp
    val verticalPadding = 9.dp
    val fontSize = 14.sp
    val iconGap = 8.dp
    val colors = LocalTheme.current.colors
    Box(modifier = Modifier.padding(12.dp)) {

        Card(
            modifier = Modifier
                .widthIn(max = 340.dp),
            shape = RoundedCornerShape(6.dp),
            colors = CardDefaults.cardColors(containerColor = colors.bgColorOperate),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Box(
                modifier = Modifier
                    .padding(horizontal = horizontalPadding, vertical = verticalPadding),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    modifier = Modifier.padding(),
                    horizontalArrangement = Arrangement.spacedBy(iconGap)
                ) {
                    val lineHeightDp = with(LocalDensity.current) {
                        (fontSize.value * 1.57).sp.toDp()
                    }
                    val iconTopPadding = (lineHeightDp - 16.dp) / 2
                    if (type != ToastType.Text) {
                        ToastIcon(
                            modifier = Modifier
                                .align(Alignment.Top)
                                .padding(top = iconTopPadding),
                            type = type,
                            size = 16.dp
                        )
                    }
                    Text(
                        modifier = Modifier.align(Alignment.CenterVertically),
                        text = message,
                        fontSize = fontSize,
                        fontWeight = FontWeight.Medium,
                        color = colors.textColorPrimary,
                        lineHeight = (fontSize.value * 1.57).sp
                    )
                }
            }
        }
    }
}

@Composable
private fun ToastIcon(
    modifier: Modifier = Modifier,
    type: ToastType,
    size: Dp
) {
    val icon = when (type) {
        ToastType.Info -> painterResource(R.drawable.base_component_toast_info_icon)
        ToastType.Help -> painterResource(R.drawable.base_component_toast_help_icon)
        ToastType.Success -> painterResource(R.drawable.base_component_toast_success_icon)
        ToastType.Warning -> painterResource(R.drawable.base_component_toast_warning_icon)
        ToastType.Error -> painterResource(R.drawable.base_component_toast_error_icon)
        else -> null
    }
    when {
        icon != null -> {
            Icon(
                painter = icon,
                contentDescription = null,
                modifier = modifier.size(size),
                tint = Color.Unspecified
            )
        }

        type == ToastType.Loading -> {
            CircularProgressIndicator(
                modifier = modifier.size(size),
                strokeWidth = 2.dp,
                color = LocalTheme.current.colors.buttonColorPrimaryDefault
            )
        }

        else -> {}
    }
}

fun Context.hideToast() {
    Toast.hide()
}

fun Context.toastInfo(message: String) = Toast.info(this, message)
fun Context.toastHelp(message: String) = Toast.help(this, message)
fun Context.toastSuccess(message: String) = Toast.success(this, message)
fun Context.toastWarning(message: String) = Toast.warning(this, message)
fun Context.toastError(message: String) = Toast.error(this, message)
fun Context.toastLoading(message: String) = Toast.loading(this, message)
fun Context.toastSimple(message: String) = Toast.simple(this, message)

fun View.toastInfo(message: String) = Toast.info(this, message)
fun View.toastHelp(message: String) = Toast.help(this, message)
fun View.toastSuccess(message: String) = Toast.success(this, message)
fun View.toastWarning(message: String) = Toast.warning(this, message)
fun View.toastError(message: String) = Toast.error(this, message)
fun View.toastLoading(message: String) = Toast.loading(this, message)
fun View.toastSimple(message: String) = Toast.simple(this, message)
