package io.trtc.tuikit.chat.uikit.components.widgets

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import io.trtc.tuikit.atomicx.common.util.ScreenUtil.dp2px
import io.trtc.tuikit.atomicx.theme.tokens.ColorTokens
import io.trtc.tuikit.chat.uikit.R
import io.trtc.tuikit.chat.uikit.components.common.expandTouchTarget

class DialogNavBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    enum class Mode {
        BackTitle,
        CancelTitleConfirm
    }

    data class Config(
        val mode: Mode,
        val title: CharSequence = "",
        val colors: ColorTokens,
        val onLeadingClick: () -> Unit,
        val onConfirmClick: (() -> Unit)? = null,
        val showConfirm: Boolean = false,
        val confirmText: CharSequence? = null,
        val cancelText: CharSequence? = null,
        val leadingContentDescription: CharSequence? = null,
        val horizontalPaddingDp: Float? = null,
        val heightDp: Float = DEFAULT_HEIGHT_DP,
        val layoutParams: ViewGroup.LayoutParams? = null
    )

    val titleView: TextView
    val confirmView: TextView
    private val backIconView: ImageView
    private val cancelView: TextView
    private val backRow: LinearLayout
    private var confirmEnabled: Boolean = true

    init {
        layoutDirection = View.LAYOUT_DIRECTION_LOCALE

        backRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_LOCALE
            layoutParams = LayoutParams(
                LayoutParams.WRAP_CONTENT,
                LayoutParams.MATCH_PARENT,
                Gravity.START or Gravity.CENTER_VERTICAL
            )
        }

        val dm = resources.displayMetrics
        val iconSize = dp2px(BACK_ICON_SIZE_DP, dm).toInt()
        backIconView = ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams(iconSize, iconSize)
            setImageResource(R.drawable.uikit_ic_back)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
        }
        backRow.addView(backIconView)

        cancelView = TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, TITLE_TEXT_SIZE_SP)
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LayoutParams(
                LayoutParams.WRAP_CONTENT,
                LayoutParams.MATCH_PARENT,
                Gravity.START or Gravity.CENTER_VERTICAL
            )
            isClickable = true
            isFocusable = true
            visibility = View.GONE
        }

        titleView = TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, TITLE_TEXT_SIZE_SP)
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            layoutParams = LayoutParams(
                LayoutParams.WRAP_CONTENT,
                LayoutParams.MATCH_PARENT,
                Gravity.CENTER
            )
        }

        confirmView = TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, TITLE_TEXT_SIZE_SP)
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LayoutParams(
                LayoutParams.WRAP_CONTENT,
                LayoutParams.MATCH_PARENT,
                Gravity.END or Gravity.CENTER_VERTICAL
            )
            isClickable = true
            isFocusable = true
            visibility = View.GONE
        }

        addView(backRow)
        addView(cancelView)
        addView(titleView)
        addView(confirmView)
    }

    fun bind(config: Config): DialogNavBar {
        val dm = resources.displayMetrics
        val height = dp2px(config.heightDp, dm).toInt()
        val hPadDp = config.horizontalPaddingDp ?: when (config.mode) {
            Mode.BackTitle -> BACK_TITLE_HORIZONTAL_PADDING_DP
            Mode.CancelTitleConfirm -> CANCEL_TITLE_HORIZONTAL_PADDING_DP
        }
        val hPad = dp2px(hPadDp, dm).toInt()

        val lp = config.layoutParams ?: LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            height
        )
        lp.height = height
        layoutParams = lp
        setPadding(hPad, 0, hPad, 0)
        titleView.text = config.title

        when (config.mode) {
            Mode.BackTitle -> {
                backRow.visibility = View.VISIBLE
                cancelView.visibility = View.GONE
                backRow.contentDescription = config.leadingContentDescription
                    ?: context.getString(R.string.uikit_back)
                backRow.setOnClickListener { config.onLeadingClick() }
                backRow.expandTouchTarget()

                confirmView.visibility = if (config.showConfirm) View.VISIBLE else View.GONE
                if (config.showConfirm) {
                    confirmView.text = config.confirmText
                        ?: context.getString(R.string.uikit_confirm)
                    confirmView.isEnabled = confirmEnabled
                    confirmView.isClickable = confirmEnabled
                    confirmView.setOnClickListener { config.onConfirmClick?.invoke() }
                    confirmView.expandTouchTarget()
                }
            }

            Mode.CancelTitleConfirm -> {
                backRow.visibility = View.GONE
                cancelView.visibility = View.VISIBLE
                cancelView.text = config.cancelText
                    ?: context.getString(R.string.uikit_cancel)
                cancelView.contentDescription = config.leadingContentDescription
                cancelView.setOnClickListener { config.onLeadingClick() }
                cancelView.expandTouchTarget()

                val showConfirmButton = config.showConfirm || config.onConfirmClick != null
                confirmView.visibility = if (showConfirmButton) View.VISIBLE else View.GONE
                if (showConfirmButton) {
                    confirmView.text = config.confirmText
                        ?: context.getString(R.string.uikit_confirm)
                    confirmView.isEnabled = confirmEnabled
                    confirmView.isClickable = confirmEnabled
                    confirmView.setOnClickListener { config.onConfirmClick?.invoke() }
                    confirmView.expandTouchTarget()
                }
            }
        }

        applyColors(config.colors)
        return this
    }

    fun setTitle(title: CharSequence) {
        titleView.text = title
    }

    fun setConfirmVisible(visible: Boolean) {
        confirmView.visibility = if (visible) View.VISIBLE else View.GONE
    }

    fun setConfirmEnabled(enabled: Boolean, colors: ColorTokens) {
        confirmEnabled = enabled
        confirmView.isEnabled = enabled
        confirmView.isClickable = enabled
        confirmView.setTextColor(
            if (enabled) colors.textColorLink else colors.textColorDisable
        )
    }

    fun applyColors(colors: ColorTokens) {
        setBackgroundColor(colors.bgColorOperate)
        titleView.setTextColor(colors.textColorPrimary)
        backIconView.imageTintList = ColorStateList.valueOf(colors.textColorSecondary)
        cancelView.setTextColor(colors.textColorLink)
        if (confirmView.visibility == View.VISIBLE) {
            confirmView.setTextColor(
                if (confirmEnabled) colors.textColorLink else colors.textColorDisable
            )
        }
    }

    companion object {
        private const val DEFAULT_HEIGHT_DP = 56f
        private const val BACK_ICON_SIZE_DP = 16f
        private const val TITLE_TEXT_SIZE_SP = 16f
        private const val BACK_TITLE_HORIZONTAL_PADDING_DP = 16f
        private const val CANCEL_TITLE_HORIZONTAL_PADDING_DP = 10f

        fun create(context: Context, config: Config): DialogNavBar {
            return DialogNavBar(context).bind(config)
        }
    }
}
