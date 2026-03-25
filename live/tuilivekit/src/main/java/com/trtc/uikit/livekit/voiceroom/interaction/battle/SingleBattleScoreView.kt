package com.trtc.uikit.livekit.voiceroom.interaction.battle

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
        textScoreLeft.text = "0"
        textScoreRight.text = "0"
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

        val ratio = if (scoreLeft + scoreRight == 0) 0.5f else scoreLeft.toFloat() / (scoreLeft + scoreRight)
        var dividerX = (width * ratio).toInt()
        dividerX = max(textWidthLeft, dividerX.toFloat()).toInt()
        dividerX = min((width - textWidthRight), dividerX.toFloat()).toInt()

        updateDivider(dividerX)
    }

    private fun updateDivider(dividerX: Int) {
        (lineDivider.layoutParams as RelativeLayout.LayoutParams).apply {
            removeRule(RelativeLayout.CENTER_HORIZONTAL)
            leftMargin = dividerX
        }.also { lineDivider.layoutParams = it }

        (imageDivider.layoutParams as RelativeLayout.LayoutParams).apply {
            removeRule(RelativeLayout.CENTER_HORIZONTAL)
            leftMargin = dividerX - width / 2 - ScreenUtil.dip2px(1f)
        }.also { imageDivider.layoutParams = it }

        invalidate()
    }
}