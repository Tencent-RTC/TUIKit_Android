package com.trtc.uikit.livekit.voiceroom.view

import android.content.Context
import android.os.Bundle
import android.view.WindowManager
import com.trtc.uikit.livekit.R
import com.trtc.uikit.livekit.voiceroom.VoiceRoomDefine
import com.trtc.uikit.livekit.voiceroom.view.TUIVoiceRoomFragment.RoomBehavior
import com.trtc.uikit.livekit.voiceroom.view.TUIVoiceRoomFragment.RoomParams
import io.trtc.tuikit.atomicx.common.FullScreenActivity

class VoiceRoomActivity : FullScreenActivity() {

    companion object {
        const val INTENT_KEY_ROOM_ID = "intent_key_room_id"
        const val INTENT_KEY_CREATE_ROOM_PARAMS = "intent_key_create_room_params"
        const val INTENT_KEY_ROOM_BEHAVIOR = "intent_key_room_behavior"
    }

    override fun attachBaseContext(context: Context?) {
        super.attachBaseContext(context)
        context?.let {
            val configuration = it.resources.configuration
            configuration.fontScale = 1f
            applyOverrideConfiguration(configuration)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(null)
        setContentView(R.layout.livekit_activity_video_live_audience)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val roomId = intent.getStringExtra(INTENT_KEY_ROOM_ID)!!
        val behavior = RoomBehavior.values()[intent.getIntExtra(INTENT_KEY_ROOM_BEHAVIOR, 0)]
        val fragmentManager = supportFragmentManager
        val fragmentTransaction = fragmentManager.beginTransaction()
        val params = RoomParams()
        val createRoomParams = intent.getSerializableExtra(INTENT_KEY_CREATE_ROOM_PARAMS) as? VoiceRoomDefine.CreateRoomParams
        createRoomParams?.let {
            params.maxSeatCount = it.maxAnchorCount
            params.seatMode = it.seatMode
        }
        val voiceRoomFragment = TUIVoiceRoomFragment(roomId, behavior, params)
        fragmentTransaction.add(R.id.fl_container, voiceRoomFragment)
        fragmentTransaction.commit()
    }
}