package com.trtc.uikit.livekit.voicelounge.settings.theme

import android.content.Context
import android.graphics.drawable.Drawable
import android.widget.FrameLayout
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.trtc.uikit.livekit.voicelounge.MATCH
import com.trtc.uikit.livekit.voicelounge.frameParams

internal class VoiceLoungeBackgroundView(context: Context) : FrameLayout(context) {
    private val imageView = ImageView(context).apply {
        scaleType = ImageView.ScaleType.CENTER_CROP
    }
    private var currentBackgroundID: String? = null
    private var currentGifRes: Int? = null

    init {
        clipChildren = true
        addView(imageView, frameParams(MATCH, MATCH))
    }

    fun show(background: VoiceLoungeBackground) {
        if (background.id == currentBackgroundID) return
        currentBackgroundID = background.id
        currentGifRes = background.gifRes
        loadCurrent(background.imageRes)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        val id = currentBackgroundID ?: return
        val background = VoiceLoungeBackground.background(id) ?: return
        if (background.gifRes != null && imageView.drawable !is com.bumptech.glide.load.resource.gif.GifDrawable) {
            loadCurrent(background.imageRes)
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        (imageView.drawable as? com.bumptech.glide.load.resource.gif.GifDrawable)?.stop()
    }

    private fun loadCurrent(staticRes: Int) {
        val gifRes = currentGifRes
        if (!isValidContext()) {
            imageView.setImageResource(staticRes)
            return
        }
        Glide.with(imageView).clear(imageView)
        imageView.setImageResource(staticRes)
        if (gifRes == null) return
        val placeholder: Drawable? = imageView.drawable
        Glide.with(imageView).asGif().load(gifRes).placeholder(placeholder)
            .diskCacheStrategy(DiskCacheStrategy.RESOURCE).centerCrop().into(imageView)
    }

    private fun isValidContext(): Boolean {
        val activity = context as? android.app.Activity ?: return true
        return !activity.isFinishing && !activity.isDestroyed
    }
}
