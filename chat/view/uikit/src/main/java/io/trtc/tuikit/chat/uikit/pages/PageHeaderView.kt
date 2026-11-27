package io.trtc.tuikit.chat.uikit.pages
import android.content.Context
import android.graphics.Typeface
import android.text.TextUtils
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import io.trtc.tuikit.atomicx.theme.ThemeStore
import io.trtc.tuikit.atomicx.theme.tokens.ColorTokens
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class PageHeaderView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val tvTitle: TextView
    private val editContentContainer: FrameLayout
    private val leftActionContainer: FrameLayout

    private val themeStore = ThemeStore.shared(context)
    private var viewScope: CoroutineScope? = null

    init {
        layoutDirection = LAYOUT_DIRECTION_LOCALE
        val density = resources.displayMetrics.density
        val paddingH = (16 * density).toInt()
        val paddingV = (12 * density).toInt()
        setPaddingRelative(paddingH, paddingV, paddingH, paddingV)

        leftActionContainer = FrameLayout(context).apply {
            layoutDirection = LAYOUT_DIRECTION_LOCALE
        }

        tvTitle = TextView(context).apply {
            textSize = 17f
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            textAlignment = TEXT_ALIGNMENT_CENTER
            textDirection = TEXT_DIRECTION_LOCALE
            ellipsize = TextUtils.TruncateAt.END
            maxLines = 1
        }
        addView(
            tvTitle,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER)
        )

        editContentContainer = FrameLayout(context).apply {
            layoutDirection = LAYOUT_DIRECTION_LOCALE
        }
        addView(
            leftActionContainer,
            LayoutParams(
                LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT,
                Gravity.START or Gravity.CENTER_VERTICAL
            )
        )
        addView(
            editContentContainer,
            LayoutParams(
                LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT,
                Gravity.END or Gravity.CENTER_VERTICAL
            )
        )

        applyColors(themeStore.themeState.value.currentTheme.tokens.color)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)

        val titleInset = PageHeaderLayoutPolicy.titleHorizontalInset(
            startActionWidth = leftActionContainer.measuredWidth,
            endActionWidth = editContentContainer.measuredWidth
        )
        if (tvTitle.paddingStart != titleInset || tvTitle.paddingEnd != titleInset) {
            tvTitle.setPaddingRelative(
                titleInset,
                tvTitle.paddingTop,
                titleInset,
                tvTitle.paddingBottom
            )
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        viewScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
        viewScope?.launch {
            themeStore.themeState.collectLatest { state ->
                applyColors(state.currentTheme.tokens.color)
            }
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        viewScope?.cancel()
        viewScope = null
    }

    private fun applyColors(colors: ColorTokens) {
        setBackgroundColor(colors.bgColorOperate)
        tvTitle.setTextColor(colors.textColorPrimary)
    }

    fun setTitle(title: String) {
        tvTitle.text = title
    }

    fun setEditContent(view: View) {
        editContentContainer.removeAllViews()
        editContentContainer.addView(view)
    }

    fun setLeftAction(view: View) {
        leftActionContainer.removeAllViews()
        leftActionContainer.addView(view)
    }
}

internal object PageHeaderLayoutPolicy {
    fun titleHorizontalInset(startActionWidth: Int, endActionWidth: Int): Int {
        return maxOf(startActionWidth, endActionWidth)
    }
}
