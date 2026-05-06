package com.trtc.uikit.livekit.features.anchorview.view.battle.widgets

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import android.widget.RelativeLayout
import android.widget.TextView
import io.trtc.tuikit.atomicx.common.util.ScreenUtil
import com.trtc.uikit.livekit.R
import kotlin.math.max
import kotlin.math.min

@SuppressLint("ViewConstructor")
class SingleBattleScoreView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    private val lineDivider: View
    private val imageDivider: View
    private val textScoreLeft: TextView
    private val textScoreRight: TextView

    init {
        inflate(context, R.layout.livekit_battle_single_battle_score_view, this)
        lineDivider = findViewById(R.id.v_divider)
        imageDivider = findViewById(R.id.iv_divider)
        textScoreLeft = findViewById(R.id.tv_score_left)
        textScoreRight = findViewById(R.id.tv_score_right)
        textScoreLeft.text = String.format("%d", 0)
        textScoreRight.text = String.format("%d", 0)
    }

    fun updateScores(scoreLeft: Int, scoreRight: Int) {
        if (scoreLeft + scoreRight < 0 || width == 0) {
            return
        }
        
        textScoreLeft.text = scoreLeft.toString()
        textScoreRight.text = scoreRight.toString()
        
        val textWidthLeft = textScoreLeft.paint.measureText(textScoreLeft.text.toString()) +
                2 * textScoreLeft.paddingLeft
        val textWidthRight = textScoreRight.paint.measureText(textScoreRight.text.toString()) +
                2 * textScoreRight.paddingLeft
        
        val width = width
        val ratio = if (scoreLeft + scoreRight == 0) 0.5f else 1.0f * scoreLeft / (scoreLeft + scoreRight)
        var dividerX = (width * ratio).toInt()
        dividerX = max(textWidthLeft, dividerX.toFloat()).toInt()
        dividerX = min(width - textWidthRight, dividerX.toFloat()).toInt()
        updateDivider(dividerX)
    }

    private fun updateDivider(dividerX: Int) {
        val vDividerParams = lineDivider.layoutParams as RelativeLayout.LayoutParams
        vDividerParams.removeRule(RelativeLayout.CENTER_HORIZONTAL)
        vDividerParams.leftMargin = dividerX
        lineDivider.layoutParams = vDividerParams

        val imageDividerParams = imageDivider.layoutParams as RelativeLayout.LayoutParams
        imageDividerParams.removeRule(RelativeLayout.CENTER_HORIZONTAL)
        imageDividerParams.leftMargin = dividerX - imageDividerParams.width / 2 - ScreenUtil.dip2px(1f)
        imageDivider.layoutParams = imageDividerParams

        invalidate()
    }
}