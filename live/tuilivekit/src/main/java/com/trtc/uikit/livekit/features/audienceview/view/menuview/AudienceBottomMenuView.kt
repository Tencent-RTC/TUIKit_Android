package com.trtc.uikit.livekit.features.audienceview.view.menuview

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import com.trtc.uikit.livekit.R
import com.trtc.uikit.livekit.component.gift.LikeButton
import com.trtc.uikit.livekit.component.giftaccess.GiftButton
import com.trtc.uikit.livekit.component.giftaccess.GiftSendDialog
import com.trtc.uikit.livekit.features.audienceview.AudienceViewDefine.AudienceBottomItem
import com.trtc.uikit.livekit.features.audienceview.store.AudienceStore
import com.trtc.uikit.livekit.features.audienceview.view.coguest.panel.CancelRequestDialog
import com.trtc.uikit.livekit.features.audienceview.view.coguest.panel.StopCoGuestDialog
import com.trtc.uikit.livekit.features.audienceview.view.coguest.panel.TypeSelectDialog
import com.trtc.uikit.livekit.features.audienceview.view.settings.AudienceSettingsPanelDialog
import io.trtc.tuikit.atomicx.common.util.ScreenUtil.dip2px
import io.trtc.tuikit.atomicxcore.api.live.CoHostStore
import io.trtc.tuikit.atomicxcore.api.live.LiveInfo
import io.trtc.tuikit.atomicxcore.api.live.SeatLayoutTemplate
import io.trtc.tuikit.atomicxcore.api.login.LoginStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class AudienceBottomMenuView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr) {

    private lateinit var audienceStore: AudienceStore
    private var stateScope: CoroutineScope? = null

    private val buttonSize = dip2px(32f)
    private val buttonSpacing = dip2px(8f)

    private var imageCoGuest: ImageView? = null
    private var imageMore: ImageView? = null
    private var buttonGift: GiftButton? = null
    private var buttonLike: LikeButton? = null

    private var _items: List<AudienceBottomItem>? = null
    internal var items: List<AudienceBottomItem>
        get() = _items ?: listOf(
            AudienceBottomItem.Gift,
            AudienceBottomItem.CoGuest,
            AudienceBottomItem.Like,
            AudienceBottomItem.More,
        )
        set(value) {
            _items = value
            rebuildButtons()
        }

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL or Gravity.END
    }

    internal  fun init(store: AudienceStore) {
        this.audienceStore = store
    }

    internal fun initComponentView(liveInfo: LiveInfo) {
        if (_items != null) {
            rebuildButtons()
        } else {
            buildDefaultButtons(liveInfo)
        }
    }

    internal fun showGiftPanel() {
        if (buttonGift != null) {
            buttonGift?.showGiftPanel()
            return
        }
        if (!::audienceStore.isInitialized) return
        val currentLive = audienceStore.getLiveListState().currentLive.value
        if (currentLive.liveID.isEmpty()) return
        val dialog = GiftSendDialog(
            context,
            currentLive.liveID,
            currentLive.liveOwner.userID,
            currentLive.liveOwner.userName,
            currentLive.liveOwner.avatarURL
        )
        dialog.show()
    }

    internal fun showCoGuestPanel() {
        if (imageCoGuest != null) {
            imageCoGuest?.performClick()
            return
        }
        if (!::audienceStore.isInitialized) return
        if (audienceStore.getViewState().isApplyingToTakeSeat.value
            || !audienceStore.getCoGuestState().connected.value.none {
                it.userID == LoginStore.shared.loginState.loginUserInfo.value?.userID
            }
        ) {
            return
        }
        val typeSelectDialog = TypeSelectDialog(context, audienceStore, -1)
        typeSelectDialog.show()
    }

    internal fun showMorePanel() {
        if (!::audienceStore.isInitialized) return
        val audienceSettingsPanelDialog = AudienceSettingsPanelDialog(context, audienceStore)
        audienceSettingsPanelDialog.show()
    }

    internal fun initCoGuestVisibility(liveInfo: LiveInfo) {
        if (!::audienceStore.isInitialized) return
        if (audienceStore.getLiveListState().currentLive.value.liveID != liveInfo.liveID) return
        val isLandscape = liveInfo.seatTemplate == SeatLayoutTemplate.VideoLandscape4Seats
        imageCoGuest?.visibility = if (isLandscape && !liveInfo.keepOwnerOnSeat) GONE else VISIBLE
    }

    private fun enableCoGuest(enable: Boolean) {
        imageCoGuest?.let { enableView(it, enable) }
    }

    private enum class CoGuestUIState { IDLE, REQUESTING, CONNECTED }

    private fun updateCoGuestUI(state: CoGuestUIState) {
        val icon = imageCoGuest ?: return
        when (state) {
            CoGuestUIState.IDLE -> {
                icon.setImageResource(R.drawable.livekit_function_link_default)
                icon.setOnClickListener {
                    if (!::audienceStore.isInitialized) return@setOnClickListener
                    if (audienceStore.getViewState().isApplyingToTakeSeat.value
                        || !audienceStore.getCoGuestState().connected.value.none {
                            it.userID == LoginStore.shared.loginState.loginUserInfo.value?.userID
                        }
                        || audienceStore.getViewState().isTakeSeatDialogShowing.value
                    ) {
                        return@setOnClickListener
                    }
                    audienceStore.getViewState().isTakeSeatDialogShowing.value = true
                    val typeSelectDialog = TypeSelectDialog(context, audienceStore, -1)
                    typeSelectDialog.setOnDismissListener { audienceStore.getViewState().isTakeSeatDialogShowing.value = false }
                    typeSelectDialog.show()
                }
            }

            CoGuestUIState.REQUESTING -> {
                icon.setImageResource(R.drawable.livekit_function_link_request)
                icon.setOnClickListener {
                    if (!::audienceStore.isInitialized) return@setOnClickListener
                    val linkMicDialog = CancelRequestDialog(context, audienceStore)
                    linkMicDialog.show()
                }
            }

            CoGuestUIState.CONNECTED -> {
                icon.setImageResource(R.drawable.livekit_function_linked)
                icon.setOnClickListener {
                    if (!::audienceStore.isInitialized) return@setOnClickListener
                    val stopCoGuestDialog = StopCoGuestDialog(context, audienceStore)
                    stopCoGuestDialog.show()
                }
            }
        }
    }

    private fun resolveCoGuestUIState(): CoGuestUIState {
        if (!::audienceStore.isInitialized) return CoGuestUIState.IDLE
        val coGuestState = audienceStore.getCoGuestState()
        val viewState = audienceStore.getViewState()
        val selfUserId = LoginStore.shared.loginState.loginUserInfo.value?.userID
        val isConnected = coGuestState.connected.value.any { it.userID == selfUserId }
        return when {
            isConnected -> CoGuestUIState.CONNECTED
            viewState.isApplyingToTakeSeat.value -> CoGuestUIState.REQUESTING
            else -> CoGuestUIState.IDLE
        }
    }

    private fun bindCoGuestState() {
        if (!::audienceStore.isInitialized) return
        val coGuestState = audienceStore.getCoGuestState()
        val viewState = audienceStore.getViewState()

        stateScope?.launch {
            coGuestState.connected.collect {
                updateCoGuestUI(resolveCoGuestUIState())
                updateWaitingCoGuestPassView()
            }
        }
        stateScope?.launch {
            viewState.isApplyingToTakeSeat.collect {
                updateCoGuestUI(resolveCoGuestUIState())
                updateWaitingCoGuestPassView()
            }
        }
    }

    private fun bindCoHostState() {
        if (!::audienceStore.isInitialized) return
        val liveID = audienceStore.getLiveListState().currentLive.value.liveID
        if (liveID.isEmpty()) return
        stateScope?.launch {
            val coHostStore = CoHostStore.create(liveID)
            coHostStore.coHostState.connected.collect { connectedList ->
                enableCoGuest(connectedList.isEmpty())
            }
        }
    }

    var onWaitingCoGuestPassViewUpdate: ((visible: Boolean) -> Unit)? = null

    private fun updateWaitingCoGuestPassView() {
        val state = resolveCoGuestUIState()
        onWaitingCoGuestPassViewUpdate?.invoke(state == CoGuestUIState.REQUESTING)
    }

    private fun buildDefaultButtons(liveInfo: LiveInfo) {
        cancelStateObservation()
        removeAllViews()
        imageCoGuest = null
        imageMore = null
        buttonGift = null
        buttonLike = null
        stateScope = CoroutineScope(Dispatchers.Main + Job())

        val gift = GiftButton(context).apply {
            layoutParams = createButtonLayoutParams()
        }
        gift.init(
            liveInfo.liveID,
            liveInfo.liveOwner.userID,
            liveInfo.liveOwner.userName,
            liveInfo.liveOwner.avatarURL
        )
        buttonGift = gift
        addView(gift)

        val coGuest = ImageView(context).apply {
            layoutParams = createButtonLayoutParams()
            scaleType = ImageView.ScaleType.CENTER_CROP
            setImageResource(R.drawable.livekit_function_link_default)
        }
        imageCoGuest = coGuest
        addView(coGuest)
        updateCoGuestUI(resolveCoGuestUIState())
        bindCoGuestState()

        val like = LikeButton(context).apply {
            layoutParams = createButtonLayoutParams()
        }
        like.init(liveInfo.liveID)
        buttonLike = like
        addView(like)

        val more = ImageView(context).apply {
            layoutParams = createButtonLayoutParams()
            scaleType = ImageView.ScaleType.CENTER_CROP
            setImageResource(R.drawable.livekit_function_more)
        }
        imageMore = more
        addView(more)
        more.setOnClickListener { showMorePanel() }

        bindCoHostState()
    }

    private fun rebuildButtons() {
        cancelStateObservation()
        removeAllViews()
        imageCoGuest = null
        imageMore = null
        buttonGift = null
        buttonLike = null
        stateScope = CoroutineScope(Dispatchers.Main + Job())

        for (item in items) {
            val itemView = createItemView(item) ?: continue
            addView(itemView)
        }

        if (imageCoGuest != null) {
            bindCoGuestState()
            bindCoHostState()
        }
    }

    private fun createItemView(item: AudienceBottomItem): View? {
        return when (item) {
            is AudienceBottomItem.Gift -> {
                val view = GiftButton(context).apply {
                    layoutParams = createButtonLayoutParams()
                }
                if (::audienceStore.isInitialized) {
                    val currentLive = audienceStore.getLiveListState().currentLive.value
                    if (currentLive.liveID.isNotEmpty()) {
                        view.init(
                            currentLive.liveID,
                            currentLive.liveOwner.userID,
                            currentLive.liveOwner.userName,
                            currentLive.liveOwner.avatarURL
                        )
                    }
                }
                buttonGift = view
                view
            }

            is AudienceBottomItem.CoGuest -> {
                val view = ImageView(context).apply {
                    layoutParams = createButtonLayoutParams()
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    setImageResource(R.drawable.livekit_function_link_default)
                }
                imageCoGuest = view
                updateCoGuestUI(resolveCoGuestUIState())
                view
            }

            is AudienceBottomItem.Like -> {
                val view = LikeButton(context).apply {
                    layoutParams = createButtonLayoutParams()
                }
                if (::audienceStore.isInitialized) {
                    val liveID = audienceStore.getLiveListState().currentLive.value.liveID
                    if (liveID.isNotEmpty()) {
                        view.init(liveID)
                    }
                }
                buttonLike = view
                view
            }

            is AudienceBottomItem.More -> {
                val view = ImageView(context).apply {
                    layoutParams = createButtonLayoutParams()
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    setImageResource(R.drawable.livekit_function_more)
                }
                imageMore = view
                view.setOnClickListener { showMorePanel() }
                view
            }

            is AudienceBottomItem.Custom -> {
                (item.view.parent as? ViewGroup)?.removeView(item.view)
                if (item.view.layoutParams == null) {
                    item.view.layoutParams = createButtonLayoutParams()
                }
                item.view
            }
        }
    }

    private fun createButtonLayoutParams(): LayoutParams {
        return LayoutParams(buttonSize, buttonSize).apply {
            marginStart = buttonSpacing
        }
    }

    private fun enableView(view: View, enable: Boolean) {
        view.isEnabled = enable
        view.alpha = if (enable) 1.0f else 0.5f
    }

    fun cancelStateObservation() {
        stateScope?.cancel()
        stateScope = null
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        cancelStateObservation()
    }
}
