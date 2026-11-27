package io.trtc.tuikit.chat.uikit.components.messagelist.adapter
import android.view.View

object MessageInteractionBinder {
    fun resolveMode(
        enableMessageInteraction: Boolean,
        isMultiSelectMode: Boolean
    ): MessageInteractionMode {
        return when {
            !enableMessageInteraction -> MessageInteractionMode.DISABLED
            isMultiSelectMode -> MessageInteractionMode.MULTI_SELECT
            else -> MessageInteractionMode.NORMAL
        }
    }

    fun bind(
        containerView: View,
        interactionTargetView: View,
        itemView: View,
        isMultiSelectMode: Boolean,
        enableMessageInteraction: Boolean,
        onLongClick: (View) -> Unit,
        onCheckToggle: () -> Unit
    ) {
        when (resolveMode(enableMessageInteraction, isMultiSelectMode)) {
            MessageInteractionMode.DISABLED -> {
                containerView.setOnLongClickListener(null)
                containerView.setOnClickListener(null)
                interactionTargetView.setOnLongClickListener(null)
                itemView.setOnClickListener(null)
                itemView.isClickable = false
            }

            MessageInteractionMode.NORMAL -> {
                val longClickListener = View.OnLongClickListener {
                    onLongClick(interactionTargetView)
                    true
                }
                containerView.setOnLongClickListener(longClickListener)
                interactionTargetView.setOnLongClickListener(longClickListener)
                containerView.setOnClickListener(null)
                itemView.setOnClickListener(null)
                itemView.isClickable = false
            }

            MessageInteractionMode.MULTI_SELECT -> {
                containerView.setOnLongClickListener(null)
                containerView.setOnClickListener(null)
                interactionTargetView.setOnLongClickListener(null)
                itemView.isClickable = true
                itemView.setOnClickListener { onCheckToggle() }
            }

            else -> {
                val longClickListener = View.OnLongClickListener {
                    onLongClick(interactionTargetView)
                    true
                }
                containerView.setOnLongClickListener(longClickListener)
                interactionTargetView.setOnLongClickListener(longClickListener)
                containerView.setOnClickListener(null)
                itemView.setOnClickListener(null)
                itemView.isClickable = false
            }
        }
    }
}

enum class MessageInteractionMode {
    DISABLED,
    NORMAL,
    MULTI_SELECT
}
