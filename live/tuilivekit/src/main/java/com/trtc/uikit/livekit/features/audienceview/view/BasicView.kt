package com.trtc.uikit.livekit.features.audienceview.view

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import com.trtc.uikit.livekit.features.audienceview.store.AudienceStore
import com.trtc.uikit.livekit.features.audienceview.store.IMState
import com.trtc.uikit.livekit.features.audienceview.store.IMStore
import com.trtc.uikit.livekit.features.audienceview.store.MediaState
import com.trtc.uikit.livekit.features.audienceview.store.MediaStore
import com.trtc.uikit.livekit.features.audienceview.store.ViewState
import io.trtc.tuikit.atomicxcore.api.live.BattleStore
import io.trtc.tuikit.atomicxcore.api.live.CoGuestState
import io.trtc.tuikit.atomicxcore.api.live.CoGuestStore
import io.trtc.tuikit.atomicxcore.api.live.CoHostState
import io.trtc.tuikit.atomicxcore.api.live.LiveAudienceStore
import io.trtc.tuikit.atomicxcore.api.live.LiveListState
import io.trtc.tuikit.atomicxcore.api.live.LiveListStore
import io.trtc.tuikit.atomicxcore.api.live.LiveSeatState
import io.trtc.tuikit.atomicxcore.api.live.LiveSeatStore
import kotlinx.coroutines.Job

abstract class BasicView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    protected lateinit var audienceStore: AudienceStore
    protected lateinit var liveListStore: LiveListStore
    protected lateinit var liveSeatStore: LiveSeatStore
    protected lateinit var liveAudienceStore: LiveAudienceStore
    protected lateinit var coGuestStore: CoGuestStore
    protected lateinit var battleStore: BattleStore
    protected lateinit var imStore: IMStore
    protected lateinit var mediaStore: MediaStore
    protected lateinit var liveListState: LiveListState
    protected lateinit var liveSeatState: LiveSeatState
    protected lateinit var coGuestState: CoGuestState
    protected lateinit var coHostState: CoHostState
    protected lateinit var imState: IMState
    protected lateinit var mediaState: MediaState
    protected lateinit var viewState: ViewState
    protected var subscribeStateJob: Job? = null
    private var isAddObserver = false

    protected fun isStoreInitialized(): Boolean = ::audienceStore.isInitialized

    init {
        initView()
    }

    open fun init(store: AudienceStore) {
        this.audienceStore = store
        liveListStore = store.getLiveListStore()
        liveListState = store.getLiveListState()
        liveSeatStore = store.getLiveSeatStore()
        liveAudienceStore = store.getLiveAudienceStore()
        battleStore = store.getBattleStore()
        liveSeatState = store.getLiveSeatState()
        coGuestStore = store.getCoGuestStore()
        coHostState = store.getCoHostState()
        imStore = store.getIMStore()
        mediaStore = store.getMediaStore()
        coGuestState = store.getCoGuestState()
        imState = store.getIMState()
        mediaState = store.getMediaState()
        viewState = store.getViewState()
        refreshView()
        if (!isAddObserver) {
            addObserver()
            isAddObserver = true
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!::audienceStore.isInitialized) {
            return
        }
        if (!isAddObserver) {
            addObserver()
            isAddObserver = true
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        if (isAddObserver) {
            removeObserver()
            isAddObserver = false
        }
    }

    protected abstract fun initView()

    protected open fun refreshView() {
    }

    protected abstract fun addObserver()

    protected abstract fun removeObserver()
}
