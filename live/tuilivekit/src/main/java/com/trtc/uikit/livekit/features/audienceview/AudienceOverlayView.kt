package com.trtc.uikit.livekit.features.audienceview

import android.content.Context
import android.text.TextUtils
import android.util.AttributeSet
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.trtc.uikit.livekit.R
import com.trtc.uikit.livekit.component.barrage.BarrageInputView
import com.trtc.uikit.livekit.component.barrage.BarrageStreamView
import com.trtc.uikit.livekit.component.gift.GiftPlayView
import com.trtc.uikit.livekit.component.giftaccess.service.GiftCacheService
import com.trtc.uikit.livekit.component.giftaccess.service.GiftConstants.GIFT_COUNT
import com.trtc.uikit.livekit.component.giftaccess.service.GiftConstants.GIFT_ICON_URL
import com.trtc.uikit.livekit.component.giftaccess.service.GiftConstants.GIFT_NAME
import com.trtc.uikit.livekit.component.giftaccess.service.GiftConstants.GIFT_RECEIVER_USERNAME
import com.trtc.uikit.livekit.component.giftaccess.service.GiftConstants.GIFT_VIEW_TYPE
import com.trtc.uikit.livekit.component.giftaccess.service.GiftConstants.GIFT_VIEW_TYPE_1
import com.trtc.uikit.livekit.component.giftaccess.store.GiftStore
import com.trtc.uikit.livekit.component.giftaccess.view.BarrageViewTypeDelegate
import com.trtc.uikit.livekit.component.giftaccess.view.GiftBarrageAdapter
import com.trtc.uikit.livekit.component.roominfo.LiveInfoView
import com.trtc.uikit.livekit.features.audienceview.AudienceViewDefine.AudienceAction
import com.trtc.uikit.livekit.features.audienceview.AudienceViewDefine.AudienceBottomItem
import com.trtc.uikit.livekit.features.audienceview.AudienceViewDefine.AudienceNode
import com.trtc.uikit.livekit.features.audienceview.AudienceViewDefine.AudienceTopRightItem
import com.trtc.uikit.livekit.features.audienceview.store.AudienceStore
import com.trtc.uikit.livekit.features.audienceview.view.menuview.AudienceBottomMenuView
import com.trtc.uikit.livekit.features.audienceview.view.menuview.AudienceTopRightView
import com.trtc.uikit.livekit.features.audienceview.view.coguest.panel.AnchorManagerDialog
import com.trtc.uikit.livekit.features.audienceview.view.coguest.panel.CancelRequestDialog
import com.trtc.uikit.livekit.features.audienceview.view.coguest.panel.CoGuestRequestFloatView
import com.trtc.uikit.livekit.features.audienceview.view.userinfo.UserInfoDialog
import io.trtc.tuikit.atomicx.common.util.ScreenUtil
import io.trtc.tuikit.atomicx.common.util.ScreenUtil.dip2px
import io.trtc.tuikit.atomicxcore.api.barrage.Barrage
import io.trtc.tuikit.atomicxcore.api.gift.Gift
import io.trtc.tuikit.atomicxcore.api.live.LiveAudienceListener
import io.trtc.tuikit.atomicxcore.api.live.LiveInfo
import io.trtc.tuikit.atomicxcore.api.live.LiveUserInfo
import io.trtc.tuikit.atomicxcore.api.login.LoginStore

class AudienceOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {
    private val nodeViewMap = mutableMapOf<AudienceNode, View>()
    private val bottomMenuView: AudienceBottomMenuView
    private val barrageInputView: BarrageInputView
    private val roomInfoView: LiveInfoView
    private val topRightView: AudienceTopRightView
    private val giftPlayView: GiftPlayView
    private val waitingCoGuestPassView: CoGuestRequestFloatView
    private val enterRoomUserTimestamps: MutableMap<String, Long> = mutableMapOf()
    internal val barrageStreamView: BarrageStreamView

    private var userInfoDialog: UserInfoDialog? = null
    private var anchorManagerDialog: AnchorManagerDialog? = null
    private var enterRoomNotifyStrategy: AudienceUserEnterRoomNotifyStrategy = AudienceUserEnterRoomNotifyStrategy.ALWAYS
    private var intervalSecondOnMerge: Long = 60_000L

    private lateinit var audienceStore: AudienceStore


    internal var bottomItems: List<AudienceBottomItem>
        get() = bottomMenuView.items
        set(value) {
            bottomMenuView.items = value
        }

