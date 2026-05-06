package com.trtc.uikit.livekit.features.anchorview.view

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import com.trtc.uikit.livekit.features.anchorview.store.AnchorBattleState
import com.trtc.uikit.livekit.features.anchorview.store.AnchorBattleStore
import com.trtc.uikit.livekit.features.anchorview.store.AnchorCoHostState
import com.trtc.uikit.livekit.features.anchorview.store.AnchorCoHostStore
import com.trtc.uikit.livekit.features.anchorview.store.AnchorStoreState
import com.trtc.uikit.livekit.features.anchorview.store.AnchorStore
import com.trtc.uikit.livekit.features.anchorview.store.MediaState
import com.trtc.uikit.livekit.features.anchorview.store.MediaStore
import com.trtc.uikit.livekit.features.anchorview.store.UserState
import com.trtc.uikit.livekit.features.anchorview.store.UserStore

abstract class BasicView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    protected val baseContext: Context = context
    protected var anchorState: AnchorStoreState? = null
    protected var coHostState: AnchorCoHostState? = null
    protected var battleState: AnchorBattleState? = null
    protected var userState: UserState? = null
    protected var mediaState: MediaState? = null
    protected var anchorStore: AnchorStore? = null
    protected var anchorCoHostStore: AnchorCoHostStore? = null
    protected var anchorBattleStore: AnchorBattleStore? = null
    protected var userStore: UserStore? = null
    protected var mediaStore: MediaStore? = null
    private var isAddObserver = false

    init {
        initView()
    }

    fun init(anchorStore: AnchorStore) {
        this@BasicView.anchorStore = anchorStore
        userStore = anchorStore.getUserStore()
        mediaStore = anchorStore.getMediaStore()
        anchorCoHostStore = anchorStore.getAnchorCoHostStore()
        anchorBattleStore = anchorStore.getAnchorBattleStore()
        anchorState = anchorStore.getState()
        userState = anchorStore.getUserState()
        mediaState = anchorStore.getMediaState()
        coHostState = anchorStore.getCoHostState()
        battleState = anchorStore.getBattleState()

        refreshView()
        if (!isAddObserver) {
            addObserver()
            isAddObserver = true
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (anchorStore == null) {
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
    protected abstract fun refreshView()
    protected abstract fun addObserver()
    protected abstract fun removeObserver()
}