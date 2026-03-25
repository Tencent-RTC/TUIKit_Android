package com.trtc.uikit.livekit.component.gift.view.cell

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.trtc.uikit.livekit.R
import io.trtc.tuikit.atomicx.common.imageloader.ImageLoader
import io.trtc.tuikit.atomicx.theme.ThemeStore
import io.trtc.tuikit.atomicxcore.api.gift.Gift

class GiftComboCell(itemView: View) : GiftBaseCell(itemView) {
    private val layoutGift: LinearLayout = itemView.findViewById(R.id.ll_gift)
    private val imageGift: ImageView = itemView.findViewById(R.id.iv_gift_icon)
    private val textGiftName: TextView = itemView.findViewById(R.id.tv_gift_name)
    private val textGiftPrice: TextView = itemView.findViewById(R.id.tv_gift_price)

    private var comboCount = 0
    private var isComboActive = false
    private val comboHandler = Handler(Looper.getMainLooper())
    private var comboResetRunnable: Runnable? = null

    private var lastComboTriggerTime = 0L

    private var comboOverlay: FrameLayout? = null
    private var haloView: View? = null
    private var progressView: ComboProgressView? = null
    private var comboButton: TextView? = null
    private var badgeView: TextView? = null
    private var comboUICreated = false

    private fun getPrimaryColor(): Int {
        return ThemeStore.shared(itemView.context).themeState.value.currentTheme.tokens.color.buttonColorPrimaryActive
    }

    private var iconContainer: FrameLayout? = null

    private fun ensureComboUI() {
        if (comboUICreated) return
        comboUICreated = true

        val density = itemView.resources.displayMetrics.density
        val primaryColor = getPrimaryColor()

        val iconSize = if (imageGift.width > 0) imageGift.width else (71 * density).toInt()
        val iconHeight = if (imageGift.height > 0) imageGift.height else (70 * density).toInt()
        val comboSize = (iconSize * 0.9f).toInt()
        val buttonSize = (comboSize * 0.75f).toInt()

        val imageIndex = layoutGift.indexOfChild(imageGift)
        val imageLp = imageGift.layoutParams as LinearLayout.LayoutParams
        layoutGift.removeView(imageGift)

        iconContainer = FrameLayout(itemView.context)
        layoutGift.addView(iconContainer, imageIndex, LinearLayout.LayoutParams(imageLp.width, imageLp.height).apply {
            gravity = imageLp.gravity
            topMargin = imageLp.topMargin
            bottomMargin = imageLp.bottomMargin
            marginStart = imageLp.marginStart
            marginEnd = imageLp.marginEnd
        })

        imageGift.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )
        iconContainer!!.addView(imageGift)

        comboOverlay = FrameLayout(itemView.context).apply {
            visibility = View.GONE
        }

