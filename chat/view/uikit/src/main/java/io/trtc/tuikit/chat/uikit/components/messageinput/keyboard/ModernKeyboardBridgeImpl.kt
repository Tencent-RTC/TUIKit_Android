package io.trtc.tuikit.chat.uikit.components.messageinput.keyboard
import android.app.Activity
import android.util.Log
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsAnimationCompat
import androidx.core.view.WindowInsetsCompat

internal class ModernKeyboardBridgeImpl(activity: Activity) : BaseKeyboardBridgeImpl(activity) {
    private var pendingHideRunnable: Runnable? = null
    private var pendingShowRunnable: Runnable? = null
    private var imeAnimationRunning = false

    override fun isAnimationSupported(): Boolean = true

    override fun attach(view: View) {
        // IME insets are reliable only when observed from the activity content root.
        setupWindowInsetsListener()
    }

    override fun detach(view: View) {
        listener = null
        imeAnimationRunning = false
        rootView?.let { rv ->
            cancelPendingHide(rv)
            cancelPendingShow(rv)
            ViewCompat.setWindowInsetsAnimationCallback(rv.rootView, null)
            ViewCompat.setOnApplyWindowInsetsListener(rv, null)
        }
    }

    private fun cancelPendingHide(view: View) {
        pendingHideRunnable?.let { view.removeCallbacks(it) }
        pendingHideRunnable = null
    }

    private fun cancelPendingShow(view: View) {
        pendingShowRunnable?.let { view.removeCallbacks(it) }
        pendingShowRunnable = null
    }

    private fun scheduleShowDispatch(view: View, delayMs: Long = 200) {
        cancelPendingShow(view)
        val r = Runnable {
            pendingShowRunnable = null
            if (imeAnimationRunning) return@Runnable
            val height = keyboardMaxHeight
            if (height <= 0) return@Runnable
            dispatchChanged(height, true)
        }
        pendingShowRunnable = r
        view.postDelayed(r, delayMs)
    }

    private fun scheduleHideDispatch(view: View, delayMs: Long = 80) {
        cancelPendingHide(view)
        val r = Runnable {
            pendingHideRunnable = null
            if (imeAnimationRunning) return@Runnable
            dispatchChanged(0, false)
        }
        pendingHideRunnable = r
        view.postDelayed(r, delayMs)
    }

    private fun setupWindowInsetsListener() {
        val view = rootView ?: return

        ViewCompat.setOnApplyWindowInsetsListener(view) { _, insets ->
            val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime())
            val imeHeight = KeyboardInsetsUtil.toPanelSpacerHeight(imeInsets.bottom, insets)
            val imeVisible = imeHeight > 0

            if (imeVisible) {
                cancelPendingHide(view)
                cancelPendingShow(view)
                keyboardMaxHeight = imeHeight
                saveKeyboardHeight(imeHeight)
                if (!imeAnimationRunning) {
                    if (!lastDispatchedVisible) {
                        scheduleShowDispatch(view)
                    } else {
                        dispatchChanged(imeHeight, true)
                    }
                }
            } else {
                cancelPendingShow(view)
                scheduleHideDispatch(view)
            }
            insets
        }

        ViewCompat.setWindowInsetsAnimationCallback(
            view.rootView,
            object : WindowInsetsAnimationCompat.Callback(DISPATCH_MODE_CONTINUE_ON_SUBTREE) {
                var softInputHeight = 0f

                val isSoftInputVisible: Boolean
                    get() {
                        val insets = ViewCompat.getRootWindowInsets(view.rootView) ?: return false
                        return insets.isVisible(WindowInsetsCompat.Type.ime())
                    }

                override fun onStart(
                    animation: WindowInsetsAnimationCompat,
                    bounds: WindowInsetsAnimationCompat.BoundsCompat
                ): WindowInsetsAnimationCompat.BoundsCompat {
                    if ((animation.typeMask and WindowInsetsCompat.Type.ime()) != 0) {
                        imeAnimationRunning = true
                        cancelPendingHide(view)
                        cancelPendingShow(view)

                        softInputHeight = bounds.upperBound.bottom.toFloat()
                        val insets = ViewCompat.getRootWindowInsets(view)
                        val adjustedHeight = KeyboardInsetsUtil.toPanelSpacerHeight(softInputHeight.toInt(), insets)
                        if (adjustedHeight > 0) {
                            keyboardMaxHeight = adjustedHeight
                            saveKeyboardHeight(adjustedHeight)
                        }

                        Log.d(TAG, "IME.onStart: adjustedH=$adjustedHeight softInputH=$softInputHeight (no listener notify)")
                    }
                    return super.onStart(animation, bounds)
                }

                override fun onProgress(
                    insets: WindowInsetsCompat,
                    runningAnimations: List<WindowInsetsAnimationCompat>
                ): WindowInsetsCompat {
                    if (softInputHeight == 0f) return insets
                    for (runningAnimation in runningAnimations) {
                        if ((runningAnimation.typeMask and WindowInsetsCompat.Type.ime()) != 0) {
                            val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime())
                            val rootInsets = ViewCompat.getRootWindowInsets(view)
                            val adjustedHeight = KeyboardInsetsUtil.toPanelSpacerHeight(imeInsets.bottom, rootInsets)
                            listener?.onKeyboardHeightChanging(adjustedHeight)
                            break
                        }
                    }
                    return insets
                }

                override fun onEnd(animation: WindowInsetsAnimationCompat) {
                    if ((animation.typeMask and WindowInsetsCompat.Type.ime()) != 0) {
                        val insets = ViewCompat.getRootWindowInsets(view)
                        val adjustedHeight = KeyboardInsetsUtil.toPanelSpacerHeight(softInputHeight.toInt(), insets)
                        imeAnimationRunning = false

                        val visible = isSoftInputVisible
                        Log.d(TAG, "IME.onEnd: visible=$visible adjustedH=$adjustedHeight maxH=$keyboardMaxHeight")
                        if (adjustedHeight > 0 && visible) {
                            keyboardMaxHeight = adjustedHeight
                            saveKeyboardHeight(adjustedHeight)
                        }
                        dispatchChanged(if (visible) adjustedHeight else 0, visible)
                    }
                    super.onEnd(animation)
                }
            }
        )
    }

    companion object {
        private const val TAG = "MsgInput.KB"
    }
}
