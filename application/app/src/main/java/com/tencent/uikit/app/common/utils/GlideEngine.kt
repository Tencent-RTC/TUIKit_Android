package com.tencent.uikit.app.common.utils

import android.graphics.Bitmap
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import com.tencent.uikit.app.R
import io.trtc.tuikit.atomicxcore.impl.common.ContextProvider
import java.util.concurrent.ExecutionException

object GlideEngine {

    fun clear(imageView: ImageView) {
        val context = ContextProvider.getApplicationContext() ?: return
        Glide.with(context).clear(imageView)
    }

    fun loadUserIcon(imageView: ImageView, uri: Any?) {
        loadUserIcon(imageView, uri, 0)
    }

    fun loadUserIcon(imageView: ImageView, uri: Any?, radius: Int) {
        val context = ContextProvider.getApplicationContext() ?: return
        Glide.with(context)
            .load(uri)
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .placeholder(R.drawable.app_default_user_icon_light)
            .apply(RequestOptions().centerCrop().error(R.drawable.app_default_user_icon_light))
            .into(imageView)
    }

    @Throws(InterruptedException::class, ExecutionException::class)
    fun loadBitmap(imageUrl: Any?, targetImageSize: Int): Bitmap {
        val context = ContextProvider.getApplicationContext()
            ?: throw IllegalArgumentException("Context cannot be null")
        if (imageUrl == null) {
            throw IllegalArgumentException("Image URL cannot be null")
        }
        return Glide.with(context)
            .asBitmap()
            .load(imageUrl)
            .apply(RequestOptions().error(R.drawable.app_default_user_icon_light))
            .into(targetImageSize, targetImageSize)
            .get()
    }
}