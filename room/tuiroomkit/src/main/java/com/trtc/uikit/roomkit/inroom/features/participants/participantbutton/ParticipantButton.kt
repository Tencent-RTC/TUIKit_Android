package com.trtc.uikit.roomkit.inroom.features.participants.participantbutton

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import com.trtc.uikit.roomkit.R
import com.trtc.uikit.roomkit.base.popup.RoomPopup
import com.trtc.uikit.roomkit.inroom.common.toolbarbutton.RoomToolbarButton
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

    private val toolbarButton: RoomToolbarButton
    private val sheet = RoomPopup(context)
    private var roomID: String = ""
    private var subscribeJob: Job? = null
    private var sheetContent: ParticipantListView? = null

    init {
        LayoutInflater.from(context).inflate(R.layout.roomkit_participant_button, this, true)
        toolbarButton = findViewById(R.id.participant_toolbar_button)
        toolbarButton.icon = context.getDrawable(R.drawable.roomkit_icon_members)
        toolbarButton.setOnClickListener { showSheet() }
    }

    fun bind(roomID: String) {
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

    private fun showSheet() {
        if (sheetContent == null) {
            sheetContent = ParticipantListView(context).also {
                it.bind(roomID)
                sheet.setContentView(it)
            }
        }
        sheet.show()
    }

    private fun observeParticipants() {
        subscribeJob?.cancel()
        subscribeJob = CoroutineScope(Dispatchers.Main).launch {
            RoomParticipantStore.create(roomID).state.participantList.collect { participants ->
                toolbarButton.badgeCount = participants.size
            }
        }
    }
}
