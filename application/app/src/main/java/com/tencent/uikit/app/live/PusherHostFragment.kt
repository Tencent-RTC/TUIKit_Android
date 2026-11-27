package com.tencent.uikit.app.live

import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import android.widget.Toast
import androidx.core.view.doOnPreDraw
import androidx.activity.addCallback
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import com.tencent.uikit.app.R
import com.tencent.uikit.app.main.MainActivity
import com.trtc.uikit.livekit.pusher.PusherView
import com.trtc.uikit.livekit.voicelounge.VoiceLoungeView
import io.trtc.tuikit.atomicx.common.event.EventManager
import io.trtc.tuikit.atomicx.common.event.INotification
import io.trtc.tuikit.atomicxcore.api.login.LoginStore

class PusherHostFragment : Fragment(R.layout.app_pusher_host) {
    private val owner = java.util.UUID.randomUUID().toString()
    private var voice = false
    private var switching = false
    private var sharedTitle: String? = null
    private var mountedDefaultTitle: String? = null
    private var video: PusherView? = null
    private var audio: VoiceLoungeView? = null
    private lateinit var typeBar: BroadcastTypeBarView
    private val listener = INotification { _, _, params ->
        if (params?.get("owner") == owner && isResumed && typeBar.isShown && !switching) {
            val index = params["type"] as? Int
            if (index == 2) Toast.makeText(context, R.string.home_coming_soon, Toast.LENGTH_SHORT).show()
            else if (index != null && (index == 1) != voice) switchType(index == 1)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        voice = savedInstanceState?.getBoolean("voice") ?: false
        typeBar = BroadcastTypeBarView(requireContext()).apply { bind(owner) }
        EventManager.instance.registerEvent("app.home.live", "broadcast", listener)
        view.doOnPreDraw { if (isAdded && this.view != null && video == null && audio == null) mount() }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
            val handled = if (voice) audio?.handleBackPressed() == true else video?.handleBackPressed() == true
            if (!handled) (activity as? MainActivity)?.finishPusher()
        }
    }

    private fun switchType(nextVoice: Boolean) {
        switching = true
        view?.clearFocus()
        WindowCompat.getInsetsController(requireActivity().window, requireView()).hide(WindowInsetsCompat.Type.ime())
        val currentTitle = video?.prepareTitle ?: audio?.prepareTitle
        sharedTitle = currentTitle?.takeIf { it != mountedDefaultTitle }
        release()
        voice = nextVoice
        mount()
        switching = false
    }

    private fun mount() {
        val container = requireView().findViewById<FrameLayout>(R.id.home_pusher_content)
        val id = LoginStore.shared.loginState.loginUserInfo.value?.userID.orEmpty()
        val content: View
        if (voice) {
            val next = VoiceLoungeView(requireContext(), VoiceLoungeView.Mode.Create("voice_$id"))
            mountedDefaultTitle = next.prepareTitle
            sharedTitle?.let { next.prepareTitle = it }
            next.prepareAccessoryView = typeBar
            next.onClose = { (activity as? MainActivity)?.finishPusher() }
            audio = next
            content = next
        } else {
            val next = PusherView(requireContext())
            next.bind("live_$id")
            mountedDefaultTitle = next.prepareTitle
            sharedTitle?.let { next.prepareTitle = it }
            next.prepareAccessoryView = typeBar
            next.onExit = { (activity as? MainActivity)?.finishPusher() }
            video = next
            content = next
        }
        typeBar.updateSelection(voice)
        content.measure(
            View.MeasureSpec.makeMeasureSpec(container.width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(container.height, View.MeasureSpec.EXACTLY)
        )
        content.layout(0, 0, container.width, container.height)
        container.addView(content, FrameLayout.LayoutParams(-1, -1))
    }

    private fun release() {
        video?.onExit = null
        video?.prepareAccessoryView = null
        audio?.onClose = null
        audio?.prepareAccessoryView = null
        audio?.release()
        (video?.parent as? FrameLayout)?.removeView(video)
        (audio?.parent as? FrameLayout)?.removeView(audio)
        video = null
        audio = null
    }

    override fun onDestroyView() {
        EventManager.instance.unRegisterEvent(listener)
        release()
        super.onDestroyView()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("voice", voice)
        super.onSaveInstanceState(outState)
    }
}