    private var _topRightItems: List<AudienceTopRightItem>? = null
    internal var topRightItems: List<AudienceTopRightItem>
        get() = _topRightItems ?: listOf(
            AudienceTopRightItem.AudienceCount,
            AudienceTopRightItem.FloatWindow,
            AudienceTopRightItem.Close,
        )
        set(value) {
            _topRightItems = value
            rebuildTopRightItems()
        }

    internal var onExitClick: (() -> Unit)? = null
        set(value) {
            field = value
            topRightView.onExitClick = value
        }

    internal var onFloatWindowClick: (() -> Unit)? = null
        set(value) {
            field = value
            topRightView.onFloatWindowClick = value
        }

    init {
        LayoutInflater.from(context).inflate(R.layout.livekit_audience_overlay_view, this, true)
        bottomMenuView = findViewById(R.id.bottom_menu_view)
        barrageInputView = findViewById(R.id.barrage_input_view)
        roomInfoView = findViewById(R.id.room_info_view)
        topRightView = findViewById(R.id.top_right_view)
        barrageStreamView = findViewById(R.id.barrage_stream_view)
        giftPlayView = findViewById(R.id.gift_play_view)
        waitingCoGuestPassView = findViewById(R.id.btn_waiting_pass)
    }

    internal fun init(store: AudienceStore) {
        this.audienceStore = store
        bottomMenuView.init(store)
        topRightView.init(store)

        bottomMenuView.onWaitingCoGuestPassViewUpdate = { isRequesting ->
            waitingCoGuestPassView.visibility = if (isRequesting) VISIBLE else GONE
        }
    }

    internal fun initComponentView(liveInfo: LiveInfo) {
        visibility = VISIBLE
        initRoomInfoView(liveInfo)
        topRightView.initAudienceList(liveInfo)
        initBarrageStreamView(liveInfo)
        initBarrageInputView(liveInfo)
        initGiftPlayView(liveInfo)
        initWaitingCoGuestPassView()

        bottomMenuView.initComponentView(liveInfo)

        if (_topRightItems != null) {
            rebuildTopRightItems()
        }
        addObserver()
    }

    fun setUserEnterRoomNotifyStrategy(
        strategy: AudienceUserEnterRoomNotifyStrategy,
        intervalSecondOnMerge: Long = 60_000L
    ) {
        this.enterRoomNotifyStrategy = strategy
        this.intervalSecondOnMerge = intervalSecondOnMerge
    }

    private fun addObserver() {
        if (!::audienceStore.isInitialized) return
        audienceStore.getLiveAudienceStore().addLiveAudienceListener(liveAudienceListener)
    }

    private fun removeObserver() {
        if (!::audienceStore.isInitialized) return
        audienceStore.getLiveAudienceStore().removeLiveAudienceListener(liveAudienceListener)
    }

    private fun shouldNotifyEnterRoom(userID: String): Boolean {
        return when (enterRoomNotifyStrategy) {
            AudienceUserEnterRoomNotifyStrategy.ALWAYS -> true
            AudienceUserEnterRoomNotifyStrategy.MERGE -> {
                val now = System.currentTimeMillis()
                cleanExpiredEnterRoomRecords(now)
                val lastTime = enterRoomUserTimestamps[userID]
                if (lastTime != null && now - lastTime < intervalSecondOnMerge) {
                    false
                } else {
                    enterRoomUserTimestamps[userID] = now
                    true
                }
            }
        }
    }

    private fun cleanExpiredEnterRoomRecords(now: Long) {
        enterRoomUserTimestamps.entries.removeAll { (_, lastTime) ->
            now - lastTime >= intervalSecondOnMerge
        }
    }

    private val liveAudienceListener = object : LiveAudienceListener() {
        override fun onAudienceJoined(audience: LiveUserInfo) {
            if (!shouldNotifyEnterRoom(audience.userID)) return
            val barrage = Barrage().apply {
                textContent =
                    this@AudienceOverlayView.context.getString(R.string.common_entered_room)
                sender.apply {
                    userID = audience.userID
                    userName = audience.userName
                    avatarURL = audience.avatarURL
                }
            }
            barrageStreamView.insertBarrages(barrage)
        }
    }

