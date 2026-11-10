package com.trtc.uikit.roomkit.inroom.features.participants.participantbutton

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.AppCompatTextView
import com.trtc.uikit.roomkit.R
import com.trtc.uikit.roomkit.base.popup.RoomPopup
import com.trtc.uikit.roomkit.inroom.features.participants.participantlist.ParticipantListView
import io.trtc.tuikit.atomicxcore.api.room.RoomParticipantStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class ParticipantButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    var isActive: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            background = AppCompatResources.getDrawable(
                context,
                if (value) R.drawable.roomkit_bg_toolbar_button_active else R.drawable.roomkit_bg_toolbar_button,
            )
        }

    private val countText: AppCompatTextView
    private val popup = RoomPopup(context)
    private var roomID: String = ""
    private var subscribeJob: Job? = null
    private var sheetContent: ParticipantListView? = null

    private var count: Int = 0
        set(value) {
            if (field == value) return
            field = value
            if (value <= 0) {
                countText.visibility = GONE
            } else {
                countText.visibility = VISIBLE
                countText.text = if (value > COUNT_MAX) "$COUNT_MAX+" else value.toString()
            }
        }

    init {
        background = AppCompatResources.getDrawable(context, R.drawable.roomkit_bg_toolbar_button)
        LayoutInflater.from(context).inflate(R.layout.roomkit_participant_button, this, true)
        countText = findViewById(R.id.participant_count)
        setOnClickListener { showPopup() }
    }

    fun init(roomID: String) {
        this.roomID = roomID
        if (isAttachedToWindow) observeParticipants()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (roomID.isNotBlank()) observeParticipants()
    }

    override fun onDetachedFromWindow() {
        subscribeJob?.cancel()
        subscribeJob = null
        super.onDetachedFromWindow()
    }

    override fun setPressed(pressed: Boolean) {
        super.setPressed(pressed)
        val scale = if (pressed) PRESSED_SCALE else 1f
        animate().setDuration(PRESSED_DURATION.toLong()).scaleX(scale).scaleY(scale)
    }

    private fun showPopup() {
        if (sheetContent == null) {
            sheetContent = ParticipantListView(context).also {
                it.init(roomID)
                popup.setContentView(it)
            }
        }
        popup.show()
    }

    private fun observeParticipants() {
        subscribeJob?.cancel()
        subscribeJob = CoroutineScope(Dispatchers.Main).launch {
            RoomParticipantStore.create(roomID).state.participantList.collect { participants ->
                count = participants.size
            }
        }
    }

    private companion object {
        const val COUNT_MAX = 99
        const val PRESSED_SCALE = 0.92f
        const val PRESSED_DURATION = 80
    }
}
