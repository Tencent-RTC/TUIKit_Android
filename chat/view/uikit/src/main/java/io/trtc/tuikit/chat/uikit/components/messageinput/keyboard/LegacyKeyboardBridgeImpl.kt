package io.trtc.tuikit.chat.uikit.components.messageinput.keyboard
import android.app.Activity
import android.os.Build
import android.util.Log
import android.view.View

internal class LegacyKeyboardBridgeImpl(activity: Activity) : BaseKeyboardBridgeImpl(activity) {
    private var pendingHideRunnable: Runnable? = null
    private var pendingShowRunnable: Runnable? = null
    private var legacyKeyboardProbe: LegacyKeyboardHeightProbe? = null

    override fun isAnimationSupported(): Boolean = false

    override fun attach(view: View) {
        setupLegacyKeyboardProbe(view)
    }

    override fun detach(view: View) {
        listener = null
        val anchor = rootView ?: view
        cancelPendingHide(anchor)
        cancelPendingShow(anchor)
        legacyKeyboardProbe?.stop()
        legacyKeyboardProbe = null
    }

    override fun onShowKeyboardRequested(editText: View) {
        scheduleLegacyShowFallback(editText)
    }

    override fun onHideKeyboardRequested(anchorView: View) {
        scheduleLegacyHideFallback(anchorView)
    }

    private fun cancelPendingHide(view: View) {
        pendingHideRunnable?.let { view.removeCallbacks(it) }
        pendingHideRunnable = null
    }

    private fun cancelPendingShow(view: View) {
        pendingShowRunnable?.let { view.removeCallbacks(it) }
        pendingShowRunnable = null
    }

    private fun scheduleLegacyShowFallback(editText: View, delayMs: Long = 250) {
        val view = rootView ?: editText
        cancelPendingHide(view)
        cancelPendingShow(view)
        val r = Runnable {
            pendingShowRunnable = null
            val navigationBarBottom = getNavigationBarBottom(view)
            val fallbackHeight = KeyboardInsetsUtil.toPanelSpacerHeight(
                imeBottom = getKeyboardHeight(),
                navigationBarBottom = navigationBarBottom
            )
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R &&
                editText.isFocused &&
                !lastDispatchedVisible &&
                fallbackHeight > 0
            ) {
                Log.d(TAG, "legacyShowFallback: height=$fallbackHeight nav=$navigationBarBottom")
                dispatchChanged(fallbackHeight, true)
            }
        }
        pendingShowRunnable = r
        view.postDelayed(r, delayMs)
    }

    private fun scheduleLegacyHideFallback(anchorView: View, delayMs: Long = 120) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R || !lastDispatchedVisible) {
            cancelPendingShow(rootView ?: anchorView)
            return
        }
        val view = rootView ?: anchorView
        cancelPendingShow(view)
        cancelPendingHide(view)
        val r = Runnable {
            pendingHideRunnable = null
            Log.d(TAG, "legacyHideFallback")
            dispatchChanged(0, false)
        }
        pendingHideRunnable = r
        view.postDelayed(r, delayMs)
    }

    private fun setupLegacyKeyboardProbe(anchor: View) {
        val view = rootView ?: return
        if (legacyKeyboardProbe != null) {
            Log.d(TAG, "skipLegacyKeyboardProbe: already attached")
            return
        }

        val minKeyboardHeight = (LEGACY_MIN_KEYBOARD_HEIGHT_DP * activity.resources.displayMetrics.density).toInt()
        Log.d(TAG, "setupLegacyKeyboardProbe: minKeyboardHeight=$minKeyboardHeight")
        legacyKeyboardProbe = LegacyKeyboardHeightProbe(
            activity = activity,
            minKeyboardHeight = minKeyboardHeight,
            navigationBarBottomProvider = { getNavigationBarBottom(view) },
            listener = object : LegacyKeyboardHeightProbe.Listener {
                override fun onProbeHeightChanged(height: Int, visible: Boolean) {
                    if (visible) {
                        cancelPendingHide(view)
                        cancelPendingShow(view)
                        keyboardMaxHeight = height
                        saveKeyboardHeight(height)
                        listener?.onKeyboardHeightChanging(height)
                        if (height != lastDispatchedHeight || !lastDispatchedVisible) {
                            Log.d(TAG, "legacyProbeVisible: height=$height")
                            dispatchChanged(height, true)
                        }
                    } else if (lastDispatchedVisible) {
                        Log.d(TAG, "legacyProbeHidden")
                        dispatchChanged(0, false)
                    }
                }
            }
        ).also { it.start(anchor) }
    }

    companion object {
        private const val TAG = "MsgInput.KB"
        private const val LEGACY_MIN_KEYBOARD_HEIGHT_DP = 80
    }
}
