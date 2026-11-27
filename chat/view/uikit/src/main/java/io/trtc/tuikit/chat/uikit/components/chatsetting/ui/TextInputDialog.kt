package io.trtc.tuikit.chat.uikit.components.chatsetting.ui
import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import io.trtc.tuikit.chat.uikit.R
import io.trtc.tuikit.atomicx.common.util.ScreenUtil.dp2px
import io.trtc.tuikit.atomicx.theme.ThemeStore
import io.trtc.tuikit.atomicx.theme.tokens.DesignTokenSet
import io.trtc.tuikit.atomicx.widget.basicwidget.popover.AtomicPopover

class TextInputDialog(
    private val context: Context,
    private val title: String,
    private val initialText: String = "",
    private val hintText: String = "",
    private val maxLength: Int = 0,
    private val multiline: Boolean = false,
    private val minLines: Int = if (multiline) 5 else 1,
    private val maxLines: Int = if (multiline) 10 else 1,
    private val onConfirm: (String) -> Unit
) {

    fun show() {
        val tokens = ThemeStore.shared(context).themeState.value.currentTheme.tokens
        val res = context.resources
        val dm = res.displayMetrics

        val contentPaddingPx = res.getDimensionPixelSize(io.trtc.tuikit.atomicx.R.dimen.spacing_20)
        val titleBottomPx = res.getDimensionPixelSize(io.trtc.tuikit.atomicx.R.dimen.spacing_20)
        val inputBottomPx = res.getDimensionPixelSize(io.trtc.tuikit.atomicx.R.dimen.spacing_20)
        val singleLineHeightPx = dp2px(40f, dm).toInt()
        val multiLineMinHeightPx = dp2px(160f, dm).toInt()
        val multiLineMaxHeightPx = dp2px(280f, dm).toInt()
        val buttonHeightPx = dp2px(36f, dm).toInt()
        val inputCornerPx = res.getDimension(io.trtc.tuikit.atomicx.R.dimen.radius_8)
        val inputPaddingHorizontalPx = res.getDimensionPixelSize(io.trtc.tuikit.atomicx.R.dimen.spacing_16)
        val inputPaddingVerticalPx = if (multiline) dp2px(12f, dm).toInt() else 0
        val strokeWidthPx = dp2px(1f, dm).toInt().coerceAtLeast(1)
        val counterTopPx = dp2px(6f, dm).toInt()

        val rootLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_LOCALE
            setPadding(contentPaddingPx, contentPaddingPx, contentPaddingPx, contentPaddingPx)
        }

        val titleView = TextView(context).apply {
            text = title
            textSize = tokens.font.bold16.size
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(tokens.color.textColorPrimary)
            gravity = Gravity.CENTER
            textDirection = View.TEXT_DIRECTION_LOCALE
        }
        rootLayout.addView(
            titleView,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = titleBottomPx }
        )

        val editText = EditText(context).apply {
            layoutDirection = View.LAYOUT_DIRECTION_LOCALE
            textDirection = View.TEXT_DIRECTION_LOCALE
            if (multiline) {
                gravity = Gravity.START or Gravity.TOP
                textAlignment = View.TEXT_ALIGNMENT_VIEW_START
                setHorizontallyScrolling(false)
                isVerticalScrollBarEnabled = true
                inputType = InputType.TYPE_CLASS_TEXT or
                    InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                    InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                imeOptions = android.view.inputmethod.EditorInfo.IME_FLAG_NO_ENTER_ACTION
                setMinLines(minLines)
                setMaxLines(maxLines)
                minHeight = multiLineMinHeightPx
                maxHeight = multiLineMaxHeightPx
            } else {
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
                textAlignment = View.TEXT_ALIGNMENT_VIEW_START
                setSingleLine(true)
            }
            if (hintText.isNotEmpty()) hint = hintText
            if (maxLength > 0) {
                filters = arrayOf(android.text.InputFilter.LengthFilter(maxLength))
            }
            setText(initialText)
            textSize = tokens.font.regular16.size
            setTextColor(tokens.color.textColorPrimary)
            setHintTextColor(tokens.color.textColorTertiary)
            background = GradientDrawable().apply {
                setColor(tokens.color.bgColorDialog)
                cornerRadius = inputCornerPx
                setStroke(strokeWidthPx, tokens.color.strokeColorPrimary)
            }
            setPadding(
                inputPaddingHorizontalPx,
                inputPaddingVerticalPx,
                inputPaddingHorizontalPx,
                inputPaddingVerticalPx
            )
        }

        val editLayoutParams = if (multiline) {
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        } else {
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                singleLineHeightPx
            )
        }
        val showCounter = multiline && maxLength > 0
        if (!showCounter) {
            editLayoutParams.bottomMargin = inputBottomPx
        }
        rootLayout.addView(editText, editLayoutParams)

        if (showCounter) {
            val counterView = TextView(context).apply {
                text = formatCounter(editText.text?.length ?: 0, maxLength)
                textSize = tokens.font.regular12.size
                setTextColor(tokens.color.textColorTertiary)
                gravity = Gravity.END
                textDirection = View.TEXT_DIRECTION_LOCALE
            }
            rootLayout.addView(
                counterView,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = counterTopPx
                    bottomMargin = inputBottomPx
                }
            )
            editText.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    counterView.text = formatCounter(s?.length ?: 0, maxLength)
                }
            })
        }

        val popover = AtomicPopover(context, AtomicPopover.PanelGravity.CENTER).apply {
            setContent(rootLayout)
            setCancelable(true)
            setCanceledOnTouchOutside(true)
        }

        val confirmButton = TextView(context).apply {
            text = context.getString(R.string.uikit_confirm)
            textSize = tokens.font.regular16.size
            typeface = Typeface.DEFAULT
            setTextColor(tokens.color.textColorButton)
            gravity = Gravity.CENTER
            textDirection = View.TEXT_DIRECTION_LOCALE
            background = createPillButtonBackground(tokens, buttonHeightPx / 2f)
            isClickable = true
            isFocusable = true
            setOnClickListener {
                val inputText = editText.text.toString().trim()
                onConfirm(inputText)
                popover.dismiss()
            }
        }
        rootLayout.addView(
            confirmButton,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                buttonHeightPx
            )
        )

        popover.window?.apply {
            setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE or
                    WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
            )
        }

        popover.show()

        editText.requestFocus()
        editText.setSelection(editText.text.length)
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        editText.postDelayed({
            imm.showSoftInput(editText, 0)
            editText.setSelection(editText.text.length)
        }, 200)
    }

    private fun formatCounter(current: Int, max: Int): String = "$current/$max"

    private fun createPillButtonBackground(tokens: DesignTokenSet, radiusPx: Float): GradientDrawable {
        return GradientDrawable().apply {
            setColor(tokens.color.buttonColorPrimaryDefault)
            cornerRadius = radiusPx
        }
    }
}
