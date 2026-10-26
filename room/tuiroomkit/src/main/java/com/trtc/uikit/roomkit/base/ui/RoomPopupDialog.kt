package com.trtc.uikit.roomkit.base.ui

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.trtc.uikit.roomkit.R

/**
 * Base bottom sheet dialog with custom styling and behavior.
 */
open class RoomPopupDialog : BottomSheetDialog {

    private var contentView: View? = null
    private var bottomSheet: View? = null
    private var dragIndicatorContainer: View? = null
    private var contentContainer: FrameLayout? = null

    constructor(context: Context) : super(context, R.style.RoomKitBottomDialog)

    @Deprecated("Use primary constructor instead")
    constructor(context: Context, theme: Int) : super(context, theme)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val baseView = LayoutInflater.from(context).inflate(R.layout.roomkit_dialog_popup_base, null)
        setContentView(baseView)
        
        bottomSheet = findViewById(com.google.android.material.R.id.design_bottom_sheet)
        dragIndicatorContainer = baseView.findViewById(R.id.drag_indicator_container)
        contentContainer = baseView.findViewById(R.id.content_container)

        contentView?.let { content ->
            contentContainer?.addView(content)
        }

        setOnShowListener { _ ->
            bottomSheet?.setBackgroundResource(android.R.color.transparent)
        }

        setupDragIndicator()
    }

    private fun setupDragIndicator() {
        dragIndicatorContainer?.setOnClickListener {
            dismiss()
        }
    }

    fun setView(view: View): RoomPopupDialog {
        contentView = view
        contentContainer?.let { container ->
            container.removeAllViews()
            container.addView(view)
        }
        return this
    }

    override fun dismiss() {
        window?.decorView?.let { decorView ->
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.hideSoftInputFromWindow(decorView.windowToken, 0)
        }
        super.dismiss()
    }

    override fun onStart() {
        super.onStart()
        val window = window ?: return
        changeConfiguration(window)
        window.setWindowAnimations(R.style.RoomKitBottomDialogAnimation)

        bottomSheet?.let { sheet ->
            val behavior = BottomSheetBehavior.from(sheet)
            behavior.apply {
                skipCollapsed = true
                state = BottomSheetBehavior.STATE_EXPANDED
                isDraggable = false
            }
        }
    }

    protected fun changeConfiguration(window: Window) {
        val configuration = context.resources.configuration
        val displayMetrics = context.resources.displayMetrics

        window.setBackgroundDrawableResource(android.R.color.transparent)
        val params = window.attributes

        when (configuration.orientation) {
            Configuration.ORIENTATION_LANDSCAPE -> {
                params.gravity = Gravity.END
                params.width = displayMetrics.widthPixels / 2
            }

            else -> {
                params.gravity = Gravity.BOTTOM
                params.width = WindowManager.LayoutParams.MATCH_PARENT
            }
        }

        params.height = displayMetrics.heightPixels
        window.attributes = params
    }
}