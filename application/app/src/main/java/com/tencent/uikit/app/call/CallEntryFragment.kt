package com.tencent.uikit.app.call

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.activity.addCallback
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.tencent.qcloud.tuikit.tuicallkit.TUICallKit
import com.tencent.uikit.app.R
import com.tencent.uikit.app.call.common.CallAccessToast
import com.tencent.uikit.app.call.common.WindowInsetsHelper
import com.tencent.uikit.app.call.entry.CallEntrySelection
import com.tencent.uikit.app.call.entry.CallEntryState
import com.tencent.uikit.app.call.entry.CallEntryView
import com.tencent.uikit.app.call.entry.EntryContactController
import com.tencent.uikit.app.call.entry.EntrySearchFeedback
import com.tencent.uikit.app.main.MainActivity
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.call.CallMediaType
import io.trtc.tuikit.atomicxcore.api.call.CallParams
import io.trtc.tuikit.atomicxcore.api.device.DeviceStore
import io.trtc.tuikit.atomicxcore.api.device.VideoOrientation

class CallEntryFragment : Fragment(R.layout.callaccess_entry_fragment) {

    private lateinit var entryView: CallEntryView
    private lateinit var state: CallEntryState
    private lateinit var selection: CallEntrySelection
    private lateinit var contactController: EntryContactController
    private var isCalling = false
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        state = (requireActivity() as MainActivity).callEntryState
        entryView = view.findViewById(R.id.entry_view)
        selection = CallEntrySelection()
        contactController = EntryContactController(selection)
        entryView.bind(state, contactController)
        entryView.setCallButtonEnabled(!isCalling)
        WindowInsetsHelper.applyStatusBarInset(view.findViewById(R.id.header))
        contactController.onFeedback = { feedback, detail -> showFeedback(feedback, detail) }
        contactController.start(viewLifecycleOwner.lifecycleScope)
        applyFacadeSettings()
        bindActions()
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
            if (entryView.dismissSheets()) return@addCallback
            isEnabled = false
            requireActivity().onBackPressedDispatcher.onBackPressed()
            isEnabled = true
        }
    }

    override fun onResume() {
        super.onResume()
        entryView.reloadFromState()
    }

    override fun onDestroyView() {
        contactController.close()
        super.onDestroyView()
    }

    private fun applyFacadeSettings() {
        val facade = TUICallKit.createInstance(requireContext())
        facade.enableMuteMode(state.muteModeEnabled)
        facade.enableIncomingBanner(state.bannerOn)
        facade.enableFloatWindow(state.showFloatButton)
        facade.enableVirtualBackground(state.blurBackgroundOn)
        facade.enableAITranscriber(state.captionOn)
    }

    private fun bindActions() {
        entryView.onMoreSettingsTap = {
            (activity as? MainActivity)?.pushPage(CallSettingsFragment())
        }
        entryView.onStartCallTap = { startCall() }
    }

    private fun showFeedback(feedback: EntrySearchFeedback, detail: String) {
        if (view == null) return
        val message = when (feedback) {
            EntrySearchFeedback.EMPTY_QUERY -> getString(R.string.callaccess_search_need_query)
            EntrySearchFeedback.SELF -> getString(R.string.callaccess_call_self_only)
            EntrySearchFeedback.NOT_REGISTERED -> getString(R.string.callaccess_search_unregistered)
            EntrySearchFeedback.NETWORK_FAILED -> getString(R.string.callaccess_search_network_failed)
            EntrySearchFeedback.LIMIT -> getString(R.string.callaccess_call_too_many)
            EntrySearchFeedback.PARTIAL_UNREGISTERED -> getString(R.string.callaccess_search_ids_unregistered, detail)
        }
        CallAccessToast.show(requireActivity(), message)
    }

    private fun startCall() {
        if (isCalling) return
        val calleeIDs = selection.userIDs
        if (calleeIDs.isEmpty()) {
            CallAccessToast.show(requireActivity(), getString(R.string.callaccess_call_empty_callee))
            return
        }
        if (calleeIDs.any(contactController::isSelf)) {
            CallAccessToast.show(requireActivity(), getString(R.string.callaccess_call_self_only))
            return
        }
        if (selection.media == CallEntryState.Media.VIDEO) {
            DeviceStore.shared().updateVideoQuality(selection.resolution.videoQuality, VideoOrientation.PORTRAIT)
        }

        val params = CallParams()
        params.roomId = state.roomID
        params.timeout = state.timeoutSeconds
        params.userData = state.extendedInfo

        applyFacadeSettings()
        isCalling = true
        entryView.setCallButtonEnabled(false)
        val mediaType = if (selection.media == CallEntryState.Media.VIDEO) CallMediaType.Video else CallMediaType.Audio
        TUICallKit.createInstance(requireContext()).calls(calleeIDs, mediaType, params, object : CompletionHandler {
            override fun onSuccess() {
                mainHandler.post {
                    isCalling = false
                    if (view == null) return@post
                    entryView.setCallButtonEnabled(true)
                    CallAccessToast.show(requireActivity(), getString(R.string.callaccess_call_calling))
                }
            }

            override fun onFailure(code: Int, desc: String) {
                mainHandler.post {
                    isCalling = false
                    if (view == null) return@post
                    entryView.setCallButtonEnabled(true)
                    if (code == -1101 || code == -1102) return@post
                    CallAccessToast.show(requireActivity(), getString(R.string.callaccess_call_failed, "$code $desc"))
                }
            }
        })
    }
}
