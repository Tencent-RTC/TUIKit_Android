package com.trtc.uikit.livekit.features.anchorview.view.menuview

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import com.trtc.uikit.livekit.R
import com.trtc.uikit.livekit.component.audiencelist.AudienceListView
import com.trtc.uikit.livekit.features.anchorview.AnchorTopRightItem
import com.trtc.uikit.livekit.features.anchorview.store.AnchorStore
import com.trtc.uikit.livekit.features.anchorview.view.usermanage.UserManagerDialog
import io.trtc.tuikit.atomicx.common.util.ScreenUtil.dip2px
import io.trtc.tuikit.atomicxcore.api.live.LiveUserInfo

class AnchorTopRightView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr) {

    private var anchorStore: AnchorStore? = null

    private lateinit var audienceListView: AudienceListView
    private lateinit var imageFloatWindow: ImageView
    private lateinit var imageEndLive: ImageView

    var onEndLiveClick: (() -> Unit)? = null

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        createBuiltInViews()
    }

    internal fun init(anchorStore: AnchorStore) {
        this.anchorStore = anchorStore
        val anchorState = anchorStore.getState()
        audienceListView.init(anchorState.liveInfo)
        audienceListView.setOnUserItemClickListener(object :
            AudienceListView.OnUserItemClickListener {
            override fun onUserItemClick(userInfo: LiveUserInfo) {
                val dialog = UserManagerDialog(context, anchorStore, userInfo)
                dialog.show()
            }
        })
        imageEndLive.setOnClickListener { onEndLiveClick?.invoke() }
        imageFloatWindow.setOnClickListener { anchorStore.notifyPictureInPictureClick() }
    }

    internal fun updateItems(items: List<AnchorTopRightItem>) {
        removeAllViews()

        val spacing = dip2px(6f)
        val itemHeight = dip2px(24f)
        val iconSize = dip2px(24f)
        val audienceListWidth = dip2px(80f)

        for (item in items) {
            val itemView: View
            val lp: LayoutParams

            when (item) {
                is AnchorTopRightItem.AudienceCount -> {
                    itemView = audienceListView.apply {
                        (parent as? ViewGroup)?.removeView(this)
                        visibility = VISIBLE
                    }
                    lp = LayoutParams(audienceListWidth, itemHeight)
                }

                is AnchorTopRightItem.FloatWindow -> {
                    itemView = imageFloatWindow.apply {
                        (parent as? ViewGroup)?.removeView(this)
                        visibility = VISIBLE
                    }
                    lp = LayoutParams(iconSize, itemHeight)
                }

                is AnchorTopRightItem.Close -> {
                    itemView = imageEndLive.apply {
                        (parent as? ViewGroup)?.removeView(this)
                        visibility = VISIBLE
                    }
                    lp = LayoutParams(iconSize, itemHeight)
                }

                is AnchorTopRightItem.Custom -> {
                    itemView = item.view.apply {
                        (parent as? ViewGroup)?.removeView(this)
                    }
                    val existingLp = itemView.layoutParams
                    lp = if (existingLp != null) {
                        LayoutParams(existingLp.width, existingLp.height).apply {
                            gravity = Gravity.CENTER_VERTICAL
                        }
                    } else {
                        LayoutParams(iconSize, itemHeight).apply {
                            gravity = Gravity.CENTER_VERTICAL
                        }
                    }
                }
            }
            lp.marginEnd = spacing
            addView(itemView, lp)
        }
    }

    internal fun showAudienceList() {
        if (::audienceListView.isInitialized) {
            audienceListView.showAudienceListPanelView()
        }
    }

    internal fun showFloatWindow() {
        if (::imageFloatWindow.isInitialized) {
            imageFloatWindow.performClick()
        }
    }

    private fun createBuiltInViews() {
        audienceListView = AudienceListView(context)
        imageFloatWindow = ImageView(context).apply {
            setImageResource(R.drawable.livekit_float_window)
            setPadding(dip2px(2f), dip2px(2f), dip2px(2f), dip2px(2f))
        }
        imageEndLive = ImageView(context).apply {
            setImageResource(R.drawable.livekit_anchor_icon_end_stream)
            setPadding(dip2px(2f), dip2px(2f), dip2px(2f), dip2px(2f))
        }
    }
}
