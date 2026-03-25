package com.trtc.uikit.livekit.component.gift.view.adapter

import android.content.Context
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.trtc.uikit.livekit.component.gift.view.cell.GiftCellConfiguration
import com.trtc.uikit.livekit.component.gift.view.cell.GiftBaseCell
import com.trtc.uikit.livekit.component.gift.view.cell.GiftBatchCell
import com.trtc.uikit.livekit.component.gift.view.cell.GiftCellListener
import com.trtc.uikit.livekit.component.gift.view.cell.GiftComboCell
import com.trtc.uikit.livekit.component.gift.view.cell.GiftSingleCell
import io.trtc.tuikit.atomicxcore.api.gift.Gift

class GiftPanelAdapter(
    private val pageIndex: Int,
    private val giftModelList: MutableList<Gift>,
    private val context: Context
) : RecyclerView.Adapter<RecyclerView.ViewHolder>(), GiftCellListener {
    private var onItemClickListener: OnItemClickListener? = null
    private var selectedPosition = RecyclerView.NO_POSITION
    private val configuration = GiftCellConfiguration()

    var cellFactory: ((ViewGroup, Gift) -> GiftBaseCell?)? = null
    var onGiftSelectedListener: ((Gift) -> Unit)? = null

    private val giftIdToViewType = mutableMapOf<String, Int>()
    private val viewTypeToGift = mutableMapOf<Int, Gift>()
    private var nextViewType = 0

    private fun getViewTypeForGift(gift: Gift): Int {
        return giftIdToViewType.getOrPut(gift.giftID) {
            val type = nextViewType++
            viewTypeToGift[type] = gift
            type
        }
    }

    override fun getItemViewType(position: Int): Int {
        return getViewTypeForGift(giftModelList[position])
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val gift = viewTypeToGift[viewType]
        val cell = if (gift != null) {
            createCell(parent, gift)
        } else {
            GiftSingleCell.create(parent)
        }
        cell.listener = this
        cell.configuration = configuration
        return GiftCellViewHolder(cell)
    }

    private fun createCell(parent: ViewGroup, gift: Gift): GiftBaseCell {
        cellFactory?.invoke(parent, gift)?.let { return it }
        return if (gift.resourceURL.isEmpty()) {
            GiftComboCell.create(parent)
        } else {
            GiftSingleCell.create(parent)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val gift = giftModelList[position]

        when (holder) {
            is GiftCellViewHolder -> {
                val cell = holder.cell
                cell.gift = gift
                cell.setSelected(selectedPosition == position)

                if (cell is GiftBatchCell) {
                    cell.setupLongPressOnBind()
                }

                cell.itemView.setOnClickListener {
                    val preSelectedPosition = selectedPosition

                    if (selectedPosition == position) {
                        if (cell is GiftComboCell) {
                            cell.triggerComboAction()
                        } else {
                            cell.handleHitWhenSelected()
                        }
                    } else {
                        selectedPosition = position
                        if (preSelectedPosition != RecyclerView.NO_POSITION) {
                            notifyItemChanged(preSelectedPosition)
                        }
                        notifyItemChanged(selectedPosition)
                        onGiftSelectedListener?.invoke(gift)
                    }
                }
            }
        }
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        super.onViewRecycled(holder)
        when (holder) {
            is GiftCellViewHolder -> {
                val cell = holder.cell
                if (cell is GiftComboCell) {
                    cell.resetComboStateForReuse()
                }
            }
        }
    }

    override fun onViewAttachedToWindow(holder: RecyclerView.ViewHolder) {
        super.onViewAttachedToWindow(holder)
        when (holder) {
            is GiftCellViewHolder -> {
                val cell = holder.cell
                if (cell is GiftComboCell) {
                    cell.setSelected(selectedPosition == holder.bindingAdapterPosition)
                }
            }
        }
    }

    override fun getItemCount(): Int {
        return giftModelList.size
    }

    override fun onSendGift(cell: GiftBaseCell, gift: Gift, count: Int) {
        val position = findPositionByGift(gift)
        if (position != RecyclerView.NO_POSITION) {
            onItemClickListener?.onItemClick(null, gift, position, pageIndex, count)
        }
    }

    private fun findPositionByGift(gift: Gift): Int {
        return giftModelList.indexOfFirst { it.giftID == gift.giftID }
    }

    class GiftCellViewHolder(val cell: GiftBaseCell) : RecyclerView.ViewHolder(cell.itemView)

    interface OnItemClickListener {
        fun onItemClick(view: View?, gift: Gift, position: Int, pageIndex: Int, count: Int = 1)
    }

    fun setOnItemClickListener(listener: OnItemClickListener) {
        onItemClickListener = listener
    }
}
