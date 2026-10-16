package com.trtc.uikit.livekit.common.gift.panel.gift.viewholder

import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.trtc.uikit.livekit.R
import com.trtc.uikit.livekit.common.basic.imageloader.ImageLoader
import io.trtc.tuikit.atomicxcore.api.gift.Gift

class ComboViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

    var onSend: ((Gift, Int) -> Unit)? = null
    var onAdvancedComboRequested: ((Gift) -> Unit)? = null

    var gift: Gift? = null
        set(value) {
            field = value
            value?.let { bindGift(it) }
        }

    private val rootLayout: FrameLayout = itemView as FrameLayout
    private val layoutGift: LinearLayout = itemView.findViewById(R.id.ll_gift)
    private val imageGift: ImageView = itemView.findViewById(R.id.iv_gift_icon)
    private val textGiftName: TextView = itemView.findViewById(R.id.tv_gift_name)
    private val textGiftPrice: TextView = itemView.findViewById(R.id.tv_gift_price)
    private val pricePanel: LinearLayout = itemView.findViewById(R.id.ll_gift_price)
    private val pricePanelSelected: LinearLayout = itemView.findViewById(R.id.ll_gift_price_selected)
    private val textGiftPriceSelected: TextView = itemView.findViewById(R.id.tv_gift_price_selected)
    private val sendButton: TextView = itemView.findViewById(R.id.btn_gift_send)

    private var isComboActive = false
    private var comboButtonView: ComboButtonView? = null

    private fun bindGift(gift: Gift) {
        ImageLoader.load(itemView.context, imageGift, gift.iconURL, 0)
        textGiftName.text = gift.name
        textGiftPrice.text = gift.coins.toString()
        textGiftPriceSelected.text = gift.coins.toString()
        resetComboState()
    }

    fun setSelected(selected: Boolean) {
        itemView.isSelected = selected
        val backgroundRes = if (selected) {
            R.drawable.livekit_gift_selected_bg
        } else {
            R.drawable.livekit_gift_normal_bg
        }
        itemView.setBackgroundResource(backgroundRes)
        applySelectionOverlay(selected)
        if (!selected) {
            resetComboState()
        }
    }

    private fun applySelectionOverlay(selected: Boolean) {
        if (isComboActive) return
        if (selected) {
            textGiftName.visibility = View.INVISIBLE
            pricePanel.visibility = View.INVISIBLE
            pricePanelSelected.visibility = View.VISIBLE
            sendButton.visibility = View.VISIBLE
            sendButton.setOnClickListener { performSendAction() }
        } else {
            textGiftName.visibility = View.VISIBLE
            pricePanel.visibility = View.VISIBLE
            pricePanelSelected.visibility = View.GONE
            sendButton.visibility = View.GONE
            sendButton.setOnClickListener(null)
        }
    }

    fun performSendAction() {
        val g = gift ?: return
        if (!isComboActive) {
            // First tap on a selected gift = start combo. For advanced gifts (with a
            // non-empty resourceURL), yield to the host so the combo continues as a
            // screen-level floating button.
            if (!g.resourceURL.isNullOrEmpty()) {
                onSend?.invoke(g, 1)
                onAdvancedComboRequested?.invoke(g)
                return
            }
            enterComboMode()
            onSend?.invoke(g, 1)
            comboButtonView?.startCombo()
        } else {
            onSend?.invoke(g, 1)
            comboButtonView?.bumpCombo()
        }
    }

    fun triggerComboAction() {
        if (itemView.isSelected) {
            performSendAction()
        }
    }

    fun resetComboStateForReuse() {
        resetComboState()
        comboButtonView?.stopCombo()
        comboButtonView?.visibility = View.GONE
    }

    private fun enterComboMode() {
        if (isComboActive) return
        isComboActive = true
        layoutGift.visibility = View.INVISIBLE
        sendButton.visibility = View.GONE
        pricePanelSelected.visibility = View.GONE
        itemView.setBackgroundResource(0)
        ensureComboButton()
        comboButtonView?.visibility = View.VISIBLE
    }

    private fun ensureComboButton() {
        if (comboButtonView != null) return
        val view = ComboButtonView(itemView.context).apply {
            visibility = View.GONE
            onSend = {
                gift?.let { g ->
                    this@ComboViewHolder.onSend?.invoke(g, 1)
                    bumpCombo()
                }
            }
            onExpire = {
                resetComboState()
            }
        }
        val lp = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ).apply {
            gravity = Gravity.CENTER
        }
        rootLayout.addView(view, lp)
        comboButtonView = view
    }

    private fun resetComboState() {
        val wasActive = isComboActive
        isComboActive = false
        comboButtonView?.stopCombo()
        comboButtonView?.visibility = View.GONE
        if (wasActive) {
            layoutGift.visibility = View.VISIBLE
            if (itemView.isSelected) {
                itemView.setBackgroundResource(R.drawable.livekit_gift_selected_bg)
                applySelectionOverlay(true)
            } else {
                itemView.setBackgroundResource(R.drawable.livekit_gift_normal_bg)
                applySelectionOverlay(false)
            }
        }
    }

    fun prepareForReuse() {
        resetComboState()
    }

    companion object {
        fun create(parent: ViewGroup): ComboViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.livekit_gift_layout_panel_recycle_item, parent, false)
            return ComboViewHolder(view)
        }
    }
}
