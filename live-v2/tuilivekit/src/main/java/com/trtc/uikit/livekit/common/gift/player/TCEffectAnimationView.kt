package com.trtc.uikit.livekit.common.gift.player

import android.content.Context
import android.os.Bundle
import android.util.AttributeSet
import android.util.Log
import com.tencent.tcmediax.tceffectplayer.api.TCEffectAnimView
import com.tencent.tcmediax.tceffectplayer.api.TCEffectPlayerConstant
import java.lang.Boolean
import kotlin.Int
import kotlin.String

class TCEffectAnimationView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    AnimationView(context, attrs) {
    private val mEffectAnimView: TCEffectAnimView = TCEffectAnimView(context)

    init {
        Log.e("TCEffectAnimationView", "init")
        mEffectAnimView.setPlayListener(object : TCEffectAnimView.IAnimPlayListener {
            override fun onPlayStart() {

            }

            override fun onPlayEnd() {
                Log.e("TCEffectAnimationView", "onPlayEnd")
                callback?.onFinished(0)
            }

            override fun onPlayError(p0: Int) {
                Log.e("TCEffectAnimationView", "onPlayError: $p0")
                callback?.onFinished(p0)
            }

            override fun onPlayEvent(p0: Int, p1: Bundle?) {
                Log.e("TCEffectAnimationView", "onPlayEvent: $p0")
            }
        })

        val params = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        addView(mEffectAnimView, params)
    }

    override fun playAnimation(playUrl: String) {
        Log.e("TCEffectAnimationView", "playAnimation: $playUrl")
        mEffectAnimView.setScaleType(TCEffectPlayerConstant.ScaleType.CENTER_CROP)
        mEffectAnimView.startPlay(playUrl)
    }

    override fun stopPlay() {
        Log.e("TCEffectAnimationView", "stopPlay")
        mEffectAnimView.stopPlay(Boolean.TRUE)
    }
}