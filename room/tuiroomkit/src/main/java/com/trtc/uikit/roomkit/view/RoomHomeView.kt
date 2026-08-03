package com.trtc.uikit.roomkit.view

import android.content.Context
import android.content.Intent
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.ActivityResultLauncher
import androidx.constraintlayout.utils.widget.ImageFilterView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.trtc.uikit.roomkit.R
import com.trtc.uikit.roomkit.RoomCreateActivity
import com.trtc.uikit.roomkit.RoomJoinActivity
import com.trtc.uikit.roomkit.RoomScheduleActivity
import com.trtc.uikit.roomkit.RoomScheduledDetailActivity
import com.trtc.uikit.roomkit.base.error.ErrorLocalized
import com.trtc.uikit.roomkit.base.extension.getDisplayName
import com.trtc.uikit.roomkit.base.log.RoomKitLogger
import com.trtc.uikit.roomkit.view.schedule.RoomScheduledRoomAdapter
import com.trtc.uikit.roomkit.view.schedule.RoomScheduledRoomItemDecoration
import io.trtc.tuikit.atomicx.common.imageloader.ImageLoader
import io.trtc.tuikit.atomicxcore.api.ListResultCompletionHandler
import io.trtc.tuikit.atomicxcore.api.login.LoginStore
import io.trtc.tuikit.atomicxcore.api.login.UserProfile
import io.trtc.tuikit.atomicxcore.api.room.RoomInfo
import io.trtc.tuikit.atomicxcore.api.room.RoomStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class RoomHomeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val logger = RoomKitLogger.getLogger("RoomHomeView")

    private val ivBack: ImageView by lazy { findViewById(R.id.iv_back) }
    private val ivUserAvatar: ImageFilterView by lazy { findViewById(R.id.iv_user_avatar) }
    private val tvUserName: TextView by lazy { findViewById(R.id.tv_user_name) }
    private val llJoinRoom: LinearLayout by lazy { findViewById(R.id.btn_join_room) }
    private val llCreateRoom: LinearLayout by lazy { findViewById(R.id.btn_create_room) }
    private val llScheduleRoom: LinearLayout by lazy { findViewById(R.id.btn_schedule_room) }
    private val rvScheduledRooms: RecyclerView by lazy { findViewById(R.id.rv_scheduled_rooms) }
    private val llNoScheduledRoom: View by lazy { findViewById(R.id.ll_no_scheduled_room) }

    private val loginStore: LoginStore = LoginStore.shared
    private val roomStore: RoomStore = RoomStore.shared()
    private val scheduledRoomAdapter = RoomScheduledRoomAdapter(context) { roomInfo ->
        val intent = Intent(context, RoomScheduledDetailActivity::class.java)
        intent.putExtra(RoomScheduledDetailActivity.EXTRA_ROOM_ID, roomInfo.roomID)
        context.startActivity(intent)
    }
    private var subscribeJob: Job? = null
    private var hasInitialFetched = false

    /** Host-injected launcher used to receive the schedule-page result; falls back to startActivity if null. */
    var scheduleRoomLauncher: ActivityResultLauncher<Intent>? = null

    init {
        LayoutInflater.from(context).inflate(R.layout.roomkit_view_home, this)
        initView()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        addObserver()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        removeObserver()
        hasInitialFetched = false
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility == VISIBLE && isAttachedToWindow) {
            if (hasInitialFetched) {
                fetchScheduledRoomList(null)
            } else {
                hasInitialFetched = true
            }
        }
    }

    private fun addObserver() {
        subscribeJob?.cancel()
        subscribeJob = CoroutineScope(Dispatchers.Main).launch {
            launch {
                loginStore.loginState.loginUserInfo.collect { loginUserInfo ->
                    loginUserInfo?.let { updateUserInfo(it) }
                }
            }
            launch {
                roomStore.state.scheduledRoomList.collect { list ->
                    updateScheduledRoomList(list)
                }
            }
        }
        fetchScheduledRoomList(null)
    }

    private fun fetchScheduledRoomList(cursor: String?) {
        roomStore.getScheduledRoomList(cursor, object : ListResultCompletionHandler<RoomInfo> {
            override fun onSuccess(result: List<RoomInfo>, cursor: String) {
                if (!isAttachedToWindow) return
                if (cursor.isEmpty()) return
                fetchScheduledRoomList(cursor)
            }

            override fun onFailure(code: Int, desc: String) {
                logger.error("getScheduledRoomList failed: code=$code, desc=$desc")
                ErrorLocalized.showError(context, code)
            }
        })
    }

    private fun removeObserver() {
        subscribeJob?.cancel()
        subscribeJob = null
    }

    private fun initView() {
        ivBack.setOnClickListener { handleBackClick() }
        llJoinRoom.setOnClickListener { handleJoinRoomClick() }
        llCreateRoom.setOnClickListener { handleCreateRoomClick() }
        llScheduleRoom.setOnClickListener { handleScheduleRoomClick() }

        rvScheduledRooms.layoutManager = LinearLayoutManager(context)
        rvScheduledRooms.adapter = scheduledRoomAdapter
        rvScheduledRooms.addItemDecoration(RoomScheduledRoomItemDecoration())
    }

    private fun updateUserInfo(userInfo: UserProfile) {
        tvUserName.text = userInfo.getDisplayName()
        if (userInfo.avatarURL.isNullOrEmpty()) {
            ivUserAvatar.setImageResource(R.drawable.roomkit_ic_default_avatar)
        } else {
            ImageLoader.load(context, ivUserAvatar, userInfo.avatarURL, R.drawable.roomkit_ic_default_avatar)
        }
    }

    private fun updateScheduledRoomList(list: List<*>?) {
        val rooms = list?.filterIsInstance<RoomInfo>() ?: emptyList()
        if (rooms.isEmpty()) {
            rvScheduledRooms.visibility = GONE
            llNoScheduledRoom.visibility = VISIBLE
        } else {
            rvScheduledRooms.visibility = VISIBLE
            llNoScheduledRoom.visibility = GONE
            scheduledRoomAdapter.setDataList(rooms)
        }
    }

    private fun handleBackClick() {
        (context as? android.app.Activity)?.finish()
    }

    private fun handleJoinRoomClick() {
        context.startActivity(Intent(context, RoomJoinActivity::class.java))
    }

    private fun handleCreateRoomClick() {
        context.startActivity(Intent(context, RoomCreateActivity::class.java))
    }

    private fun handleScheduleRoomClick() {
        val intent = Intent(context, RoomScheduleActivity::class.java)
        scheduleRoomLauncher?.launch(intent) ?: context.startActivity(intent)
    }
}
