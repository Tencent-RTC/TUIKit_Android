package com.tencent.qcloud.tuikit.tuicallkit.call.callview.bottom

import android.content.Context
import android.graphics.Color
import android.graphics.Outline
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.tencent.qcloud.tuikit.tuicallkit.R
import com.tencent.qcloud.tuikit.tuicallkit.common.CallPanelHeader
import com.tencent.qcloud.tuikit.tuicallkit.common.dp
import io.trtc.tuikit.atomicxcore.api.call.CallStore

internal class BackgroundPanel @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr) {

    private val offOption = OptionView(context, R.drawable.tuicallkit_icon_ban, context.getString(R.string.tuicallkit_panel_off))
    private val blurOption = OptionView(context, R.drawable.tuicallkit_icon_person, context.getString(R.string.tuicallkit_panel_blur), gradientThumb = true)

    init {
        orientation = VERTICAL
        addView(CallPanelHeader.create(context, context.getString(R.string.tuicallkit_panel_background_title)))

        offOption.onTap = { apply(level = 0) }
        blurOption.onTap = { apply(level = 2) }
        val optionsRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            addView(offOption)
            val blurParams = LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
            blurParams.marginStart = 11.dp(context)
            addView(blurOption, blurParams)
        }
        val rowParams = LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
        rowParams.topMargin = 18.dp(context)
        rowParams.marginStart = 20.dp(context)
        rowParams.bottomMargin = 12.dp(context)
        addView(optionsRow, rowParams)
        refreshSelection()
    }

    private fun apply(level: Int) {
        val payload = "{\"api\":\"setBlurBackground\",\"params\":{\"level\":$level}}"
        CallStore.shared.callExperimentalAPI(payload)
        currentLevel = level
        refreshSelection()
    }

    private fun refreshSelection() {
        offOption.isOptionSelected = currentLevel == 0
        blurOption.isOptionSelected = currentLevel != 0
    }

    private class OptionView(
        context: Context,
        iconRes: Int,
        text: String,
        gradientThumb: Boolean = false,
    ) : FrameLayout(context) {

        var onTap: (() -> Unit)? = null
        var isOptionSelected = false
            set(value) {
                field = value
                applySelection()
            }

        private val thumb: FrameLayout
        private val nameLabel: TextView

        init {
            val iconView = ImageView(context).apply {
                setImageResource(iconRes)
                setColorFilter(ContextCompat.getColor(context, android.R.color.white))
                scaleType = ImageView.ScaleType.FIT_CENTER
            }
            thumb = FrameLayout(context).apply {
                background = GradientDrawable().apply {
                    cornerRadius = 10f.dp(context).toFloat()
                    if (gradientThumb) {
                        orientation = GradientDrawable.Orientation.TL_BR
                        colors = intArrayOf(0xFF76816F.toInt(), 0xFF4C574B.toInt())
                    } else {
                        setColor(ContextCompat.getColor(context, R.color.tuicallkit_hairline_on_dark))
                    }
                    setStroke(2.dp(context), Color.TRANSPARENT)
                }
                addView(iconView, LayoutParams(26.dp(context), 26.dp(context), Gravity.CENTER))
            }

            nameLabel = TextView(context).apply {
                this.text = text
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                setTextColor(ContextCompat.getColor(context, R.color.tuicallkit_sheet_text4))
                gravity = Gravity.CENTER
            }

            val column = LinearLayout(context).apply {
                orientation = VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                addView(thumb, LinearLayout.LayoutParams(80.dp(context), 60.dp(context)))
                val labelParams = LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
                labelParams.topMargin = 7.dp(context)
                addView(nameLabel, labelParams)
            }
            addView(column, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))

            setOnClickListener { onTap?.invoke() }
        }

        private fun applySelection() {
            val borderColor = if (isOptionSelected) {
                ContextCompat.getColor(context, R.color.tuicallkit_call_green)
            } else {
                Color.TRANSPARENT
            }
            (thumb.background as? GradientDrawable)?.setStroke(2.dp(context), borderColor)
            nameLabel.setTextColor(
                ContextCompat.getColor(
                    context,
                    if (isOptionSelected) R.color.tuicallkit_call_green else R.color.tuicallkit_sheet_text4
                )
            )
        }
    }

    companion object {
        private var currentLevel = 0
    }
}
