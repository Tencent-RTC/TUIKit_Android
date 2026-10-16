package com.trtc.uikit.livekit.common.gift.player

import android.content.Context
import android.text.TextUtils
import android.util.AttributeSet
import android.util.Log
import com.trtc.uikit.livekit.common.gift.player.svgaplayer.SVGACallback
import com.trtc.uikit.livekit.common.gift.player.svgaplayer.SVGAImageView
import com.trtc.uikit.livekit.common.gift.player.svgaplayer.SVGAParser
import com.trtc.uikit.livekit.common.gift.player.svgaplayer.SVGAParser.Companion.shareParser
import com.trtc.uikit.livekit.common.gift.player.svgaplayer.SVGAParser.ParseCompletion
import com.trtc.uikit.livekit.common.gift.player.svgaplayer.SVGAVideoEntity
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.InputStream

class SVGAAnimationView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    AnimationView(context, attrs), SVGACallback {
    private val svgaParser: SVGAParser
    private val imageView: SVGAImageView = SVGAImageView(context)

    init {
        val params = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        addView(imageView, params)
        imageView.loops = 1
        imageView.callback = this
        svgaParser = shareParser()
        svgaParser.init(context)
    }

    override fun playAnimation(playUrl: String) {
        val stream = openInputStream(playUrl)
        if (stream == null) {
            Log.e(TAG, "InputStream is null")
            callback?.onFinished(-1)
            return
        }
        try {
            svgaParser.decodeFromInputStream(stream, "", object : ParseCompletion {
                override fun onComplete(videoItem: SVGAVideoEntity) {
                    try {
                        imageView.setVisibility(VISIBLE)
                        imageView.setVideoItem(videoItem)
                        imageView.startAnimation()
                    } catch (t: Throwable) {
                        Log.e(TAG, "startAnimation error: ${t.message}")
                        callback?.onFinished(-1)
                    }
                }

                override fun onError() {
                    Log.e(TAG, "decodeFromURL onError")
                    callback?.onFinished(-1)
                }
            }, true, null, "")
        } catch (t: Throwable) {
            Log.e(TAG, "decodeFromInputStream error: ${t.message}")
            try {
                stream.close()
            } catch (_: Throwable) {
            }
            callback?.onFinished(-1)
        }
    }

    override fun stopPlay() {
        imageView.stopAnimation(true)
    }

    override fun onFinished() {
        imageView.setVisibility(GONE)
        callback?.onFinished(0)
    }

    override fun onPause() {
    }

    override fun onRepeat() {
    }

    override fun onStep(frame: Int, percentage: Double) {
    }

    private fun openInputStream(path: String): InputStream? {
        try {
            val file = File(path)
            if (file.exists()) {
                return FileInputStream(file)
            }
        } catch (e: FileNotFoundException) {
            Log.i(TAG, " " + e.getLocalizedMessage())
        }
        return null
    }

    companion object {
        private const val TAG = "SVGAAnimationView"
    }
}