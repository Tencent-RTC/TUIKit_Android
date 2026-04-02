package com.trtc.uikit.roomkit.base.ui

import android.content.Context
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.trtc.uikit.roomkit.R

/**
 * Action sheet dialog for displaying a list of actions with optional tips.
 */
class RoomActionSheetDialog private constructor(
    context: Context,
    private val builder: Builder
) : BottomSheetDialog(context, R.style.RoomKitBottomDialog) {

    private lateinit var rootView: View
    private lateinit var topDivideView: View
    private lateinit var tvTips: TextView
    private lateinit var llActions: LinearLayout
    private lateinit var dragIndicator: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.roomkit_dialog_action_sheet)

        rootView = findViewById(R.id.cl_root)!!
        topDivideView = findViewById(R.id.view_divider_top)!!
        tvTips = findViewById(R.id.tv_tips)!!
        llActions = findViewById(R.id.ll_actions)!!
        dragIndicator = findViewById(R.id.drag_indicator)!!

        setupView()
        setupWindow()
    }

    private fun setupView() {
        if (builder.tipsText.isNotEmpty()) {
            tvTips.text = builder.tipsText
            tvTips.visibility = View.VISIBLE
            topDivideView.visibility = View.VISIBLE
        } else {
            tvTips.visibility = View.GONE
            topDivideView.visibility = View.GONE
        }
        rootView.setBackgroundResource(builder.backgroundRes ?: R.drawable.roomkit_bg_bottom_sheet_dialog)
        builder.actions.forEachIndexed { index, action ->
            if (index > 0) {
                addDivider()
            }
            addActionButton(action)
        }
        dragIndicator.setOnClickListener {
            dismiss()
        }
    }

    private fun addDivider() {
        val divider = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                context.resources.getDimensionPixelSize(R.dimen.roomkit_divider_height)
            )
            setBackgroundColor(
                ContextCompat.getColor(
                    context,
                    builder.dividerColorRes ?: R.color.roomkit_color_action_sheet_divider
                )
            )
        }
        llActions.addView(divider)
    }

    private fun addActionButton(action: ActionItem) {
        val button = LayoutInflater.from(context)
            .inflate(R.layout.roomkit_item_action_sheet, llActions, false) as TextView

        button.text = action.text
        button.setTextColor(
            ContextCompat.getColor(
                context,
                when {
                    action.isWarning -> R.color.roomkit_color_end_room
                    builder.textColorRes != null -> builder.textColorRes!!
                    else -> R.color.roomkit_color_primary
                }
            )
        )
        button.setOnClickListener {
            action.onClick?.invoke()
            dismiss()
        }

        llActions.addView(button)
    }

    private fun setupWindow() {
        window?.let { win ->
            val bottomSheet = win.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            bottomSheet?.setBackgroundResource(R.drawable.roomkit_bg_bottom_sheet_dialog)
            win.setBackgroundDrawableResource(android.R.color.transparent)
            win.setWindowAnimations(R.style.RoomKitBottomDialogAnimation)

            val params = win.attributes
            params.width = WindowManager.LayoutParams.MATCH_PARENT
            params.height = context.resources.displayMetrics.heightPixels
            params.gravity = Gravity.BOTTOM
            params.flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            win.attributes = params

            bottomSheet?.layoutParams = bottomSheet.layoutParams?.apply {
                height = ViewGroup.LayoutParams.WRAP_CONTENT
            }

            val behavior = BottomSheetBehavior.from(bottomSheet!!)
            behavior.skipCollapsed = true
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
            behavior.peekHeight = context.resources.displayMetrics.heightPixels
        }
    }

    internal data class ActionItem(
        val text: String,
        val isWarning: Boolean,
        val onClick: (() -> Unit)?
    )

    class Builder(private val context: Context) {
        internal var tipsText: String = ""
        internal val actions = mutableListOf<ActionItem>()
        internal var backgroundRes: Int? = null
        internal var textColorRes: Int? = null
        internal var dividerColorRes: Int? = null

        fun setTips(@StringRes tipsResId: Int): Builder {
            this.tipsText = context.getString(tipsResId)
            return this
        }

        fun setTips(@StringRes tipsResId: Int, vararg formatArgs: Any): Builder {
            this.tipsText = context.getString(tipsResId, *formatArgs)
            return this
        }

        fun setTips(tips: String): Builder {
            this.tipsText = tips
            return this
        }

        fun addAction(
            @StringRes textResId: Int,
            isWarning: Boolean = false,
            onClick: (() -> Unit)? = null
        ): Builder {
            actions.add(ActionItem(context.getString(textResId), isWarning, onClick))
            return this
        }

        fun addAction(
            text: String,
            isWarning: Boolean = false,
            onClick: (() -> Unit)? = null
        ): Builder {
            actions.add(ActionItem(text, isWarning, onClick))
            return this
        }

        fun setBackgroundResource(@androidx.annotation.DrawableRes drawableRes: Int): Builder {
            this.backgroundRes = drawableRes
            return this
        }

        fun setTextColor(@androidx.annotation.ColorRes colorRes: Int): Builder {
            this.textColorRes = colorRes
            return this
        }

        fun setDividerColor(@androidx.annotation.ColorRes colorRes: Int): Builder {
            this.dividerColorRes = colorRes
            return this
        }

        fun show(): RoomActionSheetDialog {
            val dialog = RoomActionSheetDialog(context, this)
            dialog.show()
            return dialog
        }

        fun build(): RoomActionSheetDialog {
            return RoomActionSheetDialog(context, this)
        }
    }
}
