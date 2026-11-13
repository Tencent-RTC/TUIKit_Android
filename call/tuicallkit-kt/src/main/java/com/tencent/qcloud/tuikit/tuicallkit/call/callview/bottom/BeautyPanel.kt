package com.tencent.qcloud.tuikit.tuicallkit.call.callview.bottom

import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.tencent.qcloud.tuikit.tuicallkit.R
import com.tencent.qcloud.tuikit.tuicallkit.common.CallPanelHeader
import com.tencent.qcloud.tuikit.tuicallkit.common.dp
import io.trtc.tuikit.atomicxcore.api.device.BaseBeautyStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

internal class BeautyPanel @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr) {

    private val whitenessRow: SliderRow
    private val smoothRow: SliderRow
    private val ruddyRow: SliderRow
    private var subscribeJob: Job? = null

    init {
        orientation = VERTICAL
        val state = BaseBeautyStore.shared().baseBeautyState
        addView(CallPanelHeader.create(context, context.getString(R.string.tuicallkit_panel_beauty_title)))

        whitenessRow = SliderRow(context, context.getString(R.string.tuicallkit_beauty_whiteness), state.whitenessLevel.value)
        smoothRow = SliderRow(context, context.getString(R.string.tuicallkit_beauty_smooth), state.smoothLevel.value)
        ruddyRow = SliderRow(context, context.getString(R.string.tuicallkit_beauty_ruddy), state.ruddyLevel.value)

        whitenessRow.onChange = { BaseBeautyStore.shared().setWhitenessLevel(it) }
        smoothRow.onChange = { BaseBeautyStore.shared().setSmoothLevel(it) }
        ruddyRow.onChange = { BaseBeautyStore.shared().setRuddyLevel(it) }

        for (row in listOf(whitenessRow, smoothRow, ruddyRow)) {
            val params = LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
            params.marginStart = 20.dp(context)
            params.marginEnd = 20.dp(context)
            params.topMargin = 18.dp(context)
            addView(row, params)
        }
        val tail = android.view.View(context)
        addView(tail, LinearLayout.LayoutParams(1, 12.dp(context)))
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        subscribeState()
    }

    override fun onDetachedFromWindow() {
        subscribeJob?.cancel()
        subscribeJob = null
        super.onDetachedFromWindow()
    }

    private fun subscribeState() {
        subscribeJob?.cancel()
        subscribeJob = CoroutineScope(Dispatchers.Main + SupervisorJob()).launch {
            val state = BaseBeautyStore.shared().baseBeautyState
            combine(state.whitenessLevel, state.smoothLevel, state.ruddyLevel) { w, s, r -> Triple(w, s, r) }
                .collect { (w, s, r) ->
                    whitenessRow.setValue(w)
                    smoothRow.setValue(s)
                    ruddyRow.setValue(r)
                }
        }
    }

    private class SliderRow(
        context: Context,
        name: String,
        value: Float,
    ) : LinearLayout(context) {

        var onChange: ((Float) -> Unit)? = null

        private val slider: SeekBar
        private val valueLabel: TextView
        private var syncing = false

        init {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL

            val iconView = ImageView(context).apply {
                setImageResource(R.drawable.tuicallkit_icon_beauty)
                setColorFilter(ContextCompat.getColor(context, android.R.color.white))
                scaleType = ImageView.ScaleType.FIT_CENTER
            }
            addView(iconView, LinearLayout.LayoutParams(22.dp(context), 22.dp(context)))

            val nameLabel = TextView(context).apply {
                text = name
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setTextColor(ContextCompat.getColor(context, R.color.tuicallkit_sheet_text4))
            }
            val nameParams = LinearLayout.LayoutParams(34.dp(context), LayoutParams.WRAP_CONTENT)
            nameParams.marginStart = 12.dp(context)
            addView(nameLabel, nameParams)

            slider = SeekBar(context).apply {
                max = 9
                progress = value.roundToInt()
                progressTintList = null
                progressBackgroundTintList = null
                progressDrawable = makeTrack(context)
                thumb = makeThumb(context)
                splitTrack = false
            }
            val sliderParams = LinearLayout.LayoutParams(0, SLIDER_HEIGHT_DP.dp(context), 1f)
            sliderParams.marginStart = 12.dp(context)
            addView(slider, sliderParams)

            valueLabel = TextView(context).apply {
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setTextColor(ContextCompat.getColor(context, android.R.color.white))
                gravity = Gravity.END
                fontFeatureSettings = "tnum"
            }
            val valueParams = LinearLayout.LayoutParams(28.dp(context), LayoutParams.WRAP_CONTENT)
            valueParams.marginStart = 12.dp(context)
            addView(valueLabel, valueParams)

            slider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                    valueLabel.text = progress.toString()
                    if (fromUser && !syncing) {
                        onChange?.invoke(progress.toFloat())
                    }
                }

                override fun onStartTrackingTouch(seekBar: SeekBar) {}
                override fun onStopTrackingTouch(seekBar: SeekBar) {}
            })
            setValue(value)
        }

        fun setValue(value: Float) {
            val rounded = value.roundToInt().coerceIn(0, 9)
            syncing = true
            slider.progress = rounded
            syncing = false
            valueLabel.text = rounded.toString()
        }

        private fun makeTrack(context: Context): Drawable {
            val trackHeight = TRACK_HEIGHT_DP.dp(context).toFloat()
            val cornerRadius = 2f.dp(context).toFloat()
            return object : Drawable() {
                private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x2EFFFFFF }
                private val rect = RectF()

                override fun draw(canvas: Canvas) {
                    val half = trackHeight / 2f
                    val centerY = bounds.exactCenterY()
                    rect.set(bounds.left.toFloat(), centerY - half, bounds.right.toFloat(), centerY + half)
                    canvas.drawRoundRect(rect, cornerRadius, cornerRadius, paint)
                }

                override fun setAlpha(alpha: Int) {
                    paint.alpha = alpha
                }

                override fun setColorFilter(colorFilter: ColorFilter?) {
                    paint.colorFilter = colorFilter
                }

                @Deprecated("Deprecated in Java")
                override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
            }
        }

        private fun makeThumb(context: Context): android.graphics.drawable.LayerDrawable {
            val shadow = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0x59000000)
                setSize(SLIDER_HEIGHT_DP.dp(context), SLIDER_HEIGHT_DP.dp(context))
            }
            val knob = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(ContextCompat.getColor(context, android.R.color.white))
            }
            return android.graphics.drawable.LayerDrawable(arrayOf(shadow, knob)).apply {
                val shadowInset = 3.dp(context)
                setLayerInset(0, shadowInset, shadowInset + 1.dp(context), shadowInset, shadowInset - 1.dp(context))
                val inset = 4.dp(context)
                setLayerInset(1, inset, inset, inset, inset)
            }
        }

        private companion object {
            const val SLIDER_HEIGHT_DP = 22
            const val TRACK_HEIGHT_DP = 4
        }
    }
}
