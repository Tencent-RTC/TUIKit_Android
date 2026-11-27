package io.trtc.tuikit.chat.uikit.components.imageviewer.ui
import android.content.Context
import android.widget.FrameLayout
import android.widget.ImageView
import com.bumptech.glide.Glide
import io.trtc.tuikit.chat.uikit.components.imageviewer.ImageElement
import io.trtc.tuikit.chat.uikit.components.imageviewer.ui.photoview.PhotoView
import io.trtc.tuikit.atomicx.theme.ThemeStore

class ZoomableImagePageView(context: Context) : FrameLayout(context) {

    private val photoView = PhotoView(context).apply {
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        scaleType = ImageView.ScaleType.FIT_CENTER
        setAllowParentInterceptOnEdge(true)
        setScaleLevels(MIN_SCALE, MID_SCALE, MAX_SCALE)
    }

    init {
        layoutDirection = LAYOUT_DIRECTION_LOCALE
        addView(photoView)
    }

    fun bind(item: ImageElement, onTap: () -> Unit) {
        val colors = ThemeStore.shared(context).themeState.value.currentTheme.tokens.color
        setBackgroundColor(colors.bgColorMask)
        photoView.setOnPhotoTapListener { _, _, _ -> onTap() }
        Glide.with(context)
            .load(item.data)
            .into(photoView)
        photoView.post {
            photoView.setScale(MIN_SCALE, false)
        }
    }

    companion object {
        private const val MIN_SCALE = 1.0f
        private const val MID_SCALE = 1.75f
        private const val MAX_SCALE = 3.0f
    }
}
