package io.trtc.tuikit.chat.uikit.components.chatsetting.ui
import android.content.Context
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import io.trtc.tuikit.atomicx.common.util.ScreenUtil.dp2px
import io.trtc.tuikit.atomicx.theme.ThemeStore
import io.trtc.tuikit.atomicx.theme.tokens.ColorTokens
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class SettingRowButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    enum class Style {
        NORMAL,
        DANGER,
        LINK
    }

    private val titleTextView: TextView
    private var viewScope: CoroutineScope? = null
    private var buttonStyle: Style = Style.NORMAL

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER
        layoutDirection = LAYOUT_DIRECTION_LOCALE
        val verticalPadding = dp2px(15f, resources.displayMetrics).toInt()
        setPadding(0, verticalPadding, 0, verticalPadding)
        setMinimumHeight(dp2px(56f, resources.displayMetrics).toInt())
        isClickable = true
        isFocusable = true

        titleTextView = TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
            gravity = Gravity.CENTER
            textAlignment = View.TEXT_ALIGNMENT_CENTER
            textDirection = View.TEXT_DIRECTION_LOCALE
        }
        addView(titleTextView, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
    }

    fun setTitle(title: String) {
        titleTextView.text = title
    }

    fun setDangerStyle(isDanger: Boolean) {
        buttonStyle = if (isDanger) Style.DANGER else Style.NORMAL
        val colors = ThemeStore.shared(context).themeState.value.currentTheme.tokens.color
        applyThemeColors(colors)
    }

    fun setButtonStyle(style: Style) {
        buttonStyle = style
        val colors = ThemeStore.shared(context).themeState.value.currentTheme.tokens.color
        applyThemeColors(colors)
    }

    private fun applyThemeColors(colors: ColorTokens) {
        setBackgroundColor(colors.bgColorOperate)
        titleTextView.setTextColor(
            when (buttonStyle) {
                Style.DANGER -> colors.textColorError
                Style.LINK -> colors.textColorLink
                Style.NORMAL -> colors.textColorPrimary
                else -> colors.textColorPrimary
            }
        )
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
}
