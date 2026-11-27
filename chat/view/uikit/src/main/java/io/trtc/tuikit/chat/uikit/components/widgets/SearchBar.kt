package io.trtc.tuikit.chat.uikit.components.widgets
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import io.trtc.tuikit.chat.uikit.R
import io.trtc.tuikit.chat.uikit.components.common.expandTouchTarget
import io.trtc.tuikit.chat.uikit.components.common.hideKeyboard
import io.trtc.tuikit.chat.uikit.components.common.showKeyboard
import io.trtc.tuikit.atomicx.theme.ThemeStore

data class SearchBarConfig(
    val showBack: Boolean = false,
    val showCancel: Boolean = false,
    val inputHeightDp: Int = 40,
    val hint: CharSequence? = null,
    val debounceMs: Long = 300L,
    val searchIconRes: Int = R.drawable.search_ic_search,
    val clearIconRes: Int = R.drawable.search_ic_search_clear,
    val backIconRes: Int = R.drawable.uikit_ic_back,
    val paddingHorizontalDp: Int = 16,
    val paddingVerticalDp: Int = 10,
    val paddingBottomDp: Int = paddingVerticalDp,
    val inputCornerRadiusDp: Int = 10,
    val searchIconMarginStartDp: Int = 8,
    val inputTextPaddingStartDp: Int = 36,
    val expandTouchTargets: Boolean = true
)

