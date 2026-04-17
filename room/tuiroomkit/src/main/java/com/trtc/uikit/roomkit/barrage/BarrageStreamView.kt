package com.trtc.uikit.roomkit.barrage

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.trtc.uikit.roomkit.R
import com.trtc.uikit.roomkit.barrage.view.CustomRecyclerView
import com.trtc.uikit.roomkit.barrage.view.IBarrageDisplayView
import com.trtc.uikit.roomkit.barrage.view.adapter.BarrageItemAdapter
import com.trtc.uikit.roomkit.barrage.view.adapter.BarrageItemDefaultAdapter
import com.trtc.uikit.roomkit.barrage.view.adapter.BarrageItemTypeDelegate
import com.trtc.uikit.roomkit.barrage.view.adapter.BarrageMsgListAdapter
import com.trtc.uikit.roomkit.base.ui.BaseView
import io.trtc.tuikit.atomicxcore.api.barrage.Barrage
import io.trtc.tuikit.atomicxcore.api.barrage.BarrageStore
import io.trtc.tuikit.atomicxcore.api.live.LiveUserInfo
import io.trtc.tuikit.atomicxcore.api.room.RoomParticipantStore
import io.trtc.tuikit.atomicxcore.api.room.RoomStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@SuppressLint("ViewConstructor")
class BarrageStreamView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : BaseView(context, attrs, defStyleAttr), IBarrageDisplayView {

    companion object {
        private const val BARRAGE_LIST_UPDATE_DURATION_MS = 250L
        private const val SMOOTH_SCROLL_COUNT_MAX = 100
    }

    private var timestampOnLastUpdate = 0L
    private var smoothScroll = true

    private val updateViewTask = Runnable { notifyDataSetChanged() }

    private val recyclerMsg: RecyclerView by lazy { findViewById(R.id.rv_msg) }
    private val msgList = mutableListOf<Barrage>()
    private val adapter = BarrageMsgListAdapter(msgList)
    private var barrageStore: BarrageStore? = null
    private val scope = CoroutineScope(Dispatchers.Main)
    private var subscribeJob: Job? = null
    private var defaultAdapter: BarrageItemDefaultAdapter
    private val roomStore = RoomStore.shared()
    private var participantStore: RoomParticipantStore? = null

    init {
        LayoutInflater.from(context).inflate(R.layout.roomkit_barrage_view_display, this)
        recyclerMsg.layoutManager = LinearLayoutManager(context)
        defaultAdapter = BarrageItemDefaultAdapter(context).apply {
            onRoleInfoChangedListener = {
                notifyDataSetChanged()
            }
        }
        recyclerMsg.adapter = adapter
        adapter.setItemAdapter(0, defaultAdapter)
    }

    public override fun init(roomID: String) {
        super.init(roomID)
        this.roomID = roomID
        participantStore = RoomParticipantStore.create(roomID)
    }

    fun setItemTypeDelegate(delegate: BarrageItemTypeDelegate) {
        adapter.setItemTypeDelegate(delegate)
    }

    fun setItemAdapter(itemType: Int, adapter: BarrageItemAdapter) {
        this.adapter.setItemAdapter(itemType, adapter)
    }

    fun setOnMessageClickListener(listener: OnMessageClickListener) {
        adapter.setOnMessageClickListener(listener)
    }

    override fun initStore(roomID: String) {
        barrageStore = BarrageStore.create(roomID)
    }

    override fun addObserver() {
        subscribeJob?.cancel()
        subscribeJob = scope.launch {
            launch {
                barrageStore?.barrageState?.messageList?.collect {
                    onBarrageListChanged(it)
                }
            }

            launch {
                participantStore?.state?.adminList?.collect { adminList ->
                    val adminIds = adminList.map { it.userID }.toSet()
                    defaultAdapter.setAdminIds(adminIds)
                }
            }

            launch {
                roomStore.state.currentRoom.collect { roomInfo ->
                    roomInfo?.let {
                        defaultAdapter.setOwnerId(it.roomOwner.userID)
                    }
                }
            }
        }
    }

    override fun removeObserver() {
        subscribeJob?.cancel()
    }

    override fun insertBarrages(vararg barrages: Barrage) {
        barrages.forEach {
            barrageStore?.appendLocalTip(it)
        }
    }

    fun onBarrageListChanged(barrages: List<Barrage>) {
        smoothScroll = barrages.size - msgList.size < SMOOTH_SCROLL_COUNT_MAX
        msgList.clear()
        msgList.addAll(barrages)
        removeCallbacks(updateViewTask)

        if (System.currentTimeMillis() - timestampOnLastUpdate >= BARRAGE_LIST_UPDATE_DURATION_MS) {
            notifyDataSetChanged()
        } else {
            postDelayed(updateViewTask, BARRAGE_LIST_UPDATE_DURATION_MS)
        }
    }

    fun getBarrageCount(): Int = barrageStore?.barrageState?.messageList?.value?.size ?: 0

    @SuppressLint("NotifyDataSetChanged")
    private fun notifyDataSetChanged() {
        timestampOnLastUpdate = System.currentTimeMillis()
        adapter.notifyDataSetChanged()
        if ((recyclerMsg as CustomRecyclerView).isLongPressed) return

        val targetPosition = maxOf(0, adapter.itemCount - 1)
        if (smoothScroll) {
            recyclerMsg.smoothScrollToPosition(targetPosition)
        } else {
            recyclerMsg.scrollToPosition(targetPosition)
        }
    }

    interface OnMessageClickListener {
        fun onMessageClick(userInfo: LiveUserInfo)
    }
}
