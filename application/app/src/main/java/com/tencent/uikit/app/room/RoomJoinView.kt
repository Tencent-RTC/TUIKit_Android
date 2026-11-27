package com.tencent.uikit.app.room

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import androidx.appcompat.widget.SwitchCompat
import com.trtc.uikit.roomkit.R
import com.trtc.uikit.roomkit.base.toast.RoomToast
import com.trtc.uikit.roomkit.base.util.applyBottomWindowInsets
import com.trtc.uikit.roomkit.base.util.applyTopWindowInsets

class RoomJoinView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    private val roomIDInput: EditText
    private val microphoneSwitch: SwitchCompat
    private val speakerSwitch: SwitchCompat
    private val cameraSwitch: SwitchCompat

    var onBack: (() -> Unit)? = null
    var onJoin: ((String, Boolean, Boolean, Boolean) -> Unit)? = null

    init {
        LayoutInflater.from(context).inflate(R.layout.roomkit_join_view, this, true)

        roomIDInput = findViewById(R.id.room_id_input)
        microphoneSwitch = findViewById(R.id.microphone_switch)
        speakerSwitch = findViewById(R.id.speaker_switch)
        cameraSwitch = findViewById(R.id.camera_switch)

        findViewById<View>(R.id.toolbar).applyTopWindowInsets()
        findViewById<View>(R.id.nav_back_button).setOnClickListener {
            onBack?.invoke()
        }
        findViewById<View>(R.id.submit_button).setOnClickListener {
            joinWithCurrentInput()
        }
    }

    private fun joinWithCurrentInput() {
        val roomID = roomIDInput.text?.toString()?.trim().orEmpty()
        if (roomID.isEmpty()) {
            RoomToast.show(context, context.getString(com.tencent.uikit.app.R.string.app_pre_room_room_id_empty))
            return
        }
        onJoin?.invoke(
            roomID,
            microphoneSwitch.isChecked,
            speakerSwitch.isChecked,
            cameraSwitch.isChecked,
        )
    }
}
