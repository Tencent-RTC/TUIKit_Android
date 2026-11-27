package io.trtc.tuikit.chat.uikit.components.messagelist.listen
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.cardview.widget.CardView
import io.trtc.tuikit.chat.uikit.R
import io.trtc.tuikit.atomicx.theme.ThemeStore
import io.trtc.tuikit.atomicx.theme.tokens.ColorTokens

// Floating playback status bar for "listen from here". Collapsed by default (a
// small round button); tap to expand and show the currently spoken text; the
// close button stops playback. Colors come from semantic theme tokens and are
// refreshed by the host via [applyColors].
class ListenPlaybackBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val cardView: CardView
    private val borderDrawable: GradientDrawable
    private val iconView: ImageView
    private val loadingView: ProgressBar
    private val textView: TextView
    private val closeView: ImageView

    private var expanded = false
    private var isLoading = false
    private var currentText = ""
    private var onCloseClick: () -> Unit = {}

    init {
        val wrap = ViewGroup.LayoutParams.WRAP_CONTENT
        val iconSize = dp(20f)

        // Allow the card's rounded elevation shadow to render fully. Without this
        // the default child clipping cuts the soft shadow off at the card's
        // rectangular bounds, leaving a hard rectangular shadow.
        clipChildren = false
        clipToPadding = false
        val shadowPadding = dp(8f)
        setPadding(shadowPadding, shadowPadding, shadowPadding, shadowPadding)

        cardView = CardView(context).apply {
            radius = dp(100f).toFloat()
            cardElevation = dp(4f).toFloat()
            preventCornerOverlap = false
            useCompatPadding = false
            isClickable = true
            isFocusable = true
        }
        // CardView has no native stroke, so the thin border is drawn by a
        // rounded GradientDrawable behind the content; its color is refreshed in
        // [applyColors].
        borderDrawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(100f).toFloat()
            setColor(Color.TRANSPARENT)
        }
        val contentView = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = borderDrawable
            // Fixed row height with horizontal-only padding so the full-height
            // close target does not change the bar height.
            setPadding(dp(10f), 0, dp(10f), 0)
        }
        val iconContainer = FrameLayout(context)
        iconView = ImageView(context).apply {
            setImageResource(R.drawable.message_list_menu_listen_icon)
        }
        loadingView = ProgressBar(context, null, android.R.attr.progressBarStyleSmall).apply {
            isIndeterminate = true
            visibility = View.GONE
        }
        iconContainer.addView(iconView, FrameLayout.LayoutParams(iconSize, iconSize, Gravity.CENTER))
        iconContainer.addView(loadingView, FrameLayout.LayoutParams(iconSize, iconSize, Gravity.CENTER))

        textView = TextView(context).apply {
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            visibility = View.GONE
        }
        closeView = ImageView(context).apply {
            setImageResource(R.drawable.message_list_listen_close_icon)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            // Keep the icon visually ~16dp but expand the tappable area so a
            // single tap reliably stops playback instead of hitting the
            // surrounding text (collapse) or card (no-op) by mistake.
            val closeTouchPadding = dp(10f)
            setPadding(closeTouchPadding, closeTouchPadding, closeTouchPadding, closeTouchPadding)
            visibility = View.GONE
        }

        contentView.addView(iconContainer, LinearLayout.LayoutParams(wrap, wrap))
        contentView.addView(
            textView,
            LinearLayout.LayoutParams(0, wrap, 1f).apply { marginStart = dp(8f) }
        )
        contentView.addView(
            closeView,
            LinearLayout.LayoutParams(dp(36f), LinearLayout.LayoutParams.MATCH_PARENT).apply {
                marginStart = dp(4f)
            }
        )
        cardView.addView(contentView, FrameLayout.LayoutParams(wrap, dp(ROW_HEIGHT_DP)))
        addView(cardView, LayoutParams(wrap, wrap))

        cardView.setOnClickListener {
            if (!expanded) {
                expanded = true
                updateViews()
            }
        }
        textView.setOnClickListener {
            if (expanded) {
                expanded = false
                updateViews()
            }
        }
        closeView.setOnClickListener { onCloseClick() }

        updateViews()
        applyColors(ThemeStore.shared(context).themeState.value.currentTheme.tokens.color)
    }

    fun setOnCloseClickListener(listener: () -> Unit) {
        onCloseClick = listener
    }

    fun render(loading: Boolean, text: String) {
        isLoading = loading
        currentText = text
        updateViews()
    }

    fun collapse() {
        expanded = false
        updateViews()
    }

    fun applyColors(colors: ColorTokens) {
        cardView.setCardBackgroundColor(colors.bgColorOperate)
        borderDrawable.setStroke(dp(0.5f), colors.strokeColorPrimary)
        iconView.imageTintList = ColorStateList.valueOf(colors.textColorLink)
        loadingView.indeterminateTintList = ColorStateList.valueOf(colors.textColorLink)
        textView.setTextColor(colors.textColorSecondary)
        closeView.imageTintList = ColorStateList.valueOf(colors.textColorPrimary)
    }

    // Cap the expanded bar at 320dp wide to match the Flutter playback bar; the
    // inner text then ellipsizes instead of pushing the bar off-screen.
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val maxWidth = dp(MAX_EXPANDED_WIDTH_DP)
        val widthMode = MeasureSpec.getMode(widthMeasureSpec)
        val widthSize = MeasureSpec.getSize(widthMeasureSpec)
        val cappedSpec = when {
            widthMode == MeasureSpec.UNSPECIFIED ->
                MeasureSpec.makeMeasureSpec(maxWidth, MeasureSpec.AT_MOST)
            widthSize > maxWidth ->
                MeasureSpec.makeMeasureSpec(maxWidth, MeasureSpec.AT_MOST)
            else -> widthMeasureSpec
        }
        super.onMeasure(cappedSpec, heightMeasureSpec)
    }

    private fun updateViews() {
        loadingView.visibility = if (isLoading) View.VISIBLE else View.GONE
        iconView.visibility = if (isLoading) View.GONE else View.VISIBLE
        textView.visibility = if (expanded) View.VISIBLE else View.GONE
        closeView.visibility = if (expanded) View.VISIBLE else View.GONE
        textView.text = currentText
    }

    private fun dp(value: Float): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value,
            resources.displayMetrics
        ).toInt()
    }

    private companion object {
        const val MAX_EXPANDED_WIDTH_DP = 320f
        const val ROW_HEIGHT_DP = 36f
    }
}
