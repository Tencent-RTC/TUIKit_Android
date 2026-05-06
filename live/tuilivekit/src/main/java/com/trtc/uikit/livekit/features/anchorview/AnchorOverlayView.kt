package com.trtc.uikit.livekit.features.anchorview

import android.annotation.SuppressLint
import android.content.Context
import android.text.TextUtils
import android.util.AttributeSet
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
import com.trtc.uikit.livekit.component.networkInfo.NetworkInfoView
import com.trtc.uikit.livekit.component.roominfo.LiveInfoView
import com.trtc.uikit.livekit.features.anchorview.store.AnchorStore
import com.trtc.uikit.livekit.features.anchorview.view.coguest.panel.ApplyCoGuestFloatView
import com.trtc.uikit.livekit.features.anchorview.view.menuview.AnchorBottomMenuView
import com.trtc.uikit.livekit.features.anchorview.view.menuview.AnchorTopRightView
import com.trtc.uikit.livekit.features.anchorview.view.usermanage.UserManagerDialog
import io.trtc.tuikit.atomicxcore.api.barrage.Barrage
import io.trtc.tuikit.atomicxcore.api.gift.Gift
import io.trtc.tuikit.atomicxcore.api.live.LiveAudienceListener
import io.trtc.tuikit.atomicxcore.api.live.LiveListStore
import io.trtc.tuikit.atomicxcore.api.live.LiveUserInfo
import io.trtc.tuikit.atomicxcore.api.login.LoginStore

