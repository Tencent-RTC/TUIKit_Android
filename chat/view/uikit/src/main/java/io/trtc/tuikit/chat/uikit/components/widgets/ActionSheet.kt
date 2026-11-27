package io.trtc.tuikit.chat.uikit.components.widgets
import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.Window
import android.view.WindowManager
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import io.trtc.tuikit.chat.uikit.R
import io.trtc.tuikit.atomicx.common.util.ScreenUtil.dp2px
import io.trtc.tuikit.atomicx.theme.ThemeStore
import io.trtc.tuikit.atomicx.theme.tokens.ColorTokens
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

data class ActionItem(
    val text: String,
    val isDestructive: Boolean = false,
    val isEnabled: Boolean = true,
    val value: Any? = null
)

class ActionSheet private constructor(
    context: Context,
    private val options: List<ActionItem>,
    private val onActionSelected: (ActionItem) -> Unit
) {

    companion object {
        private const val CORNER_RADIUS_DP = 14f
        private const val OPTION_VERTICAL_PADDING_DP = 16f
        private const val HORIZONTAL_MARGIN_DP = 16f
        private const val TABLET_SMALLEST_WIDTH_DP = 600
        private const val GAP_DP = 6f
        private const val BOTTOM_PADDING_DP = 8f
        private const val DIVIDER_HEIGHT_DP = 1f
        private const val OPTION_TEXT_SIZE_SP = 17f
        private const val CANCEL_TEXT_SIZE_SP = 17f
        private const val MAX_HEIGHT_RATIO = 0.8f
        private const val ENTER_DURATION_MS = 300L
        private const val EXIT_DURATION_MS = 200L

        fun show(
            context: Context,
            options: List<ActionItem>,
            onActionSelected: (ActionItem) -> Unit
        ) {
            ActionSheet(context, options, onActionSelected).showInternal()
        }
    }

    private val dialog: Dialog = Dialog(context)
    private val colors: ColorTokens
        get() = ThemeStore.shared(dialog.context).themeState.value.currentTheme.tokens.color
    private var viewScope: CoroutineScope? = null
    private var contentColumnRef: View? = null

    private fun showInternal() {
        val activity = dialog.context as? Activity
        if (activity?.isFinishing == true || activity?.isDestroyed == true) {
            return
        }

        val context = dialog.context
        val dm = context.resources.displayMetrics

        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
            setGravity(Gravity.BOTTOM)
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setDimAmount(0.4f)
            decorView.setPadding(0, 0, 0, 0)
            setWindowAnimations(0)
        }

        val horizontalMarginPx = dp2px(HORIZONTAL_MARGIN_DP, dm).toInt()
        val gapPx = dp2px(GAP_DP, dm).toInt()
        val bottomPaddingPx = dp2px(BOTTOM_PADDING_DP, dm).toInt()
        val cornerRadiusPx = dp2px(CORNER_RADIUS_DP, dm)
        val optionPaddingPx = dp2px(OPTION_VERTICAL_PADDING_DP, dm).toInt()
        val dividerHeightPx = dp2px(DIVIDER_HEIGHT_DP, dm).toInt()

        val screenHeight = ScreenUtil.getScreenHeight(context)
        val cancelButtonEstimatedHeight = dp2px(70f, dm).toInt()
        val maxOptionsHeight = (screenHeight * MAX_HEIGHT_RATIO - cancelButtonEstimatedHeight - bottomPaddingPx).toInt()

        val screenWidth = dm.widthPixels
        val isTablet = context.resources.configuration.smallestScreenWidthDp >= TABLET_SMALLEST_WIDTH_DP
        val contentWidth = if (isTablet) {
            screenWidth / 2
        } else {
            screenWidth - horizontalMarginPx * 2
        }

        val contentColumn = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                contentWidth,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            ).apply {
                setMargins(0, 0, 0, bottomPaddingPx)
            }
            isClickable = true
        }

        val rootContainer = FrameLayout(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val scrimView = View(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            isClickable = true
            isFocusable = true
            setOnClickListener {
                dismissWithAnimation(rootContainer)
            }
        }
        rootContainer.addView(scrimView)

        val optionsWrapper = FrameLayout(context).apply {
            background = createRoundedBackground(colors.bgColorDialog, cornerRadiusPx)
            clipToOutline = true
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, cornerRadiusPx)
                }
            }
        }

        val optionsScrollView = ScrollView(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
            isVerticalScrollBarEnabled = false
        }

        val optionsColumn = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        val optionViews = mutableListOf<TextView>()
        val dividerViews = mutableListOf<View>()

        options.forEachIndexed { index, option ->
            val itemView = createOptionItemView(context, option, rootContainer)
            optionsColumn.addView(itemView)
            optionViews.add(itemView)

            if (index < options.size - 1) {
                val divider = View(context).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dividerHeightPx
                    )
                    setBackgroundColor(colors.strokeColorPrimary)
                }
                optionsColumn.addView(divider)
                dividerViews.add(divider)
            }
        }

        optionsScrollView.addView(optionsColumn)
        optionsWrapper.addView(optionsScrollView)

        optionsColumn.measure(
            View.MeasureSpec.makeMeasureSpec(contentWidth, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val optionsNaturalHeight = optionsColumn.measuredHeight

        val wrapperLp = if (optionsNaturalHeight > maxOptionsHeight) {
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, maxOptionsHeight)
        } else {
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        optionsWrapper.layoutParams = wrapperLp

        contentColumn.addView(optionsWrapper)

        val gap = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                gapPx
            )
        }
        contentColumn.addView(gap)

        val cancelContainer = FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            background = createRoundedBackground(colors.bgColorDialog, cornerRadiusPx)
        }

        val cancelText = TextView(context).apply {
            text = context.getString(R.string.uikit_cancel)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, CANCEL_TEXT_SIZE_SP)
            setTextColor(colors.buttonColorPrimaryDefault)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, optionPaddingPx, 0, optionPaddingPx)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
            setOnClickListener { dismissWithAnimation(rootContainer) }
        }

        cancelContainer.addView(cancelText)
        contentColumn.addView(cancelContainer)

        rootContainer.addView(contentColumn)
        contentColumnRef = contentColumn

        observeTheme(
            optionsWrapper = optionsWrapper,
            cancelContainer = cancelContainer,
            cancelText = cancelText,
            optionViews = optionViews,
            dividerViews = dividerViews
        )

        contentColumn.translationY = dm.heightPixels.toFloat()

        dialog.setContentView(rootContainer)
        dialog.setCanceledOnTouchOutside(false)
        dialog.setCancelable(false)
        dialog.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                dismissWithAnimation(rootContainer)
                true
            } else {
                false
            }
        }
        dialog.show()

        contentColumn.post {
            contentColumn.translationY = contentColumn.height.toFloat()
            contentColumn.animate()
                .translationY(0f)
                .setDuration(ENTER_DURATION_MS)
                .setInterpolator(PathInterpolator(0.2f, 1f, 0.3f, 1f))
                .start()
        }
    }

    private var isDismissing = false

    private fun dismissWithAnimation(rootContainer: View, onDismissed: (() -> Unit)? = null) {
        if (isDismissing) {
            return
        }
        isDismissing = true
        val finishDismiss = {
            if (dialog.isShowing) {
                dialog.dismiss()
            }
            viewScope?.cancel()
            viewScope = null
            contentColumnRef = null
            onDismissed?.invoke()
        }
        val contentColumn = contentColumnRef ?: run {
            finishDismiss()
            return
        }
        val totalHeight = contentColumn.height.toFloat()
        contentColumn.animate()
            .translationY(totalHeight)
            .setDuration(EXIT_DURATION_MS)
            .setInterpolator(PathInterpolator(0.4f, 0f, 1f, 1f))
            .withEndAction { finishDismiss() }
            .start()
    }

    private fun createOptionItemView(
        context: Context,
        option: ActionItem,
        rootContainer: View
    ): TextView {
        val dm = context.resources.displayMetrics
        val paddingPx = dp2px(OPTION_VERTICAL_PADDING_DP, dm).toInt()

        val textColor = when {
            option.isDestructive -> colors.textColorError
            !option.isEnabled -> colors.textColorDisable
            else -> colors.buttonColorPrimaryDefault
        }

        return TextView(context).apply {
            text = option.text
            setTextSize(TypedValue.COMPLEX_UNIT_SP, OPTION_TEXT_SIZE_SP)
            setTextColor(textColor)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            gravity = Gravity.CENTER
            setPadding(0, paddingPx, 0, paddingPx)
            setBackgroundColor(colors.bgColorDialog)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            isEnabled = option.isEnabled
            isClickable = option.isEnabled
            if (option.isEnabled) {
                setOnClickListener {
                    dismissWithAnimation(rootContainer) {
                        onActionSelected(option)
                    }
                }
            }
        }
    }

    private fun createRoundedBackground(color: Int, cornerRadius: Float): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            this.cornerRadius = cornerRadius
        }
    }

    private fun observeTheme(
        optionsWrapper: FrameLayout,
        cancelContainer: FrameLayout,
        cancelText: TextView,
        optionViews: List<TextView>,
        dividerViews: List<View>
    ) {
        viewScope?.cancel()
        viewScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
        viewScope?.launch {
            ThemeStore.shared(dialog.context).themeState.collectLatest {
                applyTheme(
                    optionsWrapper = optionsWrapper,
                    cancelContainer = cancelContainer,
                    cancelText = cancelText,
                    optionViews = optionViews,
                    dividerViews = dividerViews,
                    colors = it.currentTheme.tokens.color
                )
            }
        }
    }

    private fun applyTheme(
        optionsWrapper: FrameLayout,
        cancelContainer: FrameLayout,
        cancelText: TextView,
        optionViews: List<TextView>,
        dividerViews: List<View>,
        colors: ColorTokens
    ) {
        (optionsWrapper.background as? GradientDrawable)?.setColor(colors.bgColorDialog)
        (cancelContainer.background as? GradientDrawable)?.setColor(colors.bgColorDialog)
        cancelText.setTextColor(colors.buttonColorPrimaryDefault)
        optionViews.forEachIndexed { index, textView ->
            val option = options[index]
            val textColor = when {
                option.isDestructive -> colors.textColorError
                !option.isEnabled -> colors.textColorDisable
                else -> colors.buttonColorPrimaryDefault
            }
            textView.setTextColor(textColor)
            textView.setBackgroundColor(colors.bgColorDialog)
        }
        dividerViews.forEach {
            it.setBackgroundColor(colors.strokeColorPrimary)
        }
    }

    private object ScreenUtil {
        fun getScreenHeight(context: Context): Int {
            val dm = context.resources.displayMetrics
            return dm.heightPixels
        }
    }
}
