package com.trtc.uikit.roomkit.base.popup

import com.trtc.uikit.roomkit.R
import android.app.Activity
import android.content.Context
import androidx.core.content.ContextCompat
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.appcompat.widget.AppCompatTextView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.trtc.uikit.roomkit.base.util.dp

class RoomPopup(context: Context) {

    enum class ContentHeight { FILL, AUTO }
    enum class DismissReason { MASK, DRAG }

    private val dialog: BottomSheetDialog
    private val panelView: LinearLayout
    private val contentContainer: ClampedFrame
    private val behavior: BottomSheetBehavior<*>
    private val displayHeightPx: Int
    private var suppressDismissCallback = false

    var onDismiss: ((DismissReason) -> Unit)? = null

    var heightFraction: Float = DEFAULT_HEIGHT_FRACTION
        set(value) {
            field = value
            contentContainer.maxHeightPx = (displayHeightPx * value).toInt()
        }

    init {
        require(context is Activity) { "RoomPopup requires an Activity context" }
        displayHeightPx = context.resources.displayMetrics.heightPixels
        dialog = BottomSheetDialog(context)

        panelView = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        val handleArea = FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, context.dp(20))
            addView(
                View(context).apply {
                    layoutParams = FrameLayout.LayoutParams(context.dp(36), context.dp(4), Gravity.CENTER)
                },
            )
        }
        contentContainer = ClampedFrame(context).apply {
            maxHeightPx = (displayHeightPx * DEFAULT_HEIGHT_FRACTION).toInt()
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        panelView.addView(handleArea)
        panelView.addView(contentContainer)

        dialog.setContentView(panelView)
        dialog.setCancelable(true)
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

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
        applyStyle()
        dialog.show()
        behavior.state = BottomSheetBehavior.STATE_EXPANDED
    }

    fun dismiss() {
        suppressDismissCallback = true
        dialog.dismiss()
    }

    private fun applyStyle() {
        val handle = (panelView.getChildAt(0) as FrameLayout).getChildAt(0)
        handle.background = GradientDrawable().apply {
            cornerRadius = dialog.context.dp(999).toFloat()
            setColor(ContextCompat.getColor(dialog.context, R.color.roomkit_drag_handle))
        }
        panelView.background = GradientDrawable().apply {
            setColor(ContextCompat.getColor(dialog.context, R.color.roomkit_bg_surface))
            cornerRadii = floatArrayOf(
                dialog.context.dp(16).toFloat(), dialog.context.dp(16).toFloat(),
                dialog.context.dp(16).toFloat(), dialog.context.dp(16).toFloat(),
                0f, 0f, 0f, 0f,
            )
            setStroke(1, ContextCompat.getColor(dialog.context, R.color.roomkit_popup_border))
        }
        val sheetParent = panelView.parent as? View
        sheetParent?.setBackgroundColor(Color.TRANSPARENT)
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
    }
}
