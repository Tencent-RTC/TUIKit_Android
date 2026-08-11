package com.trtc.uikit.roomkit.view.barrage

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import com.trtc.uikit.roomkit.R
import com.trtc.uikit.roomkit.base.ui.BaseView
import io.trtc.tuikit.atomicxcore.api.barrage.BarrageStore
import io.trtc.tuikit.atomicxcore.api.room.RoomInfo
import io.trtc.tuikit.atomicxcore.api.room.RoomListener
import io.trtc.tuikit.atomicxcore.api.room.RoomStore

@SuppressLint("ViewConstructor")
class BarrageInputView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : BaseView(context, attrs, defStyleAttr) {

    private var barrageSendView: BarrageSendView? = null
    private var barrageStore: BarrageStore? = null

    private val roomListener = object : RoomListener() {
        override fun onRoomEnded(roomInfo: RoomInfo) {
            barrageSendView?.dismiss()
        }
    }

    init {
        LayoutInflater.from(context).inflate(R.layout.roomkit_barrage_view_send, this)
        initView()
    }

    public override fun init(roomID: String) {
        this.roomID = roomID
    }

    override fun initStore(roomID: String) {
        barrageStore = BarrageStore.create(roomID)
    }

    private fun initView() {
        barrageSendView = BarrageSendView(context, roomID)
        setOnClickListener {
            barrageSendView?.also { view ->
                if (!view.isShowing) {
                    view.show(false)
                }
            }
        }
        findViewById<View>(R.id.rl_emoticons).setOnClickListener {
            barrageSendView?.also { view ->
                if (!view.isShowing) {
                    view.show(true)
                }
            }
        }
    }

    override fun addObserver() {
        RoomStore.shared().addRoomListener(roomListener)
    }

    override fun removeObserver() {
        RoomStore.shared().removeRoomListener(roomListener)
    }
}
