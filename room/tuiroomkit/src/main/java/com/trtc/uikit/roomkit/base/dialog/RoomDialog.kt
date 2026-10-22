package com.trtc.uikit.roomkit.base.dialog

import android.view.MotionEvent
import android.view.ViewGroup
import android.graphics.Typeface
import android.app.Dialog
import android.content.Context
import android.content.res.ColorStateList
import androidx.core.content.ContextCompat
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.appcompat.widget.AppCompatTextView
import com.trtc.uikit.roomkit.R
import androidx.appcompat.widget.AppCompatButton
import androidx.appcompat.widget.AppCompatImageView
import com.trtc.uikit.roomkit.base.util.dp

class RoomDialog private constructor(
    context: Context,
    private val config: Config,
) : Dialog(context) {

    enum class Variant { PRIMARY, GHOST, DANGER }

    class Config(
        val title: CharSequence?,
        val message: CharSequence?,
        val contentView: View?,
        val primaryText: CharSequence?,
        val primaryVariant: Variant,
        val onPrimary: (() -> Unit)?,
        val secondaryText: CharSequence?,
        val onSecondary: (() -> Unit)?,
        val closable: Boolean,
        val cancelable: Boolean = true,
        val keepsOpenOnPrimary: Boolean = false,
    )

    class Builder(private val context: Context) {
        private var title: CharSequence? = null
        private var message: CharSequence? = null
        private var contentView: View? = null
        private var primaryText: CharSequence? = null
        private var primaryVariant: Variant = Variant.PRIMARY
        private var onPrimary: (() -> Unit)? = null
        private var secondaryText: CharSequence? = null
        private var onSecondary: (() -> Unit)? = null
        private var closable: Boolean = true
        private var cancelable: Boolean = true
        private var keepsOpenOnPrimary: Boolean = false

        fun title(text: CharSequence?): Builder = apply { title = text }
        fun message(text: CharSequence?): Builder = apply { message = text }
        fun contentView(view: View?): Builder = apply { contentView = view }
        fun closable(value: Boolean): Builder = apply { closable = value }
        fun cancelable(value: Boolean): Builder = apply { cancelable = value }

        fun primaryAction(
            text: CharSequence,
            variant: Variant = Variant.PRIMARY,
            onTap: () -> Unit = {},
        ): Builder = apply {
            primaryText = text
            primaryVariant = variant
            onPrimary = onTap
        }

        fun secondaryAction(text: CharSequence, onTap: () -> Unit = {}): Builder = apply {
            secondaryText = text
            onSecondary = onTap
        }

        fun keepsOpenOnPrimary(value: Boolean): Builder = apply { keepsOpenOnPrimary = value }

        fun build(): RoomDialog = RoomDialog(
            context,
            Config(
                title, message, contentView,
                primaryText, primaryVariant, onPrimary,
                secondaryText, onSecondary, closable, cancelable, keepsOpenOnPrimary,
            ),
        )

        fun show(): RoomDialog = build().also { it.show() }
    }

    init {
        setCancelable(config.cancelable)
        setCanceledOnTouchOutside(config.cancelable)
        buildLayout()
        setCanceledOnTouchOutside(true)
    }

    override fun show() {
        val input = findEditText(config.contentView)
        if (input != null) {
            window?.setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                    WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE,
            )
        }
        super.show()
        input?.post {
            input.requestFocus()
            input.setSelection(input.text?.length ?: 0)
            (context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)
                ?.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun findEditText(view: View?): EditText? {
        if (view == null) return null
        if (view is EditText) return view
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                findEditText(view.getChildAt(i))?.let { return it }
            }
        }
        return null
    }

    private fun buildLayout() {
        val ctx = context

        requestWindowFeature(Window.FEATURE_NO_TITLE)
        window?.setBackgroundDrawable(ColorDrawable(ContextCompat.getColor(context, R.color.roomkit_scrim)))
        window?.setDimAmount(0f)

        val card = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            isClickable = true
            setPadding(ctx.dp(16), ctx.dp(18), ctx.dp(16), ctx.dp(14))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = ctx.dp(12).toFloat()
                setColor(ContextCompat.getColor(ctx, R.color.roomkit_bg_surface))
            }
            setOnClickListener { hideKeyboard() }
        }

        val centerStyle = !config.closable

        if (config.title != null || config.closable) {
            val head = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            config.title?.let {
                head.addView(AppCompatTextView(ctx).apply {
                    text = it
                    textSize = 15f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    setTextColor(ContextCompat.getColor(ctx, R.color.roomkit_fg_primary))
                    gravity = if (centerStyle) Gravity.CENTER else Gravity.START
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                })
            }
            if (config.closable) {
                head.addView(makeCloseButton(ctx))
            }
            card.addView(head)
        }

        config.message?.let {
            card.addView(AppCompatTextView(ctx).apply {
                text = it
                textSize = 13f
                setTextColor(ContextCompat.getColor(ctx, R.color.roomkit_fg_secondary))
                setLineSpacing(0f, 1.45f)
                gravity = if (centerStyle) Gravity.CENTER else Gravity.START
                val topMargin = if (config.title != null || config.closable) ctx.dp(8) else 0
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { setMargins(0, topMargin, 0, 0) }
            })
        }

        config.contentView?.let {
            val topMargin = if (config.title != null || config.message != null || config.closable) ctx.dp(12) else 0
            card.addView(
                it,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { setMargins(0, topMargin, 0, 0) },
            )
        }

        val hasAction = config.primaryText != null || config.secondaryText != null
        if (hasAction) {
            val actions = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { setMargins(0, ctx.dp(16), 0, 0) }
            }
            config.secondaryText?.let { text ->
                actions.addView(styledButton(ctx, Variant.GHOST).apply {
                    this.text = text
                    setOnClickListener {
                        config.onSecondary?.invoke()
                        dismiss()
                    }
                    layoutParams = LinearLayout.LayoutParams(0, ctx.dp(40), 1f)
                })
            }
            if (config.secondaryText != null && config.primaryText != null) {
                actions.addView(View(ctx).apply {
                    layoutParams = LinearLayout.LayoutParams(ctx.dp(8), 1)
                })
            }
            config.primaryText?.let { text ->
                actions.addView(styledButton(ctx, config.primaryVariant).apply {
                    this.text = text
                    setOnClickListener {
                        config.onPrimary?.invoke()
                        if (!config.keepsOpenOnPrimary) dismiss()
                    }
                    layoutParams = LinearLayout.LayoutParams(0, ctx.dp(40), 1f)
                })
            }
            card.addView(actions)
        }

        val root = FrameLayout(ctx).apply {
            setBackgroundColor(Color.TRANSPARENT)
            setOnClickListener { cancel() }
        }
        val cardLp = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER,
        ).apply {
            marginStart = ctx.dp(20)
            marginEnd = ctx.dp(20)
        }
        root.addView(card, cardLp)
        setContentView(root)

        window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )
    }

    private fun hideKeyboard() {
        currentFocus?.let { focus ->
            (context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)
                ?.hideSoftInputFromWindow(focus.windowToken, 0)
        }
    }

    private fun makeCloseButton(ctx: Context): View {
        return AppCompatImageView(ctx).apply {
            setImageResource(R.drawable.roomkit_icon_close)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = ctx.dp(8).toFloat()
                setColor(Color.TRANSPARENT)
            }
            layoutParams = LinearLayout.LayoutParams(ctx.dp(28), ctx.dp(28)).apply {
                marginStart = ctx.dp(8)
            }
            setOnClickListener { cancel() }
            setOnTouchListener { view, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        (view.background as? GradientDrawable)?.setColor(ContextCompat.getColor(ctx, R.color.roomkit_bg_hover))
                        view.invalidate()
                    }
                    MotionEvent.ACTION_UP,
                    MotionEvent.ACTION_CANCEL,
                    MotionEvent.ACTION_OUTSIDE -> {
                        (view.background as? GradientDrawable)?.setColor(Color.TRANSPARENT)
                        view.invalidate()
                    }
                }
                false
            }
        }
    }

    private fun styledButton(ctx: Context, variant: Variant): AppCompatButton = AppCompatButton(ctx).apply {
        stateListAnimator = null
        isAllCaps = false
        includeFontPadding = false
        textSize = 14f
        typeface = Typeface.DEFAULT_BOLD
        setPadding(ctx.dp(16), 0, ctx.dp(16), 0)
        when (variant) {
            Variant.GHOST -> {
                background = GradientDrawable().apply {
                    cornerRadius = ctx.dp(8).toFloat()
                    setColor(Color.argb(15, 28, 27, 28))
                }
                setTextColor(ContextCompat.getColor(ctx, R.color.roomkit_fg_primary))
            }
            Variant.PRIMARY -> {
                background = GradientDrawable().apply {
                    cornerRadius = ctx.dp(8).toFloat()
                    setColor(ContextCompat.getColor(ctx, R.color.roomkit_accent))
                }
                setTextColor(Color.WHITE)
            }
            Variant.DANGER -> {
                background = GradientDrawable().apply {
                    cornerRadius = ctx.dp(8).toFloat()
                    setColor(ContextCompat.getColor(ctx, R.color.roomkit_danger))
                }
                setTextColor(Color.WHITE)
            }
        }
    }

    companion object {
        fun builder(context: Context): Builder = Builder(context)
    }
}
