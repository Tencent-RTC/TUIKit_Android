package com.tencent.uikit.app.common.widget.gatherimage

import android.content.Context
import android.content.res.TypedArray
import android.graphics.Color
import android.util.AttributeSet
import com.tencent.uikit.app.R

class SynthesizedImageView : ShadeImageView {
    /**
     * Group Chat Avatar Synthesizer
     */
    private lateinit var teamHeadSynthesizer: TeamHeadSynthesizer
    private var imageSize = 100
    private var synthesizedBg = Color.parseColor("#cfd3d8")
    private var defaultImageResId = 0
    private var imageGap = 6

    constructor(context: Context) : super(context) {
        init(context)
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        initAttrs(attrs)
        init(context)
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        initAttrs(attrs)
        init(context)
    }

    private fun initAttrs(attributeSet: AttributeSet?) {
        val ta: TypedArray? = context.obtainStyledAttributes(attributeSet, R.styleable.SynthesizedImageView)
        if (ta != null) {
            synthesizedBg = ta.getColor(R.styleable.SynthesizedImageView_synthesized_image_bg, synthesizedBg)
            defaultImageResId = ta.getResourceId(R.styleable.SynthesizedImageView_synthesized_default_image, defaultImageResId)
            imageSize = ta.getDimensionPixelSize(R.styleable.SynthesizedImageView_synthesized_image_size, imageSize)
            imageGap = ta.getDimensionPixelSize(R.styleable.SynthesizedImageView_synthesized_image_gap, imageGap)
            ta.recycle()
        }
    }

    private fun init(context: Context) {
        teamHeadSynthesizer = TeamHeadSynthesizer(context, this)
        teamHeadSynthesizer.setMaxWidthHeight(imageSize, imageSize)
        teamHeadSynthesizer.setDefaultImage(defaultImageResId)
        teamHeadSynthesizer.setBgColor(synthesizedBg)
        teamHeadSynthesizer.setGap(imageGap)
    }

    fun displayImage(imageUrls: List<Any>?): SynthesizedImageView {
        teamHeadSynthesizer.getMultiImageData().imageUrls=(imageUrls?.toMutableList())
        return this
    }

    fun defaultImage(defaultImage: Int): SynthesizedImageView {
        teamHeadSynthesizer.setDefaultImage(defaultImage)
        return this
    }

    fun synthesizedWidthHeight(maxWidth: Int, maxHeight: Int): SynthesizedImageView {
        teamHeadSynthesizer.setMaxWidthHeight(maxWidth, maxHeight)
        return this
    }

    fun setImageId(id: String) {
        teamHeadSynthesizer.setImageId(id)
    }

    fun load(imageId: String) {
        teamHeadSynthesizer.load(imageId)
    }

    fun clear() {
        teamHeadSynthesizer.clearImage()
    }
}