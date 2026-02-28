package com.trtc.uikit.livekit.features.audiencecontainer

import android.app.Activity
import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import androidx.fragment.app.FragmentActivity
import com.tencent.qcloud.tuicore.TUICore
import com.tencent.qcloud.tuicore.interfaces.ITUINotification
import com.trtc.uikit.livekit.common.EVENT_KEY_LIVE_KIT
import com.trtc.uikit.livekit.common.EVENT_PARAMS_IS_LINKING
import com.trtc.uikit.livekit.common.EVENT_SUB_KEY_LINK_STATUS_CHANGE
import com.trtc.uikit.livekit.common.LiveKitLogger
import com.trtc.uikit.livekit.features.audiencecontainer.store.access.TUILiveListDataSource
import com.trtc.uikit.livekit.features.audiencecontainer.store.AudienceContainerConfig
import com.trtc.uikit.livekit.features.audiencecontainer.store.AudienceContainerStore
import com.trtc.uikit.livekit.features.audiencecontainer.store.LiveInfoListStore
import com.trtc.uikit.livekit.features.audiencecontainer.view.AudienceView
import com.trtc.uikit.livekit.features.audiencecontainer.view.liveListviewpager.LiveListViewPager
import com.trtc.uikit.livekit.features.audiencecontainer.view.liveListviewpager.LiveListViewPagerAdapter
import io.trtc.tuikit.atomicxcore.api.live.LiveInfo
import io.trtc.tuikit.atomicxcore.api.live.SeatLayoutTemplate

class AudienceContainerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr), AudienceView.ViewObserver, ITUINotification {

    companion object {
        private val LOGGER = LiveKitLogger.getLiveStreamLogger("AudienceContainerView")
    }

    private var fragmentActivity: FragmentActivity? = null
    private val liveListViewPager: LiveListViewPager = LiveListViewPager(context)
    private var liveListViewPagerAdapter: LiveListViewPagerAdapter? = null
    private var audienceView: AudienceView? = null
    private val audienceContainerManager: AudienceContainerStore = AudienceContainerStore()
    private var isLandscape: Boolean = false
    private var isLoading: Boolean = false
    private var isLinking: Boolean = false

    init {
        addView(liveListViewPager)
    }

    fun init(fragmentActivity: FragmentActivity, roomId: String) {
        val liveInfo = LiveInfo(seatTemplate = SeatLayoutTemplate.VideoDynamicGrid9Seats).apply {
            this.liveID = roomId
        }
        this.fragmentActivity = fragmentActivity
        val dataSource: AudienceContainerViewDefine.LiveListDataSource = TUILiveListDataSource()
        init(fragmentActivity, liveInfo, dataSource)
    }

    fun init(fragmentActivity: FragmentActivity, liveInfo: LiveInfo) {
        this.fragmentActivity = fragmentActivity
        val dataSource: AudienceContainerViewDefine.LiveListDataSource = TUILiveListDataSource()
        init(fragmentActivity, liveInfo, dataSource)
    }

    fun init(
        fragmentActivity: FragmentActivity,
        roomId: String,
        dataSource: AudienceContainerViewDefine.LiveListDataSource
    ) {
        this.fragmentActivity = fragmentActivity
        val liveInfo = LiveInfo(seatTemplate = SeatLayoutTemplate.VideoDynamicGrid9Seats).apply {
            this.liveID = roomId
        }
        init(fragmentActivity, liveInfo, dataSource)
    }

    fun init(
        fragmentActivity: FragmentActivity,
        liveInfo: LiveInfo,
        dataSource: AudienceContainerViewDefine.LiveListDataSource
    ) {
        this.fragmentActivity = fragmentActivity
        val liveInfoListStore = LiveInfoListStore(dataSource)
        liveListViewPagerAdapter = object : LiveListViewPagerAdapter(
            fragmentActivity,
            liveInfoListStore,
            liveInfo
        ) {
            override fun onCreateView(liveInfo: LiveInfo): View {
                return createAudienceView(liveInfo)
            }

            override fun onViewWillSlideIn(view: View?) {
                val audienceView = view as AudienceView
                audienceView.startPreviewLiveStream()
            }

            override fun onViewSlideInCancelled(view: View?) {
                val audienceView = view as AudienceView
                audienceView.stopPreviewLiveStream()
            }

            override fun onViewDidSlideIn(view: View?) {
                audienceView = view as AudienceView
                audienceView?.initStore()
                audienceView?.setViewObserver(this@AudienceContainerView)
                audienceView?.setAudienceContainerViewListenerList(
                    audienceContainerManager.getAudienceContainerViewListenerList()
                )
                audienceView?.joinRoom()
            }

            override fun onViewDidSlideOut(view: View?) {
                val audienceView = view as AudienceView
                audienceView.setViewObserver(null)
                audienceView.leaveRoom()
            }
        }
        liveListViewPager.setAdapter(liveListViewPagerAdapter!!)
        liveListViewPagerAdapter?.fetchData()
    }

