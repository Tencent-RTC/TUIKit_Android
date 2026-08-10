package io.trtc.tuikit.chat.uikit.components.chatsetting.ui
import android.content.Context
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
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

class SettingRowNavigate @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val titleTextView: TextView
    private val valueTextView: TextView
    private val accessoryImageView: ImageView
    private var viewScope: CoroutineScope? = null
    private var showArrow: Boolean = true
    private var customAccessoryResId: Int? = null

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        layoutDirection = LAYOUT_DIRECTION_LOCALE
        val horizontalPadding = dp2px(16f, resources.displayMetrics).toInt()
        val verticalPadding = dp2px(14f, resources.displayMetrics).toInt()
        setPadding(horizontalPadding, verticalPadding, horizontalPadding, verticalPadding)
        isClickable = true
        isFocusable = true

        titleTextView = TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            textAlignment = View.TEXT_ALIGNMENT_VIEW_START
            textDirection = View.TEXT_DIRECTION_LOCALE
            maxLines = 1
        }
        addView(titleTextView, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))

        valueTextView = TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            textAlignment = View.TEXT_ALIGNMENT_VIEW_END
            textDirection = View.TEXT_DIRECTION_LOCALE
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp2px(16f, resources.displayMetrics).toInt()
            }
        }
        addView(valueTextView)

        accessoryImageView = ImageView(context).apply {
            layoutDirection = LAYOUT_DIRECTION_LOCALE
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            layoutParams = LayoutParams(
                dp2px(16f, resources.displayMetrics).toInt(),
                dp2px(16f, resources.displayMetrics).toInt()
            ).apply {
                marginStart = dp2px(8f, resources.displayMetrics).toInt()
            }
        }
        addView(accessoryImageView)
    }

    fun setTitle(title: String) {
        titleTextView.text = title
    }

    fun setValue(value: String) {
        valueTextView.text = value
    }

    fun setShowArrow(show: Boolean) {
        showArrow = show
        applyAccessory(getColors())
    }

    fun setCustomAccessory(drawableResId: Int?) {
        customAccessoryResId = drawableResId
        applyAccessory(getColors())
    }

    private fun applyThemeColors(colors: ColorTokens) {
        setBackgroundColor(colors.bgColorOperate)
        titleTextView.setTextColor(colors.textColorSecondary)
        valueTextView.setTextColor(colors.textColorPrimary)
        applyAccessory(colors)
    }

    private fun applyAccessory(colors: ColorTokens) {
        val drawableResId = customAccessoryResId ?: if (showArrow) R.drawable.uikit_ic_arrow_right else null
        if (drawableResId == null) {
            accessoryImageView.visibility = GONE
            return
        }
        accessoryImageView.visibility = VISIBLE
        accessoryImageView.setImageResource(drawableResId)
        accessoryImageView.scaleX = 1f
        accessoryImageView.setColorFilter(colors.textColorSecondary)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        viewScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
        viewScope?.launch {
            ThemeStore.shared(context).themeState.collectLatest {
                applyThemeColors(it.currentTheme.tokens.color)
            }
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        viewScope?.cancel()
        viewScope = null
    }

    private fun getColors(): ColorTokens {
        return ThemeStore.shared(context).themeState.value.currentTheme.tokens.color
    }
}
