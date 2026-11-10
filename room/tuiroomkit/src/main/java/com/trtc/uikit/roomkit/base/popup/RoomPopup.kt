package com.trtc.uikit.roomkit.base.popup

import com.trtc.uikit.roomkit.R
import android.app.Activity
import android.content.Context
import androidx.core.content.ContextCompat
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.trtc.uikit.roomkit.base.util.MIN_BOTTOM_CLEARANCE_DP
import com.trtc.uikit.roomkit.base.util.dp
import kotlin.math.max

class RoomPopup(context: Context) {

    enum class ContentHeight { FILL, AUTO }
    enum class DismissReason { MASK, DRAG }

    private val dialog: BottomSheetDialog
    private val panelView: LinearLayout
    private val contentContainer: ClampedFrame
    private val handleArea: FrameLayout
    private val behavior: BottomSheetBehavior<*>
    private val displayHeightPx: Int
    private val activity: Activity
    private var suppressDismissCallback = false
    private var previousLightStatusBars: Boolean? = null

    var onDismiss: ((DismissReason) -> Unit)? = null

    var heightFraction: Float = DEFAULT_HEIGHT_FRACTION

    var panelBackgroundColor: Int = 0
        set(value) { field = value; applyPanelBackground() }

    var panelBorderWidth: Int = 1
        set(value) { field = value; applyPanelBackground() }

    var panelBorderColor: Int = 0
        set(value) { field = value; applyPanelBackground() }

    var showsHandleBar: Boolean = true
        set(value) { field = value; applyHandleBarVisibility() }

    init {
        require(context is Activity) { "RoomPopup requires an Activity context" }
        activity = context
        displayHeightPx = context.resources.displayMetrics.heightPixels
        dialog = BottomSheetDialog(context, R.style.RoomKitBottomSheetDialog)
        dialog.window?.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)

        panelView = PanelView(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        handleArea = FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, context.dp(20))
            addView(
                View(context).apply {
                    layoutParams = FrameLayout.LayoutParams(context.dp(36), context.dp(4), Gravity.CENTER)
                },
            )
        }
        panelBackgroundColor = ContextCompat.getColor(context, R.color.roomkit_bg_surface)
        panelBorderColor = ContextCompat.getColor(context, R.color.roomkit_popup_border)
        contentContainer = ClampedFrame(context).apply {
            maxHeightPx = (displayHeightPx * MAX_HEIGHT_FRACTION).toInt()
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        panelView.addView(handleArea)
        panelView.addView(contentContainer)

        dialog.setContentView(panelView)
        dialog.setCancelable(true)
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)

        behavior = dialog.behavior.apply {
            isHideable = true
            skipCollapsed = true
            isFitToContents = true
        }
        behavior.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {
                if (newState == BottomSheetBehavior.STATE_HIDDEN && !suppressDismissCallback) {
                    onDismiss?.invoke(DismissReason.DRAG)
                }
            }

            override fun onSlide(bottomSheet: View, slideOffset: Float) = Unit
        })
        dialog.setOnCancelListener {
            if (!suppressDismissCallback) onDismiss?.invoke(DismissReason.MASK)
        }
        dialog.setOnDismissListener {
            suppressDismissCallback = false
            previousLightStatusBars?.let {
                WindowCompat.getInsetsController(activity.window, activity.window.decorView)
                    .isAppearanceLightStatusBars = it
            }
            previousLightStatusBars = null
        }

        applyStyle()
    }

    fun setContentView(view: View, height: ContentHeight = ContentHeight.FILL) {
        contentContainer.removeAllViews()
        contentContainer.addView(
            view,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
        )
        view.layoutParams.height = if (height == ContentHeight.AUTO) {
            ViewGroup.LayoutParams.WRAP_CONTENT
        } else {
            (displayHeightPx * heightFraction).toInt()
        }
    }

    fun show() {
        val owner = dialog.ownerActivity
        if (owner != null && (owner.isFinishing || owner.isDestroyed)) return
        applyStyle()
        applyBottomClearance()
        dialog.window?.clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
        dialog.show()
        dialog.window?.let {
            WindowCompat.getInsetsController(it, it.decorView).isAppearanceLightStatusBars = true
        }
        WindowCompat.getInsetsController(activity.window, activity.window.decorView).let {
            previousLightStatusBars = it.isAppearanceLightStatusBars
            it.isAppearanceLightStatusBars = true
        }
        dialog.findViewById<View>(com.google.android.material.R.id.container)
            ?.setBackgroundColor(ContextCompat.getColor(activity, R.color.roomkit_scrim))
        behavior.state = BottomSheetBehavior.STATE_EXPANDED
    }

    private fun applyBottomClearance() {
        val navigationBarBottom = ViewCompat.getRootWindowInsets(activity.window.decorView)
            ?.getInsets(WindowInsetsCompat.Type.navigationBars())?.bottom ?: 0
        panelView.setPadding(0, 0, 0, max(navigationBarBottom, activity.dp(MIN_BOTTOM_CLEARANCE_DP)))
    }

    fun dismiss() {
        suppressDismissCallback = true
        dialog.window?.setFlags(
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
        )
        dialog.dismiss()
    }

    private fun applyStyle() {
        val handle = (panelView.getChildAt(0) as FrameLayout).getChildAt(0)
        handle.background = GradientDrawable().apply {
            cornerRadius = dialog.context.dp(999).toFloat()
            setColor(ContextCompat.getColor(dialog.context, R.color.roomkit_drag_handle))
        }
        applyPanelBackground()
        applyHandleBarVisibility()
        val sheetParent = panelView.parent as? View
        sheetParent?.setBackgroundColor(Color.TRANSPARENT)
    }

    private fun applyPanelBackground() {
        panelView.background = GradientDrawable().apply {
            setColor(panelBackgroundColor)
            cornerRadii = floatArrayOf(
                dialog.context.dp(16).toFloat(), dialog.context.dp(16).toFloat(),
                dialog.context.dp(16).toFloat(), dialog.context.dp(16).toFloat(),
                0f, 0f, 0f, 0f,
            )
            setStroke(dialog.context.dp(panelBorderWidth), panelBorderColor)
        }
    }

    private fun applyHandleBarVisibility() {
        handleArea.visibility = if (showsHandleBar) View.VISIBLE else View.GONE
        handleArea.layoutParams.height = if (showsHandleBar) dialog.context.dp(20) else 0
        handleArea.requestLayout()
    }

    private inner class PanelView(context: Context) : LinearLayout(context) {

        override fun dispatchTouchEvent(event: MotionEvent): Boolean {
            if (event.actionMasked == MotionEvent.ACTION_DOWN) hideKeyboardIfFocused()
            return super.dispatchTouchEvent(event)
        }
    }

    private fun hideKeyboardIfFocused() {
        val focus = dialog.window?.currentFocus ?: return
        val imm = dialog.context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(focus.windowToken, 0)
        focus.clearFocus()
    }

    private class ClampedFrame(context: Context) : FrameLayout(context) {

        var maxHeightPx: Int = 0

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            if (maxHeightPx <= 0) {
                super.onMeasure(widthMeasureSpec, heightMeasureSpec)
                return
            }
            if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.EXACTLY) {
                super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            } else {
                super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(maxHeightPx, MeasureSpec.AT_MOST))
            }
        }
    }

    private companion object {
        const val DEFAULT_HEIGHT_FRACTION = 0.84f
        const val MAX_HEIGHT_FRACTION = 0.9f
    }
}
