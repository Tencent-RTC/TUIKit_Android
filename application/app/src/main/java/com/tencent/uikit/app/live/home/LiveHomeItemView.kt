package com.tencent.uikit.app.live.home

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import com.bumptech.glide.Glide
import com.tencent.uikit.app.R
import io.trtc.tuikit.atomicx.widget.basicwidget.avatar.AtomicAvatar
import io.trtc.tuikit.atomicxcore.api.live.LiveInfo

class LiveHomeItemView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr) {
    private lateinit var cover: ImageView
    private val avatar: AtomicAvatar
    private var data: LiveInfo? = null
    private var coverSource: String? = null

    init {
        inflate(context, R.layout.app_live_home_item, this)
        setBackgroundResource(R.drawable.home_card_background)
        clipToOutline = true
        cover = findViewById(R.id.home_card_cover)
        findViewById<View>(R.id.home_card_gradient).background =
            HomeCoverGradient(floatArrayOf(.25f, 0f, 0f, .68f), floatArrayOf(0f, .35f, .6f, 1f))
        avatar = findViewById(R.id.home_card_avatar)
    }

    fun updateData(info: LiveInfo) {
        data = info
        findViewById<TextView>(R.id.home_card_title).text =
            info.liveName.ifBlank { context.getString(R.string.home_untitled) }
        val owner = info.liveOwner.userName.ifBlank { context.getString(R.string.home_unknown_host) }
        findViewById<TextView>(R.id.home_card_owner).text = owner
        findViewById<TextView>(R.id.home_card_viewers).text = info.totalViewerCount.toString()
        val avatarURL = info.liveOwner.avatarURL.trim()
        if (avatar.tag != avatarURL + owner) {
            avatar.tag = avatarURL + owner
            avatar.setContent(
                if (avatarURL.isBlank()) AtomicAvatar.AvatarContent.Text(owner)
                else AtomicAvatar.AvatarContent.URL(avatarURL, R.drawable.home_avatar_placeholder)
            )
        }
        contentDescription =
            context.getString(R.string.home_card_description, info.liveName, owner, info.totalViewerCount.toString())
        loadCover()
    }

    fun coverDrawable() = cover.drawable
    private fun loadCover() {
        val url = data?.coverURL?.trim().orEmpty().takeIf { it.startsWith("https://") || it.startsWith("http://") }
        if (coverSource == url) return
        coverSource = url
        findViewById<View>(R.id.home_card_placeholder).visibility = VISIBLE
        Glide.with(this).load(url)
            .listener(object : com.bumptech.glide.request.RequestListener<android.graphics.drawable.Drawable> {
                override fun onLoadFailed(
                    e: com.bumptech.glide.load.engine.GlideException?, model: Any?,
                    target: com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable>,
                    isFirstResource: Boolean
                ) = false

                override fun onResourceReady(
                    resource: android.graphics.drawable.Drawable, model: Any,
                    target: com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable>?,
                    dataSource: com.bumptech.glide.load.DataSource, isFirstResource: Boolean
                ): Boolean {
                    if (coverSource == url) findViewById<View>(R.id.home_card_placeholder).visibility = GONE
                    return false
                }
            }).placeholder(R.drawable.home_card_background).error(R.drawable.home_card_background).centerCrop()
            .into(cover)
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (!::cover.isInitialized) return
        if (isShown) loadCover() else {
            Glide.with(this).clear(cover); coverSource = null
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow(); loadCover()
    }

    override fun onDetachedFromWindow() {
        Glide.with(this).clear(cover)
        coverSource = null
        super.onDetachedFromWindow()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(
            widthMeasureSpec, View.MeasureSpec.makeMeasureSpec(
                (View.MeasureSpec.getSize(widthMeasureSpec) * 1.35f).toInt(), View.MeasureSpec.EXACTLY
            )
        )
    }
}

internal class HomeCoverGradient(private val alphas: FloatArray, private val positions: FloatArray) :
    android.graphics.drawable.Drawable() {
    private val paint = android.graphics.Paint()
    override fun onBoundsChange(bounds: android.graphics.Rect) {
        paint.shader = android.graphics.LinearGradient(
            0f, bounds.top.toFloat(), 0f, bounds.bottom.coerceAtLeast(bounds.top + 1).toFloat(),
            alphas.map { android.graphics.Color.argb((255 * it).toInt(), 0, 0, 0) }.toIntArray(), positions,
            android.graphics.Shader.TileMode.CLAMP
        )
    }

    override fun draw(canvas: android.graphics.Canvas) {
        canvas.drawRect(bounds, paint)
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha; invalidateSelf()
    }

    override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) {
        paint.colorFilter = colorFilter; invalidateSelf()
    }

    @Deprecated("Drawable opacity is no longer used")
    override fun getOpacity() = android.graphics.PixelFormat.TRANSLUCENT
}
