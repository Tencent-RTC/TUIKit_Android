package com.trtc.uikit.livekit.features.anchorview.view.game

import android.content.Context
import android.text.TextUtils
import android.view.View
import android.widget.FrameLayout
import com.trtc.uikit.livekit.R
import com.trtc.uikit.livekit.common.LiveKitLogger
import com.trtc.uikit.livekit.features.anchorview.store.AnchorStore
import com.trtc.uikit.livekit.features.anchorview.view.coguest.panel.AnchorManagerDialog
import io.trtc.tuikit.atomicxcore.api.live.SeatInfo

/**
 * 手游直播（屏幕分享）模式的覆盖层视图
 * 封装了屏幕分享背景、提示文字和主播端麦位列表的完整逻辑
 * 麦位点击事件在内部闭环处理，无需外部设置回调
 *
 * 使用方式：
 * 1. 在布局中通过 <include layout="@layout/livekit_anchor_screen_share_overlay" /> 引入
 * 2. 在代码中通过 [wrap] 工厂方法包装已有的 overlay FrameLayout
 * 3. 调用 [init] 显示覆盖层并初始化麦位列表
 * 4. 调用 [destroy] 释放资源
 */
class AnchorGameView private constructor(
    context: Context,
    private val anchorStore: AnchorStore,
    private val overlayRoot: FrameLayout,
    private val seatListContainer: FrameLayout?
) {

    companion object {
        private val LOGGER = LiveKitLogger.getFeaturesLogger("AnchorGameView")

        /**
         * 包装已有的 screenShareOverlay FrameLayout，将其逻辑委托给 AnchorGameView
         * @param context 上下文
         * @param anchorStore 主播 Store，用于内部处理麦位点击等逻辑
         * @param overlayRoot 布局中 fl_screen_share_overlay 对应的 FrameLayout
         * @return AnchorGameView 实例
         */
        fun wrap(context: Context, anchorStore: AnchorStore, overlayRoot: FrameLayout): AnchorGameView {
            val seatListContainer = overlayRoot.findViewById<FrameLayout>(R.id.fl_seat_list_container)
            return AnchorGameView(context, anchorStore, overlayRoot, seatListContainer)
        }
    }

    private val viewContext: Context = context
    private var anchorSeatListView: AnchorSeatListView? = null

    /**
     * 显示手游直播覆盖层，并初始化麦位列表
     * 对应原 AnchorView 中的 showScreenShareOverlay() 逻辑
     *
     * @param liveID 直播间 ID，用于初始化麦位列表数据
     */
    fun init(liveID: String) {
        LOGGER.info("show: switching to screen share UI, liveID=$liveID")

        // 显示屏幕分享覆盖层
        overlayRoot.visibility = View.VISIBLE

        // 创建并添加主播端麦位列表
        if (seatListContainer != null && anchorSeatListView == null) {
            anchorSeatListView = AnchorSeatListView(viewContext).apply {
                onSeatClick = { seatInfo ->
                    showCoGuestManageDialog(seatInfo)
                }
            }
            seatListContainer.addView(anchorSeatListView)
            anchorSeatListView?.init(liveID)
        }
    }

    /**
     * 释放资源，销毁麦位列表
     * 对应原 AnchorView 中 destroy() 里的 anchorSeatListView 清理逻辑
     */
    fun destroy() {
        LOGGER.info("destroy: cleaning up AnchorGameView")
        anchorSeatListView?.destroy()
        anchorSeatListView = null
    }

    /**
     * 内部处理麦位点击事件，显示连麦管理弹窗
     */
    private fun showCoGuestManageDialog(seatInfo: SeatInfo) {
        if (TextUtils.isEmpty(seatInfo.userInfo.userID)) {
            return
        }
        val anchorManagerDialog = AnchorManagerDialog(viewContext, anchorStore)
        anchorManagerDialog.init(seatInfo)
        anchorManagerDialog.show()
    }
}
