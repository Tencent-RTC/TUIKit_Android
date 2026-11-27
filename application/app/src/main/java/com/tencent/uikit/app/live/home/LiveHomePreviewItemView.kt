package com.tencent.uikit.app.live.home

import android.content.Context
import android.content.Intent
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import com.bumptech.glide.Glide
import com.tencent.uikit.app.R
import com.trtc.uikit.livekit.player.floating.FloatingPlayerManager
import com.trtc.uikit.livekit.player.floating.PlayerActivity
import io.trtc.tuikit.atomicx.common.event.EventManager
import io.trtc.tuikit.atomicxcore.api.live.LiveInfo

class LiveHomePreviewItemView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {
    val playerContainer: FrameLayout
    private lateinit var cover: ImageView
    private val overlay: View
    private var source = ""
    private var loadedSource: String? = null
    private var id = ""
    private var owner = ""

    init {
        inflate(context, R.layout.app_live_home_preview_item, this)
        clipChildren = true
        findViewById<View>(R.id.home_preview_top_gradient).background =
            HomeCoverGradient(floatArrayOf(.45f, .38f, .23f, 0f), floatArrayOf(0f, .32f, .72f, 1f))
        findViewById<View>(R.id.home_preview_bottom_gradient).background =
            HomeCoverGradient(floatArrayOf(0f, .14f, .22f, .31f, .44f), floatArrayOf(0f, .16f, .44f, .77f, 1f))
        cover = findViewById(R.id.home_preview_cover)
        playerContainer = findViewById(R.id.home_preview_player)
        overlay = findViewById(R.id.home_preview_overlay)
        listOf(overlay, findViewById(R.id.home_preview_enter)).forEach { button ->
            button.setOnClickListener {
                FloatingPlayerManager.releaseSession(false)
                val intent = Intent(context, PlayerActivity::class.java)
                intent.putExtra("liveID", id)
                context.startActivity(intent)
            }
        }
    }

    fun bind(info: LiveInfo, ownerID: String) {
        id = info.liveID
        owner = ownerID
        findViewById<TextView>(R.id.home_preview_owner).text = context.getString(
            R.string.home_preview_owner,
            info.liveOwner.userName.ifBlank { context.getString(R.string.home_unknown_host) })
        findViewById<TextView>(R.id.home_preview_title).text =
            info.liveName.ifBlank { context.getString(R.string.home_untitled) }
        findViewById<TextView>(R.id.home_preview_viewers).text =
            context.getString(R.string.home_preview_viewers, info.totalViewerCount.toString())
        val next = info.coverURL.trim()
        if (source != next) {
            source = next
            loadCover()
        }
    }

    fun updatePresentation(preview: Boolean, joining: Boolean, ended: Boolean, playerHidden: Boolean) {
        overlay.visibility = if (preview && !ended) VISIBLE else GONE
        playerContainer.visibility = if (playerHidden && !ended) INVISIBLE else VISIBLE
        overlay.isEnabled = !joining && !ended
        findViewById<TextView>(R.id.home_preview_enter).apply {
            isEnabled = !joining && !ended
            setText(if (ended) R.string.home_ended else R.string.home_enter)
        }
    }

    fun seedCover(drawable: android.graphics.drawable.Drawable?) {
        if (drawable != null) cover.setImageDrawable(drawable)
    }

    fun updateCanvas() {
        val fullHeight = rootView.height.coerceAtLeast(height)
        listOf(cover, playerContainer).forEach { view ->
            if (view.layoutParams.height != fullHeight) view.layoutParams =
                view.layoutParams.apply { height = fullHeight }
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh); updateCanvas()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (!::cover.isInitialized) return
        if (isShown) loadCover() else {
            Glide.with(this).clear(cover); loadedSource = null
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow(); loadCover(); post { updateCanvas() }
    }

    override fun onDetachedFromWindow() {
        Glide.with(this).clear(cover); loadedSource = null; super.onDetachedFromWindow()
    }

    private fun loadCover() {
        if (loadedSource == source) return
        loadedSource = source
        Glide.with(this).load(source.takeIf { it.startsWith("https://") || it.startsWith("http://") })
            .placeholder(R.drawable.home_card_background).error(R.drawable.home_card_background).centerCrop()
            .into(cover)
    }
}
