package com.trtc.uikit.livekit.features.audienceview.view.settings

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Rect
import android.view.LayoutInflater
import android.view.View
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.trtc.tuikit.atomicx.common.util.ScreenUtil
import com.trtc.uikit.livekit.R
import io.trtc.tuikit.atomicx.widget.basicwidget.popover.AtomicPopover
import com.trtc.uikit.livekit.features.audienceview.store.AudienceStore

@SuppressLint("ViewConstructor")
class AudienceSettingsPanelDialog(
    context: Context,
    private val audienceStore: AudienceStore
) : AtomicPopover(context), AudienceStore.AudienceStoreListener {

    init {
        initView()
    }

    private fun initView() {
        val view = LayoutInflater.from(context).inflate(R.layout.livekit_audience_settings_panel, null)
        initSettingsListView(view)
        setContent(view)
    }

    private fun initSettingsListView(view: View) {
        val recycleSettingsList = view.findViewById<RecyclerView>(R.id.rv_settings_list)
        val spanCount = 5
        recycleSettingsList.layoutManager = GridLayoutManager(context, spanCount)
        val screenWidth = ScreenUtil.getScreenWidth(context)
        val itemWidth = ScreenUtil.dip2px(56f)
        val spanSpace0 = (screenWidth - spanCount * itemWidth) / spanCount
        val spanSpace1 = (screenWidth - spanCount * itemWidth) / (spanCount + 1)
        recycleSettingsList.addItemDecoration(object : RecyclerView.ItemDecoration() {
            override fun getItemOffsets(
                outRect: Rect,
                view: View,
                parent: RecyclerView,
                state: RecyclerView.State
            ) {
                val position = parent.getChildLayoutPosition(view) % spanCount
                outRect.left = (1 + position) * spanSpace1 - position * spanSpace0
            }
        })
        val adapter = AudienceSettingsListAdapter(context, audienceStore, this)
        recycleSettingsList.adapter = adapter
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        audienceStore.addAudienceViewListener(this)
    }

    override fun onDetachedFromWindow() {
        audienceStore.removeAudienceViewListener(this)
        super.onDetachedFromWindow()
    }

    override fun onLiveEnded(roomId: String, ownerName: String, ownerAvatarUrl: String) {
        dismiss()
    }
}
