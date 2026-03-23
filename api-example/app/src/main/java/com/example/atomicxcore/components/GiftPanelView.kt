package com.example.atomicxcore.components

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.Gravity
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.atomicxcore.R
import com.google.android.material.button.MaterialButton
import io.trtc.tuikit.atomicxcore.api.gift.Gift
import io.trtc.tuikit.atomicxcore.api.gift.GiftStore

/**
 * Gift panel component
 *
 * Related APIs:
 * - GiftStore.create(liveID) - Create a gift manager instance
 * - GiftStore.refreshUsableGifts(completion) - Refresh the available gift list
 * - GiftStore.sendGift(giftID, count, completion) - Send a gift
 * - GiftStore.state - Gift state (StateFlow<GiftState>, contains usableGifts)
 *
 * Features:
 * - Grid display of available gifts (4 columns)
 * - Select and send gifts
 */
class GiftPanelView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private var giftStore: GiftStore? = null
    private val gifts = mutableListOf<Gift>()
    private var selectedGiftIndex: Int? = null
    private val recyclerView: RecyclerView
    private val giftAdapter: GiftAdapter

    /** Send gift result callback */
    var onSendGiftResult: ((code: Int, message: String) -> Unit)? = null

    init {
        orientation = VERTICAL
        setBackgroundColor(Color.parseColor("#1C1C24"))

        val dp8 = (8 * resources.displayMetrics.density).toInt()
        val dp12 = (12 * resources.displayMetrics.density).toInt()

        recyclerView = RecyclerView(context).apply {
            layoutManager = GridLayoutManager(context, 4)
            overScrollMode = OVER_SCROLL_NEVER
            setBackgroundColor(Color.TRANSPARENT)
        }
        giftAdapter = GiftAdapter()
        recyclerView.adapter = giftAdapter

        addView(recyclerView, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f).apply {
            topMargin = dp8
        })

        // Send button
        val sendButton = MaterialButton(context).apply {
            text = context.getString(R.string.interactive_gift_send)
            textSize = 14f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#FF4081"))
            cornerRadius = (20 * resources.displayMetrics.density).toInt()
            // Remove MaterialButton's default inset to prevent button content from being compressed
            insetTop = 0
            insetBottom = 0
            minimumHeight = 0
            minHeight = 0
            setPadding(0, 0, 0, 0)
            layoutParams = LayoutParams(
                (100 * resources.displayMetrics.density).toInt(),
                (40 * resources.displayMetrics.density).toInt()
            ).apply {
                gravity = Gravity.END
                marginEnd = dp12
                topMargin = dp8
                bottomMargin = dp12
            }
            setOnClickListener { sendSelectedGift() }
        }
        addView(sendButton)
    }

    /**
     * Initialize the gift panel
     */
    fun initialize(liveID: String) {
        giftStore = GiftStore.create(liveID)
        giftStore?.refreshUsableGifts(null)
    }

    /**
     * Update the gift list from GiftStore.state
     */
    fun updateGifts(giftList: List<Gift>) {
        gifts.clear()
        gifts.addAll(giftList)
        giftAdapter.notifyDataSetChanged()
    }

    private fun sendSelectedGift() {
        val index = selectedGiftIndex ?: return
        if (index >= gifts.size) return
        val gift = gifts[index]

        giftStore?.sendGift(gift.giftID, 1, com.example.atomicxcore.utils.completionHandler { code, message ->
            post { onSendGiftResult?.invoke(code, message) }
        })
    }

    // MARK: - RecyclerView Adapter

    private inner class GiftAdapter : RecyclerView.Adapter<GiftAdapter.ViewHolder>() {

        inner class ViewHolder(val container: LinearLayout) : RecyclerView.ViewHolder(container) {
            val iconView: ImageView = container.findViewWithTag("icon")
            val nameLabel: TextView = container.findViewWithTag("name")
            val priceLabel: TextView = container.findViewWithTag("price")
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val dp6 = (6 * parent.resources.displayMetrics.density).toInt()
            val dp4 = (4 * parent.resources.displayMetrics.density).toInt()
            val dp40 = (40 * parent.resources.displayMetrics.density).toInt()
            val dp10 = (10 * parent.resources.displayMetrics.density).toInt()

            val container = LinearLayout(parent.context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(dp4, dp6, dp4, dp6)
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(Color.parseColor("#26FFFFFF"))
                    cornerRadius = 10 * resources.displayMetrics.density
                }
                layoutParams = RecyclerView.LayoutParams(
                    RecyclerView.LayoutParams.MATCH_PARENT,
                    RecyclerView.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(dp4, dp4, dp4, dp4)
                }
            }

            val iconView = ImageView(parent.context).apply {
                tag = "icon"
                layoutParams = LinearLayout.LayoutParams(dp40, dp40)
                scaleType = ImageView.ScaleType.FIT_CENTER
            }
            container.addView(iconView)

            val nameLabel = TextView(parent.context).apply {
                tag = "name"
                textSize = 12f
                setTextColor(Color.parseColor("#E6FFFFFF"))
                gravity = Gravity.CENTER
                maxLines = 1
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp4 }
            }
            container.addView(nameLabel)

            val priceLabel = TextView(parent.context).apply {
                tag = "price"
                textSize = 11f
                setTextColor(Color.parseColor("#FFD700"))
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }
            container.addView(priceLabel)

            return ViewHolder(container)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val gift = gifts[position]
            holder.nameLabel.text = gift.name
            holder.priceLabel.text = "${gift.coins}"

            // Load gift icon
            holder.iconView.load(gift.iconURL) {
                placeholder(R.drawable.ic_gift)
                error(R.drawable.ic_gift)
            }

            // Selected state
            val isSelected = position == selectedGiftIndex
            val bg = holder.container.background as? android.graphics.drawable.GradientDrawable
            if (isSelected) {
                bg?.setColor(Color.parseColor("#40FF4081"))
                bg?.setStroke((1.5 * holder.container.resources.displayMetrics.density).toInt(), Color.parseColor("#FF4081"))
            } else {
                bg?.setColor(Color.parseColor("#26FFFFFF"))
                bg?.setStroke(0, Color.TRANSPARENT)
            }

            holder.container.setOnClickListener {
                val prev = selectedGiftIndex
                selectedGiftIndex = position
                prev?.let { notifyItemChanged(it) }
                notifyItemChanged(position)
            }
        }

        override fun getItemCount() = gifts.size
    }
}
