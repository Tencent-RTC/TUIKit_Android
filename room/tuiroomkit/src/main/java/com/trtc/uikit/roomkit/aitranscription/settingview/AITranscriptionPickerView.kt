package com.trtc.uikit.roomkit.aitranscription.settingview

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.trtc.uikit.roomkit.R
import com.trtc.uikit.roomkit.base.utils.dpToPx
import com.trtc.uikit.roomkit.base.utils.getScreenHeight

data class AITranscriptionPickerItem(
    val title: String,
    var isSelected: Boolean = false,
)

/**
 * Bottom sheet single-selection picker with mask overlay and slide animation.
 */
@SuppressLint("ViewConstructor")
class AITranscriptionPickerView(
    context: Context,
    private val titleText: String,
    private val items: List<AITranscriptionPickerItem>,
    private val onSelect: ((Int, AITranscriptionPickerItem) -> Unit)? = null,
) : FrameLayout(context) {

    companion object {
        private const val TITLE_HEIGHT_DP = 56
        private const val ITEM_HEIGHT_DP = 56
        private const val MAX_HEIGHT_RATIO = 0.7f
    }

    private val maskView: View
    private val contentPanel: ConstraintLayout
    private val titleLabel: TextView
    private val recyclerView: RecyclerView

    private val panelHeightPx: Int

    init {
        LayoutInflater.from(context).inflate(R.layout.roomkit_view_ai_transcription_picker, this, true)

        maskView = findViewById(R.id.view_mask)
        contentPanel = findViewById(R.id.cl_content_panel)
        titleLabel = findViewById(R.id.tv_picker_title)
        recyclerView = findViewById(R.id.rv_picker_items)

        panelHeightPx = calculatePanelHeight()

        // Setup mask
        maskView.setOnClickListener { dismiss(animated = true) }

        // Setup title
        titleLabel.text = titleText

        // Setup content panel rounded corners
        contentPanel.clipToOutline = true
        contentPanel.outlineProvider = object : android.view.ViewOutlineProvider() {
            override fun getOutline(view: View, outline: android.graphics.Outline) {
                val radiusPx = dpToPx(16).toFloat()
                outline.setRoundRect(0, 0, view.width, view.height + radiusPx.toInt(), radiusPx)
            }
        }

        // Setup content panel height
        contentPanel.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, panelHeightPx).apply {
            gravity = android.view.Gravity.BOTTOM
        }

        // Setup RecyclerView
        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.adapter = PickerAdapter()
        recyclerView.setHasFixedSize(true)

        // Add bottom padding for navigation bar safe area
        val navBarHeight = getNavigationBarHeight(context)
        recyclerView.setPadding(0, 0, 0, navBarHeight)
        recyclerView.clipToPadding = false

        // Start off-screen
        contentPanel.translationY = panelHeightPx.toFloat()
    }

    private fun getNavigationBarHeight(context: Context): Int {
        val resourceId = context.resources.getIdentifier("navigation_bar_height", "dimen", "android")
        if (resourceId > 0) {
            return context.resources.getDimensionPixelSize(resourceId)
        }
        return 0
    }

    private fun calculatePanelHeight(): Int {
        val totalItemsHeight = dpToPx(ITEM_HEIGHT_DP) * items.size
        val separatorsHeight = dpToPx(0.5f) * items.size
        val navBarHeight = getNavigationBarHeight(context)
        val naturalHeight = dpToPx(TITLE_HEIGHT_DP) + totalItemsHeight + separatorsHeight + navBarHeight
        val maxHeight = (getScreenHeight(context) * MAX_HEIGHT_RATIO).toInt()
        return naturalHeight.coerceAtMost(maxHeight)
    }

    // MARK: - Show / Dismiss

    fun show(parent: ViewGroup, animated: Boolean) {
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        parent.addView(this)

        if (animated) {
            val maskAnim = ObjectAnimator.ofFloat(maskView, "alpha", 0f, 1f)
            val slideAnim = ObjectAnimator.ofFloat(contentPanel, "translationY", panelHeightPx.toFloat(), 0f)
            AnimatorSet().apply {
                playTogether(maskAnim, slideAnim)
                duration = 300
                interpolator = DecelerateInterpolator()
                start()
            }
        } else {
            maskView.alpha = 1f
            contentPanel.translationY = 0f
        }

        scrollToSelectedItem()
    }

    fun dismiss(animated: Boolean, completion: (() -> Unit)? = null) {
        if (animated) {
            val maskAnim = ObjectAnimator.ofFloat(maskView, "alpha", 1f, 0f)
            val slideAnim = ObjectAnimator.ofFloat(contentPanel, "translationY", 0f, panelHeightPx.toFloat())
            AnimatorSet().apply {
                playTogether(maskAnim, slideAnim)
                duration = 250
                addListener(object : android.animation.AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: android.animation.Animator) {
                        (parent as? ViewGroup)?.removeView(this@AITranscriptionPickerView)
                        completion?.invoke()
                    }
                })
                start()
            }
        } else {
            (parent as? ViewGroup)?.removeView(this)
            completion?.invoke()
        }
    }

    private fun scrollToSelectedItem() {
        val selectedIndex = items.indexOfFirst { it.isSelected }
        if (selectedIndex >= 0) {
            (recyclerView.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(selectedIndex, 0)
        }
    }

    // MARK: - Adapter

    private inner class PickerAdapter : RecyclerView.Adapter<PickerAdapter.VH>() {

        inner class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val textView: TextView = itemView.findViewById(R.id.tv_picker_item)
            val separator: View = itemView.findViewById(R.id.view_separator)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.roomkit_item_ai_picker, parent, false)
            return VH(view)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = items[position]
            holder.textView.text = item.title
            holder.textView.setTextColor(
                if (item.isSelected) context.getColor(R.color.roomkit_color_button_primary)
                else context.getColor(R.color.roomkit_color_black)
            )
            holder.separator.visibility = if (position < items.size - 1) View.VISIBLE else View.GONE
            holder.itemView.setOnClickListener {
                items.forEach { it.isSelected = false }
                item.isSelected = true
                dismiss(animated = true) {
                    onSelect?.invoke(position, item)
                }
            }
        }

        override fun getItemCount(): Int = items.size
    }
}
