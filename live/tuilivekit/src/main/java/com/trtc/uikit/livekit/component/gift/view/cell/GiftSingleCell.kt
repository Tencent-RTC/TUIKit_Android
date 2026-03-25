package com.trtc.uikit.livekit.component.gift.view.cell

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.trtc.uikit.livekit.R
import io.trtc.tuikit.atomicx.common.imageloader.ImageLoader
import io.trtc.tuikit.atomicxcore.api.gift.Gift

class GiftSingleCell(itemView: View) : GiftBaseCell(itemView) {

    private val layoutGift: LinearLayout = itemView.findViewById(R.id.ll_gift)
    private val giftImageView: ImageView = itemView.findViewById(R.id.iv_gift_icon)
    private val giftNameLabel: TextView = itemView.findViewById(R.id.tv_gift_name)
    private val giftPriceLabel: TextView = itemView.findViewById(R.id.tv_gift_price)

    override val isActionStyle: Boolean = true

    private var cachedGiftName: String = ""

    override fun bindGift(gift: Gift) {
        ImageLoader.load(itemView.context, giftImageView, gift.iconURL, 0)
        cachedGiftName = gift.name
        giftNameLabel.text = gift.name
        giftPriceLabel.text = gift.coins.toString()
    }

    override fun setSelected(selected: Boolean) {
        super.setSelected(selected)
        val backgroundRes = if (selected) {
            R.drawable.gift_selected_bg
        } else {
            R.drawable.gift_normal_bg
        }
        layoutGift.setBackgroundResource(backgroundRes)

        if (selected) {
            giftNameLabel.text = itemView.context.getString(R.string.common_gift_give_gift)
            giftNameLabel.setTextColor(ContextCompat.getColor(itemView.context, android.R.color.white))
            giftNameLabel.isClickable = true
            giftNameLabel.setOnClickListener {
                performSendAction()
            }
        } else {
            giftNameLabel.text = cachedGiftName
            giftNameLabel.setTextColor(
                ContextCompat.getColor(itemView.context, R.color.common_text_color_primary)
            )
            giftNameLabel.background = null
            giftNameLabel.isClickable = false
            giftNameLabel.setOnClickListener(null)
        }
    }

    override fun handleHitWhenSelected() {
    }

    override fun performSendAction() {
        gift?.let { gift ->
            listener?.onSendGift(this, gift, 1)
        }
    }

    companion object {
        fun create(parent: ViewGroup): GiftSingleCell {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.gift_layout_panel_recycle_item, parent, false)
            return GiftSingleCell(view)
        }
    }
}
