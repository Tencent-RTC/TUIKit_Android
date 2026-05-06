package com.trtc.uikit.livekit.features.audienceview

import android.content.Context
import android.content.res.Configuration
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
import com.trtc.uikit.livekit.features.audienceview.store.access.TUILiveListDataSource
import com.trtc.uikit.livekit.features.audienceview.store.AudienceConfig
import com.trtc.uikit.livekit.features.audienceview.store.AudienceStore
import com.trtc.uikit.livekit.features.audienceview.store.LiveInfoListStore
import com.trtc.uikit.livekit.features.audienceview.view.liveListviewpager.LiveListViewPager
import com.trtc.uikit.livekit.features.audienceview.view.liveListviewpager.LiveListViewPagerAdapter
import io.trtc.tuikit.atomicxcore.api.live.LiveInfo
import io.trtc.tuikit.atomicxcore.api.live.SeatLayoutTemplate
import java.lang.ref.WeakReference
import java.util.concurrent.CopyOnWriteArrayList

class AudienceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr), AudienceLiveView.ViewObserver, ITUINotification,
    AudienceStore.AudienceStoreListener {

    companion object {
        private val LOGGER = LiveKitLogger.getLiveStreamLogger("AudienceView")
    }

    private var fragmentActivity: FragmentActivity? = null
    private val liveListViewPager: LiveListViewPager = LiveListViewPager(context)
    private var liveListViewPagerAdapter: LiveListViewPagerAdapter? = null
    private var audienceLiveView: AudienceLiveView? = null
    private val listeners: MutableList<WeakReference<AudienceViewDefine.AudienceViewListener>> = CopyOnWriteArrayList()
    private var isLandscape: Boolean = false
    private var isLoading: Boolean = false
    private var isLinking: Boolean = false

    init {
        addView(liveListViewPager)
    }

    fun init(
        fragmentActivity: FragmentActivity,
        liveID: String,
        dataSource: AudienceViewDefine.LiveListDataSource? = null
    ) {
        this.fragmentActivity = fragmentActivity
        val resolvedDataSource: AudienceViewDefine.LiveListDataSource =
            dataSource ?: TUILiveListDataSource()
        val liveInfoListStore = LiveInfoListStore(resolvedDataSource)
        val liveInfo = LiveInfo(seatTemplate = SeatLayoutTemplate.VideoDynamicGrid9Seats).apply {
            this.liveID = liveID
        }
        liveListViewPagerAdapter = object : LiveListViewPagerAdapter(
            fragmentActivity,
            liveInfoListStore,
            liveInfo
        ) {
            override fun onCreateView(liveInfo: LiveInfo): View {
                return createAudienceView(liveInfo)
            }

            override fun onViewWillSlideIn(view: View?) {
                val liveView = view as AudienceLiveView
                liveView.startPreviewLiveStream()
            }

            override fun onViewSlideInCancelled(view: View?) {
                val liveView = view as AudienceLiveView
                liveView.stopPreviewLiveStream()
            }

            override fun onViewDidSlideIn(view: View?) {
                audienceLiveView = view as AudienceLiveView
                audienceLiveView?.initStore()
                audienceLiveView?.setViewObserver(this@AudienceView)
                audienceLiveView?.addListener(this@AudienceView)
                audienceLiveView?.joinRoom()
                audienceLiveView?.let { liveView ->
                    notifyListeners { it.onLiveViewDidAppear(liveView, liveView.liveInfo) }
                }
            }

            override fun onViewDidSlideOut(view: View?) {
                val liveView = view as AudienceLiveView
                notifyListeners { it.onLiveViewDidDisappear(liveView, liveView.liveInfo) }
                liveView.removeListener(this@AudienceView)
                liveView.setViewObserver(null)
                liveView.leaveRoom()
            }
        }
        liveListViewPager.setAdapter(liveListViewPagerAdapter!!)
        liveListViewPagerAdapter?.fetchData()
    }

    fun addListener(listener: AudienceViewDefine.AudienceViewListener) {
        LOGGER.info("addListener listener:$listener")
        listeners.add(WeakReference(listener))
    }

    fun removeListener(listener: AudienceViewDefine.AudienceViewListener) {
        LOGGER.info("removeListener listener:$listener")
        listeners.removeAll { it.get() == listener || it.get() == null }
    }

    override fun onConfigurationChanged(newConfig: Configuration?) {
        super.onConfigurationChanged(newConfig)
        isLandscape = newConfig?.orientation == Configuration.ORIENTATION_LANDSCAPE
        enableSliding()
    }

    internal fun disableSliding(disable: Boolean) {
        AudienceConfig.disableSliding(disable)
        if (disable) {
            liveListViewPagerAdapter?.retainOnlyFirstElement()
        } else {
            liveListViewPagerAdapter?.fetchData()
        }
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
        audienceLiveView?.enablePictureInPictureMode(enable)
    }

    fun getRoomId(): String {
        return audienceLiveView?.getRoomId() ?: ""
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        TUICore.registerEvent(EVENT_KEY_LIVE_KIT, EVENT_SUB_KEY_LINK_STATUS_CHANGE, this)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        TUICore.unRegisterEvent(EVENT_KEY_LIVE_KIT, EVENT_SUB_KEY_LINK_STATUS_CHANGE, this)
        audienceLiveView?.leaveRoom()
    }

    private fun createAudienceView(liveInfo: LiveInfo): AudienceLiveView {
        val liveView = AudienceLiveView(fragmentActivity!!)
        liveView.init(liveInfo)
        notifyListeners { it.onCreateLiveView(liveView, liveInfo) }
        return liveView
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
        audienceLiveView?.leaveRoom()
    }

    private fun onLinkStatusChanged(param: Map<String, Any>?) {
        if (AudienceConfig.disableSliding.value == true) {
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
        if (AudienceConfig.disableSliding.value == true) {
            return
        }
        val enabled = !isLinking && !isLoading && !isLandscape
        liveListViewPager.enableSliding(enabled)
    }

    private fun notifyListeners(action: (AudienceViewDefine.AudienceViewListener) -> Unit) {
        val dead = mutableListOf<WeakReference<AudienceViewDefine.AudienceViewListener>>()
        for (ref in listeners) {
            val listener = ref.get()
            if (listener == null) dead.add(ref) else action(listener)
        }
        listeners.removeAll(dead)
    }

    override fun onLiveEnded(roomId: String, ownerName: String, ownerAvatarUrl: String) {
        notifyListeners { it.onLiveEnded(roomId, ownerName, ownerAvatarUrl) }
    }

    override fun onPictureInPictureClick() {
        notifyListeners { it.onClickFloatWindow() }
    }
}
