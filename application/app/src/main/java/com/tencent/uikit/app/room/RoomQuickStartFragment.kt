package com.tencent.uikit.app.room

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.tencent.uikit.app.main.MainActivity
import com.trtc.uikit.roomkit.inroom.views.MeetingActivity

class RoomQuickStartFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        return RoomQuickStartView(requireContext()).apply {
            onBack = { requireActivity().onBackPressedDispatcher.onBackPressed() }
            onCreateMeeting = callback@{ roomID, roomName, isMicrophoneOn, isSpeakerOn, isCameraOn ->
                if (!isAdded || view == null || parentFragmentManager.isStateSaved) return@callback
                val intent = Intent(requireContext(), MeetingActivity::class.java).apply {
                    putExtra(MeetingActivity.EXTRA_ROOM_ID, roomID)
                    putExtra(MeetingActivity.EXTRA_ROOM_NAME, roomName)
                    putExtra(MeetingActivity.EXTRA_IS_CREATE, true)
                    putExtra(MeetingActivity.EXTRA_AUTO_ENABLE_MICROPHONE, isMicrophoneOn)
                    putExtra(MeetingActivity.EXTRA_AUTO_ENABLE_CAMERA, isCameraOn)
                    putExtra(MeetingActivity.EXTRA_AUTO_ENABLE_SPEAKER, isSpeakerOn)
                }
                (requireActivity() as MainActivity).openMeeting(intent)
            }
        }
    }
}
