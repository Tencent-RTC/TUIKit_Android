package com.trtc.uikit.roomkit.inroom.features.roominfo.currentroominfo

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.appcompat.widget.AppCompatTextView
import com.trtc.uikit.roomkit.R
import com.trtc.uikit.roomkit.base.popup.RoomPopup
import io.trtc.tuikit.atomicxcore.api.room.RoomInfo
import io.trtc.tuikit.atomicxcore.api.room.RoomStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class RoomInfoView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    private val chip: View
    private val caret: ImageView
    private val roomNameText: AppCompatTextView
    private val elapsedText: AppCompatTextView
    private var isDetailOpen = false
    private val roomStore = RoomStore.shared()
    private var roomID: String = ""
    private var subscribeJob: Job? = null
    private var timerBaseMillis: Long = 0L
    private var timerBaseRoomID: String? = null

    private val elapsedTicker = object : Runnable {
        override fun run() {
            updateElapsed()
            postDelayed(this, 1000L)
        }
    }

    init {
        LayoutInflater.from(context).inflate(R.layout.roomkit_room_info, this, true)
        chip = findViewById(R.id.room_info_chip)
        caret = findViewById(R.id.room_info_caret)
        roomNameText = findViewById(R.id.room_name_text)
        elapsedText = findViewById(R.id.elapsed_text)
        chip.setOnClickListener { showInfoPopup() }
    }

    private var infoPopup: RoomPopup? = null

    private fun showInfoPopup() {
        if (roomID.isBlank()) return
        val popup = infoPopup ?: RoomPopup(context).also { infoPopup = it }
        val content = RoomInfoPopup(context).apply { init(roomID) }
        popup.setContentView(content, RoomPopup.ContentHeight.AUTO)
        popup.onDismiss = { setDetailOpen(false) }
        popup.show()
        setDetailOpen(true)
    }

    private fun setDetailOpen(open: Boolean) {
        if (isDetailOpen == open) return
        isDetailOpen = open
        caret.animate()
            .rotation(if (open) 180f else 0f)
            .setDuration(250L)
            .start()
        caret.setImageResource(
            if (open) R.drawable.roomkit_icon_collapse_accent
            else R.drawable.roomkit_icon_collapse_tertiary
        )
    }

    fun init(roomID: String) {
        this.roomID = roomID
        if (isAttachedToWindow) observeRoom()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (roomID.isNotBlank()) observeRoom()
        removeCallbacks(elapsedTicker)
        post(elapsedTicker)
    }

    override fun onDetachedFromWindow() {
        subscribeJob?.cancel()
        subscribeJob = null
        removeCallbacks(elapsedTicker)
        super.onDetachedFromWindow()
    }

    private fun observeRoom() {
        subscribeJob?.cancel()
        subscribeJob = CoroutineScope(Dispatchers.Main).launch {
            roomStore.state.currentRoom.collect { room ->
                val info = room?.takeIf { it.roomID == roomID } ?: return@collect
                updateRoomInfo(info)
            }
        }
    }

    private fun updateRoomInfo(info: RoomInfo) {
        if (info.roomName.isNotEmpty() && roomNameText.text?.toString() != info.roomName) {
            roomNameText.text = info.roomName
        }
        if (timerBaseRoomID != info.roomID) {
            timerBaseRoomID = info.roomID
            timerBaseMillis = normalizeToMillis(info.createTime)
        }
        updateElapsed()
    }

    private fun normalizeToMillis(raw: Long): Long {
        if (raw <= 0L) return 0L
        return if (raw >= MILLIS_MAGNITUDE) raw else raw * 1000L
    }

    private fun updateElapsed() {
        if (timerBaseMillis <= 0) return
        val totalSeconds = (System.currentTimeMillis() - timerBaseMillis) / 1000L
            .coerceAtLeast(0L)
        val minutes = totalSeconds / SECONDS_PER_MINUTE
        val seconds = totalSeconds % SECONDS_PER_MINUTE
        elapsedText.text = if (minutes < MINUTES_PER_HOUR) {
            String.format("%02d:%02d", minutes, seconds)
        } else {
            String.format(
                "%02d:%02d:%02d",
                minutes / MINUTES_PER_HOUR,
                minutes % MINUTES_PER_HOUR,
                seconds,
            )
        }
    }

    private companion object {
        const val SECONDS_PER_MINUTE = 60L
        const val MINUTES_PER_HOUR = 60L
        const val MILLIS_MAGNITUDE = 1_000_000_000_000L
    }
}