    internal fun replace(node: AudienceNode, view: View?) {
        val defaultView = getDefaultView(node) ?: return

        if (view == null) {
            val customView = nodeViewMap.remove(node)
            if (customView != null) {
                val parent = customView.parent as? ViewGroup
                if (parent != null) {
                    val index = parent.indexOfChild(customView)
                    val lp = customView.layoutParams
                    parent.removeView(customView)
                    defaultView.layoutParams = lp
                    parent.addView(defaultView, index)
                }
            }
            defaultView.visibility = VISIBLE
            return
        }

        val existing = nodeViewMap[node] ?: defaultView
        val parent = existing.parent as? ViewGroup ?: return
        val index = parent.indexOfChild(existing)
        if (index >= 0) {
            val defaultLp = existing.layoutParams
            parent.removeViewAt(index)
            if (view.layoutParams == null) {
                view.layoutParams = defaultLp
            } else {
                val viewLp = view.layoutParams
                val newLp = when (defaultLp) {
                    is FrameLayout.LayoutParams -> FrameLayout.LayoutParams(
                        viewLp.width,
                        viewLp.height
                    ).apply {
                        gravity = defaultLp.gravity
                        leftMargin = defaultLp.leftMargin
                        topMargin = defaultLp.topMargin
                        rightMargin = defaultLp.rightMargin
                        bottomMargin = defaultLp.bottomMargin
                        marginStart = defaultLp.marginStart
                        marginEnd = defaultLp.marginEnd
                    }

                    else -> viewLp
                }
                view.layoutParams = newLp
            }
            parent.addView(view, index)
            nodeViewMap[node] = view
        }
    }

    internal fun perform(action: AudienceAction) {
        when (action) {
            AudienceAction.SHOW_LIVE_INFO -> {
                roomInfoView.showLiveInfoPanel()
            }

            AudienceAction.SHOW_AUDIENCE_LIST -> {
                topRightView.showAudienceList()
            }

            AudienceAction.SHOW_GIFT_PANEL -> {
                bottomMenuView.showGiftPanel()
            }

            AudienceAction.SHOW_CO_GUEST_PANEL -> {
                bottomMenuView.showCoGuestPanel()
            }

            AudienceAction.SHOW_MORE_PANEL -> {
                bottomMenuView.showMorePanel()
            }

            AudienceAction.SHOW_FLOAT_WINDOW -> {
                topRightView.requestFloatWindow()
            }

            AudienceAction.EXIT_LIVE -> {
                onExitClick?.invoke()
            }
        }
    }

    internal fun updateViewByOrientation(isPortrait: Boolean) {
        barrageInputView.visibility = if (isPortrait) VISIBLE else GONE
        bottomMenuView.visibility = if (isPortrait) VISIBLE else GONE

        val topRightParams = topRightView.layoutParams as LayoutParams
        topRightParams.topMargin = if (isPortrait) dip2px(60f) else dip2px(30f)
        topRightView.layoutParams = topRightParams
        topRightView.setScreenOrientation(isPortrait)

        val roomInfoParams = roomInfoView.layoutParams as LayoutParams
        roomInfoParams.topMargin = if (isPortrait) dip2px(52f) else dip2px(30f)
        roomInfoView.layoutParams = roomInfoParams
        roomInfoView.setScreenOrientation(isPortrait)

        val barrageParams = barrageStreamView.layoutParams as LayoutParams
        barrageParams.marginStart = dip2px(16f)
        barrageParams.marginEnd = dip2px(126f)
        if (isPortrait) {
            barrageParams.height = dip2px(212f)
            barrageParams.gravity = Gravity.BOTTOM
            barrageParams.topMargin = 0
            barrageParams.bottomMargin = dip2px(70f)
            barrageStreamView.layoutParams = barrageParams
        } else {
            val screenHeight =
                minOf(ScreenUtil.getScreenHeight(context), ScreenUtil.getScreenWidth(context))
            val topMargin = (screenHeight * 0.45).toInt()
            barrageParams.height = screenHeight - topMargin - (screenHeight * 0.09).toInt()
            barrageParams.gravity = Gravity.TOP
            barrageParams.topMargin = topMargin
            barrageParams.bottomMargin = 0
            barrageStreamView.layoutParams = barrageParams
        }
        barrageStreamView.post {
            barrageStreamView.scrollToLastPosition()
        }
    }

    internal fun initCoGuestVisibility(liveInfo: LiveInfo) {
        bottomMenuView.initCoGuestVisibility(liveInfo)
    }