open class SearchBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    val editText: EditText
    private val cancelButton: TextView
    private val backButton: ImageView
    private val searchIcon: ImageView
    private val inputContainer: FrameLayout
    private val clearButton: ImageView
    private val handler = Handler(Looper.getMainLooper())
    private var debounceRunnable: Runnable? = null
    private var suppressTextCallback = false
    private var config = SearchBarConfig()

    var onQueryChanged: ((String) -> Unit)? = null
    var onQueryChange: ((String) -> Unit)?
        get() = onQueryChanged
        set(value) {
            onQueryChanged = value
        }
    var onCancel: (() -> Unit)? = null
    var onBack: (() -> Unit)? = null

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        layoutDirection = View.LAYOUT_DIRECTION_LOCALE

        backButton = ImageView(context).apply {
            val iconSize = dpToPx(16)
            layoutParams = LayoutParams(iconSize, iconSize).apply {
                marginEnd = dpToPx(10)
            }
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            visibility = View.GONE
            setOnClickListener { onBack?.invoke() }
        }
        addView(backButton)

        inputContainer = FrameLayout(context).apply {
            layoutDirection = View.LAYOUT_DIRECTION_LOCALE
            layoutParams = LayoutParams(0, dpToPx(config.inputHeightDp), 1f)
        }
        addView(inputContainer)

        editText = EditText(context).apply {
            layoutDirection = View.LAYOUT_DIRECTION_LOCALE
            textDirection = View.TEXT_DIRECTION_LOCALE
            gravity = Gravity.CENTER_VERTICAL or Gravity.START
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            background = null
            isSingleLine = true
            val horizontalPadding = dpToPx(36)
            setPadding(horizontalPadding, 0, horizontalPadding, 0)
            hint = context.getString(R.string.uikit_search_hint)
        }
        inputContainer.addView(editText)

        searchIcon = ImageView(context).apply {
            val iconSize = dpToPx(15)
            layoutParams = FrameLayout.LayoutParams(
                iconSize,
                iconSize,
                Gravity.START or Gravity.CENTER_VERTICAL
            ).apply {
                marginStart = dpToPx(8)
            }
        }
        inputContainer.addView(searchIcon)

        clearButton = ImageView(context).apply {
            val iconSize = dpToPx(16)
            layoutParams = FrameLayout.LayoutParams(
                iconSize,
                iconSize,
                Gravity.END or Gravity.CENTER_VERTICAL
            ).apply {
                marginEnd = dpToPx(10)
            }
            visibility = View.GONE
            isClickable = true
            isFocusable = true
            setOnClickListener { clearQuery() }
        }
        inputContainer.addView(clearButton)

        cancelButton = TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            text = context.getString(R.string.uikit_cancel)
            val dp12 = dpToPx(12)
            setPaddingRelative(dp12, 0, 0, 0)
            visibility = View.GONE
            setOnClickListener {
                clearQuery()
                onCancel?.invoke()
            }
        }
        addView(cancelButton, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))

        editText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                if (suppressTextCallback) {
                    updateClearButtonVisibility(s)
                    return
                }
                debounceRunnable?.let { handler.removeCallbacks(it) }
                updateClearButtonVisibility(s)
                val query = s?.toString() ?: ""
                val delay = config.debounceMs
                if (delay <= 0L) {
                    onQueryChanged?.invoke(query)
                } else {
                    debounceRunnable = Runnable {
                        onQueryChanged?.invoke(query)
                    }
                    handler.postDelayed(debounceRunnable!!, delay)
                }
            }
        })

        applyConfig(config)
        applyTheme()
    }

    fun configure(config: SearchBarConfig): SearchBar {
        applyConfig(config)
        applyTheme()
        return this
    }

    fun setQuery(query: String, notify: Boolean = false) {
        if (editText.text.toString() != query) {
            suppressTextCallback = !notify
            editText.setText(query)
            suppressTextCallback = false
            editText.setSelection(query.length.coerceAtMost(editText.text.length))
        }
        updateClearButtonVisibility(editText.text)
    }

    fun setHint(hintText: CharSequence) {
        editText.hint = hintText
    }

    fun requestFocusAndShowKeyboard() {
        editText.post {
            editText.showKeyboard()
        }
    }

    fun hideKeyboard() {
        editText.hideKeyboard()
        editText.clearFocus()
    }

    fun applyTheme() {
        val colors = ThemeStore.shared(context).themeState.value.currentTheme.tokens.color
        setBackgroundColor(colors.bgColorOperate)
        backButton.setImageResource(config.backIconRes)
        backButton.imageTintList = ColorStateList.valueOf(colors.textColorSecondary)
        searchIcon.setImageResource(config.searchIconRes)
        searchIcon.setColorFilter(colors.textColorTertiary)
        editText.setTextColor(colors.textColorPrimary)
        editText.setHintTextColor(colors.textColorTertiary)
        clearButton.setImageResource(config.clearIconRes)
        clearButton.setColorFilter(colors.textColorPrimary)
        cancelButton.setTextColor(colors.textColorPrimary)

        val bg = GradientDrawable().apply {
            setColor(colors.bgColorInput)
            cornerRadius = dpToPx(config.inputCornerRadiusDp).toFloat()
        }
        inputContainer.background = bg
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        debounceRunnable?.let { handler.removeCallbacks(it) }
    }

    private fun applyConfig(newConfig: SearchBarConfig) {
        config = newConfig
        val horizontal = dpToPx(newConfig.paddingHorizontalDp)
        val vertical = dpToPx(newConfig.paddingVerticalDp)
        setPadding(horizontal, vertical, horizontal, dpToPx(newConfig.paddingBottomDp))

        inputContainer.layoutParams = LayoutParams(0, dpToPx(newConfig.inputHeightDp), 1f)

        (searchIcon.layoutParams as? FrameLayout.LayoutParams)?.let {
            it.marginStart = dpToPx(newConfig.searchIconMarginStartDp)
            searchIcon.layoutParams = it
        }
        editText.setPaddingRelative(dpToPx(newConfig.inputTextPaddingStartDp), 0, dpToPx(36), 0)

        editText.hint = newConfig.hint ?: context.getString(R.string.uikit_search_hint)

        backButton.visibility = if (newConfig.showBack) View.VISIBLE else View.GONE
        cancelButton.visibility = if (newConfig.showCancel) View.VISIBLE else View.GONE

        if (newConfig.expandTouchTargets) {
            if (newConfig.showBack) {
                backButton.expandTouchTarget()
            }
            if (newConfig.showCancel) {
                cancelButton.expandTouchTarget()
            }
            if (clearButton.visibility == View.VISIBLE) {
                clearButton.expandTouchTarget()
            }
        }
    }

    private fun updateClearButtonVisibility(text: CharSequence?) {
        val visible = !text.isNullOrEmpty()
        clearButton.visibility = if (visible) View.VISIBLE else View.GONE
        if (visible && config.expandTouchTargets) {
            clearButton.expandTouchTarget()
        }
    }

    private fun clearQuery() {
        setQuery("")
        debounceRunnable?.let { handler.removeCallbacks(it) }
        debounceRunnable = null
        onQueryChanged?.invoke("")
    }

    private fun dpToPx(dp: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp.toFloat(),
            resources.displayMetrics
        ).toInt()
    }
}
