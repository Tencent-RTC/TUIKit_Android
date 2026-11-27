package io.trtc.tuikit.chat.uikit.components.messageinput.keyboard
import android.app.Activity
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.PopupWindow

internal class LegacyKeyboardHeightProbe(
    private val activity: Activity,
    private val minKeyboardHeight: Int,
    private val navigationBarBottomProvider: () -> Int,
    private val listener: Listener
) {
    interface Listener {
        fun onProbeHeightChanged(height: Int, visible: Boolean)
    }

    private var popupWindow: PopupWindow? = null
    private var contentView: FrameLayout? = null
    private var globalLayoutListener: ViewTreeObserver.OnGlobalLayoutListener? = null
    private var lastHeight = 0
    private var lastVisible = false
    private var baseHeight = 0

    fun start(anchor: View) {
        Log.d(TAG, "probeStart: sdk=${Build.VERSION.SDK_INT} hasToken=${anchor.windowToken != null}")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Log.d(TAG, "probeSkip: unsupported sdk=${Build.VERSION.SDK_INT}")
            return
        }
        if (popupWindow?.isShowing == true) {
            Log.d(TAG, "probeSkip: already showing")
            return
        }

        val content = FrameLayout(activity).apply {
            setBackgroundColor(Color.TRANSPARENT)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        val visibleFrame = Rect()
        val layoutListener = ViewTreeObserver.OnGlobalLayoutListener {
            content.getWindowVisibleDisplayFrame(visibleFrame)
            val rootHeight = content.rootView.height
            val contentHeight = content.height
            val nav = navigationBarBottomProvider()
            if (baseHeight <= 0 && rootHeight > 0 && contentHeight > 0) {
                baseHeight = maxOf(rootHeight, contentHeight)
                Log.d(TAG, "probeBaseHeight: baseH=$baseHeight")
            }
            val height = LegacyKeyboardHeightCalculator.toPopupProbeKeyboardHeight(
                popupHeight = rootHeight,
                baseHeight = baseHeight.takeIf { it > 0 } ?: rootHeight,
                contentHeight = contentHeight,
                visibleBottom = visibleFrame.bottom,
                navigationBarBottom = nav,
                minKeyboardHeight = minKeyboardHeight
            )
            val visible = height > 0
            Log.d(
                TAG,
                "probeGlobalLayout: baseH=$baseHeight rootH=$rootHeight contentH=$contentHeight visibleTop=${visibleFrame.top} " +
                    "visibleBottom=${visibleFrame.bottom} nav=$nav height=$height visible=$visible"
            )
            if (height != lastHeight || visible != lastVisible) {
                lastHeight = height
                lastVisible = visible
                Log.d(TAG, "probeHeightChanged: height=$height visible=$visible")
                listener.onProbeHeightChanged(height, visible)
            }
        }
        content.viewTreeObserver.addOnGlobalLayoutListener(layoutListener)

        val popup = PopupWindow(content).apply {
            width = WindowManager.LayoutParams.MATCH_PARENT
            height = WindowManager.LayoutParams.MATCH_PARENT
            isFocusable = false
            isTouchable = false
            isOutsideTouchable = false
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            inputMethodMode = PopupWindow.INPUT_METHOD_NEEDED
            @Suppress("DEPRECATION")
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
            isClippingEnabled = true
        }

        contentView = content
        globalLayoutListener = layoutListener
        popupWindow = popup
        anchor.post {
            Log.d(
                TAG,
                "probeShowRequested: hasToken=${anchor.windowToken != null} " +
                    "isFinishing=${activity.isFinishing} isDestroyed=${isActivityDestroyed()} " +
                    "popupMatches=${popupWindow === popup} isShowing=${popup.isShowing}"
            )
            if (canShow(anchor, popup)) {
                try {
                    popup.showAtLocation(anchor.rootView ?: anchor, Gravity.NO_GRAVITY, 0, 0)
                    Log.d(TAG, "probeShown: isShowing=${popup.isShowing}")
                } catch (e: RuntimeException) {
                    Log.w(TAG, "probeShowFailed", e)
                }
            } else {
                Log.d(TAG, "probeCannotShow")
            }
        }
    }

    fun stop() {
        Log.d(TAG, "probeStop: isShowing=${popupWindow?.isShowing == true}")
        val content = contentView
        val layoutListener = globalLayoutListener
        if (content != null && layoutListener != null) {
            val observer = content.viewTreeObserver
            if (observer.isAlive) {
                observer.removeOnGlobalLayoutListener(layoutListener)
            }
        }
        popupWindow?.dismiss()
        popupWindow = null
        contentView = null
        globalLayoutListener = null
        lastHeight = 0
        lastVisible = false
        baseHeight = 0
    }

    private fun canShow(anchor: View, popup: PopupWindow): Boolean {
        return anchor.windowToken != null &&
            popupWindow === popup &&
            !popup.isShowing &&
            !activity.isFinishing &&
            !isActivityDestroyed()
    }

    private fun isActivityDestroyed(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && activity.isDestroyed
    }

    companion object {
        private const val TAG = "MsgInput.KBProbe"
    }
}