    internal fun showCoGuestManageDialog(userInfo: LiveUserInfo?) {
        if (userInfo == null) return
        if (TextUtils.isEmpty(userInfo.userID)) return
        if (audienceStore.getCoGuestState().connected.value.size <=1) return
        if (userInfo.userID == LoginStore.shared.loginState.loginUserInfo.value?.userID) {
            showAnchorManagerDialog(userInfo)
        } else {
            showUserInfoDialog(userInfo)
        }
    }

    internal fun unInitComponentView() {
        roomInfoView.unInit()
        bottomMenuView.cancelStateObservation()
        removeObserver()
    }

    private fun getDefaultView(node: AudienceNode): View? {
        return when (node) {
            AudienceNode.LIVE_INFO -> roomInfoView
            AudienceNode.TOP_RIGHT_BUTTONS -> topRightView
            AudienceNode.NETWORK_INFO -> null
            AudienceNode.BARRAGE_INPUT -> barrageInputView
            AudienceNode.BOTTOM_RIGHT_BAR -> bottomMenuView
        }
    }

    private fun rebuildTopRightItems() {
        topRightView.updateItems(topRightItems)
    }

    private fun initRoomInfoView(liveInfo: LiveInfo) {
        roomInfoView.init(liveInfo)
    }

    private fun initBarrageStreamView(liveInfo: LiveInfo) {
        barrageStreamView.init(liveInfo.liveID, liveInfo.liveOwner.userID)
        barrageStreamView.setItemTypeDelegate(BarrageViewTypeDelegate())
        barrageStreamView.setItemAdapter(GIFT_VIEW_TYPE_1, GiftBarrageAdapter(context))
        barrageStreamView.setOnMessageClickListener(object :
            BarrageStreamView.OnMessageClickListener {
            override fun onMessageClick(userInfo: LiveUserInfo) {
                if (TextUtils.isEmpty(userInfo.userID)) {
                    return@onMessageClick
                }
                if (userInfo.userID == LoginStore.shared.loginState.loginUserInfo.value?.userID) {
                    return@onMessageClick
                }
                showUserInfoDialog(userInfo)
            }
        })
    }

    private fun initBarrageInputView(liveInfo: LiveInfo) {
        barrageInputView.init(liveInfo.liveID)
    }

    private fun initGiftPlayView(liveInfo: LiveInfo) {
        giftPlayView.init(liveInfo.liveID)
        val giftCacheService = GiftStore.getInstance().giftCacheService
        giftPlayView.setListener(object : GiftPlayView.TUIGiftPlayViewListener {
            override fun onReceiveGift(
                view: GiftPlayView?,
                gift: Gift,
                giftCount: Int,
                sender: LiveUserInfo,
            ) {
                val barrage = Barrage()
                barrage.textContent = "gift"
                barrage.sender.userID = sender.userID
                barrage.sender.userName =
                    if (TextUtils.isEmpty(sender.userName)) sender.userID else sender.userName
                barrage.sender.avatarURL = sender.avatarURL
                val extInfo = HashMap<String, String>()
                extInfo[GIFT_VIEW_TYPE] = GIFT_VIEW_TYPE_1.toString()
                extInfo[GIFT_NAME] = gift.name
                extInfo[GIFT_COUNT] = giftCount.toString()
                extInfo[GIFT_ICON_URL] = gift.iconURL
                extInfo[GIFT_RECEIVER_USERNAME] =
                    if (TextUtils.isEmpty(audienceStore.getLiveListState().currentLive.value.liveOwner.userName))
                        audienceStore.getLiveListState().currentLive.value.liveOwner.userID
                    else audienceStore.getLiveListState().currentLive.value.liveOwner.userName
                barrage.extensionInfo = extInfo
                barrageStreamView.insertBarrages(barrage)
            }

            override fun onPlayGiftAnimation(view: GiftPlayView?, gift: Gift) {
                giftCacheService.request(
                    gift.resourceURL,
                    object : GiftCacheService.Callback<String> {
                        override fun onResult(error: Int, result: String?) {
                            if (error == 0) {
                                view?.playGiftAnimation(result ?: "")
                            }
                        }
                    })
            }
        })
    }

    private fun initWaitingCoGuestPassView() {
        waitingCoGuestPassView.setOnClickListener {
            if (!::audienceStore.isInitialized) return@setOnClickListener
            val linkMicDialog =
                CancelRequestDialog(
                    context, audienceStore
                )
            linkMicDialog.show()
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

enum class AudienceUserEnterRoomNotifyStrategy {
    ALWAYS,
    MERGE,
}
