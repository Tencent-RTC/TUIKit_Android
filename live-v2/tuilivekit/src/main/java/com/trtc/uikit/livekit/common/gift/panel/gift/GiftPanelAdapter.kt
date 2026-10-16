package com.trtc.uikit.livekit.common.gift.panel.gift

import android.annotation.SuppressLint
import android.content.Context
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.trtc.uikit.livekit.common.gift.panel.gift.viewholder.ComboViewHolder
import io.trtc.tuikit.atomicxcore.api.gift.Gift

class GiftPanelAdapter(
    private val pageIndex: Int,
    private val giftModelList: MutableList<Gift>,
    @Suppress("UNUSED_PARAMETER") context: Context? = null,
) : RecyclerView.Adapter<ComboViewHolder>() {
    private var onItemClickListener: OnItemClickListener? = null
    private var selectedPosition = RecyclerView.NO_POSITION

    var onGiftSelectedListener: ((Gift) -> Unit)? = null
    /// Forwarded from ComboViewHolder: fires when the user re-taps a selected gift
    /// that has a non-empty `resourceURL` (advanced gift).
    var onAdvancedComboRequested: ((Gift) -> Unit)? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ComboViewHolder {
        val holder = ComboViewHolder.create(parent)
        holder.onSend = ::dispatchSend
        holder.onAdvancedComboRequested = { gift -> onAdvancedComboRequested?.invoke(gift) }
        return holder
    }

    override fun onBindViewHolder(holder: ComboViewHolder, @SuppressLint("RecyclerView") position: Int) {
        val gift = giftModelList[position]
        val isSelected = selectedPosition == position

        holder.gift = gift
        holder.setSelected(isSelected)

        holder.itemView.setOnClickListener {
            val preSelectedPosition = selectedPosition

            if (selectedPosition == position) {
                holder.triggerComboAction()
            } else {
                selectedPosition = position
                val rv = holder.itemView.parent as? RecyclerView
                if (rv != null) {
                    if (preSelectedPosition != RecyclerView.NO_POSITION) {
                        val preHolder = rv.findViewHolderForAdapterPosition(preSelectedPosition) as? ComboViewHolder
                        if (preHolder != null) {
                            preHolder.setSelected(false)
                        } else {
                            notifyItemChanged(preSelectedPosition)
                        }
                    }
                    holder.setSelected(true)
                } else {
                    if (preSelectedPosition != RecyclerView.NO_POSITION) {
                        notifyItemChanged(preSelectedPosition)
                    }
                    notifyItemChanged(selectedPosition)
                }
                onGiftSelectedListener?.invoke(gift)
            }
        }
    }

    override fun onViewRecycled(holder: ComboViewHolder) {
        super.onViewRecycled(holder)
        holder.resetComboStateForReuse()
    }

    fun clearSelection() {
        val previous = selectedPosition
        if (previous == RecyclerView.NO_POSITION) return
        selectedPosition = RecyclerView.NO_POSITION
        val rv = attachedRecyclerView
        if (rv != null) {
            val holder = rv.findViewHolderForAdapterPosition(previous) as? ComboViewHolder
            if (holder != null) {
                holder.resetComboStateForReuse()
                holder.setSelected(false)
            } else {
                notifyItemChanged(previous)
            }
        } else {
            notifyItemChanged(previous)
        }
    }

    private var attachedRecyclerView: RecyclerView? = null

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        attachedRecyclerView = recyclerView
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        if (attachedRecyclerView === recyclerView) {
            attachedRecyclerView = null
        }
    }

    override fun onViewAttachedToWindow(holder: ComboViewHolder) {
        super.onViewAttachedToWindow(holder)
        holder.setSelected(selectedPosition == holder.bindingAdapterPosition)
    }

    override fun getItemCount(): Int {
        return giftModelList.size
    }

    private fun dispatchSend(gift: Gift, count: Int) {
        val position = findPositionByGift(gift)
        if (position != RecyclerView.NO_POSITION) {
            onItemClickListener?.onItemClick(null, gift, position, pageIndex, count)
        }
    }

    private fun findPositionByGift(gift: Gift): Int {
        return giftModelList.indexOfFirst { it.giftID == gift.giftID }
    }

    interface OnItemClickListener {
        fun onItemClick(view: View?, gift: Gift, position: Int, pageIndex: Int, count: Int = 1)
    }

    fun setOnItemClickListener(listener: OnItemClickListener) {
        onItemClickListener = listener
    }
}
