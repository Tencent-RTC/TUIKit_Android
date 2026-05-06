package com.trtc.uikit.livekit.features.audienceview.view.game

import android.content.Context
import android.text.TextUtils
import android.view.View
import com.trtc.uikit.livekit.R
import com.trtc.uikit.livekit.common.LiveKitLogger
import com.trtc.uikit.livekit.features.audienceview.store.AudienceStore
import com.trtc.uikit.livekit.features.audienceview.view.coguest.panel.AnchorManagerDialog
import com.trtc.uikit.livekit.features.audienceview.view.coguest.panel.TypeSelectDialog
import com.trtc.uikit.livekit.features.audienceview.view.userinfo.UserInfoDialog
import io.trtc.tuikit.atomicxcore.api.live.LiveInfo
import io.trtc.tuikit.atomicxcore.api.live.LiveUserInfo
import io.trtc.tuikit.atomicxcore.api.live.SeatInfo
import io.trtc.tuikit.atomicxcore.api.live.SeatLayoutTemplate
import io.trtc.tuikit.atomicxcore.api.login.LoginStore

/**
 * 观众端手游直播（横向麦位）模式的管理视图
 * 封装了 AudienceSeatListView 的显示/隐藏、初始化和点击回调逻辑
 * 麦位点击事件在内部闭环处理，无需外部设置回调
 *
 * 使用方式：
 * 1. 通过 [create] 工厂方法从父 View 中查找并包装 AudienceSeatListView
 * 2. 调用 [init] 初始化 store
 * 3. 调用 [updateByTemplate] 根据直播模板更新显示状态
 */
class AudienceGameView private constructor(
    private val context: Context,
    private var audienceStore: AudienceStore,
    private val seatListView: AudienceSeatListView
) {

    companion object {
        private val LOGGER = LiveKitLogger.getFeaturesLogger("AudienceGameView")

        /**
         * 从父 View 中查找 AudienceSeatListView 并包装为 AudienceGameView
         * @param parentView 包含 co_guest_seat_list_view 的父 View
         * @param audienceStore 观众 Store，用于内部处理麦位点击等逻辑
         * @return AudienceGameView 实例
         */
        fun create(parentView: View, audienceStore: AudienceStore): AudienceGameView {
            val seatListView = parentView.findViewById<AudienceSeatListView>(R.id.co_guest_seat_list_view)
            return AudienceGameView(parentView.context, audienceStore, seatListView)
        }
    }

    private var anchorManagerDialog: AnchorManagerDialog? = null
    private var userInfoDialog: UserInfoDialog? = null

    init {
        seatListView.onSeatClick = { seatInfo ->
            onSeatItemClick(seatInfo)
        }
    }

    /**
     * 初始化 store，委托给 AudienceSeatListView
     */
    fun init(store: AudienceStore) {
        LOGGER.info("init: initializing AudienceGameView")
        this.audienceStore = store
        anchorManagerDialog = null
        userInfoDialog = null
        val liveInfo = store.getLiveListStore().liveState.currentLive.value
        seatListView.init(store)
        if (liveInfo.seatTemplate == SeatLayoutTemplate.VideoLandscape4Seats && liveInfo.keepOwnerOnSeat) {
            seatListView.visibility = View.VISIBLE
        } else {
            seatListView.visibility = View.GONE
        }
    }

    /**
     * 根据直播模板更新麦位列表显示状态
     * 仅在 VideoLandscape4Seats 模板且 keepOwnerOnSeat 时显示
     *
     * @param liveInfo 直播信息
     */
    fun updateByTemplate(liveInfo: LiveInfo) {
        val shouldShow = (liveInfo.seatTemplate == SeatLayoutTemplate.VideoLandscape4Seats && liveInfo.keepOwnerOnSeat)
        LOGGER.info("updateByTemplate: seatTemplate=${liveInfo.seatTemplate}, keepOwnerOnSeat=${liveInfo.keepOwnerOnSeat}, shouldShow=$shouldShow")
        setSeatListVisible(shouldShow)
    }

    /**
     * 设置麦位列表的显示状态
     * @param visible true 显示，false 隐藏
     */
    fun setSeatListVisible(visible: Boolean) {
        seatListView.visibility = if (visible) View.VISIBLE else View.GONE
    }

    /**
     * 内部处理麦位点击事件
     */
    private fun onSeatItemClick(seatInfo: SeatInfo) {
        LOGGER.info("onSeatItemClick: seatIndex=${seatInfo.index}, userID=${seatInfo.userInfo.userID}")
        if (seatInfo.userInfo.userID.isEmpty()) {
            // 空麦位：点击申请上麦
            val loginUserId = LoginStore.shared.loginState.loginUserInfo.value?.userID
            if (audienceStore.getViewState().isApplyingToTakeSeat.value
                || !audienceStore.getCoGuestState().connected.value.none { it.userID == loginUserId }
                || audienceStore.getViewState().isTakeSeatDialogShowing.value
            ) {
                return
            }
            audienceStore.getViewState().isTakeSeatDialogShowing.value = true
            val typeSelectDialog = TypeSelectDialog(
                context,
                audienceStore,
                seatInfo.index
            )
            typeSelectDialog.setOnDismissListener { audienceStore.getViewState().isTakeSeatDialogShowing.value = false }
            typeSelectDialog.show()
        } else {
            // 有人麦位：显示用户管理弹窗
            showCoGuestManageDialog(
                LiveUserInfo(
                    userID = seatInfo.userInfo.userID,
                    userName = seatInfo.userInfo.userName,
                    avatarURL = seatInfo.userInfo.avatarURL,
                )
            )
        }
    }

    /**
     * 显示连麦管理弹窗（自己显示 AnchorManagerDialog，他人显示 UserInfoDialog）
     */
    private fun showCoGuestManageDialog(userInfo: LiveUserInfo) {
        if (TextUtils.isEmpty(userInfo.userID)) return
        if (audienceStore.getCoGuestState().connected.value.isEmpty()) return
        if (userInfo.userID == LoginStore.shared.loginState.loginUserInfo.value?.userID) {
            showAnchorManagerDialog(userInfo)
        } else {
            showUserInfoDialog(userInfo)
        }
    }

    private fun showAnchorManagerDialog(userInfo: LiveUserInfo) {
        if (anchorManagerDialog == null) {
            anchorManagerDialog = AnchorManagerDialog(context, audienceStore)
        }
        anchorManagerDialog?.init(userInfo)
        anchorManagerDialog?.show()
    }

    private fun showUserInfoDialog(userInfo: LiveUserInfo) {
        if (userInfoDialog == null) {
            userInfoDialog = UserInfoDialog(context, audienceStore)
        }
        userInfoDialog?.init(userInfo)
        userInfoDialog?.show()
    }
}
