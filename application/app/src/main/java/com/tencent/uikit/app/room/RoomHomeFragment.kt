package com.tencent.uikit.app.room

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.tencent.uikit.app.main.MainActivity
import com.trtc.uikit.roomkit.inroom.features.setting.RoomSettingsDialog

class RoomHomeFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val host = requireActivity() as MainActivity
        return RoomHomeView(requireContext()).apply {
            onQuickStart = { host.pushPage(RoomQuickStartFragment()) }
            onJoin = { host.pushPage(RoomJoinFragment()) }
            onOpenSettings = { RoomSettingsDialog(requireContext()).show() }
        }
    }
}