@SuppressLint("ViewConstructor")
class AnchorOverlayView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    private lateinit var anchorStore: AnchorStore

    private lateinit var bottomMenu: AnchorBottomMenuView
    private lateinit var topRightBar: AnchorTopRightView
    private lateinit var _roomInfoView: LiveInfoView
    private lateinit var _networkInfoView: NetworkInfoView
    private lateinit var _barrageInputView: BarrageInputView
    private lateinit var _barrageStreamView: BarrageStreamView
    private lateinit var _giftPlayView: GiftPlayView
    private lateinit var applyCoGuestFloatView: ApplyCoGuestFloatView

    val barrageStreamView: BarrageStreamView get() = _barrageStreamView

    private val nodeViewMap = mutableMapOf<AnchorNode, View>()
    private var isComponentReady = false
    private val pendingReplacements = mutableMapOf<AnchorNode, View?>()

    private var _bottomItems: List<AnchorBottomItem>? = null
    private var _topRightItems: List<AnchorTopRightItem>? = null

    private val defaultBottomItems: List<AnchorBottomItem>
        get() = listOf(
            AnchorBottomItem.CoHost,
            AnchorBottomItem.Battle,
            AnchorBottomItem.CoGuest,
            AnchorBottomItem.More,
        )

    private val defaultTopRightItems: List<AnchorTopRightItem>
        get() = listOf(
            AnchorTopRightItem.AudienceCount,
            AnchorTopRightItem.FloatWindow,
            AnchorTopRightItem.Close,
        )

    var bottomItems: List<AnchorBottomItem>
        get() = _bottomItems ?: defaultBottomItems
        set(value) {
            _bottomItems = value
            if (isComponentReady) {
                bottomMenu.updateItems(value)
            }
        }

    var topRightItems: List<AnchorTopRightItem>
        get() = _topRightItems ?: defaultTopRightItems
        set(value) {
            _topRightItems = value
            if (isComponentReady) {
                topRightBar.updateItems(value)
            }
        }

    var onEndLiveClick: (() -> Unit)? = null
        set(value) {
            field = value
            if (::topRightBar.isInitialized) {
                topRightBar.onEndLiveClick = value
            }
        }

    private var enterRoomNotifyStrategy: AnchorUserEnterRoomNotifyStrategy = AnchorUserEnterRoomNotifyStrategy.ALWAYS
    private var intervalSecondOnMerge: Long = 60_000L
    private val enterRoomUserTimestamps: MutableMap<String, Long> = mutableMapOf()

    private val liveAudienceListener = object : LiveAudienceListener() {
        override fun onAudienceJoined(audience: LiveUserInfo) {
            if (!shouldNotifyEnterRoom(audience.userID)) return
            val barrage = Barrage().apply {
                textContent = this@AnchorOverlayView.context.getString(R.string.common_entered_room)
                sender.apply {
                    userID = audience.userID
                    userName = audience.userName
                    avatarURL = audience.avatarURL
                }
            }
            _barrageStreamView.insertBarrages(barrage)
        }
    }

    init {
        initView()
    }

    private fun initView() {
        LayoutInflater.from(context).inflate(R.layout.livekit_livestream_anchor_overlay_view, this, true)

        _barrageStreamView = findViewById(R.id.barrage_stream_view)
        _giftPlayView = findViewById(R.id.gift_play_view)
        _barrageInputView = findViewById(R.id.barrage_input_view)
        _roomInfoView = findViewById(R.id.room_info_view)
        _networkInfoView = findViewById(R.id.network_info_view)
        applyCoGuestFloatView = findViewById(R.id.rl_apply_link_audience)
        bottomMenu = findViewById(R.id.anchor_bottom_menu)
        topRightBar = findViewById(R.id.anchor_top_right_bar)
    }

   internal fun init(anchorStore: AnchorStore) {
        this.anchorStore = anchorStore
    }

    private fun getDefaultView(node: AnchorNode): View? {
        if (!::_roomInfoView.isInitialized) return null
        return when (node) {
            AnchorNode.LIVE_INFO -> _roomInfoView
            AnchorNode.TOP_RIGHT_BUTTONS -> topRightBar
            AnchorNode.NETWORK_INFO -> _networkInfoView
            AnchorNode.BARRAGE_INPUT -> _barrageInputView
            AnchorNode.BOTTOM_RIGHT_BAR -> bottomMenu
        }
    }

    internal fun replace(node: AnchorNode, view: View?) {
        if (!isComponentReady) {
            pendingReplacements[node] = view
            return
        }
        applyReplace(node, view)
    }

    private fun applyReplace(node: AnchorNode, view: View?) {
        val defaultView = getDefaultView(node) ?: return
        val existing = nodeViewMap[node] ?: defaultView
        val parent = existing.parent as? ViewGroup ?: return

        if (view == null) {
            val customView = nodeViewMap.remove(node)
            if (customView != null) {
                parent.removeView(customView)
            }
            defaultView.visibility = GONE
            return
        }

        val index = parent.indexOfChild(existing)
        if (index >= 0) {
            val defaultLp = existing.layoutParams
            parent.removeViewAt(index)
            if (view.layoutParams == null) {
                view.layoutParams = defaultLp
            } else {
                val viewLayoutParams = view.layoutParams
                val newLayoutParams = when (defaultLp) {
                    is LayoutParams -> LayoutParams(viewLayoutParams.width, viewLayoutParams.height).apply {
                        gravity = defaultLp.gravity
                        leftMargin = defaultLp.leftMargin
                        topMargin = defaultLp.topMargin
                        rightMargin = defaultLp.rightMargin
                        bottomMargin = defaultLp.bottomMargin
                        marginStart = defaultLp.marginStart
                        marginEnd = defaultLp.marginEnd
                    }

                    else -> viewLayoutParams
                }
                view.layoutParams = newLayoutParams
            }
            parent.addView(view, index)
            nodeViewMap[node] = view
        }
    }

    internal fun perform(action: AnchorAction) {
        when (action) {
            AnchorAction.SHOW_LIVE_INFO -> {
                if (::_roomInfoView.isInitialized) _roomInfoView.showLiveInfoPanel()
            }

            AnchorAction.SHOW_AUDIENCE_LIST -> topRightBar.showAudienceList()
            AnchorAction.SHOW_CO_GUEST_PANEL -> bottomMenu.showCoGuestPanel()
            AnchorAction.SHOW_CO_HOST_PANEL -> bottomMenu.showCoHostPanel()
            AnchorAction.REQUEST_BATTLE -> bottomMenu.requestBattle()
            AnchorAction.SHOW_MORE_PANEL -> bottomMenu.showMorePanel()
            AnchorAction.SHOW_FLOAT_WINDOW -> topRightBar.showFloatWindow()
            AnchorAction.END_LIVE -> onEndLiveClick?.invoke()
        }
    }

    internal fun initComponentView() {
        initRoomInfoView()
        initNetworkView()
        initBarrageInputView()
        initBarrageStreamView()
        initApplyCoGuestFloatView()
        initGiftPlayView()

        bottomMenu.init(anchorStore)
        bottomMenu.updateItems(bottomItems)

        topRightBar.init(anchorStore)
        topRightBar.onEndLiveClick = onEndLiveClick
        topRightBar.updateItems(topRightItems)

        isComponentReady = true
        flushPendingReplacements()
    }

    private fun flushPendingReplacements() {
        pendingReplacements.forEach { (node, view) ->
            applyReplace(node, view)
        }
        pendingReplacements.clear()
    }

    internal fun updateEffectiveBottomItems(effectiveItems: List<AnchorBottomItem>) {
        if (isComponentReady) {
            bottomMenu.updateItems(effectiveItems)
        }
    }

    internal fun updateEffectiveTopRightItems(effectiveItems: List<AnchorTopRightItem>) {
        if (isComponentReady) {
            topRightBar.updateItems(effectiveItems)
        }
    }

    fun setUserEnterRoomNotifyStrategy(strategy: AnchorUserEnterRoomNotifyStrategy, intervalSecondOnMerge: Long = 60_000L) {
        this.enterRoomNotifyStrategy = strategy
        this.intervalSecondOnMerge = intervalSecondOnMerge
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        startObserveState()
    }

    override fun onDetachedFromWindow() {
        stopObserveState()
        super.onDetachedFromWindow()
    }

    private fun startObserveState() {
        stopObserveState()
        anchorStore.getLiveAudienceStore().addLiveAudienceListener(liveAudienceListener)
    }

    private fun stopObserveState() {
        anchorStore.getLiveAudienceStore().removeLiveAudienceListener(liveAudienceListener)
    }

    private fun shouldNotifyEnterRoom(userID: String): Boolean {
        return when (enterRoomNotifyStrategy) {
            AnchorUserEnterRoomNotifyStrategy.ALWAYS -> true
            AnchorUserEnterRoomNotifyStrategy.MERGE -> {
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

    private fun initNetworkView() {
        val anchorState = anchorStore.getState()
        _networkInfoView.init(anchorState.liveInfo)
    }

    private fun initRoomInfoView() {
        val anchorState = anchorStore.getState()
        _roomInfoView.init(anchorState.liveInfo)
    }

    private fun initBarrageInputView() {
        val anchorState = anchorStore.getState()
        _barrageInputView.init(anchorState.roomId)
    }

    private fun initBarrageStreamView() {
        val ownerUserId = LiveListStore.shared().liveState.currentLive.value.liveOwner.userID
        val liveId = LiveListStore.shared().liveState.currentLive.value.liveID
        _barrageStreamView.init(liveId, ownerUserId)
        _barrageStreamView.setItemTypeDelegate(BarrageViewTypeDelegate())
        _barrageStreamView.setItemAdapter(GIFT_VIEW_TYPE_1, GiftBarrageAdapter(context))
        _barrageStreamView.setOnMessageClickListener(object : BarrageStreamView.OnMessageClickListener {
            override fun onMessageClick(userInfo: LiveUserInfo) {
                if (TextUtils.isEmpty(userInfo.userID) || userInfo.userID == LoginStore.shared.loginState.loginUserInfo.value?.userID) {
                    return
                }
                val userManagerDialog = UserManagerDialog(context, anchorStore, userInfo)
                userManagerDialog.show()
            }
        })
    }

    private fun initApplyCoGuestFloatView() {
        applyCoGuestFloatView.init(anchorStore)
    }

    private fun initGiftPlayView() {
        val giftCacheService: GiftCacheService = GiftStore.getInstance().giftCacheService
        _giftPlayView.setListener(object : GiftPlayView.TUIGiftPlayViewListener {
            override fun onReceiveGift(
                view: GiftPlayView?, gift: Gift, giftCount: Int, sender: LiveUserInfo,
            ) {
                val barrage = Barrage().apply {
                    textContent = "gift"
                    this.sender.userID = sender.userID
                    this.sender.userName = if (TextUtils.isEmpty(sender.userName)) {
                        sender.userID
                    } else {
                        sender.userName
                    }
                    this.sender.avatarURL = sender.avatarURL

                    val extInfo = hashMapOf<String, String>(
                        GIFT_VIEW_TYPE to GIFT_VIEW_TYPE_1.toString(),
                        GIFT_NAME to gift.name,
                        GIFT_COUNT to giftCount.toString(),
                        GIFT_ICON_URL to gift.iconURL,
                        GIFT_RECEIVER_USERNAME to context.getString(R.string.common_gift_me)
                    )
                    extensionInfo = extInfo
                }
                _barrageStreamView.insertBarrages(barrage)
            }

            override fun onPlayGiftAnimation(
                view: GiftPlayView?, gift: Gift,
            ) {
                giftCacheService.request(gift.resourceURL, object : GiftCacheService.Callback<String> {
                    override fun onResult(error: Int, result: String?) {
                        if (error == 0) {
                            result?.let {
                                view?.playGiftAnimation(it)
                            }
                        }
                    }
                })
            }
        })
        val anchorState = anchorStore.getState()
        _giftPlayView.init(anchorState.roomId)
    }
}

enum class AnchorUserEnterRoomNotifyStrategy {
    ALWAYS,
    MERGE,
}
