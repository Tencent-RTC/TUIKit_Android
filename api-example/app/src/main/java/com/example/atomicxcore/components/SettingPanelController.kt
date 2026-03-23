package com.example.atomicxcore.components

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.FragmentActivity
import com.example.atomicxcore.R
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

/**
 * Generic setting panel container - displayed as a half-screen overlay
 *
 * Responsibility: Manages the panel's presentation and lifecycle
 * Design: Accepts any View as content; uses BottomSheetDialogFragment for half-screen overlay
 *
 * Reuse notes:
 * - BasicStreaming phase: SettingPanelController + DeviceSettingView
 * - Interactive phase: SettingPanelController + TabbedSettingView
 * - The container stays the same; only the content View needs to be added
 */
class SettingPanelController : BottomSheetDialogFragment() {

    companion object {
        private const val ARG_TITLE = "arg_title"
        private const val ARG_HEIGHT = "arg_height"
        private const val ARG_DARK_MODE = "arg_dark_mode"

        /**
         * Create a panel instance
         * @param title Panel title
         * @param contentView Content view (lazily created via contentViewProvider)
         * @param height Custom panel height (dp); null uses the default
         * @param darkMode Whether to use a dark background theme
         */
        fun newInstance(
            title: String,
            height: Int? = null,
            darkMode: Boolean = false
        ): SettingPanelController {
            return SettingPanelController().apply {
                arguments = Bundle().apply {
                    putString(ARG_TITLE, title)
                    height?.let { putInt(ARG_HEIGHT, it) }
                    putBoolean(ARG_DARK_MODE, darkMode)
                }
            }
        }
    }

    /** Content view provider, invoked in onCreateView to create the view */
    var contentViewProvider: (() -> View)? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val context = requireContext()
        val dp16 = (16 * context.resources.displayMetrics.density).toInt()
        val dp12 = (12 * context.resources.displayMetrics.density).toInt()
        val dp30 = (30 * context.resources.displayMetrics.density).toInt()
        val isDarkMode = arguments?.getBoolean(ARG_DARK_MODE, false) ?: false

        // Root layout
        val rootLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            if (isDarkMode) {
                setBackgroundColor(android.graphics.Color.parseColor("#1C1C24"))
            }
        }

        // Title bar container
        val titleBar = FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp16
                bottomMargin = dp12
            }
        }

        // Title text
        val titleText = TextView(context).apply {
            text = arguments?.getString(ARG_TITLE) ?: ""
            textSize = 17f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            if (isDarkMode) {
                setTextColor(android.graphics.Color.WHITE)
            }
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = android.view.Gravity.CENTER
            }
        }
        titleBar.addView(titleText)

        // Close button
        val closeBtn = ImageButton(context).apply {
            setImageResource(R.drawable.ic_close)
            background = null
            if (isDarkMode) {
                setColorFilter(android.graphics.Color.WHITE)
            }
            layoutParams = FrameLayout.LayoutParams(dp30, dp30).apply {
                gravity = android.view.Gravity.END or android.view.Gravity.CENTER_VERTICAL
                marginEnd = dp16
            }
            setOnClickListener { dismiss() }
        }
        titleBar.addView(closeBtn)

        rootLayout.addView(titleBar)

        // Separator line
        val separator = View(context).apply {
            setBackgroundColor(
                if (isDarkMode) android.graphics.Color.parseColor("#33FFFFFF")
                else android.graphics.Color.parseColor("#E0E0E0")
            )
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1
            )
        }
        rootLayout.addView(separator)

        // Content area
        val contentView = contentViewProvider?.invoke()
        if (contentView != null) {
            contentView.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                weight = 1f
            }
            rootLayout.addView(contentView)
        }

        return rootLayout
    }

    override fun onStart() {
        super.onStart()
        val isDarkMode = arguments?.getBoolean(ARG_DARK_MODE, false) ?: false
        // Set panel height
        val bottomSheet = dialog?.findViewById<View>(
            com.google.android.material.R.id.design_bottom_sheet
        )
        bottomSheet?.let { sheet ->
            if (isDarkMode) {
                sheet.setBackgroundColor(android.graphics.Color.parseColor("#1C1C24"))
            }
            val behavior = BottomSheetBehavior.from(sheet)
            val customHeight = arguments?.getInt(ARG_HEIGHT, 0) ?: 0
            if (customHeight > 0) {
                val heightPx = (customHeight * resources.displayMetrics.density).toInt()
                behavior.peekHeight = heightPx
                sheet.layoutParams.height = heightPx
            } else {
                behavior.peekHeight = BottomSheetBehavior.PEEK_HEIGHT_AUTO
            }
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
        }
    }

    /**
     * Show the panel on the specified Activity
     */
    fun show(activity: FragmentActivity) {
        show(activity.supportFragmentManager, "SettingPanel")
    }
}
