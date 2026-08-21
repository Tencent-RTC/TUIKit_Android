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
import io.trtc.tuikit.chat.uikit.components.widgets.Switch
import io.trtc.tuikit.chat.uikit.components.widgets.SwitchSize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class SettingRowToggle @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val titleTextView: TextView
    private val aSwitch: Switch
    private var viewScope: CoroutineScope? = null

    var onToggleChanged: ((Boolean) -> Unit)? = null

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        layoutDirection = LAYOUT_DIRECTION_LOCALE
        val horizontalPadding = dp2px(16f, resources.displayMetrics).toInt()
        val verticalPadding = dp2px(10f, resources.displayMetrics).toInt()
        setPadding(horizontalPadding, verticalPadding, horizontalPadding, verticalPadding)

        titleTextView = TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            textAlignment = View.TEXT_ALIGNMENT_VIEW_START
            textDirection = View.TEXT_DIRECTION_LOCALE
            layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
        }
        addView(titleTextView)

        aSwitch = Switch(context).apply {
            layoutDirection = LAYOUT_DIRECTION_LOCALE
            setSize(SwitchSize.L)
            setOnCheckedChangeListener { checked ->
                onToggleChanged?.invoke(checked)
            }
        }
        addView(aSwitch)
    }

    fun setTitle(title: String) {
        titleTextView.text = title
    }

    fun setChecked(checked: Boolean) {
        aSwitch.setChecked(checked, animate = false)
    }

    private fun applyThemeColors(colors: ColorTokens) {
        setBackgroundColor(colors.bgColorOperate)
        titleTextView.setTextColor(colors.textColorSecondary)
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