        haloView = View(itemView.context).apply {
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(
                    android.graphics.Color.argb(
                        (255 * 0.15f).toInt(),
                        android.graphics.Color.red(primaryColor),
                        android.graphics.Color.green(primaryColor),
                        android.graphics.Color.blue(primaryColor)
                    )
                )
            }
        }

        progressView = ComboProgressView(itemView.context, primaryColor)

        comboButton = TextView(itemView.context).apply {
            text = context.getString(R.string.common_gift_combo)
            textSize = 14f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(ContextCompat.getColor(context, android.R.color.white))
            gravity = Gravity.CENTER
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(primaryColor)
            }
            elevation = 4 * density
        }

        badgeView = TextView(itemView.context).apply {
            textSize = 12f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(ContextCompat.getColor(context, android.R.color.white))
            setPadding(
                (6 * density).toInt(), 0,
                (6 * density).toInt(), 0
            )
            gravity = Gravity.CENTER
            minWidth = (34 * density).toInt()
            minHeight = (16 * density).toInt()
            visibility = View.GONE
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                cornerRadius = 3 * density
                setColor(primaryColor)
            }
        }

        val centerParams = { size: Int ->
            FrameLayout.LayoutParams(size, size).apply {
                gravity = Gravity.CENTER
            }
        }

        comboOverlay!!.addView(haloView, centerParams(comboSize))
        comboOverlay!!.addView(progressView, centerParams(comboSize))
        comboOverlay!!.addView(comboButton, centerParams(buttonSize))

        badgeView!!.elevation = 8 * density
        val comboTopOffset = (iconHeight - comboSize) / 2
        val badgeHeight = (16 * density).toInt()
        val badgeTopMargin = comboTopOffset - badgeHeight + (2 * density).toInt()
        val badgeParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            topMargin = badgeTopMargin.coerceAtLeast(0)
        }
        iconContainer!!.addView(badgeView, badgeParams)

        iconContainer!!.addView(comboOverlay, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))

        comboButton!!.setOnClickListener {
            if (isComboActive) {
                triggerCombo()
            }
        }
    }

    override fun bindGift(gift: Gift) {
        ImageLoader.load(itemView.context, imageGift, gift.iconURL, 0)
        textGiftName.text = gift.name
        textGiftPrice.text = gift.coins.toString()
        resetComboState()
    }

    override fun setSelected(selected: Boolean) {
        super.setSelected(selected)

        val backgroundRes = if (selected) {
            R.drawable.gift_selected_bg
        } else {
            R.drawable.gift_normal_bg
        }
        layoutGift.setBackgroundResource(backgroundRes)

        if (!selected) {
            resetComboState()
        }
    }

    override fun performSendAction() {
        triggerCombo()
        updateComboVisibility()
    }

    override fun handleHitWhenSelected() {
        triggerCombo()
        if (isComboActive) {
            animateComboButton(0.9f)
            comboHandler.postDelayed({ animateComboButton(1.0f) }, 100)
        }
    }

    fun triggerComboAction() {
        if (itemView.isSelected) {
            triggerCombo()
            if (isComboActive) {
                animateComboButton(0.9f)
                comboHandler.postDelayed({ animateComboButton(1.0f) }, 100)
            }
        }
    }

    fun resetComboStateForReuse() {
        resetComboState()
    }

    private fun triggerCombo() {
        val g = gift ?: return

        val now = System.currentTimeMillis()
        if (now - lastComboTriggerTime < COMBO_THROTTLE_INTERVAL_MS) {
            return
        }
        lastComboTriggerTime = now

        if (!isComboActive) {
            isComboActive = true
            ensureComboUI()
            updateComboVisibility()
        }

        comboCount++

        listener?.onSendGift(this, g, 1)

        badgeView?.apply {
            text = "x$comboCount"
            visibility = View.VISIBLE
            clearAnimation()
            val startScale = if (comboCount == 1) 0.1f else 1.2f
            scaleX = startScale
            scaleY = startScale
            animate()
                .scaleX(1f).scaleY(1f)
                .setDuration(500)
                .setInterpolator(OvershootInterpolator(1.5f))
                .start()
        }

        startComboCountdown()
    }

    private fun updateComboVisibility() {
        if (isComboActive) {
            imageGift.visibility = View.INVISIBLE
            layoutGift.setBackgroundResource(0)
            comboOverlay?.visibility = View.VISIBLE
        } else {
            imageGift.visibility = View.VISIBLE
            if (itemView.isSelected) {
                layoutGift.setBackgroundResource(R.drawable.gift_selected_bg)
            }
            comboOverlay?.visibility = View.GONE
            badgeView?.visibility = View.GONE
        }
    }

    private fun animateComboButton(scale: Float) {
        comboButton?.animate()
            ?.scaleX(scale)?.scaleY(scale)
            ?.setDuration(100)
            ?.setInterpolator(AccelerateDecelerateInterpolator())
            ?.start()
    }

    private fun startComboCountdown() {
        comboResetRunnable?.let { comboHandler.removeCallbacks(it) }
        progressView?.startCountdown(configuration.comboDuration)
        comboResetRunnable = Runnable { resetComboState() }
        comboHandler.postDelayed(comboResetRunnable!!, configuration.comboDuration)
    }

    private fun resetComboState() {
        isComboActive = false
        comboCount = 0
        lastComboTriggerTime = 0L
        comboResetRunnable?.let { comboHandler.removeCallbacks(it) }
        comboResetRunnable = null
        progressView?.stopCountdown()
        updateComboVisibility()
    }

    override fun prepareForReuse() {
        super.prepareForReuse()
        resetComboState()
    }

    private class ComboProgressView(context: Context, private val primaryColor: Int) : View(context) {
        private val density = context.resources.displayMetrics.density
        private val strokeWidthPx = 3f * density

        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = strokeWidthPx
            strokeCap = Paint.Cap.ROUND
            color = primaryColor
        }
        private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = strokeWidthPx
            color = android.graphics.Color.argb(
                (255 * 0.3f).toInt(),
                android.graphics.Color.red(primaryColor),
                android.graphics.Color.green(primaryColor),
                android.graphics.Color.blue(primaryColor)
            )
        }
        private val rectF = RectF()
        private var progress = 1f
        private var animator: ValueAnimator? = null

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val cx = width / 2f
            val cy = height / 2f
            val r = (minOf(width, height) - paint.strokeWidth) / 2f
            rectF.set(cx - r, cy - r, cx + r, cy + r)
            canvas.drawCircle(cx, cy, r, bgPaint)
            canvas.drawArc(rectF, -90f, -360f * progress, false, paint)
        }

        fun startCountdown(duration: Long) {
            animator?.cancel()
            animator = ValueAnimator.ofFloat(1f, 0f).apply {
                this.duration = duration
                interpolator = android.view.animation.LinearInterpolator()
                addUpdateListener {
                    progress = it.animatedValue as Float
                    invalidate()
                }
            }
            animator?.start()
        }

        fun stopCountdown() {
            animator?.cancel()
            progress = 1f
            invalidate()
        }
    }

    companion object {
        const val COMBO_THROTTLE_INTERVAL_MS = 200L
        fun create(parent: ViewGroup): GiftComboCell {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.gift_layout_panel_recycle_item, parent, false)
            return GiftComboCell(view)
        }
    }
}
