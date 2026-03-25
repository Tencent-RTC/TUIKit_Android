package com.trtc.uikit.livekit.component.gift.view.cell

import android.view.View
import androidx.recyclerview.widget.RecyclerView
import com.trtc.uikit.livekit.component.gift.view.cell.GiftCellConfiguration
import io.trtc.tuikit.atomicxcore.api.gift.Gift

abstract class GiftBaseCell(itemView: View) : RecyclerView.ViewHolder(itemView) {

    var gift: Gift? = null
        set(value) {
            field = value
            value?.let { bindGift(it) }
        }

    var listener: GiftCellListener? = null

    var configuration: GiftCellConfiguration = GiftCellConfiguration()
        set(value) {
            field = value
            onConfigurationChanged(value)
        }

    open val isActionStyle: Boolean = true

    protected abstract fun bindGift(gift: Gift)

    open fun setSelected(selected: Boolean) {
        itemView.isSelected = selected
        updateSelectionState()
        gift?.let { gift ->
            listener?.onCellSelectionChanged(this, gift, selected)
        }
    }

    protected open fun updateSelectionState() {
    }

    open fun handleHitWhenSelected() {
    }

    open fun performSendAction() {
        gift?.let { gift ->
            listener?.onSendGift(this, gift, 1)
        }
    }

    protected open fun onConfigurationChanged(config: GiftCellConfiguration) {
    }

    open fun prepareForReuse() {
    }
}

interface GiftCellListener {
    fun onSendGift(cell: GiftBaseCell, gift: Gift, count: Int)
    fun onCellSelectionChanged(cell: GiftBaseCell, gift: Gift, isSelected: Boolean) {}
}
