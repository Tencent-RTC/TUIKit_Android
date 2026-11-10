package com.trtc.uikit.livekit.common.gift.send

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.trtc.uikit.livekit.R
import com.trtc.uikit.livekit.common.theme.ScreenUtil
import com.trtc.uikit.livekit.common.theme.imageloader.ImageLoader
import io.trtc.tuikit.atomicxcore.api.gift.Gift

/** One category's grid, selection and in-cell gift combo. */
internal class GiftGridPageView(context: Context) : FrameLayout(context) {
    var onSend: ((Gift, Int) -> Unit)? = null
    var onAdvancedComboRequested: ((Gift) -> Unit)? = null
    private val giftAdapter = GiftGridAdapter(
        onSend = { gift, count -> onSend?.invoke(gift, count) },
        onAdvancedComboRequested = { onAdvancedComboRequested?.invoke(it) },
    )
    private val recyclerView: RecyclerView

    init {
        inflate(context, R.layout.livekit_gift_layout_category_content, this)
        recyclerView = findViewById<RecyclerView>(R.id.rv_gifts).apply {
            layoutManager = GridLayoutManager(context, 4)
            itemAnimator = null
            layoutParams.height = ScreenUtil.dip2px(228f)
            adapter = giftAdapter
        }
    }

    fun update(gifts: List<Gift>) = giftAdapter.update(gifts)

    fun clearSelection() {
        recyclerView.stopScroll()
        giftAdapter.clearSelection()
    }

    override fun onDetachedFromWindow() {
        clearSelection()
        super.onDetachedFromWindow()
    }
}

private class GiftGridAdapter(
    private val onSend: (Gift, Int) -> Unit,
    private val onAdvancedComboRequested: (Gift) -> Unit,
) : RecyclerView.Adapter<GiftItemViewHolder>() {
    private var gifts: List<Gift> = emptyList()
    private var selectedPosition = RecyclerView.NO_POSITION
    private var recyclerView: RecyclerView? = null

    fun update(gifts: List<Gift>) {
        clearSelection()
        this.gifts = gifts
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GiftItemViewHolder =
        GiftItemViewHolder.create(parent).apply {
            onSend = this@GiftGridAdapter.onSend
            onAdvancedComboRequested = this@GiftGridAdapter.onAdvancedComboRequested
        }

    override fun onBindViewHolder(holder: GiftItemViewHolder, position: Int) {
        holder.gift = gifts[position]
        holder.setSelected(position == selectedPosition)
        holder.itemView.setOnClickListener {
            val current = holder.bindingAdapterPosition
            if (current == RecyclerView.NO_POSITION) return@setOnClickListener
            if (current == selectedPosition) {
                holder.triggerComboAction()
            } else {
                val previous = selectedPosition
                selectedPosition = current
                updateSelection(previous)
                holder.setSelected(true)
            }
        }
    }

    fun clearSelection() {
        val previous = selectedPosition
        selectedPosition = RecyclerView.NO_POSITION
        updateSelection(previous)
    }

    private fun updateSelection(position: Int) {
        if (position == RecyclerView.NO_POSITION) return
        val holder = recyclerView?.findViewHolderForAdapterPosition(position) as? GiftItemViewHolder
        if (holder != null) holder.setSelected(position == selectedPosition)
        else notifyItemChanged(position)
    }

    override fun onViewRecycled(holder: GiftItemViewHolder) {
        holder.resetComboStateForReuse()
        holder.gift = null
        super.onViewRecycled(holder)
    }

    override fun onViewDetachedFromWindow(holder: GiftItemViewHolder) {
        holder.resetComboStateForReuse()
        super.onViewDetachedFromWindow(holder)
    }

    override fun onViewAttachedToWindow(holder: GiftItemViewHolder) {
        super.onViewAttachedToWindow(holder)
        holder.setSelected(selectedPosition == holder.bindingAdapterPosition)
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        this.recyclerView = recyclerView
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        clearSelection()
        this.recyclerView = null
    }

    override fun getItemCount() = gifts.size
}

private class GiftItemViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

    var onSend: ((Gift, Int) -> Unit)? = null
    var onAdvancedComboRequested: ((Gift) -> Unit)? = null

    var gift: Gift? = null
        set(value) {
            field = value
            value?.let { bindGift(it) }
        }

    private val rootLayout: ConstraintLayout = itemView as ConstraintLayout
    private val layoutGift: LinearLayout = itemView.findViewById(R.id.ll_gift)
    private val imageGift: ImageView = itemView.findViewById(R.id.iv_gift_icon)
    private val textGiftName: TextView = itemView.findViewById(R.id.tv_gift_name)
    private val textGiftPrice: TextView = itemView.findViewById(R.id.tv_gift_price)
    private val pricePanel: LinearLayout = itemView.findViewById(R.id.ll_gift_price)
    private val pricePanelSelected: LinearLayout = itemView.findViewById(R.id.ll_gift_price_selected)
    private val textGiftPriceSelected: TextView = itemView.findViewById(R.id.tv_gift_price_selected)
    private val sendButton: TextView = itemView.findViewById(R.id.btn_gift_send)

    private var isComboActive = false
    private var comboButtonView: GiftSendComboButton? = null

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
            if (g.resourceURL.isNotEmpty()) {
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
        val view = GiftSendComboButton(itemView.context).apply {
            visibility = View.GONE
            onSend = {
                gift?.let { g ->
                    this@GiftItemViewHolder.onSend?.invoke(g, 1)
                    bumpCombo()
                }
            }
            onExpire = {
                resetComboState()
            }
        }
        val lp = ConstraintLayout.LayoutParams(0, 0).apply {
            startToStart = ConstraintLayout.LayoutParams.PARENT_ID
            endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
            topToTop = ConstraintLayout.LayoutParams.PARENT_ID
            bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
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

    companion object {
        fun create(parent: ViewGroup): GiftItemViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.livekit_gift_layout_panel_recycle_item, parent, false)
            return GiftItemViewHolder(view)
        }
    }
}
