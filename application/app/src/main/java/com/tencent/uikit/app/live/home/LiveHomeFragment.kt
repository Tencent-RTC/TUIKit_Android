package com.tencent.uikit.app.live.home

import android.graphics.Color
import android.os.Bundle
import android.os.Parcelable
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.addCallback
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.tencent.uikit.app.R
import com.tencent.uikit.app.home.HomeBranchFragment
import com.tencent.uikit.app.live.PusherHostFragment
import com.tencent.uikit.app.main.MainActivity
import com.trtc.uikit.livekit.voicelounge.VoiceLoungeView
import io.trtc.tuikit.atomicx.common.event.EventManager
import io.trtc.tuikit.atomicx.common.event.INotification
import kotlinx.coroutines.launch

class LiveHomeFragment : Fragment(R.layout.app_live_home) {
    private val owner = java.util.UUID.randomUUID().toString()
    private val store = LiveHomeListStore()
    private var previewMode = false
    private var previewID: String? = null
    private var gridScroll: Parcelable? = null
    private var presented = false
    private lateinit var grid: LiveHomeListView
    private lateinit var preview: LiveHomePreviewView
    private lateinit var header: LiveHomeHeaderView
    private val listener = INotification { _, subKey, params ->
        if (isResumed && view != null) {
            val host = activity as? MainActivity
            if (subKey == "join") header.updateInteractionEnabled(host?.liveJoinPending != true)
            else if (params?.get("owner") == owner && host != null) {
                when (params["action"]) {
                    "grid", "preview" -> if (!host.liveJoinPending) setMode(params["action"] == "preview")
                    "start" -> if (!host.liveJoinPending) host.pushPage(PusherHostFragment(), true)
                    "refresh" -> store.refresh()
                    "more" -> store.loadMore()
                    "open" -> if (!host.liveJoinPending) {
                        val id = params["id"] as? String
                        if (id != null && isVoiceRoom(id)) host.pushPage(LiveHomeVoiceRoomFragment.create(id), true)
                        else if (id != null) host.pushPage(LiveHomePlayerFragment().apply {
                            listStore = store.independentCopy()
                            arguments = Bundle().apply { putString("id", id) }
                            sourceRect = params["rect"] as? android.graphics.Rect
                            sourceVisibleRect = params["visible"] as? android.graphics.Rect
                            sourceSnapshot = params["snapshot"] as? android.graphics.Bitmap
                            seedCover =
                                (params["cover"] as? android.graphics.drawable.Drawable)?.constantState?.newDrawable()
                                    ?.mutate()
                        }, true)
                    }

                    "current" -> previewID = params["id"] as? String
                    "busy" -> header.updateInteractionEnabled(params["busy"] != true)
                    "presentation" -> {
                        presented = params["fullScreen"] == true
                        header.visibility = if (presented) View.GONE else View.VISIBLE
                        host.setHomeImmersive(presented)
                    }
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        grid = view.findViewById(R.id.home_live_grid)
        preview = view.findViewById(R.id.home_live_preview)
        header = view.findViewById(R.id.home_live_header)
        grid.bind(owner)
        preview.bind(owner, previewID, false)
        header.bind(owner)
        grid.restoreScroll(gridScroll)
        ViewCompat.setOnApplyWindowInsetsListener(view) { _, insets ->
            val top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            header.layoutParams =
                (header.layoutParams as android.widget.FrameLayout.LayoutParams).apply { topMargin = top }
            grid.layoutParams =
                (grid.layoutParams as android.widget.FrameLayout.LayoutParams).apply { topMargin = top + dp(56) }
            insets
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                store.state.collect { state -> grid.updateState(state); preview.updateState(state) }
            }
        }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
            if (isResumed && presented) preview.returnToPreview()
            else {
                isEnabled = false
                requireActivity().onBackPressedDispatcher.onBackPressed()
                isEnabled = true
            }
        }
        setMode(previewMode)
    }

    override fun onResume() {
        super.onResume()
        listOf("header", "list", "feed", "join").forEach {
            EventManager.instance.registerEvent(
                "app.home.live", it, listener
            )
        }
        store.setActive(true)
        if (!previewMode) grid.restoreScroll(gridScroll)
        preview.updateActive(previewMode)
        header.updateInteractionEnabled(!(activity as MainActivity).liveJoinPending)
        (activity as? MainActivity)?.setHomeImmersive(presented)
        if (!store.state.value.loaded) store.refresh()
    }

    override fun onPause() {
        EventManager.instance.unRegisterEvent(listener)
        store.setActive(false)
        preview.updateActive(false, true)
        gridScroll = grid.saveScroll()
        super.onPause()
    }

    private fun setMode(isPreview: Boolean) {
        if (isPreview && !previewMode && previewID == null) {
            previewID = grid.firstVisibleID()
            preview.bind(owner, previewID, false)
        }
        previewMode = isPreview
        grid.visibility = if (isPreview) View.GONE else View.VISIBLE
        preview.visibility = if (isPreview) View.VISIBLE else View.GONE
        header.updateMode(isPreview)
        preview.updateState(store.state.value)
        preview.updateActive(isPreview && isResumed)
        if (!isPreview) {
            presented = false; (activity as? MainActivity)?.setHomeImmersive(false)
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}

internal fun isVoiceRoom(liveID: String) = liveID.startsWith("voice_")

class LiveHomeVoiceRoomFragment : Fragment() {
    private var voiceView: VoiceLoungeView? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        FrameLayout(inflater.context).apply { setBackgroundColor(Color.BLACK) }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val liveID = requireArguments().getString("id").orEmpty()
        val next = VoiceLoungeView(requireContext(), VoiceLoungeView.Mode.Join(liveID))
        next.onClose = { close() }
        next.onSwitchLive = { info -> switchTo(info.liveID) }
        (view as FrameLayout).addView(next, FrameLayout.LayoutParams(-1, -1))
        voiceView = next
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
            if (voiceView?.handleBackPressed() != true) close()
        }
    }

    override fun onResume() {
        super.onResume()
        (activity as? MainActivity)?.setHomeImmersive(true)
    }

    override fun onDestroyView() {
        voiceView?.onClose = null
        voiceView?.onSwitchLive = null
        voiceView?.release()
        voiceView = null
        super.onDestroyView()
    }

    private fun close() {
        if (isAdded) (parentFragment as? HomeBranchFragment)?.pop()
    }

    private fun switchTo(liveID: String) {
        val host = activity as? MainActivity ?: return
        close()
        host.pushPage(
            if (isVoiceRoom(liveID)) create(liveID)
            else LiveHomePlayerFragment().apply { arguments = Bundle().apply { putString("id", liveID) } },
            true
        )
    }

    companion object {
        fun create(liveID: String) = LiveHomeVoiceRoomFragment().apply {
            arguments = Bundle().apply { putString("id", liveID) }
        }
    }
}