    fun addListener(listener: AudienceContainerViewDefine.AudienceContainerViewListener) {
        LOGGER.info("addListener listener:$listener")
        audienceContainerManager.addListener(listener)
    }

    fun removeListener(listener: AudienceContainerViewDefine.AudienceContainerViewListener) {
        LOGGER.info("removeListener listener:$listener")
        audienceContainerManager.removeListener(listener)
    }

    fun setScreenOrientation(isPortrait: Boolean) {
        isLandscape = !isPortrait
        enableSliding()
    }

    fun disableSliding(disable: Boolean) {
        AudienceContainerStore.disableSliding(disable)
        if (disable) {
            liveListViewPagerAdapter?.retainOnlyFirstElement()
        }
    }

    fun disableHeaderFloatWin(disable: Boolean) {
        AudienceContainerStore.disableHeaderFloatWin(disable)
    }

    fun disableHeaderLiveData(disable: Boolean) {
        AudienceContainerStore.disableHeaderLiveData(disable)
    }

    fun disableHeaderVisitorCnt(disable: Boolean) {
        AudienceContainerStore.disableHeaderVisitorCnt(disable)
    }

    fun disableFooterCoGuest(disable: Boolean) {
        AudienceContainerStore.disableFooterCoGuest(disable)
    }

    /**
     * This API call is called in the [Activity.onPictureInPictureModeChanged]
     * The code example is as follows:
     * public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode) {
     * super.onPictureInPictureModeChanged(isInPictureInPictureMode);
     * mAnchorView.enablePictureInPictureMode(isInPictureInPictureMode);
     * }
     *
     * @param enable true:Turn on picture-in-picture mode; false:Turn off picture-in-picture mode
     */
    fun enablePictureInPictureMode(enable: Boolean) {
        audienceView?.enablePictureInPictureMode(enable)
    }

    fun getRoomId(): String {
        return audienceView?.getRoomId() ?: ""
    }

    fun isLiveStreaming(): Boolean {
        return audienceView?.isLiveStreaming() ?: false
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        TUICore.registerEvent(EVENT_KEY_LIVE_KIT, EVENT_SUB_KEY_LINK_STATUS_CHANGE, this)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        TUICore.unRegisterEvent(EVENT_KEY_LIVE_KIT, EVENT_SUB_KEY_LINK_STATUS_CHANGE, this)
        audienceView?.leaveRoom()
    }

    private fun createAudienceView(liveInfo: LiveInfo): AudienceView {
        val audienceView = AudienceView(fragmentActivity!!)
        audienceView.init(liveInfo)
        return audienceView
    }

    override fun onLoading() {
        isLoading = true
        enableSliding()
    }

    override fun onFinished() {
        isLoading = false
        enableSliding()
    }

    override fun onNotifyEvent(key: String, subKey: String, param: Map<String, Any>?) {
        if (EVENT_SUB_KEY_LINK_STATUS_CHANGE == subKey) {
            onLinkStatusChanged(param)
        }
    }

    fun destroy() {
        audienceView?.leaveRoom()
    }

    private fun onLinkStatusChanged(param: Map<String, Any>?) {
        if (AudienceContainerConfig.disableSliding.value == true) {
            return
        }
        if (param != null) {
            val isLinking = param[EVENT_PARAMS_IS_LINKING] as? Boolean
            if (isLinking != null) {
                this.isLinking = isLinking
                enableSliding()
            }
        }
    }

    private fun enableSliding() {
        if (AudienceContainerConfig.disableSliding.value == true) {
            return
        }
        val enabled = !isLinking && !isLoading && !isLandscape
        liveListViewPager.enableSliding(enabled)
    }
}
