package com.trtc.uikit.livekit.common.theme.imageloader

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.graphics.Outline
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.ImageView
import androidx.annotation.DrawableRes
import com.bumptech.glide.Glide
import com.bumptech.glide.RequestBuilder
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.request.RequestOptions

object ImageLoader {
    private const val BLUR_RADIUS = 80f

    @JvmStatic
    fun applyBlur(target: ImageView?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            target?.setRenderEffect(
                RenderEffect.createBlurEffect(BLUR_RADIUS, BLUR_RADIUS, Shader.TileMode.MIRROR)
            )
        }
    }

    @JvmStatic
    fun load(context: Context, target: ImageView?, source: Any?, placeImage: Int) {
        val imageOptions = ImageOptions.Builder().setPlaceImage(placeImage).build()
        load(context, target, source, imageOptions)
    }

    @JvmStatic
    fun load(context: Context, target: ImageView?, source: Any?, config: ImageOptions) {
        if (target == null) {
            return
        }
        if (source == null) {
            val image = if (config.placeImage != 0) config.placeImage else config.errorImage
            target.setImageResource(image)
            return
        }

        loadImageView(context, target, source, config)
    }

    @JvmStatic
    fun loadAvatar(target: ImageView?, source: String?, @DrawableRes placeImage: Int) {
        if (target == null) {
            return
        }
        target.scaleType = ImageView.ScaleType.CENTER_CROP
        target.clipToOutline = true
        target.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, minOf(view.width, view.height) / 2f)
            }
        }
        val url = source?.trim().orEmpty()
        val context = target.context
        if (url.isEmpty()) {
            clear(context, target)
            if (placeImage == 0) {
                target.setImageDrawable(null)
            } else {
                target.setImageResource(placeImage)
            }
            return
        }
        val options = ImageOptions.Builder().setPlaceImage(placeImage).setErrorImage(placeImage).build()
        load(context, target, url, options)
    }

    @JvmStatic
    fun clear(context: Context, target: ImageView?) {
        if (target != null) {
            Glide.with(context.applicationContext).clear(target)
        }
    }

    @SuppressLint("ObsoleteSdkInt")
    private fun loadImageView(
        context: Context, target: ImageView, source: Any, config: ImageOptions
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            if (context is Activity) {
                if (context.isFinishing || context.isDestroyed) {
                    return
                }
            }
        }
        val builder = if (config.isGif) {
            Glide.with(context.applicationContext).asGif().load(source)
        } else {
            Glide.with(context.applicationContext).load(source)
        }
        setBuilderOptions(builder, config)
        builder.into(target)
    }

    @SuppressLint("CheckResult")
    private fun setBuilderOptions(builder: RequestBuilder<*>, config: ImageOptions) {
        val options = RequestOptions()
        options.transform(CenterCrop())
        if (config.placeImage != 0) {
            options.placeholder(config.placeImage)
        }
        if (config.errorImage != 0) {
            options.error(config.errorImage)
        }
        options.diskCacheStrategy(if (config.skipDiskCache) DiskCacheStrategy.NONE else DiskCacheStrategy.ALL)
        options.skipMemoryCache(config.skipMemoryCache)
        builder.apply(options)
    }
}
