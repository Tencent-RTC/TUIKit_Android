package com.tencent.uikit.app.live.home

import android.animation.ValueAnimator
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.core.view.doOnPreDraw
import androidx.activity.addCallback
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.tencent.uikit.app.R
import com.tencent.uikit.app.main.MainActivity
import io.trtc.tuikit.atomicx.common.event.EventManager
import io.trtc.tuikit.atomicx.common.event.INotification
import kotlinx.coroutines.launch

class LiveHomePlayerFragment : Fragment(R.layout.app_live_player_host) {
    var listStore: LiveHomeListStore? = null
    var sourceRect: Rect? = null
    var seedCover: Drawable? = null
    var sourceVisibleRect: Rect? = null
    var sourceSnapshot: android.graphics.Bitmap? = null
    private val owner = java.util.UUID.randomUUID().toString()
    private lateinit var feed: LiveHomePreviewView
    private var closing = false
    private var animation: ValueAnimator? = null
    private val listener = INotification { _, _, params ->
        if (params?.get("owner") == owner && isResumed) when (params["action"]) {
            "refresh" -> listStore?.refresh()
            "more" -> listStore?.loadMore()
            "close" -> close()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        if (listStore == null) listStore = LiveHomeListStore()
        feed = view.findViewById(R.id.home_direct_feed)
        feed.bind(owner, requireArguments().getString("id"), true)
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                listStore!!.state.collect { feed.updateState(it) }
            }
        }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
            if (!isResumed) {
                isEnabled = false
                requireActivity().onBackPressedDispatcher.onBackPressed()
                isEnabled = true
            } else close()
        }
        feed.updateSeedCover(seedCover)
        if (canAnimateEntry()) {
            // Keep the destination cover and room controls out of the first transition frame.
            feed.alpha = 0f
            feed.visibility = View.INVISIBLE
        }
        view.doOnPreDraw { if (this.view === view) animateEntry(view) }
    }

    override fun onResume() {
        super.onResume()
        EventManager.instance.registerEvent("app.home.live", "feed", listener)
        listStore?.setActive(true)
        feed.updateActive(true)
        (activity as? MainActivity)?.setHomeImmersive(true)
        if (listStore?.state?.value?.loaded != true) listStore?.refresh()
    }

    override fun onPause() {
        EventManager.instance.unRegisterEvent(listener)
        listStore?.setActive(false)
        feed.updateActive(false, true)
        super.onPause()
    }

    override fun onDestroyView() {
        view?.animate()?.cancel()
        animation?.cancel(); animation = null
        seedCover = null
        sourceSnapshot = null
        super.onDestroyView()
    }

    private fun close() {
        if (!isAdded || closing) return
        closing = true
        animation?.cancel()
        // Keep the current playback surface until it fades out; detach owns final session cleanup.
        feed.updateActive(false, true)
        requireView().animate().alpha(0f).setDuration(160).withEndAction {
            (activity as? MainActivity)?.let { host ->
                (parentFragment as? com.tencent.uikit.app.home.HomeBranchFragment)?.pop()
            }
        }.start()
    }

    private fun canAnimateEntry(): Boolean {
        val rect = sourceRect ?: return false
        if (seedCover == null || rect.isEmpty) return false
        return if (android.os.Build.VERSION.SDK_INT >= 26) ValueAnimator.areAnimatorsEnabled()
        else android.provider.Settings.Global.getFloat(
            requireContext().contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f
        ) > 0f
    }

    private fun animateEntry(root: View) {
        if (!canAnimateEntry()) {
            feed.alpha = 1f
            feed.visibility = View.VISIBLE
            return
        }
        val rect = Rect(sourceRect!!)
        val image = root.findViewById<ImageView>(R.id.home_direct_cover)
        val snapshot = root.findViewById<ImageView>(R.id.home_direct_snapshot)
        image.setImageDrawable(seedCover)
        snapshot.setImageBitmap(sourceSnapshot)
        val xy = IntArray(2)
        root.getLocationInWindow(xy)
        val x = rect.left - xy[0]
        val y = rect.top - xy[1]
        val visible = Rect(sourceVisibleRect ?: rect)
        if (!visible.intersect(rect)) visible.set(rect)
        snapshot.clipBounds = Rect(visible).apply { offset(-rect.left, -rect.top) }
        var corner = 8f * resources.displayMetrics.density
        image.clipToOutline = true
        image.outlineProvider = object : android.view.ViewOutlineProvider() {
            override fun getOutline(view: View, outline: android.graphics.Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, corner)
            }
        }
        fun frame(view: View, left: Int, top: Int, width: Int, height: Int) {
            view.layoutParams = FrameLayout.LayoutParams(width, height).apply {
                leftMargin = left
                topMargin = top
            }
            // Pre-draw runs after layout: apply the actual bounds now, before any pixels can be drawn.
            view.measure(
                View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
            )
            view.layout(left, top, left + width, top + height)
        }

        fun render(t: Float) {
            val width = (rect.width() + (root.width - rect.width()) * t).toInt()
            val height = (rect.height() + (root.height - rect.height()) * t).toInt()
            frame(image, (x * (1 - t)).toInt(), (y * (1 - t)).toInt(), width, height)
            corner = 8f * resources.displayMetrics.density * (1 - t)
            image.invalidateOutline()
            image.clipBounds = Rect(
                ((visible.left - rect.left) * (1 - t)).toInt(), ((visible.top - rect.top) * (1 - t)).toInt(),
                width - ((rect.right - visible.right) * (1 - t)).toInt(),
                height - ((rect.bottom - visible.bottom) * (1 - t)).toInt()
            )
            snapshot.alpha = (1 - t / .30f).coerceIn(0f, 1f)
            feed.alpha = ((t - .75f) / .25f).coerceIn(0f, 1f)
            feed.visibility = if (t >= .75f) View.VISIBLE else View.INVISIBLE
            image.alpha = (1 - (t - .85f) / .15f).coerceIn(0f, 1f)
        }
        frame(snapshot, x, y, rect.width(), rect.height())
        render(0f)
        image.visibility = View.VISIBLE
        snapshot.visibility = if (sourceSnapshot == null) View.GONE else View.VISIBLE
        animation = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 220
            addUpdateListener { render(it.animatedValue as Float) }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animator: android.animation.Animator) {
                    feed.alpha = 1f
                    feed.visibility = View.VISIBLE
                    image.visibility = View.GONE
                    image.setImageDrawable(null)
                    snapshot.visibility = View.GONE
                    snapshot.setImageDrawable(null)
                    sourceSnapshot = null
                }
            })
            start()
        }
    }
}
