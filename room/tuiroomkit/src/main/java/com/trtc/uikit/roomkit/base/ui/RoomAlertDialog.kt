package com.trtc.uikit.roomkit.base.ui

import android.app.Dialog
import android.content.Context
import android.view.View
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.trtc.uikit.roomkit.R
import com.trtc.uikit.roomkit.base.log.RoomKitLogger

/**
 * Room alert dialog component inspired by iOS UIAlertController.
 * Automatically displays single-button or double-button mode based on configured actions.
 *
 * Features:
 * - Auto mode: Only positive button → single-button; Both negative and positive → double-button
 * - Customizable title, message, and button text
 * - Warning mode support (red positive button)
 * - Builder pattern for flexible configuration
 * - Fully reusable component
 *
 * Usage:
 * ```kotlin
 * // Single-button mode
 * RoomAlertDialog.Builder(context)
 *     .setTitle(R.string.roomkit_msg_room_dismissed)
 *     .setPositiveButton(R.string.roomkit_ok) { finishActivity() }
 *     .show()
 *
 * // Double-button mode
 * RoomAlertDialog.Builder(context)
 *     .setTitle(R.string.roomkit_confirm_leave_room_title)
 *     .setNegativeButton(R.string.roomkit_cancel) { }
 *     .setPositiveButton(R.string.roomkit_confirm) { handleLeaveRoom() }
 *     .show()
 *
 * // Warning mode
 * RoomAlertDialog.Builder(context)
 *     .setTitle(R.string.roomkit_confirm_kick_title, participant.userName)
 *     .setWarning(true)
 *     .show()
 * ```
 */
class RoomAlertDialog private constructor(
    context: Context,
    private val builder: Builder
) : Dialog(context, R.style.RoomKitCenterDialog) {

    private val logger = RoomKitLogger.getLogger("RoomConfirmDialog")

    private val tvTitle: TextView
    private val tvMessage: TextView
    private val tvCancel: TextView
    private val tvConfirm: TextView
    private val verticalDividerView: View

    init {
        setContentView(R.layout.roomkit_dialog_alert)

        tvTitle = findViewById(R.id.tv_title)
        tvMessage = findViewById(R.id.tv_message)
        tvCancel = findViewById(R.id.tv_cancel)
        tvConfirm = findViewById(R.id.tv_confirm)
        verticalDividerView = findViewById(R.id.view_divider_vertical)

        setupView()
        setupListeners()
    }

    private fun setupView() {
        tvTitle.text = builder.title

        if (builder.message.isNullOrEmpty()) {
            tvMessage.visibility = View.GONE
        } else {
            tvMessage.visibility = View.VISIBLE
            tvMessage.text = builder.message
        }

        val hasCancelAction = builder.negativeAction != null
        
        if (hasCancelAction) {
            tvCancel.visibility = View.VISIBLE
            tvCancel.text = builder.negativeAction!!.text
            verticalDividerView.visibility = View.VISIBLE
        } else {
            tvCancel.visibility = View.GONE
            verticalDividerView.visibility = View.GONE
        }

        tvConfirm.text = builder.positiveAction.text

        if (builder.isWarning) {
            tvConfirm.setTextColor(ContextCompat.getColor(context, R.color.roomkit_color_end_room))
        }
    }

    private fun setupListeners() {
        tvCancel.setOnClickListener {
            logger.info("User clicked negative button")
            builder.negativeAction?.listener?.invoke()
            dismiss()
        }

        tvConfirm.setOnClickListener {
            logger.info("User clicked positive button")
            builder.positiveAction.listener?.invoke()
            dismiss()
        }

        // Single-button mode: not cancelable by default; Double-button mode: cancelable by default
        setCanceledOnTouchOutside(builder.cancelable)
        setCancelable(builder.cancelable)
    }

    /**
     * Button action data class
     */
    data class Action(
        val text: String,
        val listener: (() -> Unit)?
    )

    /**
     * Builder pattern for constructing dialogs
     */
    class Builder(private val context: Context) {
        internal var title: String = ""
        internal var message: String? = null
        internal lateinit var positiveAction: Action
        internal var negativeAction: Action? = null
        internal var isWarning: Boolean = false
        internal var cancelable: Boolean = false

        fun setTitle(title: String): Builder {
            this.title = title
            return this
        }

        fun setTitle(titleResId: Int): Builder {
            this.title = context.getString(titleResId)
            return this
        }

        fun setTitle(titleResId: Int, vararg formatArgs: Any): Builder {
            this.title = context.getString(titleResId, *formatArgs)
            return this
        }

        fun setMessage(message: String?): Builder {
            this.message = message
            return this
        }

        fun setMessage(messageResId: Int): Builder {
            this.message = context.getString(messageResId)
            return this
        }

        /**
         * Set negative button (cancel/no button, left side).
         * If not set, dialog will display in single-button mode.
         */
        fun setNegativeButton(text: String, listener: (() -> Unit)? = null): Builder {
            this.negativeAction = Action(text, listener)
            return this
        }

        fun setNegativeButton(textResId: Int, listener: (() -> Unit)? = null): Builder {
            this.negativeAction = Action(context.getString(textResId), listener)
            return this
        }

        /**
         * Set positive button (confirm/yes button, right side).
         * This button is required.
         */
        fun setPositiveButton(text: String, listener: (() -> Unit)? = null): Builder {
            this.positiveAction = Action(text, listener)
            return this
        }

        fun setPositiveButton(textResId: Int, listener: (() -> Unit)? = null): Builder {
            this.positiveAction = Action(context.getString(textResId), listener)
            return this
        }

        /**
         * Set whether this is a warning action (positive button will be red)
         */
        fun setWarning(isWarning: Boolean): Builder {
            this.isWarning = isWarning
            return this
        }

        /**
         * Set whether dialog can be dismissed by clicking outside or back button.
         * If not set, defaults to false for single-button mode, true for double-button mode.
         */
        fun setCancelable(cancelable: Boolean): Builder {
            this.cancelable = cancelable
            return this
        }

        fun build(): RoomAlertDialog {
            if (title.isEmpty()) {
                throw IllegalArgumentException("Title must not be empty")
            }
            if (!::positiveAction.isInitialized) {
                throw IllegalArgumentException("Positive button must be set")
            }
            return RoomAlertDialog(context, this)
        }

        fun show(): RoomAlertDialog {
            val dialog = build()
            dialog.show()
            return dialog
        }
    }
}
