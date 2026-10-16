package com.trtc.uikit.livekit.common.gift.player

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.View
import android.widget.ImageView

/**
 * Gift icon image view that keeps its fallback block in sync with the loaded drawable.
 *
 * Mirrors iOS `LiveGiftEntryItemView.loadGiftImage`, which sets
 * `giftFallbackView.isHidden = image != nil`. The project image loader exposes no completion
 * callback, so the loaded state is observed through [setImageDrawable], which is the entry point
 * the loader uses when it delivers a bitmap.
 */
internal class LiveGiftIconView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : ImageView(context, attrs, defStyleAttr) {

    private var fallbackView: View? = null

    fun attachFallbackView(view: View) {
        fallbackView = view
        syncFallbackVisibility()
    }

    override fun setImageDrawable(drawable: Drawable?) {
        super.setImageDrawable(drawable)
        syncFallbackVisibility()
    }

    override fun setImageResource(resId: Int) {
        super.setImageResource(resId)
        syncFallbackVisibility()
    }

    private fun syncFallbackVisibility() {
        fallbackView?.visibility = if (drawable == null) VISIBLE else GONE
    }
}
