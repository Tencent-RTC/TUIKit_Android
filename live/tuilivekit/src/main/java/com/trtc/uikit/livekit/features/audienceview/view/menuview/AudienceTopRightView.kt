package com.trtc.uikit.livekit.features.audienceview.view.menuview

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import com.trtc.uikit.livekit.R
import com.trtc.uikit.livekit.component.audiencelist.AudienceListView
import com.trtc.uikit.livekit.features.audienceview.AudienceViewDefine.AudienceTopRightItem
import com.trtc.uikit.livekit.features.audienceview.store.AudienceStore
import io.trtc.tuikit.atomicx.common.util.ScreenUtil.dip2px
import io.trtc.tuikit.atomicxcore.api.live.LiveInfo

class AudienceTopRightView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr) {

    private var audienceStore: AudienceStore? = null

    val audienceListView: AudienceListView = AudienceListView(context).apply {
        layoutParams = LayoutParams(dip2px(80f), dip2px(24f)).apply {
            gravity = Gravity.CENTER_VERTICAL
        }
    }

    private val imageFloatWindow: ImageView = ImageView(context).apply {
        layoutParams = LayoutParams(dip2px(24f), dip2px(24f)).apply {
            marginStart = dip2px(6f)
            gravity = Gravity.CENTER_VERTICAL
        }
        setPadding(dip2px(2f), dip2px(2f), dip2px(2f), dip2px(2f))
        setImageResource(R.drawable.livekit_float_window)
    }

    private val imageStandardExit: ImageView = ImageView(context).apply {
        layoutParams = LayoutParams(dip2px(24f), dip2px(24f)).apply {
            marginStart = dip2px(6f)
            gravity = Gravity.CENTER_VERTICAL
        }
        setPadding(dip2px(2f), dip2px(2f), dip2px(2f), dip2px(2f))
        setImageResource(R.drawable.livekit_ic_audience_exit)
    }

    private val customWrappers = mutableListOf<View>()

   internal var onExitClick: (() -> Unit)? = null
    internal var onFloatWindowClick: (() -> Unit)? = null

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL or Gravity.END
        addView(audienceListView)
        addView(imageFloatWindow)
        addView(imageStandardExit)
    }

    internal fun init(audienceStore: AudienceStore) {
        this.audienceStore = audienceStore
        imageStandardExit.setOnClickListener { onExitClick?.invoke() }
        imageFloatWindow.setOnClickListener { onFloatWindowClick?.invoke() }
    }

    internal fun initAudienceList(liveInfo: LiveInfo) {
        audienceListView.init(liveInfo)
    }

    internal fun updateItems(items: List<AudienceTopRightItem>) {
        for (wrapper in customWrappers) {
            removeView(wrapper)
        }
        customWrappers.clear()

        audienceListView.visibility = GONE
        imageFloatWindow.visibility = GONE
        imageStandardExit.visibility = GONE

        removeAllViews()

        for (item in items) {
            when (item) {
                is AudienceTopRightItem.AudienceCount -> {
                    audienceListView.visibility = VISIBLE
                    addView(audienceListView)
                }
                is AudienceTopRightItem.FloatWindow -> {
                    imageFloatWindow.visibility = VISIBLE
                    addView(imageFloatWindow)
                }
                is AudienceTopRightItem.Close -> {
                    imageStandardExit.visibility = VISIBLE
                    addView(imageStandardExit)
                }
                is AudienceTopRightItem.Custom -> {
                    (item.view.parent as? ViewGroup)?.removeView(item.view)
                    val existingLp = item.view.layoutParams
                    val lp = if (existingLp != null) {
                        LayoutParams(existingLp.width, existingLp.height).apply {
                            marginStart = dip2px(6f)
                            gravity = Gravity.CENTER_VERTICAL
                        }
                    } else {
                        LayoutParams(dip2px(24f), dip2px(24f)).apply {
                            marginStart = dip2px(6f)
                            gravity = Gravity.CENTER_VERTICAL
                        }
                    }
                    item.view.layoutParams = lp
                    addView(item.view)
                    customWrappers.add(item.view)
                }
            }
        }
    }

    internal fun showAudienceList() {
        audienceListView.showAudienceListPanelView()
    }

    internal fun requestFloatWindow() {
        imageFloatWindow.performClick()
    }

    internal fun setScreenOrientation(isPortrait: Boolean) {
        audienceListView.setScreenOrientation(isPortrait)
    }
}
